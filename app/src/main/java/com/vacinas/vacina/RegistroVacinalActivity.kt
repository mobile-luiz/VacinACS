package com.vacinas.vacina

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.vacinas.vacina.ui.vacinas.VacinasListFragmento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
// ✅ ADICIONADAS importações necessárias
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
// ⚠️ Nota: A classe 'Individuo' e 'VacinaDose' são assumidas como existentes.

class RegistroVacinalActivity : AppCompatActivity(), VacinasListFragmento.DataPassListener {

    private lateinit var nomePaciente: String
    private lateinit var cnsPaciente: String
    private lateinit var dataNascimentoPaciente: String
    private lateinit var enderecoPaciente: String
    private lateinit var emailPaciente: String

    private val TAG = "RegistroVacinalActivity"

    // Referências de UI (Assumindo que tvDetIdade agora existe no XML)
    private lateinit var btnAte12Meses: Button
    private lateinit var btnApos12Meses: Button
    private lateinit var btnOutrasVacinas: Button
    private lateinit var fabExportarPdf: FloatingActionButton
    private lateinit var textQtdVacinasTomadas: TextView
    private lateinit var textQtdVacinasAgendadas: TextView
    private lateinit var tvDetIdade: TextView // Deve funcionar após correção do XML

    // Lista de doses para a exportação
    private var allMergedDoses: List<VacinaDose> = emptyList()

    // Activity Result Launcher para salvar o PDF
    private val pdfResultLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            exportPdf(uri, allMergedDoses)
        } else {
            Toast.makeText(this, "Exportação de PDF cancelada.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registro_vacinal)

        // 1. Receber e armazenar os dados do Intent
        nomePaciente = intent.getStringExtra("NOME_PACIENTE") ?: "Não Informado"
        dataNascimentoPaciente = intent.getStringExtra("DATA_NASCIMENTO_PACIENTE") ?: "Não Informada"
        enderecoPaciente = intent.getStringExtra("ENDERECO_PACIENTE") ?: "Não Informado"
        cnsPaciente = intent.getStringExtra("CNS_PACIENTE") ?: ""
        emailPaciente = intent.getStringExtra("EMAIL_PACIENTE") ?: "Não Informado"

        // 2. Setup de Views
        setupHeaderViews()
        setupToolbar()
        setupCategoryButtons()
        setupFabListener()
        setupFabExportListener()

        // 3. Carregar a lista inicial (Padrão: Até 12 meses)
        if (cnsPaciente.isNotEmpty()) {
            launchVacinasListFragment("ATE_12_MESES", btnAte12Meses)
        } else {
            Toast.makeText(this, "Erro: CNS do paciente não encontrado.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /**
     * Calcula a idade formatada em anos e meses usando o API legado (Calendar/SimpleDateFormat).
     * @param dataNascimento Data de nascimento no formato "dd/MM/yyyy".
     * @return String formatada: "X anos e Y meses", "X meses", ou "Recém-nascido".
     */
    private fun calculateAge(dataNascimento: String): String {
        // 1. Validação Inicial
        if (dataNascimento.isEmpty()) {
            return "Idade: Não informada"
        }

        // 2. Processamento da Data
        return try {
            // Define o formato e o Locale
            val DATE_FORMAT = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

            // Converte a string de data para um objeto Date
            // Adicionado ?.let para parsear e tratar o resultado null de forma segura
            val birthDate: Date = DATE_FORMAT.parse(dataNascimento) ?: return "Idade: Inválida"

            // Inicializa Calendar para hoje e para a data de nascimento
            val now = Calendar.getInstance()
            val dob = Calendar.getInstance().apply { time = birthDate }

            // 3. Cálculo Inicial (Anos e Meses Brutos)
            var years = now.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
            var months = now.get(Calendar.MONTH) - dob.get(Calendar.MONTH)

            // 4. Ajuste da Idade (Verifica se o aniversário já passou este mês)
            // Se o dia do mês atual for anterior ao dia de nascimento,
            // o mês de aniversário ainda não passou.
            if (now.get(Calendar.DAY_OF_MONTH) < dob.get(Calendar.DAY_OF_MONTH)) {
                months--
            }

            // 5. Normalização de Meses (Garante que months >= 0)
            if (months < 0) {
                years--
                months += 12 // Se os meses forem negativos, subtrai 1 ano e adiciona 12 meses
            }

            // 6. Formatação do Resultado

            if (years <= 0) {
                return when (months) {
                    0 -> {
                        // Se anos=0 e meses=0, verifica os dias
                        val days = (now.timeInMillis - dob.timeInMillis) / (1000 * 60 * 60 * 24)
                        if (days < 30) {
                            if (days < 7) "$days dias" else "Recém-nascido (aprox. $days dias)"
                        } else {
                            "Erro: Meses=0, mas não é recém-nascido."
                        }
                    }
                    1 -> "1 mês"
                    else -> "$months meses"
                }
            }

            // Formato para anos e meses
            val yearsText = if (years == 1) "1 ano" else "$years anos"
            val monthsText = if (months > 0) " e $months meses" else ""

            return "$yearsText$monthsText"

        } catch (e: Exception) {
            Log.e(TAG, "Erro de cálculo de idade", e)
            return "Erro de cálculo de idade"
        }
    }


    // --- MÉTODOS DE SETUP E CARREGAMENTO ---

    override fun onVacinasLoaded(nomePaciente: String, allMergedDoses: List<VacinaDose>) {
        this.allMergedDoses = allMergedDoses
        this.nomePaciente = nomePaciente

        val dosesAplicadas = allMergedDoses.count { it.status == "Aplicada" }

        val dosesAgendadas = allMergedDoses.count { it.status == "Agendada" }

        textQtdVacinasTomadas.text = "Vacinas Aplicadas: $dosesAplicadas doses"
        textQtdVacinasAgendadas.text = "Vacinas Agendadas: $dosesAgendadas doses"

        if (dosesAplicadas > 0) {
            fabExportarPdf.show()
        } else {
            fabExportarPdf.hide()
        }
    }

    private fun setupHeaderViews() {
        textQtdVacinasTomadas = findViewById(R.id.text_qtd_vacinas_tomadas)
        textQtdVacinasTomadas.text = "Vacinas Aplicadas: 0 doses"

        textQtdVacinasAgendadas = findViewById(R.id.text_qtd_vacinas_agendadas)
        textQtdVacinasAgendadas.text = "Vacinas Agendadas: 0 doses"

        // Inicialização da TextView de Idade
        tvDetIdade = findViewById(R.id.tv_det_idade)

        // Exibe os detalhes
        findViewById<TextView>(R.id.text_nome_paciente_header)?.text = nomePaciente
        findViewById<TextView>(R.id.text_nascimento_paciente_header)?.text = "Nasc.: $dataNascimentoPaciente"
        findViewById<TextView>(R.id.text_endereco_paciente_header)?.text = "End.: $enderecoPaciente"
        findViewById<TextView>(R.id.text_cns_paciente_header)?.text = "CNS: $cnsPaciente"
        findViewById<TextView>(R.id.text_email_paciente_header)?.text = "Email: $emailPaciente"

        // Define o texto da idade
        tvDetIdade.text = calculateAge(dataNascimentoPaciente)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar_vacinas)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Carteira Vacinal"
        }
        toolbar.navigationIcon?.setTint(Color.WHITE)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupCategoryButtons() {
        btnAte12Meses = findViewById(R.id.btn_ate_12_meses)
        btnApos12Meses = findViewById(R.id.btn_apos_12_meses)
        btnOutrasVacinas = findViewById(R.id.btn_outras_vacinas)

        fabExportarPdf = findViewById(R.id.fab_exportar_pdf)

        btnAte12Meses.setOnClickListener {
            launchVacinasListFragment("ATE_12_MESES", btnAte12Meses)
        }
        btnApos12Meses.setOnClickListener {
            launchVacinasListFragment("APOS_12_MESES", btnApos12Meses)
        }
        btnOutrasVacinas.setOnClickListener {
            launchVacinasListFragment("OUTRAS_VACINAS", btnOutrasVacinas)
        }
    }

    private fun launchVacinasListFragment(categoria: String, activeBtn: Button) {
        val fragment = VacinasListFragmento.newInstance(cnsPaciente, categoria)

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container_vacinas, fragment)
            .commit()

        updateButtonStyles(activeBtn)
        fabExportarPdf.hide()
    }

    private fun updateButtonStyles(activeButton: Button) {
        val allButtons = listOf(btnAte12Meses, btnApos12Meses, btnOutrasVacinas)

        val activeColor = ContextCompat.getColor(this, android.R.color.holo_blue_dark)
        val inactiveColor = ContextCompat.getColor(this, android.R.color.white)

        val activeTextColor = ContextCompat.getColor(this, android.R.color.white)
        val inactiveTextColor = ContextCompat.getColor(this, android.R.color.black)

        for (btn in allButtons) {
            if (btn == activeButton) {
                btn.setBackgroundColor(activeColor)
                btn.setTextColor(activeTextColor)
            } else {
                btn.setBackgroundColor(inactiveColor)
                btn.setTextColor(inactiveTextColor)
            }
        }
    }

    private fun setupFabListener() {
        val fabAddVacina = findViewById<FloatingActionButton>(R.id.fab_add_vacina)

        fabAddVacina.setOnClickListener {
            val intent = Intent(this, FormularioVacinaActivity::class.java).apply {
                putExtra("CNS_PACIENTE", cnsPaciente)
                putExtra("NOME_PACIENTE", nomePaciente)
            }
            startActivity(intent)
        }
    }

    private fun setupFabExportListener() {
        fabExportarPdf.hide()

        fabExportarPdf.setOnClickListener {
            if (allMergedDoses.isEmpty()) {
                Toast.makeText(this, "Dados de vacina não carregados. Tente novamente.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startPdfCreation()
        }
    }

    private fun startPdfCreation() {
        if (cnsPaciente.isEmpty()) {
            Toast.makeText(this, "Erro: CNS do paciente não está disponível para exportação.", Toast.LENGTH_SHORT).show()
            return
        }

        val cleanName = nomePaciente.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val pdfFileName = "CartaoVacina_${cleanName}_$cnsPaciente.pdf"

        pdfResultLauncher.launch(pdfFileName)
    }

    private fun exportPdf(uri: android.net.Uri, doses: List<VacinaDose>) {
        Toast.makeText(this, "Iniciando a criação do PDF...", Toast.LENGTH_LONG).show()

        val dosesAgendadas = doses.count { it.status == "Agendada" }

        lifecycleScope.launch(Dispatchers.IO) {
            // A classe 'Individuo' é assumida como existente para este bloco
            val individuo = Individuo(
                cns = cnsPaciente,
                nome = nomePaciente,
                dataNascimento = dataNascimentoPaciente,
                endereco = enderecoPaciente,
                email = emailPaciente
            )

            try {
                contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                    FileOutputStream(pfd.fileDescriptor).use { outputStream ->

                        val pdfDocument = generateVacinaPdf(doses, individuo, dosesAgendadas)
                        pdfDocument.writeTo(outputStream)
                        pdfDocument.close()
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RegistroVacinalActivity, "PDF exportado com sucesso para o armazenamento.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao salvar PDF: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RegistroVacinalActivity, "Falha ao salvar PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Geração do Documento PDF com layout estilo Cartão de Vacinação oficial, em modo LANDSCAPE.
     */
    private fun generateVacinaPdf(allDoses: List<VacinaDose>, paciente: Individuo, dosesAgendadas: Int): PdfDocument {
        val document = PdfDocument()

        // CONFIGURAÇÃO LANDSCAPE: 842 x 595 (A4 Invertido)
        val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create()
        var page = document.startPage(pageInfo)

        var canvas = page.canvas
        val textPaint = Paint().apply { color = Color.BLACK }
        val borderPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val margin = 30f
        var yPos = 40f

        // --- Variáveis de Estilo e Layout ---
        val headerSize = 10f
        val textSize = 8f
        val smallTextSize = 7f
        val lineSpacing = 11f
        val doseBoxHeight = 55f
        val totalWidth = 842f
        val pageContentWidth = totalWidth - (2 * margin)
        val maxDosesPerLine = 3
        val numTotalColumns = maxDosesPerLine + 1
        val columnWidth = pageContentWidth / numTotalColumns.toFloat()
        val nameColEnd = margin + columnWidth
        val rowHeight = doseBoxHeight + 5f
        // LIMITE MAXIMIZADO
        val yPageBreakLimit = 580f

        // ----------------------------------------------------
        // FUNÇÕES AUXILIARES
        // ----------------------------------------------------

        fun startNewPage() {
            document.finishPage(page)
            page = document.startPage(pageInfo)
            canvas = page.canvas
            yPos = 40f
        }

        fun drawText(text: String, x: Float, y: Float, size: Float, isBold: Boolean = false) {
            textPaint.textSize = size
            textPaint.isFakeBoldText = isBold
            canvas.drawText(text, x, y, textPaint)
        }

        fun drawDoseDetails(dose: VacinaDose, x: Float, y: Float, width: Float) {
            val doseTitle = dose.dose.uppercase()
            textPaint.textSize = textSize
            textPaint.isFakeBoldText = true
            val doseTitleWidth = textPaint.measureText(doseTitle)
            drawText(doseTitle, x + (width - doseTitleWidth) / 2f, y + 10f, textSize, true)

            var currentY = y + 20f
            val detailsX = x + 3f

            drawText("Data: ${dose.dataAplicacao ?: dose.dataAgendada ?: "/ /"}", detailsX, currentY, smallTextSize)
            currentY += lineSpacing
            drawText("Lote: ${dose.lote ?: "-"}", detailsX, currentY, smallTextSize)
            currentY += lineSpacing
            drawText("Lab.Produt: ${dose.labProdut ?: "-"}", detailsX, currentY, smallTextSize)
            currentY += lineSpacing
            drawText("Unidade: ${dose.unidade ?: "-"}", detailsX, currentY, smallTextSize)
            currentY += lineSpacing
            drawText("Ass.: ${dose.assinaturaAcs ?: "-"}", detailsX, currentY, smallTextSize)
        }

        // ----------------------------------------------------
        // INÍCIO DO DESENHO PRINCIPAL
        // ----------------------------------------------------

        // --- CABEÇALHO ---
        drawText("APLICATIVO VACINACS", margin, yPos, headerSize, true)
        drawText("CARTEIRA DE VACINAÇÃO DIGITAL", margin + 300f, yPos, headerSize, true)
        yPos += 20f

        // Box do Paciente
        val headerBoxHeight = 55f
        val headerBox = RectF(margin, yPos, margin + pageContentWidth, yPos + headerBoxHeight)
        canvas.drawRect(headerBox, borderPaint)

        // Linha 1 da caixa: Nome e Total de Doses Aplicadas
        drawText("NOME: ${paciente.nome}", margin + 5f, yPos + 12f, headerSize)
        val dosesAplicadas = allDoses.count { it.status == "Aplicada" }
        drawText("TOTAL DE DOSES APLICADAS: $dosesAplicadas", margin + 550f, yPos + 12f, headerSize)

        // Linha 2 da caixa: CNS e Total de Doses Agendadas
        drawText("CNS: ${paciente.cns}", margin + 5f, yPos + 27f, headerSize)
        drawText("TOTAL DE DOSES AGENDADAS: $dosesAgendadas", margin + 550f, yPos + 27f, headerSize)

        // Linha 3 da caixa: Data de Nascimento/Idade
        drawText("NASCIMENTO: ${paciente.dataNascimento}", margin + 5f, yPos + 42f, headerSize)
        // Chamada correta da função calculateAge
        val idadePaciente = calculateAge(paciente.dataNascimento)
        drawText("IDADE CALCULADA: $idadePaciente", margin + 300f, yPos + 42f, headerSize)


        yPos += headerBoxHeight + 15f

        // --- AGRUPAMENTO E ORDENAÇÃO DE DOSES ---
        val dosesGrouped = allDoses
            .filter { it.nomeVacina.isNotBlank() }
            .groupBy { it.nomeVacina }
            .toSortedMap()

        var currentCategory: String = ""

        dosesGrouped.forEach { (vacinaNome, dosesList) ->
            val newCategory = when (vacinaNome) {
                "BCG", "Hepatite B", "Penta", "Rotavírus humano", "Pneumocócica 10V (conjugada)", "VIP",
                "Meningocócica C (conjugada)", "Febre Amarela", "Tríplice viral", "Covid-19" -> "ATÉ 12 MESES"
                "DTP", "VOP", "Tetraviral", "Varicela", "Hepatite A", "HPV",
                "Pneumocócica 23V (povos indígenas)" -> "A PARTIR DE 12 MESES"
                else -> "OUTRAS VACINAS E ESTRATÉGIAS"
            }

            if (newCategory != currentCategory) {
                if (yPos > yPageBreakLimit - (lineSpacing * 2)) {
                    startNewPage()
                }
                yPos += lineSpacing + 10f
                currentCategory = newCategory
            }

            val numDoses = dosesList.size
            val numRows = (numDoses + maxDosesPerLine - 1) / maxDosesPerLine
            val totalVacinaHeight = (numRows * rowHeight)

            // LÓGICA DE QUEBRA DE PÁGINA
            if (yPos + totalVacinaHeight + 15f > yPageBreakLimit) {
                startNewPage()
                yPos += lineSpacing + 10f
            }

            // Desenha a caixa de vacina
            val vacinaBox = RectF(margin, yPos, margin + pageContentWidth, yPos + totalVacinaHeight)
            canvas.drawRect(vacinaBox, borderPaint)

            // Desenha o nome da vacina
            val vacinaNameY = yPos + totalVacinaHeight / 2f + textSize / 2f
            drawText(vacinaNome.uppercase(), margin + 5f, vacinaNameY, headerSize, true)

            // Desenha divisórias
            canvas.drawLine(nameColEnd, yPos, nameColEnd, yPos + totalVacinaHeight, borderPaint)

            for (i in 2 until numTotalColumns) {
                val x = margin + (i * columnWidth)
                canvas.drawLine(x, yPos, x, yPos + totalVacinaHeight, borderPaint)
            }

            for (i in 1 until numRows) {
                val y = yPos + (i * rowHeight)
                canvas.drawLine(nameColEnd, y, margin + pageContentWidth, y, borderPaint)
            }

            // Desenha os detalhes da dose
            for (i in dosesList.indices) {
                val dose = dosesList[i]
                val row = i / maxDosesPerLine
                val col = i % maxDosesPerLine

                val xStart = nameColEnd + (col * columnWidth)
                val yStart = yPos + (row * rowHeight)

                drawDoseDetails(dose, xStart, yStart, columnWidth)
            }

            yPos += totalVacinaHeight + 15f
        }

        // --- FINALIZAÇÃO DO DOCUMENTO ---
        document.finishPage(page)
        return document
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}