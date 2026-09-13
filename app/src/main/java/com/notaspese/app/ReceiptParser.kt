package com.notaspese.app

object ReceiptParser {
    data class Parsed(val date: String, val merchant: String, val amount: Double, val currency: String)

    fun parse(text: String): Parsed {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val merchant = lines.firstOrNull { it.length in 3..40 && it.any(Char::isLetter) } ?: "Scontrino"

        val dateRegex = Regex("\\b([0-3]?\\d)[./-]([01]?\\d)[./-](20\\d{2}|\\d{2})\\b")
        val date = dateRegex.find(text)?.value ?: ""

        val currency = when {
            Regex("\\bCHF\\b|Fr\\.?", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "CHF"
            Regex("€|\\bEUR\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "EUR"
            else -> "CHF"
        }

        fun numbers(s: String): List<Double> =
            Regex("(?<!\\d)(\\d{1,6}[.,]\\d{2})(?!\\d)")
                .findAll(s).mapNotNull { it.groupValues[1].replace(",", ".").toDoubleOrNull() }.toList()

        val totalLines = lines.filter {
            it.contains("totale", true) || it.contains("total", true) || it.contains("importo", true)
        }

        val amount = totalLines.flatMap(::numbers).maxOrNull()
            ?: lines.flatMap(::numbers).maxOrNull() ?: 0.0

        return Parsed(date, merchant, amount, currency)
    }
}
