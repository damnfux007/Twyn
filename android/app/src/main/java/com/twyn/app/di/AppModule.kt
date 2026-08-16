package com.twyn.app.di

import android.content.Context
import com.twyn.app.data.local.database.TwynDatabase
import com.twyn.app.data.remote.websocket.TwynWebSocketClient
import com.twyn.app.encryption.EncryptionManager
import com.twyn.app.util.PreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt dependency injection module.
 * Provides singletons for database, WebSocket client, encryption, etc.
 *
 * The server URL is read from PreferencesManager so users can configure it
 * from the Settings screen after installing the APK.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TwynDatabase {
        return TwynDatabase.create(context)
    }

    @Provides
    fun provideMessageDao(database: TwynDatabase) = database.messageDao()

    @Provides
    fun providePairingDao(database: TwynDatabase) = database.pairingDao()

    @Provides
    fun provideUserDao(database: TwynDatabase) = database.userDao()

    @Provides
    fun provideEncryptionSessionDao(database: TwynDatabase) = database.encryptionSessionDao()

    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager {
        return PreferencesManager(context)
    }

    @Provides
    @Singleton
    fun provideWebSocketClient(prefs: PreferencesManager): TwynWebSocketClient {
        return TwynWebSocketClient(prefs.serverUrl)
    }

    @Provides
    @Singleton
    fun provideEncryptionManager(@ApplicationContext context: Context): EncryptionManager {
        return EncryptionManager(context)
    }
}
