package com.uniplanner.app.timetable

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.uniplanner.app.domain.ImportedClass
import com.uniplanner.app.domain.TimetableGrid
import com.uniplanner.app.health.PlanImport

/** A timetable drawn as a grid, from a photo, a screenshot or a PDF (such as SIGA's printed page). */
object GridImport {
    suspend fun read(ctx: Context, uri: Uri): List<ImportedClass> =
        PlanImport.readPages(ctx, uri).flatMap { (bitmap, boxes) ->
            TimetableGrid.read(boxes) { x, top, bottom -> edges(bitmap, x, top, bottom) }
        }.distinctBy { it.key }

    /** The cell's border lines above and below a block of text: the first non-white row each way. */
    private fun edges(bitmap: Bitmap, x: Int, top: Int, bottom: Int): Pair<Int, Int>? {
        if (x !in 0 until bitmap.width) return null
        val reach = (bottom - top) * 3 + 40
        fun line(y: Int) = bitmap.getPixel(x, y).let { (Color.red(it) + Color.green(it) + Color.blue(it)) / 3 < 235 }
        val up = (top - 3 downTo maxOf(0, top - reach)).firstOrNull { line(it) } ?: return null
        val down = (bottom + 3 until minOf(bitmap.height, bottom + reach)).firstOrNull { line(it) } ?: return null
        return up to down
    }
}
