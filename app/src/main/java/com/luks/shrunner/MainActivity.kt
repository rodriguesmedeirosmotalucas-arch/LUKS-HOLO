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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.File

class MainActivity : Activity() {

    // Cores LUKS STORE (roxo neon)
    private val corFundo = 0xFF160A25.toInt()
    private val corCard = 0xFF1E0F33.toInt()
    private val corTexto = 0xFFC084FC.toInt()
    private val corTitulo = 0xFFD9A7FF.toInt()

    private lateinit var tvStatus: TextView
    private var scriptConteudo: String? = null
    private var scriptNome: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        montarTela()
    }

    // ==================== INTERFACE ====================

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
            LinearLayout.LayoutParams.MATCH_PARENT, px(56)
        ).apply { bottomMargin = px(12) }

        val dica = TextView(this).apply {
            text = "Carregue um script antes de executar.\nSe o Shizuku não estiver ativo, abra o app Shizuku primeiro."
            setTextColor(0xFF6E5A8A.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, px(16), 0, 0)
        }

        coluna.addView(titulo)
        coluna.addView(subtitulo)
        coluna.addView(tvStatus, lpStatus)
        coluna.addView(botao("📂  Carregar script (.sh)", 0xFF7B2FBE.toInt()) { abrirSeletor() }, lpBotao)
        coluna.addView(botao("🗂️  Gerenciador próprio", 0xFF4B1D6E.toInt()) { abrirGerenciadorProprio() }, lpBotao)
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

    // ==================== CARREGAR SCRIPT ====================

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

    private fun temAcessoArquivos(): Boolean {
        return if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun pedirAcessoArquivos() {
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
            Toast.makeText(this, "Conceda 'Todos os arquivos' e volte", Toast.LENGTH_LONG).show()
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
            navegador(Environment.getExternalStorageDirectory())
        }
    }

    private fun navegador(pasta: File) {
        val itens = pasta.listFiles()
        if (itens == null) {
            Toast.makeText(this, "Sem acesso: ${pasta.absolutePath}", Toast.LENGTH_SHORT).show()
            return
        }
        val ordenados = itens.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }
        )
        val temPai = pasta.parentFile != null
        val nomes = ArrayList<String>()
        if (temPai) nomes.add("⬆️  ..")
        ordenados.forEach { nomes.add(if (it.isDirectory) "📁 ${it.name}" else "📜 ${it.name}") }

        AlertDialog.Builder(this)
            .setTitle(pasta.absolutePath)
            .setItems(nomes.toTypedArray()) { _, pos ->
                when {
                    temPai && pos == 0 -> navegador(pasta.parentFile!!)
                    else -> {
                        val alvo = ordenados[pos - if (temPai) 1 else 0]
                        if (alvo.isDirectory) navegador(alvo)
                        else if (alvo.name.endsWith(".sh")) {
                            try {
                                selecionar(alvo.readText(), alvo.name)
                            } catch (e: Exception) {
                                Toast.makeText(this, "Falha ao ler: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(this, "Escolha um arquivo .sh", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val nome = queryNome(uri) ?: "script.sh"
                val conteudo = contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                if (conteudo != null) selecionar(conteudo, nome)
                else Toast.makeText(this, "Não foi possível ler o arquivo", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Falha: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun queryNome(uri: Uri): String? = try {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
        }
    } catch (e: Exception) { null }

    private fun selecionar(conteudo: String, nome: String) {
        scriptConteudo = conteudo
        scriptNome = nome
        tvStatus.text = "📜 $nome pronto (${conteudo.length} caracteres)"
        Toast.makeText(this, "Script carregado!", Toast.LENGTH_SHORT).show()
    }

    // ==================== EXECUTAR ====================

    private fun acaoRoot() {
        if (scriptConteudo == null) {
            Toast.makeText(this, "Carregue um script primeiro", Toast.LENGTH_SHORT).show()
            return
        }
        executar("root")
    }

    private fun acaoShizuku() {
        if (scriptConteudo == null) {
            Toast.makeText(this, "Carregue um script primeiro", Toast.LENGTH_SHORT).show()
            return
        }
        val pronto = try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { false }

        if (pronto) {
            executar("shizuku")
        } else {
            try {
                Shizuku.requestPermission(200)
                Toast.makeText(this, "Conceda a permissão no Shizuku e toque de novo", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Shizuku não está rodando — abra o app Shizuku", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Cria o Process via Shizuku usando reflexão (imune a diferenças de pacote)
    private fun shizukuProcess(cmd: Array<String>): Process {
        val binder = Shizuku.getBinder()
            ?: throw IllegalStateException("Shizuku não conectado")

        // Localiza o Stub do AIDL (tenta pacote novo, depois antigo)
        val stub = try {
            Class.forName("rikka.shizuku.server.IShizukuService\$Stub")
        } catch (e: ClassNotFoundException) {
            Class.forName("moe.shizuku.server.IShizukuService\$Stub")
        }
        val service = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)

        val newProcess = service.javaClass.methods.first {
            it.name == "newProcess" && it.parameterTypes.size == 3
        }
        val remote = newProcess.invoke(service, cmd, null, null)

        // Construtor package-private: ShizukuRemoteProcess(IRemoteProcess)
        val ctor = ShizukuRemoteProcess::class.java.declaredConstructors.first {
            it.parameterTypes.size == 1 &&
                it.parameterTypes[0].simpleName == "IRemoteProcess"
        }
        ctor.isAccessible = true
        return ctor.newInstance(remote) as Process
    }

    private fun anexar(tv: TextView, texto: String) {
        runOnUiThread { tv.append(texto) }
    }

    private fun executar(modo: String) {
        val conteudo = scriptConteudo ?: return
        val tmp = "/data/local/tmp/luks_script.sh"

        // Terminal em diálogo, saída ao vivo
        val tv = TextView(this).apply {
            setTextColor(0xFFB8F5C0.toInt())
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(px(12), px(12), px(12), px(12))
            text = "Script: $scriptNome\nModo: ${if (modo == "root") "Root" else "Shizuku"}\n\n"
        }
        val scroll = ScrollView(this).apply { addView(tv) }
        AlertDialog.Builder(this)
            .setTitle("Terminal")
            .setView(scroll)
            .setPositiveButton("Fechar", null)
            .show()

        Thread {
            try {
                // 1) Enviar o script para /data/local/tmp via stdin
                val escritor = if (modo == "root")
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "cat > $tmp"))
                else
                    shizukuProcess(arrayOf("sh", "-c", "cat > $tmp"))
                escritor.outputStream.use { it.write(conteudo.toByteArray()) }
                escritor.waitFor()

                // 2) Dar permissão de execução
                val chmod = if (modo == "root")
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 777 $tmp"))
                else
                    shizukuProcess(arrayOf("sh", "-c", "chmod 777 $tmp"))
                chmod.waitFor()

                // 3) Executar
                val proc = if (modo == "root")
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "sh $tmp"))
                else
                    shizukuProcess(arrayOf("sh", tmp))

                val t1 = Thread {
                    proc.inputStream.bufferedReader().forEachLine { anexar(tv, "$it\n") }
                }
                val t2 = Thread {
                    proc.errorStream.bufferedReader().forEachLine { anexar(tv, "[erro] $it\n") }
                }
                t1.start(); t2.start()
                proc.waitFor()
                t1.join(); t2.join()
                anexar(tv, "\n[finalizado — código ${proc.exitValue()}]\n")
            } catch (e: Exception) {
                anexar(tv, "\n[ERRO] ${e.message}\n")
            }
        }.start()
    }
}
