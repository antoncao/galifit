package com.tfm.galifit.notifications

sealed class SedentaryPermissionOutcome {
    data object Granted : SedentaryPermissionOutcome()
    data object Denied : SedentaryPermissionOutcome()

    data object NotificationsDenied : SedentaryPermissionOutcome()

    data object SensorDenied : SedentaryPermissionOutcome()

    data object SensorUnavailable : SedentaryPermissionOutcome()

    data object ProviderMissingOrOutdated : SedentaryPermissionOutcome()

    data object Unavailable : SedentaryPermissionOutcome()
    data class Error(val message: String?) : SedentaryPermissionOutcome()

    fun userMessage(): String = when (this) {
        Granted -> ""
        NotificationsDenied -> """
            Galifit necesita permiso para mostrar notificaciones y poder avisarte si llevas mucho rato sin moverte.

            1. Pulsa «Reintentar» y acepta el permiso.
            2. Si lo denegaste antes, ve a Ajustes del móvil → Apps → Galifit → Permisos → Notificaciones.
        """.trimIndent()
        Denied -> """
            Galifit necesita leer tus pasos en Health Connect para avisarte si llevas mucho rato sin moverte.

            Si Galifit no aparece en la lista de apps:
            1. Pulsa «Reintentar» para volver a pedir el permiso.
            2. Si sigue sin salir, actualiza Health Connect en Play Store y reinstala Galifit.

            Si ya aparece Galifit:
            1. Abre Health Connect.
            2. Ve a Permisos de apps.
            3. Elige Galifit y activa Pasos (lectura).
        """.trimIndent()
        SensorDenied -> """
            Galifit necesita el permiso de actividad física para contar pasos con el sensor de tu móvil.

            1. Pulsa «Reintentar» y acepta el permiso.
            2. Si lo denegaste antes, ve a Ajustes del móvil → Apps → Galifit → Permisos → Actividad física.
        """.trimIndent()
        SensorUnavailable ->
            "Este móvil no tiene un sensor de pasos compatible. Prueba la opción de Health Connect si usas reloj u otras apps."
        ProviderMissingOrOutdated ->
            "Actualiza o instala la app Health Connect desde Google Play.\n\nEnlazar Samsung Health no basta: Galifit necesita el proveedor de Health Connect de Google."
        Unavailable ->
            "Health Connect no está disponible en este dispositivo o perfil de usuario."
        is Error ->
            message?.let { "No se pudo activar sedentarismo: $it" }
                ?: "No se pudo activar sedentarismo. Revisa Health Connect y los permisos de Galifit."
    }
}
