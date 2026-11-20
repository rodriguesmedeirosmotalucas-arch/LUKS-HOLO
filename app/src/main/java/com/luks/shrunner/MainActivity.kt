package com.luks.shrunner

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val selecionarScript = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importarScript(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnCarregar).setOnClickListener {
            selecionarScript.launch(arrayOf("*/*"))
        }
    }

    private fun importarScript(uri: Uri) {
        Thread {
            val resultado = runCatching {
                copiarParaPastaDoApp(uri)
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                resultado.fold(
                    onSuccess = { arquivo ->
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Script importado")
                            .setMessage(
                                "${arquivo.name}\n\n" +
                                    "Foi guardado no armazenamento privado do app. " +
                                    "Nesta etapa, ele ainda não é executado."
                            )
                            .setPositiveButton("OK", null)
                            .show()
                    },
                    onFailure = { erro ->
                        Toast.makeText(
                            this@MainActivity,
                            erro.message ?: "Não foi possível importar o arquivo.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }.start()
    }

    private fun copiarParaPastaDoApp(uri: Uri): File {
        val nomeOriginal = obterNome(uri)

        if (!nomeOriginal.endsWith(".sh", ignoreCase = true)) {
            throw IOException("Selecione um arquivo com a extensão .sh.")
        }

        val pastaScripts = File(filesDir, "scripts")
        if (!pastaScripts.exists() && !pastaScripts.mkdirs()) {
            throw IOException("Não foi possível criar a pasta de scripts.")
        }

        val destino = criarNomeUnico(pastaScripts, nomeOriginal)

        try {
            val entrada = contentResolver.openInputStream(uri)
                ?: throw IOException("Não foi possível abrir o arquivo escolhido.")

            entrada.use { input ->
                destino.outputStream().buffered().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var total = 0L

                    while (true) {
                        val quantidade = input.read(buffer)
                        if (quantidade == -1) break

                        total += quantidade
                        if (total > MAX_SCRIPT_BYTES) {
                            throw IOException("O arquivo passou do limite de 10 MB.")
                        }

                        output.write(buffer, 0, quantidade)
                    }
                }
            }
        } catch (erro: Exception) {
            destino.delete()
            throw erro
        }

        return destino
    }

    private fun obterNome(uri: Uri): String {
        val nomeDoProvedor = contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val coluna = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (coluna >= 0 && cursor.moveToFirst()) cursor.getString(coluna) else null
        }

        return nomeDoProvedor?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringAfterLast(':')
                ?.takeIf { it.isNotBlank() }
            ?: "script.sh"
    }

    private fun criarNomeUnico(pasta: File, nomeOriginal: String): File {
        val nomeSemCaminho = nomeOriginal
            .substringAfterLast('/')
            .substringAfterLast('\\')

        var nomeSeguro = nomeSemCaminho
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(100)

        if (nomeSeguro.isBlank() || nomeSeguro.equals(".sh", ignoreCase = true)) {
            nomeSeguro = "script.sh"
        }
        if (!nomeSeguro.endsWith(".sh", ignoreCase = true)) {
            nomeSeguro += ".sh"
        }

        val base = nomeSeguro.dropLast(3)
        var arquivo = File(pasta, nomeSeguro)
        var numero = 1

        while (arquivo.exists()) {
            arquivo = File(pasta, "${base}_$numero.sh")
            numero++
        }

        return arquivo
    }

    companion object {
        private const val MAX_SCRIPT_BYTES = 10L * 1024L * 1024L
    }
}
