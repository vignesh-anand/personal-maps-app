package com.scoot.transit.data

import com.scoot.transit.BuildConfig
import com.scoot.transit.data.remote.BartEtdApi
import com.scoot.transit.data.remote.BartEtdRoute
import com.scoot.transit.data.remote.BartEtdStation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * BART's legacy ETD endpoint - simpler than full GTFS-RT for the BART page's "next departures by destination" view.
 * Cached briefly to reduce API hits.
 */
@Singleton
class BartEtdRepo @Inject constructor(
    private val api: BartEtdApi,
) {
    private val cache = mutableMapOf<String, CachedEtd>()
    private val mutex = Mutex()

    suspend fun etdFor(stationAbbr: String): List<BartEtdRoute>? {
        val now = System.currentTimeMillis()
        mutex.withLock {
            val c = cache[stationAbbr.uppercase()]
            if (c != null && now - c.fetchedAt < CACHE_MS) return c.routes
        }
        return runCatching {
            val response = api.etd(orig = stationAbbr.uppercase(), key = BuildConfig.API_BART_KEY)
            val station: BartEtdStation? = response.root?.station?.firstOrNull()
            val routes = station?.etd.orEmpty()
            mutex.withLock { cache[stationAbbr.uppercase()] = CachedEtd(routes, now) }
            routes
        }.onFailure { Timber.w(it, "BART ETD fetch failed for $stationAbbr") }
            .getOrNull()
    }

    private data class CachedEtd(val routes: List<BartEtdRoute>, val fetchedAt: Long)

    companion object { private const val CACHE_MS = 25_000L }
}
