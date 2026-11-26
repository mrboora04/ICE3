package com.mrboora.plate.data

import android.graphics.Bitmap
import android.media.FaceDetector

/**
 * Lines two selfies up for Compare: finds the eyes in each photo and shows the second one moved
 * and sized so its eyes land where the first one's are, at the same size. Only what's on screen
 * moves; the photos themselves are never changed.
 *
 * Uses Android's own face finder (no download, works offline). It gives the point between the
 * eyes and how far apart they are, which is enough to match position and size.
 */
object SelfieAlign {
    /** Where the eyes are, as fractions of the photo: centre x and y, and eye distance ÷ photo width. */
    data class Eyes(val x: Float, val y: Float, val apart: Float)

    enum class Kind(val label: String) { FACE("Face"), BODY("Body") }

    /**
     * A close-up has the eyes far apart relative to the photo (a selfie at arm's length: about a
     * fifth of the width); a mirror photo of the body has them close (a few percent).
     */
    const val FACE_APART = 0.12f

    /** Face photo or body photo. A photo with no face found counts as body. */
    fun kind(e: Eyes?): Kind = if (e != null && e.apart >= FACE_APART) Kind.FACE else Kind.BODY

    /** Finds the most confident face, or null. Works on a small copy, so it's quick. */
    fun eyes(photo: Bitmap): Eyes? = runCatching {
        val w = (minOf(photo.width, 480) / 2) * 2   // the face finder needs an even width
        if (w < 2) return null
        val h = (photo.height * w / photo.width).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(photo, w, h, true).copy(Bitmap.Config.RGB_565, false)
        val found = arrayOfNulls<FaceDetector.Face>(3)
        val n = FaceDetector(w, h, found.size).findFaces(small, found)
        val face = found.take(n).filterNotNull().maxByOrNull { it.confidence() } ?: return null
        val mid = android.graphics.PointF().also(face::getMidPoint)
        Eyes(mid.x / w, mid.y / h, face.eyesDistance() / w)
    }.getOrNull()

    /** How to draw a photo in the view: scale it, then move it by (dx, dy), in view pixels. */
    data class Place(val scale: Float, val dx: Float, val dy: Float)

    /** Fills the view, cropping the overflow, centred (the plain way photos are shown). */
    fun fill(imgW: Int, imgH: Int, viewW: Float, viewH: Float): Place {
        val s = maxOf(viewW / imgW, viewH / imgH)
        return Place(s, (viewW - imgW * s) / 2f, (viewH - imgH * s) / 2f)
    }

    /**
     * Places the [after] photo so its eyes sit exactly on the [before] photo's eyes (as the before
     * photo is shown, filling the view), at the same distance apart. Null when either has no face.
     */
    fun match(
        before: Eyes?, beforeW: Int, beforeH: Int,
        after: Eyes?, afterW: Int, afterH: Int,
        viewW: Float, viewH: Float,
    ): Place? {
        if (before == null || after == null || after.apart <= 0f) return null
        val b = fill(beforeW, beforeH, viewW, viewH)
        val bx = b.dx + before.x * beforeW * b.scale
        val by = b.dy + before.y * beforeH * b.scale
        val bApart = before.apart * beforeW * b.scale
        val s = bApart / (after.apart * afterW)
        return Place(s, bx - after.x * afterW * s, by - after.y * afterH * s)
    }
}
