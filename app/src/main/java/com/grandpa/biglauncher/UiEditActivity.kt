package com.grandpa.biglauncher

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

class UiEditActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var box: LinearLayout

    private val paletteNames = listOf("Default", "Green", "Red", "Blue", "Pink", "Orange", "Purple", "Teal", "Grey")
    private val paletteColors = listOf(
        null, 0xFF2E7D32.toInt(), 0xFFC62828.toInt(), 0xFF1565C0.toInt(), 0xFFAD1457.toInt(),
        0xFFE65100.toInt(), 0xFF6A1B9A.toInt(), 0xFF00695C.toInt(), 0xFF455A64.toInt()
    )

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        Ui.applyTheme(prefs)
        val p = Ui.dp(this, 16)
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(p, Ui.dp(this@UiEditActivity, 32), p, p) }
        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(box) })
        build()
    }

    private fun appLabel(pkg: String) = prefs.label(pkg) ?: try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    private fun add(label: String, onClick: () -> Unit) {
        val btn = Button(this).apply { text = label; textSize = 16f; isAllCaps = false; setOnClickListener { onClick() } }
        box.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = Ui.dp(this@UiEditActivity, 8) })
    }

    private fun build() {
        Ui.applyTheme(prefs)
        box.removeAllViews()
        box.addView(Ui.text(this, "Edit look", 30f, Color.WHITE, true))

        val colNames = listOf("Auto", "1 big column", "2 columns", "3 columns")
        val sizes = listOf("Small", "Medium", "Large", "Huge")
        val bgs = listOf("Dark grey", "Black", "Navy")

        add("Columns: ${colNames[prefs.columns.coerceIn(0, 3)]}") { choose("Columns", colNames, prefs.columns) { prefs.columns = it } }
        add("Text size: ${sizes[prefs.textLevel.coerceIn(0, 3)]}") { choose("Text size", sizes, prefs.textLevel) { prefs.textLevel = it } }
        add("Background: ${bgs[prefs.bgTheme.coerceIn(0, 2)]}") { choose("Background", bgs, prefs.bgTheme) { prefs.bgTheme = it } }
        add("'+ Add app' tile: ${if (prefs.showPlus) "ON" else "OFF"}") { prefs.showPlus = !prefs.showPlus; build() }
        add("App icons on tiles: ${if (prefs.showIcons) "ON" else "OFF"}") { prefs.showIcons = !prefs.showIcons; build() }
        add("Arrange, rename, recolor apps") { arrange() }
        add("Widgets page: choose widgets") { widgets() }
        add("Edit 'My info' card text") { editInfo() }
        add("Done") { finish() }
    }

    private fun choose(title: String, items: List<String>, cur: Int, set: (Int) -> Unit) {
        AlertDialog.Builder(this).setTitle(title)
            .setSingleChoiceItems(items.toTypedArray(), cur.coerceIn(0, items.size - 1)) { d, i -> set(i); d.dismiss(); build() }
            .show()
    }

    private fun arrange() {
        val list = prefs.visible()
        if (list.isEmpty()) return
        AlertDialog.Builder(this).setTitle("Tap an app")
            .setItems(list.map { appLabel(it) }.toTypedArray()) { _, i -> appMenu(list, i) }
            .setNegativeButton("Close", null).show()
    }

    private fun appMenu(list: List<String>, i: Int) {
        val pkg = list[i]
        AlertDialog.Builder(this).setTitle(appLabel(pkg))
            .setItems(arrayOf("Move up", "Move down", "Rename", "Change color", "Hide from home")) { _, which ->
                val m = list.toMutableList()
                when (which) {
                    0 -> if (i > 0) { m[i] = m[i - 1]; m[i - 1] = pkg; prefs.setVisible(m); arrange() } else arrange()
                    1 -> if (i < m.size - 1) { m[i] = m[i + 1]; m[i + 1] = pkg; prefs.setVisible(m); arrange() } else arrange()
                    2 -> rename(pkg)
                    3 -> recolor(pkg)
                    4 -> { m.removeAt(i); prefs.setVisible(m); arrange() }
                }
            }.show()
    }

    private fun rename(pkg: String) {
        val et = EditText(this).apply { setText(prefs.label(pkg) ?: ""); hint = "New name (empty = original)" }
        AlertDialog.Builder(this).setTitle("Rename").setView(et)
            .setPositiveButton("Save") { _, _ -> prefs.setLabel(pkg, et.text.toString().trim()); arrange() }
            .setNegativeButton("Cancel") { _, _ -> arrange() }.show()
    }

    private fun recolor(pkg: String) {
        AlertDialog.Builder(this).setTitle("Tile color")
            .setItems(paletteNames.toTypedArray()) { _, i -> prefs.setColor(pkg, paletteColors[i]); arrange() }.show()
    }

    private fun widgets() {
        val checked = BooleanArray(Prefs.ALL_WIDGETS.size) { Prefs.ALL_WIDGETS[it] in prefs.widgets() }
        AlertDialog.Builder(this).setTitle("Widgets")
            .setMultiChoiceItems(Prefs.WIDGET_NAMES.toTypedArray(), checked) { _, i, v -> checked[i] = v }
            .setPositiveButton("Save") { _, _ ->
                prefs.setWidgets(Prefs.ALL_WIDGETS.filterIndexed { i, _ -> checked[i] })
            }.setNegativeButton("Cancel", null).show()
    }

    private fun editInfo() {
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(prefs.infoText); minLines = 4
        }
        AlertDialog.Builder(this).setTitle("My info (name, address, medicines...)").setView(et)
            .setPositiveButton("Save") { _, _ -> prefs.infoText = et.text.toString() }
            .setNegativeButton("Cancel", null).show()
    }
}
