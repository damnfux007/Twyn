package com.twyn.app.ui.theme

import androidx.compose.ui.graphics.Color

// Apple-inspired palette for dark mode
val Primary = Color(0xFF0A84FF)           // iOS blue
val PrimaryDark = Color(0xFF0066CC)
val PrimaryLight = Color(0xFF5AC8FA)      // iOS light blue
val Secondary = Color(0xFF30D158)         // iOS green
val SecondaryLight = Color(0xFF34C759)

// Backgrounds (iOS-style dark)
val Background = Color(0xFF000000)        // True black (OLED)
val Surface = Color(0xFF1C1C1E)           // iOS grouped background
val SurfaceLight = Color(0xFF2C2C2E)      // iOS secondary bg
val SurfaceVariant = Color(0xFF3A3A3C)    // iOS tertiary bg

val OnPrimary = Color.White
val OnSecondary = Color.White
val OnBackground = Color(0xFFFFFFFF)
val OnSurface = Color(0xFFFFFFFF)
val OnSurfaceVariant = Color(0xFF8E8E93)  // iOS secondary label

// Chat bubbles (iOS-style)
val BubbleSent = Color(0xFF0A84FF)        // iOS blue for sent
val BubbleReceived = Color(0xFF2C2C2E)    // iOS dark for received
val BubbleSentText = Color.White
val BubbleReceivedText = Color(0xFFFFFFFF)

// iOS system colors
val OnlineGreen = Color(0xFF30D158)
val UnreadBadge = Color(0xFFFF453A)       // iOS red
val WarningOrange = Color(0xFFFF9F0A)     // iOS orange

// Light theme
val LightPrimary = Color(0xFF007AFF)      // iOS blue light
val LightBackground = Color(0xFFF2F2F7)   // iOS grouped bg light
val LightSurface = Color.White
val LightOnBackground = Color(0xFF1C1C1E)
val LightOnSurface = Color(0xFF1C1C1E)
val LightBubbleSent = Color(0xFF007AFF)
val LightBubbleReceived = Color(0xFFE9E9EB)
val LightBubbleSentText = Color.White
val LightBubbleReceivedText = Color(0xFF1C1C1E)

// Chat theme presets (user-selectable)
object ChatThemes {
    data class BubbleColors(
        val sentBg: Color,
        val sentText: Color,
        val receivedBg: Color,
        val receivedText: Color,
        val name: String
    )

    val blue = BubbleColors(
        Color(0xFF0A84FF), Color.White,
        Color(0xFF2C2C2E), Color.White,
        "Blue (Default)"
    )
    val green = BubbleColors(
        Color(0xFF30D158), Color.White,
        Color(0xFF1C1C1E), Color.White,
        "Green"
    )
    val purple = BubbleColors(
        Color(0xFFBF5AF2), Color.White,
        Color(0xFF2C2C2E), Color.White,
        "Purple"
    )
    val orange = BubbleColors(
        Color(0xFFFF9F0A), Color.White,
        Color(0xFF2C2C2E), Color.White,
        "Orange"
    )
    val pink = BubbleColors(
        Color(0xFFFF375F), Color.White,
        Color(0xFF2C2C2E), Color.White,
        "Pink"
    )

    val all = listOf(blue, green, purple, orange, pink)
}
