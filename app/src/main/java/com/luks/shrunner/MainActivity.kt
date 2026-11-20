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
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.io.File
import java.io.IOException
import java.util.UUID

class MainActivity : Activity() {

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

    private fun px(valor: Int): Int =
        (valor * resources.displayMetrics.density).toInt()

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
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = px(28)
        }

        val lpBotao = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            px(56)
        ).apply {
            bottomMargin = px(12)
        }

        val dica = TextView(this).apply {
            text = "Carregue um script antes de executar.\n" +
                "Se o Shizuku não estiver ativo, abra o app Shizuku primeiro."
            setTextColor(0xFF6E5A8A.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, px(16), 0, 0)
        }

        coluna.addView(titulo)
        coluna.addView(subtitulo)
        coluna.addView(tvStatus, lpStatus)
        coluna.addView(
            botao("📂  Carregar script (.sh)", 0xFF7B2FBE) { abrirSeletor() },
            lpBotao
        )
        coluna.addView(
            botao("🗂️  Gerenciador próprio", 0xFF4B1D6E) {
                abrirGerenciadorProprio()
            },
            lpBotao
        )
        coluna.addView(
            botao("⚡  Executar via Shizuku", 0xFF2E7D5B) {
                acaoShizuku()
            },
            lpBotao
        )
        coluna.addView(
            botao("🧱  Executar via Root", 0xFF8C3B2E) {
                acaoRoot()
            },
            lpBotao
        )
        coluna.addView(dica)
    }

    private fun botao(
        texto: String,
        cor: Int,
        acao: () -> Unit
    ): Button {
        return Button(this).apply {
            text = texto
            setTextColor(Color.WHITE)
            textSize = 15f
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(cor)
                cornerRadius = px(14).toFloat()
            }
            setOnClickListener { acao() }
        }
    }

    // Seletor de arquivos do Android
    private fun abrirSeletor() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(intent, 101)
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Nenhum seletor de arquivos encontrado",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Navegador próprio; requer acesso a todos os arquivos no Android 11+
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
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                Toast.makeText(
                    this,
                    "Conceda o acesso e volte ao app",
                    Toast.LENGTH_LONG
                ).show()
            } catch (_: Exception) {
                try {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    )
                } catch (_: Exception) {
                    Toast.makeText(
                        this,
                        "Não foi possível abrir as configurações de arquivos",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                100
            )
        }
    }

    @Deprecated("Callback de permissão legado")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (
            requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            navegador(Environment.getExternalStorageDirectory())
        }
    }

    private fun navegador(pasta: File) {
        val itens = pasta.listFiles()

        if (itens == null) {
            Toast.makeText(
                this,
                "Sem acesso: ${pasta.absolutePath}",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val ordenados = itens.sortedWith(
            compareByDescending<File> { it.isDirectory }
                .thenBy { it.name.lowercase() }
        )

        val temPai = pasta.parentFile != null
        val nomes = ArrayList<String>()

        if (temPai) nomes.add("⬆️  ..")

        ordenados.forEach { arquivo ->
            nomes.add(
                if (arquivo.isDirectory) "📁 ${arquivo.name}"
                else "📜 ${arquivo.name}"
            )
        }

        AlertDialog.Builder(this)
            .setTitle(pasta.absolutePath)
            .setItems(nomes.toTypedArray()) { _, posicao ->
                if (temPai && posicao == 0) {
                    navegador(pasta.parentFile!!)
                } else {
                    val indice = posicao - if (temPai) 1 else 0

                    if (indice in ordenados.indices) {
                        val alvo = ordenados[indice]

                        if (alvo.isDirectory) {
                            navegador(alvo)
                        } else if (alvo.name.endsWith(".sh", ignoreCase = true)) {
                            try {
                                selecionar(alvo.readText(), alvo.name)
                            } catch (erro: Exception) {
                                Toast.makeText(
                                    this,
                                    "Falha ao ler: ${erro.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            Toast.makeText(
                                this,
                                "Escolha um arquivo .sh",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    @Deprecated("Seletor legado")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 101 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return

            try {
                val nome = queryNome(uri) ?: "script.sh"

                val conteudo = contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }

                if (conteudo != null) {
                    selecionar(conteudo, nome)
                } else {
                    Toast.makeText(
                        this,
                        "Não foi possível ler o arquivo",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (erro: Exception) {
                Toast.makeText(
                    this,
                    "Falha: ${erro.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun queryNome(uri: Uri): String? {
        return try {
            contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )?.use { cursor ->
                val indice = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && indice >= 0) {
                    cursor.getString(indice)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun selecionar(conteudo: String, nome: String) {
        if (!nome.endsWith(".sh", ignoreCase = true)) {
            Toast.makeText(
                this,
                "Selecione um arquivo com extensão .sh",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        scriptConteudo = conteudo
        scriptNome = nome
        tvStatus.text = "📜 $nome pronto (${conteudo.length} caracteres)"

        Toast.makeText(this, "Script carregado!", Toast.LENGTH_SHORT).show()
    }

    private fun acaoRoot() {
        if (scriptConteudo == null) {
            Toast.makeText(
                this,
                "Carregue um script primeiro",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        confirmarExecucao("root")
    }

    private fun acaoShizuku() {
        if (scriptConteudo == null) {
            Toast.makeText(
                this,
                "Carregue um script primeiro",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val pronto = try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() ==
                PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }

        if (pronto) {
            confirmarExecucao("shizuku")
        } else {
            try {
                Shizuku.requestPermission(200)
                Toast.makeText(
                    this,
                    "Conceda a permissão do Shizuku e toque de novo",
                    Toast.LENGTH_LONG
                ).show()
            } catch (_: Exception) {
                Toast.makeText(
                    this,
                    "Shizuku não está ativo. Abra o app Shizuku primeiro.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun confirmarExecucao(modo: String) {
        val nome = scriptNome ?: "script"

        AlertDialog.Builder(this)
            .setTitle("Confirmar execução")
            .setMessage(
                "Executar \"$nome\" usando ${if (modo == "root") "Root" else "Shizuku"}?\n\n" +
                    "Um script pode alterar ou apagar dados do aparelho. " +
                    "Execute apenas scripts que você conhece e confia."
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Executar") { _, _ ->
                executar(modo)
            }
            .show()
    }

    private fun anexar(textView: TextView, texto: String) {
        runOnUiThread {
            textView.append(texto)
        }
    }

    private fun executar(modo: String) {
        val conteudo = scriptConteudo ?: return
        val nome = scriptNome ?: "script.sh"

        // Nome aleatório para evitar sobrescrever outro arquivo temporário.
        val temporario = "/data/local/tmp/luks_${UUID.randomUUID()}.sh"

        val terminal = TextView(this).apply {
            setTextColor(0xFFB8F5C0.toInt())
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(px(12), px(12), px(12), px(12))
            text = "Script: $nome\n" +
                "Modo: ${if (modo == "root") "Root" else "Shizuku"}\n\n"
        }

        val scroll = ScrollView(this).apply {
            addView(terminal)
        }

        AlertDialog.Builder(this)
            .setTitle("Terminal")
            .setView(scroll)
            .setPositiveButton("Fechar", null)
            .show()

        Thread {
            try {
                val escritor = if (modo == "root") {
                    Runtime.getRuntime().exec(
                        arrayOf("su", "-c", "cat > '$temporario'")
                    )
                } else {
                    Shizuku.newProcess(
                        arrayOf("sh", "-c", "cat > '$temporario'"),
                        null,
                        null
                    )
                }

                escritor.outputStream.use { saida ->
                    saida.write(conteudo.toByteArray(Charsets.UTF_8))
                }

                val codigoGravacao = escritor.waitFor()
                if (codigoGravacao != 0) {
                    throw IOException(
                        "Falha ao copiar o script (código $codigoGravacao)"
                    )
                }

                // Não precisa de chmod: o script é executado passando-o para sh.
                val processo = if (modo == "root") {
                    Runtime.getRuntime().exec(
                        arrayOf("su", "-c", "sh '$temporario'")
                    )
                } else {
                    Shizuku.newProcess(
                        arrayOf("sh", temporario),
                        null,
                        null
                    )
                }

                val leitorSaida = Thread {
                    try {
                        processo.inputStream.bufferedReader().forEachLine {
                            anexar(terminal, "$it\n")
                        }
                    } catch (erro: Exception) {
                        anexar(
                            terminal,
                            "[erro ao ler saída: ${erro.message}]\n"
                        )
                    }
                }

                val leitorErro = Thread {
                    try {
                        processo.errorStream.bufferedReader().forEachLine {
                            anexar(terminal, "[erro] $it\n")
                        }
                    } catch (erro: Exception) {
                        anexar(
                            terminal,
                            "[erro ao ler mensagem: ${erro.message}]\n"
                        )
                    }
                }

                leitorSaida.start()
                leitorErro.start()

                val codigo = processo.waitFor()
                leitorSaida.join()
                leitorErro.join()

                anexar(
                    terminal,
                    "\n[finalizado — código $codigo]\n"
                )
            } catch (erro: Exception) {
                anexar(
                    terminal,
                    "\n[ERRO] ${erro.message}\n"
                )
            } finally {
                removerTemporario(modo, temporario)
            }
        }.start()
    }

    private fun removerTemporario(modo: String, caminho: String) {
        try {
            val processo = if (modo == "root") {
                Runtime.getRuntime().exec(
                    arrayOf("su", "-c", "rm -f '$caminho'")
                )
            } else {
                Shizuku.newProcess(
                    arrayOf("rm", "-f", caminho),
                    null,
                    null
                )
            }

            processo.waitFor()
        } catch (_: Exception) {
            // A limpeza é uma tentativa; não interrompe a tela do app.
        }
    }
}
