package com.kruzzi.pogocompanion

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.TextView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class LiveCaptureService : Service() {
    companion object {
        const val ACTION_STOP = "com.kruzzi.pogocompanion.STOP"
        const val EXTRA_MODE = "captureMode"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        private const val CHANNEL = "live_capture"
    }
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var overlay: TextView? = null
    private var windowManager: WindowManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var mode = "encounter"
    private var busy = false
    private var lastFrameAt = 0L
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { stopSelf() }
    }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL, "PoGO Live Capture", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        mode = intent?.getStringExtra(EXTRA_MODE) ?: "encounter"
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("PoGO Companion · Live")
            .setContentText("Η οθόνη αναλύεται τοπικά · πάτησε για διακοπή")
            .setOngoing(true)
            .setContentIntent(PendingIntent.getService(this, 1,
                Intent(this, LiveCaptureService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .build()
        startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        if (projection != null) return START_STICKY
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        else intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        if (resultCode != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }
        try {
            showOverlay()
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            projection!!.registerCallback(callback, handler)
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            reader!!.setOnImageAvailableListener({ source ->
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                val now = SystemClock.elapsedRealtime()
                if (busy || now - lastFrameAt < 1250L) { image.close(); return@setOnImageAvailableListener }
                lastFrameAt = now
                busy = true
                try {
                    val plane = image.planes[0]
                    val stride = plane.rowStride
                    val pixelStride = plane.pixelStride
                    val padded = Bitmap.createBitmap(width + (stride - pixelStride * width) / pixelStride, height, Bitmap.Config.ARGB_8888)
                    padded.copyPixelsFromBuffer(plane.buffer)
                    val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
                    padded.recycle()
                    image.close()
                    recognizer.process(InputImage.fromBitmap(cropped, 0))
                        .addOnSuccessListener { result ->
                            val cleanText = result.text.lines().filterNot { it.contains("PoGO", true) }.joinToString("\n")
                            if (mode == "pvp") {
                                overlay?.text = "PoGO PvP COACH\n" + BattleBrain.pvpAdvice(cleanText)
                            } else {
                                val analysis = BattleBrain.analyze(cleanText)
                                overlay?.text = if (analysis == null) {
                                    "PoGO ENCOUNTER IV\nΠεριμένω Pokémon + CP…"
                                } else {
                                    "PoGO IV · ${analysis.species} CP ${analysis.cp}\n" +
                                    "Πιθανοί IV συνδυασμοί: ${analysis.candidates}\n" +
                                    (if (analysis.candidates > 0) "IV εύρος: ${analysis.minPercent}–${analysis.maxPercent}%\n" else "") +
                                    analysis.detail
                                }
                            }
                        }
                        .addOnFailureListener { overlay?.text = "PoGO LIVE · Αποτυχία OCR" }
                        .addOnCompleteListener { cropped.recycle(); busy = false }
                } catch (e: Exception) {
                    image.close()
                    busy = false
                    overlay?.text = "PoGO LIVE · Σφάλμα καρέ"
                }
            }, handler)
            display = projection!!.createVirtualDisplay("PoGO-Live", width, height,
                metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface, null, handler)
        } catch (e: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlay = TextView(this).apply {
            text = "PoGO LIVE · Περιμένω εικόνα…"
            textSize = 12f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0xDD14251A.toInt())
            setPadding(18, 14, 18, 14)
            maxLines = 9
        }
        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.65f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT)
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 12
        params.y = 110
        windowManager?.addView(overlay, params)
    }

    override fun onDestroy() {
        reader?.setOnImageAvailableListener(null, null)
        display?.release()
        reader?.close()
        projection?.unregisterCallback(callback)
        projection?.stop()
        overlay?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        recognizer.close()
        super.onDestroy()
    }
}
