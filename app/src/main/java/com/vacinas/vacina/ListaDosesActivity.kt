package com.vacinas.vacina

import android.app.DatePickerDialog // ⭐️ Importação necessária
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vacinas.vacina.R

import com.google.android.material.textfield.TextInputEditText // ⭐️ Importação necessária
import com.google.firebase.auth.FirebaseAuth
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class ListaDosesActivity : AppCompatActivity() {

    private var barChart: BarChart? = null
    private var cardChart: View? = null
    private var dosesRecyclerView: RecyclerView? = null

    // ⭐️ Propriedades para o filtro de data
    private lateinit var edtDataInicial: TextInputEditText
    private lateinit var edtDataFinal: TextInputEditText

    private lateinit var vacinaRepository: VacinaRepository
    private lateinit var auth: FirebaseAuth
    private var currentAcsUid: String? = null
    private val TAG = "ListaDoses"

    private val APPLIED_STATUSES = setOf("APLICADA", "APLICADO", "CONCLUIDA", "ADMINISTRADA")

    // ⭐️ Formatadores de data
    private val displayDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val dbDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        currentAcsUid = auth.currentUser?.uid

        setContentView(R.layout.activity_lista_doses)

        // CONFIGURAÇÃO DA TOOLBAR
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        vacinaRepository = VacinaRepository(application)
        initializeViews()
        setupDatePickers() // ⭐️ Configura os seletores de data para filtro automático

        Log.d(TAG, "DEBUG FLUXO: Inicializando busca de dados...")

        window.decorView.post {
            fetchDosesFromRoom()
        }
    }

    // LÓGICA DE VOLTAR AO CLICAR NA SETA
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initializeViews() {
        barChart = findViewById(R.id.chart_vacinas_mes)
        cardChart = findViewById(R.id.card_chart_vacinas)

        dosesRecyclerView = findViewById(R.id.recycler_doses_resumo)
        dosesRecyclerView?.layoutManager = LinearLayoutManager(this)
        dosesRecyclerView?.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        // ⭐️ Inicializa os campos de filtro
        edtDataInicial = findViewById(R.id.edt_data_inicial)
        edtDataFinal = findViewById(R.id.edt_data_final)
    }

    // ⭐️ NOVO: Configura os DatePickers para chamarem o filtro
    private fun setupDatePickers() {
        edtDataInicial.setOnClickListener {
            showDatePickerDialog(edtDataInicial)
        }
        edtDataFinal.setOnClickListener {
            showDatePickerDialog(edtDataFinal)
        }
    }

    // ⭐️ NOVO: Função para exibir o DatePickerDialog e acionar o filtro
    private fun showDatePickerDialog(editText: TextInputEditText) {
        val calendar = Calendar.getInstance()
        val initialDateText = editText.text.toString()

        if (initialDateText.isNotBlank()) {
            try {
                val initialDate = displayDateFormat.parse(initialDateText)
                initialDate?.let { calendar.time = it }
            } catch (e: Exception) {
                Log.w(TAG, "Data inicial inválida no campo, usando a data atual.")
            }
        }

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(selectedYear, selectedMonth, selectedDayOfMonth)

                // 1. Atualiza o campo de texto
                editText.setText(displayDateFormat.format(selectedDate.time))

                // 2. CHAMA O FILTRO AUTOMATICAMENTE
                fetchDosesFromRoom()

            },
            year, month, day
        )
        datePickerDialog.show()
    }


    // ⭐️ FUNÇÃO MODIFICADA: Agora aceita datas de início e fim
    private fun filterAppliedDoses(rawList: List<VacinaDose>, startDate: Date?, endDate: Date?): List<VacinaDose> {
        return rawList.filter { dose ->
            val status = dose.status?.uppercase(Locale.ROOT) ?: ""
            val isApplied = status in APPLIED_STATUSES

            if (!isApplied) return@filter false

            // Filtra por data se as datas de início/fim forem fornecidas
            if (startDate != null || endDate != null) {
                try {
                    val doseDate = dbDateFormat.parse(dose.dataAplicacao)
                    if (doseDate != null) {
                        // Verifica se a dose está DENTRO do intervalo
                        val isAfterStart = startDate == null || !doseDate.before(startDate)
                        // Adicionamos 1 dia ao endDate para incluir doses aplicadas no último dia do filtro
                        val nextDayAfterEnd = if (endDate != null) {
                            val cal = Calendar.getInstance()
                            cal.time = endDate
                            cal.add(Calendar.DAY_OF_MONTH, 1)
                            cal.time
                        } else null

                        // Dose é antes do dia seguinte ao final (ou seja, INCLUI a data final)
                        val isBeforeEnd = nextDayAfterEnd == null || doseDate.before(nextDayAfterEnd)

                        return@filter isApplied && isAfterStart && isBeforeEnd
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao parsear data da dose: ${dose.dataAplicacao}", e)
                    return@filter false
                }
            }
            isApplied
        }
    }

    // ⭐️ FUNÇÃO MODIFICADA: Agora lê os campos de data antes de filtrar
    private fun fetchDosesFromRoom() {
        lifecycleScope.launch {
            try {
                val dosesFromDb = withContext(Dispatchers.IO) {
                    val acsId = currentAcsUid
                    if (acsId.isNullOrEmpty()) return@withContext emptyList<VacinaDose>()
                    vacinaRepository.getDosesByAcs(acsId)
                }

                // ⭐️ Lê e parseia as datas dos campos
                val startDate: Date? = try {
                    edtDataInicial.text.toString().takeIf { it.isNotBlank() }?.let { displayDateFormat.parse(it) }
                } catch (e: Exception) { null }

                val endDate: Date? = try {
                    edtDataFinal.text.toString().takeIf { it.isNotBlank() }?.let { displayDateFormat.parse(it) }
                } catch (e: Exception) { null }

                // ⭐️ Passa as datas para o filtro
                val appliedDoseList = filterAppliedDoses(dosesFromDb, startDate, endDate)

                if (appliedDoseList.isNotEmpty()) {
                    setupVacinasChartAndList(appliedDoseList)
                } else {
                    cardChart?.visibility = View.GONE
                    dosesRecyclerView?.adapter = DosesResumoAdapter(emptyList()) // Limpa a lista
                    Log.d(TAG, "DEBUG FLUXO: Nenhuma dose aplicada encontrada com os filtros.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao carregar doses do Room: ${e.message}", e)
                cardChart?.visibility = View.GONE
            }
        }
    }

    // ... [calculateDosesByMonth e setupVacinasChartAndList (Gráfico) permanecem inalteradas]

    private fun calculateDosesByMonth(allAppliedDoses: List<VacinaDose>): Map<String, Int> {
        val inputDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val outputMonthFormat = SimpleDateFormat("MMM/yy", Locale("pt", "BR"))

        val rawMap = allAppliedDoses
            .filter { !it.dataAplicacao.isNullOrBlank() }
            .mapNotNull { dose ->
                try {
                    inputDateFormat.parse(dose.dataAplicacao)?.let { date ->
                        outputMonthFormat.format(date).replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                        }
                    }
                } catch (e: Exception) { null }
            }
            .groupingBy { it }
            .eachCount()

        return rawMap.toSortedMap(compareBy { monthLabel ->
            try {
                SimpleDateFormat("MMM/yy", Locale("pt", "BR")).parse(monthLabel)
            } catch (e: Exception) {
                Date(Long.MAX_VALUE)
            }
        })
    }

    private fun setupVacinasChartAndList(allAppliedDoses: List<VacinaDose>) {
        val dosesMap = calculateDosesByMonth(allAppliedDoses)

        Log.d(TAG, "Gráfico: Conteúdo de dosesMap (Mês/Ano=Contagem): $dosesMap")

        if (dosesMap.isEmpty()) {
            cardChart?.visibility = View.GONE
            return
        }

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        dosesMap.keys.forEachIndexed { index: Int, monthLabel: String ->
            val count = dosesMap[monthLabel] ?: 0
            entries.add(BarEntry(index.toFloat(), count.toFloat()))
            labels.add(monthLabel)
        }

        barChart?.let { chart ->
            val dataSet = BarDataSet(entries, "Doses Aplicadas")
            dataSet.color = ContextCompat.getColor(this, android.R.color.holo_blue_dark)
            dataSet.valueTextSize = 10f
            dataSet.setDrawValues(true)
            dataSet.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String { return value.toInt().toString() }
            }
            val barData = BarData(dataSet)
            barData.barWidth = 0.9f
            chart.data = barData

            val maxCount = entries.maxOfOrNull { it.y }?.toInt() ?: 1
            val yAxisMax = maxOf((maxCount + 1).toFloat(), 3f)
            chart.axisLeft.axisMaximum = yAxisMax
            chart.axisLeft.axisMinimum = 0f

            // O gráfico usa os rótulos na ordem crescente (índice 0 = mais antigo)
            chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
            chart.xAxis.granularity = 1f
            chart.axisRight.isEnabled = false
            chart.description.isEnabled = false
            chart.animateY(1000)

            window.decorView.post {
                Log.d(TAG, "DEBUG RENDER: **Iniciando Redraw Forçado** no DecorView")

                cardChart?.visibility = View.VISIBLE
                chart.notifyDataSetChanged()
                chart.requestLayout()
                chart.invalidate()

                // 🛑 CORREÇÃO: Invertemos a lista antes de passar para o adapter da RecyclerView.
                // Isso exibe o resumo de doses do mês mais recente para o mais antigo.
                dosesRecyclerView?.adapter = DosesResumoAdapter(dosesMap.toList().reversed())
                Log.d(TAG, "DEBUG RENDER: Redraw concluído. Lista setada.")
            }
        }
    }

    // CLASSE ADAPTER (Inalterada)
    class DosesResumoAdapter(private val dosesList: List<Pair<String, Int>>) :
        RecyclerView.Adapter<DosesResumoAdapter.DoseViewHolder>() {

        class DoseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textMes: TextView = itemView.findViewById(R.id.text_mes)
            val textContagem: TextView = itemView.findViewById(R.id.text_contagem)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DoseViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_resumo, parent, false)
            return DoseViewHolder(view)
        }

        override fun onBindViewHolder(holder: DoseViewHolder, position: Int) {
            val (mesAno, contagem) = dosesList[position]
            holder.textMes.text = "Mês: $mesAno"
            holder.textContagem.text = "$contagem Doses Aplicadas"
        }

        override fun getItemCount() = dosesList.size
    }
}