package com.vacinas.vacina.service

import android.content.Context
import android.util.Log
import com.vacinas.vacina.Individuo
import com.vacinas.vacina.util.NotificationScheduler

// Esta classe DEVE ser instanciada uma vez (ex: injetada no ViewModel)
// e usada para processar atualizações do Indivíduo.

class PacienteService(private val context: Context) {

    private val TAG = "PacienteService"

    /**
     * @brief Ponto central para processar a atualização dos dados do Indivíduo e
     * decidir se agenda ou cancela o lembrete de visita.
     * * @param individuoAtualizado O objeto Indivíduo com os dados mais recentes.
     */
    fun processarAtualizacaoIndividuo(individuoAtualizado: Individuo) {

        // --- 1. CHAMADAS DE ACESSO A DADOS (SIMULADAS) ---
        // Aqui você chamaria o DAO para salvar no Room e o SyncManager para o Firebase.
        // A ordem é crucial: o agendamento/cancelamento só deve ocorrer após os dados estarem salvos.
        // salvarIndividuoLocalmente(individuoAtualizado)
        // sincronizarIndividuoFirebase(individuoAtualizado)

        // --- 2. LÓGICA DE AGENDAMENTO DE VISITA (CHAMADA CRÍTICA) ---
        agendarOuCancelarLembreteVisita(individuoAtualizado)
    }

    /**
     * @brief Verifica o status de visita e chama o NotificationScheduler.
     */
    private fun agendarOuCancelarLembreteVisita(individuo: Individuo) {

        val statusVisita = individuo.statusVisita
        val dataAgendada = individuo.ultimaAtualizacaoStr

        val nome = individuo.nome
        val cns = individuo.cns

        // 💡 CONDIÇÃO: Agendar SÓ se o status for "Agendado" e a data não estiver vazia.
        if (statusVisita == "Agendado" && !dataAgendada.isNullOrEmpty()) {

            Log.i(TAG, "Status 'Agendado'. Solicitando AGENDAMENTO de visita para $nome em $dataAgendada.")

            // Chama a função scheduleVisitReminder.
            // O scheduler internamente irá checar se a data é futura.
            NotificationScheduler.scheduleVisitReminder(
                context,
                dataAgendada,
                nome,
                cns
            )

        } else {
            // Se o status mudou (ex: para "Concluído"), ou a data foi limpa/passou, CANCELAMOS.
            Log.i(TAG, "Status de visita de $nome não é Agendado ($statusVisita). Solicitando CANCELAMENTO.")

            // Chama a função cancelVisitReminder.
            NotificationScheduler.cancelVisitReminder(context, cns)
        }
    }

    // Você pode substituir por funções reais do seu DAO e Firebase Manager:
    // private fun salvarIndividuoLocalmente(individuo: Individuo) { /* ... */ }
    // private fun sincronizarIndividuoFirebase(individuo: Individuo) { /* ... */ }
}