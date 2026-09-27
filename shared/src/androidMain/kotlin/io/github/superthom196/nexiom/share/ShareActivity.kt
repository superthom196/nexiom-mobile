package io.github.superthom196.nexiom.share

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.superthom196.nexiom.AndroidPlatform
import io.github.superthom196.nexiom.Destination
import io.github.superthom196.nexiom.ShareModel
import io.github.superthom196.nexiom.ui.NexiomTheme
import io.github.superthom196.nexiom.ui.ShareScreen

/** Nexiom in the share sheet: a folder picker, then the upload goes on in [UploadService]. */
class ShareActivity : ComponentActivity() {

    private var pending: (() -> Unit)? = null

    // Android 13+ asks before an app shows notifications; the upload goes ahead either way.
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending?.invoke()
        pending = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val uris = sharedUris(intent)
        val names = uris.map { SharedFiles.describe(this, it).name }
        val platform = AndroidPlatform(applicationContext)
        setContent {
            val model = viewModel { ShareModel(platform, platform.newHttpClient()) }
            NexiomTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ShareScreen(
                        model = model,
                        fileNames = names,
                        onSend = { to, sending -> send(uris, to, sending) },
                        onClose = ::finish,
                        onOpenApp = ::openApp,
                    )
                }
            }
        }
    }

    private fun send(uris: List<Uri>, to: Destination, sending: String) {
        val go = {
            UploadService.start(this, uris, to)
            Toast.makeText(this, sending, Toast.LENGTH_SHORT).show()
            finish()
        }
        val needsAsking = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsking) {
            pending = go
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            go()
        }
    }

    private fun openApp() {
        packageManager.getLaunchIntentForPackage(packageName)?.let { startActivity(it) }
        finish()
    }

    @Suppress("DEPRECATION") // The typed getters are Android 13+; this app starts at 12.
    private fun sharedUris(intent: Intent): List<Uri> {
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else -> emptyList()
        }
        val clip = intent.clipData
        val fromClip = if (clip == null) emptyList() else (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        return (uris.ifEmpty { fromClip }).distinct()
    }
}
