
package com.luks.shrunner

import android.content.Context
import android.app.AlertDialog

class ExecuteDialog(
    ctx: Context,
    private val path: String,
    private val onExecute: (String) -> Unit
) {

    fun show() {
        val options = arrayOf(
            "Execute in system environment"
        )

        AlertDialog.Builder(ctx)
            .setTitle("Please select an action to perform")
            .setMessage(
                "Execute the selected script in the application environment."
            )
            .setSingleChoiceItems(options, 0, null)
            .setPositiveButton("EXECUTE") { _, _ ->
                onExecute("SYSTEM")
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }
}
