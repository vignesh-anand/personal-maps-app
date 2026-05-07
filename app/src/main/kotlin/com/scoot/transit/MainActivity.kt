package com.scoot.transit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.scoot.transit.ui.ScootApp
import com.scoot.transit.ui.theme.ScootTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ScootTheme {
                ScootApp(deepLinkIntent = intent)
            }
        }
    }
}
