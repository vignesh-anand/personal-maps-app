package com.scoot.transit.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * BART legacy ETD (Estimated Time of Departure) API - wraps the public/legacy XML API in JSON mode.
 * https://api.bart.gov/docs/etd/etd.aspx
 */
interface BartEtdApi {
    @GET("api/etd.aspx")
    suspend fun etd(
        @Query("cmd") cmd: String = "etd",
        @Query("orig") orig: String,
        @Query("key") key: String,
        @Query("json") json: String = "y",
    ): BartEtdEnvelope
}

@Serializable
data class BartEtdEnvelope(val root: BartEtdRoot? = null)

@Serializable
data class BartEtdRoot(
    val time: String? = null,
    val date: String? = null,
    val station: List<BartEtdStation> = emptyList(),
    val message: JsonElement? = null,
)

@Serializable
data class BartEtdStation(
    val name: String,
    val abbr: String,
    val etd: List<BartEtdRoute> = emptyList(),
)

@Serializable
data class BartEtdRoute(
    val destination: String,
    val abbreviation: String,
    val estimate: List<BartEtdEstimate> = emptyList(),
)

@Serializable
data class BartEtdEstimate(
    val minutes: String,
    val platform: String? = null,
    val direction: String? = null,
    val length: String? = null,
    @SerialName("color") val color: String? = null,
    val hexcolor: String? = null,
    val bikeflag: String? = null,
    val delay: String? = null,
)
