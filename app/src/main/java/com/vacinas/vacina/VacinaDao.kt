package com.vacinas.vacina.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import android.util.Log
import com.vacinas.vacina.VacinaDose
import com.vacinas.vacina.data.VacinaContract.VacinaEntry as Entry
import com.vacinas.vacina.data.IndividuoContract.IndividuoEntry as IndividuoEntry


/**
 * Data Access Object (DAO) para a tabela 'vacinas'.
 */
class VacinaDao(context: Context) {

    // ⚠️ Assumindo que IndividuoDbHelper é o SQLiteOpenHelper principal
    private val dbHelper: IndividuoDbHelper = IndividuoDbHelper(context)
    private val TAG = "VacinaDao"

    // ---------------------------------------------------------------------------------
    // PERSISTÊNCIA (INSERT ou UPDATE)
    // ---------------------------------------------------------------------------------

    /**
     * Insere uma nova dose de vacina ou atualiza uma dose existente (baseado no CNS + VacinaKey).
     */
    fun saveOrUpdate(dose: VacinaDose): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(Entry.COLUMN_CNS_INDIVIDUO, dose.cnsIndividuo)
            put(Entry.COLUMN_VACINA_KEY, dose.vacinaKey)
            put(Entry.COLUMN_NOME_VACINA, dose.nomeVacina)
            put(Entry.COLUMN_DOSE, dose.dose)
            put(Entry.COLUMN_STATUS, dose.status)
            put(Entry.COLUMN_DATA_APLICACAO, dose.dataAplicacao)
            put(Entry.COLUMN_LOTE, dose.lote)
            put(Entry.COLUMN_LAB_PRODUT, dose.labProdut)
            put(Entry.COLUMN_UNIDADE, dose.unidade)
            put(Entry.COLUMN_ASSINATURA_ACS, dose.assinaturaAcs)
            put(Entry.COLUMN_DATA_AGENDADA, dose.dataAgendada)
            put(Entry.COLUMN_IS_SYNCHRONIZED, if (dose.isSynchronized) 1 else 0)
            put(Entry.COLUMN_ULTIMA_ATUALIZACAO, dose.ultimaAtualizacao)
        }
        // ⚠️ Nota: Os novos campos do Paciente (pacienteNome, dataNascimento, pacienteEndereco, pacienteEmail)
        // não são gravados na tabela de Vacinas, assumindo que já estão na tabela de Indivíduos.

        // Condição de atualização: CNS E VacinaKey
        val selection = "${Entry.COLUMN_CNS_INDIVIDUO} = ? AND ${Entry.COLUMN_VACINA_KEY} = ?"
        val selectionArgs = arrayOf(dose.cnsIndividuo, dose.vacinaKey)

        val count = db.update(
            VacinaContract.TABLE_NAME,
            values,
            selection,
            selectionArgs
        )
        // db.close() // 🛑 REMOVIDO: Evita o erro "attempt to re-open an already-closed object"

        return if (count == 0) {
            val insertId = db.insert(VacinaContract.TABLE_NAME, null, values)
            Log.d(TAG, "Dose ${dose.vacinaKey} inserida. ID: $insertId")
            insertId
        } else {
            Log.d(TAG, "Dose ${dose.vacinaKey} atualizada. Linhas: $count")
            count.toLong()
        }
    }

    // ---------------------------------------------------------------------------------
    // BUSCA POR CHAVE (PARA AGENDAMENTO/CANCELAMENTO)
    // ---------------------------------------------------------------------------------

    /**
     * Busca uma dose específica no banco de dados local pelo CNS e pela chave da vacina (VacinaKey).
     */
    fun getVacinaDose(cns: String, vacinaKey: String): VacinaDose? {
        val db = dbHelper.readableDatabase
        var dose: VacinaDose? = null

        val selection = "${Entry.COLUMN_CNS_INDIVIDUO} = ? AND ${Entry.COLUMN_VACINA_KEY} = ?"
        val selectionArgs = arrayOf(cns, vacinaKey)

        val cursor = db.query(
            VacinaContract.TABLE_NAME,
            null, // Retorna todas as colunas
            selection,
            selectionArgs,
            null, null, null
        )

        cursor.use { c ->
            if (c.moveToFirst()) {
                dose = cursorToVacinaDose(c)
            }
        }
        // db.close() // 🛑 REMOVIDO: Evita o erro de reabertura após o uso
        Log.d(TAG, "Busca Dose $vacinaKey para CNS $cns: ${if (dose != null) "ENCONTRADA" else "NÃO ENCONTRADA"}")
        return dose
    }


    // ---------------------------------------------------------------------------------
    // SINCRONIZAÇÃO E BUSCA (db.close() removidos)
    // ---------------------------------------------------------------------------------

    /**
     * Retorna todas as doses associadas aos indivíduos de um ACS.
     */
    fun getVacinaDosesByAcs(acsUid: String): List<VacinaDose> {
        val db = dbHelper.readableDatabase
        val doses = mutableListOf<VacinaDose>()
        val query = """
            SELECT V.* FROM ${VacinaContract.TABLE_NAME} V
            INNER JOIN ${IndividuoContract.TABLE_NAME} I 
            ON V.${Entry.COLUMN_CNS_INDIVIDUO} = I.${IndividuoEntry.COLUMN_CNS}
            WHERE I.${IndividuoEntry.COLUMN_REGISTERED_BY_UID} = ?  
        """.trimIndent()
        val cursor = db.rawQuery(query, arrayOf(acsUid))

        with(cursor) {
            while (moveToNext()) {
                doses.add(cursorToVacinaDose(this))
            }
        }
        cursor.close()
        // db.close() // 🛑 REMOVIDO
        Log.d(TAG, "Encontradas ${doses.size} doses para o ACS $acsUid.")
        return doses
    }

    /**
     * Retorna todas as doses de vacina de um indivíduo específico, baseadas no CNS.
     */
    fun getVacinasByCns(cns: String): List<VacinaDose> {
        val db = dbHelper.readableDatabase
        val projection = arrayOf(
            BaseColumns._ID,
            Entry.COLUMN_CNS_INDIVIDUO, Entry.COLUMN_VACINA_KEY, Entry.COLUMN_NOME_VACINA,
            Entry.COLUMN_DOSE, Entry.COLUMN_STATUS, Entry.COLUMN_DATA_APLICACAO,
            Entry.COLUMN_LOTE, Entry.COLUMN_LAB_PRODUT, Entry.COLUMN_UNIDADE,
            Entry.COLUMN_ASSINATURA_ACS, Entry.COLUMN_DATA_AGENDADA,
            Entry.COLUMN_IS_SYNCHRONIZED, Entry.COLUMN_ULTIMA_ATUALIZACAO
        )

        val selection = "${Entry.COLUMN_CNS_INDIVIDUO} = ?"
        val selectionArgs = arrayOf(cns)

        val cursor = db.query(
            VacinaContract.TABLE_NAME,
            projection,
            selection,
            selectionArgs,
            null, null, "${Entry.COLUMN_VACINA_KEY} ASC"
        )

        val doses = mutableListOf<VacinaDose>()
        with(cursor) {
            while (moveToNext()) {
                doses.add(cursorToVacinaDose(this))
            }
        }
        cursor.close()
        // db.close() // 🛑 REMOVIDO
        Log.d(TAG, "Encontradas ${doses.size} doses para o CNS $cns.")
        return doses
    }

    /**
     * Retorna todas as doses NÃO sincronizadas de um CNS específico.
     */
    fun getUnsynchronizedVacinas(cns: String): List<VacinaDose> {
        val db = dbHelper.readableDatabase
        val doses = mutableListOf<VacinaDose>()
        val selection = "${Entry.COLUMN_CNS_INDIVIDUO} = ? AND ${Entry.COLUMN_IS_SYNCHRONIZED} = 0"
        val selectionArgs = arrayOf(cns)

        val cursor = db.query(
            VacinaContract.TABLE_NAME,
            null, selection, selectionArgs, null, null, null
        )

        with(cursor) {
            while (moveToNext()) {
                doses.add(cursorToVacinaDose(this))
            }
        }
        cursor.close()
        // db.close() // 🛑 REMOVIDO
        Log.d(TAG, "Encontradas ${doses.size} doses NÃO sincronizadas para o CNS $cns.")
        return doses
    }

    /**
     * Retorna todas as doses que não foram sincronizadas (is_synchronized = 0).
     */
    fun getUnsyncedDoses(): List<VacinaDose> {
        val db = dbHelper.readableDatabase
        val projection = arrayOf(
            BaseColumns._ID,
            Entry.COLUMN_CNS_INDIVIDUO, Entry.COLUMN_VACINA_KEY, Entry.COLUMN_NOME_VACINA,
            Entry.COLUMN_DOSE, Entry.COLUMN_STATUS, Entry.COLUMN_DATA_APLICACAO,
            Entry.COLUMN_LOTE, Entry.COLUMN_LAB_PRODUT, Entry.COLUMN_UNIDADE,
            Entry.COLUMN_ASSINATURA_ACS, Entry.COLUMN_DATA_AGENDADA,
            Entry.COLUMN_IS_SYNCHRONIZED, Entry.COLUMN_ULTIMA_ATUALIZACAO
        )

        val selection = "${Entry.COLUMN_IS_SYNCHRONIZED} = ?"
        val selectionArgs = arrayOf("0")

        val cursor = db.query(
            VacinaContract.TABLE_NAME,
            projection, selection, selectionArgs, null, null,
            "${Entry.COLUMN_ULTIMA_ATUALIZACAO} ASC"
        )

        val doses = mutableListOf<VacinaDose>()
        with(cursor) {
            while (moveToNext()) {
                doses.add(cursorToVacinaDose(this))
            }
        }
        cursor.close()
        // db.close() // 🛑 REMOVIDO
        Log.d(TAG, "Encontradas ${doses.size} doses para PUSH em massa.")
        return doses
    }

    /**
     * Marca uma dose como sincronizada no SQLite.
     */
    fun markAsSynced(cns: String, vacinaKey: String, lastModified: Long): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(Entry.COLUMN_IS_SYNCHRONIZED, 1) // TRUE
            put(Entry.COLUMN_ULTIMA_ATUALIZACAO, lastModified)
        }
        val selection = "${Entry.COLUMN_CNS_INDIVIDUO} = ? AND ${Entry.COLUMN_VACINA_KEY} = ?"
        val selectionArgs = arrayOf(cns, vacinaKey)

        val rowsUpdated = db.update(VacinaContract.TABLE_NAME, values, selection, selectionArgs)
        // db.close() // 🛑 REMOVIDO
        Log.d(TAG, "Dose $vacinaKey do CNS $cns atualizada para TRUE. Linhas: $rowsUpdated")
        return rowsUpdated
    }

    // ---------------------------------------------------------------------------------
    // 🗑️ EXCLUSÃO (db.close() removidos)
    // ---------------------------------------------------------------------------------

    /**
     * Exclui permanentemente todas as doses de vacina de um indivíduo específico do SQLite.
     */
    fun deleteDosesByCns(cns: String): Int {
        val db = dbHelper.writableDatabase
        val selection = "${VacinaContract.VacinaEntry.COLUMN_CNS_INDIVIDUO} = ?"
        val selectionArgs = arrayOf(cns)

        val rowsDeleted = db.delete(VacinaContract.TABLE_NAME, selection, selectionArgs)
        // db.close() // 🛑 REMOVIDO
        Log.d(TAG, "Excluídas $rowsDeleted doses para o CNS $cns.")
        return rowsDeleted
    }

    /**
     * EXCLUI uma dose específica usando o CNS e a VacinaKey.
     */
    fun deleteVacinaDose(cns: String, vacinaKey: String): Int {
        val db = dbHelper.writableDatabase

        // Condição de exclusão: CNS E VacinaKey
        val selection = "${Entry.COLUMN_CNS_INDIVIDUO} = ? AND ${Entry.COLUMN_VACINA_KEY} = ?"
        val selectionArgs = arrayOf(cns, vacinaKey)

        val rowsDeleted = db.delete(VacinaContract.TABLE_NAME, selection, selectionArgs)
        // db.close() // 🛑 REMOVIDO
        Log.d(TAG, "Excluídas $rowsDeleted doses para o CNS $cns e VacinaKey $vacinaKey (agendamento cancelado).")
        return rowsDeleted
    }


    /**
     * Mapeia um Cursor para um objeto VacinaDose.
     * ⭐️ ATUALIZADO para incluir novos campos do modelo VacinaDose, usando valores padrão (null ou "").
     * NOTA: Esses campos não estão na tabela de Vacinas, então são inicializados para null/"" para evitar erros.
     */
    private fun cursorToVacinaDose(cursor: Cursor): VacinaDose {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID))
        val cnsIndividuo = cursor.getString(cursor.getColumnIndexOrThrow(Entry.COLUMN_CNS_INDIVIDUO))
        val vacinaKey = cursor.getString(cursor.getColumnIndexOrThrow(Entry.COLUMN_VACINA_KEY))
        val nomeVacina = cursor.getString(cursor.getColumnIndexOrThrow(Entry.COLUMN_NOME_VACINA))
        val dose = cursor.getString(cursor.getColumnIndexOrThrow(Entry.COLUMN_DOSE))
        val status = cursor.getString(cursor.getColumnIndexOrThrow(Entry.COLUMN_STATUS))
        val dataAplicacao = cursor.getString(cursor.getColumnIndexOrThrow(Entry.COLUMN_DATA_APLICACAO))
        val lote = cursor.getString(cursor.getColumnIndexOrThrow(Entry.COLUMN_LOTE))
        val labProdut = cursor.getString(cursor.getColumnIndexOrThrow(Entry.COLUMN_LAB_PRODUT))
        val unidade = cursor.getString(cursor.getColumnIndexOrThrow(Entry.COLUMN_UNIDADE))
        val assinaturaAcs = cursor.getString(cursor.getColumnIndexOrThrow(Entry.COLUMN_ASSINATURA_ACS))
        val dataAgendada = cursor.getString(cursor.getColumnIndexOrThrow(Entry.COLUMN_DATA_AGENDADA))
        val isSynchronized = cursor.getInt(cursor.getColumnIndexOrThrow(Entry.COLUMN_IS_SYNCHRONIZED)) == 1
        val ultimaAtualizacao = cursor.getLong(cursor.getColumnIndexOrThrow(Entry.COLUMN_ULTIMA_ATUALIZACAO))

        return VacinaDose(
            id = id,
            cnsIndividuo = cnsIndividuo,
            vacinaKey = vacinaKey,
            // ⭐️ Novos campos do Paciente (inicializados como padrão, pois não estão nesta tabela)
            pacienteNome = "",
            dataNascimento = null,
            pacienteEndereco = null,
            pacienteEmail = null,
            // --------------------------------------------------------------------------
            nomeVacina = nomeVacina,
            dose = dose,
            status = status,
            dataAplicacao = dataAplicacao,
            lote = lote,
            labProdut = labProdut,
            unidade = unidade,
            assinaturaAcs = assinaturaAcs,
            dataAgendada = dataAgendada,
            isSynchronized = isSynchronized,
            ultimaAtualizacao = ultimaAtualizacao
        )
    }
}