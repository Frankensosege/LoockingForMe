package com.frankensosege.loockingforme.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 분류된 사진 메타데이터 엔티티
 * - 코사인 유사도 기준치를 통과한 사진의 파일 정보를 저장
 */
@Entity(
    tableName = "photo_items",
    foreignKeys = [
        ForeignKey(
            entity        = PhotoList::class,
            parentColumns = ["id"],
            childColumns  = ["list_id"],
            onDelete      = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["list_id"]),
        // file_uri 단독 unique 제거 → (list_id + file_uri) 복합 unique로 변경
        // 같은 사진이 서로 다른 목록에는 저장 가능, 같은 목록에 중복만 방지
        Index(value = ["list_id", "file_uri"], unique = true),
    ],
)
data class PhotoItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 연결된 목록 ID */
    @ColumnInfo(name = "list_id")
    val listId: Long,

    /** 파일 URI (SAF or MediaStore URI 문자열) */
    @ColumnInfo(name = "file_uri")
    val fileUri: String,

    /** 폴더를 포함한 파일명 (표시용) */
    @ColumnInfo(name = "display_name")
    val displayName: String,

    /** 코사인 유사도 점수 (0.0 ~ 1.0) */
    @ColumnInfo(name = "similarity_score")
    val similarityScore: Float,

    /** 추가 타임스탬프 */
    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis(),
)
