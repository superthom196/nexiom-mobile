package io.github.superthom196.nexiom.share

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.provider.OpenableColumns
import io.github.superthom196.nexiom.AndroidPlatform
import io.github.superthom196.nexiom.Destination
import io.github.superthom196.nexiom.Outgoing
import io.github.superthom196.nexiom.SessionStore
import io.github.superthom196.nexiom.UploadResult
import io.github.superthom196.nexiom.Uploader
import io.github.superthom196.nexiom.api.NexiomApi
import io.github.superthom196.nexiom.api.SignedOut
import io.github.superthom196.nexiom.api.Unreachable
import io.github.superthom196.nexiom.resources.Res
import io.github.superthom196.nexiom.resources.upload_channel
import io.github.superthom196.nexiom.resources.upload_done
import io.github.superthom196.nexiom.resources.upload_done_one
import io.github.superthom196.nexiom.resources.upload_failed
import io.github.superthom196.nexiom.resources.upload_file
import io.github.superthom196.nexiom.resources.upload_partly
import io.github.superthom196.nexiom.resources.upload_sending
import io.github.superthom196.nexiom.resources.upload_signed_out
import io.github.superthom196.nexiom.resources.upload_unreachable
import io.github.superthom196.nexiom.resources.upload_why
import io.github.superthom196.nexiom.safeName
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getString
import java.io.FileNotFoundException

/** A shared file's name (as the box takes it) and size. */
data class SharedFile(val name: String, val size: Long?)

object SharedFiles {
    fun describe(context: Context, uri: Uri): SharedFile {
        var name: String? = null
        var size: Long? = null
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        name = c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(c::getString)
                        size = c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getLong)
                    }
                }
        }
        return SharedFile(safeName(name ?: uri.lastPathSegment.orEmpty()), size)
    }
}

/**
 * Sends shared files in the background, one batch after another, with a progress notification.
 * Each batch's files come as the start intent's ClipData with read permission, which Android keeps
 * for the service until that start is stopped.
 */
class UploadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val oneAtATime = Mutex()
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }
    private val platform by lazy { AndroidPlatform(applicationContext) }
    private val client by lazy { platform.newHttpClient() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val to = intent?.getStringExtra(EXTRA_TO)?.let { runCatching { Json.decodeFromString<Destination>(it) }.getOrNull() }
        val clip = intent?.clipData
        if (to == null || clip == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        ensureChannel()
        val first = runBlocking { getString(Res.string.upload_sending, to.label) }
        startForeground(PROGRESS_ID, progress(first, "", 0, 0), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        scope.launch {
            oneAtATime.withLock { upload(uris, to) }
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private suspend fun upload(uris: List<Uri>, to: Destination) {
        val title = getString(Res.string.upload_sending, to.label)
        val token = SessionStore(platform.store).load()?.token
        if (token == null) {
            finished(getString(Res.string.upload_failed, to.label), getString(Res.string.upload_signed_out))
            return
        }
        val files = uris.map { uri ->
            val shared = SharedFiles.describe(this, uri)
            Outgoing(shared.name, shared.size) {
                (contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())).toByteReadChannel()
            }
        }
        val lines = files.mapIndexed { i, file -> getString(Res.string.upload_file, file.name, i + 1, files.size) }
        var lastPercent = -1
        var lastIndex = -1
        val result = Uploader(NexiomApi(client)).send(token, to, files) { index, sent ->
            val size = files[index].size
            val percent = if (size != null && size > 0) (100 * sent / size).toInt() else 0
            if (index != lastIndex || percent != lastPercent) {
                lastIndex = index
                lastPercent = percent
                notifications.notify(PROGRESS_ID, progress(title, lines[index], percent, if (size == null) -1 else 100))
            }
        }
        report(result, files.size, to)
    }

    private suspend fun report(result: UploadResult, total: Int, to: Destination) {
        val title = when {
            result.failed.isEmpty() && total == 1 -> getString(Res.string.upload_done_one, result.sent.single(), to.label)
            result.failed.isEmpty() -> getString(Res.string.upload_done, total, to.label)
            result.sent.isEmpty() -> getString(Res.string.upload_failed, to.label)
            else -> getString(Res.string.upload_partly, result.sent.size, total, to.label)
        }
        val why = result.failed.firstOrNull()?.let { (name, e) ->
            val reason = when (e) {
                is SignedOut -> getString(Res.string.upload_signed_out)
                is Unreachable -> getString(Res.string.upload_unreachable)
                else -> e.message.orEmpty()
            }
            getString(Res.string.upload_why, name, reason)
        }
        finished(title, why)
    }

    private fun finished(title: String, text: String?) {
        val note = Notification.Builder(this, CHANNEL)
            .setSmallIcon(if (text == null) android.R.drawable.stat_sys_upload_done else android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .apply { if (text != null) setContentText(text).setStyle(Notification.BigTextStyle().bigText(text)) }
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        notifications.notify(nextDoneId++, note)
    }

    private fun progress(title: String, text: String, value: Int, max: Int): Notification =
        Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(maxOf(max, 0), value, max < 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .build()

    private fun openApp(): PendingIntent? =
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

    private fun ensureChannel() {
        val name = runBlocking { getString(Res.string.upload_channel) }
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, name, NotificationManager.IMPORTANCE_LOW))
    }

    /** Android 15 limits background data syncs to six hours a day. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        scope.cancel()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        client.close()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "uploads"
        private const val PROGRESS_ID = 1
        private const val EXTRA_TO = "to"
        private var nextDoneId = 100

        fun start(context: Context, uris: List<Uri>, to: Destination) {
            if (uris.isEmpty()) return
            val clip = ClipData.newRawUri("", uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            val intent = Intent(context, UploadService::class.java)
                .putExtra(EXTRA_TO, Json.encodeToString(to))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = clip
            context.startForegroundService(intent)
        }
    }
}
