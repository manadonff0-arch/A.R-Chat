package com.example.core

data class CountryDialCode(
    val name: String,
    val code: String,
    val flag: String
)

enum class PakistanCarrier(val label: String, val prefixes: List<String>) {
    JAZZ("Jazz", listOf("300", "301", "302", "303", "304", "305", "306", "307", "308", "309", "320", "321", "322", "323", "324", "325")),
    ZONG("Zong", listOf("310", "311", "312", "313", "314", "315", "316", "317", "318", "319")),
    UFONE("Ufone", listOf("330", "331", "332", "333", "334", "335", "336", "337")),
    TELENOR("Telenor", listOf("340", "341", "342", "343", "344", "345", "346", "347", "348", "349")),
    SCO("SCO / Special", listOf("355")),
    OTHER("Pakistani Mobile", emptyList())
}

object PakistanPhoneValidator {
    val supportedCountries = listOf(
        CountryDialCode("Pakistan", "+92", "PK"),
        CountryDialCode("United Arab Emirates", "+971", "AE"),
        CountryDialCode("Saudi Arabia", "+966", "SA"),
        CountryDialCode("United Kingdom", "+44", "GB"),
        CountryDialCode("United States", "+1", "US")
    )

    fun normalizeDigits(rawInput: String): String {
        val digitsOnly = rawInput.filter { it.isDigit() }
        return when {
            digitsOnly.startsWith("92") && digitsOnly.length >= 12 -> digitsOnly.removePrefix("92")
            digitsOnly.startsWith("0") && digitsOnly.length >= 11 -> digitsOnly.removePrefix("0")
            else -> digitsOnly
        }
    }

    fun detectCarrier(nationalNumber: String): PakistanCarrier {
        val clean = normalizeDigits(nationalNumber)
        if (clean.length < 3) return PakistanCarrier.OTHER
        val prefix = clean.substring(0, 3)
        return PakistanCarrier.entries.firstOrNull { carrier ->
            prefix in carrier.prefixes
        } ?: PakistanCarrier.OTHER
    }

    fun isValidNumber(countryCode: String, rawInput: String): Boolean {
        val clean = normalizeDigits(rawInput)
        return if (countryCode == "+92") {
            clean.length == 10 && clean.startsWith("3")
        } else {
            clean.length in 7..14
        }
    }

    fun formatE164(countryCode: String, rawInput: String): String {
        val clean = normalizeDigits(rawInput)
        return "$countryCode$clean"
    }
}
