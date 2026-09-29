package com.simplegamegen.sudoku.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.simplegamegen.sudoku.wordplay.EnglishLexicon
import com.simplegamegen.sudoku.wordplay.LexiconQuery
import com.simplegamegen.sudoku.wordplay.SqlEnglishLexicon
import java.io.File
import java.security.MessageDigest

/**
 * Opens the generated English lexicon from assets, copying it into the cache once per checksum.
 * The database is read-only. [shared] keeps one connection for the process; [open] returns a new one
 * the caller should [EnglishLexicon.close].
 */
object EnglishLexiconAsset {
    private val gate = Any()
    @Volatile private var cached: EnglishLexicon? = null

    fun shared(context: Context): EnglishLexicon {
        cached?.let { return it }
        synchronized(gate) {
            cached?.let { return it }
            return open(context).also { cached = it }
        }
    }

    fun open(context: Context): EnglishLexicon {
        val file = install(context.applicationContext)
        val sqlite = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return SqlEnglishLexicon(AndroidLexiconQuery(sqlite))
    }

    private fun install(context: Context): File = synchronized(gate) {
        val cache = File(context.cacheDir, "lexicon").apply { mkdirs() }
        val db = File(cache, "lexicon.db")
        val stamp = File(cache, "lexicon.sha256")
        val expected = context.assets.open("lexicon/lexicon.sha256").bufferedReader().use { it.readText().trim() }
        val current = if (db.isFile && db.length() > 0L && stamp.isFile) stamp.readText().trim() else ""
        if (current == expected) return db
        val tmp = File(cache, "lexicon.db.partial")
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open("lexicon/lexicon.db").use { input ->
            tmp.outputStream().use { output ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != expected) {
            tmp.delete()
            error("English lexicon asset does not match its checksum")
        }
        if (db.exists() && !db.delete()) error("Could not replace the cached English lexicon")
        if (!tmp.renameTo(db)) {
            tmp.copyTo(db, overwrite = true)
            tmp.delete()
        }
        stamp.writeText(expected + "\n")
        db
    }
}

private class AndroidLexiconQuery(private val db: SQLiteDatabase) : LexiconQuery {
    override fun query(sql: String, vararg args: String): List<Map<String, String?>> =
        db.rawQuery(sql, args).use { cursor ->
            val names = Array(cursor.columnCount) { cursor.getColumnName(it) }
            val rows = ArrayList<Map<String, String?>>()
            while (cursor.moveToNext()) {
                val row = HashMap<String, String?>(names.size)
                for (i in names.indices) row[names[i]] = if (cursor.isNull(i)) null else cursor.getString(i)
                rows += row
            }
            rows
        }

    override fun close() {
        db.close()
    }
}
