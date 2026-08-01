package ka.kitool.save

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern
import ka.kitool.R

class SaveService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val jobs = HashSet<CopyJob>()
    private var cancellationStarted = false

    @Volatile
    private var latestStartId = 0

    @Volatile
    private var stopping = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val uris =
            if (intent?.action == ACTION_SAVE) {
                readUris(intent)
            } else {
                emptyList()
            }
        val job = CopyJob()
        jobs.add(job)

        try {
            startForeground(
                NOTIFICATION_ID,
                createProgressNotification(current = 0, total = uris.size.coerceAtLeast(1)),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
            executor.execute { runJob(job, uris) }
        } catch (_: RuntimeException) {
            jobs.remove(job)
            showToast(R.string.save_error_start)
            finishIfIdle()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopping = true
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Toast.makeText(this, R.string.save_error_timeout, Toast.LENGTH_LONG).show()
        cancelJobsAsync()
    }

    override fun onDestroy() {
        stopping = true
        cancelJobsAsync()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun runJob(job: CopyJob, uris: List<Uri>) {
        var savedCount = 0
        val outcome =
            try {
                job.begin()
                if (uris.isEmpty()) {
                    CopyOutcome(savedCount = 0, attemptedCount = 0)
                } else {
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    for (index in uris.indices) {
                        job.checkCancelled()
                        val uri = uris[index]
                        val source = readMetadata(job, uri, index)
                        notifyProgress(
                            job = job,
                            itemIndex = index + 1,
                            itemCount = uris.size,
                            copied = 0,
                            total = source.size,
                            force = true,
                        )
                        if (
                            copyOne(
                                job = job,
                                source = source,
                                buffer = buffer,
                                itemIndex = index + 1,
                                itemCount = uris.size,
                            )
                        ) {
                            savedCount += 1
                        }
                    }
                    CopyOutcome(savedCount, uris.size)
                }
            } catch (_: CopyCancelledException) {
                null
            } catch (_: Exception) {
                CopyOutcome(savedCount, attemptedCount = uris.size)
            } finally {
                job.end()
            }

        mainHandler.post {
            if (stopping) return@post
            jobs.remove(job)
            if (outcome != null) showOutcome(outcome)
            finishIfIdle()
        }
    }

    private fun finishIfIdle() {
        if (jobs.isNotEmpty()) return
        if (stopSelfResult(latestStartId)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun cancelJobsAsync() {
        executor.shutdownNow()
        if (cancellationStarted) return
        cancellationStarted = true

        val jobsToCancel = jobs.toTypedArray()
        jobs.clear()
        Thread(
            {
                jobsToCancel.forEach { runCatching { it.cancel() } }
            },
            "Kitool-save-cancel",
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun showOutcome(outcome: CopyOutcome) {
        val text =
            if (outcome.savedCount == 0) {
                getString(R.string.save_result_failed)
            } else {
                val message =
                    if (outcome.savedCount == outcome.attemptedCount) {
                        R.string.save_result_complete
                    } else {
                        R.string.save_result_partial
                    }
                getString(
                    message,
                    outcome.savedCount,
                    outcome.attemptedCount,
                    getString(R.string.downloads_root),
                )
            }
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    private fun showToast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    @Throws(CopyCancelledException::class)
    private fun copyOne(
        job: CopyJob,
        source: SourceMetadata,
        buffer: ByteArray,
        itemIndex: Int,
        itemCount: Int,
    ): Boolean {
        var destination: Uri? = null
        var input: InputStream? = null
        var output: OutputStream? = null
        return try {
            job.checkCancelled()
            input = openSource(job, source)
            input.use { sourceStream ->
                job.checkCancelled()
                destination =
                    contentResolver.insert(
                        MediaStore.Downloads.getContentUri(
                            MediaStore.VOLUME_EXTERNAL_PRIMARY
                        ),
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, source.displayName)
                            put(MediaStore.MediaColumns.MIME_TYPE, source.mimeType)
                            put(
                                MediaStore.MediaColumns.RELATIVE_PATH,
                                Environment.DIRECTORY_DOWNLOADS,
                            )
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        },
                    ) ?: throw IOException("MediaStore insert returned null")

                job.checkCancelled()
                output =
                    openDestination(
                        job,
                        destination,
                    )
                output.use { destinationStream ->
                    var copied = 0L
                    while (true) {
                        job.checkCancelled()
                        val read = sourceStream.read(buffer)
                        if (read < 0) break
                        destinationStream.write(buffer, 0, read)
                        copied += read
                        notifyProgress(
                            job = job,
                            itemIndex = itemIndex,
                            itemCount = itemCount,
                            copied = copied,
                            total = source.size,
                            force = false,
                        )
                    }
                    destinationStream.flush()
                }
                job.clearOutput(output)
            }
            job.clearInput(input)

            job.checkCancelled()
            val completedDestination =
                destination ?: throw IOException("MediaStore destination is null")
            val published =
                contentResolver.update(
                    completedDestination,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )
            if (published <= 0) throw IOException("MediaStore publish failed")
            job.checkCancelled()
            true
        } catch (cancelled: CopyCancelledException) {
            destination?.let { runCatching { contentResolver.delete(it, null, null) } }
            throw cancelled
        } catch (_: Exception) {
            destination?.let { runCatching { contentResolver.delete(it, null, null) } }
            if (job.isCancelled()) throw CopyCancelledException()
            false
        } finally {
            job.clearOutput(output)
            job.clearInput(input)
        }
    }

    @Throws(CopyCancelledException::class)
    private fun readMetadata(job: CopyJob, uri: Uri, index: Int): SourceMetadata {
        job.checkCancelled()
        var rawName: String? = null
        var size: Long? = null
        val signal = CancellationSignal()
        job.registerSignal(signal)
        try {
            contentResolver
                .query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null,
                    signal,
                )
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        rawName = cursor.getNullableString(OpenableColumns.DISPLAY_NAME)
                        size = cursor.getNullableLong(OpenableColumns.SIZE)
                    }
                }
        } catch (_: Exception) {
            job.checkCancelled()
        } finally {
            job.clearSignal(signal)
        }
        job.checkCancelled()

        val displayName =
            FileNamePolicy.sanitize(
                rawName ?: uri.lastPathSegment?.substringAfterLast('/'),
                "文件-${index + 1}",
            )
        val mimeType =
            runCatching { contentResolver.getType(uri) }
                .getOrNull()
                ?.takeIf(::isSafeMimeType)
                ?: DEFAULT_MIME_TYPE
        return SourceMetadata(uri, displayName, size, mimeType)
    }

    @Throws(CopyCancelledException::class)
    private fun openSource(job: CopyJob, source: SourceMetadata): InputStream {
        try {
            return openRawInput(job, source.uri)
        } catch (_: FileNotFoundException) {
            job.checkCancelled()
        }

        val signal = CancellationSignal()
        job.registerSignal(signal)
        return try {
            val descriptor =
                contentResolver.openTypedAssetFileDescriptor(
                    source.uri,
                    if (source.mimeType == DEFAULT_MIME_TYPE) "*/*" else source.mimeType,
                    null,
                    signal,
                ) ?: throw FileNotFoundException(source.uri.toString())
            try {
                descriptor.createInputStream().also(job::registerInput)
            } catch (failure: Exception) {
                runCatching { descriptor.close() }
                throw failure
            }
        } catch (failure: Exception) {
            job.checkCancelled()
            throw failure
        } finally {
            job.clearSignal(signal)
        }
    }

    @Throws(CopyCancelledException::class)
    private fun openRawInput(job: CopyJob, uri: Uri): InputStream {
        val signal = CancellationSignal()
        job.registerSignal(signal)
        return try {
            val descriptor =
                contentResolver.openAssetFileDescriptor(uri, "r", signal)
                    ?: throw FileNotFoundException(uri.toString())
            try {
                descriptor.createInputStream().also(job::registerInput)
            } catch (failure: Exception) {
                runCatching { descriptor.close() }
                throw failure
            }
        } catch (failure: Exception) {
            job.checkCancelled()
            throw failure
        } finally {
            job.clearSignal(signal)
        }
    }

    @Throws(CopyCancelledException::class)
    private fun openDestination(job: CopyJob, uri: Uri): OutputStream {
        val signal = CancellationSignal()
        job.registerSignal(signal)
        return try {
            val descriptor =
                contentResolver.openAssetFileDescriptor(uri, "w", signal)
                    ?: throw FileNotFoundException(uri.toString())
            try {
                descriptor.createOutputStream().also(job::registerOutput)
            } catch (failure: Exception) {
                runCatching { descriptor.close() }
                throw failure
            }
        } catch (failure: Exception) {
            job.checkCancelled()
            throw failure
        } finally {
            job.clearSignal(signal)
        }
    }

    private fun notifyProgress(
        job: CopyJob,
        itemIndex: Int,
        itemCount: Int,
        copied: Long,
        total: Long?,
        force: Boolean,
    ) {
        if (job.isCancelled() || stopping) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - job.lastNotificationUpdate < NOTIFICATION_UPDATE_INTERVAL_MS) return
        job.lastNotificationUpdate = now

        val hasTotal = total != null && total > 0
        val progress =
            if (hasTotal) ((copied.coerceAtMost(total) * 1000L) / total).toInt() else 0
        val notification =
            createProgressNotification(
                current = itemIndex,
                total = itemCount,
                indeterminate = !hasTotal,
                progress = progress,
            )
        runCatching {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        }
    }

    private fun createProgressNotification(
        current: Int,
        total: Int,
        indeterminate: Boolean = true,
        progress: Int = 0,
    ): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_saving_title))
            .setContentText(getString(R.string.notification_saving_message, current, total))
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(1000, progress, indeterminate)
            .build()

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.notification_channel_description) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun readUris(intent: Intent): List<Uri> {
        val clipData = intent.clipData ?: return emptyList()
        val result = ArrayList<Uri>(clipData.itemCount)
        val seen = HashSet<Uri>(clipData.itemCount)
        for (index in 0 until clipData.itemCount) {
            val uri = clipData.getItemAt(index).uri ?: continue
            if (!uri.scheme.equals("content", ignoreCase = true)) continue
            val normalized = uri.normalizeScheme()
            if (seen.add(normalized)) result.add(normalized)
        }
        return result
    }

    private fun Cursor.getNullableString(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun Cursor.getNullableLong(columnName: String): Long? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getLong(index).takeIf { it >= 0 } else null
    }

    private fun isSafeMimeType(value: String): Boolean =
        value.length <= 127 && MIME_TYPE.matcher(value).matches()

    private class CopyOutcome(
        val savedCount: Int,
        val attemptedCount: Int,
    )

    private class SourceMetadata(
        val uri: Uri,
        val displayName: String,
        val size: Long?,
        val mimeType: String,
    )

    private class CopyJob {
        private val cancelled = AtomicBoolean(false)
        private val input = AtomicReference<InputStream?>()
        private val output = AtomicReference<OutputStream?>()
        private val signal = AtomicReference<CancellationSignal?>()

        @Volatile
        var lastNotificationUpdate = 0L

        @Volatile
        private var worker: Thread? = null

        fun begin() {
            worker = Thread.currentThread()
            checkCancelled()
        }

        fun end() {
            worker = null
            input.set(null)
            output.set(null)
            signal.set(null)
            Thread.interrupted()
        }

        fun isCancelled(): Boolean = cancelled.get()

        fun cancel() {
            cancelled.set(true)
            worker?.interrupt()
            runCatching { signal.getAndSet(null)?.cancel() }
            runCatching { output.getAndSet(null)?.close() }
            runCatching { input.getAndSet(null)?.close() }
        }

        fun checkCancelled() {
            if (cancelled.get() || Thread.currentThread().isInterrupted) {
                throw CopyCancelledException()
            }
        }

        fun registerInput(stream: InputStream) {
            input.set(stream)
            if (cancelled.get() && input.compareAndSet(stream, null)) {
                runCatching { stream.close() }
                throw CopyCancelledException()
            }
        }

        fun clearInput(stream: InputStream?) {
            if (stream != null) input.compareAndSet(stream, null)
        }

        fun registerOutput(stream: OutputStream) {
            output.set(stream)
            if (cancelled.get() && output.compareAndSet(stream, null)) {
                runCatching { stream.close() }
                throw CopyCancelledException()
            }
        }

        fun clearOutput(stream: OutputStream?) {
            if (stream != null) output.compareAndSet(stream, null)
        }

        fun registerSignal(value: CancellationSignal) {
            signal.set(value)
            if (cancelled.get() && signal.compareAndSet(value, null)) {
                value.cancel()
                throw CopyCancelledException()
            }
        }

        fun clearSignal(value: CancellationSignal) {
            signal.compareAndSet(value, null)
        }
    }

    private class CopyCancelledException : Exception()

    companion object {
        private const val ACTION_SAVE = "ka.kitool.action.SAVE"
        private const val CHANNEL_ID = "file_copies"
        private const val NOTIFICATION_ID = 1001
        private const val COPY_BUFFER_SIZE = 128 * 1024
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 500L
        private const val DEFAULT_MIME_TYPE = "application/octet-stream"
        private val MIME_TYPE =
            Pattern.compile(
                "(?:\\*/\\*|[A-Za-z0-9!#$%&'*+.^_`|~\\-]+/" +
                    "(?:\\*|[A-Za-z0-9!#$%&'*+.^_`|~\\-]+))"
            )

        fun createStartIntent(
            context: Context,
            uris: List<Uri>,
        ): Intent {
            require(uris.isNotEmpty())
            val normalizedUris = ArrayList<Uri>(uris.size)
            for (uri in uris) normalizedUris.add(uri.normalizeScheme())
            val grantClipData = ClipData.newRawUri("", normalizedUris[0])
            for (index in 1 until normalizedUris.size) {
                grantClipData.addItem(ClipData.Item(normalizedUris[index]))
            }
            return Intent(context, SaveService::class.java).apply {
                action = ACTION_SAVE
                clipData = grantClipData
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}
