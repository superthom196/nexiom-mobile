package io.github.superthom196.nexiom

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.superthom196.nexiom.ui.App

class NexiomApplication : Application() {
    val platform: Platform by lazy { AndroidPlatform(this) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val platform = (application as NexiomApplication).platform
        setContent {
            App(platform)
        }
    }
}
