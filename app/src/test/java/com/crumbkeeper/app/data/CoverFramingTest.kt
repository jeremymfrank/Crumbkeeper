package com.crumbkeeper.app.data

import org.junit.Assert.*
import org.junit.Test

class CoverFramingTest {
    @Test fun landscapePhotoFillsPortraitCardWithoutDistortion() {
        assertEquals(CoverFraming.Crop(360, 0, 480, 600), CoverFraming().crop(1200, 600, 80, 100))
    }
    @Test fun framingCanReachBothEdgesAndZoomWithoutLeavingOriginal() {
        assertEquals(CoverFraming.Crop(0, 0, 480, 600), CoverFraming(0f, 0f).crop(1200, 600, 80, 100))
        assertEquals(CoverFraming.Crop(720, 0, 480, 600), CoverFraming(1f, 1f).crop(1200, 600, 80, 100))
        assertEquals(CoverFraming.Crop(960, 300, 240, 300), CoverFraming(1f, 1f, 2f).crop(1200, 600, 80, 100))
    }
    @Test fun portraitPhotoFillsWidePreviewWithoutStretching() {
        assertEquals(CoverFraming.Crop(0, 450, 600, 300), CoverFraming().crop(600, 1200, 200, 100))
    }
    @Test fun malformedFramingCannotEscapeImageBounds() {
        assertEquals(CoverFraming(.5f, .5f, 1f), CoverFraming(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN).normalized())
        assertEquals(CoverFraming(0f, 1f, 3f), CoverFraming(-10f, 10f, 20f).normalized())
        val framing = CoverFraming(1f, 1f, 3f)
        val crop = framing.crop(1, 1, 500, 1)
        assertEquals(CoverFraming.Crop(0, 0, 1, 1), crop)
    }
}
