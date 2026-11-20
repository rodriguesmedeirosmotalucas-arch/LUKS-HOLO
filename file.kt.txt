object ScriptTransporter {
    suspend fun transport(ctx: Context, source: Any): File = withContext(Dispatchers.IO) {
        val tmp = File("/data/local/tmp")
        if (!tmp.exists()) tmp.mkdirs()
        val name = (source as? File)?.name
            ?: ctx.contentResolver.query(source as Uri, null, null, null, null)
                ?.use { c -> c.moveToFirst(); c.getString(0) }?.substringAfterLast('/')
            ?: "script_luks.sh"
        val dest = File(tmp, name)

        val raw = if (source is File) source.readText()
            else ctx.contentResolver.openInputStream(source as Uri)!!
                .bufferedReader().readText()

        // Correções invisíveis de formatação (estilo MT Manager):
        // 1. remove BOM, 2. CRLF -> LF, 3. garante shebang na 1ª linha
        var text = raw.replace("\uFEFF", "").replace("\r\n", "\n").replace("\r", "\n")
        if (!text.startsWith("#!")) text = "#!/system/bin/sh\n$text"

        dest.writeText(text)
        dest.setExecutable(true, false)
        dest
    }
}
