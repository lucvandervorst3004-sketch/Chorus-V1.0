package com.example.qrspotify.classic

object ClassicModeConfig {

    const val EXTRA_CLASSIC_ROLE = "extra_classic_role"
    const val EXTRA_CLASSIC_VARIANT = "extra_classic_variant"

    const val ROLE_SOLO = "solo"
    const val ROLE_PARTY = "party"

    const val VARIANT_STANDARD = "standard"
    const val VARIANT_HIGH_PRESSURE = "high_pressure"

    fun sanitizeRole(role: String?): String {
        return if (role == ROLE_SOLO) ROLE_SOLO else ROLE_PARTY
    }

    fun sanitizeVariant(variant: String?): String {
        return if (variant == VARIANT_HIGH_PRESSURE) VARIANT_HIGH_PRESSURE else VARIANT_STANDARD
    }

    fun isSolo(role: String?): Boolean = sanitizeRole(role) == ROLE_SOLO
    fun isParty(role: String?): Boolean = sanitizeRole(role) == ROLE_PARTY
    fun isHighPressure(variant: String?): Boolean = sanitizeVariant(variant) == VARIANT_HIGH_PRESSURE

    fun roleLabel(role: String?): String {
        return if (isSolo(role)) "Solo Mode" else "Party Mode"
    }

    fun variantLabel(variant: String?): String {
        return if (isHighPressure(variant)) "High Pressure" else "Casual"
    }
}
