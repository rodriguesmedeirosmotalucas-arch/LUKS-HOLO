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
    private lateinit var nav: Navegador
    private var scriptConteudo: String? = null
    private var scriptNome: String? = null
    private var aguardandoAcesso = false

    @Volatile
    private var procAtual: Process? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nav = Navegador(this, { txt, nome -> carregar(txt, nome) }, { t -> tvStatus.text = t })
        nav.shzExec = { cmd -> shzProc(cmd) }
        nav.onRunShz = { c -> executarShzDireto(c) }
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

        col.addView(botao("🌐  Navegar TUDO via Shizuku (MT style)", 0xFF2E5C8C.toInt()) { acaoNavShz() }, lb)
        col.addView(botao("📜  Executar .sh de /data/local/tmp (Root)", 0xFFB85C2E.toInt()) { executarDeTmp() }, lb)
        col.addView(botao("📂  Carregar script (escolher gerenciador)", 0xFF7B2FBE.toInt()) { abrirArquivo(101) }, lb)
        col.addView(botao("🗂️  Gerenciador próprio", 0xFF4B1D6E.toInt()) { abrirProprio() }, lb)
        col.addView(botao("📦  Transportar arquivo (ZArchiver etc.)", 0xFF8C6A2E.toInt()) { abrirArquivo(202) }, lb)
        col.addView(botao("🎮  Navegar /data/local/tmp (Root)", 0xFF5C2E8C.toInt()) { nav.navDataTmp(false) }, lb)
        col.addView(botao("🔓  Ativar acesso total", 0xFF1D5C8C.toInt()) { acaoPerm() }, lb)
        col.addView(botao("⚡  Executar via Shizuku", 0xFF2E7D5B.toInt()) { acaoShizuku() }, lb)
        col.addView(botao("🧱  Executar via Root", 0xFF8C3B2E.toInt()) { acaoRoot() }, lb)
        col.addView(TextView(this).apply {
            text = "🌐 Shizuku vê Android/data igual MT Manager.\nToque num .sh nele p/ executar no lugar original."
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

    private fun shzPronto(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) { false }

    private fun acaoNavShz() {
        if (shzPronto()) {
            nav.navShz("/storage/emulated/0", false)
        } else try {
            Shizuku.requestPermission(200)
            Toast.makeText(this, "Conceda a permissão no Shizuku e toque de novo", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Shizuku não está rodando — abra o app Shizuku", Toast.LENGTH_LONG).show()
        }
    }

    private fun temAcesso(): Boolean {
        return if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun acaoPerm() {
        if (temAcesso()) {
            tvStatus.text = "🔓 Acesso total: ATIVO"
            Toast.makeText(this, "Acesso total ativo ✅", Toast.LENGTH_SHORT).show()
        } else pedir()
    }

    private fun pedir() {
        aguardandoAcesso = true
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (e2: Exception) {
                    aguardandoAcesso = false
                }
            }
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
        if (temAcesso()) nav.abrirInicio(false)
        else pedir()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        val uri = data.data ?: return
        nav.resultado(requestCode, uri)
    }

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
        if (shzPronto()) executar("shizuku")
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

    private fun terminal(titulo: String, bloco: (TextView) -> Unit) {
        val tv = TextView(this).apply {
            setTextColor(0xFFB8F5C0.toInt())
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(px(12), px(12), px(12), px(12))
            text = "$titulo\n\n"
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

        Thread { bloco(tv) }.start()
    }

    private fun executarDeTmp() {
        Thread {
            val saida = try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls /data/local/tmp/*.sh 2>&1"))
                val txt = p.inputStream.bufferedReader().readText()
                p.waitFor()
                txt
            } catch (e: Exception) { "ERRO:${e.message}" }
            runOnUiThread {
                if (saida.startsWith("ERRO") || saida.contains("No such") || saida.contains("denied")) {
                    Toast.makeText(this, "Root indisponível ou nenhum .sh em /data/local/tmp", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val arquivos = saida.split("\n").map { it.trim() }.filter { it.endsWith(".sh") }
                if (arquivos.isEmpty()) {
                    Toast.makeText(this, "Nenhum .sh em /data/local/tmp", Toast.LENGTH_SHORT).show()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Executar de /data/local/tmp")
                        .setItems(arquivos.toTypedArray()) { _, pos -> executarArquivoDireto(arquivos[pos]) }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
            }
        }.start()
    }

    private fun executarArquivoDireto(caminho: String) {
        val dir = caminho.substringBeforeLast("/")
        val nome = caminho.substringAfterLast("/")
        val fixado = "$dir/.luks_fix.sh"
        terminal("Arquivo: $nome\nCorreção Android/data → /data/media/0") { tv ->
            try {
                anexar(tv, "[1/3] Corrigindo caminhos do script...\n")
                val fix = Runtime.getRuntime().exec(arrayOf("su", "-c",
                    "sed 's|/storage/emulated/0/Android/data|/data/media/0/Android/data|g; s|/sdcard/Android/data|/data/media/0/Android/data|g' '$caminho' > $fixado && chmod 777 $fixado"))
                fix.waitFor()
                anexar(tv, "[2/3] Diagnóstico do pdcache...\n")
                val diag = Runtime.getRuntime().exec(arrayOf("su", "-c",
                    "echo '--- script procura:'; grep -n pdcache '$caminho' | head -4; echo '--- pdcache real:'; find /data/media/0/Android/data /data/data -maxdepth 5 -type d -name 'pdcache' 2>/dev/null | head -5; echo '---fim---'"))
                diag.inputStream.bufferedReader().forEachLine { anexar(tv, "$it\n") }
                diag.waitFor()
                anexar(tv, "\n[3/3] Executando...\n\n")
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c",
                    "export EXTERNAL_STORAGE=/sdcard; export HOME=/data/local/tmp; export TERM=xterm; cd '$dir' && sh $fixado"))
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
        }
    }

    private fun executarShzDireto(caminho: String) {
        val dir = caminho.substringBeforeLast("/")
        val nome = caminho.substringAfterLast("/")
        terminal("Arquivo: $nome\n[Shizuku] execução no lugar original") { tv ->
            try {
                anexar(tv, "[executando via Shizuku (contexto shell = MT Manager)...]\n\n")
                val p = shzProc(arrayOf("sh", "-c",
                    "export EXTERNAL_STORAGE=/sdcard; export HOME=/data/local/tmp; export TERM=xterm; cd '$dir' && sh '$caminho'"))
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
        }
    }

    private fun executar(modo: String) {
        val conteudo = scriptConteudo ?: return
        if (conteudo.isBlank()) {
            Toast.makeText(this, "Script vazio — carregue de novo", Toast.LENGTH_LONG).show()
            return
        }
        val dir = "/data/local/tmp"
        val tmp = "$dir/luks_script.sh"
        terminal("Script: $scriptNome\nModo: ${if (modo == "root") "Root" else "Shizuku"}") { tv ->
            try {
                anexar(tv, "[1/3] Enviando script...\n")
                val prep = if (modo == "root") Runtime.getRuntime().exec(arrayOf("su", "-c", "rm -f $tmp"))
                        else shzProc(arrayOf("sh", "-c", "rm -f $tmp"))
                prep.waitFor()
                val w = if (modo == "root") Runtime.getRuntime().exec(arrayOf("su", "-c", "cat > $tmp"))
                        else shzProc(arrayOf("sh", "-c", "cat > $tmp"))
                w.outputStream.use { it.write(conteudo.toByteArray()) }
                w.waitFor()
                anexar(tv, "[1/3] enviado (exit ${w.exitValue()})\n")
                val c = if (modo == "root") Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 777 $tmp"))
                        else shzProc(arrayOf("sh", "-c", "chmod 777 $tmp"))
                c.waitFor()
                anexar(tv, "[2/3] Permissões OK\n[3/3] Executando dentro de $dir...\n\n")
                val p = if (modo == "root") Runtime.getRuntime().exec(arrayOf("su", "-c", "cd $dir && sh $tmp"))
                        else shzProc(arrayOf("sh", "-c", "cd $dir && sh $tmp"))
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
        }
    }
}
