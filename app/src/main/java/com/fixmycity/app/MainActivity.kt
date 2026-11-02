package com.fixmycity.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fixmycity.app.ui.navigation.NavGraph
import com.fixmycity.app.ui.theme.FixMyCityTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by remember { mutableStateOf(false) }
            FixMyCityTheme(darkTheme = isDarkTheme) {
                NavGraph(onThemeToggle = { isDarkTheme = !isDarkTheme }, isDarkTheme = isDarkTheme)
            }
        }
    }
}