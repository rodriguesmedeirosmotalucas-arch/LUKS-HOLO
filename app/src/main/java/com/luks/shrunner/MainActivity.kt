    private fun executarArquivoDireto(caminho: String) {
        val dir = caminho.substringBeforeLast("/")
        val nome = caminho.substringAfterLast("/")
        terminal("Arquivo: $nome\nLocal: $caminho (Root direto)") { tv ->
            try {
                anexar(tv, "[diagnóstico: caçando pdcache como Root...]\n")
                val diag = Runtime.getRuntime().exec(arrayOf("su", "-c",
                    "ls /storage/emulated/0/Android/data 2>&1 | head -6; echo '---pdcache---'; find /storage/emulated/0/Android/data /data/data -maxdepth 4 -type d -name 'pdcache' 2>/dev/null; echo '---fim---'"))
                diag.inputStream.bufferedReader().forEachLine { anexar(tv, "$it\n") }
                diag.waitFor()
                anexar(tv, "\n[executando com ambiente de sistema (MT style)...]\n\n")
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c",
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
