package com.uniplanner.app.timetable

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.uniplanner.app.R
import com.uniplanner.app.domain.ImportedClass
import com.uniplanner.app.domain.Timetable
import com.uniplanner.app.reminders.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Reads the timetable from the school's portal page (such as IPCA's SIGA), a calendar link or a
 * photo, and updates it every morning.
 */
object TimetableSync {
    /**
     * Runs inside the portal page. It finds the time labels down the side ("08h30") and the day
     * headings across the top, then places each block by where it is drawn, so blocks spanning
     * several rows get the right start and end whatever the table's markup.
     */
    val READ_PAGE_JS = """
        (function() {
          function norm(s) { return (s || '').replace(/\s+/g, ' ').trim(); }
          var dayNames = [['segunda','seg','monday','mon','2ª'],['terça','terca','ter','tuesday','tue','3ª'],
            ['quarta','qua','wednesday','wed','4ª'],['quinta','qui','thursday','thu','5ª'],
            ['sexta','sex','friday','fri','6ª'],['sábado','sabado','sáb','sab','saturday','sat'],['domingo','dom','sunday','sun']];
          function dayOf(t) {
            t = norm(t).toLowerCase().replace(/[.:,]/g, '');
            var first = t.split(' ')[0];
            for (var i = 0; i < 7; i++) for (var j = 0; j < dayNames[i].length; j++) {
              var n = dayNames[i][j];
              if (first === n || (n.length > 3 && first.indexOf(n) === 0)) return i + 1;
            }
            return 0;
          }
          function docs(d, out) {
            out.push(d);
            var frames = d.querySelectorAll('iframe,frame');
            for (var i = 0; i < frames.length; i++) { try { if (frames[i].contentDocument) docs(frames[i].contentDocument, out); } catch (e) {} }
            return out;
          }
          var result = [];
          docs(document, []).forEach(function(doc) {
            var win = doc.defaultView || window;
            var sx = win.scrollX || 0, sy = win.scrollY || 0;
            var times = [], days = [], cells = [];
            var els = doc.querySelectorAll('td,th,div,span,li,a,p');
            for (var i = 0; i < els.length; i++) {
              var el = els[i], text = norm(el.innerText), r = el.getBoundingClientRect();
              if (!text || r.width === 0) continue;
              var m = text.match(/^(\d{1,2})\s*[hH:.]\s*(\d{2})(\s*[-–]\s*\d{1,2}\s*[hH:.]\s*\d{2})?$/);
              var box = { top: r.top + sy, bottom: r.bottom + sy, left: r.left + sx, right: r.right + sx };
              if (m) { box.min = (+m[1]) * 60 + (+m[2]); times.push(box); continue; }
              var d = text.length < 20 ? dayOf(text) : 0;
              if (d) { box.day = d; days.push(box); continue; }
              var lines = (el.innerText || '').split('\n').map(norm).filter(function(s) { return s.length; });
              // The innermost element holding the block, not the rows or table around it.
              var inner = false, kids = el.children;
              for (var q = 0; q < kids.length; q++) { if ((kids[q].innerText || '').split('\n').filter(function(s) { return norm(s).length; }).length >= 2) { inner = true; break; } }
              if (lines.length >= 2 && lines.length <= 8 && !inner && r.width < 600) {
                // The table cell around it tells how many rows the class spans.
                var cell = el.closest('td') || el, cr = cell.getBoundingClientRect();
                cells.push({ top: cr.top + sy, bottom: cr.bottom + sy, left: cr.left + sx, right: cr.right + sx, lines: lines });
              }
            }
            // One label per time, the tightest box around it.
            var byMin = {};
            times.forEach(function(t) { var o = byMin[t.min]; if (!o || (t.bottom - t.top) < (o.bottom - o.top)) byMin[t.min] = t; });
            times = Object.keys(byMin).map(function(k) { return byMin[k]; });
            var dayBy = {};
            days.forEach(function(h) { var o = dayBy[h.day]; if (!o || (h.right - h.left) < (o.right - o.left)) dayBy[h.day] = h; });
            days = Object.keys(dayBy).map(function(k) { return dayBy[k]; });
            if (times.length < 2 || !cells.length) return;
            var top = Math.min.apply(null, times.map(function(t) { return t.top; })) - 5;
            var left = Math.max.apply(null, times.map(function(t) { return t.right; })) - 5;
            cells = cells.filter(function(c) { return c.top >= top && c.left >= left; });
            // Blocks inside blocks (a span in a cell) count once.
            cells = cells.filter(function(c) { return !cells.some(function(o) { return o !== c && o.lines.join('|') === c.lines.join('|') && (o.bottom - o.top) * (o.right - o.left) > (c.bottom - c.top) * (c.right - c.left); }); });
            times.sort(function(a, b) { return a.top - b.top; });
            var step = 30;
            for (var k = 1; k < times.length; k++) { var dm = times[k].min - times[k-1].min; if (dm > 0) { step = dm; break; } }
            function nearest(y, key) {
              var best = null, dist = 1e9;
              times.forEach(function(t) { var dd = Math.abs(t[key] - y); if (dd < dist) { dist = dd; best = t; } });
              return best;
            }
            // Without day headings, blocks are placed in their columns from left to right, Monday first.
            var cols = [];
            if (!days.length) {
              cells.forEach(function(c) { var x = (c.left + c.right) / 2; if (!cols.some(function(v) { return Math.abs(v - x) < 20; })) cols.push(x); });
              cols.sort(function(a, b) { return a - b; });
            }
            cells.forEach(function(c) {
              var x = (c.left + c.right) / 2, day = 0;
              if (days.length) {
                var best = 1e9;
                days.forEach(function(h) { var dd = Math.abs((h.left + h.right) / 2 - x); if (dd < best) { best = dd; day = h.day; } });
              } else {
                cols.forEach(function(v, idx) { if (Math.abs(v - x) < 20) day = idx + 1; });
              }
              var s = nearest(c.top, 'top'), e = nearest(c.bottom, 'bottom');
              if (!s || !e || !day) return;
              result.push({ day: day, start: s.min, end: e.min + step, lines: c.lines });
            });
          });
          return JSON.stringify(result);
        })();
    """.trimIndent()

    /** What [READ_PAGE_JS] returns, as classes. */
    fun parsePageResult(json: String?): List<ImportedClass> {
        // The page hands back a JSON string holding the JSON list.
        val text = runCatching { JSONArray("[${json ?: return emptyList()}]").getString(0) }.getOrNull() ?: return emptyList()
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val lines = o.optJSONArray("lines") ?: return@mapNotNull null
            Timetable.fromCell(o.optInt("day"), o.optInt("start"), o.optInt("end"), (0 until lines.length()).map { lines.optString(it) })
        }.distinctBy { it.key }
    }

    /**
     * Opens the portal page without showing it, with the login the student left in the app's web
     * view, and reads it. Null when the page shows no timetable, which usually means the login ran out.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun readPortal(ctx: Context, url: String): List<ImportedClass>? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(60_000) {
            suspendCancellableCoroutine { cont ->
                val web = WebView(ctx.applicationContext)
                val handler = Handler(Looper.getMainLooper())
                var finished = false
                fun done(value: List<ImportedClass>?) {
                    if (finished) return
                    finished = true
                    handler.removeCallbacksAndMessages(null)
                    web.stopLoading()
                    web.destroy()
                    if (cont.isActive) cont.resume(value)
                }
                cont.invokeOnCancellation { handler.post { done(null) } }
                web.settings.javaScriptEnabled = true
                web.settings.domStorageEnabled = true
                web.settings.userAgentString = web.settings.userAgentString.replace("; wv", "")
                // A wide page, like on a computer, so the table is laid out as a grid.
                web.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(1600, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(4000, android.view.View.MeasureSpec.EXACTLY),
                )
                web.layout(0, 0, 1600, 4000)
                web.settings.useWideViewPort = true
                web.settings.loadWithOverviewMode = true
                CookieManager.getInstance().setAcceptCookie(true)
                web.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, pageUrl: String) {
                        // Some portals finish drawing a moment later, or pass through a redirect first.
                        handler.removeCallbacksAndMessages(null)
                        handler.postDelayed({
                            view.evaluateJavascript(READ_PAGE_JS) { json ->
                                val found = parsePageResult(json)
                                if (found.isNotEmpty()) done(found)
                            }
                        }, 2_000)
                        handler.postDelayed({ done(null) }, 12_000)
                    }
                }
                web.loadUrl(url)
            }
        }
    }

    suspend fun readCalendar(link: String): List<ImportedClass> = withContext(Dispatchers.IO) {
        val connection = URL(link.trim().replace(Regex("^webcals?://"), "https://")).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            if (!body.contains("BEGIN:VCALENDAR")) throw IOException("not a calendar")
            Timetable.fromIcs(body)
        } finally {
            connection.disconnect()
        }
    }

    /** Reads again from wherever the timetable came from. False when the portal needs the login again. */
    suspend fun refresh(ctx: Context): Boolean {
        val s = TimetableStore.get(ctx)
        when (s.source) {
            TimetableSource.PORTAL -> {
                val found = readPortal(ctx, s.link)
                if (found.isNullOrEmpty()) {
                    TimetableStore.needsLogin(ctx)
                    return false
                }
                TimetableStore.imported(ctx, found, TimetableSource.PORTAL, s.link)
            }
            TimetableSource.CALENDAR -> {
                val found = readCalendar(s.link)
                if (found.isNotEmpty()) TimetableStore.imported(ctx, found, TimetableSource.CALENDAR, s.link)
            }
            else -> Unit
        }
        return true
    }

    /** Every morning at about 7, when there is internet. */
    fun schedule(ctx: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(7, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<TimetableSyncWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("timetable-sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class TimetableSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val before = TimetableStore.get(ctx)
        if (before.source != TimetableSource.PORTAL && before.source != TimetableSource.CALENDAR) return Result.success()
        val ok = runCatching { TimetableSync.refresh(ctx) }.getOrElse { return Result.retry() }
        // Told once, not every morning.
        if (!ok && !before.needsLogin) {
            Notifications.show(ctx, 69_999, Notifications.CHANNEL_CLASSES, ctx.getString(R.string.timetable_login_title), ctx.getString(R.string.timetable_login_text))
        }
        return Result.success()
    }
}
