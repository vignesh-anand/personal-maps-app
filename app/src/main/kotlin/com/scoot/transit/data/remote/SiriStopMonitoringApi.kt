package com.scoot.transit.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Subset of 511.org SIRI StopMonitoring used for "next departures at this stop" - a thin client
 * alternative when the GTFS-RT feed is stale. We deserialize lazily via JsonElement because
 * SIRI's schema is gnarly.
 */
interface SiriStopMonitoringApi {
    @GET("transit/StopMonitoring")
    suspend fun stopMonitoring(
        @Query("api_key") apiKey: String,
        @Query("agency") agency: String,
        @Query("stopcode") stopCode: String,
        @Query("format") format: String = "json",
    ): SiriEnvelope
}

@Serializable
data class SiriEnvelope(
    val ServiceDelivery: SiriServiceDelivery? = null,
)

@Serializable
data class SiriServiceDelivery(
    val ResponseTimestamp: String? = null,
    val StopMonitoringDelivery: JsonElement? = null,
)
