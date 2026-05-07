package com.scoot.transit.ui.wayfinding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scoot.transit.domain.Place
import com.scoot.transit.domain.TripLeg
import com.scoot.transit.domain.TripPlan
import com.scoot.transit.routing.TripTiming
import com.scoot.transit.ui.common.LoadingState
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WayfindingScreen(
    presetId: String? = null,
    onConsumePreset: () -> Unit = {},
    vm: WayfindingViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val savedPresets by vm.presetsFlow.collectAsStateWithLifecycle()
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetLabel by remember { mutableStateOf("") }
    var showMap by remember { mutableStateOf(false) }

    LaunchedEffect(presetId) {
        if (presetId != null) {
            vm.loadPreset(presetId)
            onConsumePreset()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Plan a trip") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (savedPresets.isNotEmpty()) {
                item { PresetsRow(savedPresets, onTap = vm::loadPreset, onDelete = vm::deletePreset) }
            }
            item { FromToBlock(state, vm) }
            item { TimingBlock(state.timing) { vm.setTiming(it) } }
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { vm.plan() },
                        enabled = state.from != null && state.to != null && !state.isPlanning,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (state.isPlanning) "Planning..." else "Find routes") }
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = { showSaveDialog = true },
                        enabled = state.from != null && state.to != null,
                    ) { Text("Save") }
                }
            }
            if (state.isPlanning) item { LoadingState() }
            if (state.plans.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Show map", modifier = Modifier.weight(1f))
                        androidx.compose.material3.Switch(
                            checked = showMap,
                            onCheckedChange = { showMap = it },
                        )
                    }
                }
                if (showMap) {
                    item {
                        RouteMap(
                            origin = state.from,
                            dest = state.to,
                            plan = state.plans.firstOrNull(),
                            maxRangeMiles = state.maxScootMiles,
                        )
                    }
                }
            }
            items(state.plans, key = { it.signatureKey() }) { plan ->
                TripPlanCard(plan)
            }
            if (state.plans.isEmpty() && !state.isPlanning && state.from != null && state.to != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("No trip yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tap Find routes. If nothing is found, the destination may be outside scooter range or transit isn't running. Try increasing your max range in Settings.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save preset") },
            text = {
                OutlinedTextField(
                    value = presetLabel,
                    onValueChange = { presetLabel = it },
                    placeholder = { Text("Label (e.g. Gym)") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (presetLabel.isNotBlank()) {
                        vm.saveCurrentAsPreset(presetLabel)
                        presetLabel = ""
                        showSaveDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                Button(onClick = { showSaveDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PresetsRow(
    presets: List<com.scoot.transit.data.db.PresetEntity>,
    onTap: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(presets, key = { it.id }) { p ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(50),
                modifier = Modifier.clickable { onTap(p.id) },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(p.label, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    if (!p.id.startsWith("home-to-") && !p.id.startsWith("work-to-")) {
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.Icon(
                            Icons.Filled.Close,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.clickable { onDelete(p.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FromToBlock(state: WayfindingState, vm: WayfindingViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaceField(
                label = "From",
                place = state.from,
                query = state.fromQuery,
                suggestions = state.fromSuggestions,
                home = state.home,
                work = state.work,
                onQueryChange = vm::searchFrom,
                onPickSuggestion = vm::pickFrom,
                onUseGps = vm::setFromCurrentLocation,
                onUseHome = vm::setFromHome,
                onUseWork = vm::setFromWork,
            )
            PlaceField(
                label = "To",
                place = state.to,
                query = state.toQuery,
                suggestions = state.toSuggestions,
                home = state.home,
                work = state.work,
                onQueryChange = vm::searchTo,
                onPickSuggestion = vm::pickTo,
                onUseGps = vm::setToCurrentLocation,
                onUseHome = vm::setToHome,
                onUseWork = vm::setToWork,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceField(
    label: String,
    place: Place?,
    query: String,
    suggestions: List<com.scoot.transit.data.PlacesRepo.PlaceSuggestion>,
    home: com.scoot.transit.data.NamedPlace?,
    work: com.scoot.transit.data.NamedPlace?,
    onQueryChange: (String) -> Unit,
    onPickSuggestion: (com.scoot.transit.data.PlacesRepo.PlaceSuggestion) -> Unit,
    onUseGps: () -> Unit,
    onUseHome: () -> Unit,
    onUseWork: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(
                onClick = onUseGps,
                leadingIcon = { Icon(Icons.Filled.MyLocation, contentDescription = null) },
                label = { Text("GPS") },
            )
            if (home != null) AssistChip(
                onClick = onUseHome,
                leadingIcon = { Icon(Icons.Filled.Home, contentDescription = null) },
                label = { Text("Home") },
            )
            if (work != null) AssistChip(
                onClick = onUseWork,
                leadingIcon = { Icon(Icons.Filled.Work, contentDescription = null) },
                label = { Text("Work") },
            )
        }
        OutlinedTextField(
            value = if (query.isNotBlank()) query else (place?.name ?: ""),
            onValueChange = onQueryChange,
            placeholder = { Text("Search address") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        suggestions.take(5).forEach { s ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onPickSuggestion(s) },
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(s.primary, style = MaterialTheme.typography.bodyMedium)
                    Text(s.secondary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimingBlock(timing: TripTiming, onChange: (TripTiming) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val mode = when (timing) {
        is TripTiming.DepartAt -> if (Duration.between(Instant.now(), timing.at).toMinutes() in -2..2) "now" else "depart"
        is TripTiming.ArriveBy -> "arrive"
    }
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Timing", style = MaterialTheme.typography.titleMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == "now",
                    onClick = { onChange(TripTiming.DepartAt(Instant.now())) },
                    label = { Text("Depart now") },
                )
                FilterChip(
                    selected = mode == "depart",
                    onClick = { showPicker = true },
                    label = { Text("Depart at...") },
                )
                FilterChip(
                    selected = mode == "arrive",
                    onClick = { showPicker = true },
                    label = { Text("Arrive by...") },
                )
            }
            Text(formatTiming(timing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
    if (showPicker) {
        TimingPickerDialog(
            initial = (timing as? TripTiming.DepartAt)?.at ?: (timing as? TripTiming.ArriveBy)?.by ?: Instant.now(),
            onDismiss = { showPicker = false },
            onConfirm = { instant, isArrive ->
                showPicker = false
                onChange(if (isArrive) TripTiming.ArriveBy(instant) else TripTiming.DepartAt(instant))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimingPickerDialog(
    initial: Instant,
    onDismiss: () -> Unit,
    onConfirm: (Instant, isArrive: Boolean) -> Unit,
) {
    val zone = ZoneId.of("America/Los_Angeles")
    val zdt = ZonedDateTime.ofInstant(initial, zone)
    val state = rememberTimePickerState(initialHour = zdt.hour, initialMinute = zdt.minute, is24Hour = false)
    var isArrive by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    FilterChip(
                        selected = !isArrive,
                        onClick = { isArrive = false },
                        label = { Text("Depart at") },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = isArrive,
                        onClick = { isArrive = true },
                        label = { Text("Arrive by") },
                    )
                }
                TimePicker(state = state)
            }
        },
        confirmButton = {
            Button(onClick = {
                val now = ZonedDateTime.now(zone)
                val pickedToday = ZonedDateTime.of(LocalDate.now(zone), LocalTime.of(state.hour, state.minute), zone)
                val resolved = if (pickedToday.isBefore(now)) pickedToday.plusDays(1) else pickedToday
                onConfirm(resolved.toInstant(), isArrive)
            }) { Text("OK") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatTiming(timing: TripTiming): String {
    val zone = ZoneId.of("America/Los_Angeles")
    val fmt = DateTimeFormatter.ofPattern("EEE h:mm a").withZone(zone)
    return when (timing) {
        is TripTiming.DepartAt -> "Depart ${fmt.format(timing.at)}"
        is TripTiming.ArriveBy -> "Arrive by ${fmt.format(timing.by)}"
    }
}

@Composable
private fun TripPlanCard(plan: TripPlan) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(plan.totalDuration), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("Leave ${formatTime(plan.leaveByTime)}", style = MaterialTheme.typography.bodyMedium)
            }
            Text("Arrive ${formatTime(plan.arrivalTime)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(4.dp))
            plan.legs.forEach { leg -> LegRow(leg) }
            if (plan.notes.isNotEmpty()) {
                plan.notes.forEach { note ->
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            Text("Scoot total: ${"%.1f".format(plan.totalScootMiles)} mi", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LegRow(leg: TripLeg) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (leg) {
            is TripLeg.Scoot -> {
                Icon(Icons.Filled.DirectionsBike, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scoot ${"%.1f".format(leg.distanceMiles)} mi to ${leg.to.name}", modifier = Modifier.weight(1f))
                Text(formatDuration(leg.duration), style = MaterialTheme.typography.bodySmall)
            }
            is TripLeg.Walk -> {
                Icon(Icons.Filled.DirectionsWalk, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Walk ${"%.1f".format(leg.distanceMiles)} mi to ${leg.to.name}", modifier = Modifier.weight(1f))
                Text(formatDuration(leg.duration), style = MaterialTheme.typography.bodySmall)
            }
            is TripLeg.Transit -> {
                Icon(Icons.Filled.Train, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val routeLabel = leg.routeShortName ?: leg.routeLongName ?: leg.tripId
                    Text("${leg.agency.display} $routeLabel → ${leg.to.name}")
                    leg.headsign?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                    Text(
                        leg.rule.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (leg.rule == com.scoot.transit.domain.ScooterOnTransitRule.PEAK_RESTRICTED)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.tertiary,
                    )
                }
                Text(
                    "${formatTime(leg.departureTime)} → ${formatTime(leg.arrivalTime)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun formatDuration(d: Duration): String {
    val total = d.toMinutes()
    val h = total / 60
    val m = total % 60
    return if (h == 0L) "${m}m" else "${h}h ${m}m"
}

private fun formatTime(instant: Instant): String =
    DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.of("America/Los_Angeles")).format(instant)

private fun TripPlan.signatureKey(): String =
    legs.joinToString("|") { leg ->
        when (leg) {
            is TripLeg.Transit -> "T:${leg.agency.operatorId}:${leg.tripId}"
            is TripLeg.Scoot -> "S:${leg.from.location.lat.hashCode()},${leg.to.location.lat.hashCode()}"
            is TripLeg.Walk -> "W:${leg.from.location.lat.hashCode()},${leg.to.location.lat.hashCode()}"
        }
    } + "@" + departureTime.toEpochMilli()
