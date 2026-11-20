
package com.luks.shrunner

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.app.AlertDialog
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { uri ->
            processScript(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnCarregar)
            .setOnClickListener {
                showManagerChoice()
            }
    }

    private fun showManagerChoice() {
        AlertDialog.Builder(this)
            .setTitle("Importar script .sh")
            .setItems(
                arrayOf(
                    "Gerenciador LUKS STORE",
                    "Gerenciador do celular"
                )
            ) { _, which ->
                if (which == 0) {
                    openLuksManager()
                } else {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }

                    filePicker.launch(intent)
                }
            }
            .show()
    }

    private fun openLuksManager() {
        val folder = getExternalFilesDir("scripts")

        val files = folder?.listFiles()
            ?.filter { it.isFile && it.extension.equals("sh", true) }
            ?: emptyList()

        if (files.isEmpty()) {
            AlertDialog.Builder(this)
                .setMessage("Nenhum script encontrado. Importe um arquivo primeiro.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Gerenciador LUKS STORE")
            .setItems(
                files.map { it.name }.toTypedArray()
            ) { _, position ->
                processScript(files[position])
            }
            .show()
    }

    private fun processScript(source: Any) {
        lifecycleScope.launch {
            try {
                val dest = ScriptTransporter.transport(
                    this@MainActivity,
                    source
                )

                PermissionsDialog(
                    this@MainActivity,
                    dest
                ) { path ->

                    ExecuteDialog(
                        this@MainActivity,
                        path
                    ) { mode ->
                        TerminalActivity.start(
                            this@MainActivity,
                            path
                        )
                    }.show()

                }.show()

            } catch (e: Exception) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Erro ao importar")
                    .setMessage(e.message ?: "Erro desconhecido")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
}
