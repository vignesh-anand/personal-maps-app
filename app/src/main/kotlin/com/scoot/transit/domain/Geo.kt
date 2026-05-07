package com.scoot.transit.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_MI = 3958.7613

    fun distanceMiles(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val sinDLat = sin(dLat / 2)
        val sinDLng = sin(dLng / 2)
        val h = sinDLat.pow(2.0) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sinDLng.pow(2.0)
        val c = 2 * atan2(sqrt(h), sqrt(1 - h))
        return EARTH_RADIUS_MI * c
    }
}
