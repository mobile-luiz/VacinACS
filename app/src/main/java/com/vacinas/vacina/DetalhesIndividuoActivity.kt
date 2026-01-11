package com.vacinas.vacina

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.vacinas.vacina.data.FirebaseSyncManager
import com.vacinas.vacina.data.IndividuoDao
import java.text.SimpleDateFormat
import java.util.*

class DetalhesIndividuoActivity : AppCompatActivity() {

    private val MOCK_GENDER_FOR_ICON = "F"
    private var acsName: String = "Não Definido"
    private var currentIndividuo: Individuo? = null
    private lateinit var individuoDao: IndividuoDao
    private lateinit var firebaseSyncManager: FirebaseSyncManager
    private val TAG = "DetalhesIndividuo"

    // IMPORTANTISSIMO: Assumindo que a classe Individuo agora tem o campo:
    // var ultimaAtualizacaoStr: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalhes_individuo)

        individuoDao = IndividuoDao(applicationContext)
        firebaseSyncManager = FirebaseSyncManager(this)

        val cns = intent.getStringExtra("CNS_INDIVIDUO")

        if (cns.isNullOrEmpty()) {
            Toast.makeText(
                this,
                "Erro: CNS do paciente não encontrado na Intent.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        loadIndividuo(cns)

        findViewById<Button>(R.id.btn_registrar_visita)?.setOnClickListener {
            currentIndividuo?.let { individuo ->
                // Permite registrar nova visita se já foi agendado ou visitado.
                if (individuo.statusVisita.equals(
                        "visitado",
                        ignoreCase = true
                    ) || individuo.statusVisita.equals("agendado", ignoreCase = true)
                ) {
                    showVisitConfirmationDialog(individuo)
                } else {
                    registerVisit()
                }
            }
        }
    }

    private fun loadIndividuo(cns: String) {
        val individuo = individuoDao.findByCns(cns)

        if (individuo == null) {
            Toast.makeText(
                this,
                "Erro: Detalhes do paciente não encontrados no DB local.",
                Toast.LENGTH_LONG
            ).show()
            Log.e(TAG, "Falha ao carregar detalhes para o CNS: $cns.")
            finish()
            return
        }

        currentIndividuo = individuo
        setupDetails(currentIndividuo!!)
        setupFooterButtons(currentIndividuo!!)
    }

    // --- FUNÇÕES DE DATA E HORA CORE ---

    /**
     * Define como um timestamp (UTC) deve ser formatado para exibição no APP (DD/MM/AAAA).
     */
    private fun formatTimestampToDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        // ✅ Força a interpretação do timestamp como sendo em UTC para Agendamentos.
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timestamp))
    }

    /**
     * Formata o timestamp para ser salvo como String no banco de dados (ultimaAtualizacaoStr).
     * @param isInstantVisit Se TRUE, usa data e hora local. Se FALSE (agendamento), usa apenas data UTC.
     */
    private fun formatTimestampForDbString(timestamp: Long, isInstantVisit: Boolean): String {
        val pattern = if (isInstantVisit) "dd/MM/yyyy HH:mm:ss" else "dd/MM/yyyy"
        val sdf = SimpleDateFormat(pattern, Locale("pt", "BR"))

        // Se for AGENDAMENTO, usamos UTC. Para visita instantânea/edição, usamos o fuso local padrão.
        if (!isInstantVisit) {
            sdf.timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(timestamp))
    }

    // --- LÓGICA DE EDIÇÃO (CORRIGIDA) ---

    private fun showEditModal(individuo: Individuo) {
        // [CÓDIGO OMITIDO: Funções auxiliares para criar campos]
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 30, 60, 30)
        }
        // Funções auxiliares para criar campos (Mantidas para referência, mas podem estar fora desta classe)
        fun createLabeledEditText(
            labelText: String,
            initialValue: String,
            inputType: Int = android.text.InputType.TYPE_CLASS_TEXT,
            isEditable: Boolean = true
        ): EditText {
            val fieldContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 20, 0, 0) }
            }
            val label = TextView(this).apply {
                text = labelText
                textSize = 14f
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        android.R.color.darker_gray
                    )
                )
                setPadding(0, 0, 0, 4)
            }
            fieldContainer.addView(label)
            val editText = EditText(this).apply {
                setText(initialValue)
                hint = "Digite aqui..."
                isFocusableInTouchMode = isEditable
                isFocusable = isEditable
                isClickable = !isEditable
                setBackgroundResource(android.R.drawable.edit_text)
                setPadding(16, 16, 16, 16)
                if (inputType != android.text.InputType.TYPE_CLASS_TEXT) {
                    this.inputType = inputType
                }
            }
            fieldContainer.addView(editText)
            layout.addView(fieldContainer)
            return editText
        }

        // --- Campos de Edição ---
        val etNome = createLabeledEditText("Nome Completo", individuo.nome)
        val etCns = createLabeledEditText(
            "CNS (Cartão Nacional de Saúde)",
            individuo.cns,
            android.text.InputType.TYPE_CLASS_NUMBER
        )
        val etEndereco = createLabeledEditText("Endereço", individuo.endereco)
        val etProntuarioFamilia = createLabeledEditText(
            "Prontuário da Família",
            individuo.prontuarioFamilia,
            android.text.InputType.TYPE_CLASS_NUMBER
        )

        // --- Campo Data de Nascimento (com DatePicker) ---
        val etDataNascimento = createLabeledEditText(
            "Data de Nascimento",
            individuo.dataNascimento,
            isEditable = false
        )

        val selectedCalendar = Calendar.getInstance()
        try {
            if (individuo.dataNascimento.isNotEmpty()) {
                val date = SimpleDateFormat(
                    "dd/MM/yyyy",
                    Locale("pt", "BR")
                ).parse(individuo.dataNascimento)
                date?.let { selectedCalendar.time = it }
            }
        } catch (e: Exception) {
            Log.e(
                "DetalhesActivity",
                "Erro ao parsear Data de Nascimento: ${individuo.dataNascimento}"
            )
        }

        val dateSetListener =
            DatePickerDialog.OnDateSetListener { _, year, monthOfYear, dayOfMonth ->
                selectedCalendar.set(Calendar.YEAR, year)
                selectedCalendar.set(Calendar.MONTH, monthOfYear)
                selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
                etDataNascimento.setText(sdf.format(selectedCalendar.time))
            }

        etDataNascimento.setOnClickListener {
            DatePickerDialog(
                this,
                dateSetListener,
                selectedCalendar.get(Calendar.YEAR),
                selectedCalendar.get(Calendar.MONTH),
                selectedCalendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val etNomeMae = createLabeledEditText("Nome da Mãe", individuo.nomeMae)
        val etNomePai = createLabeledEditText("Nome do Pai (Opcional)", individuo.nomePai)
        val etCelular = createLabeledEditText(
            "Celular",
            individuo.celular,
            android.text.InputType.TYPE_CLASS_PHONE
        )
        val etEmail = createLabeledEditText(
            "Cadastro em",
            individuo.email,
            android.text.InputType.TYPE_CLASS_PHONE
        )

        scrollView.addView(layout)

        AlertDialog.Builder(this)
            .setTitle("✏️ Edição de Cadastro")
            .setView(scrollView)
            .setPositiveButton("Salvar") { dialog, _ ->
                val novoNome = etNome.text.toString().trim()
                val novoCns = etCns.text.toString().trim()
                val novoEndereco = etEndereco.text.toString().trim()
                val novoProntuarioFamilia = etProntuarioFamilia.text.toString().trim()
                val novaDataNascimento = etDataNascimento.text.toString().trim()
                val novoNomeMae = etNomeMae.text.toString().trim()
                val novoNomePai = etNomePai.text.toString().trim()
                val novoCelular = etCelular.text.toString().trim()
                val novoEmail = etEmail.text.toString().trim()

                if (novoNome.isEmpty() || novoCns.isEmpty()) {
                    Toast.makeText(
                        this,
                        "Nome e CNS são obrigatórios e não podem estar vazios.",
                        Toast.LENGTH_LONG
                    ).show()
                    dialog.dismiss()
                    return@setPositiveButton
                }

                currentIndividuo?.apply {
                    nome = novoNome
                    cns = novoCns
                    endereco = novoEndereco
                    prontuarioFamilia = novoProntuarioFamilia
                    dataNascimento = novaDataNascimento
                    nomeMae = novoNomeMae
                    nomePai = novoNomePai
                    celular = novoCelular
                    email = novoEmail

                    // 🔑 Atualização de Edição: Usa o tempo atual
                    val updatedTime = System.currentTimeMillis()
                    ultimaAtualizacao = updatedTime
                    // ✅ CORREÇÃO APLICADA: Usa TRUE para incluir data e hora local (DD/MM/AAAA HH:mm:ss)
                    ultimaAtualizacaoStr = formatTimestampForDbString(updatedTime, true)

                    isSynchronized = false
                }

                val sqliteResult = currentIndividuo?.let { updatedIndividuo ->
                    individuoDao.saveOrUpdate(updatedIndividuo)
                } ?: -1L

                val isDbUpdated = sqliteResult > 0


                if (isDbUpdated) {
                    Toast.makeText(
                        this,
                        "Dados de ${novoNome} atualizados e salvos localmente!",
                        Toast.LENGTH_LONG
                    ).show()

                    currentIndividuo?.let { updatedIndividuo ->
                        setupDetails(updatedIndividuo)
                        val resultIntent = Intent().apply {
                            putExtra("EXTRA_INDIVIDUO_ATUALIZADO", updatedIndividuo)
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        firebaseSyncManager.syncIndividuoDetails(updatedIndividuo)
                    }

                } else {
                    Toast.makeText(
                        this,
                        "Erro ao tentar salvar as alterações no banco de dados local.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }


    private fun setupFooterButtons(individuo: Individuo) {
        val btnEditar = findViewById<TextView>(R.id.btn_footer_editar)
        val btnAgendar = findViewById<Button>(R.id.btn_footer_agendar)

        btnEditar?.setOnClickListener {
            showEditModal(individuo)
        }

        btnAgendar?.setOnClickListener {
            showScheduleVisitModal()
        }

        // Lógica de navegação para a tela de Vacinas
        findViewById<TextView>(R.id.btn_footer_vacinas)?.setOnClickListener {
            val intent = Intent(this, RegistroVacinalActivity::class.java).apply {
                putExtra("NOME_PACIENTE", individuo.nome)
                putExtra("CNS_PACIENTE", individuo.cns)
                // 🚨 NOVO: Adicionando a data de nascimento à Intent
                putExtra("DATA_NASCIMENTO_PACIENTE", individuo.dataNascimento)
                // 🚨 NOVO: Adicionando Endereço
                putExtra("ENDERECO_PACIENTE", individuo.endereco)
                // ✅ NOVO: Adicionando o EMAIL à Intent
                putExtra("EMAIL_PACIENTE", individuo.email) // <--- LINHA ADICIONADA
            }
            startActivity(intent)
        }
    }

    private fun setupDetails(individuo: Individuo) {
        // ... (Configuração do card principal) ...
        findViewById<TextView>(R.id.tv_name_main).text = individuo.nome.uppercase()
        findViewById<TextView>(R.id.tv_name_full).text =
            "NOME COMPLETO: ${individuo.nome.uppercase()}"

        findViewById<TextView>(R.id.tv_gender_icon)?.let {
            setupGenderIcon(
                MOCK_GENDER_FOR_ICON,
                it
            )
        }

        findViewById<TextView>(R.id.tv_det_status_visita)?.let {
            setupStatusVisita(individuo.statusVisita, individuo.ultimaAtualizacao, it)
        }

        val btnRegistrar = findViewById<Button>(R.id.btn_registrar_visita)
        btnRegistrar?.visibility = View.VISIBLE


        // --- SEÇÃO IDENTIFICAÇÃO ---
        val layoutDetails = findViewById<LinearLayout>(R.id.layout_identification_details)
        val children = mutableListOf<TextView>()

        for (i in 0 until layoutDetails.childCount) {
            (layoutDetails.getChildAt(i) as? TextView)?.let { children.add(it) }
        }

        if (children.size >= 10) {
            // Preenche os campos por índice:
            children[0].text = "Endereço: ${individuo.endereco.ifEmpty { "Não informado" }}"
            children[1].text =
                "Família: PRONTUÁRIO ${individuo.prontuarioFamilia.ifEmpty { "Não informado" }}"
            children[2].text = calculateAge(individuo.dataNascimento)
            children[3].text = "Nascimento: ${individuo.dataNascimento.ifEmpty { "Não informado" }}"
            children[4].text = "Celular: ${individuo.celular.ifEmpty { "Não informado" }}"
            children[5].text = "Cadastro em: ${individuo.email.ifEmpty { "Não cadastrado" }}"
            children[6].text = "CNS: ${individuo.cns.ifEmpty { "Não informado" }}"
            children[7].text = "CPF: Não informado"
            children[8].text = "Nome da mãe: ${individuo.nomeMae.ifEmpty { "Não informado" }}"
            children[9].text = "Nome do pai: ${individuo.nomePai.ifEmpty { "Não informado" }}"
        }
    }

    private fun showVisitConfirmationDialog(individuo: Individuo) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Visita Já Registrada")
        builder.setMessage("O paciente já foi visitado ou agendado. Deseja registrar uma nova visita (isso irá atualizar a data e hora no banco de dados para agora)?")
        builder.setPositiveButton("Registrar Nova Visita") { dialog, _ ->
            registerVisit()
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    /**
     * Garante o uso consistente de UTC para evitar o erro de um dia no agendamento.
     */
    private fun showScheduleVisitModal() {
        val initialCalendar = Calendar.getInstance()

        var selectedTimestamp: Long = 0L

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }

        val tvInstruction = TextView(this).apply {
            text = "Selecione a data planejada para a visita:"
            textSize = 16f
            setPadding(0, 0, 0, 16)
        }
        layout.addView(tvInstruction)

        val etDate = EditText(this).apply {
            isFocusable = false
            isClickable = true
            hint = "Toque para selecionar a data"
        }
        layout.addView(etDate)

        // DatePickerDialog setup
        val dateSetListener =
            DatePickerDialog.OnDateSetListener { _, year, monthOfYear, dayOfMonth ->

                // ✅ CORREÇÃO 1: Cria um Calendar forçando o fuso UTC (meia-noite)
                val selectedCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, monthOfYear)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                // Define o formatador para o campo de texto
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
                // ✅ CORREÇÃO 2: Força o formatador a usar UTC para exibir a data correta no campo do modal.
                sdf.timeZone = TimeZone.getTimeZone("UTC")

                etDate.setText(sdf.format(selectedCalendar.time))

                selectedTimestamp = selectedCalendar.timeInMillis
            }

        // Listener para abrir o DatePickerDialog
        etDate.setOnClickListener {
            DatePickerDialog(
                this,
                dateSetListener,
                initialCalendar.get(Calendar.YEAR),
                initialCalendar.get(Calendar.MONTH),
                initialCalendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // Constrói e exibe o AlertDialog
        AlertDialog.Builder(this)
            .setTitle("📅 Agendar Visita")
            .setView(layout)
            .setPositiveButton("Confirmar Agendamento") { dialog, _ ->
                if (selectedTimestamp > 0) {
                    scheduleVisit(selectedTimestamp)
                } else {
                    Toast.makeText(
                        this,
                        "Por favor, toque e selecione uma data para agendar.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }


    /**
     * LÓGICA PRINCIPAL para Registro de Visita (AGORA): Atualiza o status para "Visitado".
     */
    private fun registerVisit() {
        currentIndividuo?.let { individuo ->
            val currentTimestamp = System.currentTimeMillis()

            individuo.statusVisita = "Visitado"
            individuo.ultimaAtualizacao = currentTimestamp // Salva o timestamp Long
            // 🔑 Salva a string formatada (data e hora local) para leitura no DB
            individuo.ultimaAtualizacaoStr = formatTimestampForDbString(currentTimestamp, true)
            individuo.isSynchronized = false

            val isUpdated = individuoDao.saveOrUpdate(individuo) > 0

            if (isUpdated) {
                Toast.makeText(
                    this,
                    "Visita de ${individuo.nome} registrada e salva no banco!",
                    Toast.LENGTH_LONG
                ).show()

                firebaseSyncManager.syncIndividuoDetails(individuo)

                val resultIntent = Intent().apply {
                    putExtra("EXTRA_INDIVIDUO_ATUALIZADO", individuo)
                }
                setResult(Activity.RESULT_OK, resultIntent)
            } else {
                Toast.makeText(
                    this,
                    "Erro ao salvar a visita no banco de dados. Verifique o CNS.",
                    Toast.LENGTH_LONG
                ).show()
            }

            setupDetails(individuo)
        }
    }

    /**
     * LÓGICA PRINCIPAL para Agendamento (FUTURO): Atualiza o status para "Agendado".
     */
    private fun scheduleVisit(visitTimestamp: Long) {
        currentIndividuo?.let { individuo ->
            individuo.statusVisita = "Agendado"
            individuo.ultimaAtualizacao = visitTimestamp // Salva o timestamp Long (meia-noite UTC)
            // 🔑 Salva a string formatada (apenas data em UTC) para leitura no DB
            individuo.ultimaAtualizacaoStr = formatTimestampForDbString(visitTimestamp, false)
            individuo.isSynchronized = false

            val isUpdated = individuoDao.saveOrUpdate(individuo) > 0

            if (isUpdated) {
                Toast.makeText(
                    this,
                    "Visita de ${individuo.nome} agendada e salva localmente!",
                    Toast.LENGTH_LONG
                ).show()

                firebaseSyncManager.syncIndividuoDetails(individuo)

                val resultIntent = Intent().apply {
                    putExtra("EXTRA_INDIVIDUO_ATUALIZADO", individuo)
                }
                setResult(Activity.RESULT_OK, resultIntent)
            } else {
                Toast.makeText(
                    this,
                    "Erro ao agendar a visita no banco de dados. Verifique o CNS.",
                    Toast.LENGTH_LONG
                ).show()
            }

            setupDetails(individuo)
        }
    }


    /**
     * Calcula a idade formatada em anos e meses.
     */
    private fun calculateAge(dataNascimento: String): String {
        // ... (código inalterado) ...
        if (dataNascimento.isEmpty()) return "Idade: Não informada"

        return try {
            val DATE_FORMAT = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
            val birthDate = DATE_FORMAT.parse(dataNascimento) ?: return "Idade: Inválida"
            val now = Calendar.getInstance()
            val dob = Calendar.getInstance().apply { time = birthDate }

            var years = now.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
            var months = now.get(Calendar.MONTH) - dob.get(Calendar.MONTH)

            if (months < 0) {
                years--
                months += 12
            }

            if (years == 0) {
                return when (months) {
                    0 -> "Recém-nascido"
                    1 -> "1 mês"
                    else -> "$months meses"
                }
            }

            val yearsText = if (years == 1) "1 ano" else "$years anos"
            val monthsText = if (months > 0) " e $months meses" else ""

            "$yearsText$monthsText"

        } catch (e: Exception) {
            "Erro de cálculo de idade"
        }
    }


    /**
     * Define o ícone de gênero (♀ ou ♂) e sua cor.
     */
    private fun setupGenderIcon(gender: String, textView: TextView) {
        if (gender.equals("Feminino", ignoreCase = true) || gender.equals("F", ignoreCase = true)) {
            textView.text = "♀"
            textView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
        } else {
            textView.text = "♂"
            textView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
        }
    }


    /**
     * Define a cor de fundo e o texto para o status de visita, usando a data formatada.
     */
    private fun setupStatusVisita(status: String, timestamp: Long, textView: TextView) {
        val (baseText, statusColorResId) = when (status.lowercase()) {
            "visitado" -> {
                val dateString = formatTimestampToDate(timestamp)
                "VISITADO EM ${dateString}" to android.R.color.holo_green_dark
            }

            "agendado" -> {
                val dateString = formatTimestampToDate(timestamp)
                "AGENDADO P/ ${dateString}" to android.R.color.holo_orange_dark
            }

            else -> "SEM VISITA" to android.R.color.holo_red_dark
        }

        textView.text = baseText
        textView.backgroundTintList = ContextCompat.getColorStateList(this, statusColorResId)
    }

}