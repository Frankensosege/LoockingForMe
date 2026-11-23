package com.frankensosege.loockingforme.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.frankensosege.loockingforme.data.model.FaceFeature
import com.frankensosege.loockingforme.data.model.PhotoItem
import com.frankensosege.loockingforme.data.model.PhotoList
import kotlinx.coroutines.flow.Flow

// ─── DAOs ─────────────────────────────────────────────────────────────────────

@Dao
interface PhotoListDao {

    @Query("SELECT * FROM photo_lists ORDER BY is_unclassified ASC, created_at DESC")
    fun getAllLists(): Flow<List<PhotoList>>

    @Query("SELECT * FROM photo_lists WHERE id = :id LIMIT 1")
    suspend fun getListById(id: Long): PhotoList?

    /** 미분류 목록 조회 */
    @Query("SELECT * FROM photo_lists WHERE is_unclassified = 1 LIMIT 1")
    suspend fun getUnclassifiedList(): PhotoList?

    /** 일반 목록(미분류 제외) 전체 조회 — 재분류 목록 선택용 */
    @Query("SELECT * FROM photo_lists WHERE is_unclassified = 0 ORDER BY created_at DESC")
    suspend fun getClassifiedLists(): List<PhotoList>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: PhotoList): Long

    @Update
    suspend fun updateList(list: PhotoList)

    @Delete
    suspend fun deleteList(list: PhotoList)
}

@Dao
interface PhotoItemDao {

    @Query("SELECT * FROM photo_items WHERE list_id = :listId ORDER BY similarity_score DESC")
    fun getItemsByListId(listId: Long): Flow<List<PhotoItem>>

    @Query("SELECT COUNT(*) FROM photo_items WHERE list_id = :listId")
    fun countByListIdFlow(listId: Long): Flow<Int>

    /** 특정 파일 URI들이 일반 목록에 하나라도 존재하는지 확인 */
    @Query("""
        SELECT DISTINCT pi.file_uri 
        FROM photo_items pi
        INNER JOIN photo_lists pl ON pi.list_id = pl.id
        WHERE pl.is_unclassified = 0
          AND pi.file_uri IN (:fileUris)
    """)
    suspend fun getClassifiedFileUris(fileUris: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PhotoItem>)

    @Query("DELETE FROM photo_items WHERE list_id = :listId")
    suspend fun deleteByListId(listId: Long)

    @Query("DELETE FROM photo_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** 미분류 목록에서 분류된 파일 제거 */
    @Query("""
        DELETE FROM photo_items 
        WHERE list_id = :unclassifiedListId 
          AND file_uri IN (:classifiedUris)
    """)
    suspend fun removeFromUnclassified(unclassifiedListId: Long, classifiedUris: List<String>)

    /** 선택 항목 조회 (재분류용) */
    @Query("SELECT * FROM photo_items WHERE id IN (:ids)")
    suspend fun getItemsByIds(ids: List<Long>): List<PhotoItem>

    /** 특정 목록의 모든 파일 URI 조회 (미분류 정리용) */
    @Query("SELECT file_uri FROM photo_items WHERE list_id = :listId")
    suspend fun getFileUrisByListId(listId: Long): List<String>
}

@Dao
interface FaceFeatureDao {

    @Query("SELECT * FROM face_features WHERE list_id = :listId LIMIT 1")
    suspend fun getFeatureByListId(listId: Long): FaceFeature?

    @Query("SELECT * FROM face_features WHERE list_id = :listId LIMIT 1")
    fun getFeatureByListIdFlow(listId: Long): Flow<FaceFeature?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeature(feature: FaceFeature): Long

    @Query("DELETE FROM face_features WHERE list_id = :listId")
    suspend fun deleteByListId(listId: Long)
}

// ─── Database ─────────────────────────────────────────────────────────────────

/**
 * 앱 메인 Room DB (v7)
 * v6 → v7: photo_lists.source_dir_uri 컬럼 삭제.
 *   개발 단계이므로 마이그레이션 없이 fallbackToDestructiveMigration()으로 재생성한다.
 *   (기존 설치본의 목록/얼굴/분류 결과는 초기화됨)
 */
@Database(
    entities     = [PhotoList::class, PhotoItem::class, FaceFeature::class],
    version      = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun photoListDao(): PhotoListDao
    abstract fun photoItemDao(): PhotoItemDao
    abstract fun faceFeatureDao(): FaceFeatureDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lfmf_app.db",
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}