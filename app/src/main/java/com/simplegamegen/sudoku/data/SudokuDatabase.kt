package com.simplegamegen.sudoku.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/** Single-row autosave (id is always 1): the current unfinished game. */
@Entity(tableName = "saved_games")
data class SavedGameEntity(
    @PrimaryKey val id: Int = 1,
    val variant: String,
    val size: Int,
    val boxRows: Int,
    val boxCols: Int,
    val difficulty: String,
    val seed: Long,
    val givens: String,
    val solution: String,
    val current: String,
    val notes: String,
    val regions: String?,
    val cages: String?,
    val thermos: String?,
    val dots: String?,
    val arrows: String?,
    val sandwichTop: String?,
    val sandwichBottom: String?,
    val sandwichLeft: String?,
    val sandwichRight: String?,
    val elapsedSeconds: Int,
    val hintsUsed: Int,
    val updatedAt: Long,
)

@Dao
interface SavedGameDao {
    @Query("SELECT * FROM saved_games WHERE id = 1")
    suspend fun get(): SavedGameEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SavedGameEntity)

    @Query("DELETE FROM saved_games WHERE id = 1")
    suspend fun clear()
}

@Database(entities = [SavedGameEntity::class], version = 1, exportSchema = false)
abstract class SudokuDatabase : RoomDatabase() {
    abstract fun savedGameDao(): SavedGameDao
}
