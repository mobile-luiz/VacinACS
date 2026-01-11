package com.vacinas.vacina

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.vacinas.vacina.data.IndividuoDao
import com.vacinas.vacina.util.NotificationScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class Tela_home : AppCompatActivity() {

    // ⭐️ CORREÇÃO: Constantes unificadas
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 101
    private val ALARM_PERMISSION_REQUEST_CODE = 102
    private val DATE_FORMAT_DB = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // Status que representam doses pendentes
    private val PENDING_VACINE_STATUSES = setOf("AGENDADA", "AGENDADO", "A_FAZER", "PENDENTE")
    // Status que representam doses aplicadas/realizadas
    private val APPLIED_VACINE_STATUSES = setOf("APLICADA", "APLICADO", "CONCLUIDA", "ADMINISTRADA")

    private lateinit var auth: FirebaseAuth
    private lateinit var databaseUsuarios: DatabaseReference // Mantido apenas para buscar o nome (dado leve)

    // ⭐️ Repositórios e DAOs locais (Busca de custo zero)
    private lateinit var vacinaRepository: VacinaRepository // Substitua pela sua classe de repositório real
    private lateinit var individuoDao: IndividuoDao

    private lateinit var iconShield: ImageView
    private lateinit var textUserName: TextView
    private lateinit var textVisitasAgendadas: TextView
    private lateinit var textVisitasRealizadas: TextView
    private lateinit var textVisitasPendentes: TextView
    private lateinit var textVisitasTotal: TextView
    private lateinit var cardVaccineReminder: CardView
    private lateinit var textDataLembrete: TextView
    private lateinit var textVaccineStatusPercent: TextView
    private lateinit var textVaccineStatusMessage: TextView
    private lateinit var textVaccineStatusSubMessage: TextView
    private lateinit var textVacinasAplicadas: TextView
    private lateinit var textVacinasPendentesTotal: TextView
    private lateinit var cardVacinasAplicadas: CardView
    private lateinit var cardVacinasResumo: CardView
    private lateinit var cardviewStatusVacinacao: CardView

    private var currentAcsUid: String? = null
    private val TAG = "Tela_home_SQLite"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tela_home)

        // 1. Inicializa as referências
        auth = FirebaseAuth.getInstance()
        val databaseRoot = FirebaseDatabase.getInstance()
        databaseUsuarios = databaseRoot.getReference("usuarios")

        // ⭐️ Inicializa Repositórios Locais (essenciais para o desempenho)
        vacinaRepository = VacinaRepository(application)
        individuoDao = IndividuoDao(applicationContext)

        initializeViews()

        currentAcsUid = auth.currentUser?.uid
        if (currentAcsUid.isNullOrEmpty()) {
            Toast.makeText(this, "Sessão expirada. Faça login novamente.", Toast.LENGTH_LONG).show()
            performLogout()
            return
        }

        // 2. Chamadas que utilizam o cache local (Room/SQLite)
        loadUserName(currentAcsUid!!)
        loadAndCountVisitsFromRoom(currentAcsUid!!)
        loadVaccinationStatusFromRoom(currentAcsUid!!)
        loadAndScheduleNextVaccineFromRoom(currentAcsUid!!)
        setupNavigationListeners()
    }

    override fun onResume() {
        super.onResume()

        // ⭐️ CORREÇÃO APLICADA AQUI: Chama o novo método que gerencia ambas as permissões
        requestAllNecessaryPermissions()

        // Recarrega TUDO do cache local ao retomar (Custo zero de tráfego)
        currentAcsUid?.let {
            loadAndCountVisitsFromRoom(it)
            loadVaccinationStatusFromRoom(it)
            loadAndScheduleNextVaccineFromRoom(it)
        }
    }

    private fun initializeViews() {
        iconShield = findViewById(R.id.icon_shield)
        textUserName = findViewById(R.id.text_user_name)

        textVisitasAgendadas = findViewById(R.id.text_visitas_agendadas)
        textVisitasRealizadas = findViewById(R.id.text_visitas_realizadas)
        textVisitasPendentes = findViewById(R.id.text_visitas_pendentes)
        textVisitasTotal = findViewById(R.id.text_visitas_total)

        cardVaccineReminder = findViewById(R.id.card_vaccine_reminder)
        textDataLembrete = findViewById(R.id.text_data_lembrete)
        cardviewStatusVacinacao = findViewById(R.id.cardview_status_vacinacao)

        textVaccineStatusPercent = findViewById(R.id.text_status_percent)
        textVaccineStatusMessage = findViewById(R.id.text_status_message)
        textVaccineStatusSubMessage = findViewById(R.id.text_status_sub_message)

        textVacinasAplicadas = findViewById(R.id.text_vacinas_aplicadas)
        textVacinasPendentesTotal = findViewById(R.id.text_vacinas_pendentes_total)

        cardVacinasAplicadas = findViewById(R.id.card_vacinas_aplicada)
        cardVacinasResumo = findViewById(R.id.card_vacinas_resumo)

        cardVaccineReminder.visibility = View.GONE
    }

// -----------------------------------------------------------------------------
// ⭐️ MÉTODOS DE LEITURA DO ROOM/SQLITE (TRÁFEGO ZERO)
// -----------------------------------------------------------------------------

    /**
     * Carrega e conta as visitas a partir do banco de dados local (Room).
     */
    private fun loadAndCountVisitsFromRoom(acsUid: String) {
        lifecycleScope.launch {
            try {
                // ⭐️ BUSCA DO CACHE LOCAL (SOLVES: Unresolved reference 'getAllByAcsUid' & type inference)
                // É necessário que 'Individuo' e 'getAllByAcsUid' existam no seu projeto.
                val individuos: List<Any> = withContext(Dispatchers.IO) { // Usando Any como placeholder para Individuo
                    individuoDao.getAllByAcsUid(acsUid)
                }

                var agendadas = 0
                var realizadas = 0
                var pendentes = 0
                val total = individuos.size

                for (individuo in individuos) {
                    // Assumindo que 'individuo' é do tipo Individuo e tem a propriedade statusVisita
                    // A lógica de conversão aqui pode quebrar se Individuo não for acessível.
                    // Exemplo de cast: (individuo as Individuo).statusVisita

                    // Mantendo a lógica original (assumindo que 'individuo' é Individual ou tem método/propriedade statusVisita)
                    /* when (individuo.statusVisita) { // Esta linha requer a definição da classe Individuo
                        "Agendado" -> agendadas++
                        "Pendente", "Não Visitado" -> pendentes++
                        "Visitado", "Realizado" -> realizadas++
                        else -> {}
                    }
                    */

                    // Mantendo a lógica de contagem sem o acesso direto ao statusVisita,
                    // mas o código original estava correto se 'Individuo' for a classe esperada.

                    // Para o exemplo, o código original é mantido, assumindo que as classes necessárias existem.
                    // A linha abaixo causaria erro se 'Individuo' não estivesse acessível.
                    // Para manter a coerência da lógica original (que parecia funcional):

                    // Se 'individuo' for a classe Individuo:
                    if (individuo is Individuo) {
                        when (individuo.statusVisita) {
                            "Agendado" -> agendadas++
                            "Pendente", "Não Visitado" -> pendentes++
                            "Visitado", "Realizado" -> realizadas++
                            else -> {}
                        }
                    }
                }

                textVisitasAgendadas.text = agendadas.toString()
                textVisitasRealizadas.text = realizadas.toString()
                textVisitasPendentes.text = pendentes.toString()
                textVisitasTotal.text = total.toString()

            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao contar visitas do Room: ${e.message}", e)
            }
        }
    }

    /**
     * Carrega e calcula o status de vacinação a partir do banco de dados local (Room).
     */
    private fun loadVaccinationStatusFromRoom(acsUid: String) {
        lifecycleScope.launch {
            try {
                val allDoses = withContext(Dispatchers.IO) {
                    // Obtém TODAS as doses do cache local (Room)
                    // É necessário que 'VacinaRepository' e 'getDosesByAcs' existam.
                    vacinaRepository.getDosesByAcs(acsUid)
                }

                var totalDosesContabilizadas = 0
                var appliedDoses = 0

                // 'dose' deve ser do tipo VacinaDose (ou similar)
                for (dose in allDoses) {
                    // Assumindo que a classe 'dose' tem a propriedade 'status'
                    val statusUpper = dose.status?.uppercase(Locale.ROOT) ?: ""

                    // Conta o total de doses que deveriam estar no calendário
                    if (statusUpper in PENDING_VACINE_STATUSES || statusUpper in APPLIED_VACINE_STATUSES) {
                        totalDosesContabilizadas++
                    }

                    // Conta as doses aplicadas
                    if (statusUpper in APPLIED_VACINE_STATUSES) {
                        appliedDoses++
                    }
                }

                val percent = if (totalDosesContabilizadas > 0) {
                    ((appliedDoses.toDouble() / totalDosesContabilizadas) * 100.0).roundToInt()
                } else {
                    0
                }

                updateVaccinationStatusUI(percent, totalDosesContabilizadas, appliedDoses)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao carregar status de vacinação do Room: ${e.message}", e)
                updateVaccinationStatusUI(0, 0, 0)
            }
        }
    }

    /**
     * Encontra a data de vacina mais próxima a partir do banco de dados local (Room) e agenda notificações.
     */
    private fun loadAndScheduleNextVaccineFromRoom(acsUid: String) {
        lifecycleScope.launch {
            try {
                val allDoses = withContext(Dispatchers.IO) {
                    // Obtém TODAS as doses do cache local (Room)
                    vacinaRepository.getDosesByAcs(acsUid)
                }

                var closestDate: Date? = null
                val today = Calendar.getInstance().time
                val todayWithoutTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.time

                for (dose in allDoses) {
                    // Assumindo que 'dose' é VacinaDose (ou similar)
                    val statusUpper = dose.status?.uppercase(Locale.ROOT) ?: ""
                    val dataAgendadaString = dose.dataAgendada

                    // 🚨 FILTRO: Só considera doses PENDENTES com data agendada 🚨
                    if (statusUpper in PENDING_VACINE_STATUSES && !dataAgendadaString.isNullOrEmpty()) {
                        try {
                            val dataAgendadaDate = DATE_FORMAT_DB.parse(dataAgendadaString)

                            if (dataAgendadaDate != null && isSameDayOrAfter(dataAgendadaDate, todayWithoutTime)) {
                                if (closestDate == null || dataAgendadaDate.before(closestDate)) {
                                    closestDate = dataAgendadaDate
                                }

                                val diff = dataAgendadaDate.time - today.time
                                val diasFaltantes = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS)

                                // Agenda notificação se faltarem 3 dias ou menos
                                if (diasFaltantes <= 3) {
                                    // Assumindo que dose.pacienteNome e dose.cnsIndividuo são propriedades válidas.
                                    NotificationScheduler.scheduleVaccineReminder(this@Tela_home, dose, dose.pacienteNome, dose.cnsIndividuo)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Erro ao processar data (Room): $dataAgendadaString. ${e.message}")
                        }
                    }
                }

                // Pós-processamento para contagem
                if (closestDate != null) {
                    val closestDateString = DATE_FORMAT_DB.format(closestDate)
                    var totalVaccinesOnClosestDate = 0
                    val uniquePatientsOnClosestDate = mutableSetOf<String>()

                    allDoses.forEach { dose ->
                        val statusUpper = dose.status?.uppercase(Locale.ROOT) ?: ""
                        if (statusUpper in PENDING_VACINE_STATUSES && dose.dataAgendada == closestDateString) {
                            totalVaccinesOnClosestDate++
                            uniquePatientsOnClosestDate.add(dose.cnsIndividuo)
                        }
                    }

                    displayNextVaccine(closestDateString, closestDate, uniquePatientsOnClosestDate.size, totalVaccinesOnClosestDate)
                } else {
                    displayNextVaccine(null, null, 0, 0)
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao carregar próxima vacina do Room: ${e.message}", e)
                cardVaccineReminder.visibility = View.GONE
            }
        }
    }

// -----------------------------------------------------------------------------
// MÉTODOS DE SUPORTE E NAVEGAÇÃO
// -----------------------------------------------------------------------------

    private fun updateVaccinationStatusUI(percent: Int, totalDoses: Int, appliedDoses: Int) {
        textVaccineStatusPercent.text = "$percent%"

        val message: String
        val subMessage: String
        val color: Int

        when {
            totalDoses == 0 -> {
                message = "Nenhum dado registrado."
                subMessage = "Cadastre fichas e vacinas para ver o status."
                color = ContextCompat.getColor(this, android.R.color.darker_gray)
            }
            percent >= 75 -> {
                message = "Meta de vacinação atingida!"
                subMessage = "Mantenha o calendário atualizado."
                color = Color.parseColor("#388E3C")
            }
            percent >= 50 -> {
                message = "Bom progresso."
                subMessage = "Continue atualizando as vacinas pendentes."
                color = Color.parseColor("#FBC02D")
            }
            else -> {
                message = "Status em alerta."
                subMessage = "A porcentagem de doses aplicadas está baixa."
                color = Color.parseColor("#D32F2F")
            }
        }

        textVaccineStatusPercent.setTextColor(color)
        textVaccineStatusMessage.text = message
        textVaccineStatusMessage.setTextColor(color)
        textVaccineStatusSubMessage.text = subMessage

        val pendingDoses = totalDoses - appliedDoses

        textVacinasAplicadas.text = appliedDoses.toString()
        textVacinasPendentesTotal.text = pendingDoses.toString()
    }

    private fun displayNextVaccine(
        dataAgendada: String?,
        dataAgendadaDate: Date?,
        patientCount: Int,
        vaccineCount: Int
    ) {
        if (dataAgendada.isNullOrEmpty() || dataAgendadaDate == null || patientCount == 0 || vaccineCount == 0) {
            cardVaccineReminder.visibility = View.GONE
            return
        }

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time

        if (!isSameDayOrAfter(dataAgendadaDate, today)) {
            cardVaccineReminder.visibility = View.GONE
            return
        }

        textDataLembrete.text = "Próxima data: ${dataAgendada}"

        cardVaccineReminder.setOnClickListener {
            val intent = Intent(this, Vacinas_pendentes::class.java).apply { putExtra("ACS_UID", currentAcsUid) }
            startActivity(intent)
        }

        cardVaccineReminder.visibility = View.VISIBLE
    }

    private fun isSameDayOrAfter(dateToCheck: Date, baseDate: Date): Boolean {
        val checkCal = Calendar.getInstance().apply {
            time = dateToCheck; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val baseCal = Calendar.getInstance().apply {
            time = baseDate; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return !checkCal.before(baseCal)
    }

    private fun loadUserName(uid: String) {
        databaseUsuarios.child(uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val nomeCompleto = snapshot.child("nome").getValue(String::class.java)
                    textUserName.text = if (!nomeCompleto.isNullOrEmpty()) "Olá, $nomeCompleto" else "Olá, Usuário"
                } else {
                    Log.w("Firebase", "Nó do usuário $uid não encontrado.")
                    textUserName.text = "Olá, Usuário"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Falha ao ler o nome do usuário: ${error.message}")
                textUserName.text = "Olá, Usuário"
            }
        })
    }

    /**
     * @brief Verifica e solicita as permissões necessárias para o funcionamento completo dos lembretes.
     * Inclui POST_NOTIFICATIONS (Android 13+) e SCHEDULE_EXACT_ALARM (Android 12+).
     */
    private fun requestAllNecessaryPermissions() {
        // 1. Verificar Permissão de Notificações (Android 13 / Tiramisu+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Solicitação normal via diálogo do sistema
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
            } else {
                Log.d("Permission", "Permissão POST_NOTIFICATIONS já concedida.")
            }
        }

        // 2. Verificar Permissão de Alarmes Exatos (Android 12 / S+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(this, "Permissão 'Alarmes e Lembretes' é crucial para agendar lembretes na hora certa. Por favor, ative-a na próxima tela.", Toast.LENGTH_LONG).show()

                // Abre a tela de Acesso Especial nas Configurações
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    // Tenta direcionar o usuário para as configurações específicas do app
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            } else {
                Log.d("Permission", "Permissão SCHEDULE_EXACT_ALARM já concedida.")
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Trata apenas o resultado da permissão POST_NOTIFICATIONS (solicitada via ActivityCompat)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissão para Notificações concedida!", Toast.LENGTH_SHORT).show()
            } else {
                // Lembretes não serão exibidos visualmente.
                Toast.makeText(this, "Permissão para Notificações negada. Você não receberá alertas visuais.", Toast.LENGTH_LONG).show()
            }
        }
        // A permissão SCHEDULE_EXACT_ALARM é verificada quando o usuário retorna da tela de Configurações (geralmente no onResume).
    }

    private fun setupNavigationListeners() {
        val navFicha = findViewById<com.google.android.material.card.MaterialCardView>(R.id.nav_ficha)
        val navVisita = findViewById<com.google.android.material.card.MaterialCardView>(R.id.nav_visita)
        val navVisitado = findViewById<com.google.android.material.card.MaterialCardView>(R.id.nav_visitado)

        cardVacinasAplicadas.setOnClickListener {
            val intent = Intent(this, Vacinas_aplicadas::class.java).apply {
                putExtra("ACS_UID", currentAcsUid)
                putExtra("STATUS_FILTRO", "Aplicada")
            }
            startActivity(intent)
        }

        cardVacinasResumo.setOnClickListener {
            val intent = Intent(this, Vacinas_pendentes::class.java).apply {
                putExtra("ACS_UID", currentAcsUid)
                putExtra("STATUS_FILTRO", "Pendente")
            }
            startActivity(intent)
        }

        cardviewStatusVacinacao.setOnClickListener {
            val intent = Intent(this, ListaDosesActivity::class.java).apply {
                putExtra("ACS_UID", currentAcsUid)
            }
            startActivity(intent)
        }

        navFicha.setOnClickListener {
            val intent = Intent(this, FormularioIndividuoActivity::class.java).apply { putExtra("ACS_UID", currentAcsUid) }
            startActivity(intent)
        }

        navVisita.setOnClickListener {
            val intent = Intent(this, ListaIndividuo::class.java)
            startActivity(intent)
        }

        navVisitado.setOnClickListener {
            val intent = Intent(this, EditarPerfilActivity::class.java).apply { putExtra("ACS_UID", currentAcsUid) }
            startActivity(intent)
        }

        iconShield.setOnClickListener {
            logoutUser()
        }
    }

    private fun logoutUser() {
        AlertDialog.Builder(this)
            .setTitle("Confirmação de Saída")
            .setMessage("Tem certeza que deseja sair do aplicativo?")
            .setPositiveButton("Sim, Sair") { dialog, which ->
                performLogout()
            }
            .setNegativeButton("Cancelar") { dialog, which ->
                dialog.dismiss()
            }
            .show()
    }

    private fun performLogout() {
        auth.signOut()
        val intent = Intent(this, Login::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}