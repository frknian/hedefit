package com.hedefit.app.gym

// Normalised (0..1) ellipse centres/radii over res/drawable/muscle_anatomy.png (front left, back right).
internal data class AnatomicalRegion(val muscle: String, val x: Float, val y: Float, val rx: Float, val ry: Float)

internal val muscleRegionsFront = listOf(
    AnatomicalRegion("neck", .312f, .152f, .028f, .020f),
    AnatomicalRegion("front_delts", .258f, .192f, .030f, .023f), AnatomicalRegion("front_delts", .360f, .192f, .032f, .023f),
    AnatomicalRegion("side_delts", .207f, .208f, .026f, .024f), AnatomicalRegion("side_delts", .421f, .208f, .026f, .024f),
    AnatomicalRegion("chest", .312f, .245f, .072f, .032f),
    AnatomicalRegion("biceps", .183f, .285f, .024f, .038f), AnatomicalRegion("biceps", .438f, .285f, .024f, .038f),
    AnatomicalRegion("abs", .315f, .345f, .048f, .054f),
    AnatomicalRegion("forearms", .150f, .400f, .026f, .042f), AnatomicalRegion("forearms", .462f, .400f, .026f, .042f),
    AnatomicalRegion("abductors", .228f, .512f, .022f, .030f), AnatomicalRegion("abductors", .396f, .512f, .022f, .030f),
    AnatomicalRegion("adductors", .285f, .548f, .022f, .038f), AnatomicalRegion("adductors", .340f, .548f, .022f, .038f),
    AnatomicalRegion("quads", .252f, .578f, .042f, .052f), AnatomicalRegion("quads", .373f, .578f, .042f, .052f),
    AnatomicalRegion("calves", .245f, .748f, .031f, .040f), AnatomicalRegion("calves", .377f, .748f, .031f, .040f),
)

internal val muscleRegionsBack = listOf(
    AnatomicalRegion("neck", .687f, .152f, .026f, .020f),
    AnatomicalRegion("traps", .688f, .196f, .052f, .026f),
    AnatomicalRegion("rear_delts", .584f, .212f, .030f, .024f), AnatomicalRegion("rear_delts", .790f, .212f, .030f, .024f),
    AnatomicalRegion("upper_back", .689f, .272f, .068f, .030f),
    AnatomicalRegion("triceps", .568f, .292f, .024f, .038f), AnatomicalRegion("triceps", .813f, .292f, .026f, .038f),
    AnatomicalRegion("lats", .688f, .345f, .076f, .042f),
    AnatomicalRegion("forearms", .542f, .400f, .026f, .042f), AnatomicalRegion("forearms", .844f, .400f, .026f, .042f),
    AnatomicalRegion("lower_back", .687f, .437f, .048f, .030f),
    AnatomicalRegion("glutes", .687f, .503f, .078f, .038f),
    AnatomicalRegion("abductors", .604f, .532f, .022f, .030f), AnatomicalRegion("abductors", .772f, .532f, .022f, .030f),
    AnatomicalRegion("adductors", .652f, .578f, .021f, .036f), AnatomicalRegion("adductors", .722f, .578f, .021f, .036f),
    AnatomicalRegion("hamstrings", .628f, .605f, .040f, .048f), AnatomicalRegion("hamstrings", .746f, .605f, .040f, .048f),
    AnatomicalRegion("calves", .621f, .735f, .031f, .040f), AnatomicalRegion("calves", .754f, .735f, .031f, .040f),
)

internal val muscleRegions = muscleRegionsFront + muscleRegionsBack
