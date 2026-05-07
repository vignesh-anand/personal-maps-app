package com.scoot.transit.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.scoot.transit.domain.DataSource
import com.scoot.transit.domain.Departure
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LiveBadge(source: DataSource, modifier: Modifier = Modifier) {
    val (label, color) = when (source) {
        DataSource.SCHEDULE -> "Sched" to MaterialTheme.colorScheme.outline
        DataSource.GTFS_RT -> "Live" to MaterialTheme.colorScheme.tertiary
        DataSource.BART_ETD -> "Live" to MaterialTheme.colorScheme.tertiary
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun MinutesPill(eta: Instant?, scheduled: Instant?, source: DataSource) {
    val now = Instant.now()
    val showInstant = eta ?: scheduled
    val secs = showInstant?.let { Duration.between(now, it).seconds }

    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            secs == null -> Text("--", style = MaterialTheme.typography.titleMedium)
            secs < -60 -> {
                val fmt = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.of("America/Los_Angeles"))
                Text(
                    fmt.format(showInstant),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            secs <= 60 -> Text("Now", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
            secs < 60 * 60 -> Text("${secs / 60}m", style = MaterialTheme.typography.titleMedium)
            else -> {
                val fmt = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.of("America/Los_Angeles"))
                Text(fmt.format(showInstant), style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.width(6.dp))
        LiveBadge(source = source)
    }
}

@Composable
fun LoadingState(label: String = "Loading...", modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ErrorBanner(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}

fun Departure.bestInstant(): Instant = realtime ?: scheduled
