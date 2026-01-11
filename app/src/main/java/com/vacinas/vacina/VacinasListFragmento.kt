package com.vacinas.vacina.ui.vacinas

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.vacinas.vacina.FormularioVacinaActivity
import com.vacinas.vacina.R
import com.vacinas.vacina.VacinaDose
import com.vacinas.vacina.data.VacinaDao
import com.vacinas.vacina.ui.vacinas.adapter.VacinaAdapter
import com.vacinas.vacina.util.NotificationScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import android.content.Context
import java.util.Locale

class VacinasListFragmento : Fragment(), VacinaAdapter.VacinaItemClickListener {

    // ⭐️ Interface para comunicação com a Activity
    interface DataPassListener {
        fun onVacinasLoaded(nomePaciente: String, allMergedDoses: List<VacinaDose>)
    }

    private var listener: DataPassListener? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyTextView: TextView
    private var cnsPaciente: String = ""
    private var categoria: String = ""
    private var pacienteNome: String = "Paciente Desconhecido"

    private lateinit var vacinaDao: VacinaDao

    private val database = FirebaseDatabase.getInstance()
    private val individuosRef = database.getReference("individuos")
    private val TAG = "VacinasFragmento"

    companion object {
        private const val ARG_CNS = "cns"
        private const val ARG_CATEGORY = "category"

        fun newInstance(cns: String, category: String): VacinasListFragmento {
            val fragment = VacinasListFragmento()
            fragment.arguments = Bundle().apply {
                putString(ARG_CNS, cns)
                putString(ARG_CATEGORY, category)
            }
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is DataPassListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement DataPassListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            cnsPaciente = it.getString(ARG_CNS) ?: ""
            categoria = it.getString(ARG_CATEGORY) ?: "ATE_12_MESES"
        }
        context?.let {
            vacinaDao = VacinaDao(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_vacinas_list_fragmento, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recycler_vacinas_list)
        emptyTextView = view.findViewById(R.id.text_empty_list)

        Log.d(TAG, "Carregando Categoria: $categoria para CNS: $cnsPaciente")

        if (cnsPaciente.isNotEmpty()) {
            loadVacinas()
        } else {
            emptyTextView.text = "Erro: CNS do paciente não encontrado."
            emptyTextView.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        if (cnsPaciente.isNotEmpty()) {
            loadVacinas()
        }
    }

    // -------------------------------------------------------------------------
    // LÓGICA DE CARREGAMENTO (OFFLINE FIRST)
    // -------------------------------------------------------------------------

    private fun loadVacinas() {
        lifecycleScope.launch(Dispatchers.IO) {
            val listaVacinasSalvasLocal = try {
                vacinaDao.getVacinasByCns(cnsPaciente)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar vacinas do SQLite: ${e.message}")
                emptyList<VacinaDose>()
            }

            withContext(Dispatchers.Main) {
                if (listaVacinasSalvasLocal.isNotEmpty()) {
                    Log.d(TAG, "Carregando ${listaVacinasSalvasLocal.size} doses do SQLite (Modo Offline/Cache).")

                    val calendarioPadrao = createMockVacinaDoseList(cnsPaciente, "TODAS")
                    // ⭐️ CHAMADA DO MERGE CORRIGIDO:
                    val listaFinalMesclada = mergeVacinas(calendarioPadrao, listaVacinasSalvasLocal)

                    listener?.onVacinasLoaded(pacienteNome, listaFinalMesclada)

                    val listaFiltrada = filterVacinasByCategory(listaFinalMesclada, categoria)

                    scheduleRemindersForList(listaFiltrada)
                    setupRecyclerView(listaFiltrada)

                    fetchVacinasFromFirebase(updateOnly = true)

                } else {
                    Log.d(TAG, "SQLite vazio. Tentando buscar no Firebase...")
                    emptyTextView.text = "Buscando dados no servidor..."
                    fetchVacinasFromFirebase(updateOnly = false)
                }
            }
        }
    }

    private fun fetchVacinasFromFirebase(updateOnly: Boolean) {
        individuosRef.child(cnsPaciente)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        if (!updateOnly) {
                            emptyTextView.text = "Paciente não encontrado no Firebase."
                            emptyTextView.visibility = View.VISIBLE
                        }
                        return
                    }

                    pacienteNome = snapshot.child("nome").getValue(String::class.java) ?: "Paciente Desconhecido"
                    val vacinasSnapshot = snapshot.child("vacinas")
                    val listaVacinasSalvas = mutableListOf<VacinaDose>()

                    if (vacinasSnapshot.exists()) {
                        for (childSnapshot in vacinasSnapshot.children) {
                            val dose = parseVacinaDose(childSnapshot)
                            listaVacinasSalvas.add(dose)

                            // Salva no SQLite para Cache
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    val dbKey = childSnapshot.key ?: dose.vacinaKey
                                    val doseToSave = dose.copy(
                                        cnsIndividuo = cnsPaciente,
                                        vacinaKey = dbKey,
                                        isSynchronized = true,
                                        ultimaAtualizacao = System.currentTimeMillis()
                                    )
                                    vacinaDao.saveOrUpdate(doseToSave)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Falha ao salvar dose do Firebase no SQLite: ${e.message}")
                                }
                            }
                        }

                        val calendarioPadrao = createMockVacinaDoseList(cnsPaciente, "TODAS")
                        // ⭐️ CHAMADA DO MERGE CORRIGIDO:
                        val listaFinalMesclada = mergeVacinas(calendarioPadrao, listaVacinasSalvas)

                        listener?.onVacinasLoaded(pacienteNome, listaFinalMesclada)

                        val listaFiltrada = filterVacinasByCategory(listaFinalMesclada, categoria)

                        scheduleRemindersForList(listaFiltrada)
                        setupRecyclerView(listaFiltrada)

                    } else if (!updateOnly) {
                        Log.d(TAG, "Nenhuma vacina salva. Carregando calendário padrão.")
                        val listaMockTotal = createMockVacinaDoseList(cnsPaciente, "TODAS")

                        listener?.onVacinasLoaded(pacienteNome, listaMockTotal)

                        val listaFiltrada = filterVacinasByCategory(listaMockTotal, categoria)

                        scheduleRemindersForList(listaFiltrada)
                        setupRecyclerView(listaFiltrada)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Erro ao carregar paciente do Firebase: ${error.message}")
                    if (!updateOnly) {
                        emptyTextView.text = "Erro de conexão. Verifique o modo offline ou tente novamente."
                        emptyTextView.visibility = View.VISIBLE
                    }
                }
            })
    }

    private fun parseVacinaDose(snapshot: DataSnapshot): VacinaDose {
        val vacinaKey = snapshot.key ?: "KEY_MISSING"

        return VacinaDose(
            cnsIndividuo = cnsPaciente,
            vacinaKey = vacinaKey,
            nomeVacina = snapshot.child("nomeVacina").getValue(String::class.java) ?: "Desconhecida",
            dose = snapshot.child("dose").getValue(String::class.java) ?: "Dose",
            status = snapshot.child("status").getValue(String::class.java) ?: "Pendente",
            dataAplicacao = snapshot.child("dataAplicacao").getValue(String::class.java),
            lote = snapshot.child("lote").getValue(String::class.java),
            labProdut = snapshot.child("labProdut").getValue(String::class.java),
            unidade = snapshot.child("unidade").getValue(String::class.java),
            assinaturaAcs = snapshot.child("assinaturaAcs").getValue(String::class.java),
            dataAgendada = snapshot.child("dataAgendada").getValue(String::class.java)
        )
    }

    private fun scheduleRemindersForList(list: List<VacinaDose>) {
        list.forEach { dose ->
            if (dose.status != "Aplicada" && !dose.dataAgendada.isNullOrEmpty()) {
                context?.let { ctx ->
                    NotificationScheduler.scheduleVaccineReminder(ctx, dose, pacienteNome, cnsPaciente)
                }
            }
        }
    }


    // -----------------------------------------------------------
    // IMPLEMENTAÇÃO DO CLIQUE
    // -----------------------------------------------------------
    override fun onVacinaItemClick(dose: VacinaDose) {
        Log.d(
            TAG,
            "Clique em: ${dose.nomeVacina} - ${dose.dose} | Status: ${dose.status}"
        )

        // Passa o agendamento salvo que está na DOSE CLICADA (se houver)
        var dataAgendadaNaDoseClicada: String? = dose.dataAgendada

        // Se a dose atual foi aplicada, tentamos ver se a próxima dose na lista já tem um agendamento
        if (dose.status == "Aplicada") {
            val adapter = recyclerView.adapter as? VacinaAdapter
            val listaDosesAtuais = adapter?.listaDoses

            if (listaDosesAtuais != null) {
                val index = listaDosesAtuais.indexOf(dose)

                if (index != -1 && index + 1 < listaDosesAtuais.size) {
                    val proximaDoseNaLista = listaDosesAtuais[index + 1]

                    // Verifica se é a próxima dose lógica e está pendente/agendada
                    if (proximaDoseNaLista.nomeVacina == dose.nomeVacina &&
                        proximaDoseNaLista.status != "Aplicada" &&
                        !proximaDoseNaLista.dataAgendada.isNullOrEmpty()) {

                        // Se encontrou um agendamento na próxima dose (que está pendente), usa este
                        dataAgendadaNaDoseClicada = proximaDoseNaLista.dataAgendada
                        Log.d(
                            TAG,
                            "Próxima dose (${proximaDoseNaLista.dose}) detectada com agendamento: ${dataAgendadaNaDoseClicada}"
                        )
                    }
                }
            }
        }


        // Envia os dados para o formulário
        val intent = Intent(requireContext(), FormularioVacinaActivity::class.java).apply {
            putExtra("CNS_PACIENTE", cnsPaciente)
            putExtra("NOME_PACIENTE", pacienteNome)
            putExtra("NOME_VACINA", dose.nomeVacina)
            putExtra("DOSE_VACINA", dose.dose)
            putExtra("DATA_APLICACAO", dose.dataAplicacao)
            putExtra("STATUS", dose.status)
            putExtra("LOTE", dose.lote)
            putExtra("LAB_PRODUT", dose.labProdut)
            putExtra("UNIDADE", dose.unidade)
            putExtra("ASSINATURA_ACS", dose.assinaturaAcs)

            // ⭐️ Passa o agendamento da próxima dose. Se for a dose atual, será a data agendada dela.
            // Se a dose atual estiver Aplicada, buscamos o agendamento da próxima na lista.
            putExtra("DATA_AGENDADA_DOSE_CLICADA", dataAgendadaNaDoseClicada)

            putExtra("VACINA_KEY", dose.vacinaKey)
            putExtra("MODO_EDICAO", true)
        }
        startActivity(intent)
    }

    // -----------------------------------------------------------
    // MOCKUP BASE, MERGE E LÓGICA DE FILTRAGEM
    // -----------------------------------------------------------

    private fun generateVacinaKey(nomeVacina: String, dose: String): String {
        val keyNome = nomeVacina.uppercase(Locale.ROOT).replace(" ", "_").replace("/", "_").replace("-", "_")
        val keyDose = dose.uppercase(Locale.ROOT).replace(" ", "_").replace("Ã", "A").replace("ª", "").replace("Á", "A").replace("É", "E").replace("Í", "I").replace("Ó", "O").replace("Ú", "U")
        return "${keyNome}_${keyDose}"
    }

    /**
     * @brief Cria a lista de doses padrão.
     */
    private fun createMockVacinaDoseList(cns: String, category: String): List<VacinaDose> {
        fun createDose(nome: String, dose: String, status: String = "Pendente"): VacinaDose {
            val key = generateVacinaKey(nome, dose)
            return VacinaDose(
                cnsIndividuo = cns,
                vacinaKey = key,
                nomeVacina = nome,
                dose = dose,
                status = status,
                dataAplicacao = null,
                lote = null,
                labProdut = null,
                unidade = null,
                assinaturaAcs = null,
                dataAgendada = null,
                isSynchronized = false,
                ultimaAtualizacao = 0L
            )
        }

        val listaDosesCompletas = listOf(
            // --- ATÉ 12 MESES ---
            createDose("BCG", "Dose Única"),
            createDose("Hepatite B", "Dose ao Nascer"),
            createDose("Penta", "1ª Dose"),
            createDose("Penta", "2ª Dose"),
            createDose("Penta", "3ª Dose"),
            createDose("Rotavírus humano", "1ª Dose"),
            createDose("Rotavírus humano", "2ª Dose"),
            createDose("Pneumocócica 10V (conjugada)", "1ª Dose"),
            createDose("Pneumocócica 10V (conjugada)", "2ª Dose"),
            createDose("VIP", "1ª Dose"),
            createDose("VIP", "2ª Dose"),
            createDose("VIP", "3ª Dose"),
            createDose("Meningocócica C (conjugada)", "1ª Dose"),
            createDose("Meningocócica C (conjugada)", "2ª Dose"),
            createDose("Febre Amarela", "Dose"),
            createDose("Tríplice viral", "1ª Dose"),
            createDose("Covid-19", "1ª Dose"),
            createDose("Covid-19", "2ª Dose"),
            createDose("Covid-19", "3ª Dose"),

            // --- A PARTIR DE 12 MESES ---
            createDose("Pneumocócica 10V (conjugada)", "Reforço"),
            createDose("Meningocócica C (conjugada)", "Reforço"),
            createDose("DTP", "1º Reforço"),
            createDose("DTP", "2º Reforço"),
            createDose("VOP", "1º Reforço"),
            createDose("VOP", "2º Reforço"),
            createDose("Tetraviral", "Uma dose"),
            createDose("Varicela", "Uma dose"),
            createDose("Febre Amarela", "Dose de Reforço"),
            createDose("Hepatite A", "Uma dose"),
            createDose("HPV", "Dose 1"),
            createDose("HPV", "2ª Dose"), // ⭐️ CORRIGIDO: Harmonizado com o VaccineScheduler
            createDose("Pneumocócica 23V (povos indígenas)","Uma dose"),

            // --- OUTRAS VACINAS / CAMPANHAS (7 Doses no total) ---
            createDose("Campanha/Outra", "Dose"),
            createDose("Estratégia/Outra", "Dose"),
            createDose("Influenza (Gripe)", "Dose"), // ⭐️ NOVA
            createDose("Sarampo", "Dose"),          // ⭐️ NOVA
            createDose("Meningocócica ACWY", "Dose"), // ⭐️ NOVA
            createDose("Difteria e Tétano (dT)", "Dose"), // ⭐️ NOVA
            createDose("Poliomielite (Campanha)", "Dose") // ⭐️ NOVA
        )

        if (category == "TODAS") return listaDosesCompletas

        return emptyList()
    }


    /**
     * ⭐️ LÓGICA CORRIGIDA E COMPLETA:
     * 1. Mescla os dados salvos com o calendário padrão.
     * 2. Aplica a validação de dependência: se a dose anterior está Pendente, doses futuras
     * na mesma sequência têm seu status e agendamento limpos para evitar inconsistências.
     */
    private fun mergeVacinas(
        calendarioPadrao: List<VacinaDose>,
        vacinasSalvas: List<VacinaDose>
    ): List<VacinaDose> {

        // Mapeia doses salvas para substituição fácil
        val mapaVacinasSalvas =
            vacinasSalvas.associateBy { "${it.nomeVacina}|${it.dose}" }.toMutableMap()

        // Cria a lista inicial mesclada, mantendo a ORDEM do calendário padrão
        val listaMescladaOrdenada = calendarioPadrao.map { originalDose ->
            val chave = "${originalDose.nomeVacina}|${originalDose.dose}"
            mapaVacinasSalvas[chave] ?: originalDose
        }.toMutableList()


        // ⭐️ VERIFICAÇÃO DE DEPENDÊNCIA E CORREÇÃO DE INCONSISTÊNCIAS
        for (i in listaMescladaOrdenada.indices) {
            val doseAtual = listaMescladaOrdenada[i]

            // Só precisamos verificar a quebra de sequência se a dose atual estiver PENDENTE.
            if (doseAtual.status == "Pendente" && i > 0) {

                // Encontrar o início da sequência de doses desta vacina
                var k = i
                while (k >= 0 && listaMescladaOrdenada[k].nomeVacina == doseAtual.nomeVacina) {
                    k--
                }

                // O k+1 é a primeira dose da sequência. Se esta dose está Pendente, checamos.

                // Limpa TODAS as doses seguintes na MESMA SEQUÊNCIA (mesmo nome de vacina)
                var j = i + 1
                while (j < listaMescladaOrdenada.size && listaMescladaOrdenada[j].nomeVacina == doseAtual.nomeVacina) {
                    val doseFutura = listaMescladaOrdenada[j]

                    // Se a dose futura tem status Aplicada (erro de sequência) OU tem data agendada (inconsistência)
                    if (doseFutura.status == "Aplicada" || !doseFutura.dataAgendada.isNullOrEmpty()) {

                        Log.w(TAG, "Inconsistência detectada. Dose ${doseAtual.nomeVacina} - ${doseAtual.dose} (Pendente). Limpando dose futura: ${doseFutura.dose}")

                        // 🛑 CRUCIAL: Limpa o registro da dose futura para o estado 'base' (Pendente, sem agendamento/aplicação)
                        val doseLimpa = doseFutura.copy(
                            status = "Pendente",
                            dataAplicacao = null,
                            lote = null,
                            labProdut = null,
                            unidade = null,
                            assinaturaAcs = null,
                            dataAgendada = null, // ⭐️ REMOVE O AGENDAMENTO INCORRETO
                            isSynchronized = false // Marca para sincronização
                        )

                        // 1. Atualiza a lista que será exibida
                        listaMescladaOrdenada[j] = doseLimpa

                        // 2. Salva a dose corrigida no SQLite no background (para sincronizar e persistir a correção)
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                vacinaDao.saveOrUpdate(doseLimpa)
                            } catch (e: Exception) {
                                Log.e(TAG, "Falha ao limpar agendamento de dose futura no SQLite: ${e.message}")
                            }
                        }
                    }
                    j++
                }
            }
        }

        return listaMescladaOrdenada
    }


    private fun filterVacinasByCategory(
        allDoses: List<VacinaDose>,
        category: String
    ): List<VacinaDose> {

        // Define as chaves para fácil verificação de pertencimento à categoria "A PARTIR DE 12 MESES"
        val chavesApos12Meses = listOf(
            "PNEUMOCÓCICA_10V_(CONJUGADA)_REFORÇO",
            "MENINGOCÓCICA_C_(CONJUGADA)_REFORÇO",
            "DTP_1º_REFORÇO", "DTP_2º_REFORÇO",
            "VOP_1º_REFORÇO", "VOP_2º_REFORÇO",
            "TETRAVIRAL_UMA_DOSE",
            "VARICELA_UMA_DOSE",
            "FEBRE_AMARELA_DOSE_DE_REFORÇO",
            "HEPATITE_A_UMA_DOSE",
            "HPV_DOSE_1", "HPV_2_DOSE", // ⭐️ CORRIGIDO: Chave alterada de HPV_DOSE_2 para HPV_2_DOSE
            "PNEUMOCÓCICA_23V_(POVOS_INDÍGENAS)_UMA_DOSE"
        )

        val vacinasOutrasNomes = listOf(
            "Campanha/Outra",
            "Estratégia/Outra",
            "Influenza (Gripe)", // ⭐️ NOVA: Gripe
            "Sarampo",          // ⭐️ NOVA: Sarampo
            "Meningocócica ACWY", // ⭐️ NOVA: Meningocócica ACWY
            "Difteria e Tétano (dT)", // ⭐️ NOVA: Difteria e Tétano (dT)
            "Poliomielite (Campanha)" // ⭐️ NOVA: Pólio Campanha


        )


        return when (category) {
            "ATE_12_MESES" -> allDoses.filter { dose ->
                val chaveDose = dose.vacinaKey
                val isApos12MesesDose = chavesApos12Meses.contains(chaveDose)
                val isOutraVacina = vacinasOutrasNomes.any { dose.nomeVacina.contains(it, true) }

                // Inclui tudo que não é 'A PARTIR DE 12 MESES' e não é 'OUTRA'
                !isApos12MesesDose && !isOutraVacina
            }

            "APOS_12_MESES" -> allDoses.filter { dose ->
                // Inclui apenas doses de "A PARTIR DE 12 MESES"
                chavesApos12Meses.contains(dose.vacinaKey)
            }

            "OUTRAS_VACINAS" -> allDoses.filter { dose ->
                // Inclui apenas Campanhas/Estratégias
                vacinasOutrasNomes.any { dose.nomeVacina.contains(it, true) }
            }

            else -> allDoses
        }
    }

    private fun setupRecyclerView(listaVacinasDoses: List<VacinaDose>) {
        if (listaVacinasDoses.isNotEmpty()) {
            val adapter = VacinaAdapter(listaVacinasDoses, this)
            recyclerView.adapter = adapter
            recyclerView.visibility = View.VISIBLE
            emptyTextView.visibility = View.GONE
        } else {
            recyclerView.visibility = View.GONE
            emptyTextView.text = "Nenhuma vacina encontrada nesta categoria."
            emptyTextView.visibility = View.VISIBLE
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }
}