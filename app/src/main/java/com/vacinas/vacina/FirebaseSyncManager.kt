package com.vacinas.vacina.data

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.FirebaseDatabase
import com.vacinas.vacina.Individuo
import com.vacinas.vacina.VacinaDose
import com.vacinas.vacina.util.NotificationScheduler // ⭐️ Importação necessária
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Gerencia a sincronização (upload/atualização/exclusão) de dados de um indivíduo
 * e suas vacinas para o Firebase Realtime Database.
 */
class FirebaseSyncManager(private val context: Context) {

    // ⭐️ O Firebase Realtime Database é referenciado aqui.
    private val databaseRef = FirebaseDatabase.getInstance().getReference("individuos")
    private val TAG = "FirebaseSyncManager"
    private val individuoDao = IndividuoDao(context)
    private val vacinaDao = VacinaDao(context)

    // ⭐️ Scope para executar operações fora da Main Thread (I/O)
    private val scope = CoroutineScope(Dispatchers.IO)


    /**
     * Remove caracteres inválidos para chaves do Firebase (., #, $, /, [, ])
     * e os substitui por um sublinhado (_).
     */
    private fun sanitizeFirebaseKey(cns: String): String {
        // Regex para capturar todos os caracteres ilegais em chaves do Firebase
        val regex = Regex("[.#\$\\[\\]/]")
        return cns.replace(regex, "_")
    }


    /**
     * Tenta sincronizar os detalhes do indivíduo para o Firebase.
     */
    fun syncIndividuoDetails(individuo: Individuo) {
        val currentUserId = Firebase.auth.currentUser?.uid
        if (currentUserId.isNullOrEmpty()) {
            Log.e(
                TAG,
                "ERRO FATAL: Usuário não autenticado no SyncManager. Não é possível enviar dados."
            )
            return
        }

        // 1. Cria um mapa com APENAS os campos do indivíduo (dados do formulário).
        val updates = mapOf<String, Any>(
            "nome" to individuo.nome,
            "cns" to individuo.cns,
            "dataNascimento" to individuo.dataNascimento,
            "endereco" to individuo.endereco,
            "prontuarioFamilia" to individuo.prontuarioFamilia,
            "nomeMae" to individuo.nomeMae,
            "nomePai" to individuo.nomePai,
            "celular" to individuo.celular,
            "email" to individuo.email,
            "statusVisita" to individuo.statusVisita,
            "ultimaAtualizacao" to individuo.ultimaAtualizacao,
            "ultimaAtualizacaoStr" to individuo.ultimaAtualizacaoStr,
            "registeredByUid" to currentUserId
        )

        Log.d(TAG, "Iniciando atualização de detalhes para CNS: ${individuo.cns}")

        val firebaseCnsKey = sanitizeFirebaseKey(individuo.cns)

        // 2. Usa updateChildren() para atualizar detalhes, preservando os filhos (como 'vacinas').
        databaseRef.child(firebaseCnsKey).updateChildren(updates).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(
                    TAG,
                    "Sucesso ao atualizar detalhes no Firebase: ${individuo.cns}. Atualizando status local..."
                )
                // É CRÍTICO rodar o acesso ao SQLite fora da Main Thread.
                scope.launch {
                    try {
                        // 3. Se o Firebase foi atualizado com sucesso, atualiza o status local.
                        val rowsUpdated =
                            individuoDao.markAsSynced(individuo.cns, individuo.ultimaAtualizacao)
                        if (rowsUpdated > 0) {
                            Log.d(TAG, "Status de sync local de indivíduo atualizado para TRUE.")

                            // ⭐️ NOVO: Após o sync e update local, gerencia a notificação de visita.
                            handleVisitNotificationLogic(individuo)

                        } else {
                            Log.e(
                                TAG,
                                "Falha ao atualizar status de sync local de indivíduo (SQLite)."
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "Erro ao acessar o SQLite para update de sync status do indivíduo.",
                            e
                        )
                    }
                }
            } else {
                // 4. Se falhar, o status local permanece FALSE.
                Log.e(
                    TAG,
                    "Falha ao enviar detalhes do indivíduo para Firebase: ${individuo.cns}.",
                    task.exception
                )
            }
        }
    }

    /**
     * @brief Verifica o status de visita e chama o NotificationScheduler.
     * Deve ser chamada APÓS a confirmação de que os dados foram salvos.
     */
    private fun handleVisitNotificationLogic(individuo: Individuo) {
        val status = individuo.statusVisita
        val dataAgendada = individuo.ultimaAtualizacaoStr

        val nome = individuo.nome
        val cns = individuo.cns

        // Agendar SÓ se o status for "Agendado" E a data for fornecida
        if (status == "Agendado" && !dataAgendada.isNullOrEmpty()) {

            Log.i(
                TAG,
                "Status 'Agendado'. Agendando lembrete de visita para $nome em $dataAgendada."
            )

            NotificationScheduler.scheduleVisitReminder(
                context,
                dataAgendada,
                nome,
                cns
            )

        } else {
            // Caso contrário, se a visita foi concluída, cancelada ou a data removida, cancela o lembrete.
            Log.i(
                TAG,
                "Status de visita de $nome não é Agendado ($status). Solicitando CANCELAMENTO do lembrete."
            )

            NotificationScheduler.cancelVisitReminder(context, cns)
        }
    }


    // -------------------------------------------------------------------------
    // LÓGICA DE SINCRONIZAÇÃO INSTANTÂNEA DE VACINAS (CHAMADA DA ACTIVITY)
    // -------------------------------------------------------------------------

    /**
     * Inicia a sincronização de todas as doses pendentes de um CNS.
     */
    fun syncPendingVacinas(cns: String) {
        // Inicia um Coroutine para realizar as operações de I/O em background
        scope.launch {
            Log.d(TAG, "Iniciando sincronização instantânea de doses para CNS: $cns")
            try {
                // 1. Busca todas as doses NÃO sincronizadas do indivíduo (acesso ao DAO)
                val dosesToSync = vacinaDao.getUnsynchronizedVacinas(cns)

                if (dosesToSync.isEmpty()) {
                    Log.d(
                        TAG,
                        "Nenhuma dose pendente de sincronização encontrada no SQLite para o CNS $cns."
                    )
                    return@launch
                }

                Log.d(TAG, "Encontradas ${dosesToSync.size} doses para sincronizar.")

                // 2. Tenta sincronizar cada dose de forma suspensa e sequencial
                dosesToSync.forEach { dose: VacinaDose ->
                    syncVacinaDose(dose)
                }

                Log.d(TAG, "Sincronização instantânea concluída para o CNS: $cns.")

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Erro fatal durante a sincronização instantânea de vacinas: ${e.message}",
                    e
                )
            }
        }
    }


    /**
     * Sincroniza uma única dose de vacina para o Firebase.
     */
    suspend fun syncVacinaDose(dose: VacinaDose) {

        // 1. Prepara a dose para o Firebase (mapa de dados)
        val doseData = mapOf<String, Any?>(
            "nomeVacina" to dose.nomeVacina,
            "dose" to dose.dose,
            "status" to dose.status,
            "dataAplicacao" to dose.dataAplicacao,
            "lote" to dose.lote,
            "labProdut" to dose.labProdut,
            "unidade" to dose.unidade,
            "assinaturaAcs" to dose.assinaturaAcs,
            "dataAgendada" to dose.dataAgendada,
            "ultimaAtualizacao" to dose.ultimaAtualizacao
        )

        // 2. Constrói o caminho completo: individuos/<cns>/vacinas/<vacinaKey>
        val firebaseCnsKey = sanitizeFirebaseKey(dose.cnsIndividuo)
        val firebasePath = databaseRef
            .child(firebaseCnsKey)
            .child("vacinas")
            .child(dose.vacinaKey)


        Log.d(TAG, "Tentando enviar dose ${dose.vacinaKey} para Firebase...")


        try {
            // 3. Envia os dados e ESPERA a conclusão usando .await()
            firebasePath.setValue(doseData).await()
            Log.d(TAG, "Sucesso ao enviar dose ${dose.vacinaKey} para Firebase.")

            // 4. Se o Firebase foi atualizado com sucesso, atualiza o status local da dose.
            val rowsUpdated = vacinaDao.markAsSynced(
                dose.cnsIndividuo,
                dose.vacinaKey,
                dose.ultimaAtualizacao
            )

            if (rowsUpdated > 0) {
                Log.d(TAG, "Status de sync local da dose atualizado para TRUE.")
            } else {
                Log.e(TAG, "Falha ao atualizar status de sync local da dose (SQLite).")
            }

        } catch (e: Exception) {
            // 5. Se falhar, o status local permanece FALSE. O Worker fará a retentativa.
            Log.e(TAG, "Falha ao enviar dose ${dose.vacinaKey} para Firebase: ${e.message}", e)
        }
    }

    // -------------------------------------------------------------------------
    // LÓGICA DE EXCLUSÃO (CANCELAMENTO DE AGENDAMENTO)
    // -------------------------------------------------------------------------

    /**
     * ⭐️ MÉTODO ATUALIZADO: Exclui uma dose específica do Firebase Realtime Database.
     * Esta função é chamada quando uma dose (normalmente um agendamento futuro) é excluída localmente.
     * @param dose O objeto VacinaDose a ser excluído (usa cnsIndividuo e vacinaKey).
     */
    fun deleteVacinaDose(dose: VacinaDose) {
        val cns = dose.cnsIndividuo
        val vacinaKey = dose.vacinaKey

        if (cns.isEmpty() || vacinaKey.isEmpty()) {
            Log.e(TAG, "DELETE: CNS ou Vacina Key inválidos. Operação cancelada.")
            return
        }

        val firebaseCnsKey = sanitizeFirebaseKey(cns)
        val firebasePath = databaseRef
            .child(firebaseCnsKey)
            .child("vacinas")
            .child(vacinaKey)

        Log.d(TAG, "DELETE: Tentando excluir dose $vacinaKey para CNS $cns do Firebase...")

        // Executa a exclusão na coroutine de I/O
        scope.launch {
            try {
                // Remove o valor (exclui o nó) e ESPERA a conclusão.
                firebasePath.removeValue().await()
                Log.d(TAG, "DELETE: Sucesso ao excluir dose $vacinaKey do Firebase.")

                // NOTA: Não é necessário marcar como sincronizado localmente, pois o registro
                // já foi excluído do SQLite pela VacinaDao.

            } catch (e: Exception) {
                // Loga a falha, mas a exclusão local (SQLite) já ocorreu.
                Log.e(TAG, "DELETE: Falha ao excluir dose $vacinaKey do Firebase: ${e.message}", e)
            }
        }

    }

}