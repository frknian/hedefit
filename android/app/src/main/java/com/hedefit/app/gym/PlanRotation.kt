package com.hedefit.app.gym

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * How often a fresh training block is offered. Blocks are calendar-aligned
 * (weekly = every Monday, monthly = every 1st) and numbered exactly like
 * lib/training/rotation.ts on the server, so both sides agree on when a plan
 * belongs to an older block.
 */
enum class PlanRotationPeriod(val key: String) {
    Weekly("weekly"),
    Monthly("monthly");

    companion object {
        /** Anything but "weekly" is monthly, the default. */
        fun fromKey(key: String?) = if (key == "weekly") Weekly else Monthly
    }
}

object PlanRotation {
    // 1970-01-05 (epoch day 4) is a Monday: weekly blocks are counted from there.
    private const val FIRST_MONDAY_EPOCH_DAY = 4L

    fun blockId(period: PlanRotationPeriod, date: LocalDate): Long = when (period) {
        PlanRotationPeriod.Weekly -> Math.floorDiv(date.toEpochDay() - FIRST_MONDAY_EPOCH_DAY, 7L)
        PlanRotationPeriod.Monthly -> date.year * 12L + (date.monthValue - 1)
    }

    fun nextBlockStart(period: PlanRotationPeriod, date: LocalDate): LocalDate = when (period) {
        PlanRotationPeriod.Weekly -> date.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        PlanRotationPeriod.Monthly -> date.withDayOfMonth(1).plusMonths(1)
    }

    /** True once a plan generated on [generatedOn] belongs to an earlier block than [today]. */
    fun isDue(period: PlanRotationPeriod, generatedOn: LocalDate?, today: LocalDate): Boolean =
        generatedOn != null && blockId(period, today) > blockId(period, generatedOn)
}
