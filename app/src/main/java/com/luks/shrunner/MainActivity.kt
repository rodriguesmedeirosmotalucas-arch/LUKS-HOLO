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
            if (transportUri != null) nav(Environment.getExternalStorageDirectory(), true)
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

        col.addView(botao("📂  Carregar script (.sh)", 0xFF7B2FBE.toInt()) { abrirSeletor() }, lb)
        col.addView(botao("🗂️  Gerenciador próprio", 0xFF4B1D6E.toInt()) { abrirProprio() }, lb)
        col.addView(botao("📦  Transportar arquivo", 0xFF8C6A2E.toInt()) { transportar() }, lb)
        col.addView(botao("🔓  Ativar acesso total", 0xFF1D5C8C.toInt()) { acaoPerm() }, lb)
        col.addView(botao("⚡  Executar via Shizuku", 0xFF2E7D5B.toInt()) { acaoShizuku() }, lb)
        col.addView(botao("🧱  Executar via Root", 0xFF8C3B2E.toInt()) { acaoRoot() }, lb)
        col.addView(TextView(this).apply {
            text = "Scripts com menu aceitam resposta no terminal.\nUse Transportar p/ copiar p/ Android/data."
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
            nav(Environment.getExternalStorageDirectory(), transportUri != null)
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

    private fun abrirProprio() {
        if (temAcesso()) nav(Environment.getExternalStorageDirectory(), false)
        else pedir()
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

    private fun colar(destino: File) {
        val uri = transportUri ?: return
        val nome = transportNome ?: return
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
                    if (nova.mkdirs() || nova.exists()) nav(nova, destino)
                    else Toast.makeText(this, "Falha ao criar", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun nav(pasta: File, destino: Boolean) {
        val itens = pasta.listFiles()
        if (itens == null) {
            Toast.makeText(this, "Sem acesso: ${pasta.absolutePath}", Toast.LENGTH_SHORT).show()
            return
        }
        val ord = itens.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
        val temPai = pasta.parentFile != null
        val nomes = ArrayList<String>()
        if (temPai) nomes.add("⬆️  ..")
        if (destino) {
            nomes.add("✅  COPIAR ARQUIVO AQUI")
            nomes.add("➕  Criar pasta")
            nomes.add("🎯  Ir para Android/data")
        }
        ord.forEach { nomes.add(if (it.isDirectory) "📁 ${it.name}" else "📄 ${it.name}") }

        val extra = if (destino) 3 else 0
        val off = (if (temPai) 1 else 0) + extra
        val iColar = if (temPai) 1 else 0
        val iCriar = iColar + 1
        val iData = iColar + 2

        AlertDialog.Builder(this)
            .setTitle(pasta.absolutePath)
            .setItems(nomes.toTypedArray()) { _, pos ->
                when {
                    temPai && pos == 0 -> nav(pasta.parentFile!!, destino)
                    destino && pos == iColar -> colar(pasta)
                    destino && pos == iCriar -> novaPasta(pasta, destino)
                    destino && pos == iData -> {
                        val d = File(Environment.getExternalStorageDirectory(), "Android/data")
                        if (d.exists()) nav(d, true)
                        else Toast.makeText(this, "Android/data não encontrado", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        val alvo = ord[pos - off]
                        if (alvo.isDirectory) nav(alvo, destino)
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
            if (temAcesso()) nav(Environment.getExternalStorageDirectory(), true)
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
        val ctor = ShizukuRemoteProcess::class.java.declaredConstructors.first {
            it.parameterTypes.size == 1 && it.parameterTypes[0].simpleName == "IRemoteProcess"
        }
        ctor.isAccessible = true
        return ctor.newInstance(remote) as Process
    }

    private fun anexar(tv: TextView, s: String) {
        runOnUiThread { tv.append(s) }
    }

    private fun executar(modo: String) {
        val conteudo = scriptConteudo ?: return
        val tmp = "/data/local/tmp/luks_script.sh"

        val tv = TextView(this).apply {
            setTextColor(0xFFB8F5C0.toInt())
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(px(12), px(12), px(12), px(12))
            text = "Script: $scriptNome\nModo: ${if (modo == "root") "Root" else "Shizuku"}\n\n"
        }
        val scroll = ScrollView(this).apply { addView(tv) }
        val input = EditText(this).apply {
            hint = "Digite e envie..."
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF6E5A8A.toInt())
            maxLines = 1
        }
        val bEnv = Button(this).apply {
            text = "Enviar ➤"
            setTextColor(Color.WHITE)
            setAllCaps(false)
            background = card(0xFF2E7D5B.toInt(), 10)
        }
        val linha = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(px(8), px(6), px(8), px(8))
            gravity = Gravity.CENTER_VERTICAL
        }
        linha.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        linha.addView(bEnv)
        val caixa = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0D0514.toInt())
        }
        caixa.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(380)))
        caixa.addView(linha)
        AlertDialog.Builder(this)
            .setTitle("Terminal")
            .setView(caixa)
            .setPositiveButton("Fechar", null)
            .show()

        bEnv.setOnClickListener {
            val p = procAtual
            val t = input.text.toString().trim()
            if (p == null) Toast.makeText(this, "Nenhum script em execução", Toast.LENGTH_SHORT).show()
            else if (t.isNotEmpty()) {
                anexar(tv, "> $t\n")
                input.setText("")
                try {
                    p.outputStream.write((t + "\n").toByteArray())
                    p.outputStream.flush()
                } catch (e: Exception) {
                    anexar(tv, "[encerrado]\n")
                }
            }
        }

        Thread {
            try {
                val w = if (modo == "root") Runtime.getRuntime().exec(arrayOf("su", "-c", "cat > $tmp"))
                        else shzProc(arrayOf("sh", "-c", "cat > $tmp"))
                w.outputStream.use { it.write(conteudo.toByteArray()) }
                w.waitFor()
                val c = if (modo == "root") Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 777 $tmp"))
                        else shzProc(arrayOf("sh", "-c", "chmod 777 $tmp"))
                c.waitFor()
                val p = if (modo == "root") Runtime.getRuntime().exec(arrayOf("su", "-c", "sh $tmp"))
                        else shzProc(arrayOf("sh", tmp))
                procAtual = p
                val t1 = Thread { p.inputStream.bufferedReader().forEachLine { anexar(tv, "$it\n") } }
                val t2 = Thread { p.errorStream.bufferedReader().forEachLine { anexar(tv, "[erro] $it\n") } }
                t1.start(); t2.start()
                p.waitFor()
                t1.join(); t2.join()
                procAtual = null
                anexar(tv, "\n[finalizado — código ${p.exitValue()}]\n")
            } catch (e: Exception) {
                procAtual = null
                anexar(tv, "\n[ERRO] ${e.message}\n")
            }
        }.start()
    }
}
