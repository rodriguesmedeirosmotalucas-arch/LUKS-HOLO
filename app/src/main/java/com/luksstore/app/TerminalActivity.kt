
package com.luks.shrunner

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter

class TerminalActivity : AppCompatActivity() {

    private var process: Process? = null
    private var input: BufferedWriter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)

        val path = intent.getStringExtra("PATH") ?: run {
            finish()
            return
        }

        val output = findViewById<TextView>(R.id.txtTerminal)
        val editInput = findViewById<EditText>(R.id.edtInput)

        val btnEnviar = findViewById<Button>(R.id.btnEnviar)
        val btnParar = findViewById<Button>(R.id.btnParar)
        val btnLimpar = findViewById<Button>(R.id.btnLimpar)

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val proc = ProcessBuilder("sh", path)
                        .redirectErrorStream(true)
                        .start()

                    process = proc
                    input = proc.outputStream.bufferedWriter()

                    proc.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            withContext(Dispatchers.Main) {
                                output.append("$line\n")
                            }
                        }
                    }

                    proc.waitFor()
                }
            } catch (e: Exception) {
                output.append("\nErro: ${e.message}\n")
            }
        }

        btnEnviar.setOnClickListener {
            val command = editInput.text.toString()

            try {
                input?.let {
                    it.write(command)
                    it.newLine()
                    it.flush()
                    editInput.text.clear()
                } ?: output.append("Terminal ainda não iniciado.\n")
            } catch (e: Exception) {
                output.append("Erro ao enviar: ${e.message}\n")
            }
        }

        btnParar.setOnClickListener {
            process?.destroy()
            output.append("\nProcesso encerrado.\n")
        }

        btnLimpar.setOnClickListener {
            output.text = ""
        }
    }

    override fun onDestroy() {
        process?.destroy()
        input?.close()
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context, path: String) {
            ctx.startActivity(
                Intent(ctx, TerminalActivity::class.java)
                    .putExtra("PATH", path)
            )
        }
    }
}
