package com.frankensosege.loockingforme.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 사진 분류 목록 엔티티
 * - 메인 화면에서 '+' 버튼으로 추가되는 목록
 */
@Entity(tableName = "photo_lists")
data class PhotoList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_scanned_at") val lastScannedAt: Long = 0L,
    @ColumnInfo(name = "is_scanned") val isScanned: Boolean = false,
    @ColumnInfo(name = "is_unclassified") val isUnclassified: Boolean = false,
)