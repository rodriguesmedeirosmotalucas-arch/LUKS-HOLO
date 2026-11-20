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
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.Comparator

class MainActivity : Activity() {

    private lateinit var txtFileName: TextView
    private lateinit var txtPermStatus: TextView
    private lateinit var txtTerminal: TextView
    private lateinit var scrollTerminal: ScrollView
    private lateinit var btnRun: Button
    private lateinit var btnStop: Button
    private lateinit var inputCommand: EditText

    private var selectedScriptFile: File? = null
    private var originalName: String = "script.sh"
    private var permOctal: String = "777"
    private var permSym: String = "rwxrwxrwx"

    private var useRoot: Boolean = true
    private var useShizuku: Boolean = false
    private var useExtEnv: Boolean = false

    private var activeProcess: Process? = null
    private var processOut: OutputStream? = null

    private val cBg = Color.parseColor("#0F051D")
    private val cCard = Color.parseColor("#1E0B36")
    private val cPrimary = Color.parseColor("#7B1FA2")
    private val cNeon = Color.parseColor("#9C27B0")
    private val cLight = Color.parseColor("#E1BEE7")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

            val main = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(cBg)
                setPadding(25, 25, 25, 25)
            }

            val header = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = roundBg(cCard, cNeon, 20f, 3)
                setPadding(25, 20, 25, 20)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 15 }
            }
            header.addView(label("⚡ LUKS STORE ⚡", 22f, Color.WHITE, true, Gravity.CENTER))
            header.addView(label("MT Runner • /data/local/tmp/ • Root & Shizuku", 11f, cLight, false, Gravity.CENTER))

            val btnSelect = actionBtn("📁 FAZER UPLOAD / ESCOLHER ARQUIVO .SH", cPrimary, cNeon) {
                requestStorageAccess()
                showChooseManagerDialog()
            }

            txtFileName = label("Arquivo: Nenhum selecionado", 13f, Color.WHITE, false).apply { setPadding(5, 10, 5, 2) }
            txtPermStatus = label("Destino: /data/local/tmp/ | Perm: -rwxrwxrwx (777)", 11f, cLight, false).apply { setPadding(5, 0, 5, 10) }

            val bar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 4f
                setPadding(0, 5, 0, 12)
            }

            btnRun = barBtn("▶ EXEC", "#6A1B9A") {
                if (selectedScriptFile?.exists() == true) showMtExecuteDialog()
                else Toast.makeText(this, "Selecione um arquivo .sh primeiro!", Toast.LENGTH_SHORT).show()
            }
            val btnPerms = barBtn("🔒 777", "#4A148C") { showPermissionsDialog { transportToTmp() } }
            btnStop = barBtn("⏹ PARAR", "#880E4F") { stopProcess() }.apply { isEnabled = false }
            val btnClear = barBtn("🧹 LIMPAR", "#311B92") { txtTerminal.text = "" }

            bar.addView(btnRun); bar.addView(btnPerms); bar.addView(btnStop); bar.addView(btnClear)

            txtTerminal = TextView(this).apply {
                text = "=== [ LUKS STORE TERMINAL ] ===\n• Transporte obrigatório: /data/local/tmp/\n• Suporte: Root (su) & Shizuku\n\n"
                setTextColor(Color.parseColor("#E040FB"))
                typeface = Typeface.MONOSPACE
                textSize = 13f
                setPadding(20, 20, 20, 20)
            }

            scrollTerminal = ScrollView(this).apply {
                background = roundBg(Color.parseColor("#07020D"), cNeon, 15f, 3)
                layoutParams = LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = 12 }
                addView(txtTerminal)
            }

            val inputRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = roundBg(cCard, cNeon, 15f, 2)
                setPadding(15, 8, 15, 8)
                layoutParams = LinearLayout.LayoutParams(-1, -2)
            }

            inputCommand = EditText(this).apply {
                hint = "Digite a opção (ex: 1, 2) e envie..."
                setHintTextColor(Color.parseColor("#9575CD"))
                setTextColor(Color.WHITE)
                setSingleLine(true)
                imeOptions = EditorInfo.IME_ACTION_SEND
                background = null
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }

            val btnSend = actionBtn("ENVIAR ↵", cNeon, cLight) {
                sendInput(inputCommand.text.toString())
                inputCommand.setText("")
            }.apply { layoutParams = LinearLayout.LayoutParams(-2, -2) }

            inputCommand.setOnEditorActionListener { _, id, ev ->
                if (id == EditorInfo.IME_ACTION_SEND || (ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN)) {
                    sendInput(inputCommand.text.toString())
                    inputCommand.setText("")
                    true
                } else false
            }

            inputRow.addView(inputCommand); inputRow.addView(btnSend)
            main.addView(header); main.addView(btnSelect); main.addView(txtFileName)
            main.addView(txtPermStatus); main.addView(bar); main.addView(scrollTerminal); main.addView(inputRow)
            setContentView(main)

        } catch (e: Throwable) {
            val errTxt = TextView(this).apply {
                text = "Erro ao iniciar:\n${e.stackTraceToString()}"
                setTextColor(Color.RED)
                setPadding(30, 30, 30, 30)
            }
            setContentView(ScrollView(this).apply { addView(errTxt) })
        }
    }

    private fun label(txt: String, size: Float, col: Int, bold: Boolean, grav: Int = Gravity.START): TextView {
        return TextView(this).apply {
            text = txt; textSize = size; setTextColor(col); gravity = grav
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    }

    private fun actionBtn(txt: String, bgCol: Int, strokeCol: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = txt; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            background = roundBg(bgCol, strokeCol, 14f, 2)
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            setOnClickListener { onClick() }
        }
    }

    private fun barBtn(txt: String, hex: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = txt; textSize = 11f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            background = roundBg(Color.parseColor(hex), cNeon, 12f, 2)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(4, 0, 4, 0) }
            setOnClickListener { onClick() }
        }
    }

    private fun roundBg(fill: Int, stroke: Int, rad: Float, w: Int): GradientDrawable {
        return GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = rad; setColor(fill); setStroke(w, stroke) }
    }

    private fun requestStorageAccess() {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                requestPermissions(arrayOf("android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"), 101)
            }
            if (Build.VERSION.SDK_INT >= 30) {
                val mgr = try { Environment::class.java.getMethod("isExternalStorageManager").invoke(null) as Boolean } catch (_: Exception) { true }
                if (!mgr) {
                    val intent = Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION")
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
            }
        } catch (_: Exception) {}
    }

    private fun showChooseManagerDialog() {
        val opts = arrayOf(
            "🟣 Gerenciador LUKS STORE (Interno - Não esconde .sh)",
            "⚡ Abrir direto de /data/local/tmp/",
            "📂 Gerenciador de Arquivos do Sistema"
        )
        AlertDialog.Builder(this)
            .setTitle("Escolher Gerenciador de Upload")
            .setItems(opts) { _, which ->
                when (which) {
                    0 -> {
                        val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        showInternalFileManager(if (dl.exists()) dl else Environment.getExternalStorageDirectory())
                    }
                    1 -> showInternalFileManager(File("/data/local/tmp"))
                    2 -> {
                        val i = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }
                        startActivityForResult(Intent.createChooser(i, "Escolher .sh"), 100)
                    }
                }
            }.show()
    }

    private fun showInternalFileManager(dir: File) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#18092B")); setPadding(25, 25, 25, 25) }
        box.addView(label("📁 ${dir.absolutePath}", 13f, cLight, true).apply { setPadding(0, 0, 0, 15) })

        var alert: AlertDialog? = null
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 3f; setPadding(0, 0, 0, 12) }
        nav.addView(barBtn("/sdcard", "#4A148C") { alert?.dismiss(); showInternalFileManager(Environment.getExternalStorageDirectory()) })
        nav.addView(barBtn("Download", "#4A148C") { alert?.dismiss(); showInternalFileManager(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)) })
        nav.addView(barBtn("/data/local/tmp", "#4A148C") { alert?.dismiss(); showInternalFileManager(File("/data/local/tmp")) })
        box.addView(nav)

        val entries = mutableListOf<File>()
        val names = mutableListOf<String>()
        dir.parentFile?.let { entries.add(it); names.add("⬆️ .. (Voltar pasta)") }

        val raw = dir.listFiles()?.toList() ?: listViaShell(dir)
        val sorted = raw.sortedWith(Comparator<File> { a, b ->
            if (a.isDirectory != b.isDirectory) if (a.isDirectory) -1 else 1 else a.name.compareTo(b.name, true)
        })
        for (f in sorted) {
            entries.add(f)
            names.add(if (f.isDirectory) "📁 ${f.name}/" else if (f.name.endsWith(".sh", true)) ">_ ${f.name} [SH]" else "📄 ${f.name}")
        }

        val lv = ListView(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, names) }
        box.addView(lv)

        alert = AlertDialog.Builder(this)
            .setTitle("Gerenciador LUKS STORE").setView(box).setNegativeButton("FECHAR", null).create()

        lv.setOnItemClickListener { _, _, pos, _ ->
            val target = entries[pos]
            alert?.dismiss()
            if (names[pos].startsWith("⬆️") || target.isDirectory) showInternalFileManager(target)
            else loadFromFile(target)
        }
        alert?.show()
    }

    private fun listViaShell(dir: File): List<File> {
        val list = mutableListOf<File>()
        for (s in arrayOf("su", "sh")) {
            try {
                val p = Runtime.getRuntime().exec(arrayOf(s, "-c", "ls -1 '${dir.absolutePath}'"))
                val r = BufferedReader(InputStreamReader(p.inputStream))
                var l: String?
                while (r.readLine().also { l = it } != null) {
                    if (l!!.trim().isNotEmpty()) list.add(File(dir, l!!.trim()))
                }
                p.waitFor()
                if (list.isNotEmpty()) break
            } catch (_: Exception) {}
        }
        return list
    }

    private fun loadFromFile(f: File) {
        try {
            originalName = f.name
            val dest = File(filesDir, originalName)
            if (f.canRead()) copyClean(FileInputStream(f), dest)
            else Runtime.getRuntime().exec(arrayOf("su", "-c", "cp '${f.absolutePath}' '${dest.absolutePath}' && chmod 777 '${dest.absolutePath}'")).waitFor()
            onFileReady(dest)
        } catch (e: Exception) { appendLog("\n[ERRO]: ${e.message}\n") }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 100 && res == RESULT_OK && data?.data != null) {
            try {
                val uri = data.data!!
                originalName = uri.lastPathSegment?.substringAfterLast("/")?.substringAfterLast(":") ?: "script.sh"
                if (!originalName.contains(".")) originalName += ".sh"
                val dest = File(filesDir, originalName)
                contentResolver.openInputStream(uri)?.let { copyClean(it, dest); onFileReady(dest) }
            } catch (e: Exception) { appendLog("\n[ERRO Upload]: ${e.message}\n") }
        }
    }

    private fun copyClean(ins: InputStream, dest: File) {
        val r = BufferedReader(InputStreamReader(ins))
        val w = BufferedWriter(FileWriter(dest))
        var line: String?
        while (r.readLine().also { line = it } != null) { w.write(line!!.replace("\r", "")); w.newLine() }
        w.flush(); w.close(); r.close()
    }

    private fun onFileReady(f: File) {
        f.setReadable(true, false); f.setWritable(true, false); f.setExecutable(true, false)
        selectedScriptFile = f
        txtFileName.text = "Arquivo: $originalName"
        transportToTmp()
        showPermissionsDialog { transportToTmp(); showMtExecuteDialog() }
    }

    private fun showPermissionsDialog(onOk: () -> Unit) {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#2B2B2B")); setPadding(35, 25, 35, 15) }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 4f }
        for (h in arrayOf("", "Read", "Write", "Exec")) head.addView(label(h, 13f, Color.WHITE, true, Gravity.CENTER).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        root.addView(head)

        val rows = arrayOf("Owner", "Group", "Other")
        val cbs = Array(3) { Array(3) { CheckBox(this).apply { isChecked = true } } }
        for (r in 0..2) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 4f; gravity = Gravity.CENTER_VERTICAL }
            row.addView(label(rows[r], 13f, Color.WHITE, false).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            for (c in 0..2) {
                val wrap = LinearLayout(this).apply { gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
                wrap.addView(cbs[r][c]); row.addView(wrap)
            }
            root.addView(row)
        }

        root.addView(label("---- Special Permissions ----", 12f, Color.LTGRAY, false, Gravity.CENTER).apply { setPadding(0, 20, 0, 10) })
        val specRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val cbUid = CheckBox(this).apply { text = "Set UID"; setTextColor(Color.WHITE) }
        val cbGid = CheckBox(this).apply { text = "Set GID"; setTextColor(Color.WHITE) }
        val cbStk = CheckBox(this).apply { text = "Sticky"; setTextColor(Color.WHITE) }
        specRow.addView(cbUid); specRow.addView(cbGid); specRow.addView(cbStk); root.addView(specRow)

        val preview = label("rwxrwxrwx(777)", 17f, Color.WHITE, true, Gravity.CENTER).apply { setPadding(0, 20, 0, 10) }
        root.addView(preview)

        val calc: () -> Unit = {
            var oct = ""; var sym = ""; val ch = charArrayOf('r', 'w', 'x')
            var sp = 0
            if (cbUid.isChecked) sp += 4; if (cbGid.isChecked) sp += 2; if (cbStk.isChecked) sp += 1
            for (r in 0..2) {
                var s = 0
                for (c in 0..2) { if (cbs[r][c].isChecked) { s += (1 shl (2 - c)); sym += ch[c] } else sym += "-" }
                oct += s.toString()
            }
            permOctal = if (sp > 0) "$sp$oct" else oct
            permSym = sym
            preview.text = "$sym($permOctal)"
            txtPermStatus.text = "Destino: /data/local/tmp/$originalName | Perm: -$sym ($permOctal)"
        }
        for (r in 0..2) for (c in 0..2) cbs[r][c].setOnCheckedChangeListener { _, _ -> calc() }
        cbUid.setOnCheckedChangeListener { _, _ -> calc() }; cbGid.setOnCheckedChangeListener { _, _ -> calc() }; cbStk.setOnCheckedChangeListener { _, _ -> calc() }
        calc()

        AlertDialog.Builder(this)
            .setTitle("Permissions").setView(root).setNegativeButton("CANCEL", null).setPositiveButton("OK") { _, _ -> onOk() }.show()
    }

    private fun showMtExecuteDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#303030")); setPadding(35, 25, 35, 25) }
        val warn = label("You have checked to execute with root privileges, please make sure the script does not contain dangerous code!", 13f, Color.parseColor("#FF5252"), false).apply { setPadding(0, 0, 0, 20) }

        var dlg: AlertDialog? = null
        val acts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, 15) }
        acts.addView(label("PERMISSIONS", 13f, Color.parseColor("#40C4FF"), true).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f); setOnClickListener { dlg?.dismiss(); showPermissionsDialog { showMtExecuteDialog() } } })
        acts.addView(label("EDIT", 13f, Color.parseColor("#40C4FF"), true).apply { setPadding(20, 10, 20, 10); setOnClickListener { dlg?.dismiss(); showEditorDialog() } })
        acts.addView(label("EXECUTE", 13f, Color.parseColor("#40C4FF"), true).apply { setPadding(20, 10, 0, 10); setOnClickListener { dlg?.dismiss(); runInTerminal() } })

        val rg = RadioGroup(this)
        val rbSys = RadioButton(this).apply { text = "Execute in system environment"; setTextColor(Color.WHITE); isChecked = !useExtEnv }
        val rbExt = RadioButton(this).apply { text = "Execute in extension pack environment"; setTextColor(Color.WHITE); isChecked = useExtEnv }
        rg.addView(rbSys); rg.addView(rbExt)

        val cbR = CheckBox(this).apply { text = "Execute with root privileges"; setTextColor(Color.WHITE); isChecked = useRoot }
        val cbS = CheckBox(this).apply { text = "Execute with Shizuku (/data/local/tmp)"; setTextColor(cLight); isChecked = useShizuku }

        cbR.setOnCheckedChangeListener { _, b -> useRoot = b; if (b) 
