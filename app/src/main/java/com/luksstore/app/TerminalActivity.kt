class TerminalActivity : AppCompatActivity() {
    private lateinit var process: Process
    private lateinit var input: BufferedWriter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)
        val path = intent.getStringExtra("PATH")!!
        val mode = intent.getStringExtra("MODE") ?: "SYSTEM"
        val output = findViewById<TextView>(R.id.txtTerminal)

        lifecycleScope.launch(Dispatchers.IO) {
            process = when (mode) {
                "ROOT" -> ProcessBuilder("su", "-c", "sh $path").start()
                "SHIZUKU" -> ShizukuShell.exec("sh $path")   // ver módulo abaixo
                else -> ProcessBuilder("sh", path).start()
            }
            input = process.outputStream.bufferedWriter()

            process.inputStream.bufferedReader().forEachLine { line ->
                runOnUiThread { output.append(line + "\n") }  // saída ao vivo, linha por linha
            }
        }

        findViewById<Button>(R.id.btnEnviar).setOnClickListener {
            val cmd = findViewById<EditText>(R.id.edtInput).text.toString()
            input.write(cmd + "\n"); input.flush()
            findViewById<EditText>(R.id.edtInput).setText("")
        }

        findViewById<Button>(R.id.btnParar).setOnClickListener { process.destroy() }
        findViewById<Button>(R.id.btnLimpar).setOnClickListener { output.text = "" }
    }

    companion object {
        fun start(ctx: Context, path: String, mode: String) =
            ctx.startActivity(Intent(ctx, TerminalActivity::class.java)
                .putExtra("PATH", path).putExtra("MODE", mode))
    }
}
