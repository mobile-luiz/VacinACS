package com.vacinas.vacina.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.database.FirebaseDatabase
import com.vacinas.vacina.data.IndividuoDao
import com.vacinas.vacina.data.VacinaDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

class DeletionSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val individuoDao = IndividuoDao(appContext)
    private val vacinaDao = VacinaDao(appContext)
    private val databaseRef = FirebaseDatabase.getInstance().getReference("individuos")
    private val TAG = "DeletionSyncWorker"

    /**
     * Remove caracteres inválidos para chaves do Firebase (., #, $, /, [, ])
     * e os substitui por um sublinhado (_).
     */
    private fun sanitizeFirebaseKey(cns: String): String {
        val regex = Regex("[.#\$\\[\\]/]")
        return cns.replace(regex, "_")
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Iniciando processamento de exclusões pendentes...")

        val cnssToDelete = individuoDao.getPendingDeletions()

        if (cnssToDelete.isEmpty()) {
            Log.d(TAG, "Nenhuma exclusão pendente encontrada.")
            return@withContext Result.success()
        }

        // Processa as exclusões em paralelo
        val deletionTasks = cnssToDelete.map { cns ->
            async {
                processSingleDeletion(cns)
            }
        }

        val results = deletionTasks.awaitAll()
        val successfulDeletions = results.count { it }
        val failedDeletions = results.count { !it }

        Log.d(TAG, "Exclusões processadas. Sucesso: $successfulDeletions, Falha: $failedDeletions")

        return@withContext when {
            failedDeletions > 0 -> Result.retry()
            else -> Result.success()
        }
    }

    /**
     * Processa a exclusão de um único CNS: Firebase -> Vacinas SQLite -> Individuo SQLite.
     */
    private suspend fun processSingleDeletion(cns: String): Boolean {
        val firebaseKey = sanitizeFirebaseKey(cns)

        return try {
            // 1. Exclui no Firebase (Indivíduo e todos os seus filhos, incluindo 'vacinas')
            databaseRef.child(firebaseKey).removeValue().await()
            Log.d(TAG, "Excluído com sucesso do Firebase: $firebaseKey")

            // 2. Exclui permanentemente as doses de vacina associadas do SQLite
            vacinaDao.deleteDosesByCns(cns) // ⭐️ Referência agora resolvida!
            Log.d(TAG, "Doses de vacina do CNS $cns excluídas do SQLite.")

            // 3. Exclui permanentemente o Indivíduo do SQLite
            individuoDao.deleteIndividuo(cns)
            Log.d(TAG, "Indivíduo $cns excluído permanentemente do SQLite.")

            true // Sucesso

        } catch (e: Exception) {
            // Em caso de falha de rede ou Firebase, não remove do SQLite (para retentar).
            Log.e(TAG, "Falha ao excluir no Firebase: $firebaseKey. Será retentado.", e)
            false // Falha
        }
    }
}