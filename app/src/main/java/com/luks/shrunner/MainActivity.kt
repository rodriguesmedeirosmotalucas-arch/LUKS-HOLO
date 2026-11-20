package com.luks.shrunner

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.*

class MainActivity : Activity() {

    private lateinit var txtFileName: TextView
    private lateinit var txtTerminal: TextView
    private lateinit var scrollTerminal: ScrollView
    private lateinit var chkRoot: CheckBox
    private lateinit var btnRun: Button
    private var selectedScriptFile: File? = null
    private var originalName: String = "script.sh"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Layout Principal (Tema Escuro estilo MT Manager)
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(30, 30, 30, 30)
        }

        // Título
        val title = TextView(this).apply {
            text = "SH Runner (Modo MT Manager)"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }

        // Botão para selecionar o arquivo .sh
        val btnSelect = Button(this).apply {
            text = "📁 Selecionar Arquivo .SH"
            setBackgroundColor(Color.parseColor("#3A3A3A"))
            setTextColor(Color.WHITE)
        }

        // Texto mostrando o arquivo selecionado
        txtFileName = TextView(this).apply {
            text = "Nenhum arquivo selecionado"
            setTextColor(Color.LTGRAY)
            setPadding(0, 15, 0, 15)
        }

        // Opção de Root (necessário para /data/local/tmp em alguns aparelhos)
        chkRoot = CheckBox(this).apply {
            text = "Usar Root (su) -> /data/local/tmp/"
            setTextColor(Color.WHITE)
            isChecked = true
        }

        // Barra de botões (Executar e Limpar)
        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, 10, 0, 20)
        }

        btnRun = Button(this).apply {
            text = "▶ EXECUTAR"
            setBackgroundColor(Color.parseColor("#2E7D32"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 10
            }
        }

        val btnClear = Button(this).apply {
            text = "🧹 LIMPAR"
            setBackgroundColor(Color.parseColor("#C62828"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 10
            }
        }

        btnLayout.addView(btnRun)
        btnLayout.addView(btnClear)

        // Painel do Terminal
        txtTerminal = TextView(this).apply {
            text = "=== Terminal Pronto ===\nSelecione um arquivo .sh para começar.\n\n"
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setPadding(20, 20, 20, 20)
        }

        scrollTerminal = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            addView(txtTerminal)
        }

        // Adicionando tudo na tela
        mainLayout.addView(title)
        mainLayout.addView(btnSelect)
        mainLayout.addView(txtFileName)
        mainLayout.addView(chkRoot)
        mainLayout.addView(btnLayout)
        mainLayout.addView(scrollTerminal)

        setContentView(mainLayout)

        // Ação: Escolher arquivo
        btnSelect.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(Intent.createChooser(intent, "Escolha o script .sh"), 100)
        }

        // Ação: Limpar Terminal
        btnClear.setOnClickListener {
            txtTerminal.text = ""
        }

        // Ação: Executar Script
        btnRun.setOnClickListener {
            val file = selectedScriptFile
            if (file == null || !file.exists()) {
                Toast.makeText(this, "Selecione um arquivo .sh primeiro!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            executeScript(file, chkRoot.isChecked)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK && data?.data != null) {
            val uri = data.data!!
            prepareScriptFile(uri)
        }
    }

    // Prepara o arquivo, remove erros de formatação do Windows (CRLF) e salva temporariamente
    private fun prepareScriptFile(uri: Uri) {
        try {
            originalName = uri.lastPathSegment?.substringAfterLast("/") ?: "script.sh"
            if (!originalName.endsWith(".sh")) originalName = "script.sh"

            val inputStream = contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val tempFile = File(filesDir, originalName)
            val writer = BufferedWriter(FileWriter(tempFile))

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                // Converte quebras de linha para formato Linux puro (igual o MT Manager faz)
                writer.write(line!!.replace("\r", ""))
                writer.newLine()
            }
            writer.flush()
            writer.close()
            reader.close()

            // Aplica permissão total no arquivo interno
            tempFile.setReadable(true, false)
            tempFile.setWritable(true, false)
            tempFile.setExecutable(true, false)

            selectedScriptFile = tempFile
            txtFileName.text = "Arquivo carregado: $originalName"
            appendTerminal("\n[+] Arquivo '$originalName' carregado e formatado com sucesso.\n")

        } catch (e: Exception) {
            appendTerminal("\n[ERRO ao carregar arquivo]: ${e.message}\n")
        }
    }

    private fun executeScript(stagedFile: File, useRoot: Boolean) {
        btnRun.isEnabled = false
        val tmpPath = "/data/local/tmp/$originalName"

        appendTerminal("\n====================================\n")
        appendTerminal("[*] Preparando ambiente de execução...\n")

        Thread {
            try {
                val shell = if (useRoot) "su" else "sh"
                val processBuilder = ProcessBuilder(shell)
                processBuilder.redirectErrorStream(true) // Junta saídas normais e erros no mesmo painel
                val process = processBuilder.start()

                val os = DataOutputStream(process.outputStream)
                val reader = BufferedReader(InputStreamReader(process.inputStream))

                if (useRoot) {
                    // Com Root: Move para /data/local/tmp/, dá chmod 777 e executa de lá
                    appendTerminal("[*] Movendo para $tmpPath...\n")
                    appendTerminal("[*] Aplicando permissões (chmod 777)...\n")
                    appendTerminal("[*] Executando com ROOT (su)...\n\n")

                    os.writeBytes("cp '${stagedFile.absolutePath}' '$tmpPath'\n")
                    os.writeBytes("chmod 777 '$tmpPath'\n")
                    os.writeBytes("cd /data/local/tmp/\n")
                    os.writeBytes("sh '$tmpPath'\n")
                    os.writeBytes("exit\n")
                    os.flush()
                } else {
                    // Sem Root: Tenta copiar para /data/local/tmp, se o Android bloquear, roda do diretório executável interno com chmod 777
                    appendTerminal("[*] Aplicando permissões (chmod 777)...\n")
                    appendTerminal("[*] Executando em modo normal (sh)...\n\n")

                    os.writeBytes("cp '${stagedFile.absolutePath}' '$tmpPath' 2>/dev/null\n")
                    os.writeBytes("if [ -f '$tmpPath' ]; then\n")
                    os.writeBytes("  chmod 777 '$tmpPath'\n")
                    os.writeBytes("  cd /data/local/tmp/\n")
                    os.writeBytes("  sh '$tmpPath'\n")
                    os.writeBytes("else\n")
                    os.writeBytes("  chmod 777 '${stagedFile.absolutePath}'\n")
                    os.writeBytes("  cd '${filesDir.absolutePath}'\n")
                    os.writeBytes("  sh '${stagedFile.absolutePath}'\n")
                    os.writeBytes("fi\n")
                    os.writeBytes("exit\n")
                    os.flush()
                }

                // Lê a saída em tempo real linha por linha (Igual o terminal do MT Manager)
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    appendTerminal("$line\n")
                }

                val exitCode = process.waitFor()
                appendTerminal("\n------------------------------------\n")
                appendTerminal("[✓] Processo finalizado (Código: $exitCode)\n")

            } catch (e: Exception) {
                appendTerminal("\n[ERRO DE EXECUÇÃO]: ${e.message}\n")
                if (useRoot) {
                    appendTerminal("[DICA]: Verifique se o seu celular tem Root ativo ou desmarque a opção 'Usar Root'.\n")
                }
            } finally {
                runOnUiThread {
                    btnRun.isEnabled = true
                }
            }
        }.start()
    }

    private fun appendTerminal(text: String) {
        runOnUiThread {
            txtTerminal.append(text)
            scrollTerminal.post {
                scrollTerminal.fullScroll(View.FOCUS_DOWN)
            }
        }
    }
}
