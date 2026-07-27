package dev.galex.toyapp.probe

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.MotionEvent
import android.view.PixelCopy
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Keeps a weak handle on whatever activity is on screen, so we always drive the right window. */
class CurrentActivityTracker : Application.ActivityLifecycleCallbacks {

    private var ref: WeakReference<Activity>? = null
    val current: Activity? get() = ref?.get()

    override fun onActivityResumed(activity: Activity) {
        ref = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (ref?.get() === activity) ref = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

class AndroidProbeDriver(
    private val activityTracker: CurrentActivityTracker,
) : ProbeDriver {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** A tap is a swipe with no duration, so there is a single gesture path to get right. */
    override fun tap(x: Float, y: Float) = dispatchGesture(x, y, x, y, durationMs = 0)

    override fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long) =
        dispatchGesture(startX, startY, endX, endY, durationMs)

    private fun dispatchGesture(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
    ) {
        val decorView = activityTracker.current?.window?.decorView ?: return
        mainHandler.post {
            // downTime is SHARED by every event of the gesture. Give each event its own and Android
            // reads three unrelated events instead of one stream.
            val downTime = SystemClock.uptimeMillis()
            val steps = (durationMs / 16).toInt().coerceAtLeast(1)

            fun send(action: Int, x: Float, y: Float, eventTime: Long) {
                val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
                decorView.dispatchTouchEvent(event)
                event.recycle() // these come from a pool
            }

            send(MotionEvent.ACTION_DOWN, startX, startY, downTime)
            // Interpolated MOVEs are what make a swipe register as a fling rather than a teleport.
            for (i in 1..steps) {
                val t = i / steps.toFloat()
                send(
                    MotionEvent.ACTION_MOVE,
                    startX + (endX - startX) * t,
                    startY + (endY - startY) * t,
                    downTime + (durationMs * t).toLong(),
                )
            }
            send(MotionEvent.ACTION_UP, endX, endY, downTime + durationMs)
        }
    }

    /**
     * Types through the real IME path, so onValueChange, filters and validation all run. Setting a
     * text field's value directly would be one line and would test nothing.
     */
    override fun typeText(text: String) {
        val decorView = activityTracker.current?.window?.decorView ?: return
        mainHandler.post {
            val focused = decorView.findFocus() ?: decorView
            val events = KeyCharacterMap
                .load(KeyCharacterMap.VIRTUAL_KEYBOARD)
                .getEvents(text.toCharArray())
            events?.forEach { focused.dispatchKeyEvent(it) }
        }
    }

    @Suppress("DEPRECATION")
    override fun pressBack() {
        mainHandler.post { activityTracker.current?.onBackPressed() }
    }

    /** PixelCopy captures what the GPU actually composited, which View.draw() does not. */
    override fun screenshot(): ByteArray {
        val activity = activityTracker.current ?: return ByteArray(0)
        val view = activity.window.decorView
        if (view.width <= 0 || view.height <= 0) return ByteArray(0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        PixelCopy.request(activity.window, bitmap, { latch.countDown() }, mainHandler)
        latch.await(2, TimeUnit.SECONDS)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    override fun displaySize(): Pair<Int, Int> {
        val view = activityTracker.current?.window?.decorView
        return (view?.width ?: 0) to (view?.height ?: 0)
    }
}
