package com.scoot.transit.data

import com.scoot.transit.BuildConfig
import com.scoot.transit.data.db.LegCacheDao
import com.scoot.transit.data.db.LegCacheEntity
import com.scoot.transit.data.remote.OpenRouteServiceApi
import com.scoot.transit.data.remote.OrsDirectionsRequest
import com.scoot.transit.domain.LatLng
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import timber.log.Timber

/**
 * Wraps OpenRouteService with on-disk caching (legs are highly repeatable - origin/dest cells +
 * profile = same answer for days). Falls back to a haversine-based estimate if the API is unreachable.
 */
@Singleton
class RoutingRepo @Inject constructor(
    private val api: OpenRouteServiceApi,
    private val cache: LegCacheDao,
) {
    suspend fun scootLeg(origin: LatLng, dest: LatLng): RouteLeg? = leg(origin, dest, PROFILE_SCOOT)

    suspend fun walkLeg(origin: LatLng, dest: LatLng): RouteLeg? = leg(origin, dest, PROFILE_WALK)

    private suspend fun leg(origin: LatLng, dest: LatLng, profile: String): RouteLeg? {
        val origKey = origin.cellKey()
        val destKey = dest.cellKey()
        val cached = cache.get(origKey, destKey, profile)
        if (cached != null && System.currentTimeMillis() - cached.fetched_at < CACHE_TTL) {
            return cached.toLeg()
        }
        if (BuildConfig.API_ORS_KEY.isBlank()) {
            Timber.d("No ORS key - returning estimated leg")
            return estimate(origin, dest, profile)
        }
        return runCatching {
            val resp = api.directions(
                apiKey = BuildConfig.API_ORS_KEY,
                profile = profile,
                req = OrsDirectionsRequest(
                    coordinates = listOf(listOf(origin.lng, origin.lat), listOf(dest.lng, dest.lat)),
                ),
            )
            val route = resp.routes.firstOrNull() ?: return@runCatching null
            val leg = RouteLeg(
                distanceMiles = route.summary.distance,
                durationSeconds = route.summary.duration,
                polyline = route.geometry,
            )
            cache.put(
                LegCacheEntity(
                    origin_key = origKey,
                    dest_key = destKey,
                    profile = profile,
                    distance_meters = leg.distanceMiles * 1609.34,
                    duration_seconds = leg.durationSeconds,
                    polyline = leg.polyline,
                    fetched_at = System.currentTimeMillis(),
                )
            )
            leg
        }.onFailure { Timber.w(it, "ORS directions failed for $profile $origin -> $dest") }
            .getOrNull() ?: estimate(origin, dest, profile)
    }

    /** Crude great-circle estimate when ORS is unavailable. Kept for offline/no-key fallback. */
    private fun estimate(origin: LatLng, dest: LatLng, profile: String): RouteLeg {
        val miles = com.scoot.transit.domain.Geo.distanceMiles(origin, dest) * 1.25
        val mph = if (profile == PROFILE_SCOOT) 12.0 else 3.2
        return RouteLeg(distanceMiles = miles, durationSeconds = miles / mph * 3600.0, polyline = null)
    }

    private fun LatLng.cellKey(): String {
        val latC = (lat * 1e4).roundToInt()
        val lngC = (lng * 1e4).roundToInt()
        return "$latC,$lngC"
    }

    private fun LegCacheEntity.toLeg(): RouteLeg = RouteLeg(
        distanceMiles = distance_meters / 1609.34,
        durationSeconds = duration_seconds,
        polyline = polyline,
    )

    companion object {
        const val PROFILE_SCOOT = "cycling-electric"
        const val PROFILE_WALK = "foot-walking"
        private const val CACHE_TTL = 7L * 24 * 3600 * 1000
    }
}

data class RouteLeg(
    val distanceMiles: Double,
    val durationSeconds: Double,
    val polyline: String?,
) {
    val durationMinutes: Double get() = durationSeconds / 60.0
}
