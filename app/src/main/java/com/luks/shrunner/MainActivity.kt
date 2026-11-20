package com.luks.shrunner

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.IOException

@Suppress("DEPRECATION")
class MainActivity : Activity() {

    private val roxo = Color.parseColor("#6D28D9")
    private val fundo = Color.parseColor("#28153D")

    private var arquivo: Uri? = null
    @Volatile private var processoAtivo: Process? = null
    @Volatile private var encerrada = false

    private lateinit var escolher: Button
    private lateinit var executar: Button
    private lateinit var modoRoot: CheckBox
    private lateinit var terminal: TextView
    private lateinit var rolagem: ScrollView
    private lateinit var campo: EditText
    private lateinit var enviar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val marca = TextView(this).apply {
            text = "LUKS STORE"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(roxo)
            setPadding(dp(12), dp(16), dp(12), dp(16))
        }

        escolher = botao("Escolher arquivo .sh") {
            val seletor = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(seletor, 1)
        }

        modoRoot = CheckBox(this).apply {
            text = "Executar como root"
            isChecked = false
            setTextColor(Color.WHITE)
            buttonTintList = ColorStateList.valueOf(Color.parseColor("#D8B4FE"))
        }

        executar = botao("Executar") { confirmarExecucao() }.apply {
            isEnabled = false
        }

        terminal = TextView(this).apply {
            text = "Escolha um arquivo .sh para começar.\n"
            textSize = 15f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        rolagem = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#170C24"))
            addView(terminal)
        }

        campo = EditText(this).apply {
            hint = "Digite uma opção (ex.: 1)"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#D8B4FE"))
            imeOptions = EditorInfo.IME_ACTION_SEND
            isEnabled = false

            setOnEditorActionListener { _, acao, evento ->
                when {
                    evento != null && evento.keyCode == KeyEvent.KEYCODE_ENTER -> {
                        if (evento.action == KeyEvent.ACTION_DOWN) enviarLinha()
                        true
                    }
                    acao == EditorInfo.IME_ACTION_SEND -> {
                        enviarLinha()
                        true
                    }
                    else -> false
                }
            }
        }

        enviar = botao("Enviar") { enviarLinha() }.apply {
            isEnabled = false
        }

        val barraEntrada = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
            addView(campo, LinearLayout.LayoutParams(0, -2, 1f))
            addView(enviar)
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setBackgroundColor(fundo)
            addView(marca)
            addView(escolher)
            addView(modoRoot)
            addView(executar)
            addView(rolagem, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(barraEntrada)
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            arquivo = data?.data ?: return
            terminal.text = "Arquivo selecionado. Toque em Executar.\n"
            executar.isEnabled = true
        }
    }

    private fun confirmarExecucao() {
        val uri = arquivo ?: return
        if (!modoRoot.isChecked) {
            rodarScript(uri, false)
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Executar como root?")
            .setMessage("O script poderá alterar o sistema. Continue só se confiar no arquivo.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Executar") { _, _ -> rodarScript(uri, true) }
            .show()
    }

    private fun rodarScript(uri: Uri, comoRoot: Boolean) {
        if (!executar.isEnabled) return

        escolher.isEnabled = false
        executar.isEnabled = false
        modoRoot.isEnabled = false
        campo.text.clear()
        terminal.text = if (comoRoot) "[Executando como root...]\n" else "[Executando...]\n"

        Thread {
            var copia: File? = null
            var shell: Process? = null

            try {
                val script = File.createTempFile("shrunner-", ".sh", cacheDir)
                copia = script

                val origem = contentResolver.openInputStream(uri)
                    ?: error("Não foi possível abrir o arquivo.")
                origem.use { entrada ->
                    script.outputStream().use { saida -> entrada.copyTo(saida) }
                }

                if (encerrada) return@Thread

                val caminho = script.absolutePath
                val comando = if (comoRoot) {
                    val caminhoSeguro = "'" + caminho.replace("'", "'\\''") + "'"
                    listOf("su", "-c", "exec /system/bin/sh $caminhoSeguro")
                } else {
                    listOf("/system/bin/sh", caminho)
                }

                val processo = ProcessBuilder(comando)
                    .directory(cacheDir)
                    .redirectErrorStream(true)
                    .start()

                shell = processo
                processoAtivo = processo
                if (encerrada) return@Thread

                naTela {
                    if (processoAtivo === processo) habilitarEntrada(true)
                }

                processo.inputStream.bufferedReader(Charsets.UTF_8).use { leitor ->
                    val buffer = CharArray(1024)
                    while (!encerrada) {
                        val n = leitor.read(buffer)
                        if (n == -1) break
                        mostrar(String(buffer, 0, n))
                    }
                }

                if (!encerrada) {
                    mostrar("\n[Finalizado com código ${processo.waitFor()}]\n")
                }
            } catch (e: Exception) {
                mostrar("\n[Erro: ${e.message ?: "não foi possível executar"}]\n")
            } finally {
                processoAtivo = null
                shell?.destroy()
                copia?.delete()
                naTela {
                    habilitarEntrada(false)
                    escolher.isEnabled = true
                    executar.isEnabled = arquivo != null
                    modoRoot.isEnabled = true
                }
            }
        }.start()
    }

    private fun enviarLinha() {
        val processo = processoAtivo ?: return
        if (!enviar.isEnabled) return

        val linha = (campo.text.toString() + "\n").toByteArray(Charsets.UTF_8)
        campo.text.clear()
        enviar.isEnabled = false

        Thread {
            val enviado = try {
                processo.outputStream.write(linha)
                processo.outputStream.flush()
                true
            } catch (_: IOException) {
                false
            }

            naTela {
                if (processoAtivo === processo) {
                    if (enviado) {
                        enviar.isEnabled = true
                    } else {
                        habilitarEntrada(false)
                        mostrar("\n[Não foi possível enviar a opção ao script.]\n")
                    }
                }
            }
        }.start()
    }

    private fun habilitarEntrada(ativa: Boolean) {
        campo.isEnabled = ativa
        enviar.isEnabled = ativa
    }

    private fun mostrar(texto: String) = naTela {
        terminal.append(texto)
        if (terminal.text.length > 50_000) {
            terminal.text = terminal.text.takeLast(50_000)
        }
        rolagem.post { rolagem.scrollTo(0, terminal.height) }
    }

    private fun naTela(acao: () -> Unit) {
        runOnUiThread { if (!encerrada) acao() }
    }

    private fun botao(titulo: String, acao: () -> Unit) = Button(this).apply {
        text = titulo
        setAllCaps(false)
        setTextColor(Color.WHITE)
        backgroundTintList = ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(Color.parseColor("#574468"), roxo)
        )
        setOnClickListener { acao() }
    }

    private fun dp(valor: Int): Int =
        (valor * resources.displayMetrics.density + 0.5f).toInt()

    override fun onDestroy() {
        encerrada = true
        processoAtivo?.let { processo ->
            Thread { processo.destroy() }.start()
        }
        super.onDestroy()
    }
}
