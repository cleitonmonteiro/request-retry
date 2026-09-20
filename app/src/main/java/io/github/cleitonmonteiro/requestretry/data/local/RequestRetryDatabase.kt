package io.github.cleitonmonteiro.requestretry.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [OrderOperationEntity::class], version = 1, exportSchema = true)
abstract class RequestRetryDatabase : RoomDatabase() {
    abstract fun orderOperationDao(): OrderOperationDao
}
