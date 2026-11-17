package com.stormrevolution.launcher

import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/** Native Vulkan surface and first multi-touch input test area. */
class NativeVulkanSurfaceView(context: Context) : SurfaceView(context),
    SurfaceHolder.Callback {

    init {
        setBackgroundColor(Color.rgb(9, 12, 20))
        isFocusable = true
        isFocusableInTouchMode = true
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        StormEngine.startRenderer(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        StormEngine.stopRenderer()
        StormEngine.startRenderer(holder.surface)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        StormEngine.stopRenderer()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_MOVE) {
            for (index in 0 until event.pointerCount) sendPointer(event, index, action)
        } else if (action == MotionEvent.ACTION_CANCEL) {
            StormEngine.sendTouch(-1, 0f, 0f, action)
        } else {
            sendPointer(event, event.actionIndex, action)
        }
        return true
    }

    private fun sendPointer(event: MotionEvent, index: Int, action: Int) {
        if (index !in 0 until event.pointerCount) return
        StormEngine.sendTouch(
            event.getPointerId(index),
            event.getX(index) / width.coerceAtLeast(1),
            event.getY(index) / height.coerceAtLeast(1),
            action
        )
    }
}
