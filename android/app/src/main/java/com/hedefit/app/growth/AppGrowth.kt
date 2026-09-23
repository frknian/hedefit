package com.hedefit.app.growth

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory

object AppGrowth {
    private const val PREFS = "hedefit_growth"
    private const val KEY_LAST_PROMPT_AT = "review_last_prompt_at"
    private const val KEY_LAST_PROMPT_WORKOUTS = "review_last_prompt_workouts"
    private const val MIN_DAYS_BETWEEN_PROMPTS = 60L

    fun storeUrl(context: Context) = "https://play.google.com/store/apps/details?id=${context.packageName}"

    fun openStoreListing(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(storeUrl(context))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun defaultShareMessage(context: Context, en: Boolean) =
        if (en) "I'm tracking my workouts, meals and progress with Hedefit — it builds a plan for you and has an AI coach. Try it: ${storeUrl(context)}"
        else "Antrenmanlarımı, beslenmemi ve ilerlememi Hedefit ile takip ediyorum; sana göre program çıkarıyor ve bir AI koçu var. Sen de dene: ${storeUrl(context)}"

    fun shareApp(context: Context, message: String, en: Boolean) {
        val text = message.trim().let { if (storeUrl(context) in it) it else "$it\n${storeUrl(context)}" }
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, if (en) "Share Hedefit" else "Hedefit'i paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Asks for a Play review right after a satisfying moment (a finished workout) once real usage exists:
     * first at 3 completed workouts, then every further 10, never more often than every 60 days.
     * Play itself may still decide not to show the sheet.
     */
    fun maybeRequestReview(activity: Activity, completedWorkouts: Int) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastAt = prefs.getLong(KEY_LAST_PROMPT_AT, 0L)
        val lastWorkouts = prefs.getInt(KEY_LAST_PROMPT_WORKOUTS, -1)
        val dueByCount = if (lastWorkouts < 0) completedWorkouts >= 3 else completedWorkouts >= lastWorkouts + 10
        val dueByTime = now - lastAt >= MIN_DAYS_BETWEEN_PROMPTS * 24 * 60 * 60 * 1000
        if (!dueByCount || !dueByTime) return
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnSuccessListener { info ->
            prefs.edit().putLong(KEY_LAST_PROMPT_AT, now).putInt(KEY_LAST_PROMPT_WORKOUTS, completedWorkouts).apply()
            manager.launchReviewFlow(activity, info)
        }
    }
}
