package com.hedefit.app.ui.i18n

import com.hedefit.app.data.model.DashboardData
import com.hedefit.app.data.model.WorkoutExerciseData

/**
 * Hareket adları programa oluşturulduğu dilde kaydedilir. Katalogdaki TR/EN adları
 * kimliğe (yoksa ada) göre eşleyip ekranda uygulama dilinde gösteririz; kayıtlı veri değişmez.
 */
data class ExerciseNameIndex(val byId: Map<String, Pair<String, String>>, val byName: Map<String, Pair<String, String>>) {
    fun localize(id: String, name: String): String {
        val pair = byId[id] ?: byName[name.lowercase()] ?: return name
        return tr(pair.first, pair.second)
    }

    companion object {
        val Empty = ExerciseNameIndex(emptyMap(), emptyMap())
    }
}

private fun List<WorkoutExerciseData>.localized(index: ExerciseNameIndex) = map { it.copy(name = index.localize(it.id, it.name)) }

fun DashboardData.withLocalizedExercises(index: ExerciseNameIndex): DashboardData =
    if (index.byId.isEmpty()) this
    else copy(
        workouts = workouts.localized(index),
        workoutPrograms = workoutPrograms.map { it.copy(exercises = it.exercises.localized(index)) },
    )
