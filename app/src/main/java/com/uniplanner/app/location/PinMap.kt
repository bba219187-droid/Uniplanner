package com.uniplanner.app.location

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File

/** A place on the map: a named pin for a friend, or a plain dot on the admins' map. */
data class MapPin(val id: String, val lat: Double, val lng: Double, val title: String? = null, val snippet: String? = null)

/**
 * An OpenStreetMap map (no API key needed) with [pins]. It frames all pins the first time they
 * appear, and again only when someone new shows up, so it does not jump while the student pans.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun PinMap(pins: List<MapPin>, dark: Boolean, pinColor: Color, modifier: Modifier = Modifier, dots: Boolean = false) {
    val context = LocalContext.current
    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setMinZoomLevel(3.0)
            setMaxZoomLevel(19.0)
            controller.setZoom(6.0)
            // Portugal until there is someone to show.
            controller.setCenter(GeoPoint(39.6, -8.0))
            overlays.add(CopyrightOverlay(context))
            // Inside a scrolling page, dragging the map moves the map, not the page.
            setOnTouchListener { v, _ ->
                v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
            tag = ""
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }
    AndroidView(
        factory = { map },
        modifier = modifier,
        update = { view ->
            view.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
            view.overlays.removeAll { it is Marker }
            val density = view.resources.displayMetrics.density
            val dot = if (dots) {
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(pinColor.toArgb())
                    setStroke((2 * density).toInt(), android.graphics.Color.WHITE)
                    setSize((14 * density).toInt(), (14 * density).toInt())
                }
            } else {
                null
            }
            pins.forEach { pin ->
                view.overlays.add(
                    Marker(view).apply {
                        position = GeoPoint(pin.lat, pin.lng)
                        setAnchor(Marker.ANCHOR_CENTER, if (dots) Marker.ANCHOR_CENTER else Marker.ANCHOR_BOTTOM)
                        if (dot != null) icon = dot
                        title = pin.title
                        snippet = pin.snippet
                        // Dots have no name to show.
                        if (pin.title == null) setOnMarkerClickListener { _, _ -> true }
                    },
                )
            }
            val key = pins.map { it.id }.sorted().joinToString()
            if (pins.isNotEmpty() && view.tag != key) {
                view.tag = key
                view.post { frame(view, pins) }
            }
            view.invalidate()
        },
    )
}

private fun frame(view: MapView, pins: List<MapPin>) {
    if (pins.size == 1) {
        view.controller.setZoom(15.0)
        view.controller.setCenter(GeoPoint(pins[0].lat, pins[0].lng))
        return
    }
    val box = BoundingBox.fromGeoPointsSafe(pins.map { GeoPoint(it.lat, it.lng) })
    if (view.width > 0 && view.height > 0) {
        view.zoomToBoundingBox(box.increaseByScale(1.3f), false)
    } else {
        view.controller.setCenter(box.centerWithDateLine)
    }
}
