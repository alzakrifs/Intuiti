package com.intuiti.cardscanner

import android.app.Application
import com.intuiti.cardscanner.data.SettingsRepository

class CardScannerApplication : Application() {
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
}
