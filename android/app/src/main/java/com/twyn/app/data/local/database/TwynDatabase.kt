package com.twyn.app.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.twyn.app.data.local.dao.MessageDao
import com.twyn.app.data.local.dao.PairingDao
import com.twyn.app.data.local.dao.UserDao
import com.twyn.app.data.local.dao.EncryptionSessionDao
import com.twyn.app.data.local.entity.MessageEntity
import com.twyn.app.data.local.entity.PairingEntity
import com.twyn.app.data.local.entity.UserEntity
import com.twyn.app.data.local.entity.EncryptionSessionEntity

/**
 * Room database for Twyn.
 * Stores messages, pairings, user profiles, and encryption session state locally.
 * All message content is stored encrypted.
 */
@Database(
    entities = [
        MessageEntity::class,
        PairingEntity::class,
        UserEntity::class,
        EncryptionSessionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TwynDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun pairingDao(): PairingDao
    abstract fun userDao(): UserDao
    abstract fun encryptionSessionDao(): EncryptionSessionDao

    companion object {
        fun create(context: Context): TwynDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                TwynDatabase::class.java,
                "twyn_database"
            ).build()
        }
    }
}
