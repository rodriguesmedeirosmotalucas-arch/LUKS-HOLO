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

    // Abre seletor onde o usuário ESCOLHE o gerenciador (ZArchiver, MT Manager...)
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

    // ============ NAVEGADOR (armazenamento normal) ============
    private fun navFile(pasta: File, destino: Boolean) {
        val itens = pasta.listFiles()
        if (itens == null) {
            if (temRootQuick()) navShell(pasta.absolutePath, destino)
            else Toast.makeText(this, "Sem acesso: ${pasta.absolutePath}", Toast.LENGTH_SHORT).show()
            return
        }
        val ord = itens.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
        val temPai = pasta.parentFile != null
        val nomes = ArrayList<String>()
        if (temPai) nomes.add("⬆️  ..")
        nomes.add("🎯  Ir para Android/data")
        nomes.add("🎮  Ir para /data/local/tmp (Root)")
        if (destino) {
            nomes.add("✅  COPIAR ARQUIVO AQUI")
            nomes.add("➕  Criar pasta")
        }
        ord.forEach { nomes.add(if (it.isDirectory) "📁 ${it.name}" else "📄 ${it.name}") }

        val off = (if (temPai) 1 else 0) + 2 + (if (destino) 2 else 0)
        val iData = if (temPai) 1 else 0
        val iTmp = iData + 1
        val iColar = iTmp + 1
        val iCriar = iColar + 1

        AlertDialog.Builder(this)
            .setTitle(pasta.absolutePath)
            .setItems(nomes.toTypedArray()) { _, pos ->
                when {
                    temPai && pos == 0 -> navFile(pasta.parentFile!!, destino)
                    pos == iData -> {
                        val d = File("/storage/emulated/0/Android/data")
                        if (d.exists()) navFile(d, destino)
                        else navShell("/storage/emulated/0/Android/data", destino)
                    }
                    pos == iTmp -> navDataTmp(destino)
                    destino && pos == iColar -> colar(pasta)
                    destino && pos == iCriar -> novaPasta(pasta, destino)
                    else -> {
                        val alvo = ord[pos - off]
                        if (alvo.isDirectory) navFile(alvo, destino)
                        else if (destino) Toast.makeText(this, "Use 'COPIAR ARQUIVO AQUI'", Toast.LENGTH_SHORT).show()
                        else if (alvo.name.endsWith(".sh")) {
                            try {
                                carregar(alvo.readText(), alvo.name)
                            } catch (e: Exception) {
                                Toast.makeText(this, "Falha ao ler: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        } else Toast.makeText(this, "Escolha um arquivo .sh", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun temRootQuick(): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        p.waitFor() == 0
    } catch (e: Exception) { false }

    // ============ NAVEGADOR ROOT (shell) — igual MT Manager ============
    private fun navShell(path: String, destino: Boolean) {
        Thread {
            val saida = try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls -la '$path' 2>&1"))
                val txt = p.inputStream.bufferedReader().readText()
                p.waitFor()
                txt
            } catch (e: Exception) {
                "ERRO:${e.message}"
            }
            runOnUiThread {
                if (saida.startsWith("ERRO") || saida.contains("denied") || saida.contains("No such")) {
                    Toast.makeText(this, "Root sem acesso a: $path", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val entradas = saida.split("\n")
                    .filter { it.isNotBlank() && !it.startsWith("total") }
                    .mapNotNull { l ->
                        val parts = l.trim().split(Regex("\\s+"), 9)
                        if (parts.size < 9) null else Pair(parts[8], parts[0].startsWith("d"))
                    }
                    .filter { it.first != "." && it.first != ".." }
                    .sortedWith(compareByDescending<Pair<String, Boolean>> { it.second }.thenBy { it.first.lowercase() })

                val nomes = ArrayList<String>()
                nomes.add("⬆️  ..")
                if (destino) nomes.add("✅  COPIAR ARQUIVO AQUI (Root)")
                entradas.forEach { nomes.add(if (it.second) "📁 ${it.first}" else "📄 ${it.first}") }

                val off = 1 + (if (destino) 1 else 0)
                val iColar = 1

                AlertDialog.Builder(this)
                    .setTitle("[Root] $path")
                    .setItems(nomes.toTypedArray()) { _, pos ->
                        when {
                            pos == 0 -> {
                                if (path != "/") {
                                    val pai = path.substringBeforeLast("/", "/")
                                    navShell(if (pai.isBlank()) "/" else pai, destino)
                                }
                            }
                            destino && pos == iColar -> colarShell(path)
                            else -> {
                                val e = entradas[pos - off]
                                val novo = "$path/${e.first}"
                                if (e.second) navShell(novo, destino)
                                else if (destino) Toast.makeText(this, "Use 'COPIAR AQUI (Root)'", Toast.LENGTH_SHORT).show()
                                else if (e.first.endsWith(".sh")) carregarShell(novo)
                                else Toast.makeText(this, "Escolha um arquivo .sh", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }.start()
    }

    private fun colarShell(destino: String) {
        val uri = transportUri
        val nome = transportNome
        if (uri == null || nome == null) {
            Toast.makeText(this, "Nenhum arquivo selecionado", Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            try {
                val local = File(cacheDir, nome)
                contentResolver.openInputStream(uri)?.use { e -> local.outputStream().use { e.copyTo(it) } }
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp '${local.absolutePath}' '$destino/' && chmod 777 '$destino/$nome'"))
                p.waitFor()
                val ok = p.exitValue() == 0
                local.delete()
                runOnUiThread {
                    if (ok) {
                        transportUri = null
                        transportNome = null
                        tvStatus.text = "📦 Copiado (Root): $destino/$nome"
                        Toast.makeText(this, "✅ Copiado para:\n$destino/$nome", Toast.LENGTH_LONG).show()
                    } else Toast.makeText(this, "Falha no cp (Root)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Falha: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun carregarShell(caminho: String) {
        Thread {
            try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat '$caminho'"))
                val txt = p.inputStream.bufferedReader().readText()
                p.waitFor()
                runOnUiThread {
                    if (txt.isNotBlank()) carregar(txt, caminho.substringAfterLast("/"))
                    else Toast.makeText(this, "Arquivo vazio/ilegível", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Falha: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun colar(destino: File) {
        val uri = transportUri
        val nome = transportNome
        if (uri == null || nome == null) {
            Toast.makeText(this, "Nenhum arquivo selecionado", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val alvo = File(destino, nome)
            contentResolver.openInputStream(uri)?.use { e -> alvo.outputStream().use { e.copyTo(it) } }
            transportUri = null
            transportNome = null
            tvStatus.text = "📦 Copiado: ${alvo.absolutePath}"
            Toast.makeText(this, "✅ Copiado:\n${alvo.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Falha ao copiar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun novaPasta(pasta: File, destino: Boolean) {
        val input = EditText(this)
        input.hint = "nome da pasta"
        AlertDialog.Builder(this)
            .setTitle("Nova pasta")
            .setMessage("Criar em:\n${pasta.absolutePath}")
            .setView(input)
            .setPositiveButton("Criar") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) {
                    val nova = File(pasta, n)
                    if (nova.mkdirs() || nova.exists()) navFile(nova, destino)
                    else Toast.makeText(this, "Falha ao criar", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        val uri = data.data ?: return
        if (requestCode == 101) {
            try {
                val nome = queryNome(uri) ?: "script.sh"
                val txt = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (txt != null) carregar(txt, nome)
                else Toast.makeText(this, "Não foi possível ler", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Falha: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else if (requestCode == 202) {
            transportNome = queryNome(uri)
            transportUri = uri
            if (temAcesso()) navFile(baseDir(), true)
            else {
                Toast.makeText(this, "Ative o acesso total p/ escolher destino", Toast.LENGTH_LONG).show()
                pedir()
            }
        }
    }

    private fun queryNome(uri: Uri): String? = try {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        }
    } catch (e: Exception) { null }

    private fun carregar(txt: String, nome: String) {
        scriptConteudo = txt
        scriptNome = nome
        tvStatus.text = "📜 $nome pronto"
        Toast.makeText(this, "Script carregado!", Toast.LENGTH_SHORT).show()
    }

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
        val ok = try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { false }
        if (ok) executar("shizuku")
        else try {
            Shizuku.requestPermission(200)
            Toast.makeText(this, "Conceda a permissão e toque de novo", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Shizuku não está rodando", Toast.LENGTH_LONG).show()
        }
    }

    private fun shzProc(cmd: Array<String>): Process {
        val binder = Shizuku.getBinder() ?: throw IllegalStateException("Shizuku não conectado")
        val stub = try {
            Class.forName("rikka.shizuku.server.IShizukuService\$Stub")
        } catch (e: ClassNotFoundException) {
            Class.forName("moe.shizuku.server.IShizukuService\$Stub")
        }
        val svc = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        val np = svc.javaClass.methods.first { it.name == "newProcess" && it.parameterTypes.size == 3 }
        val remote = np.invoke(svc, cmd, null, null)
        val ctor 
