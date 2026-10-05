package com.hedefit.app.ui.settings

import com.hedefit.app.data.model.BodyMeasurementData
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Kilo kayıtlarından trend, hedef ilerlemesi ve VKİ hesapları (saf fonksiyonlar). */
object WeightTrend {
    data class Point(val date: LocalDate, val kg: Double)

    enum class BmiBand { Low, Healthy, High }

    fun points(measurements: List<BodyMeasurementData>): List<Point> =
        measurements.mapNotNull { m ->
            val kg = m.weightKg ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(m.date.take(10)) }.getOrNull() ?: return@mapNotNull null
            Point(date, kg)
        }.sortedBy { it.date }

    /** Her nokta için son [windowDays] gündeki kayıtların ortalaması; günlük dalgalanmayı yumuşatır. */
    fun smooth(points: List<Point>, windowDays: Long = 7): List<Double> =
        points.map { p ->
            val window = points.filter { it.date <= p.date && ChronoUnit.DAYS.between(it.date, p.date) < windowDays }
            window.map { it.kg }.average()
        }

    /**
     * Son [lookbackDays] gündeki en küçük kareler eğiminden haftalık değişim (kg/hafta).
     * En az 2 kayıt ve 5 günlük aralık yoksa null: kısa aralık sahte bir trend üretir.
     */
    fun weeklyRateKg(points: List<Point>, today: LocalDate = LocalDate.now(), lookbackDays: Long = 28): Double? {
        val recent = points.filter { ChronoUnit.DAYS.between(it.date, today) in 0 until lookbackDays }
        if (recent.size < 2) return null
        val origin = recent.first().date
        val xs = recent.map { ChronoUnit.DAYS.between(origin, it.date).toDouble() }
        if (xs.last() - xs.first() < 5) return null
        val ys = recent.map { it.kg }
        val xMean = xs.average()
        val yMean = ys.average()
        val denominator = xs.sumOf { (it - xMean) * (it - xMean) }
        if (denominator == 0.0) return null
        val slopePerDay = xs.indices.sumOf { (xs[it] - xMean) * (ys[it] - yMean) } / denominator
        return slopePerDay * 7
    }

    fun bmi(weightKg: Double?, heightCm: Double?): Double? {
        if (weightKg == null || heightCm == null) return null
        if (heightCm !in 100.0..250.0 || weightKg !in 20.0..400.0) return null
        val meters = heightCm / 100.0
        return weightKg / (meters * meters)
    }

    fun bmiBand(bmi: Double): BmiBand = when {
        bmi < 18.5 -> BmiBand.Low
        bmi < 25.0 -> BmiBand.Healthy
        else -> BmiBand.High
    }

    /** VKİ yalnızca yetişkinler için anlamlı; yaş bilinmiyorsa gösterilir, 18 altında gösterilmez. */
    fun bmiApplies(age: Int?): Boolean = age == null || age >= 18

    /** Başlangıç → hedef arasındaki ilerleme, 0..1. Başlangıç hedefe eşitse null. */
    fun goalProgress(startKg: Double, currentKg: Double, targetKg: Double): Float? {
        val total = targetKg - startKg
        if (abs(total) < 0.05) return null
        return ((currentKg - startKg) / total).toFloat().coerceIn(0f, 1f)
    }

    /** Hedefe doğru gidiliyorsa tahmini ulaşma tarihi; yön ters ya da hız çok düşükse null. */
    fun projectedDate(currentKg: Double, targetKg: Double, weeklyRateKg: Double?, today: LocalDate = LocalDate.now()): LocalDate? {
        weeklyRateKg ?: return null
        val remaining = targetKg - currentKg
        if (abs(remaining) < 0.05) return null
        if (abs(weeklyRateKg) < 0.05 || remaining * weeklyRateKg < 0) return null
        val weeks = remaining / weeklyRateKg
        if (weeks > 104) return null
        return today.plusDays((weeks * 7).toLong())
    }
}
