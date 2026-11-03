package com.hectormtzm.liftlog.data

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

enum class WeightUnit(val label: String, private val perKg: Double) {
    LB("lb", 2.20462262185),
    KG("kg", 1.0);

    fun fromKg(kg: Double): Double = kg * perKg
    fun toKg(value: Double): Double = value / perKg

    /** Formats a kilogram value in this unit: "135", "62.5". */
    fun format(kg: Double): String = formatNumber(fromKg(kg))

    companion object {
        fun formatNumber(value: Double): String {
            val rounded = (value * 10).roundToLong() / 10.0
            return if (abs(rounded - rounded.roundToLong()) < 1e-9) {
                rounded.roundToLong().toString()
            } else {
                String.format(Locale.US, "%.1f", rounded)
            }
        }
    }
}

/** Epley estimated one-rep max. */
fun estimatedOneRepMax(weightKg: Double, reps: Int): Double =
    if (reps <= 0) 0.0 else if (reps == 1) weightKg else weightKg * (1 + reps / 30.0)
