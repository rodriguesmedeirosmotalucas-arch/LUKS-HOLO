package com.luks.shrunner

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.*
import java.io.*

class MainActivity : Activity() {

    private lateinit var txtFileName: TextView
    private lateinit var txtPermStatus: TextView
    private lateinit var txtTerminal: TextView
    private lateinit var scrollTerminal: ScrollView
    private lateinit var btnRun: Button
    private lateinit var btnStop: Button
    private lateinit var btnPerms: Button
    private lateinit var inputCommand: EditText

    private var selectedScriptFile: File? = null
    private var originalName: String = "script.sh"
    private var currentPermissionOctal: String = "777"
    private var currentPermissionSymbolic: String = "rwxrwxrwx"

    // Opções do painel estilo MT Manager
    private var useRootPriv: Boolean = true
    private var useShizukuPriv: Boolean = false
    private var useExtensionEnv: Boolean = false

    // Controle do terminal interativo
    private var activeProcess: Process? = null
    private var processOutputStream: OutputStream? = null

    // Cores LUKS STORE
    private val bgDarkPurple = Color.parseColor("#0F051D")
    private val cardPurple = Color.parseColor("#1E0B36")
    private val primaryPurple = Color.parseColor("#7B1FA2")
    private val neonPurple = Color.parseColor("#9C27B0")
    private val lightPurple = Color.parseColor("#E1BEE7")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        requestStorageAccessIfNeeded()

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgDarkPurple)
            setPadding(25, 25, 25, 25)
        }

        // Cabeçalho LUKS STORE
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedBg(cardPurple, neonPurple, 20f, 3)
            setPadding(25, 20, 25, 20)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 15 }
        }

        val title = TextView(this).apply {
            text = "⚡ LUKS STORE ⚡"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "MT Runner • /data/local/tmp/ • Root & Shizuku"
            textSize = 11f
            setTextColor(lightPurple)
            gravity = Gravity.CENTER
        }

        headerLayout.addView(title)
        headerLayout.addView(subtitle)

        // Botão de Upload (Escolhe Gerenciador Próprio ou do Sistema)
        val btnSelect = Button(this).apply {
            text = "📁 FAZER UPLOAD / ESCOLHER ARQUIVO .SH"
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = createRoundedBg(primaryPurple, neonPurple, 15f, 2)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Informações do arquivo e permissão atual
        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(5, 10, 5, 10)
        }

        txtFileName = TextView(this).apply {
            text = "Arquivo: Nenhum selecionado"
            setTextColor(Color.WHITE)
            textSize = 13f
        }

        txtPermStatus = TextView(this).apply {
            text = "Destino: /data/local/tmp/ | Permissão: -rwxrwxrwx (777)"
            setTextColor(lightPurple)
            textSize = 11f
        }

        infoLayout.addView(txtFileName)
        infoLayout.addView(txtPermStatus)

        // Barra de Botões (Executar, Permissões 777, Parar, Limpar)
        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 4f
            setPadding(0, 5, 0, 12)
        }

        btnRun = Button(this).apply {
            text = "▶ EXEC"
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = createRoundedBg(Color.parseColor("#6A1B9A"), neonPurple, 12f, 2)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 5
            }
        }

        btnPerms = Button(this).apply {
            text = "🔒 777"
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = createRoundedBg(Color.parseColor("#4A148C"), lightPurple, 12f, 2)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 5
                rightMargin = 5
            }
        }

        btnStop = Button(this).apply {
            text = "⏹ PARAR"
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            isEnabled = false
            background = createRoundedBg(Color.parseColor("#880E4F"), Color.parseColor("#F06292"), 12f, 2)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 5
                rightMargin = 5
            }
        }

        val btnClear = Button(this).apply {
            text = "🧹 LIMPAR"
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = createRoundedBg(Color.parseColor("#311B92"), primaryPurple, 12f, 2)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 5
            }
        }

        btnLayout.addView(btnRun)
        btnLayout.addView(btnPerms)
        btnLayout.addView(btnStop)
        btnLayout.addView(btnClear)

        // Painel do Terminal
        txtTerminal = TextView(this).apply {
            text = "=== [ LUKS STORE TERMINAL ] ===\n" +
                   "• Todos os scripts são transportados para /data/local/tmp/\n" +
                   "• Suporte a Root (su) e Shizuku (rish/adb)\n\n"
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
            ).apply { bottomMargin = 12 }
            addView(txtTerminal)
        }

        // Barra Inferior para Digitar no Terminal (Opções 1, 2, etc.)
        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createRoundedBg(cardPurple, neonPurple, 15f, 2)
            setPadding(15, 8, 15, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        inputCommand = EditText(this).apply {
            hint = "Digite no terminal (ex: 1, 2) e envie..."
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
        mainLayout.addView(infoLayout)
        mainLayout.addView(btnLayout)
        mainLayout.addView(scrollTerminal)
        mainLayout.addView(inputLayout)

        setContentView(mainLayout)

        // Eventos dos botões
        btnSelect.setOnClickListener {
            showChooseFileManagerDialog()
        }

        btnPerms.setOnClickListener {
            showPermissionsDialog {
                transportToTmpAndChmod()
            }
        }

        btnRun.setOnClickListener {
            val file = selectedScriptFile
            if (file == null || !file.exists()) {
                Toast.makeText(this, "Faça upload de um arquivo .sh primeiro!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showMtExecuteDialog()
        }

        btnStop.setOnClickListener {
            stopRunningProcess()
        }

        btnClear.setOnClickListener {
            txtTerminal.text = ""
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

    // Solicita permissão total de arquivos no Android 11+ para nenhum .sh sumir
    private fun requestStorageAccessIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    // =========================================================================
    // 1. ESCOLHA DO GERENCIADOR DE ARQUIVOS (PRÓPRIO OU DO SISTEMA)
    // =========================================================================
    private fun showChooseFileManagerDialog() {
        val options = arrayOf(
            "🟣 Gerenciador LUKS STORE (Interno - Não esconde .sh)",
            "⚡ Abrir direto da pasta /data/local/tmp/",
            "📂 Gerenciador de Arquivos do Sistema (Android)"
        )

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Escolher Gerenciador de Upload")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val startDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val fallbackDir = Environment.getExternalStorageDirectory()
                        showBuiltInFileManager(if (startDir.exists()) startDir else fallbackDir)
                    }
                    1 -> showBuiltInFileManager(File("/data/local/tmp"))
                    2 -> {
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "*/*"
                            addCategory(Intent.CATEGORY_OPENABLE)
                        }
                        startActivityForResult(Intent.createChooser(intent, "Selecione o script .sh"), 100)
                    }
                }
            }
            .show()
    }

    // =========================================================================
    // 2. GERENCIADOR DE ARQUIVOS PRÓPRIO (ESTILO MT MANAGER)
    // =========================================================================
    private fun showBuiltInFileManager(directory: File) {
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#18092B"))
            setPadding(25, 25, 25, 25)
        }

        val txtPath = TextView(this).apply {
            text = "📁 ${directory.absolutePath}"
            setTextColor(lightPurple)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 15)
        }

        // Atalhos rápidos
        val shortcutLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
            setPadding(0, 0, 0, 15)
        }

        var alert: AlertDialog? = null

        val btnSdcard = Button(this).apply {
            text = "/sdcard"
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                alert?.dismiss()
                showBuiltInFileManager(Environment.getExternalStorageDirectory())
            }
        }

        val btnDownload = Button(this).apply {
            text = "Download"
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                alert?.dismiss()
                showBuiltInFileManager(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            }
        }

        val btnTmp = Button(this).apply {
            text = "/data/local/tmp"
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                alert?.dismiss()
                showBuiltInFileManager(File("/data/local/tmp"))
            }
        }

        shortcutLayout.addView(btnSdcard)
        shortcutLayout.addView(btnDownload)
        shortcutLayout.addView(btnTmp)

        val listView = ListView(this)
        val entries = mutableListOf<File>()
        val displayNames = mutableListOf<String>()

        // Opção de voltar pasta
        directory.parentFile?.let { parent ->
            entries.add(parent)
            displayNames.add("⬆️ .. (Voltar pasta)")
        }

        // Lista arquivos normalmente ou via Root/Shell se for pasta protegida (/data/local/tmp)
        val rawFiles = directory.listFiles()?.toList() ?: listFilesViaRootOrShell(directory)
        val sortedFiles = rawFiles.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

        for (f in sortedFiles) {
            entries.add(f)
            if (f.isDirectory) {
                displayNames.add("📁 ${f.name}/")
            } else if (f.name.endsWith(".sh", ignoreCase = true)) {
                displayNames.add(">_ ${f.name}  [SH]")
            } else {
                displayNames.add("📄 ${f.name}")
            }
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayNames)
        listView.adapter = adapter

        dialogLayout.addView(txtPath)
        dialogLayout.addView(shortcutLayout)
        dialogLayout.addView(listView)

        alert = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Gerenciador LUKS STORE")
            .setView(dialogLayout)
            .setNegativeButton("FECHAR", null)
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            val clickedFile = entries[position]
            val label = displayNames[position]
            if (label.startsWith("⬆️") || clickedFile.isDirectory) {
                alert.dismiss()
                showBuiltInFileManager(clickedFile)
            } else {
                alert.dismiss()
                loadFileFromBuiltInManager(clickedFile)
            }
        }

        alert.show()
    }

    // Se o Android tentar esconder arquivos de /data/local/tmp, lê usando Root ou Shizuku/Shell
    private fun listFilesViaRootOrShell(dir: File): List<File> {
        val result = mutableListOf<File>()
        val shells = arrayOf(arrayOf("su", "-c", "ls -1 '${dir.absolutePath}'"), arrayOf("sh", "-c", "ls -1 '${dir.absolutePath}'"))
        for (cmd in shells) {
            try {
                val p = Runtime.getRuntime().exec(cmd)
                val reader = BufferedReader(InputStreamReader(p.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.isNotBlank()) {
                        result.add(File(dir, line!!))
                    }
                }
                p.waitFor()
                if (result.isNotEmpty()) break
            } catch (_: Exception) {}
        }
        return result
    }

    private fun loadFileFromBuiltInManager(file: File) {
        try {
            originalName = file.name
            val tempFile = File(filesDir, originalName)

            if (file.canRead()) {
                cleanAndCopyScript(FileInputStream(file), tempFile)
            } else {
                // Copia via Root/Shell caso esteja em /data/local/tmp protegido
                Runtime.getRuntime().exec(arrayOf("su", "-c", "cp '${file.absolutePath}' '${tempFile.absolutePath}' && chmod 777 '${tempFile.absolutePath}'")).waitFor()
            }

            onScriptLoadedReady(tempFile)
        } catch (e: Exception) {
            appendTerminal("\n[ERRO ao carregar arquivo]: ${e.message}\n")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK && data?.data != null) {
            val uri = data.data!!
            try {
                originalName = uri.lastPathSegment?.substringAfterLast("/")?.substringAfterLast(":") ?: "script.sh"
                if (!originalName.contains(".")) originalName += ".sh"
                val tempFile = File(filesDir, originalName)
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    cleanAndCopyScript(inputStream, tempFile)
                    onScriptLoadedReady(tempFile)
                }
            } catch (e: Exception) {
                appendTerminal("\n[ERRO no Upload]: ${e.message}\n")
            }
        }
    }

    private fun cleanAndCopyScript(inputStream: InputStream, destFile: File) {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val writer = BufferedWriter(FileWriter(destFile))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            writer.write(line!!.replace("\r", ""))
            writer.newLine()
        }
        writer.flush()
        writer.close()
       
