package com.scoot.transit.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.action.clickable
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.scoot.transit.MainActivity
import com.scoot.transit.data.DepartureRepo
import com.scoot.transit.data.GtfsStaticRepo
import com.scoot.transit.data.LocationRepo
import com.scoot.transit.data.db.FavoritesDao
import com.scoot.transit.domain.Agency
import com.scoot.transit.domain.Departure
import com.scoot.transit.domain.Direction
import com.scoot.transit.domain.Station
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class NextTrainsWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun statics(): GtfsStaticRepo
        fun departures(): DepartureRepo
        fun location(): LocationRepo
        fun favorites(): FavoritesDao
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val data = loadData(ep)
        provideContent {
            GlanceTheme { WidgetContent(data) }
        }
    }

    private suspend fun loadData(ep: WidgetEntryPoint): WidgetData {
        if (!ep.statics().isLoaded(Agency.CALTRAIN)) {
            return WidgetData(stationName = "Caltrain not loaded", northbound = null, southbound = null)
        }
        val current = ep.location().current()
        val favIds = ep.favorites().forAgency(Agency.CALTRAIN.operatorId).map { it.stop_id }
        val favStations = favIds.mapNotNull { ep.statics().stationById(Agency.CALTRAIN, it) }
        val nearestFav = if (current != null) {
            favStations.minByOrNull { com.scoot.transit.domain.Geo.distanceMiles(it.location, current) }
        } else favStations.firstOrNull()
        val station: Station = nearestFav
            ?: current?.let { ep.statics().nearestStation(Agency.CALTRAIN, it) }
            ?: return WidgetData("No station", null, null)

        val now = ZonedDateTime.now(ZoneId.of("America/Los_Angeles"))
        val deps = ep.departures().upcomingDepartures(Agency.CALTRAIN, station.stopId, now, limit = 30)
        return WidgetData(
            stationName = station.name,
            northbound = deps.firstOrNull { it.direction == Direction.NORTHBOUND },
            southbound = deps.firstOrNull { it.direction == Direction.SOUTHBOUND },
        )
    }
}

private data class WidgetData(
    val stationName: String,
    val northbound: Departure?,
    val southbound: Departure?,
)

@Composable
private fun WidgetContent(data: WidgetData) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Text(
                data.stationName,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onBackground,
                ),
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            DirRow("NB", data.northbound)
            Spacer(modifier = GlanceModifier.height(2.dp))
            DirRow("SB", data.southbound)
        }
    }
}

@Composable
private fun DirRow(label: String, dep: Departure?) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$label  ",
            style = TextStyle(color = GlanceTheme.colors.onBackground, fontWeight = FontWeight.Medium),
        )
        Text(
            etaText(dep),
            style = TextStyle(color = GlanceTheme.colors.onBackground),
        )
    }
}

private fun etaText(dep: Departure?): String {
    if (dep == null) return "--"
    val zone = ZoneId.of("America/Los_Angeles")
    val instant = dep.realtime ?: ZonedDateTime.of(
        java.time.LocalDate.now(zone), dep.scheduled, zone
    ).toInstant()
    val mins = Duration.between(Instant.now(), instant).toMinutes()
    return when {
        mins <= 0 -> "Now"
        mins < 60 -> "${mins}m"
        else -> dep.scheduled.toString().take(5)
    }
}
