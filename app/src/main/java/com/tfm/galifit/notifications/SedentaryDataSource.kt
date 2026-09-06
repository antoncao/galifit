package com.tfm.galifit.notifications

object SedentaryDataSource {

    const val HEALTH_CONNECT = "health_connect"

    const val DEVICE_SENSOR = "device_sensor"

    fun isValid(value: String): Boolean =
        value == HEALTH_CONNECT || value == DEVICE_SENSOR

    fun defaultForLegacy(enabled: Boolean, stored: String?): String {
        if (!enabled) return ""
        if (!stored.isNullOrBlank() && isValid(stored)) return stored
        return HEALTH_CONNECT
    }
}
