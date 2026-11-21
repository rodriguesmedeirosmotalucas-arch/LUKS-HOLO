package com.luks.shrunner

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.EditText
import android.widget.Toast
import java.io.File

class Navegador(
    private val act: Activity,
    private val onScript: (String, String) -> Unit,
    private val onStatus: (String) -> Unit
) {

    var transportUri: Uri? = null
    var transportNome: String? = null

    var shzExec: ((Array<String>) -> Process)? = null
    var onRunShz: ((String) -> Unit)? = null

    fun temRoot(): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        p.waitFor() == 0
    } catch (e: Exception) { false }

    fun baseDir(): File {
        val f = File("/storage/emulated/0")
        if (f.exists()) return f
        val e = Environment.getExternalStorageDirectory()
        return e ?: File("/sdcard")
    }

    fun abrirInicio(destino: Boolean) = navFile(baseDir(), destino)

    fun navDataTmp(destino: Boolean) {
        Thread {
            val r = temRoot()
            act.runOnUiThread {
                if (r) navShell("/data/local/tmp", destino)
                else Toast.makeText(act, "Root não disponível", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    fun navAndroidData(destino: Boolean) {
        Thread {
            val r = temRoot()
            act.runOnUiThread {
                if (r) navShell("/storage/emulated/0/Android/data", destino)
                else Toast.makeText(act, "Sem Root para Android/data", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    // ===== NOVO: Navegador via SHIZUKU (contexto shell = igual MT Manager) =====
    fun navShz(path: String, destino: Boolean) {
        val exec = shzExec
        if (exec == null) {
            Toast.makeText(act, "Shizuku não configurado", Toast.LENGTH_LONG).show()
            return
        }
        Thread {
            val saida = try {
                val p = exec(arrayOf("sh", "-c", "ls -la '$path' 2>&1"))
                val txt = p.inputStream.bufferedReader().readText()
                p.waitFor()
                txt
            } catch (e: Exception) { "ERRO:${e.message}" }
            act.runOnUiThread {
                if (saida.startsWith("ERRO") || saida.contains("No such") || saida.contains("denied")) {
                    Toast.makeText(act, "Shizuku sem acesso a: $path", Toast.LENGTH_SHORT).show()
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
                if (destino) nomes.add("✅  COPIAR ARQUIVO AQUI (Shizuku)")
                entradas.forEach { nomes.add(if (it.second) "📁 ${it.first}" else "📄 ${it.first}") }

                val off = 1 + (if (destino) 1 else 0)
                val iColar = 1

                AlertDialog.Builder(act)
                    .setTitle("[Shizuku] $path")
                    .setItems(nomes.toTypedArray()) { _, pos ->
                        when {
                            pos == 0 -> {
                                if (path != "/") {
                                    val pai = path.substringBeforeLast("/", "/")
                                    navShz(if (pai.isBlank()) "/" else pai, destino)
                                }
                            }
                            destino && pos == iColar -> colarShz(path)
                            else -> {
                                val e = entradas[pos - off]
                                val novo = "$path/${e.first}"
                                if (e.second) navShz(novo, destino)
                                else if (destino) Toast.makeText(act, "Use 'COPIAR AQUI (Shizuku)'", Toast.LENGTH_SHORT).show()
                                else if (e.first.endsWith(".sh")) menuShz(novo)
                                else Toast.makeText(act, "Escolha um arquivo .sh", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }.start()
    }

    private fun menuShz(caminho: String) {
        AlertDialog.Builder(act)
            .setTitle(caminho.substringAfterLast("/"))
            .setItems(arrayOf("▶️  Executar agora via Shizuku", "📜  Carregar como script")) { _, pos ->
                if (pos == 0) onRunShz?.invoke(caminho)
                else carregarShz(caminho)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun carregarShz(caminho: String) {
        val exec = shzExec ?: return
        Thread {
            try {
                val p = exec(arrayOf("sh", "-c", "cat '$caminho'"))
                val txt = p.inputStream.bufferedReader().readText()
                p.waitFor()
                act.runOnUiThread {
                    if (txt.isNotBlank()) onScript(txt, caminho.substringAfterLast("/"))
                    else Toast.makeText(act, "Arquivo vazio/ilegível", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                act.runOnUiThread { Toast.makeText(act, "Falha: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    fun colarShz(destino: String) {
        val uri = transportUri
        val nome = transportNome
        val exec = shzExec
        if (uri == null || nome == null || exec == null) {
            Toast.makeText(act, "Nenhum arquivo selecionado", Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            try {
                val local = File(act.cacheDir, nome)
                act.contentResolver.openInputStream(uri)?.use { e -> local.outputStream().use { e.copyTo(it) } }
                val tmpFile = "/data/local/tmp/.luks_copiar_$nome"
                val w = exec(arrayOf("sh", "-c", "cat > $tmpFile"))
                w.outputStream.use { it.write(local.readBytes()) }
                w.waitFor()
                val p = exec(arrayOf("sh", "-c", "cp '$tmpFile' '$destino/' && chmod 666 '$destino/$nome' && rm -f '$tmpFile'"))
                p.waitFor()
                val ok = p.exitValue() == 0
                local.delete()
                act.runOnUiThread {
                    if (ok) {
                        transportUri = null
                        transportNome = null
                        onStatus("📦 Copiado (Shizuku): $destino/$nome")
                        Toast.makeText(act, "✅ Copiado para:\n$destino/$nome", Toast.LENGTH_LONG).show()
                    } else Toast.makeText(act, "Falha no cp — tente o modo Root 🎮", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                act.runOnUiThread { Toast.makeText(act, "Falha: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun criarPastaShz(path: String, destino: Boolean) {
        val input = EditText(act)
        input.hint = "nome da pasta"
        AlertDialog.Builder(act)
            .setTitle("Nova pasta (Shizuku)")
            .setMessage("Criar em:\n$path")
            .setView(input)
            .setPositiveButton("Criar") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) {
                    Thread {
                        val exec = shzExec
                        var ok = false
                        if (exec != null) {
                            val p = exec(arrayOf("sh", "-c", "mkdir -p '$path/$n'"))
                            p.waitFor()
                            ok = p.exitValue() == 0
                        }
                        val f = ok
                        act.runOnUiThread {
                            if (f) navShz("$path/$n", destino)
                            else Toast.makeText(act, "Falha ao criar", Toast.LENGTH_SHORT).show()
                        }
                    }.start()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun navFile(pasta: File, destino: Boolean) {
        val itens = pasta.listFiles()
        if (itens == null) {
            if (temRoot()) navShell(pasta.absolutePath, destino)
            else Toast.makeText(act, "Sem acesso: ${pasta.absolutePath}", Toast.LENGTH_SHORT).show()
            return
        }
        val ord = itens.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
        val temPai = pasta.parentFile != null
        val nomes = ArrayList<String>()
        if (temPai) nomes.add("⬆️  ..")
        nomes.add("🎯  Ir para Android/data (Root)")
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

        AlertDialog.Builder(act)
            .setTitle(pasta.absolutePath)
            .setItems(nomes.toTypedArray()) { _, pos ->
                when {
                    temPai && pos == 0 -> navFile(pasta.parentFile!!, destino)
                    pos == iData -> navAndroidData(destino)
                    pos == iTmp -> navDataTmp(destino)
                    destino && pos == iColar -> colar(pasta)
                    destino && pos == iCriar -> novaPasta(pasta, destino)
                    else -> {
                        val alvo = ord[pos - off]
                        if (alvo.isDirectory) navFile(alvo, destino)
                        else if (destino) Toast.makeText(act, "Use 'COPIAR ARQUIVO AQUI'", Toast.LENGTH_SHORT).show()
                        else if (alvo.name.endsWith(".sh")) {
                            try {
                                onScript(alvo.readText(), alvo.name)
                            } catch (e: Exception) {
                                Toast.makeText(act, "Falha ao ler: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        } else Toast.makeText(act, "Escolha um arquivo .sh", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun navShell(path: String, destino: Boolean) {
        Thread {
            val saida = try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls -la '$path' 2>&1"))
                val txt = p.inputStream.bufferedReader().readText()
                p.waitFor()
                txt
            } catch (e: Exception) { "ERRO:${e.message}" }
            act.runOnUiThread {
                if (saida.startsWith("ERRO") || saida.contains("denied") || saida.contains("No such")) {
                    Toast.makeText(act, "Root sem acesso a: $path", Toast.LENGTH_SHORT).show()
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

                AlertDialog.Builder(act)
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
                                else if (destino) Toast.makeText(act, "Use 'COPIAR AQUI (Root)'", Toast.LENGTH_SHORT).show()
                                else if (e.first.endsWith(".sh")) carregarShell(novo)
                                else Toast.makeText(act, "Escolha um arquivo .sh", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }.start()
    }

    fun colarShell(destino: String) {
        val uri = transportUri
        val nome = transportNome
        if (uri == null || nome == null) {
            Toast.makeText(act, "Nenhum arquivo selecionado", Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            try {
                val local = File(act.cacheDir, nome)
                act.contentResolver.openInputStream(uri)?.use { e -> local.outputStream().use { e.copyTo(it) } }
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp '${local.absolutePath}' '$destino/' && chmod 777 '$destino/$nome'"))
                p.waitFor()
                val ok = p.exitValue() == 0
                local.delete()
                act.runOnUiThread {
                    if (ok) {
                        transportUri = null
                        transportNome = null
                        onStatus("📦 Copiado (Root): $destino/$nome")
                        Toast.makeText(act, "✅ Copiado para:\n$destino/$nome", Toast.LENGTH_LONG).show()
                    } else Toast.makeText(act, "Falha no cp (Root)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                act.runOnUiThread { Toast.makeText(act, "Falha: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    fun carregarShell(caminho: String) {
        Thread {
            try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat '$caminho'"))
                val txt = p.inputStream.bufferedReader().readText()
                p.waitFor()
                act.runOnUiThread {
                    if (txt.isNotBlank()) onScript(txt, caminho.substringAfterLast("/"))
                    else Toast.makeText(act, "Arquivo vazio/ilegível", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                act.runOnUiThread { Toast.makeText(act, "Falha: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    fun colar(destino: File) {
        val uri = transportUri
        val nome = transportNome
        if (uri == null || nome == null) {
            Toast.makeText(act, "Nenhum arquivo selecionado", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val alvo = File(destino, nome)
            act.contentResolver.openInputStream(uri)?.use { e -> alvo.outputStream().use { e.copyTo(it) } }
            transportUri = null
            transportNome = null
            onStatus("📦 Copiado: ${alvo.absolutePath}")
            Toast.makeText(act, "✅ Copiado:\n${alvo.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(act, "Falha ao copiar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun novaPasta(pasta: File, destino: Boolean) {
        val input = EditText(act)
        input.hint = "nome da pasta"
        AlertDialog.Builder(act)
            .setTitle("Nova pasta")
            .setMessage("Criar em:\n${pasta.absolutePath}")
            .setView(input)
            .setPositiveButton("Criar") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) {
                    val nova = File(pasta, n)
                    if (nova.mkdirs() || nova.exists()) navFile(nova, destino)
                    else Toast.makeText(act, "Falha ao criar", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun queryNome(uri: Uri): String? = try {
        act.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        }
    } catch (e: Exception) { null }

    fun resultado(code: Int, uri: Uri) {
        if (code == 101) {
            try {
                val nome = queryNome(uri) ?: "script.sh"
                val txt = act.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (txt != null) onScript(txt, nome)
                else Toast.makeText(act, "Não foi possível ler", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(act, "Falha: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else if (code == 202) {
            transportNome = queryNome(uri)
            transportUri = uri
            abrirInicio(true)
        }
    }
}
