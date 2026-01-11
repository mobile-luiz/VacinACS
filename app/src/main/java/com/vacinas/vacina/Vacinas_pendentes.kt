package com.vacinas.vacina

import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.vacinas.vacina.data.IndividuoDao
import com.vacinas.vacina.ui.vacinas.adapter.VacinaAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*
import kotlin.math.min
import android.graphics.Color // Import necessário para cores
import java.util.Locale // Já presente no seu código
// ... outros imports

class Vacinas_pendentes : AppCompatActivity(), VacinaAdapter.VacinaItemClickListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var vacinaAdapter: VacinaAdapter
    private lateinit var exportPdfButton: FloatingActionButton
    private lateinit var editSearchFilter: EditText
    private var loadMoreButton: Button? = null

    private lateinit var vacinaRepository: VacinaRepository
    private lateinit var individuoDao: IndividuoDao

    private lateinit var auth: FirebaseAuth
    private var currentAcsUid: String? = null
    private val TAG = "VacinasPendentesActivity"

    private var completeDoseList = mutableListOf<VacinaDose>()
    private var currentDisplayedList = mutableListOf<VacinaDose>()

    private var currentPage = 0
    private val ITEMS_PER_PAGE = 20
    private var isSearching = false

    private val PENDING_STATUSES = setOf("AGENDADA", "AGENDADO", "A_FAZER", "PENDENTE")


    private val pdfLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // LOG 1: Resultado da seleção de arquivo
        Log.d(TAG, "PDF_LAUNCHER: resultCode: ${result.resultCode}")

        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.i(TAG, "PDF_LAUNCHER: URI de destino recebida. URI: $uri")

                // Adiciona a FLAG de permissão de escrita para máxima compatibilidade
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                lifecycleScope.launch {
                    generatePdf(uri)
                }
            } ?: Log.e(TAG, "❌ PDF_LAUNCHER_ERROR: Result OK, mas data ou URI é nula.")
        } else {
            Toast.makeText(this, "Exportação para PDF cancelada.", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "PDF_LAUNCHER: Exportação cancelada ou falha na seleção de arquivo.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        currentAcsUid = auth.currentUser?.uid

        if (currentAcsUid.isNullOrEmpty()) {
            Toast.makeText(this, "Usuário não logado. A lista de vacinas está indisponível.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "❌ ERRO CRÍTICO: UID do ACS não encontrado. Fechando lista.")
            finish()
            return
        }

        setContentView(R.layout.activity_agenda_vacinas)

        vacinaRepository = VacinaRepository(application)
        individuoDao = IndividuoDao(applicationContext)

        initializeViews()
        fetchDosesFromRoom()
    }

    /**
     * Lógica CORRIGIDA: Não limpa a lista se estiver voltando do seletor de PDF.
     * Garante que os dados em memória sejam usados para a exportação.
     */
    override fun onRestart() {
        super.onRestart()
        Log.d(TAG, "ON_RESTART: Activity voltando ao foreground. Verificando dados.")

        // 1. Garante que o botão de exportação esteja habilitado.
        exportPdfButton.isEnabled = true

        // 2. Recarrega os dados SOMENTE se a lista estiver vazia (ex: se a Activity foi destruída).
        // Se completeDoseList NÃO estiver vazia, significa que os dados estão prontos para 'generatePdf'.
        if (completeDoseList.isEmpty()) {
            Log.d(TAG, "ON_RESTART: Lista vazia. Forçando recarga assíncrona.")

            // Limpa o estado visual antes de recarregar
            currentDisplayedList.clear()
            vacinaAdapter.updateList(emptyList())
            currentPage = 0
            loadMoreButton?.visibility = View.GONE

            fetchDosesFromRoom()
        }
    }


    private fun initializeViews() {
        setupToolbar()
        recyclerView = findViewById(R.id.recycler_individuos)
        exportPdfButton = findViewById(R.id.fab_export_pdf)
        editSearchFilter = findViewById(R.id.edit_search_filter)
        loadMoreButton = findViewById(R.id.button_load_more)

        setupRecyclerView()
        setupPdfButton()
        setupSearchFilter()
        setupLoadMoreListener()

        // Garante que o botão está desabilitado no início, evitando cliques prematuros
        exportPdfButton.isEnabled = false
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar_main)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Agenda de Vacinas (Pendentes)"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupRecyclerView() {
        vacinaAdapter = VacinaAdapter(emptyList(), this, showPatientName = true)

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@Vacinas_pendentes)
            adapter = vacinaAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupPdfButton() {
        exportPdfButton.setOnClickListener {
            exportToPdf()
        }
    }

    private fun setupSearchFilter() {
        editSearchFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                isSearching = query.isNotBlank()
                if (isSearching) {
                    filterList(query)
                } else {
                    loadFirstPage()
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupLoadMoreListener() {
        loadMoreButton?.setOnClickListener {
            loadNextPage()
        }
    }

    private fun sortAllDoses(rawList: List<VacinaDose>): List<VacinaDose> {
        // Filtro para pendentes/agendadas
        return rawList.filter { dose ->
            val status = dose.status?.uppercase(Locale.ROOT) ?: ""
            status in PENDING_STATUSES
        }.sortedWith(compareBy<VacinaDose> { dose ->
            // Ordena as pendentes pela data agendada
            if (dose.dataAgendada.isNullOrEmpty()) "99/99/9999" else dose.dataAgendada
        }.thenBy { it.pacienteNome })
    }

    private fun fetchDosesFromRoom() {
        lifecycleScope.launch {
            try {
                val dosesFromDb = withContext(Dispatchers.IO) {
                    val acsId = currentAcsUid
                    if (acsId.isNullOrEmpty()) return@withContext emptyList<VacinaDose>()
                    vacinaRepository.getDosesByAcs(acsId)
                }

                if (dosesFromDb.isNotEmpty()) {
                    completeDoseList = sortAllDoses(dosesFromDb).toMutableList()

                    if (completeDoseList.isEmpty()) {
                        Toast.makeText(this@Vacinas_pendentes, "Nenhuma dose de vacina pendente encontrada.", Toast.LENGTH_LONG).show()
                    }

                    loadFirstPage()
                } else {
                    Toast.makeText(this@Vacinas_pendentes, "Nenhuma dose de vacina pendente registrada.", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ DATA_LOAD_ERROR: Erro ao carregar doses do Room: ${e.message}", e)
                Toast.makeText(this@Vacinas_pendentes, "Erro ao carregar dados locais.", Toast.LENGTH_LONG).show()
            } finally {
                // Habilita o botão APÓS o carregamento (sucesso ou falha).
                withContext(Dispatchers.Main) {
                    exportPdfButton.isEnabled = true
                }
            }
        }
    }

    private fun loadFirstPage() {
        currentPage = 0
        if (isSearching) {
            filterList(editSearchFilter.text.toString())
            return
        }

        val firstPage = completeDoseList.take(ITEMS_PER_PAGE)
        currentDisplayedList = firstPage.toMutableList()
        vacinaAdapter.updateList(firstPage)

        updateLoadMoreButton(firstPage.size)
    }

    private fun loadNextPage() {
        if (isSearching) return

        val nextOffset = (currentPage + 1) * ITEMS_PER_PAGE
        val listSize = completeDoseList.size

        if (nextOffset < listSize) {
            val nextPage = completeDoseList.subList(nextOffset, min(nextOffset + ITEMS_PER_PAGE, listSize))
            vacinaAdapter.appendList(nextPage)
            currentDisplayedList.addAll(nextPage)
            currentPage++

            updateLoadMoreButton(nextPage.size)
        } else {
            Toast.makeText(this, "Fim da lista de doses.", Toast.LENGTH_SHORT).show()
            loadMoreButton?.visibility = View.GONE
        }
    }

    private fun updateLoadMoreButton(lastLoadedCount: Int) {
        val button = loadMoreButton ?: return

        val totalAvailable = completeDoseList.size
        val currentlyDisplayed = currentDisplayedList.size

        if (isSearching) {
            button.visibility = View.GONE
        } else if (currentlyDisplayed < totalAvailable) {
            button.visibility = View.VISIBLE
            val remaining = totalAvailable - currentlyDisplayed
            button.text = "Carregar Mais Doses (${min(ITEMS_PER_PAGE, remaining)})"
        } else {
            button.visibility = View.GONE
        }
    }

    private fun filterList(query: String) {
        val lowerCaseQuery = query.lowercase(Locale.getDefault())

        val filteredList = if (lowerCaseQuery.isEmpty()) {
            completeDoseList.take(ITEMS_PER_PAGE)
        } else {
            completeDoseList.filter { dose ->
                val nameMatch = dose.pacienteNome.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
                val vacinaMatch = dose.nomeVacina.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
                val statusMatch = dose.status?.lowercase(Locale.getDefault())?.contains(lowerCaseQuery) ?: false

                nameMatch || vacinaMatch || statusMatch
            }
        }

        currentDisplayedList = filteredList.toMutableList()
        vacinaAdapter.updateList(filteredList)
        updateLoadMoreButton(filteredList.size)

        if (isSearching && filteredList.isEmpty()) {
            Toast.makeText(this, "Nenhuma dose encontrada com o critério de busca.", Toast.LENGTH_SHORT).show()
        }
    }

    // ----------------------------------------------------------------------------------
    // LÓGICA DE EXPORTAÇÃO PDF
    // ----------------------------------------------------------------------------------

    private fun exportToPdf() {
        Log.d(TAG, "PDF_EXPORT: Botão clicado. Verificando listas...")

        if (completeDoseList.isEmpty() && currentDisplayedList.isEmpty()) {
            Log.w(TAG, "PDF_EXPORT: Abortado. Nenhuma dose para exportar.")
            Toast.makeText(this, "Nenhuma dose pendente para exportar.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
                putExtra(Intent.EXTRA_TITLE, "VACINACS_AgendaPendentes_${System.currentTimeMillis()}.pdf")
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            Log.d(TAG, "PDF_EXPORT: Iniciando Activity para selecionar destino.")
            pdfLauncher.launch(intent)

        } catch (e: Exception) {
            Log.e(TAG, "❌ PDF_EXPORT_ERROR: Falha ao iniciar Intent de criação de documento: ${e.message}", e)
            Toast.makeText(this, "Não foi possível iniciar a criação do arquivo.", Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun generatePdf(uri: Uri) {
        Log.d(TAG, "PDF_GENERATE: Iniciando geração para URI: $uri")

        // ... (Lógica de seleção de lista mantida) ...
        val listToExport = when {
            isSearching -> currentDisplayedList
            completeDoseList.isNotEmpty() -> completeDoseList
            else -> currentDisplayedList
        }

        Log.i(TAG, "PDF_GENERATE: Lista final possui ${listToExport.size} doses.")

        if (listToExport.isEmpty()) {
            Log.w(TAG, "PDF_GENERATE: Lista vazia após seleção. Abortando.")
            withContext(Dispatchers.Main) {
                Toast.makeText(this@Vacinas_pendentes, "Nenhuma dose para exportar no momento.", Toast.LENGTH_LONG).show()
            }
            return
        }

        val pdfDocument = PdfDocument()

        try {
            withContext(Dispatchers.Default) {
                Log.d(TAG, "PDF_GENERATE: Início do desenho (Dispatcher.Default).")
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 portrait: 595x842 pts

                // --- DEFINIÇÃO DE ESTILOS (PAINTS) ---
                val titlePaint = Paint().apply {
                    textSize = 24f
                    isFakeBoldText = true
                    color = Color.BLACK
                }

                val patientHeaderPaint = Paint().apply {
                    textSize = 16f
                    isFakeBoldText = true
                    color = Color.DKGRAY
                }

                val detailPaint = Paint().apply {
                    textSize = 12f
                    isFakeBoldText = false
                    color = Color.BLACK
                }

                val linePaint = Paint().apply {
                    strokeWidth = 1f
                    color = Color.GRAY
                }

                val pendingPaint = Paint().apply {
                    textSize = 12f
                    isFakeBoldText = true
                    color = Color.RED // Destaque para Status PENDENTE
                }

                // --- DEFINIÇÃO DE CONSTANTES DE LAYOUT ---
                val margin = 40f
                val contentWidth = 595f - 2 * margin // 515f
                val lineHeight = 25f
                val headerLineHeight = 35f

                // Posições de Colunas para os Detalhes das Doses
                val xPosDose = margin
                val xPosVacina = margin + 50f
                val xPosData = margin + 350f // Ajuste de posição para a data
                val xPosStatus = margin + 460f // Ajuste de posição para o status

                var yPos = margin + headerLineHeight

                var page = pdfDocument.startPage(pageInfo)
                var canvas = page.canvas

                val dosesByPatient = listToExport.groupBy {
                    it.pacienteNome.ifEmpty {
                        Log.w(TAG, "PDF_GENERATE: Paciente com nome vazio (CNS: ${it.cnsIndividuo}).")
                        "Paciente Desconhecido (CNS: ${it.cnsIndividuo})"
                    }
                }

                // 1. TÍTULO PRINCIPAL (Aparece em todas as páginas)
                canvas.drawText("VACINACS - Agenda de Vacinas (Pendentes)", margin, margin + 15f, titlePaint)

                // 2. LINHA SEPARADORA DO TÍTULO
                canvas.drawLine(margin, margin + 25f, 595f - margin, margin + 25f, linePaint)


                dosesByPatient.forEach { (pacienteNome, doses) ->
                    // --- QUEBRA DE PÁGINA (CHECK) ---
                    if (yPos > 780) {
                        pdfDocument.finishPage(page)
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        yPos = margin + headerLineHeight

                        // Desenha título e linha novamente na nova página
                        canvas.drawText("VACINACS - Agenda (Continuação)", margin, margin + 15f, titlePaint)
                        canvas.drawLine(margin, margin + 25f, 595f - margin, margin + 25f, linePaint)
                    }

                    // 3. CABEÇALHO DO PACIENTE
                    canvas.drawText("Paciente: $pacienteNome", xPosDose, yPos, patientHeaderPaint)
                    yPos += lineHeight * 0.8f // Menor espaçamento após o nome

                    // 4. LINHA SEPARADORA DO PACIENTE
                    canvas.drawLine(margin, yPos, 595f - margin, yPos, linePaint)
                    yPos += lineHeight * 1.5f // Espaçamento maior antes da lista de doses

                    // 5. CABEÇALHO DA TABELA DE DOSES
                    canvas.drawText("Dose", xPosDose, yPos, patientHeaderPaint)
                    canvas.drawText("Vacina / Estratégia", xPosVacina, yPos, patientHeaderPaint)
                    canvas.drawText("Agendado para", xPosData, yPos, patientHeaderPaint)
                    canvas.drawText("Status", xPosStatus, yPos, patientHeaderPaint)
                    yPos += lineHeight * 0.5f
                    canvas.drawLine(margin, yPos, 595f - margin, yPos, linePaint.apply { strokeWidth = 0.5f }) // Linha mais fina
                    yPos += lineHeight

                    doses.forEach { dose ->
                        // --- QUEBRA DE PÁGINA (CHECK DE DOSE) ---
                        if (yPos > 800) {
                            pdfDocument.finishPage(page)
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            yPos = margin + headerLineHeight

                            // Desenha título e linha novamente
                            canvas.drawText("VACINACS - Agenda (Continuação)", margin, margin + 15f, titlePaint)
                            canvas.drawLine(margin, margin + 25f, 595f - margin, margin + 25f, linePaint)

                            // Cabeçalho de paciente em continuação
                            canvas.drawText("Paciente: $pacienteNome (continuação)", xPosDose, yPos, patientHeaderPaint)
                            yPos += lineHeight * 0.8f
                            canvas.drawLine(margin, yPos, 595f - margin, yPos, linePaint)
                            yPos += lineHeight * 1.5f

                            // Cabeçalho da tabela
                            canvas.drawText("Dose", xPosDose, yPos, patientHeaderPaint)
                            canvas.drawText("Vacina / Estratégia", xPosVacina, yPos, patientHeaderPaint)
                            canvas.drawText("Agendado para", xPosData, yPos, patientHeaderPaint)
                            canvas.drawText("Status", xPosStatus, yPos, patientHeaderPaint)
                            yPos += lineHeight * 0.5f
                            canvas.drawLine(margin, yPos, 595f - margin, yPos, linePaint.apply { strokeWidth = 0.5f })
                            yPos += lineHeight
                        }

                        // 6. DETALHES DA DOSE EM COLUNAS
                        val dataAgendadaDisplay = dose.dataAgendada?.take(10) ?: "N/D"
                        val statusDisplay = dose.status?.uppercase(Locale.ROOT) ?: "PENDENTE"
                        val nomeVacinaDisplay = dose.nomeVacina.ifEmpty { "Vacina Desconhecida" }

                        // Coluna 1: Número da Dose
                        canvas.drawText(dose.dose, xPosDose, yPos, detailPaint)

                        // Coluna 2: Nome da Vacina
                        canvas.drawText(nomeVacinaDisplay, xPosVacina, yPos, detailPaint)

                        // Coluna 3: Data Agendada
                        canvas.drawText(dataAgendadaDisplay, xPosData, yPos, detailPaint)

                        // Coluna 4: Status (com destaque em vermelho se PENDENTE)
                        val currentStatusPaint = if (statusDisplay == "AGENDADA" || statusDisplay == "PENDENTE") pendingPaint else detailPaint
                        canvas.drawText(statusDisplay, xPosStatus, yPos, currentStatusPaint)

                        yPos += lineHeight // Próxima linha
                    }

                    yPos += lineHeight // Espaço extra entre pacientes
                }

                pdfDocument.finishPage(page)
                Log.d(TAG, "PDF_GENERATE: Desenho concluído. Tentando escrever no OutputStream.")
            } // Fim de withContext(Dispatchers.Default)

            // ... (Bloco de Gravação do PDF no Disco e Tratamento de Erros mantido) ...
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                Log.d(TAG, "PDF_WRITE: OutputStream aberto com sucesso.")
                pdfDocument.writeTo(outputStream)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@Vacinas_pendentes, "✅ PDF salvo com sucesso! (${listToExport.size} doses)", Toast.LENGTH_LONG).show()
                }
                Log.i(TAG, "PDF_WRITE: Escrita finalizada. Sucesso.")
            } ?: run {
                Log.e(TAG, "❌ PDF_WRITE_ERROR: Falha ao abrir OutputStream para a URI. Permissão negada ou URI inválida.")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@Vacinas_pendentes, "Erro: Não foi possível abrir o fluxo de saída. Permissão negada ou URI inválida.", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "❌ PDF_WRITE_IO_ERROR: Falha ao escrever ou fechar o PDF: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@Vacinas_pendentes, "Erro de I/O ao salvar PDF. Verifique o Logcat para detalhes.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ PDF_GENERATE_GENERAL_ERROR: Falha durante a geração do conteúdo ou escrita: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@Vacinas_pendentes, "Erro inesperado: Falha ao gerar o conteúdo do PDF.", Toast.LENGTH_LONG).show()
            }
        } finally {
            pdfDocument.close()
            Log.d(TAG, "PDF_GENERATE: PdfDocument fechado.")
        }
    }


    override fun onVacinaItemClick(dose: VacinaDose) {
        Toast.makeText(this, "Carregando dados de ${dose.pacienteNome}...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val individuoCompleto = withContext(Dispatchers.IO) {
                individuoDao.findByCns(dose.cnsIndividuo)
            }

            if (individuoCompleto != null) {
                val intent = Intent(this@Vacinas_pendentes, RegistroVacinalActivity::class.java).apply {
                    putExtra("CNS_PACIENTE", individuoCompleto.cns)
                    putExtra("NOME_PACIENTE", individuoCompleto.nome)
                    putExtra("DATA_NASCIMENTO_PACIENTE", individuoCompleto.dataNascimento)
                    putExtra("ENDERECO_PACIENTE", individuoCompleto.endereco)
                    putExtra("EMAIL_PACIENTE", individuoCompleto.email)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this@Vacinas_pendentes, "Erro: Detalhes completos do paciente não encontrados.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}