package com.notaspese.app

data class Expense(
    val id: Long = System.currentTimeMillis(),
    var date: String,
    var merchant: String,
    var amount: Double,
    var currency: String,
    var imagePath: String? = null
)
