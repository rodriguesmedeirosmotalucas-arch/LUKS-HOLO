
package com.luks.shrunner

import android.app.AlertDialog
import android.content.Context
import android.widget.TextView
import java.io.File

class PermissionsDialog(
    private val ctx: Context,
    private val file: File,
    private val onOk: (String) -> Unit
) {
    fun show() {
        val info = TextView(ctx).apply {
            text = if (file.exists()) {
                "Arquivo: ${file.name}\nTamanho: ${file.length()} bytes\n\nO arquivo será apenas visualizado."
            } else {
                "Arquivo não encontrado."
            }
            setPadding(32, 24, 32, 24)
        }

        AlertDialog.Builder(ctx)
            .setTitle("Informações do arquivo")
            .setView(info)
            .setNegativeButton("FECHAR", null)
            .setPositiveButton("CONTINUAR") { _, _ ->
                if (file.exists()) {
                    onOk(file.absolutePath)
                }
            }
            .show()
    }
}
