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

    private val corFundo = 0xFF160A25.toInt()
    private val corCard = 0xFF1E0F33.toInt()
    private val corTexto = 0xFFC084FC.toInt()
    private val corTitulo = 0xFFD9A7FF.toInt()

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
        if (aguardandoAcesso && temAcessoArquivos()) {
            aguardandoAcesso = false
            tvStatus.text = "🔓 Acesso total: ATIVO"
            if (transportUri != null) {
                navegador(Environment.getExternalStorageDirectory(), true)
            }
        }
    }

    private fun px(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun montarTela() {
        window.statusBarColor = 0xFF12061F.toInt()

        val scroll = ScrollView(this)
        val coluna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24), px(40), px(24), px(24))
            setBackgroundColor(corFundo)
        }
        scroll.addView(coluna)
        setContentView(scroll)

        val titulo = TextView(this).apply {
            text = "LUKS STORE"
            setTextColor(corTitulo)
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val subtitulo = TextView(this).apply {
            text = "SH Runner • Root + Shizuku"
            setTextColor(0xFF9C7BC4.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, px(4), 0, px(24))
        }

        tvStatus = TextView(this).apply {
            text = "Nenhum script carregado"
            setTextColor(corTexto)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(px(16), px(16), px(16), px(16))
            background = GradientDrawable().apply {
                setColor(corCard)
                cornerRadius = px(14).toFloat()
            }
        }

        val lpStatus = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = px(28) }

        val lpBotao = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, px(52)
        ).apply { bottomMargin = px(10) }

        val dica = TextView(this).apply {
            text = "Scripts com menu aceitam resposta no terminal.\nUse Transportar p/ copiar arquivos p/ Android/data."
            setTextColor(0xFF6E5A8A.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, px(14), 0, 0)
        }

        coluna.addView(titulo)
        coluna.addView(subtitulo)
        coluna.addView(tvStatus, lpStatus)
        coluna.addView(botao("📂  Carregar script (.sh)", 0xFF7B2FBE.toInt()) { abrirSeletor() }, lpBotao)
        coluna.addView(botao("🗂️  Gerenciador próprio", 0xFF4B1D6E.toInt()) { abrirGerenciadorProprio() }, lpBotao)
        coluna.addView(botao("📦  Transportar arquivo (copiar p/ pasta)", 0xFF8C6A2E.toInt()) { transportar() }, lpBotao)
        coluna.addView(botao("🔓  Ativar acesso total aos arquivos", 0xFF1D5C8C.toInt()) { acaoPermissao() }, lpBotao)
        coluna.addView(botao("⚡  Executar via Shizuku", 0xFF2E7D5B.toInt()) { acaoShizuku() }, lpBotao)
        coluna.addView(botao("🧱  Executar via Root", 0xFF8C3B2E.toInt()) { acaoRoot() }, lpBotao)
        coluna.addView(dica)
    }

    private fun botao(texto: String, cor: Int, acao: () -> Unit) = Button(this).apply {
        this.text = texto
        setTextColor(Color.WHITE)
        textSize = 15f
        setAllCaps(false)
        background = GradientDrawable().apply {
            setColor(cor)
            cornerRadius = px(14).toFloat()
        }
        setOnClickListener { acao() }
    }

    private fun acaoPermissao() {
        if (temAcessoArquivos()) {
            tvStatus.text = "🔓 Acesso total: ATIVO"
            Toast.makeText(this, "Acesso total já está ativo ✅", Toast.LENGTH_SHORT).show()
        } else {
            pedirAcessoArquivos()
        }
    }

    private fun temAcessoArquivos(): Boolean {
        return if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun pedirAcessoArquivos() {
        aguardandoAcesso = true
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            Toast.makeText(this, "Conceda 'Todos os arquivos' e volte ao app", Toast.LENGTH_LONG).show()
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 100)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            aguardandoAcesso = false
            tvStatus.text = "🔓 Acesso total: ATIVO"
            if (transportUri != null) {
                navegador(Environment.getExternalStorageDirectory(), true)
            } else {
                navegador(Environment.getExternalStorageDirectory())
            }
        }
    }

    private fun abrirSeletor() {
        try {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            startActivityForResult(i, 101)
        } catch (e: Exception) {
            Toast.makeText(this, "Nenhum gerenciador encontrado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun abrirGerenciadorProprio() {
        if (temAcessoArquivos()) {
            navegador(Environment.getExternalStorageDirectory())
        } else {
            pedirAcessoArquivos()
        }
    }

    private fun transportar() {
        try {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            startActivityForResult(i, 202)
        } catch (e: Exception) {
            Toast.makeText(this, "Nenhum gerenciador encontrado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun colarArquivo(destino: File) {
        val uri = transportUri
        val nome = transportNome
        if (uri == null || nome == null) {
            Toast.makeText(this, "Nenhum arquivo selecionado", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val alvo = File(destino, nome)
            contentResolver.openInputStream(uri)?.use { entrada ->
                alvo.outputStream().use { saida -> entrada.copyTo(saida) }
            }
            transportUri = null
            transportNome = null
            tvStatus.text = "📦 Copiado: ${alvo.absolutePath}"
            Toast.makeText(this, "✅ Copiado para:\n${alvo.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Falha ao copiar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun criarPasta(pasta: File, modoDestino: Boolean) {
        val input = EditText(this)
        input.hint = "nome da nova pasta"
        AlertDialog.Builder(this)
            .setTitle("Nova pasta")
            .setMessage("Criar dentro de:\n${pasta.absolutePath}")
            .setView(input)
            .setPositiveButton("Criar") { _, _ ->
                val nome = input.text.toString().trim()
                if (nome.isNotEmpty()) {
                    val nova = File(pasta, nome)
                    if (nova.mkdirs() || nova.exists()) {
                        Toast.makeText(this, "Pasta criada ✅", Toast.LENGTH_SHORT).show()
                        navegador(nova, modoDestino)
                    } else {
                        Toast.makeText(this, "Falha ao criar (sem permissão?)", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
