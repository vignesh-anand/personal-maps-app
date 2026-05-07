package com.scoot.transit.domain

/**
 * Transit agencies we care about for v1. Maps to 511.org operator IDs.
 */
enum class Agency(val operatorId: String, val display: String) {
    CALTRAIN("CT", "Caltrain"),
    BART("BA", "BART"),
    AC_TRANSIT("3D", "AC Transit"),
    SAMTRANS("SM", "SamTrans"),
    VTA("SC", "VTA"),
    MUNI("SF", "SF Muni");

    companion object {
        fun fromOperatorId(id: String): Agency? = entries.firstOrNull { it.operatorId.equals(id, true) }
    }
}
