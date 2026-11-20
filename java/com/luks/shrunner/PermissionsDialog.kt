class PermissionsDialog(
    ctx: Context, private val file: File,
    private val onOk: (String) -> Unit
) : AlertDialog.Builder(ctx) {

    private var bits = 0b111101101  // rwxr-xr-x inicial
    private val labels = listOf("Owner", "Group", "Other")
    private val perms = listOf("Read", "Write", "Exec")

    init {
        setTitle("Permissions")
        val grid = GridLayout(ctx).apply {
            columnCount = 4; orientation = GridLayout.HORIZONTAL
        }
        grid.addView(TextView(ctx))
        perms.forEach { grid.addView(TextView(ctx).apply { text = it }) }

        for (row in 0..2) {
            grid.addView(TextView(ctx).apply { text = labels[row] })
            for (col in 0..2) {
                val idx = row * 3 + col
                grid.addView(CheckBox(ctx).apply {
                    isChecked = (bits shr (8 - idx) and 1) == 1
                    setOnCheckedChangeListener { _, c ->
                        bits = if (c) bits or (1 shl (8 - idx)) else bits and (1 shl (8 - idx)).inv()
                    }
                })
            }
        }
        // Set UID / Set GID / Sticky ficam desmarcados (como no MT)
        setView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(grid)
            addView(TextView(ctx).apply { text = "---- Special Permissions ----" })
            addView(TextView(ctx).apply { text = octal(bits) })
        })
        setNegativeButton("CANCEL", null)
        setPositiveButton("OK") { _, _ ->
            Runtime.getRuntime().exec("chmod ${octal(bits)} ${file.absolutePath}").waitFor()
            onOk(file.absolutePath)
        }
    }

    private fun octal(b: Int) = "${b.toString(8).padStart(3, '0')}" // ex.: rwxrwxrwx(777)
}
