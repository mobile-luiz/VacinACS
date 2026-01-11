package com.vacinas.vacina.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import android.util.Log
import com.vacinas.vacina.Individuo
import com.vacinas.vacina.VacinaDose
import com.vacinas.vacina.data.IndividuoContract.IndividuoEntry as Entry

class IndividuoDbHelper(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {
    companion object {
        const val DATABASE_NAME = "VacinaMais.db"

        // 🚨 ATUALIZAÇÃO: A versão foi aumentada para 8.
        const val DATABASE_VERSION = 8
    }

    // Comandos SQL para a tabela INDIVIDUOS
    private val SQL_CREATE_INDIVIDUOS =
        "CREATE TABLE ${IndividuoContract.TABLE_NAME} (" +
                "${BaseColumns._ID} INTEGER PRIMARY KEY," +
                "${Entry.COLUMN_CNS} TEXT UNIQUE NOT NULL," +
                "${Entry.COLUMN_NOME} TEXT NOT NULL," +
                "${Entry.COLUMN_DATA_NASCIMENTO} TEXT," +
                "${Entry.COLUMN_NOME_MAE} TEXT," +
                "${Entry.COLUMN_NOME_PAI} TEXT," +
                "${Entry.COLUMN_CELULAR} TEXT," +
                "${Entry.COLUMN_EMAIL} TEXT," +
                "${Entry.COLUMN_ENDERECO} TEXT," +
                "${Entry.COLUMN_PRONTUARIO_FAMILIA} TEXT," +
                "${Entry.COLUMN_STATUS_VISITA} TEXT," +
                "${Entry.COLUMN_ULTIMA_ATUALIZACAO} INTEGER," +
                "${Entry.COLUMN_ULTIMA_ATUALIZACAO_STR} TEXT DEFAULT ''," + // ⭐️ NOVO CAMPO INCLUÍDO
                "${Entry.COLUMN_IS_SYNCHRONIZED} INTEGER DEFAULT 0," +
                "${Entry.COLUMN_DELETE_PENDING} INTEGER DEFAULT 0," +
                "${Entry.COLUMN_REGISTERED_BY_UID} TEXT)"

    private val SQL_DELETE_INDIVIDUOS = "DROP TABLE IF EXISTS ${IndividuoContract.TABLE_NAME}"

    // ⭐️ NOVO: Comandos SQL para a tabela de Perfil ACS (Mantido da v7)
    private val SQL_CREATE_ACS_PROFILE =
        "CREATE TABLE acs_profile (" +
                "uid TEXT PRIMARY KEY NOT NULL," +
                "nome TEXT NOT NULL," +
                "email TEXT," +
                "is_synchronized INTEGER DEFAULT 0)"

    override fun onCreate(db: SQLiteDatabase) {
        // 1. Cria a tabela de Indivíduos (agora com COLUMN_ULTIMA_ATUALIZACAO_STR)
        db.execSQL(SQL_CREATE_INDIVIDUOS)

        // 2. Cria a tabela de Vacinas
        db.execSQL(VacinaContract.SQL_CREATE_TABLE)

        // 3. Cria a tabela de Perfil ACS
        db.execSQL(SQL_CREATE_ACS_PROFILE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

        // --- Migrações de Indivíduos (V3 para V4) ---
        if (oldVersion < 4) {
            try {
                // Adiciona a coluna REGISTERED_BY_UID
                db.execSQL("ALTER TABLE ${IndividuoContract.TABLE_NAME} ADD COLUMN ${Entry.COLUMN_REGISTERED_BY_UID} TEXT DEFAULT ''")
                Log.d("DbHelper", "Migração V4: Coluna ${Entry.COLUMN_REGISTERED_BY_UID} adicionada.")
            } catch (e: Exception) {
                Log.e("DbHelper", "Erro V4: Coluna ${Entry.COLUMN_REGISTERED_BY_UID} já deve existir ou falha: ${e.message}")
            }
        }

        // --- Migrações de Vacinas (V4 para V5) ---
        if (oldVersion < 5) {
            try {
                // Cria a tabela de Vacinas
                db.execSQL(VacinaContract.SQL_CREATE_TABLE)
                Log.d("DbHelper", "Migração V5: Tabela ${VacinaContract.TABLE_NAME} criada com sucesso.")
            } catch (e: Exception) {
                Log.e("DbHelper", "Erro V5: Falha ao criar a tabela de vacinas: ${e.message}")
            }
        }

        // --- Migrações de V6 para V7 ---
        if (oldVersion < 7) {
            try {
                // Migração V7: Cria a tabela de Perfil ACS, se não existir
                db.execSQL(SQL_CREATE_ACS_PROFILE)
                Log.d("DbHelper", "Migração V7: Tabela acs_profile criada com sucesso.")
            } catch (e: Exception) {
                Log.e("DbHelper", "Erro V7: Falha ao criar a tabela acs_profile: ${e.message}")
            }
        }

        // 🚨 MIGRAÇÃO V7 PARA V8 🚨
        if (oldVersion < 8) {
            try {
                // Adiciona a coluna COLUMN_ULTIMA_ATUALIZACAO_STR para o agendamento de notificação.
                val alterTableSql = "ALTER TABLE ${IndividuoContract.TABLE_NAME} ADD COLUMN ${Entry.COLUMN_ULTIMA_ATUALIZACAO_STR} TEXT DEFAULT '';"
                db.execSQL(alterTableSql)
                Log.d("DbHelper", "Migração V8: Coluna ${Entry.COLUMN_ULTIMA_ATUALIZACAO_STR} adicionada.")
            } catch (e: Exception) {
                Log.e("DbHelper", "Erro V8: Falha ao adicionar ${Entry.COLUMN_ULTIMA_ATUALIZACAO_STR}: ${e.message}")
            }
        }

        // Para testes ou problemas críticos (MÉTODO DESTRUTIVO):
        // if (oldVersion < newVersion && newVersion >= 8) {
        //     db.execSQL(SQL_DELETE_INDIVIDUOS)
        //     db.execSQL(VacinaContract.SQL_DELETE_TABLE)
        //     db.execSQL("DROP TABLE IF EXISTS acs_profile")
        //     onCreate(db)
        // }
    }

    // -----------------------------------------------------------------------------------------
    // MÉTODOS DE ATUALIZAÇÃO DE INDIVÍDUO
    // -----------------------------------------------------------------------------------------

    /**
     * Atualiza todos os detalhes (nome, cns, contatos, pais) de um indivíduo existente
     * com base no CNS (identificador único).
     */
    fun updateIndividuoFullDetails(individuo: Individuo): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(Entry.COLUMN_NOME, individuo.nome)
            put(Entry.COLUMN_DATA_NASCIMENTO, individuo.dataNascimento)
            put(Entry.COLUMN_NOME_MAE, individuo.nomeMae)
            put(Entry.COLUMN_NOME_PAI, individuo.nomePai)
            put(Entry.COLUMN_CELULAR, individuo.celular)
            put(Entry.COLUMN_EMAIL, individuo.email)
            put(Entry.COLUMN_ENDERECO, individuo.endereco)
            put(Entry.COLUMN_PRONTUARIO_FAMILIA, individuo.prontuarioFamilia)
            put(Entry.COLUMN_ULTIMA_ATUALIZACAO, individuo.ultimaAtualizacao)
            put(Entry.COLUMN_ULTIMA_ATUALIZACAO_STR, individuo.ultimaAtualizacaoStr) // Incluído
            put(Entry.COLUMN_IS_SYNCHRONIZED, 0) // Marca como não sincronizado
            put(Entry.COLUMN_REGISTERED_BY_UID, individuo.registeredByUid)
        }

        val selection = "${Entry.COLUMN_CNS} = ?"
        val selectionArgs = arrayOf(individuo.cns)

        val count = db.update(
            IndividuoContract.TABLE_NAME,
            values,
            selection,
            selectionArgs
        )
        return count > 0
    }

    /**
     * Atualiza o status da visita (Visitado ou Agendado) e o timestamp da última atualização
     * para um indivíduo com o CNS fornecido.
     * ⭐️ ATUALIZADO: Recebe a data formatada (ultimaAtualizacaoStr)
     */
    fun updateIndividuoVisitStatus(cns: String, statusVisita: String, ultimaAtualizacao: Long, ultimaAtualizacaoStr: String): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(Entry.COLUMN_STATUS_VISITA, statusVisita)
            put(Entry.COLUMN_ULTIMA_ATUALIZACAO, ultimaAtualizacao)
            put(Entry.COLUMN_ULTIMA_ATUALIZACAO_STR, ultimaAtualizacaoStr) // ⭐️ SALVANDO O NOVO CAMPO
            put(Entry.COLUMN_IS_SYNCHRONIZED, 0) // Marca como não sincronizado
        }

        val selection = "${Entry.COLUMN_CNS} = ?"
        val selectionArgs = arrayOf(cns)

        val count = db.update(
            IndividuoContract.TABLE_NAME,
            values,
            selection,
            selectionArgs
        )
        return count > 0
    }


    // -----------------------------------------------------------------------------------------
    // MÉTODOS DE BUSCA DE DOSES (Mantidos - Nenhuma alteração necessária)
    // -----------------------------------------------------------------------------------------

    /**
     * Busca todas as doses de vacina associadas a um CNS de indivíduo.
     */
    fun getVacinaDosesByCns(cnsIndividuo: String): List<VacinaDose> {
        val db = readableDatabase
        val projection = arrayOf(
            BaseColumns._ID,
            VacinaContract.VacinaEntry.COLUMN_CNS_INDIVIDUO,
            VacinaContract.VacinaEntry.COLUMN_VACINA_KEY,
            VacinaContract.VacinaEntry.COLUMN_NOME_VACINA,
            VacinaContract.VacinaEntry.COLUMN_DOSE,
            VacinaContract.VacinaEntry.COLUMN_STATUS,
            VacinaContract.VacinaEntry.COLUMN_DATA_APLICACAO,
            VacinaContract.VacinaEntry.COLUMN_LOTE,
            VacinaContract.VacinaEntry.COLUMN_LAB_PRODUT,
            VacinaContract.VacinaEntry.COLUMN_UNIDADE,
            VacinaContract.VacinaEntry.COLUMN_ASSINATURA_ACS,
            VacinaContract.VacinaEntry.COLUMN_DATA_AGENDADA,
            VacinaContract.VacinaEntry.COLUMN_IS_SYNCHRONIZED,
            VacinaContract.VacinaEntry.COLUMN_ULTIMA_ATUALIZACAO
        )

        val selection = "${VacinaContract.VacinaEntry.COLUMN_CNS_INDIVIDUO} = ?"
        val selectionArgs = arrayOf(cnsIndividuo)

        val sortOrder = "${VacinaContract.VacinaEntry.COLUMN_VACINA_KEY} ASC"

        val cursor = db.query(
            VacinaContract.TABLE_NAME,
            projection,
            selection,
            selectionArgs,
            null,
            null,
            sortOrder
        )

        val dosesList = mutableListOf<VacinaDose>()
        with(cursor) {
            while (moveToNext()) {
                val id = getLong(getColumnIndexOrThrow(BaseColumns._ID))
                val cns = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_CNS_INDIVIDUO))
                val vacinaKey = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_VACINA_KEY))
                val nomeVacina = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_NOME_VACINA))
                val dose = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_DOSE))
                val status = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_STATUS))

                val dataAplicacao = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_DATA_APLICACAO))
                val lote = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_LOTE))
                val labProdut = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_LAB_PRODUT))
                val unidade = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_UNIDADE))
                val assinaturaAcs = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_ASSINATURA_ACS))
                val dataAgendada = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_DATA_AGENDADA))

                val isSynchronized = getInt(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_IS_SYNCHRONIZED)) == 1
                val ultimaAtualizacao = getLong(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_ULTIMA_ATUALIZACAO))

                dosesList.add(
                    VacinaDose(
                        id = id,
                        cnsIndividuo = cns,
                        vacinaKey = vacinaKey,
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
                        ultimaAtualizacao = ultimaAtualizacao,
                        pacienteNome = ""
                    )
                )
            }
        }
        cursor.close()
        return dosesList
    }

    /**
     * Busca doses de vacina filtradas pelo UID do ACS.
     */
    fun getVacinaDosesByAcs(acsUid: String): List<VacinaDose> {
        val db = readableDatabase

        // 1. Definição da Consulta SQL com JOIN e WHERE
        val query = """
            SELECT 
                V.${BaseColumns._ID},
                V.${VacinaContract.VacinaEntry.COLUMN_CNS_INDIVIDUO},
                V.${VacinaContract.VacinaEntry.COLUMN_VACINA_KEY},
                V.${VacinaContract.VacinaEntry.COLUMN_NOME_VACINA},
                V.${VacinaContract.VacinaEntry.COLUMN_DOSE},
                V.${VacinaContract.VacinaEntry.COLUMN_STATUS},
                V.${VacinaContract.VacinaEntry.COLUMN_DATA_APLICACAO},
                V.${VacinaContract.VacinaEntry.COLUMN_LOTE},
                V.${VacinaContract.VacinaEntry.COLUMN_LAB_PRODUT},
                V.${VacinaContract.VacinaEntry.COLUMN_UNIDADE},
                V.${VacinaContract.VacinaEntry.COLUMN_ASSINATURA_ACS},
                V.${VacinaContract.VacinaEntry.COLUMN_DATA_AGENDADA},
                V.${VacinaContract.VacinaEntry.COLUMN_IS_SYNCHRONIZED},
                V.${VacinaContract.VacinaEntry.COLUMN_ULTIMA_ATUALIZACAO},
                I.${Entry.COLUMN_NOME} AS pacienteNome  
            FROM 
                ${VacinaContract.TABLE_NAME} AS V
            INNER JOIN 
                ${IndividuoContract.TABLE_NAME} AS I 
            ON 
                V.${VacinaContract.VacinaEntry.COLUMN_CNS_INDIVIDUO} = I.${Entry.COLUMN_CNS}
            WHERE 
                I.${Entry.COLUMN_REGISTERED_BY_UID} = ?  -- CLÁUSULA WHERE PARA FILTRO
            ORDER BY
                I.${Entry.COLUMN_NOME} ASC,
                V.${VacinaContract.VacinaEntry.COLUMN_VACINA_KEY} ASC
        """.trimIndent()

        // 2. Executa a consulta, passando o acsUid como argumento de seleção
        val selectionArgs = arrayOf(acsUid)
        val cursor = db.rawQuery(query, selectionArgs)

        val dosesList = mutableListOf<VacinaDose>()
        with(cursor) {
            while (moveToNext()) {
                val id = getLong(getColumnIndexOrThrow(BaseColumns._ID))
                val cns = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_CNS_INDIVIDUO))
                val vacinaKey = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_VACINA_KEY))
                val nomeVacina = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_NOME_VACINA))
                val dose = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_DOSE))
                val status = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_STATUS))

                val dataAplicacao = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_DATA_APLICACAO))
                val lote = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_LOTE))
                val labProdut = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_LAB_PRODUT))
                val unidade = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_UNIDADE))
                val assinaturaAcs = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_ASSINATURA_ACS))
                val dataAgendada = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_DATA_AGENDADA))

                val isSynchronized = getInt(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_IS_SYNCHRONIZED)) == 1
                val ultimaAtualizacao = getLong(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_ULTIMA_ATUALIZACAO))

                // BUSCA O NOME DO PACIENTE DO JOIN
                val pacienteNome = getString(getColumnIndexOrThrow("pacienteNome"))

                dosesList.add(
                    VacinaDose(
                        id = id,
                        cnsIndividuo = cns,
                        vacinaKey = vacinaKey,
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
                        ultimaAtualizacao = ultimaAtualizacao,
                        pacienteNome = pacienteNome // Nome do paciente preenchido!
                    )
                )
            }
        }
        cursor.close()
        return dosesList
    }


    /**
     * Busca TODAS as doses, realizando um JOIN com a tabela de Indivíduos
     * para incluir o nome do paciente em cada dose.
     */
    fun getAllVacinaDoses(): List<VacinaDose> {
        val db = readableDatabase

        // 1. Definição da Consulta SQL com JOIN
        val query = """
            SELECT 
                V.${BaseColumns._ID},
                V.${VacinaContract.VacinaEntry.COLUMN_CNS_INDIVIDUO},
                V.${VacinaContract.VacinaEntry.COLUMN_VACINA_KEY},
                V.${VacinaContract.VacinaEntry.COLUMN_NOME_VACINA},
                V.${VacinaContract.VacinaEntry.COLUMN_DOSE},
                V.${VacinaContract.VacinaEntry.COLUMN_STATUS},
                V.${VacinaContract.VacinaEntry.COLUMN_DATA_APLICACAO},
                V.${VacinaContract.VacinaEntry.COLUMN_LOTE},
                V.${VacinaContract.VacinaEntry.COLUMN_LAB_PRODUT},
                V.${VacinaContract.VacinaEntry.COLUMN_UNIDADE},
                V.${VacinaContract.VacinaEntry.COLUMN_ASSINATURA_ACS},
                V.${VacinaContract.VacinaEntry.COLUMN_DATA_AGENDADA},
                V.${VacinaContract.VacinaEntry.COLUMN_IS_SYNCHRONIZED},
                V.${VacinaContract.VacinaEntry.COLUMN_ULTIMA_ATUALIZACAO},
                I.${Entry.COLUMN_NOME} AS pacienteNome  -- Coluna do nome do indivíduo
            FROM 
                ${VacinaContract.TABLE_NAME} AS V
            INNER JOIN 
                ${IndividuoContract.TABLE_NAME} AS I 
            ON 
                V.${VacinaContract.VacinaEntry.COLUMN_CNS_INDIVIDUO} = I.${Entry.COLUMN_CNS}
            ORDER BY
                I.${Entry.COLUMN_NOME} ASC,
                V.${VacinaContract.VacinaEntry.COLUMN_VACINA_KEY} ASC
        """.trimIndent()

        val cursor = db.rawQuery(query, null)

        val dosesList = mutableListOf<VacinaDose>()
        with(cursor) {
            while (moveToNext()) {
                val id = getLong(getColumnIndexOrThrow(BaseColumns._ID))
                val cns = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_CNS_INDIVIDUO))
                val vacinaKey = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_VACINA_KEY))
                val nomeVacina = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_NOME_VACINA))
                val dose = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_DOSE))
                val status = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_STATUS))

                val dataAplicacao = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_DATA_APLICACAO))
                val lote = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_LOTE))
                val labProdut = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_LAB_PRODUT))
                val unidade = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_UNIDADE))
                val assinaturaAcs = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_ASSINATURA_ACS))
                val dataAgendada = getString(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_DATA_AGENDADA))

                val isSynchronized = getInt(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_IS_SYNCHRONIZED)) == 1
                val ultimaAtualizacao = getLong(getColumnIndexOrThrow(VacinaContract.VacinaEntry.COLUMN_ULTIMA_ATUALIZACAO))

                // BUSCA O NOME DO PACIENTE DO JOIN
                val pacienteNome = getString(getColumnIndexOrThrow("pacienteNome"))

                dosesList.add(
                    VacinaDose(
                        id = id,
                        cnsIndividuo = cns,
                        vacinaKey = vacinaKey,
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
                        ultimaAtualizacao = ultimaAtualizacao,
                        pacienteNome = pacienteNome // Nome do paciente preenchido!
                    )
                )
            }
        }
        cursor.close()
        return dosesList
    }
}