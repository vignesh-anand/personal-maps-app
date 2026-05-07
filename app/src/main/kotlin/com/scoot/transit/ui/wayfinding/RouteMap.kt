package com.scoot.transit.ui.wayfinding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.scoot.transit.domain.Place
import com.scoot.transit.domain.TripLeg
import com.scoot.transit.domain.TripPlan

/**
 * Lightweight map preview for a single trip plan, plus a reachability circle around origin.
 */
@Composable
fun RouteMap(
    origin: Place?,
    dest: Place?,
    plan: TripPlan?,
    maxRangeMiles: Double,
    modifier: Modifier = Modifier,
) {
    val center = when {
        origin != null -> LatLng(origin.location.lat, origin.location.lng)
        dest != null -> LatLng(dest.location.lat, dest.location.lng)
        else -> LatLng(37.4419, -122.1430) // Palo Alto fallback
    }
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, 12f)
    }
    GoogleMap(
        modifier = modifier.fillMaxWidth().height(220.dp),
        cameraPositionState = cameraState,
        properties = MapProperties(mapType = MapType.NORMAL),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
    ) {
        if (origin != null) {
            Marker(
                state = MarkerState(LatLng(origin.location.lat, origin.location.lng)),
                title = origin.name,
            )
            Circle(
                center = LatLng(origin.location.lat, origin.location.lng),
                radius = maxRangeMiles * 1609.344,
                strokeColor = Color(0x55E85D2A),
                fillColor = Color(0x14E85D2A),
                strokeWidth = 2f,
            )
        }
        if (dest != null) {
            Marker(
                state = MarkerState(LatLng(dest.location.lat, dest.location.lng)),
                title = dest.name,
            )
        }
        plan?.legs?.forEach { leg ->
            val polylinePoints = leg.polyline?.let { decodePolyline(it) }.orEmpty()
            if (polylinePoints.isNotEmpty()) {
                val color = when (leg) {
                    is TripLeg.Scoot -> Color(0xFFE85D2A)
                    is TripLeg.Walk -> Color(0xFF2E7D32)
                    is TripLeg.Transit -> Color(0xFF1F5DAE)
                }
                Polyline(points = polylinePoints, color = color, width = 8f)
            }
        }
    }
}

/** Google encoded polyline algorithm. */
private fun decodePolyline(encoded: String): List<LatLng> {
    val poly = ArrayList<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0
    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlat = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
        lat += dlat
        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlng = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
        lng += dlng
        poly.add(LatLng(lat / 1e5, lng / 1e5))
    }
    return poly
}
