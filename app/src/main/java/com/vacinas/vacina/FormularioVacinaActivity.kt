package com.vacinas.vacina

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.vacinas.vacina.data.FirebaseSyncManager
import com.vacinas.vacina.data.VacinaDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

class FormularioVacinaActivity : AppCompatActivity() {

    // ... (Atributos e constantes inalteradas) ...
    // UI Components (Lazy)
    private val editNomeVacina: TextView by lazy { findViewById(R.id.edit_vacina_nome) }
    private val editDose: TextView by lazy { findViewById(R.id.edit_dose_nome) }
    private val editDataAplicacao: EditText by lazy { findViewById(R.id.edit_data_aplicacao) }
    private val editLote: EditText by lazy { findViewById(R.id.edit_lote) }
    private val editLabProdut: EditText by lazy { findViewById(R.id.edit_lab_produt) }
    private val editUnidade: EditText by lazy { findViewById(R.id.edit_unidade) }
    private val editAssinaturaAcs: EditText by lazy { findViewById(R.id.edit_assinatura_acs) }
    private val btnSalvar: Button by lazy { findViewById(R.id.btn_salvar_vacina) }
    private val btnAgendarProxima: Button by lazy { findViewById(R.id.btn_agendar_proxima) }
    private val toolbar: Toolbar by lazy { findViewById(R.id.toolbar_formulario) }

    // State
    private var cnsPaciente: String = ""
    private var doseVacina: String = ""
    private var nomeVacina: String = ""
    private var vacinaKey: String = ""
    private var dataAgendadaProximaDose: String? = null

    private val TAG = "FormularioVacina"

    // Dependencies
    private lateinit var vacinaDao: VacinaDao
    private lateinit var firebaseSyncManager: FirebaseSyncManager

    // Status constants
    private companion object {
        const val STATUS_APLICADA = "Aplicada"
        const val STATUS_CANCELADO = "Cancelado"
        const val STATUS_PENDENTE = "Pendente"
        const val STATUS_AGENDADA = "Agendada"
    }

    // ---------------- Lifecycle ----------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_formulario_vacina)

        // As classes VacinaDao e FirebaseSyncManager precisam ser acessíveis
        vacinaDao = VacinaDao(applicationContext)
        firebaseSyncManager = FirebaseSyncManager(applicationContext)

        setupUi()
        readIntentExtras()
        setupListeners()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // ---------------- UI & Intent ----------------
    private fun setupUi() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.navigationIcon?.setTint(android.graphics.Color.WHITE)

        // Evita digitação direta na data (abrir DatePicker)
        editDataAplicacao.apply {
            isFocusable = false
            isFocusableInTouchMode = false
            isEnabled = true
        }
    }

    private fun readIntentExtras() {
        val extras = intent.extras ?: return

        cnsPaciente = extras.getString("CNS_PACIENTE") ?: ""
        nomeVacina = extras.getString("NOME_VACINA") ?: "Vacina Desconhecida"
        doseVacina = extras.getString("DOSE_VACINA") ?: "Dose Única"
        // ⭐️ Garante que a chave lida do Fragmento (ou gerada) esteja no formato unificado
        vacinaKey = extras.getString("VACINA_KEY") ?: generateVacinaKey(nomeVacina, doseVacina)

        // Preenche os campos com dados existentes (se estiver editando)
        editDataAplicacao.setText(extras.getString("DATA_APLICACAO"))
        editLote.setText(extras.getString("LOTE"))
        editLabProdut.setText(extras.getString("LAB_PRODUT"))
        editUnidade.setText(extras.getString("UNIDADE"))
        editAssinaturaAcs.setText(extras.getString("ASSINATURA_ACS"))

        editNomeVacina.text = nomeVacina
        editDose.text = doseVacina
        supportActionBar?.title = nomeVacina
        btnSalvar.text = "Registrar Aplicação"

        // Busca assíncrona do agendamento futuro
        configureNextDoseButtonAsync()
    }

    private fun setupListeners() {
        btnSalvar.setOnClickListener { saveVacinaData() }
        editDataAplicacao.setOnClickListener { showDatePicker(editDataAplicacao, allowClear = true) }
        btnAgendarProxima.setOnClickListener {
            if (btnAgendarProxima.isEnabled) showDatePickerForNextDose()
            else Toast.makeText(this, "Esta dose não requer agendamento de próxima dose.", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------- Próxima dose (async) ----------------
    private fun configureNextDoseButtonAsync() {
        val next = VaccineScheduler.getNextDose(nomeVacina, doseVacina)
        if (next == null) {
            btnAgendarProxima.visibility = View.GONE
            btnAgendarProxima.isEnabled = false
            return
        }

        // ⭐️ Usa a chave unificada
        val nextKey = generateVacinaKey(next.vacinaNome, next.doseNome)

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { vacinaDao.getVacinaDose(cnsPaciente, nextKey) }
                .onSuccess { existing ->
                    withContext(Dispatchers.Main) {
                        dataAgendadaProximaDose = existing?.dataAgendada

                        // ⭐️ LOG 4: Estado da Próxima Dose lido do DB/Cache
                        Log.d(TAG, "LOG 4 | Leitura do DB para Próxima Dose (Key: $nextKey): Status: ${existing?.status}, Data: ${existing?.dataAgendada}")

                        btnAgendarProxima.isEnabled = true
                        btnAgendarProxima.visibility = View.VISIBLE
                        updateNextDoseButtonText(dataAgendadaProximaDose)
                    }
                }
                .onFailure { e ->
                    Log.e(TAG, "Erro ao carregar agendamento futuro: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        btnAgendarProxima.isEnabled = true
                        btnAgendarProxima.visibility = View.VISIBLE
                        updateNextDoseButtonText(null)
                    }
                }
        }
    }

    private fun updateNextDoseButtonText(date: String?) {
        btnAgendarProxima.text = if (!date.isNullOrEmpty()) {
            "PRÓXIMA DOSE AGENDADA: $date (Clique para mudar)"
        } else {
            "Agendar Próxima Dose"
        }
    }

    private fun showDatePicker(editText: EditText, allowClear: Boolean) {
        val c = Calendar.getInstance()
        val dp = DatePickerDialog(
            this,
            { _, y, m, d ->
                editText.setText(formatDate(d, m + 1, y))
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        )

        if (allowClear) {
            dp.setButton(DatePickerDialog.BUTTON_NEGATIVE, "Limpar/Cancelar Aplicação") { _, _ ->
                editText.setText("")
                clearApplicationFields()
                Toast.makeText(this, "Data e dados de Aplicação removidos. Status será Cancelado.", Toast.LENGTH_SHORT).show()
            }
        }

        dp.show()
    }

    // ---------------- Lógica de Agendamento Imediato ----------------

    /**
     * @brief Abre o DatePicker. Após a seleção, salva o agendamento imediatamente.
     */
    private fun showDatePickerForNextDose() {
        val c = Calendar.getInstance()
        val dp = DatePickerDialog(
            this,
            { _, y, m, d ->
                val selected = formatDate(d, m + 1, y)
                dataAgendadaProximaDose = selected
                updateNextDoseButtonText(selected)
                Toast.makeText(this, "Agendada para $selected", Toast.LENGTH_SHORT).show()

                // ⭐️ LOG 1: Data de Agendamento Selecionada
                Log.d(TAG, "LOG 1 | Agendamento de Próxima Dose selecionado e salvo na memória: $selected")

                // 🛑 FLUXO CORRETO: Salva a próxima dose imediatamente
                saveNextDoseScheduling(selected)
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        )

        dp.setButton(DatePickerDialog.BUTTON_NEGATIVE, "Remover Agendamento") { _, _ ->
            removeNextDoseScheduling()
        }

        dp.show()
    }

    /**
     * @brief Salva a próxima dose com o status AGENDADA/PENDENTE imediatamente.
     */
    private fun saveNextDoseScheduling(dataAgendada: String?) {
        val next = VaccineScheduler.getNextDose(nomeVacina, doseVacina) ?: return
        val nextKey = generateVacinaKey(next.vacinaNome, next.doseNome)

        // Se há data, o status é AGENDADA. Se não, é PENDENTE (remoção/não agendada).
        val newStatus = if (dataAgendada != null) STATUS_AGENDADA else STATUS_PENDENTE

        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                // 1. Busca a dose existente ou cria uma base
                val existing = vacinaDao.getVacinaDose(cnsPaciente, nextKey)

                // Define a dose base. Se já existe, usa os dados existentes.
                val doseToSave = (existing ?: VacinaDose(
                    cnsIndividuo = cnsPaciente,
                    vacinaKey = nextKey,
                    nomeVacina = next.vacinaNome,
                    dose = next.doseNome,
                    status = STATUS_PENDENTE, // Default status
                    dataAplicacao = null
                )).copy(
                    status = newStatus, // Status corrigido: AGENDADA
                    dataAgendada = dataAgendada,
                    isSynchronized = false,
                    ultimaAtualizacao = System.currentTimeMillis()
                )

                // 2. Salva/Atualiza o status e data (força o AGENDADA)
                vacinaDao.saveOrUpdate(doseToSave)
                firebaseSyncManager.syncVacinaDose(doseToSave)

                // ⭐️ LOG 5: Confirma o Salvamento Imediato
                Log.d(TAG, "LOG 5 | SALVAMENTO IMEDIATO (Next Dose): Status: ${doseToSave.status}, Data: ${doseToSave.dataAgendada}")
            }

            withContext(Dispatchers.Main) {
                if (result.isFailure) {
                    Log.e(TAG, "Erro ao salvar agendamento: ${result.exceptionOrNull()?.message}", result.exceptionOrNull())
                }
            }
        }
    }

    private fun removeNextDoseScheduling() {
        dataAgendadaProximaDose = null
        updateNextDoseButtonText(null)

        // 🛑 NOVO FLUXO: Chama a função de salvamento com data nula para forçar STATUS_PENDENTE
        saveNextDoseScheduling(null)
        Toast.makeText(this@FormularioVacinaActivity, "Agendamento futuro removido/cancelado.", Toast.LENGTH_SHORT).show()
    }

    private fun formatDate(day: Int, month: Int, year: Int): String {
        return String.format(Locale.getDefault(), "%02d/%02d/%d", day, month, year)
    }

    private fun clearApplicationFields() {
        editLote.setText("")
        editLabProdut.setText("")
        editUnidade.setText("")
        editAssinaturaAcs.setText("")
    }

    // ---------------- Sequência de doses / Chave ----------------
    /**
     * ⭐️ CHAVE UNIFICADA E CORRIGIDA: Usa replace("ª", "") e remove acentos para consistência.
     */
    private fun generateVacinaKey(nome: String, dose: String): String {
        val keyNome = nome.uppercase(Locale.ROOT).replace(" ", "_").replace("/", "_").replace("-", "_")
        val keyDose = dose.uppercase(Locale.ROOT).replace(" ", "_").replace("Ã", "A").replace("ª", "").replace("Á", "A").replace("É", "E").replace("Í", "I").replace("Ó", "O").replace("Ú", "U")
        return "${keyNome}_${keyDose}"
    }

    // ---------------- Salvamento / Sincronização (Lógica de Dose Futura Restaurada) ----------------
    private fun saveVacinaData() {
        val inputDataAplicacao = editDataAplicacao.text.toString().trim()
        val isAplicada = inputDataAplicacao.isNotEmpty()

        val inputLote = editLote.text.toString().trim()
        val inputLabProdut = editLabProdut.text.toString().trim()
        val inputUnidade = editUnidade.text.toString().trim()
        val inputAssinaturaAcs = editAssinaturaAcs.text.toString().trim()

        if (!validateBeforeSave(isAplicada, inputLote, inputLabProdut, inputUnidade, inputAssinaturaAcs)) return
        if (cnsPaciente.isEmpty()) {
            Toast.makeText(this, "Erro: CNS do Paciente não encontrado.", Toast.LENGTH_LONG).show()
            return
        }

        val doseAtual = buildDoseAtual(isAplicada, inputDataAplicacao, inputLote, inputLabProdut, inputUnidade, inputAssinaturaAcs)
        val next = VaccineScheduler.getNextDose(nomeVacina, doseVacina)

        var nextDoseToSave: VacinaDose? = null
        var nextDoseKey: String? = null

        if (next != null && isAplicada) { // 🛑 SOMENTE SE A DOSE ATUAL FOR APLICADA
            // ⭐️ Usa a chave unificada
            nextDoseKey = generateVacinaKey(next.vacinaNome, next.doseNome)

            // Prepara a próxima dose baseada no estado do botão de agendamento (memória)
            nextDoseToSave = VacinaDose(
                cnsIndividuo = cnsPaciente,
                vacinaKey = nextDoseKey,
                nomeVacina = next.vacinaNome,
                dose = next.doseNome,
                // O status é derivado da variável de estado 'dataAgendadaProximaDose'
                status = if (dataAgendadaProximaDose != null) STATUS_AGENDADA else STATUS_PENDENTE,
                dataAgendada = dataAgendadaProximaDose, // Estado salvo pelo DatePicker ou carregado
                dataAplicacao = null,
                isSynchronized = false,
                ultimaAtualizacao = System.currentTimeMillis()
            )
            // ⭐️ LOG 2: Status calculado ANTES de salvar (se btnSalvar foi clicado)
            Log.d(TAG, "LOG 2 | (saveVacinaData) Dose Futura: Status CALCULADO (Será salvo): ${nextDoseToSave.status}, Data: ${nextDoseToSave.dataAgendada}")
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                // A. Salva/Atualiza a dose atual
                vacinaDao.saveOrUpdate(doseAtual)

                // B. Lógica de Próxima Dose
                if (nextDoseKey != null) {
                    when {
                        // 1. A dose atual foi aplicada, salvar/atualizar a próxima dose (Pendente/Agendada)
                        nextDoseToSave != null -> {
                            vacinaDao.saveOrUpdate(nextDoseToSave)
                        }

                        // 2. A dose atual foi CANCELADA e existe um agendamento futuro
                        doseAtual.status == STATUS_CANCELADO -> {
                            val existingNext = vacinaDao.getVacinaDose(cnsPaciente, nextDoseKey)
                            if (existingNext != null) {
                                val canceled = existingNext.copy(
                                    status = STATUS_CANCELADO,
                                    dataAgendada = null,
                                    isSynchronized = false,
                                    ultimaAtualizacao = System.currentTimeMillis()
                                )
                                vacinaDao.saveOrUpdate(canceled)
                                firebaseSyncManager.syncVacinaDose(canceled)
                                // ⭐️ LOG 3-CANCEL: Confirma cancelamento
                                Log.d(TAG, "LOG 3-CANCEL | Próxima Dose CANCELADA devido ao cancelamento da Dose Atual.")
                            }
                        }
                    }
                }

                // C. Sincroniza todas as doses pendentes para este paciente
                firebaseSyncManager.syncPendingVacinas(cnsPaciente)
            }

            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val message = when (doseAtual.status) {
                        STATUS_CANCELADO -> "Agendamento da dose atual CANCELADO. Agendamento futuro também cancelado."
                        STATUS_APLICADA -> "Registro salvo com sucesso! ${next?.doseNome ?: ""} ${next?.vacinaNome ?: ""} agendada/pendente."
                        else -> "Agendamento atualizado com sucesso."
                    }
                    Toast.makeText(this@FormularioVacinaActivity, message, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Log.e(TAG, "Erro fatal ao salvar o registro: ${result.exceptionOrNull()?.message}", result.exceptionOrNull())
                    Toast.makeText(this@FormularioVacinaActivity, "Erro fatal ao salvar o registro.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun validateBeforeSave(
        isAplicada: Boolean,
        lote: String,
        lab: String,
        unidade: String,
        assinatura: String
    ): Boolean {
        val hasPartialData = lote.isNotEmpty() || lab.isNotEmpty() || unidade.isNotEmpty() || assinatura.isNotEmpty()

        if (isAplicada) {
            return true
        }

        if (!isAplicada && hasPartialData) {
            Toast.makeText(this, "Para manter como AGENDADA ou CANCELAR a aplicação, remova todos os dados de aplicação (Lote, Laboratório, Unidade e Assinatura).", Toast.LENGTH_LONG).show()
            return false
        }

        return true
    }

    private fun buildDoseAtual(
        isAplicada: Boolean,
        dataAplicacao: String,
        lote: String,
        lab: String,
        unidade: String,
        assinatura: String
    ): VacinaDose {
        return VacinaDose(
            cnsIndividuo = cnsPaciente,
            vacinaKey = vacinaKey,
            nomeVacina = nomeVacina,
            dose = doseVacina,
            status = if (isAplicada) STATUS_APLICADA else STATUS_CANCELADO,
            dataAplicacao = if (isAplicada) dataAplicacao else null,
            lote = if (isAplicada) lote else null,
            labProdut = if (isAplicada) lab else null,
            unidade = if (isAplicada) unidade else null,
            assinaturaAcs = if (isAplicada) assinatura else null,
            dataAgendada = null, // A dose atual não é agendada
            isSynchronized = false,
            ultimaAtualizacao = System.currentTimeMillis()
        )
    }
}

/**
 * VaccineScheduler - objeto responsável por decidir qual é a próxima dose. (Inalterado)
 */
private object VaccineScheduler {

    data class NextDose(val doseNome: String, val vacinaNome: String)

    fun getNextDose(currentVacinaName: String, currentDose: String): NextDose? {
        // ... (Lógica de Sequência de Doses idêntica à fornecida no código original) ...
        val dose = currentDose.trim().lowercase(Locale.ROOT)
        val vacina = currentVacinaName.trim().lowercase(Locale.ROOT)

        fun isDose(number: Int): Boolean {
            val variants = listOf("${number}ª dose", "${number}a dose", "dose $number", "dose$number", "dose $number completa")
            return variants.any { dose.contains(it) }
        }

        // Regras para considerar dose final (não há próxima dose)
        val isFinalDose = listOf("única", "unica", "ao nascer", "nascimento", "uma dose", "dose final", "3º reforço")
            .any { dose.contains(it) }
                || (vacina.contains("rotavírus") && isDose(2))
                || (vacina.contains("pneumocócica") && dose.contains("reforço")) // 2ª Dose ou Reforço pode ser final
                || (vacina.contains("meningocócica c (conjugada)") && dose.contains("reforço"))
                || (vacina.contains("hpv") && isDose(2))
                || dose.contains("2º reforço")

        if (isFinalDose) return null

        // Regras por vacina
        return when (vacina) {
            "penta" -> when {
                isDose(1) -> NextDose("2ª Dose", "Penta")
                isDose(2) -> NextDose("3ª Dose", "Penta")
                isDose(3) -> NextDose("1º Reforço", "DTP") // Muda de nome
                else -> null
            }
            "dtp" -> when {
                dose.contains("1º reforço") -> NextDose("2º Reforço", currentVacinaName)
                else -> null
            }
            "vip" -> when {
                isDose(1) -> NextDose("2ª Dose", "VIP")
                isDose(2) -> NextDose("3ª Dose", "VIP")
                isDose(3) -> NextDose("1º Reforço", "VOP") // Muda de nome
                else -> null
            }
            "hpv" -> when {
                isDose(1) -> NextDose("2ª Dose", currentVacinaName) // A próxima dose é calculada
                else -> null
            }
            "covid-19", "campanha/outra" -> when {
                isDose(1) -> NextDose("2ª Dose", currentVacinaName)
                isDose(2) -> NextDose("3ª Dose", currentVacinaName)
                isDose(3) -> NextDose("4ª Dose", currentVacinaName)
                else -> null
            }
            "rotavírus humano", "pneumocócica 10v (conjugada)" -> when {
                isDose(1) -> NextDose("2ª Dose", currentVacinaName)
                else -> null
            }
            "meningocócica c (conjugada)" -> when {
                isDose(1) -> NextDose("2ª Dose", currentVacinaName)
                isDose(2) -> NextDose("Reforço", currentVacinaName)
                else -> null
            }
            "vop" -> when {
                dose.contains("1º reforço") -> NextDose("2º Reforço", "VOP")
                else -> null
            }
            else -> null
        }
    }
}