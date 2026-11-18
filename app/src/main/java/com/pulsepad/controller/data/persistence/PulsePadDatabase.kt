package com.pulsepad.controller.data.persistence

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    /** Newest first, which is the order the profiles list shows them in. */
    @Query("SELECT * FROM profiles ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun findById(id: Long): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE controllerFamilyKey = :familyKey ORDER BY updatedAtEpochMs DESC LIMIT 1")
    suspend fun findLatestForFamily(familyKey: String): ProfileEntity?

    @Query("SELECT COUNT(*) FROM profiles WHERE name = :name")
    suspend fun countWithName(name: String): Int

    @Query("SELECT * FROM profiles WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/**
 * Profile storage.
 *
 * `exportSchema` is on so the schema lands in the `schemas` directory and a future
 * migration can be written against a checked-in baseline rather than guesswork.
 */
@Database(
    entities = [ProfileEntity::class],
    version = 1,
    exportSchema = true
)
abstract class PulsePadDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
}
