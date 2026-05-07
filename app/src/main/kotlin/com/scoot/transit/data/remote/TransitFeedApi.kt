package com.scoot.transit.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * 511.org Open Data feeds. We hit the GTFS-RT trip-update + service-alert protobuf endpoints,
 * plus the static GTFS zip download for nightly refresh.
 */
interface TransitFeedApi {

    /** GTFS-RT TripUpdates protobuf. */
    @GET("transit/tripupdates")
    @Streaming
    suspend fun tripUpdates(
        @Query("api_key") apiKey: String,
        @Query("agency") agency: String,
    ): ResponseBody

    /** GTFS-RT VehiclePositions protobuf. */
    @GET("transit/vehiclepositions")
    @Streaming
    suspend fun vehiclePositions(
        @Query("api_key") apiKey: String,
        @Query("agency") agency: String,
    ): ResponseBody

    /** GTFS-RT ServiceAlerts protobuf. */
    @GET("transit/servicealerts")
    @Streaming
    suspend fun serviceAlerts(
        @Query("api_key") apiKey: String,
        @Query("agency") agency: String,
    ): ResponseBody

    /** Static GTFS zip for an operator. */
    @GET("transit/datafeeds")
    @Streaming
    suspend fun gtfsZip(
        @Query("api_key") apiKey: String,
        @Query("operator_id") operatorId: String,
    ): ResponseBody
}
