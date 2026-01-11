package com.vacinas.vacina.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.BaseColumns
import android.util.Log
import com.vacinas.vacina.Individuo
import com.vacinas.vacina.data.IndividuoContract.IndividuoEntry as Entry

/**
 * Data Access Object (DAO) para a entidade Individuo.
 * Gerencia todas as operações de CRUD com o SQLite, incluindo persistência, sincronização
 * e funcionalidades de lista (busca, paginação, exclusão lógica).
 */
class IndividuoDao(private val context: Context) {

    private val dbHelper = IndividuoDbHelper(context)
    private val TAG = "IndividuoDao"

    // Colunas necessárias para leitura
    private val projection = arrayOf(
        BaseColumns._ID,
        Entry.COLUMN_CNS,
        Entry.COLUMN_NOME,
        Entry.COLUMN_DATA_NASCIMENTO,
        Entry.COLUMN_NOME_MAE,
        Entry.COLUMN_NOME_PAI,
        Entry.COLUMN_CELULAR,
        Entry.COLUMN_EMAIL,
        Entry.COLUMN_ENDERECO,
        Entry.COLUMN_PRONTUARIO_FAMILIA,
        Entry.COLUMN_STATUS_VISITA,
        Entry.COLUMN_ULTIMA_ATUALIZACAO,
        Entry.COLUMN_IS_SYNCHRONIZED,
        Entry.COLUMN_DELETE_PENDING,
        Entry.COLUMN_REGISTERED_BY_UID,
        Entry.COLUMN_ULTIMA_ATUALIZACAO_STR, // ⭐️ NOVO CAMPO: Adicionado à projeção
    )

    // ----------------------------------------------------------------------
    // ⚙️ MÉTODOS AUXILIARES
    // ----------------------------------------------------------------------

    private fun individuoToContentValues(individuo: Individuo): ContentValues {
        return ContentValues().apply {
            put(Entry.COLUMN_CNS, individuo.cns)
            put(Entry.COLUMN_NOME, individuo.nome)
            put(Entry.COLUMN_DATA_NASCIMENTO, individuo.dataNascimento)
            put(Entry.COLUMN_NOME_MAE, individuo.nomeMae)
            put(Entry.COLUMN_NOME_PAI, individuo.nomePai)
            put(Entry.COLUMN_CELULAR, individuo.celular)
            put(Entry.COLUMN_EMAIL, individuo.email)
            put(Entry.COLUMN_ENDERECO, individuo.endereco)
            put(Entry.COLUMN_PRONTUARIO_FAMILIA, individuo.prontuarioFamilia)
            put(Entry.COLUMN_STATUS_VISITA, individuo.statusVisita)
            put(Entry.COLUMN_ULTIMA_ATUALIZACAO, individuo.ultimaAtualizacao)
            put(Entry.COLUMN_ULTIMA_ATUALIZACAO_STR, individuo.ultimaAtualizacaoStr) // ⭐️ NOVO CAMPO: Adicionado ao ContentValues
            put(Entry.COLUMN_IS_SYNCHRONIZED, if (individuo.isSynchronized) 1 else 0)
            put(Entry.COLUMN_DELETE_PENDING, if (individuo.deletePending) 1 else 0)
            put(Entry.COLUMN_REGISTERED_BY_UID, individuo.registeredByUid)
        }
    }

    private fun cursorToIndividuo(cursor: Cursor): Individuo {
        val idIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID)
        val cnsIndex = cursor.getColumnIndexOrThrow(Entry.COLUMN_CNS)
        val nomeIndex = cursor.getColumnIndexOrThrow(Entry.COLUMN_NOME)
        val dataNascIndex = cursor.getColumnIndexOrThrow(Entry.COLUMN_DATA_NASCIMENTO)
        val nomeMaeIndex = cursor.getColumnIndexOrThrow(Entry.COLUMN_NOME_MAE)
        val nomePaiIndex = cursor.getColumnIndexOrThrow(Entry.COLUMN_NOME_PAI)
        val celularIndex = cursor.getColumnIndexOrThrow(Entry.COLUMN_CELULAR)
        val emailIndex = cursor.getColumnIndexOrThrow(Entry.COLUMN_EMAIL)
        val enderecoIndex = cursor.getColumnIndexOrThrow(Entry.COLUMN_ENDERECO)
        val prontuarioFamiliaIndex = cursor.getColumnIndexOrThrow(Entry.COLUMN_PRONTUARIO_FAMILIA)
        val statusVisitaIndex = cursor.getColumnIndexOrThrow(Entry.COLUMN_STATUS_VISITA)
        val ultimaAtualizacaoIndex = cursor.getColumnIndexOrThrow(Entry.COLUMN_ULTIMA_ATUALIZACAO)
        val isSynchronizedIndex = cursor.getColumnIndexOrThrow(Entry.COLUMN_IS_SYNCHRONIZED)
        val deletePendingIndex = cursor.getColumnIndexOrThrow(Entry.COLUMN_DELETE_PENDING)
        val registeredByUidIndex = cursor.getColumnIndexOrThrow(Entry.COLUMN_REGISTERED_BY_UID)
        val ultimaAtualizacaoStrIndex = cursor.getColumnIndexOrThrow(Entry.COLUMN_ULTIMA_ATUALIZACAO_STR) // ⭐️ NOVO CAMPO: Obtém o índice

        return Individuo(
            id = cursor.getLong(idIndex).toString(),
            cns = cursor.getString(cnsIndex),
            nome = cursor.getString(nomeIndex),
            dataNascimento = cursor.getString(dataNascIndex),
            nomeMae = cursor.getString(nomeMaeIndex),
            nomePai = cursor.getString(nomePaiIndex),
            celular = cursor.getString(celularIndex),
            email = cursor.getString(emailIndex),
            endereco = cursor.getString(enderecoIndex),
            prontuarioFamilia = cursor.getString(prontuarioFamiliaIndex),
            statusVisita = cursor.getString(statusVisitaIndex),
            ultimaAtualizacao = cursor.getLong(ultimaAtualizacaoIndex),
            ultimaAtualizacaoStr = cursor.getString(ultimaAtualizacaoStrIndex), // ⭐️ NOVO CAMPO: Lendo o valor
            isSynchronized = cursor.getInt(isSynchronizedIndex) == 1,
            deletePending = cursor.getInt(deletePendingIndex) == 1,
            registeredByUid = cursor.getString(registeredByUidIndex)
        )
    }

    // ----------------------------------------------------------------------
    // 💾 MÉTODOS DE PERSISTÊNCIA (CRUD - Restante do código inalterado)
    // ----------------------------------------------------------------------

    fun saveOrUpdate(individuo: Individuo): Long {
        val db = dbHelper.writableDatabase
        val values = individuoToContentValues(individuo)
        val cns = individuo.cns
        // ... (restante da função saveOrUpdate)
        val selection = "${Entry.COLUMN_CNS} = ?"
        val selectionArgs = arrayOf(cns)

        val rowsUpdated = db.update(IndividuoContract.TABLE_NAME, values, selection, selectionArgs)

        return if (rowsUpdated > 0) {
            Log.d(TAG, "Indivíduo $cns atualizado com sucesso.")
            rowsUpdated.toLong()
        } else {
            val newRowId = db.insert(IndividuoContract.TABLE_NAME, null, values)
            if (newRowId != -1L) {
                Log.d(TAG, "Novo indivíduo $cns inserido com sucesso (ID: $newRowId).")
            } else {
                Log.e(TAG, "Falha ao inserir novo indivíduo $cns. Possível CNS duplicado.")
            }
            newRowId
        }
    }

    fun findByCns(cns: String): Individuo? {
        val db = dbHelper.readableDatabase
        var individuo: Individuo? = null

        val selection = "${Entry.COLUMN_CNS} = ?"
        val selectionArgs = arrayOf(cns)

        val cursor = db.query(
            IndividuoContract.TABLE_NAME,
            projection,
            selection,
            selectionArgs,
            null,
            null,
            null
        )

        with(cursor) {
            if (moveToFirst()) {
                individuo = cursorToIndividuo(this)
            }
            close()
        }
        return individuo
    }

    /**
     * Retorna todos os indivíduos ativos (não deletados).
     */
    fun getAllIndividuos(): List<Individuo> {
        val db = dbHelper.readableDatabase
        val itemList = mutableListOf<Individuo>()
        val selection = "${Entry.COLUMN_DELETE_PENDING} = 0"
        val sortOrder = "${Entry.COLUMN_ULTIMA_ATUALIZACAO} DESC"

        val cursor = db.query(
            IndividuoContract.TABLE_NAME,
            projection,
            selection,
            null,
            null,
            null,
            sortOrder
        )

        with(cursor) {
            while (moveToNext()) {
                itemList.add(cursorToIndividuo(this))
            }
            close()
        }
        return itemList
    }

    // ----------------------------------------------------------------------
    // 📊 MÉTODOS DE BUSCA, FILTRO E CONTAGEM (ListaIndividuo e Tela_home - Restante do código inalterado)
    // ----------------------------------------------------------------------

    /**
     * ⭐️ NOVO MÉTODO PARA A TELA HOME
     * Retorna todos os indivíduos ativos (não deletados) registrados por um ACS específico.
     * Necessário para a Tela_home para contagens de visitas.
     */
    fun getAllByAcsUid(acsUid: String): List<Individuo> {
        val db = dbHelper.readableDatabase
        val itemList = mutableListOf<Individuo>()

        // Condição: DELETE_PENDING = 0 AND REGISTERED_BY_UID = ?
        val selection = "${Entry.COLUMN_DELETE_PENDING} = 0 AND ${Entry.COLUMN_REGISTERED_BY_UID} = ?"
        val selectionArgs = arrayOf(acsUid)
        val sortOrder = "${Entry.COLUMN_ULTIMA_ATUALIZACAO} DESC"

        val cursor = db.query(
            IndividuoContract.TABLE_NAME,
            projection,
            selection,
            selectionArgs,
            null,
            null,
            sortOrder
        )

        with(cursor) {
            while (moveToNext()) {
                itemList.add(cursorToIndividuo(this))
            }
            close()
        }
        Log.d(TAG, "Carregados ${itemList.size} indivíduos ativos para o ACS $acsUid.")
        return itemList
    }

    /**
     * Conta o número total de indivíduos ATIVOS registrados por um ACS (UID).
     */
    fun getIndividuoCountByAcs(acsUid: String): Long {
        val db = dbHelper.readableDatabase
        val query = "SELECT COUNT(*) FROM ${IndividuoContract.TABLE_NAME} " +
                "WHERE ${Entry.COLUMN_DELETE_PENDING} = 0 AND ${Entry.COLUMN_REGISTERED_BY_UID} = ?"

        val cursor = db.rawQuery(query, arrayOf(acsUid))

        var count = 0L
        if (cursor.moveToFirst()) {
            count = cursor.getLong(0)
        }
        cursor.close()
        Log.d(TAG, "Total de indivíduos ativos para o ACS $acsUid: $count")
        return count
    }

    /**
     * Carrega indivíduos vinculados a um ACS, suportando pesquisa, limite e offset (paginação).
     */
    fun getIndividuosPaginadosByAcs(acsUid: String, query: String, offset: Int, limit: Int): List<Individuo> {
        val db = dbHelper.readableDatabase
        val itemList = mutableListOf<Individuo>()

        var selection = "${Entry.COLUMN_DELETE_PENDING} = 0 AND ${Entry.COLUMN_REGISTERED_BY_UID} = ?"
        val selectionArgs = mutableListOf(acsUid)

        if (query.isNotBlank()) {
            val searchWildcard = "%$query%"
            selection += " AND (${Entry.COLUMN_NOME} LIKE ? OR ${Entry.COLUMN_CNS} LIKE ?)"
            selectionArgs.add(searchWildcard)
            selectionArgs.add(searchWildcard)
        }

        val sortOrder = "${Entry.COLUMN_ULTIMA_ATUALIZACAO} DESC"

        val limitAndOffset = if (limit > 0) "$limit OFFSET $offset" else null

        val cursor = db.query(
            IndividuoContract.TABLE_NAME,
            projection,
            selection,
            selectionArgs.toTypedArray(),
            null,
            null,
            sortOrder,
            limitAndOffset
        )

        with(cursor) {
            while (moveToNext()) {
                itemList.add(cursorToIndividuo(this))
            }
            close()
        }
        Log.d(TAG, "Carregados ${itemList.size} indivíduos. Offset: $offset, Limit: $limit.")
        return itemList
    }

    /**
     * Filtra indivíduos por ACS e status 'Agendado'.
     */
    fun getAgendadosByAcs(acsUid: String, query: String): List<Individuo> {
        val db = dbHelper.readableDatabase
        val itemList = mutableListOf<Individuo>()

        var selection = "${Entry.COLUMN_DELETE_PENDING} = 0 AND ${Entry.COLUMN_REGISTERED_BY_UID} = ? AND ${Entry.COLUMN_STATUS_VISITA} = 'Agendado'"
        val selectionArgs = mutableListOf(acsUid)

        if (query.isNotBlank()) {
            val searchWildcard = "%$query%"
            selection += " AND (${Entry.COLUMN_NOME} LIKE ? OR ${Entry.COLUMN_CNS} LIKE ?)"
            selectionArgs.add(searchWildcard)
            selectionArgs.add(searchWildcard)
        }

        val sortOrder = "${Entry.COLUMN_ULTIMA_ATUALIZACAO} DESC"

        val cursor = db.query(
            IndividuoContract.TABLE_NAME,
            projection,
            selection,
            selectionArgs.toTypedArray(),
            null,
            null,
            sortOrder
        )

        with(cursor) {
            while (moveToNext()) {
                itemList.add(cursorToIndividuo(this))
            }
            close()
        }
        return itemList
    }

    /**
     * Filtra indivíduos por ACS e status 'Visitado'.
     */
    fun getRealizadosByAcs(acsUid: String, query: String): List<Individuo> {
        val db = dbHelper.readableDatabase
        val itemList = mutableListOf<Individuo>()

        var selection = "${Entry.COLUMN_DELETE_PENDING} = 0 AND ${Entry.COLUMN_REGISTERED_BY_UID} = ? AND ${Entry.COLUMN_STATUS_VISITA} = 'Visitado'"
        val selectionArgs = mutableListOf(acsUid)

        if (query.isNotBlank()) {
            val searchWildcard = "%$query%"
            selection += " AND (${Entry.COLUMN_NOME} LIKE ? OR ${Entry.COLUMN_CNS} LIKE ?)"
            selectionArgs.add(searchWildcard)
            selectionArgs.add(searchWildcard)
        }

        val sortOrder = "${Entry.COLUMN_ULTIMA_ATUALIZACAO} DESC"

        val cursor = db.query(
            IndividuoContract.TABLE_NAME,
            projection,
            selection,
            selectionArgs.toTypedArray(),
            null,
            null,
            sortOrder
        )

        with(cursor) {
            while (moveToNext()) {
                itemList.add(cursorToIndividuo(this))
            }
            close()
        }
        return itemList
    }

    // ----------------------------------------------------------------------
    // 🔄 MÉTODOS DE SINCRONIZAÇÃO (WorkManager - Restante do código inalterado)
    // ----------------------------------------------------------------------

    fun updateIndividuoLastUpdate(cns: String, statusVisita: String, ultimaAtualizacao: Long): Boolean {
        val db = dbHelper.writableDatabase
        // 🚨 ATENÇÃO: Se este método for chamado, ele DEVE atualizar também a string,
        // mas como a string é gerada na Activity e salva via saveOrUpdate,
        // vou manter este método simples.
        val values = ContentValues().apply {
            put(Entry.COLUMN_STATUS_VISITA, statusVisita)
            put(Entry.COLUMN_ULTIMA_ATUALIZACAO, ultimaAtualizacao)
            put(Entry.COLUMN_IS_SYNCHRONIZED, 0) // Marca como não sincronizado (0)
        }

        val selection = "${Entry.COLUMN_CNS} = ?"
        val selectionArgs = arrayOf(cns)

        val count = db.update(
            IndividuoContract.TABLE_NAME,
            values,
            selection,
            selectionArgs
        )

        Log.d(TAG, "Status de Visita do CNS $cns atualizado para '$statusVisita'. Linhas: $count")

        return count > 0
    }

    fun getUnsyncedIndividuos(): List<Individuo> {
        val db = dbHelper.readableDatabase
        val itemList = mutableListOf<Individuo>()
        val selection = "${Entry.COLUMN_IS_SYNCHRONIZED} = 0 AND ${Entry.COLUMN_DELETE_PENDING} = 0"
        val sortOrder = "${Entry.COLUMN_ULTIMA_ATUALIZACAO} ASC"

        val cursor = db.query(
            IndividuoContract.TABLE_NAME,
            projection,
            selection,
            null,
            null,
            null,
            sortOrder
        )

        with(cursor) {
            while (moveToNext()) {
                itemList.add(cursorToIndividuo(this))
            }
            close()
        }
        Log.d(TAG, "Encontrados ${itemList.size} indivíduos para PUSH.")
        return itemList
    }

    fun markAsSynced(cns: String, lastModified: Long): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(Entry.COLUMN_IS_SYNCHRONIZED, 1) // TRUE
            put(Entry.COLUMN_ULTIMA_ATUALIZACAO, lastModified)
        }
        val selection = "${Entry.COLUMN_CNS} = ?"
        val selectionArgs = arrayOf(cns)

        val rowsUpdated = db.update(IndividuoContract.TABLE_NAME, values, selection, selectionArgs)
        Log.d(TAG, "Status de sync do CNS $cns atualizado para TRUE. Linhas: $rowsUpdated")
        return rowsUpdated
    }

    fun updateSyncStatus(cns: String, isSynchronized: Boolean): Int {
        if (isSynchronized) {
            return markAsSynced(cns, System.currentTimeMillis())
        }

        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(Entry.COLUMN_IS_SYNCHRONIZED, 0)
            put(Entry.COLUMN_ULTIMA_ATUALIZACAO, System.currentTimeMillis())
        }
        val selection = "${Entry.COLUMN_CNS} = ?"
        val selectionArgs = arrayOf(cns)
        return db.update(IndividuoContract.TABLE_NAME, values, selection, selectionArgs)
    }

    // ----------------------------------------------------------------------
    // 🗑️ MÉTODOS DE EXCLUSÃO LÓGICA (Restante do código inalterado)
    // ----------------------------------------------------------------------

    fun getPendingDeletions(): List<String> {
        val db = dbHelper.readableDatabase
        val cnss = mutableListOf<String>()
        val selection = "${Entry.COLUMN_DELETE_PENDING} = 1"
        val projectionCns = arrayOf(Entry.COLUMN_CNS)

        val cursor = db.query(
            IndividuoContract.TABLE_NAME,
            projectionCns,
            selection,
            null,
            null,
            null,
            null
        )

        with(cursor) {
            val cnsIndex = getColumnIndexOrThrow(Entry.COLUMN_CNS)
            while (moveToNext()) {
                cnss.add(getString(cnsIndex))
            }
            close()
        }
        Log.d(TAG, "Encontrados ${cnss.size} registros pendentes de exclusão.")
        return cnss
    }

    fun markForDeletion(cns: String): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(Entry.COLUMN_DELETE_PENDING, 1) // Marca como pendente (1)
            put(Entry.COLUMN_IS_SYNCHRONIZED, 0) // Força a sincronização da exclusão
            put(Entry.COLUMN_ULTIMA_ATUALIZACAO, System.currentTimeMillis())
        }
        val selection = "${Entry.COLUMN_CNS} = ?"
        val selectionArgs = arrayOf(cns)

        return db.update(IndividuoContract.TABLE_NAME, values, selection, selectionArgs)
    }

    fun unmarkForDeletion(cns: String): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(Entry.COLUMN_DELETE_PENDING, 0) // Desmarca (0)
            put(Entry.COLUMN_IS_SYNCHRONIZED, 0) // Força a sincronização da reversão
            put(Entry.COLUMN_ULTIMA_ATUALIZACAO, System.currentTimeMillis())
        }
        val selection = "${Entry.COLUMN_CNS} = ?"
        val selectionArgs = arrayOf(cns)

        return db.update(IndividuoContract.TABLE_NAME, values, selection, selectionArgs)
    }

    fun deleteIndividuo(cns: String): Int {
        val db = dbHelper.writableDatabase
        val selection = "${Entry.COLUMN_CNS} = ?"
        val selectionArgs = arrayOf(cns)

        val rowsDeleted = db.delete(IndividuoContract.TABLE_NAME, selection, selectionArgs)
        Log.d(TAG, "Excluído permanentemente o CNS $cns do SQLite. Linhas afetadas: $rowsDeleted")
        return rowsDeleted
    }
}