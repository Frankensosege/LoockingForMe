package com.frankensosege.loockingforme.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.frankensosege.loockingforme.BuildConfig
import com.frankensosege.loockingforme.data.db.FaceFeatureDao
import com.frankensosege.loockingforme.data.db.PhotoItemDao
import com.frankensosege.loockingforme.data.db.PhotoListDao
import com.frankensosege.loockingforme.data.model.FaceFeature
import com.frankensosege.loockingforme.data.model.PhotoItem
import com.frankensosege.loockingforme.data.model.PhotoList
import com.frankensosege.loockingforme.utils.FaceAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class PhotoRepository(
    private val context: Context,
    private val photoListDao: PhotoListDao,
    private val photoItemDao: PhotoItemDao,
    private val faceFeatureDao: FaceFeatureDao,
    private val faceAnalyzer: FaceAnalyzer,
) {

    // ── 목록 관리 ──────────────────────────────────────────────────────────────

    fun getAllLists(): Flow<List<PhotoList>> = photoListDao.getAllLists()

    suspend fun insertList(name: String): Long =
        photoListDao.insertList(PhotoList(name = name))

    /**
     * 목록 삭제
     *
     * - 목록의 얼굴 특징과 분류 결과를 삭제한다.
     * - 이 목록에 분류돼 있던 사진은 미분류 목록으로 이동한다.
     *   단, 다른 일반 목록에도 분류돼 있는 사진은 그 목록에 남으므로 미분류로 옮기지 않는다.
     * - 미분류 목록 자체를 삭제할 때는 이동 없이 기존 동작(삭제만)을 유지한다.
     *
     * @param unclassifiedName 미분류 목록이 없어 새로 만들 때 쓸 이름 (다국어 대응)
     */
    suspend fun deleteList(list: PhotoList, unclassifiedName: String = "미분류") =
        withContext(Dispatchers.IO) {
            // 1. 삭제 전에 이 목록에 분류돼 있던 사진 URI 수집
            val movedUris =
                if (list.isUnclassified) emptyList()
                else photoItemDao.getFileUrisByListId(list.id)

            // 2. 기존 삭제 동작
            faceFeatureDao.deleteByListId(list.id)
            photoItemDao.deleteByListId(list.id)
            photoListDao.deleteList(list)

            // 3. 다른 일반 목록에 남아 있지 않은 사진만 미분류로 이동 (공용 함수)
            moveToUnclassifiedIfOrphan(movedUris, unclassifiedName)
        }

    /** 일반 목록(미분류 제외) 전체 반환 — 재분류 목록 선택 다이얼로그용 */
    suspend fun getClassifiedLists(): List<PhotoList> =
        photoListDao.getClassifiedLists()

    // ── 얼굴 특징 관리 ─────────────────────────────────────────────────────────

    fun getFaceFeature(listId: Long): Flow<FaceFeature?> =
        faceFeatureDao.getFeatureByListIdFlow(listId)

    /** 단건 조회 (suspend) — onAddFaceSample에서 기존 임베딩 확인 시 사용 */
    suspend fun getFaceFeatureOnce(listId: Long): FaceFeature? =
        faceFeatureDao.getFeatureByListId(listId)

    suspend fun saveFaceFeature(feature: FaceFeature): Long =
        faceFeatureDao.insertFeature(feature)

    suspend fun deleteFaceFeature(listId: Long) =
        faceFeatureDao.deleteByListId(listId)

    // ── 스캔 결과 관리 ─────────────────────────────────────────────────────────

    fun getPhotoItems(listId: Long): Flow<List<PhotoItem>> =
        photoItemDao.getItemsByListId(listId)

    fun getPhotoItemCount(listId: Long): Flow<Int> =
        photoItemDao.countByListIdFlow(listId)

    /**
     * 선택한 사진을 현재 목록에서 삭제
     * - 삭제한 사진이 다른 일반 목록에도 분류돼 있으면 그대로 두고,
     *   어디에도 분류돼 있지 않게 되면 미분류 목록으로 이동한다.
     * - 미분류 목록에서 삭제하는 경우는 이동 없이 삭제만 한다.
     */
    suspend fun deletePhotoItemsByIds(ids: List<Long>, unclassifiedName: String) =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext

            // 1. 삭제 전에 대상 사진의 URI 수집 (IN 절 제한 → 청크). 미분류 목록 항목은 이동 대상 아님
            val unclassifiedId = photoListDao.getUnclassifiedList()?.id
            val movedUris = ids.chunked(500)
                .flatMap { chunk -> photoItemDao.getItemsByIds(chunk) }
                .filter { it.listId != unclassifiedId }
                .map { it.fileUri }
                .distinct()

            // 2. 기존 삭제 동작
            ids.chunked(500).forEach { chunk -> photoItemDao.deleteByIds(chunk) }

            // 3. 다른 일반 목록에 남아 있지 않은 사진만 미분류로 이동
            moveToUnclassifiedIfOrphan(movedUris, unclassifiedName)
        }

    /** 다른 일반 목록에 분류돼 있지 않은 URI만 미분류 목록에 추가 (미분류 목록이 없으면 생성) */
    private suspend fun moveToUnclassifiedIfOrphan(uris: List<String>, unclassifiedName: String) {
        if (uris.isEmpty()) return
        val stillClassified = uris.chunked(500)
            .flatMap { chunk -> photoItemDao.getClassifiedFileUris(chunk) }
            .toSet()
        val orphanUris = uris.filter { it !in stillClassified }
        if (orphanUris.isEmpty()) return

        val unclassified = photoListDao.getUnclassifiedList()
            ?: run {
                val id = photoListDao.insertList(
                    PhotoList(name = unclassifiedName, isUnclassified = true)
                )
                photoListDao.getListById(id)!!
            }
        orphanUris.chunked(500).forEach { chunk ->
            photoItemDao.insertAll(
                chunk.map { uriString ->
                    PhotoItem(
                        listId          = unclassified.id,
                        fileUri         = uriString,
                        displayName     = uriString.toUri().lastPathSegment ?: uriString,
                        similarityScore = 0f,
                    )
                }
            )
        }
    }

    // ── 미분류 목록 관리 ───────────────────────────────────────────────────────

    /**
     * 미분류 목록 생성/갱신
     *
     * 동작:
     * 1. 미분류 목록이 없으면 새로 생성
     * 2. 스캔 디렉토리 내 전체 이미지를 미분류 목록에 추가
     * 3. 일반 목록에 이미 분류된 파일은 미분류에서 즉시 제거
     *
     * ★ 목록이 없는 경우: 폴더 내 전체 파일을 미분류로 등록
     * ★ 목록이 있는 경우: 스캔 후 분류된 파일을 미분류에서 제거
     *
     * @param scanDirUri       탐색 폴더 URI
     * @param unclassifiedName 미분류 목록 이름 (다국어 대응)
     * @param onProgress       진행률 콜백 (0.0 ~ 1.0)
     */
    suspend fun buildUnclassifiedList(
        scanDirUri: Uri,
        unclassifiedName: String = "미분류",
        onProgress: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        // 1. 미분류 목록 확인/생성
        val unclassifiedList = photoListDao.getUnclassifiedList()
            ?: run {
                val id = photoListDao.insertList(
                    PhotoList(
                        name           = unclassifiedName,
                        isUnclassified = true,
                    )
                )
                photoListDao.getListById(id)!!
            }

        // 2. 스캔 디렉토리 내 전체 이미지 URI 수집
        val allImageUris = faceAnalyzer.listImagesInDirectory(context, scanDirUri)
        if (allImageUris.isEmpty()) return@withContext

        val total = allImageUris.size

        // 3. 기존 미분류 항목 초기화 후 전체 파일 등록 (유사도 0.0)
        photoItemDao.deleteByListId(unclassifiedList.id)
        allImageUris.forEachIndexed { idx, uri ->
            photoItemDao.insertAll(
                listOf(
                    PhotoItem(
                        listId          = unclassifiedList.id,
                        fileUri         = uri.toString(),
                        displayName     = uri.lastPathSegment ?: uri.toString(),
                        similarityScore = 0f,
                    )
                )
            )
            onProgress((idx + 1).toFloat() / total)
        }

        // 4. 이미 일반 목록에 분류된 파일은 미분류에서 제거
        pruneUnclassifiedInternal(unclassifiedList.id)
    }

    /**
     * 미분류 목록에서 일반 목록에 이미 분류된 파일 제거
     * - 스캔 완료 후 호출하여 미분류 목록을 최신 상태로 유지
     */
    suspend fun pruneUnclassified() = withContext(Dispatchers.IO) {
        val unclassified = photoListDao.getUnclassifiedList() ?: return@withContext
        pruneUnclassifiedInternal(unclassified.id)
    }

    /** 내부 구현 — 미분류 목록 ID를 직접 받아 정리 */
    private suspend fun pruneUnclassifiedInternal(unclassifiedListId: Long) {
        // 1. 미분류 목록의 파일 URI 전체 조회
        val allUris = photoItemDao.getFileUrisByListId(unclassifiedListId)
        if (allUris.isEmpty()) return

        // 2. 일반 목록에 분류된 URI 조회 (SQLite IN 절 999개 제한으로 청크 처리)
        val classifiedUris = allUris.chunked(500).flatMap { chunk ->
            photoItemDao.getClassifiedFileUris(chunk)
        }
        if (classifiedUris.isEmpty()) return

        // 3. 미분류 목록에서 분류된 파일 제거
        classifiedUris.chunked(500).forEach { chunk ->
            photoItemDao.removeFromUnclassified(unclassifiedListId, chunk)
        }
    }

    // ── 재분류 ────────────────────────────────────────────────────────────────

    /**
     * 선택된 사진들을 지정 목록들로 재분류
     *
     * 동작:
     * 1. 현재 목록(currentListId)에서 선택 항목 삭제
     * 2. 선택한 목록들에 각각 INSERT
     * 3. 재분류된 파일은 미분류 목록에서도 자동 제거
     *
     * @param itemIds       재분류할 PhotoItem ID 목록
     * @param currentListId 현재 분류된 목록 ID (여기서 삭제)
     * @param targetListIds 이동할 목록 ID 목록 (다중 선택 가능)
     */
    suspend fun reclassifyItems(
        itemIds: List<Long>,
        currentListId: Long,
        targetListIds: List<Long>,
    ) = withContext(Dispatchers.IO) {
        if (itemIds.isEmpty() || targetListIds.isEmpty()) return@withContext

        // 1. 원본 항목 정보 조회
        val sourceItems = photoItemDao.getItemsByIds(itemIds)
        if (sourceItems.isEmpty()) return@withContext

        // 2. 현재 목록에서 삭제
        photoItemDao.deleteByIds(itemIds)

        // 3. 선택한 목록들에 INSERT (id=0 으로 새 레코드 생성)
        targetListIds.forEach { targetListId ->
            val newItems = sourceItems.map { item ->
                item.copy(id = 0, listId = targetListId)
            }
            photoItemDao.insertAll(newItems)
        }

        // 4. 미분류 목록에서도 해당 URI 제거 (이제 분류된 파일이므로)
        val unclassified = photoListDao.getUnclassifiedList()
        if (unclassified != null) {
            val classifiedUris = sourceItems.map { it.fileUri }
            classifiedUris.chunked(500).forEach { chunk ->
                photoItemDao.removeFromUnclassified(unclassified.id, chunk)
            }
        }
    }

    // ── 핵심 스캔 로직 ─────────────────────────────────────────────────────────

    /**
     * 디렉토리를 스캔하여 목록별로 일치하는 사진을 DB에 저장
     *
     * ★ 중단 지원: 코루틴 isActive를 매 이미지마다 확인
     *   → 중단(취소)되더라도 그때까지 발견된 결과는 DB에 반영됨
     *   → 이미지마다 즉시 INSERT(배치 저장 X)하여 중단 시 손실 최소화
     *
     * @param lists      스캔할 목록들 (미분류 제외 일반 목록)
     * @param scanDirUri 탐색 대상 폴더 URI (SAF)
     * @param threshold  코사인 유사도 임계값
     * @param onProgress 진행 콜백 (progress 0~1, 처리 수, 전체 수)
     * @param onFound    사진 발견 콜백 (발견 수)
     */
    suspend fun scanAndClassify(
        lists: List<PhotoList>,
        scanDirUri: Uri,
        threshold: Float = COSINE_SIMILARITY_THRESHOLD,
        deleteExisting: Boolean = true,
        onProgress: (progress: Float, scanned: Int, total: Int) -> Unit = { _, _, _ -> },
        onFound: (count: Int) -> Unit = {},
    ) = withContext(Dispatchers.Default) {
        val targetLists = lists.filter { !it.isUnclassified }

        val listFeatures = targetLists.mapNotNull { list ->
            val feature  = faceFeatureDao.getFeatureByListId(list.id) ?: return@mapNotNull null
            val centroid = feature.toCentroid()
            if (centroid.isEmpty()) return@mapNotNull null
            android.util.Log.d("PhotoRepository",
                "목록 '${list.name}': 임베딩 ${feature.embeddingCount}개 → centroid 사용")
            list to centroid
        }
        if (listFeatures.isEmpty()) return@withContext

        val imageUris = faceAnalyzer.listImagesInDirectory(context, scanDirUri)
        if (imageUris.isEmpty()) return@withContext

        val total = imageUris.size

        // deleteExisting=true: 기존 분류 전체 삭제 후 재분류
        // deleteExisting=false: 이미 분류된 파일은 건너뜀
        if (deleteExisting) {
            listFeatures.forEach { (list, _) -> photoItemDao.deleteByListId(list.id) }
        }

        // deleteExisting=false일 때 이미 분류된 URI 집합 미리 수집
        val alreadyClassifiedUris: Set<String> = if (!deleteExisting) {
            imageUris.map { it.toString() }.chunked(500).flatMap { chunk ->
                photoItemDao.getClassifiedFileUris(chunk)
            }.toSet()
        } else emptySet()

        imageUris.forEachIndexed { index, uri ->
            if (!currentCoroutineContext().isActive) return@forEachIndexed

            // 이미 분류된 파일 건너뛰기
            if (!deleteExisting && uri.toString() in alreadyClassifiedUris) {
                onProgress((index + 1).toFloat() / total, index + 1, total)
                return@forEachIndexed
            }

            val embeddings = faceAnalyzer.extractFaceEmbeddings(context, uri)
            var foundInThisImage = 0

            embeddings.forEach { embedding ->
                // 얼굴 하나는 '가장 유사한 목록 1개'에만 분류 (닮은 목록에 중복 저장 방지)
                // 단체 사진은 얼굴마다 각자 가장 유사한 목록으로 분류되므로 여러 목록에 들어갈 수 있음
                val best = listFeatures
                    .map { (list, centroid) -> list to cosineSimilarity(embedding, centroid) }
                    .maxByOrNull { it.second }

                // [디버그] 임계값 튜닝용 — 각 얼굴의 최고 유사도 기록 (디버그 빌드에서만)
                if (BuildConfig.DEBUG && best != null) {
                    android.util.Log.d("PhotoRepository",
                        "유사도 ${String.format(java.util.Locale.US, "%.3f", best.second)}" +
                                " (임계값 $threshold) 목록='${best.first.name}' 파일=${uri.lastPathSegment}")
                }

                if (best != null && best.second >= threshold) {
                    val (list, similarity) = best
                    photoItemDao.insertAll(
                        listOf(
                            PhotoItem(
                                listId          = list.id,
                                fileUri         = uri.toString(),
                                displayName     = uri.lastPathSegment ?: uri.toString(),
                                similarityScore = similarity,
                            )
                        )
                    )
                    foundInThisImage++
                }
            }

            if (foundInThisImage > 0) onFound(foundInThisImage)
            onProgress((index + 1).toFloat() / total, index + 1, total)
        }

        listFeatures.forEach { (list, _) ->
            photoListDao.updateList(
                list.copy(
                    isScanned     = true,
                    lastScannedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    companion object {
        const val COSINE_SIMILARITY_THRESHOLD = 0.75f

        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size || a.isEmpty()) return 0f
            var dotProduct = 0f
            var normA = 0f
            var normB = 0f
            for (i in a.indices) {
                dotProduct += a[i] * b[i]
                normA      += a[i] * a[i]
                normB      += b[i] * b[i]
            }
            val denominator = Math.sqrt((normA * normB).toDouble()).toFloat()
            return if (denominator == 0f) 0f else dotProduct / denominator
        }
    }
}