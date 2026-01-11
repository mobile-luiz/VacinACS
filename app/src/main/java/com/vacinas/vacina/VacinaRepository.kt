package com.vacinas.vacina

import android.content.Context
import com.vacinas.vacina.data.IndividuoDbHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.vacinas.vacina.VacinaDose

/**
 * Repository responsável por encapsular a lógica de acesso aos dados de vacinas,
 * utilizando o IndividuoDbHelper (SQLite).
 * Garante que todas as operações de I/O sejam executadas de forma assíncrona.
 */
class VacinaRepository(context: Context) {

    // O helper de BD é inicializado com o Contexto fornecido.
    private val dbHelper = IndividuoDbHelper(context)

    // ----------------------------------------------------
    // MÉTODOS DE BUSCA (Assíncronos)
    // ----------------------------------------------------

    /**
     * Busca TODAS as doses de vacina armazenadas no banco de dados local (Geral).
     *
     * @return Uma lista de todas as VacinaDose encontradas.
     */
    suspend fun getAllDoses(): List<VacinaDose> {
        return withContext(Dispatchers.IO) {
            // Chama a implementação real no DbHelper (SQLite)
            dbHelper.getAllVacinaDoses()
        }
    }

    /**
     * Busca doses de vacina filtradas pelo UID do ACS.
     * Esta função é usada na Tela_home.kt para calcular o status de vacinação do ACS.
     *
     * @param acsUid O UID do Agente Comunitário de Saúde para filtrar.
     * @return Uma lista de VacinaDose que pertencem aos indivíduos registrados por este ACS.
     */
    suspend fun getDosesByAcs(acsUid: String): List<VacinaDose> {
        return withContext(Dispatchers.IO) {
            // 🚨 Requer que dbHelper.getVacinaDosesByAcs(acsUid) esteja implementado
            dbHelper.getVacinaDosesByAcs(acsUid)
        }
    }

    /**
     * Busca doses de vacina para um indivíduo específico (usado no RegistroVacinal).
     *
     * @param cnsIndividuo O CNS (identificador) do indivíduo a ser consultado.
     * @return Uma lista de VacinaDose.
     */
    suspend fun getDosesPorIndividuo(cnsIndividuo: String): List<VacinaDose> {
        return withContext(Dispatchers.IO) {
            // Chama a implementação real no DbHelper (SQLite)
            dbHelper.getVacinaDosesByCns(cnsIndividuo)
        }
    }

    // ----------------------------------------------------
    // MÉTODOS DE ESCRITA (Exemplo/Placeholder)
    // ----------------------------------------------------

    /*
    /**
     * Insere ou atualiza uma dose de vacina no banco de dados local.
     */
    suspend fun saveVacinaDose(dose: VacinaDose): Long {
        return withContext(Dispatchers.IO) {
            dbHelper.insertOrUpdateVacinaDose(dose) // Método real no DbHelper
        }
    }
    */
}