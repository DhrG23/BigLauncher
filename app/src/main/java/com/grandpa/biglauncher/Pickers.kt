package com.grandpa.biglauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent

object Pickers {
    /** Multi-choice list of every installed launchable app. Keeps existing order, appends new ones. */
    fun apps(a: Activity, title: String, current: List<String>, save: (List<String>) -> Unit) {
        val pm = a.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first != a.packageName }
            .sortedBy { it.second.lowercase() }
        val checked = BooleanArray(apps.size) { apps[it].first in current }
        AlertDialog.Builder(a).setTitle(title)
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, i, v -> checked[i] = v }
            .setPositiveButton("Save") { _, _ ->
                val chosen = apps.filterIndexed { i, _ -> checked[i] }.map { it.first }
                val kept = current.filter { chosen.contains(it) }
                save(kept + chosen.filter { it !in kept })
            }
            .setNegativeButton("Cancel", null).show()
    }
}
