package com.scoot.transit.data

import com.scoot.transit.BuildConfig
import com.scoot.transit.data.remote.SiriStopMonitoringApi
import com.scoot.transit.domain.Agency
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

/**
 * Lightweight bus-fallback layer. We call 511's SIRI StopMonitoring endpoint at user-relevant bus
 * stop codes (AC Transit / SamTrans / VTA / Muni) and surface the next departures so the user
 * has *some* bus signal when no train plan works. We deliberately do NOT do full GTFS multimodal
 * for buses in v1 - that would require bundling bus GTFS for all four agencies (~70 MB).
 */
@Singleton
class BusFallbackRepo @Inject constructor(
    private val api: SiriStopMonitoringApi,
) {
    suspend fun nextAtStop(agency: Agency, stopCode: String, limit: Int = 5): List<BusDeparture> {
        if (BuildConfig.API_511_KEY.isBlank()) return emptyList()
        return runCatching {
            val env = api.stopMonitoring(BuildConfig.API_511_KEY, agency.operatorId, stopCode)
            val visits = env.ServiceDelivery?.StopMonitoringDelivery
                ?.jsonObject?.get("MonitoredStopVisit")?.jsonArray ?: return@runCatching emptyList()
            visits.take(limit).mapNotNull { visit ->
                val mvj = visit.jsonObject["MonitoredVehicleJourney"]?.jsonObject ?: return@mapNotNull null
                val line = mvj["LineRef"]?.string()
                val dest = mvj["DestinationName"]?.string()
                val call = mvj["MonitoredCall"]?.jsonObject
                val expected = call?.get("ExpectedDepartureTime")?.string()
                    ?: call?.get("AimedDepartureTime")?.string()
                BusDeparture(
                    agency = agency,
                    stopCode = stopCode,
                    line = line.orEmpty(),
                    destination = dest.orEmpty(),
                    expectedTimeIso = expected,
                )
            }
        }.onFailure { Timber.w(it, "SIRI StopMonitoring failed at $stopCode") }
            .getOrDefault(emptyList())
    }

    private fun JsonElement.string(): String? =
        runCatching { jsonPrimitive.contentOrNull }.getOrNull()
}

data class BusDeparture(
    val agency: Agency,
    val stopCode: String,
    val line: String,
    val destination: String,
    val expectedTimeIso: String?,
)
