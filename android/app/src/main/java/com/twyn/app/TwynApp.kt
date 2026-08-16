package com.twyn.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Twyn Application class.
 * Enables Hilt dependency injection across the entire app.
 */
@HiltAndroidApp
class TwynApp : Application()
