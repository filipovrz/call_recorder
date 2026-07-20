package com.androkall.recorder.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import com.androkall.recorder.R

/**
 * Floating quick-action bubble shown while the phone is ringing / in-call,
 * so recording can be armed or started before answering.
 */
class CallOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var phoneNumber: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                phoneNumber = intent.getStringExtra(EXTRA_NUMBER)
                showOverlay()
            }
            ACTION_HIDE -> {
                hideOverlay()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        if (overlayView != null) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val button = Button(this).apply {
            text = getString(R.string.overlay_start_record)
            setBackgroundColor(0xE01B7A5A.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                CallRecordingService.start(this@CallOverlayService, phoneNumber)
                text = getString(R.string.overlay_stop_record)
                setOnClickListener {
                    CallRecordingService.stop(this@CallOverlayService)
                    hideOverlay()
                    stopSelf()
                }
            }
        }

        val container = FrameLayout(this).apply {
            setPadding(24, 24, 24, 24)
            addView(button)
        }
        overlayView = container

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 180
        }

        windowManager?.addView(container, params)
    }

    private fun hideOverlay() {
        overlayView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        overlayView = null
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_SHOW = "com.androkall.recorder.action.SHOW_OVERLAY"
        const val ACTION_HIDE = "com.androkall.recorder.action.HIDE_OVERLAY"
        const val EXTRA_NUMBER = "extra_number"

        fun show(context: Context, phoneNumber: String?) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, CallOverlayService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_NUMBER, phoneNumber)
            }
            context.startService(intent)
        }

        fun hide(context: Context) {
            val intent = Intent(context, CallOverlayService::class.java).apply {
                action = ACTION_HIDE
            }
            context.startService(intent)
        }
    }
}
