
package com.luks.shrunner

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ScriptTransporter {

    suspend fun transport(
        ctx: Context,
        source: Any
    ): File = withContext(Dispatchers.IO) {

        val raw = when (source) {
            is File -> source.readText()
            is Uri -> {
                ctx.contentResolver.openInputStream(source)?.use {
                    it.bufferedReader().readText()
                } ?: throw IllegalArgumentException(
                    "Não foi possível abrir o arquivo."
                )
            }
            else -> throw IllegalArgumentException(
                "Tipo de arquivo não suportado."
            )
        }

        var text = raw
            .replace("\uFEFF", "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        if (!text.startsWith("#!")) {
            text = "#!/system/bin/sh\n$text"
        }

        val folder = File(ctx.filesDir, "scripts")

        if (!folder.exists() && !folder.mkdirs()) {
            throw IllegalStateException(
                "Não foi possível criar a pasta de scripts."
            )
        }

        val name = when (source) {
            is File -> source.name
            is Uri -> "script_luks.sh"
            else -> "script_luks.sh"
        }.substringAfterLast('/')

        val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val dest = File(folder, safeName)

        dest.writeText(text)
        dest.setExecutable(true, true)

        dest
    }
}
