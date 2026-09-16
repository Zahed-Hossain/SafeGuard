package com.safeguard.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BlockedDomain::class,
        WhitelistDomain::class,
        BlockEvent::class,
        SettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SafeGuardDatabase : RoomDatabase() {

    abstract fun blockedDomainDao(): BlockedDomainDao
    abstract fun whitelistDao(): WhitelistDao
    abstract fun blockEventDao(): BlockEventDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: SafeGuardDatabase? = null

        fun getInstance(context: Context): SafeGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SafeGuardDatabase::class.java,
                    "safeguard_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
