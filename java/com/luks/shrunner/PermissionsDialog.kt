
package com.luks.shrunner

import android.content.Context
import android.app.AlertDialog
import android.widget.GridLayout
import android.widget.TextView
import android.widget.CheckBox
import android.widget.LinearLayout
import java.io.File

class PermissionsDialog(
    ctx: Context,
    private val file: File,
    private val onOk: (String) -> Unit
) {

    private var bits = 493 // 755

    private val labels = listOf("Owner", "Group", "Other")
    private val perms = listOf("Read", "Write", "Exec")

    fun show() {
        val grid = GridLayout(ctx).apply {
            columnCount = 4
        }

        grid.addView(TextView(ctx))

        perms.forEach {
            grid.addView(TextView(ctx).apply {
                text = it
                setPadding(8, 8, 8, 8)
            })
        }

        val valueText = TextView(ctx)

        for (row in 0..2) {
            grid.addView(TextView(ctx).apply {
                text = labels[row]
                setPadding(8, 8, 8, 8)
            })

            for (col in 0..2) {
                val idx = row * 3 + col

                grid.addView(CheckBox(ctx).apply {
                    isChecked = (bits shr (8 - idx) and 1) == 1

                    setOnCheckedChangeListener { _, checked ->
                        bits = if (checked) {
                            bits or (1 shl (8 - idx))
                        } else {
                            bits and (1 shl (8 - idx)).inv()
                        }

                        valueText.text = octal(bits)
                    }
                })
            }
        }

        valueText.text = octal(bits)

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            addView(grid)
            addView(TextView(ctx).apply {
                text = "Permissions"
            })
            addView(valueText)
        }

        AlertDialog.Builder(ctx)
            .setTitle("Permissions")
            .setView(layout)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("OK") { _, _ ->
                try {
                    if (!file.exists()) {
                        valueText.text = "File not found"
                        return@setPositiveButton
                    }

                    val mode = octal(bits)
                    val success = file.setReadable(
                        (bits and 256) != 0, false
                    )

                    onOk(file.absolutePath)
                } catch (e: Exception) {
                    valueText.text = "Error: ${e.message}"
                }
            }
            .show()
    }

    private fun octal(value: Int): String {
        return value.toString(8).padStart(3, '0')
    }
}
