package com.uniplanner.app.notes

/**
 * One line drawn with the pen. [xy] holds x and y one after the other, in dp, so a drawing looks the
 * same on a phone and on a tablet.
 */
class InkStroke(val tool: Int, val color: Int, val width: Float, val xy: FloatArray) {
    companion object {
        const val PEN = 0
        const val MARKER = 1
    }
}

/**
 * Keeps drawings small as text: each line is "tool color width:" followed by its points as quarter-dp
 * steps from the previous point, so a page of notes fits in a few kilobytes.
 */
object InkCodec {
    private const val SCALE = 4f

    fun encode(strokes: List<InkStroke>): String = strokes.joinToString("\n") { s ->
        val sb = StringBuilder()
        sb.append(s.tool).append(' ').append(Integer.toHexString(s.color)).append(' ')
            .append((s.width * 10).toInt()).append(':')
        var px = 0
        var py = 0
        var i = 0
        while (i + 1 < s.xy.size) {
            val x = Math.round(s.xy[i] * SCALE)
            val y = Math.round(s.xy[i + 1] * SCALE)
            if (i > 0) sb.append(',')
            sb.append(x - px).append(' ').append(y - py)
            px = x
            py = y
            i += 2
        }
        sb.toString()
    }

    fun decode(text: String): List<InkStroke> = text.lineSequence().mapNotNull { line ->
        runCatching {
            val (head, body) = line.split(':', limit = 2)
            val h = head.split(' ')
            val pts = if (body.isEmpty()) emptyList() else body.split(',')
            val xy = FloatArray(pts.size * 2)
            var x = 0
            var y = 0
            pts.forEachIndexed { k, p ->
                val sp = p.indexOf(' ')
                x += p.substring(0, sp).toInt()
                y += p.substring(sp + 1).toInt()
                xy[k * 2] = x / SCALE
                xy[k * 2 + 1] = y / SCALE
            }
            InkStroke(h[0].toInt(), java.lang.Long.parseLong(h[1], 16).toInt(), h[2].toInt() / 10f, xy)
        }.getOrNull()
    }.toList()
}
