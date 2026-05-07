package com.scoot.transit.data

import com.scoot.transit.BuildConfig
import com.scoot.transit.data.db.LegCacheDao
import com.scoot.transit.data.db.LegCacheEntity
import com.scoot.transit.data.remote.GoogleDirectionsApi
import com.scoot.transit.domain.LatLng
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import timber.log.Timber

/**
 * Wraps Google Directions with on-disk caching (legs are highly repeatable - origin/dest cells +
 * profile = same answer for days). Falls back to a haversine-based estimate if the API is
 * unreachable or no key is configured.
 *
 * We use Google's `bicycling` mode as the closest off-the-shelf analog to a personal escooter.
 * Google models a ~10 mph cyclist; an escooter cruises closer to 12-15 mph, so we apply
 * [SCOOT_SPEEDUP] to the duration.
 */
@Singleton
class RoutingRepo @Inject constructor(
    private val api: GoogleDirectionsApi,
    private val cache: LegCacheDao,
) {
    suspend fun scootLeg(origin: LatLng, dest: LatLng): RouteLeg? = leg(origin, dest, MODE_SCOOT)

    suspend fun walkLeg(origin: LatLng, dest: LatLng): RouteLeg? = leg(origin, dest, MODE_WALK)

    private suspend fun leg(origin: LatLng, dest: LatLng, mode: String): RouteLeg? {
        val origKey = origin.cellKey()
        val destKey = dest.cellKey()
        val cached = cache.get(origKey, destKey, mode)
        if (cached != null && System.currentTimeMillis() - cached.fetched_at < CACHE_TTL) {
            return cached.toLeg(mode)
        }
        if (BuildConfig.API_GOOGLE_KEY.isBlank()) {
            Timber.d("No Google key - returning estimated leg")
            return estimate(origin, dest, mode)
        }
        return runCatching {
            val resp = api.directions(
                origin = "${origin.lat},${origin.lng}",
                destination = "${dest.lat},${dest.lng}",
                mode = if (mode == MODE_SCOOT) "bicycling" else "walking",
                apiKey = BuildConfig.API_GOOGLE_KEY,
            )
            if (resp.status != "OK") {
                Timber.w("Google directions status=${resp.status} ${resp.error_message.orEmpty()}")
                return@runCatching null
            }
            val route = resp.routes.firstOrNull() ?: return@runCatching null
            val leg = route.legs.firstOrNull() ?: return@runCatching null
            val miles = (leg.distance?.value ?: 0L) / 1609.344
            val rawSeconds = (leg.duration?.value ?: 0L).toDouble()
            val adjustedSeconds = if (mode == MODE_SCOOT) rawSeconds / SCOOT_SPEEDUP else rawSeconds
            val out = RouteLeg(
                distanceMiles = miles,
                durationSeconds = adjustedSeconds,
                polyline = route.overview_polyline?.points,
            )
            cache.put(
                LegCacheEntity(
                    origin_key = origKey,
                    dest_key = destKey,
                    profile = mode,
                    distance_meters = miles * 1609.344,
                    duration_seconds = adjustedSeconds,
                    polyline = out.polyline,
                    fetched_at = System.currentTimeMillis(),
                )
            )
            out
        }.onFailure { Timber.w(it, "Google directions failed for $mode $origin -> $dest") }
            .getOrNull() ?: estimate(origin, dest, mode)
    }

    /** Crude great-circle estimate when Directions is unavailable. Used for offline / no-key fallback. */
    private fun estimate(origin: LatLng, dest: LatLng, mode: String): RouteLeg {
        val miles = com.scoot.transit.domain.Geo.distanceMiles(origin, dest) * 1.25
        val mph = if (mode == MODE_SCOOT) 13.0 else 3.2
        return RouteLeg(distanceMiles = miles, durationSeconds = miles / mph * 3600.0, polyline = null)
    }

    /** ~11m grid cell - roughly the same street segment counts as a cache hit. */
    private fun LatLng.cellKey(): String {
        val latC = (lat * 1e4).roundToInt()
        val lngC = (lng * 1e4).roundToInt()
        return "$latC,$lngC"
    }

    private fun LegCacheEntity.toLeg(mode: String): RouteLeg = RouteLeg(
        distanceMiles = distance_meters / 1609.344,
        durationSeconds = duration_seconds,
        polyline = polyline,
    )

    companion object {
        const val MODE_SCOOT = "scoot"
        const val MODE_WALK = "walk"

        /** Compensate for Google's ~10 mph cyclist vs a ~13 mph escooter. */
        private const val SCOOT_SPEEDUP = 1.3

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
