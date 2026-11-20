class ExecuteDialog(
    ctx: Context, private val path: String,
    private val onExecute: (String) -> Unit
) : AlertDialog.Builder(ctx) {

    init {
        setTitle("Please select an action to perform")
        setMessage("⚠️ You have checked to execute with root privileges, " +
            "please make sure the script does not contain dangerous code!")

        val options = arrayOf(
            "Execute in system environment",
            "Execute in extension pack environment",
            "Execute with root privileges",
            "Execute with Shizuku (/data/local/tmp)")

        setSingleChoiceItems(options, 0) { _, _ }
        setPositiveButton("EXECUTE") { d, _ ->
            val mode = (d as AlertDialog).listView.checkedItemId.toInt()
            onExecute(when (mode) {
                2 -> "ROOT"
                3 -> "SHIZUKU"
                else -> "SYSTEM"
            })
        }
        setNeutralButton("EDIT") { _, _ ->
            // abre editor interno com o conteúdo do .sh
            EditScriptActivity.start(ctx, path)
        }
        setNegativeButton("PERMISSIONS", null)
        show()
    }
}
