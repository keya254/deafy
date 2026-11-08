package tech.briankeya.opencaption.phone.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/** One recognized caption line. Stored on the phone; the watch fetches pages on demand. */
@Entity(tableName = "captions", indices = [Index("ts")])
data class CaptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val ts: Long,
)

@Dao
interface CaptionDao {
    @Insert
    suspend fun insert(line: CaptionEntity)

    /** Newest lines first. Used for the phone screen and the first watch page. */
    @Query("SELECT * FROM captions ORDER BY ts DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<CaptionEntity>

    /** Lines strictly older than [before], newest first. Used for watch scrollback. */
    @Query("SELECT * FROM captions WHERE ts < :before ORDER BY ts DESC LIMIT :limit")
    suspend fun before(before: Long, limit: Int): List<CaptionEntity>

    /** Lines strictly newer than [since], oldest first. Used for watch catch-up after a gap. */
    @Query("SELECT * FROM captions WHERE ts > :since ORDER BY ts ASC LIMIT :limit")
    suspend fun after(since: Long, limit: Int): List<CaptionEntity>

    @Query("SELECT COUNT(*) FROM captions")
    suspend fun count(): Int

    /** Deletes the whole conversation history. */
    @Query("DELETE FROM captions")
    suspend fun deleteAll()
}

@Database(entities = [CaptionEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun captions(): CaptionDao
}
