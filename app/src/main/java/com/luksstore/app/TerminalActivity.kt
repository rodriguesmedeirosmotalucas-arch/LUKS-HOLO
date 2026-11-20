
package com.luks.shrunner

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TerminalActivity : AppCompatActivity() {

    private var conteudoOriginal = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)

        val output = findViewById<TextView>(R.id.txtTerminal)
        val pesquisa = findViewById<EditText>(R.id.edtInput)
        val btnEnviar = findViewById<Button>(R.id.btnEnviar)
        val btnParar = findViewById<Button>(R.id.btnParar)
        val btnLimpar = findViewById<Button>(R.id.btnLimpar)

        val path = intent.getStringExtra("PATH")

        lifecycleScope.launch {
            try {
                val texto = withContext(Dispatchers.IO) {
                    val arquivo = path?.let { File(it) }
                        ?: throw IllegalArgumentException("Arquivo não informado.")

                    if (!arquivo.isFile) {
                        throw IllegalArgumentException("Arquivo não encontrado.")
                    }

                    arquivo.readText()
                }

                conteudoOriginal = texto
                output.text = texto
            } catch (e: Exception) {
                output.text = "Erro ao abrir arquivo: ${e.message}"
            }
        }

        btnEnviar.setOnClickListener {
            val termo = pesquisa.text.toString()
            output.text = if (termo.isBlank()) {
                conteudoOriginal
            } else {
                conteudoOriginal.lines()
                    .filter { it.contains(termo, ignoreCase = true) }
                    .joinToString("\n")
                    .ifBlank { "Nenhum resultado encontrado." }
            }
        }

        btnParar.setOnClickListener {
            finish()
        }

        btnLimpar.setOnClickListener {
            output.text = ""
        }
    }

    companion object {
        fun start(ctx: android.content.Context, path: String) {
            ctx.startActivity(
                android.content.Intent(ctx, TerminalActivity::class.java)
                    .putExtra("PATH", path)
            )
        }
    }
}
