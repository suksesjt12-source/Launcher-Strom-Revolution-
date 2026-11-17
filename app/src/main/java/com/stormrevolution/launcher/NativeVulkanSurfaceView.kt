package com.stormrevolution.launcher

import android.content.Context
import android.graphics.Color
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Displays a native Vulkan surface. The current frame is only a renderer
 * bring-up test screen; it does not contain game graphics or game assets.
 */
class NativeVulkanSurfaceView(context: Context) : SurfaceView(context),
    SurfaceHolder.Callback {

    init {
        setBackgroundColor(Color.rgb(9, 12, 20))
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        StormEngine.startRenderer(holder.surface)
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    ) {
        StormEngine.stopRenderer()
        StormEngine.startRenderer(holder.surface)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        StormEngine.stopRenderer()
    }
}
