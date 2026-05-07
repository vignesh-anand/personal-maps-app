package com.scoot.transit.data

import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.LocationBias
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.model.Place as GmsPlace
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import android.content.Context
import com.scoot.transit.domain.LatLng
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Wraps Google Places autocomplete + place-fetch for "From"/"To" address search. Biased to the
 * Bay Area to keep results relevant.
 */
@Singleton
class PlacesRepo @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val sessionToken = AutocompleteSessionToken.newInstance()

    /** Bay Area bounding box. */
    private val bayAreaBias: LocationBias = RectangularBounds.newInstance(
        LatLngBounds(
            com.google.android.gms.maps.model.LatLng(37.10, -122.55),
            com.google.android.gms.maps.model.LatLng(38.10, -121.70),
        ),
    )

    suspend fun autocomplete(query: String): List<PlaceSuggestion> {
        if (query.isBlank()) return emptyList()
        if (!Places.isInitialized()) return emptyList()
        val client = Places.createClient(context)
        val req = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setSessionToken(sessionToken)
            .setLocationBias(bayAreaBias)
            .build()
        return runCatching {
            val resp = client.findAutocompletePredictions(req).await()
            resp.autocompletePredictions.map {
                PlaceSuggestion(
                    placeId = it.placeId,
                    primary = it.getPrimaryText(null).toString(),
                    secondary = it.getSecondaryText(null).toString(),
                )
            }
        }.onFailure { Timber.w(it, "Places autocomplete failed") }
            .getOrDefault(emptyList())
    }

    suspend fun fetch(placeId: String): PlaceDetails? {
        if (!Places.isInitialized()) return null
        val client = Places.createClient(context)
        val req = FetchPlaceRequest.newInstance(
            placeId,
            listOf(GmsPlace.Field.NAME, GmsPlace.Field.LAT_LNG, GmsPlace.Field.ADDRESS),
        )
        return runCatching {
            val resp = client.fetchPlace(req).await()
            val ll = resp.place.latLng ?: return@runCatching null
            PlaceDetails(
                name = resp.place.name ?: resp.place.address.orEmpty(),
                location = LatLng(ll.latitude, ll.longitude),
                address = resp.place.address.orEmpty(),
            )
        }.onFailure { Timber.w(it, "Places fetch failed") }
            .getOrNull()
    }

    data class PlaceSuggestion(val placeId: String, val primary: String, val secondary: String)
    data class PlaceDetails(val name: String, val location: LatLng, val address: String)
}
