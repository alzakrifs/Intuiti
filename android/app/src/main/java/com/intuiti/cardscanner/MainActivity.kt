package com.intuiti.cardscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.intuiti.cardscanner.ui.CardScannerApp
import com.intuiti.cardscanner.ui.theme.CardScannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CardScannerTheme {
                CardScannerApp()
            }
        }
    }
}
