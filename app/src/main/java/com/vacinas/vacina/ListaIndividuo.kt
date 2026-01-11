package com.vacinas.vacina

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog // Importação do AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.Callback.DISMISS_EVENT_ACTION
import com.google.firebase.auth.FirebaseAuth
import com.vacinas.vacina.data.IndividuoDao
import com.vacinas.vacina.sync.DataSyncWorker
import com.vacinas.vacina.sync.DeletionSyncWorker
import com.vacinas.vacina.ui.adapters.IndividuoAdapter
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class ListaIndividuo : AppCompatActivity() {

    private lateinit var individuoAdapter: IndividuoAdapter
    private lateinit var individuoDao: IndividuoDao
    private lateinit var auth: FirebaseAuth
    private var currentAcsUid: String? = null

    private val TAG = "ListaIndividuo"

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var fabAddIndividuo: FloatingActionButton

    // VARIÁVEIS DE PAGINAÇÃO
    private var currentPage = 0
    private val ITEMS_PER_PAGE = 20
    private lateinit var loadMoreButton: Button
    private var isSearching = false

    // Constantes do WorkManager
    private val UNIQUE_INITIAL_SYNC_WORK = "InitialDataSyncWork"
    private val UNIQUE_PERIODIC_SYNC_WORK = "PeriodicDataSyncWork"
    private val UNIQUE_DELETION_SYNC_WORK = "DeletionSyncWork"

    // -------------------------------------------------------------------------
    // ON CREATE
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_listaindividuo)

        auth = FirebaseAuth.getInstance()
        currentAcsUid = auth.currentUser?.uid

        if (currentAcsUid.isNullOrEmpty()) {
            Toast.makeText(this, "Usuário não logado. A lista de pacientes está indisponível.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "UID do ACS não encontrado. Fechando lista.")
            finish()
            return
        }

        setupViewsFromXml()

        individuoDao = IndividuoDao(applicationContext)

        // Lógica de Agendamento de Sincronização
        if (individuoDao.getIndividuoCountByAcs(currentAcsUid!!) == 0L) {
            scheduleInitialDataLoadWork()
        } else {
            schedulePeriodicDataLoadWork()
        }

        setupRecyclerView()
        setupSearchListener()
        setupFabListener()
        setupLoadMoreListener()

        loadFirstPage()
    }

    // -------------------------------------------------------------------------
    // LIFE CYCLE
    // -------------------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        // Recarrega a lista para refletir edições/criações
        if (!isSearching) {
            loadFirstPage()
        } else {
            filterList(searchEditText.text.toString())
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Recarrega a lista quando a DetalhesIndividuoActivity retorna OK (após edição/visita)
        if (resultCode == Activity.RESULT_OK) {
            if (!isSearching) {
                loadFirstPage()
            } else {
                filterList(searchEditText.text.toString())
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // -------------------------------------------------------------------------
    // SETUP DE VIEW E LISTENERS
    // -------------------------------------------------------------------------

    private fun setupViewsFromXml() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar_formulario)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // OBS: A cor do ícone de navegação é definida aqui, mantido como está no código anterior.
         toolbar.navigationIcon?.setTint(android.graphics.Color.WHITE)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed() // ou finish()
        }

        searchEditText = findViewById(R.id.edit_text_search)
        recyclerView = findViewById(R.id.recycler_individuos)
        loadMoreButton = findViewById(R.id.button_load_more)
        fabAddIndividuo = findViewById(R.id.fab_add_individuo)
    }

    private fun setupFabListener() {
        fabAddIndividuo.setOnClickListener {
            val intent = Intent(this, FormularioIndividuoActivity::class.java).apply {
                putExtra("ACS_UID", currentAcsUid)
            }
            startActivity(intent)
        }
    }

    private fun setupLoadMoreListener() {
        loadMoreButton.setOnClickListener {
            loadNextPage()
        }
    }

    private fun setupRecyclerView() {
        // Inicializa o adapter com o listener de clique curto (para Detalhes)
        individuoAdapter = IndividuoAdapter(
            emptyList(),
            onItemClick = { individuoClicado ->
                val intent = Intent(this, DetalhesIndividuoActivity::class.java).apply {
                    putExtra("CNS_INDIVIDUO", individuoClicado.cns)
                }
                // Usa startActivityForResult se precisar saber o resultado da edição/visita.
                startActivityForResult(intent, 1)
            },
            // ✅ NOVO: Adiciona o listener de clique longo para exclusão
            onItemLongClick = { individuoLongoClicado ->
                showDeletionConfirmationDialog(individuoLongoClicado)
                true // Retorna true para consumir o evento
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = individuoAdapter

        // ❌ REMOVIDO: ItemTouchHelper removido para implementar clique longo.
    }

    private fun setupSearchListener() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                isSearching = query.isNotBlank()
                if (isSearching) {
                    filterList(query)
                    loadMoreButton.visibility = View.GONE
                } else {
                    loadFirstPage()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    // -------------------------------------------------------------------------
    // LÓGICA DE EXCLUSÃO (CLIQUE LONGO)
    // -------------------------------------------------------------------------

    /**
     * Exibe um diálogo de confirmação de exclusão para o clique longo.
     */
    private fun showDeletionConfirmationDialog(individuo: Individuo) {
        AlertDialog.Builder(this)
            .setTitle("Excluir Indivíduo")
            .setMessage("Tem certeza de que deseja excluir o paciente ${individuo.nome}? Esta exclusão será sincronizada com a nuvem na próxima conexão.")
            .setPositiveButton("Excluir") { dialog, _ ->
                handleDeletion(individuo)
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    /**
     * Lógica de exclusão: 1. Remove da UI, 2. Marca no DB, 3. Exibe Snackbar Desfazer, 4. Agenda Worker.
     */
    private fun handleDeletion(individuoToDelete: Individuo) {
        // Usa o novo método indexOf do Adapter para encontrar a posição
        val position = individuoAdapter.indexOf(individuoToDelete)

        if (position == -1) return // Indivíduo não está mais visível/na lista

        val cnsToDelete = individuoToDelete.cns

        // 1. Remove da UI e marca no DB local
        individuoAdapter.removeItem(position)
        individuoDao.markForDeletion(cnsToDelete)

        // 2. Exibe Snackbar para Desfazer
        Snackbar.make(
            recyclerView,
            "Indivíduo ${individuoToDelete.nome} excluído localmente.",
            Snackbar.LENGTH_LONG
        )
            .setAction("DESFAZER") {
                individuoAdapter.restoreItem(individuoToDelete, position)
                individuoDao.unmarkForDeletion(cnsToDelete)
                // Recarrega a lista para garantir que a paginação/filtros sejam resetados corretamente
                loadFirstPage()
            }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    // Verifica se o DESFAZER (DISMISS_EVENT_ACTION) NÃO foi clicado
                    if (event != DISMISS_EVENT_ACTION) {
                        // 3. Agenda a exclusão na nuvem
                        scheduleDeletionSyncWork()
                    }
                }
            }).show()
    }

    // -------------------------------------------------------------------------
    // LÓGICA DE PAGINAÇÃO E BUSCA
    // -------------------------------------------------------------------------

    private fun filterList(query: String) {
        // Carrega todos os resultados de busca (limit=0 para trazer todos os matches)
        val filteredList = individuoDao.getIndividuosPaginadosByAcs(currentAcsUid!!, query, 0, 0)
        individuoAdapter.updateList(filteredList)
        Log.d(TAG, "Busca ativa. Resultados: ${filteredList.size}")
    }

    private fun loadFirstPage() {
        val acsUid = currentAcsUid ?: return
        currentPage = 0
        val offset = currentPage * ITEMS_PER_PAGE
        val listFromDb = individuoDao.getIndividuosPaginadosByAcs(acsUid, "", offset, ITEMS_PER_PAGE)
        individuoAdapter.updateList(listFromDb)
        updateLoadMoreButton(listFromDb.size)
    }

    private fun loadNextPage() {
        val acsUid = currentAcsUid ?: return
        if (isSearching) return
        val offset = (currentPage + 1) * ITEMS_PER_PAGE
        val listFromDb = individuoDao.getIndividuosPaginadosByAcs(acsUid, "", offset, ITEMS_PER_PAGE)

        if (listFromDb.isNotEmpty()) {
            individuoAdapter.appendList(listFromDb)
            currentPage++
            Log.d(TAG, "Carregada página ${currentPage + 1}. Offset: $offset, Itens: ${listFromDb.size}")
        } else {
            Log.d(TAG, "Fim da lista de pacientes.")
        }
        updateLoadMoreButton(listFromDb.size)
    }

    private fun updateLoadMoreButton(lastLoadedCount: Int) {
        if (isSearching || lastLoadedCount < ITEMS_PER_PAGE) {
            loadMoreButton.visibility = View.GONE
            if (!isSearching && lastLoadedCount > 0 && currentPage > 0) {
                Toast.makeText(this, "Fim da lista de pacientes.", Toast.LENGTH_SHORT).show()
            }
        } else {
            loadMoreButton.visibility = View.VISIBLE
            loadMoreButton.text = "Carregar Mais Pacientes ($ITEMS_PER_PAGE)"
        }
    }

    // -------------------------------------------------------------------------
    // WORK MANAGER (SINCRONIZAÇÃO)
    // -------------------------------------------------------------------------

    private fun createSyncConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    private fun scheduleInitialDataLoadWork() {
        val syncRequest = OneTimeWorkRequestBuilder<DataSyncWorker>()
            .setConstraints(createSyncConstraints())
            .addTag("InitialSyncTag")
            .build()

        WorkManager.getInstance(applicationContext).beginUniqueWork(
            UNIQUE_INITIAL_SYNC_WORK,
            ExistingWorkPolicy.KEEP,
            syncRequest
        ).enqueue()
        Log.d(TAG, "Sincronização inicial agendada.")
    }

    private fun schedulePeriodicDataLoadWork() {
        val periodicRequest = PeriodicWorkRequestBuilder<DataSyncWorker>(
            1, TimeUnit.HOURS // Sincroniza a cada 1 hora
        )
            .setConstraints(createSyncConstraints())
            .addTag("PeriodicSyncTag")
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
        Log.d(TAG, "Sincronização periódica agendada.")
    }

    private fun scheduleDeletionSyncWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val deletionRequest = OneTimeWorkRequestBuilder<DeletionSyncWorker>()
            .setConstraints(constraints)
            .addTag("DeletionSyncTag")
            .build()

        WorkManager.getInstance(applicationContext).beginUniqueWork(
            UNIQUE_DELETION_SYNC_WORK,
            // REPLACE garante que se houver um worker de exclusão anterior, ele será cancelado/substituído
            ExistingWorkPolicy.REPLACE,
            deletionRequest
        ).enqueue()

        Log.d(TAG, "Exclusão na nuvem agendada com sucesso.")
    }
}