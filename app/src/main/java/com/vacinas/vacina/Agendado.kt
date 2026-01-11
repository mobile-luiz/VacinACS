package com.vacinas.vacina

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem // Importação adicionada para o botão Voltar
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.vacinas.vacina.data.IndividuoDao // Presumindo que este é seu DAO
import com.vacinas.vacina.ui.adapters.IndividuoAdapter
import com.vacinas.vacina.sync.DataSyncWorker
import com.vacinas.vacina.sync.DeletionSyncWorker

class Agendado : AppCompatActivity() {

    private lateinit var individuoAdapter: IndividuoAdapter
    private lateinit var individuoDao: IndividuoDao
    private lateinit var auth: FirebaseAuth
    private var currentAcsUid: String? = null

    private val TAG = "ListaAgendados"

    private lateinit var searchEditText: EditText


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agendado)


        // 1. Encontre a Toolbar no layout.
    /*    val toolbar = findViewById<Toolbar>(R.id.toolbar_formulario)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.navigationIcon?.setTint(android.graphics.Color.WHITE)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed() // ou finish()
        }

        // 1. Inicializa o Firebase Auth e verifica o login
        auth = FirebaseAuth.getInstance()
        currentAcsUid = auth.currentUser?.uid

        // 🔑 FORÇAR UID PARA DEBUG
        // REMOVA ESTA LINHA NA VERSÃO DE PRODUÇÃO!
        currentAcsUid = " "
        Log.w(TAG, "🚨 DEBUG: UID do ACS forçado para: $currentAcsUid")
        // ------------------------

        // --- VERIFICAÇÃO CRÍTICA DE LOGIN ---
        if (currentAcsUid.isNullOrEmpty()) {
            Toast.makeText(this, "Usuário não logado. A lista de agendados está indisponível.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "UID do ACS não encontrado. Fechando lista.")
            finish()
            return
        }
        // ------------------------------------

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Visitas Agendadas do ACS"

        // Inicialização do DAO
        individuoDao = IndividuoDao(applicationContext)

        // Lógica de Carregamento Inicial
        // Obs: 'individuoDao.getIndividuoCountByAcs' deve retornar o total de indivíduos
        // cadastrados por este ACS, independentemente do status.
        if (individuoDao.getIndividuoCountByAcs(currentAcsUid!!) == 0L) {
            Log.d(TAG, "BD Local vazio. Agendando sincronização inicial do Firebase.")
            scheduleInitialDataLoadWork()
        } else {
            schedulePeriodicDataLoadWork()
        }

        setupRecyclerView()
        setupFabListener()
        setupSearchListener()

        // Chamada inicial para carregar AGENDADOS (após setup)
        loadAgendadosFromDb()
    }

    override fun onResume() {
        super.onResume()
        // Recarrega a lista ao voltar para a Activity
        loadAgendadosFromDb()
    }

    // Gerencia o clique no botão 'Voltar' na barra de ação
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish() // Volta para a tela anterior
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecyclerView() {
        val recyclerView: RecyclerView = findViewById(R.id.recycler_individuos)

        individuoAdapter = IndividuoAdapter(emptyList()) { individuoClicado ->
            // Abre a DetalhesIndividuoActivity ao clicar
            val intent = Intent(this, DetalhesIndividuoActivity::class.java).apply {
                putExtra("CNS_INDIVIDUO", individuoClicado.cns)
            }
            startActivity(intent)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = individuoAdapter

        // Lógica de Exclusão por Deslizamento (Swipe-to-Delete)
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.absoluteAdapterPosition
                val individuoToDelete = individuoAdapter.getIndividuoAt(position)
                val cnsToDelete = individuoToDelete.cns

                individuoAdapter.removeItem(position)
                individuoDao.markForDeletion(cnsToDelete)
                Log.d(TAG, "Indivíduo $cnsToDelete marcado para exclusão na nuvem.")

                Snackbar.make(
                    recyclerView,
                    "Indivíduo ${individuoToDelete.nome} excluído localmente.",
                    Snackbar.LENGTH_LONG
                )
                    .setAction("DESFAZER") {
                        individuoAdapter.restoreItem(individuoToDelete, position)
                        val rowsUnmarked = individuoDao.unmarkForDeletion(cnsToDelete)
                        Log.d(TAG, "Desfeito! Indivíduo $cnsToDelete desmarcado para exclusão. Linhas: $rowsUnmarked")
                        loadAgendadosFromDb() // Recarrega com os agendados
                    }
                    .addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            if (event != DISMISS_EVENT_ACTION) {
                                // Se não foi desfeito, agenda a sincronização de exclusão
                                scheduleDeletionSyncWork()
                            }
                        }
                    }).show()
            }
        }

        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recyclerView)
    }

    // ---
    // 🔍 LÓGICA DE PESQUISA E FILTRO
    // ---

    private fun setupSearchListener() {
        searchEditText = findViewById(R.id.edit_text_search)

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Filtra APENAS dentro do subconjunto "Agendado"
                filterList(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterList(query: String) {
        // Chamada CRÍTICA: filtra por ACS E status 'Agendado'
        val filteredList = individuoDao.getAgendadosByAcs(currentAcsUid!!, query)
        individuoAdapter.updateList(filteredList)
    }

    // ---
    // CARREGAMENTO DE DADOS E WORKMANAGER
    // ---

    private fun loadAgendadosFromDb() {
        val acsUid = currentAcsUid ?: return

        // Mantém o filtro de busca ativo, se houver
        val currentQuery = if (::searchEditText.isInitialized) searchEditText.text.toString() else ""

        // Chamada CRÍTICA: carrega por ACS E status 'Agendado'
        val listFromDb = individuoDao.getAgendadosByAcs(acsUid, currentQuery)

        Log.d(TAG, "Total de AGENDADOS carregados do BD para o ACS $acsUid: ${listFromDb.size}")
        individuoAdapter.updateList(listFromDb)
    }

    private fun setupFabListener() {
        val fab = findViewById<FloatingActionButton>(R.id.fab_add_individuo)
        fab.setOnClickListener {
            val intent = Intent(this, FormularioIndividuoActivity::class.java)
            // Passa o UID do ACS para o Formulário de Cadastro
            intent.putExtra("ACS_UID", currentAcsUid)
            startActivity(intent)
        }
    }

    private fun scheduleInitialDataLoadWork() {
        val syncConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<DataSyncWorker>()
            .setConstraints(syncConstraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "InitialDataLoadWork",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
        Log.d(TAG, "WorkManager agendado para Carga Inicial de Dados.")
    }

    private fun schedulePeriodicDataLoadWork() {
        val syncConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<DataSyncWorker>()
            .setConstraints(syncConstraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "PeriodicDataSyncWork",
            ExistingWorkPolicy.KEEP,
            syncRequest
        )
        Log.d(TAG, "WorkManager agendado para Sincronização Periódica de Dados.")
    }

    private fun scheduleDeletionSyncWork() {
        val deleteConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val deleteRequest = OneTimeWorkRequestBuilder<DeletionSyncWorker>()
            .setConstraints(deleteConstraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "DeleteSyncWork",
            ExistingWorkPolicy.REPLACE,
            deleteRequest
        )
        Log.d(TAG, "WorkManager agendado para processar exclusões pendentes.")

     */
    }
}