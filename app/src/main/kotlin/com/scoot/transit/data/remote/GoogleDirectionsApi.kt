package com.scoot.transit.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Google Directions API. We hit `bicycling` mode as the closest off-the-shelf analog to a personal
 * escooter and apply a small speed-up factor in [com.scoot.transit.data.RoutingRepo] to compensate
 * for the fact that a 12-15 mph escooter is faster than the ~10 mph cyclist Google models.
 *
 * Docs: https://developers.google.com/maps/documentation/directions/get-directions
 */
interface GoogleDirectionsApi {
    @GET("maps/api/directions/json")
    suspend fun directions(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("mode") mode: String = "bicycling",
        @Query("alternatives") alternatives: Boolean = false,
        @Query("units") units: String = "imperial",
        @Query("key") apiKey: String,
    ): GoogleDirectionsResponse
}

@Serializable
data class GoogleDirectionsResponse(
    val status: String,
    val error_message: String? = null,
    val routes: List<GoogleRoute> = emptyList(),
)

@Serializable
data class GoogleRoute(
    val summary: String? = null,
    val overview_polyline: GooglePolyline? = null,
    val legs: List<GoogleLeg> = emptyList(),
)

@Serializable
data class GoogleLeg(
    val duration: GoogleDuration? = null,
    val distance: GoogleDistance? = null,
)

@Serializable
data class GoogleDuration(val value: Long, val text: String? = null)

@Serializable
data class GoogleDistance(val value: Long, val text: String? = null)

@Serializable
data class GooglePolyline(val points: String? = null)
