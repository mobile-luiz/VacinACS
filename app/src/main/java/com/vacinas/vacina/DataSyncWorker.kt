package com.vacinas.vacina.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.database
import com.vacinas.vacina.Individuo // Seu modelo de dados
import com.vacinas.vacina.data.IndividuoDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException

class DataSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val individuoDao = IndividuoDao(appContext)
    private val database = Firebase.database.reference
    private val auth = Firebase.auth
    private val TAG = "DataSyncWorker"

    // 🚨 LOG DE DIAGNÓSTICO: Ajuda a confirmar que a classe do Worker foi instanciada
    init {
        val currentUserId = auth.currentUser?.uid ?: "NULO"
        Log.d(TAG, "Worker Class Instantiated. Auth UID Check: $currentUserId")
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Iniciando DataSyncWorker - Execução do doWork().")

        val userId = auth.currentUser?.uid
        if (userId.isNullOrEmpty()) {
            Log.e(TAG, "Usuário não autenticado. Falha no DataSyncWorker.")
            // Retorna um erro irreversível se não houver usuário logado
            return@withContext Result.failure()
        }

        // --- Ordem de Sincronização: 1. PULL, 2. PUSH, 3. DELETION ---

        // 1. PULL (Carga Inicial/Restaurar)
        val pullResult = pullDataFromFirebase(userId)
        if (pullResult != Result.success()) {
            return@withContext pullResult
        }

        // 2. PUSH (Novos/Editados Registros Locais)
        val pushResult = pushDataToFirebase(userId)
        if (pushResult != Result.success()) {
            return@withContext pushResult
        }

        // 3. DELETE (Exclusões Pendentes)
        val deleteResult = processPendingDeletions()

        return@withContext deleteResult
    }

    // --- LÓGICA PULL: FIREBASE -> SQLITE (Restauração) ---
    private suspend fun pullDataFromFirebase(userId: String): Result {
        return try {
            Log.d(TAG, "Iniciando PULL para o usuário: $userId")

            val query = database.child("individuos")
                .orderByChild("registeredByUid")
                .equalTo(userId)

            val dataSnapshot = query.get().await()

            var updatedCount = 0

            for (snapshot in dataSnapshot.children) {
                val individuo = snapshot.getValue(Individuo::class.java)

                if (individuo != null) {
                    individuoDao.saveOrUpdate(individuo)
                    updatedCount++
                } else {
                    // Este log será CRUCIAL se o mapeamento do Individuo.kt estiver falhando
                    Log.w(TAG, "Falha ao mapear indivíduo do nó: ${snapshot.key}. Verifique @PropertyName('synchronized').")
                }
            }

            Log.d(TAG, "PULL concluído. $updatedCount indivíduos carregados do Firebase para o SQLite.")
            Result.success()
        } catch (e: IOException) {
            Log.e(TAG, "Erro de rede no PULL: ${e.message}")
            Result.retry()
        } catch (e: DatabaseException) {
            Log.e(TAG, "Erro de Database no PULL (Permissão/Estrutura?): ${e.message}")
            Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Erro fatal desconhecido no PULL: ${e.message}", e)
            Result.failure()
        }
    }

    // --- LÓGICA PUSH: SQLITE -> FIREBASE (Novos Registros Locais) ---
    private suspend fun pushDataToFirebase(userId: String): Result {
        val unsyncedList = individuoDao.getUnsyncedIndividuos()
        if (unsyncedList.isEmpty()) {
            Log.d(TAG, "Nenhum dado pendente de PUSH.")
            return Result.success()
        }

        Log.d(TAG, "Iniciando PUSH de ${unsyncedList.size} indivíduos.")
        var hasFailure = false

        for (individuo in unsyncedList) {
            try {
                val individuoToPush = individuo.copy(
                    registeredByUid = userId,
                    isSynchronized = true,
                    ultimaAtualizacao = System.currentTimeMillis()
                )

                val firebaseKey = individuo.cns

                database.child("individuos")
                    .child(firebaseKey)
                    .setValue(individuoToPush)
                    .await()

                individuoDao.markAsSynced(individuo.cns, individuoToPush.ultimaAtualizacao)
            } catch (e: Exception) {
                Log.e(TAG, "Falha no PUSH do CNS ${individuo.cns}: ${e.message}", e)
                hasFailure = true
            }
        }

        return if (hasFailure) Result.retry() else Result.success()
    }

    // --- LÓGICA DELETE: SQLITE -> FIREBASE (Processa Exclusões Pendentes) ---
    private suspend fun processPendingDeletions(): Result {
        val cnssToDelete = individuoDao.getPendingDeletions()

        if (cnssToDelete.isEmpty()) {
            Log.d(TAG, "Nenhuma exclusão pendente.")
            return Result.success()
        }

        Log.d(TAG, "Processando ${cnssToDelete.size} exclusões pendentes.")
        var hasFailure = false

        for (cns in cnssToDelete) {
            try {
                database.child("individuos")
                    .child(cns)
                    .removeValue()
                    .await()

                individuoDao.deleteIndividuo(cns)
                Log.d(TAG, "Exclusão do CNS $cns sincronizada e removida do SQLite.")
            } catch (e: Exception) {
                Log.e(TAG, "Falha na exclusão do CNS $cns no Firebase: ${e.message}", e)
                hasFailure = true
            }
        }

        return if (hasFailure) {
            Log.w(TAG, "Algumas exclusões falharam. Retentando.")
            Result.retry()
        } else {
            Result.success()
        }
    }
}