package com.deafy.phone

import android.content.Context
import androidx.room.Room
import com.deafy.phone.db.AppDatabase
import com.deafy.phone.db.CaptionEntity
import com.deafy.shared.StoredLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Caption history on the phone, in a Room (SQLite) database. Holds the full conversation,
 * so it can grow large. The watch keeps only a recent window and fetches older pages from here.
 */
object History {
    private lateinit var db: AppDatabase
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "deafy.db").build()
    }

    fun isReady(): Boolean = ::db.isInitialized

    /** Saves a finished caption line. Runs off the main thread. */
    fun append(text: String, ts: Long) {
        if (!isReady()) return
        io.launch { db.captions().insert(CaptionEntity(text = text, ts = ts)) }
    }

    suspend fun latest(limit: Int): List<StoredLine> =
        db.captions().latest(limit).reversed().map { StoredLine(it.text, it.ts) }

    /** Up to [limit] lines older than [before], oldest first, plus whether more exist. */
    suspend fun page(before: Long, limit: Int): Pair<List<StoredLine>, Boolean> {
        val rows = db.captions().before(before, limit + 1)
        val hasMore = rows.size > limit
        val lines = rows.take(limit).reversed().map { StoredLine(it.text, it.ts) }
        return lines to hasMore
    }

    suspend fun after(since: Long, limit: Int): List<StoredLine> =
        db.captions().after(since, limit).map { StoredLine(it.text, it.ts) }
}
