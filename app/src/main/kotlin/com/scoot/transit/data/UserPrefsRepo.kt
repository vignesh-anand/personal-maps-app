package com.scoot.transit.data

import com.scoot.transit.data.db.UserPrefDao
import com.scoot.transit.data.db.UserPrefEntity
import com.scoot.transit.domain.LatLng
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class UserPrefsRepo @Inject constructor(
    private val dao: UserPrefDao,
) {
    suspend fun getString(key: String, default: String? = null): String? =
        dao.get(key)?.value ?: default

    suspend fun setString(key: String, value: String) = dao.put(UserPrefEntity(key, value))

    suspend fun getDouble(key: String, default: Double? = null): Double? =
        dao.get(key)?.value?.toDoubleOrNull() ?: default

    suspend fun setDouble(key: String, value: Double) =
        dao.put(UserPrefEntity(key, value.toString()))

    suspend fun getInt(key: String, default: Int? = null): Int? =
        dao.get(key)?.value?.toIntOrNull() ?: default

    suspend fun setInt(key: String, value: Int) =
        dao.put(UserPrefEntity(key, value.toString()))

    suspend fun getBool(key: String, default: Boolean = false): Boolean =
        dao.get(key)?.value?.toBoolean() ?: default

    suspend fun setBool(key: String, value: Boolean) =
        dao.put(UserPrefEntity(key, value.toString()))

    suspend fun getLatLng(key: String): LatLng? {
        val raw = dao.get(key)?.value ?: return null
        val parts = raw.split(",")
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lng = parts[1].toDoubleOrNull() ?: return null
        return LatLng(lat, lng)
    }

    suspend fun setLatLng(key: String, value: LatLng) {
        dao.put(UserPrefEntity(key, "${value.lat},${value.lng}"))
    }

    suspend fun setNamedPlace(prefix: String, name: String, location: LatLng) {
        setString("${prefix}.name", name)
        setLatLng("${prefix}.location", location)
    }

    suspend fun getNamedPlace(prefix: String): NamedPlace? {
        val name = getString("${prefix}.name") ?: return null
        val loc = getLatLng("${prefix}.location") ?: return null
        return NamedPlace(name, loc)
    }

    suspend fun maxScootRangeMiles(): Double = getDouble(KEY_MAX_RANGE) ?: 15.0

    suspend fun setMaxScootRangeMiles(v: Double) = setDouble(KEY_MAX_RANGE, v)

    fun observeAll(): Flow<Map<String, String>> =
        dao.observeAll().map { rows -> rows.associate { it.key to it.value } }

    companion object Keys {
        const val KEY_HOME = "place.home"
        const val KEY_WORK = "place.work"
        const val KEY_MAX_RANGE = "scoot.max_range_mi"
        const val KEY_NOTIF_ALERTS = "notif.alerts"
        const val KEY_NOTIF_LAST_TRAIN = "notif.last_train"
    }
}

data class NamedPlace(val name: String, val location: LatLng)
