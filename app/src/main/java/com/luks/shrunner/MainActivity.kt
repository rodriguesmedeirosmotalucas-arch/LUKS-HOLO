package com.luks.shrunner

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        val inputCmd = EditText(this).apply {
            hint = "Digite o comando ou: sh /sdcard/arquivo.sh"
            setSingleLine(false)
        }

        val btnRun = Button(this).apply {
            text = "Executar SH"
        }

        val txtOutput = TextView(this).apply {
            text = "Saída do terminal aparecerá aqui...\n"
            setTextColor(Color.GREEN)
            setBackgroundColor(Color.BLACK)
            setPadding(20, 20, 20, 20)
        }

        val scroll = ScrollView(this).apply {
            addView(txtOutput)
        }

        layout.addView(inputCmd)
        layout.addView(btnRun)
        layout.addView(scroll)

        setContentView(layout)

        btnRun.setOnClickListener {
            val cmd = inputCmd.text.toString()
            if (cmd.isNotEmpty()) {
                txtOutput.text = "Executando...\n"
                Thread {
                    val result = runShell(cmd)
                    runOnUiThread {
                        txtOutput.text = result
                    }
                }.start()
            }
        }
    }

    private fun runShell(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val output = StringBuilder()
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            while (errorReader.readLine().also { line = it } != null) {
                output.append("ERRO: ").append(line).append("\n")
            }
            process.waitFor()
            if (output.isEmpty()) "Comando executado com sucesso (sem retorno)." else output.toString()
        } catch (e: Exception) {
            "Falha ao executar: ${e.message}"
        }
    }
}
