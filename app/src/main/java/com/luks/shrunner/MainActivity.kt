    private fun navFile(pasta: File, destino: Boolean) {
        val itens = pasta.listFiles()
        if (itens == null) {
            if (temRoot()) navShell(pasta.absolutePath, destino)
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
