package com.luks.shrunner

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.File

class MainActivity : Activity() {

    private lateinit var tvStatus: TextView
    private var scriptConteudo: String? = null
    private var scriptNome: String? = null
    private var transportUri: Uri? = null
    private var transportNome: String? = null
    private var aguardandoAcesso = false

    @Volatile
    private var procAtual: Process? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        montarTela()
    }

    override fun onResume() {
        super.onResume()
        if (aguardandoAcesso && temAcesso()) {
            aguardandoAcesso = false
            tvStatus.text = "🔓 Acesso total: ATIVO"
        }
    }

    private fun px(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun card(cor: Int, raio: Int) = GradientDrawable().apply {
        setColor(cor)
        cornerRadius = px(raio).toFloat()
    }

    private fun montarTela() {
        window.statusBarColor = 0xFF12061F.toInt()
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24), px(40), px(24), px(24))
            setBackgroundColor(0xFF160A25.toInt())
        }
        scroll.addView(col)
        setContentView(scroll)

        col.addView(TextView(this).apply {
            text = "LUKS STORE"
            setTextColor(0xFFD9A7FF.toInt())
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        col.addView(TextView(this).apply {
            text = "SH Runner • Root + Shizuku"
            setTextColor(0xFF9C7BC4.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, px(4), 0, px(24))
        })
        tvStatus = TextView(this).apply {
            text = "Nenhum script carregado"
            setTextColor(0xFFC084FC.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(px(16), px(16), px(16), px(16))
            background = card(0xFF1E0F33.toInt(), 14)
        }
        val ls = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        ls.bottomMargin = px(28)
        col.addView(tvStatus, ls)

        val lb = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(52))
        lb.bottomMargin = px(10)

        col.addView(botao("📂  Carregar script (escolher gerenciador)", 0xFF7B2FBE.toInt()) { abrirArquivo(101) }, lb)
        col.addView(botao("🗂️  Gerenciador próprio", 0xFF4B1D6E.toInt()) { abrirProprio() }, lb)
        col.addView(botao("📦  Transportar arquivo (ZArchiver etc.)", 0xFF8C6A2E.toInt()) { abrirArquivo(202) }, lb)
        col.addView(botao("🎮  Navegar /data/local/tmp (Root)", 0xFF5C2E8C.toInt()) { navDataTmp(false) }, lb)
        col.addView(botao("🔓  Ativar acesso total", 0xFF1D5C8C.toInt()) { acaoPerm() }, lb)
        col.addView(botao("⚡  Executar via Shizuku", 0xFF2E7D5B.toInt()) { acaoShizuku() }, lb)
        col.addView(botao("🧱  Executar via Root", 0xFF8C3B2E.toInt()) { acaoRoot() }, lb)
        col.addView(TextView(this).apply {
            text = "Terminal aceita respostas de menu.\nTransportar + Root copiam até em /data."
            setTextColor(0xFF6E5A8A.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, px(14), 0, 0)
        })
    }

    private fun botao(texto: String, cor: Int, acao: () -> Unit) = Button(this).apply {
        text = texto
        setTextColor(Color.WHITE)
        textSize = 15f
        setAllCaps(false)
        background = card(cor, 14)
        setOnClickListener { acao() }
    }

    private fun acaoPerm() {
        if (temAcesso()) {
            tvStatus.text = "🔓 Acesso total: ATIVO"
            Toast.makeText(this, "Acesso total já ativo ✅", Toast.LENGTH_SHORT).show()
        } else pedir()
    }

    private fun temAcesso(): Boolean {
        return if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun pedir() {
        aguardandoAcesso = true
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            Toast.makeText(this, "Conceda 'Todos os arquivos' e volte", Toast.LENGTH_LONG).show()
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            aguardandoAcesso = false
            tvStatus.text = "🔓 Acesso total: ATIVO"
        }
    }

    private fun baseDir(): File {
        val f = File("/storage/emulated/0")
        if (f.exists()) return f
        val e = Environment.getExternalStorageDirectory()
        return e ?: File("/sdcard")
    }

    private fun abrirArquivo(code: Int) {
        try {
            val i = Intent(Intent.ACTION_GET_CONTENT)
            i.type = "*/*"
            i.addCategory(Intent.CATEGORY_OPENABLE)
            startActivityForResult(Intent.createChooser(i, "Escolha o gerenciador"), code)
        } catch (e: Exception) {
            Toast.makeText(this, "Nenhum gerenciador encontrado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun abrirProprio() {
        if (temAcesso()) navFile(baseDir(), false)
        else pedir()
    }

    private fun temRoot(): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        p.waitFor() == 0
    } catch (e: Exception) { false }

    private fun navDataTmp(destino: Boolean) {
        Thread {
            val r = temRoot()
            runOnUiThread {
                if (r) navShell("/data/local/tmp", destino)
                else Toast.makeText(this, "Root não disponível (conceda no su)", Toast.LENGTH_LONG).show()
            }
        }.start()
    }
