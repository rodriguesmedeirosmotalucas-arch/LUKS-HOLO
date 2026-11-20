class MainActivity : AppCompatActivity() {
    private val SAFAchoo = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.data?.let { uri -> processScript(uri) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnCarregar).setOnClickListener { showManagerChoice() }
    }

    private fun showManagerChoice() {
        AlertDialog.Builder(this)
            .setTitle("📂 Importar script .sh")
            .setItems(arrayOf(
                "🟣 Gerenciador LUKS STORE (recomendado)",
                "📱 Gerenciador do celular")) { _, which ->
                if (which == 0) openLuksManager()
                else SAFAchoo.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"   // alguns gerenciadores não expõem .sh com type custom
                })
            }.show()
    }

    private fun openLuksManager() {
        val dirs = listOf(
            "/sdcard", "/sdcard/Download", "/data/local/tmp")
        val files = dirs.flatMap { d ->
            File(d).takeIf { it.canRead() }?.listFiles { f -> f.name.endsWith(".sh") }
                ?.toList() ?: emptyList() }
        AlertDialog.Builder(this)
            .setTitle("Gerenciador LUKS STORE")
            .setItems(files.map { "📄 ${it.name}\n   ${it.parent}" }.toTypedArray()) { _, i ->
                processScript(files[i])
            }.show()
    }

    private fun processScript(source: Any) = lifecycleScope.launch(Dispatchers.IO) {
        val dest = ScriptTransporter.transport(this@MainActivity, source)
        runOnUiThread {
            PermissionsDialog(this@MainActivity, dest) { path ->
                ExecuteDialog(this@MainActivity, path) { mode ->
                    TerminalActivity.start(this@MainActivity, path, mode)
                }.show()
            }.show()
        }
    }
}

