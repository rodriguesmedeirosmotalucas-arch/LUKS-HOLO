package com.luks.shrunner

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.*
import java.io.*

class MainActivity : Activity() {

    private lateinit var txtFileName: TextView
    private lateinit var txtTerminal: TextView
    private lateinit var scrollTerminal: ScrollView
    private lateinit var chkRoot: CheckBox
    private lateinit var btnRun: Button
    private lateinit var btnStop: Button
    private lateinit var inputCommand: EditText

    private var selectedScriptFile: File? = null
    private var originalName: String = "script.sh"

    private var activeProcess: Process? = null
    private var processOutputStream: OutputStream? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Cores LUKS STORE (Roxo)
        val bgDarkPurple = Color.parseColor("#0F051D")
        val cardPurple = Color.parseColor("#1E0B36")
        val primaryPurple = Color.parseColor("#7B1FA2")
        val neonPurple = Color.parseColor("#9C27B0")
        val lightPurple = Color.parseColor("#E1BEE7")

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgDarkPurple)
            setPadding(25, 25, 25, 25)
        }

        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedBg(cardPurple, neonPurple, 20f, 3)
            setPadding(30, 25, 30, 25)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }
        }

        val title = TextView(this).apply {
            text = "⚡ LUKS STORE ⚡"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "SH Runner Pro • Terminal Interativo"
            textSize = 12f
            setTextColor(lightPurple)
            gravity = Gravity.CENTER
        }

        headerLayout.addView(title)
        headerLayout.addView(subtitle)

        val btnSelect = Button(this).apply {
            text = "📁 SELECIONAR ARQUIVO .SH"
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = createRoundedBg(primaryPurple, neonPurple, 15f, 2)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        txtFileName = TextView(this).apply {
            text = "Nenhum script selecionado"
            setTextColor(lightPurple)
            textSize = 13f
            setPadding(5, 12, 5, 5)
        }

        chkRoot = CheckBox(this).apply {
            text = "Modo Root (su) -> /data/local/tmp/"
            setTextColor(Color.WHITE)
            isChecked = true
        }

        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
            setPadding(0, 10, 0, 15)
        }

        btnRun = Button(this).apply {
            text = "▶ INICIAR"
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = createRoundedBg(Color.parseColor("#6A1B9A"), neonPurple, 12f, 2)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 8
            }
        }

        btnStop = Button(this).apply {
            text = "⏹ PARAR"
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            isEnabled = false
            background = createRoundedBg(Color.parseColor("#4A148C"), Color.parseColor("#880E4F"), 12f, 2)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 4
                rightMargin = 4
            }
        }

        val btnClear = Button(this).apply {
            text = "🧹 LIMPAR"
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = createRoundedBg(Color.parseColor("#311B92"), primaryPurple, 12f, 2)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 8
            }
        }

        btnLayout.addView(btnRun)
        btnLayout.addView(btnStop)
        btnLayout.addView(btnClear)

        txtTerminal = TextView(this).apply {
            text = "=== [ LUKS STORE TERMINAL ] ===\nSelecione um arquivo .sh e toque em INICIAR.\n\n"
            setTextColor(Color.parseColor("#E040FB"))
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setPadding(20, 20, 20, 20)
        }

        scrollTerminal = ScrollView(this).apply {
            background = createRoundedBg(Color.parseColor("#07020D"), neonPurple, 15f, 3)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { bottomMargin = 15 }
            addView(txtTerminal)
        }

        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createRoundedBg(cardPurple, neonPurple, 15f, 2)
            setPadding(15, 10, 15, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        inputCommand = EditText(this).apply {
            hint = "Digite a opção (ex: 1, 2) e envie..."
            setHintTextColor(Color.parseColor("#9575CD"))
            setTextColor(Color.WHITE)
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEND
            background = null
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnSend = Button(this).apply {
            text = "ENVIAR ↵"
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = createRoundedBg(neonPurple, lightPurple, 12f, 2)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        inputLayout.addView(inputCommand)
        inputLayout.addView(btnSend)

        mainLayout.addView(headerLayout)
        mainLayout.addView(btnSelect)
        mainLayout.addView(txtFileName)
        mainLayout.addView(chkRoot)
        mainLayout.addView(btnLayout)
        mainLayout.addView(scrollTerminal)
        mainLayout.addView(inputLayout)

        setContentView(mainLayout)

        btnSelect.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(Intent.createChooser(intent, "Escolha o script .sh"), 100)
        }

        btnClear.setOnClickListener {
            txtTerminal.text = ""
        }

        btnRun.setOnClickListener {
            val file = selectedScriptFile
            if (file == null || !file.exists()) {
                Toast.makeText(this, "Selecione um arquivo .sh primeiro!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            executeScript(file, chkRoot.isChecked)
        }

        btnStop.setOnClickListener {
            stopRunningProcess()
        }

        btnSend.setOnClickListener {
            val cmd = inputCommand.text.toString()
            sendInputToTerminal(cmd)
            inputCommand.setText("")
        }

        inputCommand.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val cmd = inputCommand.text.toString()
                sendInputToTerminal(cmd)
                inputCommand.setText("")
                true
            } else {
                false
            }
        }
    }

    private fun createRoundedBg(fillColor: Int, strokeColor: Int, radius: Float, strokeWidth: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fillColor)
            setStroke(strokeWidth, strokeColor)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK && data?.data != null) {
            prepareScriptFile(data.data!!)
        }
    }

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
                writer.write(line!!.replace("\r", ""))
                writer.newLine()
            }
            writer.flush()
            writer.close()
            reader.close()

            tempFile.setReadable(true, false)
            tempFile.setWritable(true, false)
            tempFile.setExecutable(true, false)

            selectedScriptFile = tempFile
            txtFileName.text = "✅ Carregado: $originalName"
            appendTerminal("\n[+] Script '$originalName' pronto para execução.\n")

        } catch (e: Exception) {
            appendTerminal("\n[ERRO ao carregar]: ${e.message}\n")
        }
    }

    private fun executeScript(stagedFile: File, useRoot: Boolean) {
        stopRunningProcess()
        btnRun.isEnabled = false
        btnStop.isEnabled = true

        val tmpPath = "/data/local/tmp/$originalName"
        val stagedPath = stagedFile.absolutePath

        appendTerminal("\n====================================\n")
        appendTerminal("[*] LUKS STORE: Preparando ambiente...\n")

        Thread {
            try {
                val commandString = if (useRoot) {
                    "cp '$stagedPath' '$tmpPath' && chmod 777 '$tmpPath' && cd /data/local/tmp/ && export TERM=xterm && sh '$tmpPath'"
                } else {
                    "cp '$stagedPath' '$tmpPath' 2>/dev/null; " +
                    "if [ -f '$tmpPath' ]; then chmod 777 '$tmpPath'; cd /data/local/tmp/; export TERM=xterm; sh '$tmpPath'; " +
                    "else chmod 777 '$stagedPath'; cd '${filesDir.absolutePath}'; export TERM=xterm; sh '$stagedPath'; fi"
                }

                val cmdArray = if (useRoot) arrayOf("su", "-c", commandString) else arrayOf("sh", "-c", commandString)

                val processBuilder = ProcessBuilder(*cmdArray)
                processBuilder.redirectErrorStream(true)
                val process = processBuilder.start()

                activeProcess = process
                processOutputStream = process.outputStream

                appendTerminal("[*] Executando em /data/local/tmp/ (chmod 777)...\n\n")

                val reader = InputStreamReader(process.inputStream, Charsets.UTF_8)
                val buffer = CharArray(1024)
                var charsRead: Int

                while (reader.read(buffer).also { charsRead = it } != -1) {
                    val rawText = String(buffer, 0, charsRead)
                    processTerminalChunk(rawText)
                }

                val exitCode = process.waitFor()
                appendTerminal("\n------------------------------------\n")
                appendTerminal("[✓] Finalizado (Código: $exitCode)\n")

            } catch (e: Exception) {
                appendTerminal("\n[!] Execução encerrada: ${e.message}\n")
            } finally {
                activeProcess = null
                processOutputStream = null
                runOnUiThread {
                    btnRun.isEnabled = true
                    btnStop.isEnabled = false
                }
            }
        }.start()
    }

    private fun sendInputToTerminal(input: String) {
        val os = processOutputStream
        if (activeProcess == null || os == null) {
            Toast.makeText(this, "Inicie o script primeiro!", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                appendTerminal("$input\n")
                os.write((input + "\n").toByteArray(Charsets.UTF_8))
                os.flush()
            } catch (e: Exception) {
                appendTerminal("\n[Erro ao enviar opção]: ${e.message}\n")
            }
        }.start()
    }

    private fun stopRunningProcess() {
        try {
            activeProcess?.destroy()
            activeProcess = null
            processOutputStream = null
            appendTerminal("\n[⏹] Script interrompido pelo usuário.\n")
            btnRun.isEnabled = true
            btnStop.isEnabled = false
        } catch (_: Exception) {}
    }

    private fun processTerminalChunk(rawText: String) {
        var text = rawText
        val shouldClear = text.contains("\u001B[2J") || text.contains("\u001Bc")

        val ansiRegex = Regex("\u001B\\[[0-9;?]*[a-zA-Z]")
        text = ansiRegex.replace(text, "")

        runOnUiThread {
            if (shouldClear) {
                txtTerminal.text = ""
            }
            txtTerminal.append(text)
            scrollTerminal.post {
                scrollTerminal.fullScroll(View.FOCUS_DOWN)
            }
        }
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
