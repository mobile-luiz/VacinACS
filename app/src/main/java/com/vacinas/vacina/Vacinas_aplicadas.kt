package com.vacinas.vacina

import android.content.Intent
import android.graphics.Color // NOVO: Necessário para cores
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
import java.io.IOException // NOVO: Melhor tratamento de exceções
import java.util.Locale
import kotlin.math.min

// 🚨 CORREÇÃO 1: Implementar a interface VacinaAdapter.VacinaItemClickListener
class Vacinas_aplicadas : AppCompatActivity(), VacinaAdapter.VacinaItemClickListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var vacinaAdapter: VacinaAdapter
    private lateinit var exportPdfButton: FloatingActionButton
    private lateinit var editSearchFilter: EditText

    private lateinit var vacinaRepository: VacinaRepository
    // 🔑 Instância do DAO para buscar os detalhes do paciente
    private lateinit var individuoDao: IndividuoDao

    private lateinit var auth: FirebaseAuth
    private var currentAcsUid: String? = null
    // ⚠️ TAG ajustada
    private val TAG = "VacinasAplicadasActivity"

    // VARIÁVEIS DE LISTA: Armazena TODAS as doses (Aplicadas)
    private var completeDoseList = mutableListOf<VacinaDose>()
    private var currentDisplayedList = mutableListOf<VacinaDose>()

    // VARIÁVEIS DE PAGINAÇÃO
    private var currentPage = 0
    private val ITEMS_PER_PAGE = 20
    private var loadMoreButton: Button? = null

    private var isSearching = false

    // Conjunto de status que indicam que a dose foi concluída
    private val APPLIED_STATUSES = setOf("APLICADA", "APLICADO", "CONCLUIDA", "FINALIZADA")


    private val pdfLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                // ✅ CHAMADA CORRIGIDA: Função suspend deve ser chamada dentro de um coroutine
                lifecycleScope.launch {
                    generatePdf(uri)
                }
            }
        } else {
            Toast.makeText(this, "Exportação para PDF cancelada.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        currentAcsUid = auth.currentUser?.uid

        // --- VERIFICAÇÃO CRÍTICA DE LOGIN ---
        if (currentAcsUid.isNullOrEmpty()) {
            Toast.makeText(this, "Usuário não logado. A lista de vacinas está indisponível.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "UID do ACS não encontrado. Fechando lista.")
            finish()
            return
        }
        // ------------------------------------

        setContentView(R.layout.activity_agenda_vacinas)

        // 🔑 Inicialização dos Repositórios/DAOs
        vacinaRepository = VacinaRepository(application)
        // 🔑 Inicializa o DAO do Indivíduo
        individuoDao = IndividuoDao(applicationContext)

        initializeViews()
        fetchDosesFromRoom()
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
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar_main)
        setSupportActionBar(toolbar)
        // 🚨 CORREÇÃO 3: Título atualizado para refletir o conteúdo
        supportActionBar?.title = "Histórico de Vacinas Aplicadas"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    private fun setupRecyclerView() {
        // ✅ CORREÇÃO 1: 'this' agora é válido pois a classe implementa a interface
        vacinaAdapter = VacinaAdapter(emptyList(), this, showPatientName = true)

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@Vacinas_aplicadas)
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
                    loadFirstPage() // Volta para a primeira página paginada da lista completa
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

    // ----------------------------------------------------
    // LÓGICA PRINCIPAL: FILTRAGEM E ORDENAÇÃO (APENAS APLICADAS)
    // ----------------------------------------------------

    /**
     * 🚨 AJUSTADO: Filtra e ordena APENAS as doses aplicadas por data de aplicação decrescente (mais recente primeiro).
     */
    private fun sortAllDoses(rawList: List<VacinaDose>): List<VacinaDose> {

        // 🚨 Filtra apenas as doses aplicadas/concluídas
        val appliedDoses = rawList.filter { dose ->
            dose.status.uppercase() in APPLIED_STATUSES
        }.sortedWith(compareByDescending<VacinaDose> { dose ->
            // Ordena APLICADAS por data de aplicação decrescente (mais recente primeiro)
            dose.dataAplicacao ?: "00/00/0000"
        }.thenBy { it.pacienteNome })

        // Retorna apenas a lista de Aplicadas
        return appliedDoses
    }


    /**
     * Busca APENAS as doses aplicadas do banco de dados Room de forma assíncrona,
     * ordena-as e exibe a primeira página.
     */
    private fun fetchDosesFromRoom() {
        lifecycleScope.launch {
            try {
                // Busca os dados no Room (DAO) em uma thread de I/O.
                val dosesFromDb = withContext(Dispatchers.IO) {
                    val acsId = currentAcsUid
                    if (acsId.isNullOrEmpty()) return@withContext emptyList<VacinaDose>()
                    // O repositório deve buscar TODAS as doses e a função sortAllDoses filtra
                    vacinaRepository.getDosesByAcs(acsId)
                }

                if (dosesFromDb.isNotEmpty()) {
                    // Ordena e filtra a lista completa para exibição (APENAS APLICADAS)
                    completeDoseList = sortAllDoses(dosesFromDb).toMutableList()

                    Log.i(TAG, "✅ Carregamento COMPLETO. ${completeDoseList.size} doses (APLICADAS).")
                    loadFirstPage()
                } else {
                    Log.w(TAG, "Nenhuma dose de vacina aplicada encontrada para o ACS UID: $currentAcsUid.")
                    Toast.makeText(this@Vacinas_aplicadas, "Nenhuma dose de vacina aplicada registrada.", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao carregar doses do Room: ${e.message}", e)
                Toast.makeText(this@Vacinas_aplicadas, "Erro ao carregar dados locais.", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Carrega a primeira página de dados (ou a lista filtrada se a busca estiver ativa).
     */
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
        Log.d(TAG, "PAGINATION: Carregada página 1. Itens: ${firstPage.size}")
    }

    /**
     * Carrega a próxima página de dados.
     */
    private fun loadNextPage() {
        if (isSearching) return

        val nextOffset = (currentPage + 1) * ITEMS_PER_PAGE
        val listSize = completeDoseList.size

        if (nextOffset < listSize) {
            val nextPage = completeDoseList.subList(nextOffset, minOf(nextOffset + ITEMS_PER_PAGE, listSize))
            vacinaAdapter.appendList(nextPage)
            currentDisplayedList.addAll(nextPage)
            currentPage++

            updateLoadMoreButton(nextPage.size)
            Log.d(TAG, "PAGINATION: Carregada página ${currentPage + 1}. Offset: $nextOffset, Itens: ${nextPage.size}")
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
            button.text = "Carregar Mais Doses (${minOf(ITEMS_PER_PAGE, remaining)})"
        } else {
            button.visibility = View.GONE
        }
    }

    // ----------------------------------------------------
    // FUNÇÃO DE FILTRAGEM LOCAL
    // ----------------------------------------------------

    private fun filterList(query: String) {
        val lowerCaseQuery = query.lowercase(Locale.getDefault())

        val filteredList = if (lowerCaseQuery.isEmpty()) {
            completeDoseList.take(ITEMS_PER_PAGE)
        } else {
            // A busca é feita na lista COMPLETA (aplicadas)
            completeDoseList.filter { dose ->
                val nomeMatch = dose.pacienteNome.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
                val vacinaMatch = dose.nomeVacina.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
                val statusMatch = dose.status.lowercase(Locale.getDefault()).contains(lowerCaseQuery)

                nomeMatch || vacinaMatch || statusMatch
            }
        }

        currentDisplayedList = filteredList.toMutableList()
        vacinaAdapter.updateList(filteredList)
        updateLoadMoreButton(filteredList.size)

        if (isSearching && filteredList.isEmpty()) {
            Toast.makeText(this, "Nenhuma dose encontrada com o critério de busca.", Toast.LENGTH_SHORT).show()
        }

        Log.d(TAG, "SEARCH: Filtro aplicado. Resultados: ${filteredList.size}")
    }

    // ----------------------------------------------------
    // LÓGICA DE EXPORTAÇÃO/DOWNLOAD (PDF)
    // ----------------------------------------------------

    private fun exportToPdf() {
        if (currentDisplayedList.isEmpty()) {
            Toast.makeText(this, "Nenhuma dose para exportar.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
                // Nome do arquivo ajustado
                putExtra(Intent.EXTRA_TITLE, "VACINACS_VacinasAplicadas_${System.currentTimeMillis()}.pdf")
            }
            pdfLauncher.launch(intent)

        } catch (e: Exception) {
            Log.e(TAG, "PDF_EXPORT: Erro ao iniciar Intent de criação de documento: ${e.message}", e)
            Toast.makeText(this, "Não foi possível iniciar a criação do arquivo.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 🚨 FUNÇÃO REESCRITA: Gera um PDF profissional e assíncrono.
     */
    private suspend fun generatePdf(uri: Uri) {
        val listToExport = currentDisplayedList
        val pdfDocument = PdfDocument()

        // Configuração do documento (A4 portrait: 595x842 pts)
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()

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

        val appliedPaint = Paint().apply {
            textSize = 12f
            isFakeBoldText = true
            color = Color.parseColor("#006A60") // Dark Teal/Green para status de sucesso (APLICADA)
        }

        val linePaint = Paint().apply {
            strokeWidth = 1f
            color = Color.LTGRAY // Linha cinza clara
        }

        // --- DEFINIÇÃO DE CONSTANTES DE LAYOUT ---
        val margin = 40f
        val lineHeight = 25f
        val headerLineHeight = 35f

        // Posições de Colunas
        val xPosDose = margin
        val xPosVacina = margin + 50f
        val xPosData = margin + 350f
        val xPosStatus = margin + 460f

        // 🔑 Largura máxima disponível para o Nome da Vacina
        val maxVacinaWidth = xPosData - xPosVacina - 10f

        try {
            withContext(Dispatchers.Default) {
                var yPos = margin + headerLineHeight

                var page = pdfDocument.startPage(pageInfo)
                var canvas = page.canvas

                // Agrupa por paciente para criar blocos
                val dosesByPatient = listToExport.groupBy {
                    it.pacienteNome.ifEmpty { "Paciente Desconhecido" }
                }

                // 1. TÍTULO PRINCIPAL (Aparece em todas as páginas)
                canvas.drawText("VACINACS - Histórico de Vacinas Aplicadas", margin, margin + 15f, titlePaint)

                // 2. LINHA SEPARADORA DO TÍTULO
                canvas.drawLine(margin, margin + 25f, 595f - margin, margin + 25f, linePaint.apply { strokeWidth = 2f }) // Linha mais grossa
                yPos += lineHeight * 0.5f


                dosesByPatient.forEach { (pacienteNome, doses) ->
                    // --- QUEBRA DE PÁGINA (CHECK) ---
                    if (yPos > 780) {
                        pdfDocument.finishPage(page)
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        yPos = margin + headerLineHeight

                        // Desenha título e linha novamente na nova página
                        canvas.drawText("VACINACS - Histórico (Continuação)", margin, margin + 15f, titlePaint)
                        canvas.drawLine(margin, margin + 25f, 595f - margin, margin + 25f, linePaint.apply { strokeWidth = 2f })
                    }

                    // 3. CABEÇALHO DO PACIENTE
                    canvas.drawText("Paciente: $pacienteNome", xPosDose, yPos, patientHeaderPaint)
                    yPos += lineHeight * 0.8f

                    // 4. LINHA SEPARADORA DO PACIENTE
                    canvas.drawLine(margin, yPos, 595f - margin, yPos, linePaint.apply { strokeWidth = 1f })
                    yPos += lineHeight * 1.5f

                    // 5. CABEÇALHO DA TABELA DE DOSES
                    canvas.drawText("Dose", xPosDose, yPos, patientHeaderPaint)
                    canvas.drawText("Vacina / Estratégia", xPosVacina, yPos, patientHeaderPaint)
                    canvas.drawText("Data de Aplicação", xPosData, yPos, patientHeaderPaint)
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

                            // Redesenha cabeçalhos na nova página
                            canvas.drawText("VACINACS - Histórico (Continuação)", margin, margin + 15f, titlePaint)
                            canvas.drawLine(margin, margin + 25f, 595f - margin, margin + 25f, linePaint.apply { strokeWidth = 2f })

                            canvas.drawText("Paciente: $pacienteNome (continuação)", xPosDose, yPos, patientHeaderPaint)
                            yPos += lineHeight * 0.8f
                            canvas.drawLine(margin, yPos, 595f - margin, yPos, linePaint.apply { strokeWidth = 1f })
                            yPos += lineHeight * 1.5f

                            canvas.drawText("Dose", xPosDose, yPos, patientHeaderPaint)
                            canvas.drawText("Vacina / Estratégia", xPosVacina, yPos, patientHeaderPaint)
                            canvas.drawText("Data de Aplicação", xPosData, yPos, patientHeaderPaint)
                            canvas.drawText("Status", xPosStatus, yPos, patientHeaderPaint)
                            yPos += lineHeight * 0.5f
                            canvas.drawLine(margin, yPos, 595f - margin, yPos, linePaint.apply { strokeWidth = 0.5f })
                            yPos += lineHeight
                        }

                        // 6. DETALHES DA DOSE EM COLUNAS
                        val dataAplicacaoDisplay = dose.dataAplicacao?.take(10) ?: "N/D"
                        val statusDisplay = dose.status?.uppercase(Locale.ROOT) ?: "N/D"

                        // LÓGICA DE TRUNCAMENTO PARA EVITAR SOBREPOSIÇÃO
                        var nomeVacinaDisplay = dose.nomeVacina.ifEmpty { "Vacina Desconhecida" }
                        if (detailPaint.measureText(nomeVacinaDisplay) > maxVacinaWidth) {
                            // Trunca a string para caber na largura disponível
                            val numChars = detailPaint.breakText(nomeVacinaDisplay, true, maxVacinaWidth, null)
                            nomeVacinaDisplay = nomeVacinaDisplay.substring(0, numChars) + "..."
                        }

                        // Usa a cor verde/teal para o status "APLICADA"
                        val currentStatusPaint = if (statusDisplay == "APLICADA") appliedPaint else detailPaint

                        canvas.drawText(dose.dose, xPosDose, yPos, detailPaint)
                        canvas.drawText(nomeVacinaDisplay, xPosVacina, yPos, detailPaint) // Truncado se necessário
                        canvas.drawText(dataAplicacaoDisplay, xPosData, yPos, detailPaint)
                        canvas.drawText(statusDisplay, xPosStatus, yPos, currentStatusPaint)

                        yPos += lineHeight
                    }

                    yPos += lineHeight // Espaço extra entre pacientes
                }

                pdfDocument.finishPage(page)

            } // Fim de withContext(Dispatchers.Default)

            // 7. Gravação do PDF no Disco
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }

            // ✅ CORREÇÃO DE CONTEXTO
            withContext(Dispatchers.Main) {
                Toast.makeText(this@Vacinas_aplicadas, "✅ PDF salvo com sucesso! (${listToExport.size} doses)", Toast.LENGTH_LONG).show()
            }

        } catch (e: IOException) {
            // Tratamento específico de I/O
            Log.e(TAG, "PDF_EXPORT: Erro de I/O ao salvar PDF: ${e.message}", e)
            // ✅ CORREÇÃO DE CONTEXTO
            withContext(Dispatchers.Main) {
                Toast.makeText(this@Vacinas_aplicadas, "Erro de I/O ao salvar PDF.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            // Tratamento genérico
            Log.e(TAG, "PDF_EXPORT: Erro ao gerar ou salvar PDF: ${e.message}", e)
            // ✅ CORREÇÃO DE CONTEXTO
            withContext(Dispatchers.Main) {
                Toast.makeText(this@Vacinas_aplicadas, "Erro ao salvar PDF: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } finally {
            pdfDocument.close()
        }
    }

    // ----------------------------------------------------
    // LÓGICA DE CLIQUE: NAVEGAÇÃO PARA REGISTRO VACINAL (Com Busca de Detalhes)
    // ----------------------------------------------------

    // ✅ CORREÇÃO 2: 'onVacinaItemClick' agora é um override válido
    override fun onVacinaItemClick(dose: VacinaDose) {
        Log.d(TAG, "CLICK: Item clicado - Paciente: ${dose.pacienteNome} | Vacina: ${dose.nomeVacina} | CNS: ${dose.cnsIndividuo}")

        // Exibe um Toast enquanto carrega, para dar feedback ao usuário
        Toast.makeText(this, "Carregando dados de ${dose.pacienteNome}...", Toast.LENGTH_SHORT).show()

        // Inicia a busca assíncrona para buscar os detalhes completos do Indivíduo
        lifecycleScope.launch {

            val individuoCompleto = withContext(Dispatchers.IO) {
                // Usa o CNS da dose para buscar o registro completo do paciente (Indivíduo)
                individuoDao.findByCns(dose.cnsIndividuo)
            }

            if (individuoCompleto != null) {
                // Se o objeto Indivíduo completo for encontrado, navega com todos os dados
                val intent = Intent(this@Vacinas_aplicadas, RegistroVacinalActivity::class.java).apply {
                    // Passa todos os dados do objeto Indivíduo completo
                    putExtra("CNS_PACIENTE", individuoCompleto.cns)
                    putExtra("NOME_PACIENTE", individuoCompleto.nome)
                    // ✅ Dados recuperados do Indivíduo completo
                    putExtra("DATA_NASCIMENTO_PACIENTE", individuoCompleto.dataNascimento)
                    putExtra("ENDERECO_PACIENTE", individuoCompleto.endereco)
                    putExtra("EMAIL_PACIENTE", individuoCompleto.email)
                }
                startActivity(intent)
            } else {
                // Feedback de erro caso não encontre o paciente
                Toast.makeText(this@Vacinas_aplicadas, "Erro: Detalhes completos do paciente não encontrados.", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Falha ao carregar detalhes do Indivíduo para o CNS: ${dose.cnsIndividuo}")
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}