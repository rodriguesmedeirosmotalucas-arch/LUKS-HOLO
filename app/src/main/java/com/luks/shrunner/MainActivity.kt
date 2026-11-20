class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnCarregar).setOnClickListener {
            // TODO: abrir seletor de arquivo .sh e carregar
        }
    }
}
