package com.notaspese.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ExpenseStore(context: Context) {
    private val prefs = context.getSharedPreferences("nota_spese", Context.MODE_PRIVATE)

    fun saveProfile(nome: String, cognome: String, azienda: String, mese: String) {
        prefs.edit().putString("nome", nome).putString("cognome", cognome)
            .putString("azienda", azienda).putString("mese", mese).apply()
    }

    fun profile(): Map<String, String> = mapOf(
        "nome" to (prefs.getString("nome", "") ?: ""),
        "cognome" to (prefs.getString("cognome", "") ?: ""),
        "azienda" to (prefs.getString("azienda", "") ?: ""),
        "mese" to (prefs.getString("mese", "") ?: "")
    )

    fun loadExpenses(): MutableList<Expense> {
        val arr = JSONArray(prefs.getString("expenses", "[]"))
        val out = mutableListOf<Expense>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += Expense(
                o.getLong("id"), o.getString("date"), o.getString("merchant"),
                o.getDouble("amount"), o.getString("currency"),
                if (o.isNull("imagePath")) null else o.getString("imagePath")
            )
        }
        return out
    }

    fun saveExpenses(expenses: List<Expense>) {
        val arr = JSONArray()
        expenses.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("date", it.date); put("merchant", it.merchant)
                put("amount", it.amount); put("currency", it.currency); put("imagePath", it.imagePath)
            })
        }
        prefs.edit().putString("expenses", arr.toString()).apply()
    }
}
