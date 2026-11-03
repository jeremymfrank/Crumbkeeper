package com.crumbkeeper.app.data

import kotlin.math.min
import kotlin.math.roundToInt

/** Display-only framing in upright image coordinates; the original file is never cropped. */
data class CoverFraming(val x: Float = .5f, val y: Float = .5f, val zoom: Float = 1f) {
    fun normalized() = CoverFraming(
        if (x.isFinite()) x.coerceIn(0f, 1f) else .5f,
        if (y.isFinite()) y.coerceIn(0f, 1f) else .5f,
        if (zoom.isFinite()) zoom.coerceIn(1f, 3f) else 1f
    )

    fun crop(imageWidth: Int, imageHeight: Int, frameWidth: Int, frameHeight: Int): Crop {
        require(imageWidth > 0 && imageHeight > 0 && frameWidth > 0 && frameHeight > 0)
        val framing = normalized()
        val ratio = frameWidth.toDouble() / frameHeight
        val width = (min(imageWidth.toDouble(), imageHeight * ratio) / framing.zoom)
            .roundToInt().coerceIn(1, imageWidth)
        val height = (min(imageHeight.toDouble(), imageWidth / ratio) / framing.zoom)
            .roundToInt().coerceIn(1, imageHeight)
        return Crop(((imageWidth - width) * framing.x).roundToInt(),
            ((imageHeight - height) * framing.y).roundToInt(), width, height)
    }

    data class Crop(val left: Int, val top: Int, val width: Int, val height: Int)
}
