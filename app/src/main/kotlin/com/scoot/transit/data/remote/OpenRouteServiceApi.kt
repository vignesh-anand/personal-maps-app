package com.scoot.transit.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * OpenRouteService Directions v2 - we use the cycling-electric profile as our escooter proxy.
 * Docs: https://openrouteservice.org/dev/#/api-docs/v2/directions
 */
interface OpenRouteServiceApi {

    @POST("v2/directions/{profile}/json")
    suspend fun directions(
        @Header("Authorization") apiKey: String,
        @Path("profile") profile: String,
        @Body req: OrsDirectionsRequest,
    ): OrsDirectionsResponse

    @POST("v2/isochrones/{profile}")
    suspend fun isochrones(
        @Header("Authorization") apiKey: String,
        @Path("profile") profile: String,
        @Body req: OrsIsochronesRequest,
    ): OrsIsochronesResponse
}

@Serializable
data class OrsDirectionsRequest(
    /** [[lng, lat], [lng, lat]] */
    val coordinates: List<List<Double>>,
    val units: String = "mi",
    val instructions: Boolean = false,
    val geometry: Boolean = true,
    val preference: String = "recommended",
    val radiuses: List<Int>? = null,
)

@Serializable
data class OrsDirectionsResponse(
    val routes: List<OrsRoute> = emptyList(),
)

@Serializable
data class OrsRoute(
    val summary: OrsSummary,
    val geometry: String? = null,
    val bbox: List<Double>? = null,
)

@Serializable
data class OrsSummary(
    val distance: Double,
    val duration: Double,
)

@Serializable
data class OrsIsochronesRequest(
    val locations: List<List<Double>>,
    val range: List<Int>,
    val range_type: String = "distance",
    val units: String = "mi",
)

@Serializable
data class OrsIsochronesResponse(
    val type: String? = null,
    val features: List<OrsIsoFeature> = emptyList(),
)

@Serializable
data class OrsIsoFeature(
    val type: String? = null,
    val properties: OrsIsoProps? = null,
)

@Serializable
data class OrsIsoProps(
    val value: Double? = null,
)
