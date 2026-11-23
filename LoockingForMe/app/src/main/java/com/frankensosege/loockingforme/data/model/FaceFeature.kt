package com.frankensosege.loockingforme.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "face_features",
    foreignKeys = [
        ForeignKey(
            entity        = PhotoList::class,
            parentColumns = ["id"],
            childColumns  = ["list_id"],
            onDelete      = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["list_id"], unique = true)],
)
data class FaceFeature(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "list_id")
    val listId: Long,

    /**
     * 임베딩 (단일 또는 다중)
     * 구 형식 단일: "0.1,0.2,..."
     * 구 형식 다중: "0.1,0.2,...||SPLIT||0.3,0.4,..."
     * 슬롯 형식  : "[S]슬롯0임베딩들||SLOT||슬롯1임베딩들||SLOT||..." (슬롯 안의 임베딩은 ||SPLIT|| 구분,
     *              빈 슬롯은 빈 문자열) — 슬롯 단위 교체/삭제를 위해 슬롯별로 묶어서 저장
     */
    @ColumnInfo(name = "embedding")
    val embedding: String,

    @ColumnInfo(name = "source_uri")
    val sourceUri: String,

    /**
     * 썸네일 경로 (단일 또는 다중 — THUMBNAIL_SEPARATOR 구분)
     * 슬롯별로 각각 저장: "path1||THUMB||path2||THUMB||..."
     * DB 스키마 변경 없이 기존 단일 경로와 하위 호환
     */
    @ColumnInfo(name = "face_thumbnail_path")
    val faceThumbnailPath: String = "",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toEmbeddingList(): List<FloatArray> =
        slotGroups().flatMap { group -> parseGroup(group) }

    /** 한 슬롯(또는 구 형식 전체) 문자열 → 임베딩 목록 */
    private fun parseGroup(group: String): List<FloatArray> =
        group.split(EMBEDDING_SEPARATOR)
            .map { chunk ->
                chunk.trim().split(",")
                    .mapNotNull { it.trim().toFloatOrNull() }
                    .toFloatArray()
            }
            .filter { it.isNotEmpty() }

    /** 슬롯 형식 여부 — 구 형식은 슬롯 구분 없이 임베딩만 이어 붙인 형태 */
    val isSlotted: Boolean get() = embedding.startsWith(SLOT_PREFIX)

    /** 슬롯별 임베딩 묶음 원문. 구 형식은 전체를 슬롯 0 하나로 취급한다 */
    private fun slotGroups(): List<String> =
        if (isSlotted) embedding.removePrefix(SLOT_PREFIX).split(SLOT_SEPARATOR)
        else listOf(embedding)

    /** 해당 슬롯에 샘플이 있는지 (구 형식은 썸네일 개수 기준 — 기존 동작 유지) */
    fun isSlotFilled(slotIndex: Int): Boolean =
        if (isSlotted) slotGroups().getOrNull(slotIndex)?.isNotBlank() == true
        else slotIndex < getThumbnailPaths().size

    /**
     * 해당 슬롯의 임베딩·썸네일을 교체(없으면 추가)한 새 FaceFeature 반환
     * 다른 슬롯은 그대로 유지한다. 구 형식은 기존 임베딩 전체를 슬롯 0으로 보고 변환한다.
     */
    fun withSlot(slotIndex: Int, embeddings: List<FloatArray>, thumbnailPath: String): FaceFeature {
        val groups = slotGroups().toMutableList()
        while (groups.size <= slotIndex) groups.add("")
        groups[slotIndex] = embeddings.joinToString(EMBEDDING_SEPARATOR) { it.joinToString(",") }

        val thumbs = getThumbnailPaths().toMutableList()
        while (thumbs.size <= slotIndex) thumbs.add("")
        if (thumbnailPath.isNotEmpty()) thumbs[slotIndex] = thumbnailPath

        return copy(
            embedding         = SLOT_PREFIX + groups.joinToString(SLOT_SEPARATOR),
            faceThumbnailPath = thumbs.joinToString(THUMBNAIL_SEPARATOR),
            createdAt         = System.currentTimeMillis(),
        )
    }

    /**
     * 해당 슬롯의 샘플(임베딩·썸네일)을 제거한 새 FaceFeature 반환
     * - 남은 샘플이 없으면 null (호출 측에서 FaceFeature 자체를 삭제)
     * - 구 형식은 슬롯별로 분리할 수 없으므로 null (전체 초기화 → 다시 등록 필요)
     */
    fun withoutSlot(slotIndex: Int): FaceFeature? {
        if (!isSlotted) return null
        val groups = slotGroups().toMutableList()
        if (slotIndex !in groups.indices) return this
        groups[slotIndex] = ""
        if (groups.all { it.isBlank() }) return null

        val thumbs = getThumbnailPaths().toMutableList()
        if (slotIndex in thumbs.indices) thumbs[slotIndex] = ""

        // 뒤쪽의 빈 슬롯 정리 (가운데 빈 슬롯은 자리를 유지)
        while (groups.isNotEmpty() && groups.last().isBlank()) groups.removeAt(groups.lastIndex)
        while (thumbs.isNotEmpty() && thumbs.last().isEmpty()) thumbs.removeAt(thumbs.lastIndex)

        return copy(
            embedding         = SLOT_PREFIX + groups.joinToString(SLOT_SEPARATOR),
            faceThumbnailPath = thumbs.joinToString(THUMBNAIL_SEPARATOR),
            createdAt         = System.currentTimeMillis(),
        )
    }

    fun toCentroid(): FloatArray {
        val list = toEmbeddingList()
        if (list.isEmpty()) return FloatArray(0)
        if (list.size == 1) return list[0]
        val dim      = list[0].size
        val centroid = FloatArray(dim)
        list.forEach { emb ->
            for (i in 0 until minOf(dim, emb.size)) centroid[i] += emb[i]
        }
        val count = list.size.toFloat()
        for (i in centroid.indices) centroid[i] = centroid[i] / count
        val norm = kotlin.math.sqrt(centroid.sumOf { (it * it).toDouble() }).toFloat()
            .coerceAtLeast(1e-10f)
        return FloatArray(centroid.size) { centroid[it] / norm }
    }

    val embeddingCount: Int get() =
        slotGroups().sumOf { group ->
            group.split(EMBEDDING_SEPARATOR).count { it.trim().isNotEmpty() }
        }

    /**
     * 슬롯별 썸네일 경로 목록 반환
     * 기존 단일 경로(구분자 없음)는 첫 번째 슬롯 경로로 반환
     */
    fun getThumbnailPaths(): List<String> =
        if (faceThumbnailPath.contains(THUMBNAIL_SEPARATOR)) {
            faceThumbnailPath.split(THUMBNAIL_SEPARATOR)
        } else {
            if (faceThumbnailPath.isNotEmpty()) listOf(faceThumbnailPath) else emptyList()
        }

    /** 특정 슬롯의 썸네일 경로 반환 (없으면 "") */
    fun getThumbnailPath(slotIndex: Int): String =
        getThumbnailPaths().getOrElse(slotIndex) { "" }

    companion object {
        const val EMBEDDING_SEPARATOR  = "||SPLIT||"
        const val THUMBNAIL_SEPARATOR  = "||THUMB||"

        /** 슬롯 형식 표식 / 슬롯 구분자 */
        const val SLOT_PREFIX          = "[S]"
        const val SLOT_SEPARATOR       = "||SLOT||"

        /**
         * 기존 FaceFeature에 새 임베딩 + 썸네일 추가 (구 형식 호환용)
         * 썸네일은 THUMBNAIL_SEPARATOR로 누적
         * ※ 슬롯 단위 교체/추가에는 [withSlot]을 사용한다.
         */
        fun addEmbedding(
            existing: FaceFeature,
            newEmbedding: FloatArray,
            newThumbnailPath: String = "",
        ): FaceFeature {
            val newEmbStr   = existing.embedding + EMBEDDING_SEPARATOR + newEmbedding.joinToString(",")
            val newThumbStr = if (newThumbnailPath.isNotEmpty()) {
                if (existing.faceThumbnailPath.isNotEmpty())
                    existing.faceThumbnailPath + THUMBNAIL_SEPARATOR + newThumbnailPath
                else
                    newThumbnailPath
            } else {
                existing.faceThumbnailPath
            }
            return existing.copy(
                embedding         = newEmbStr,
                faceThumbnailPath = newThumbStr,
                createdAt         = System.currentTimeMillis(),
            )
        }

        fun fromFloatArrayList(
            listId: Long,
            embeddings: List<FloatArray>,
            sourceUri: String,
            faceThumbnailPath: String = "",
        ): FaceFeature = FaceFeature(
            listId            = listId,
            // 슬롯 형식으로 저장 — 이 임베딩들은 슬롯 0의 샘플
            embedding         = SLOT_PREFIX +
                    embeddings.joinToString(EMBEDDING_SEPARATOR) { it.joinToString(",") },
            sourceUri         = sourceUri,
            faceThumbnailPath = faceThumbnailPath,
        )
    }
}