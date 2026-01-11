package com.vacinas.vacina.data

import android.provider.BaseColumns

/**
 * Define o esquema do banco de dados para a tabela 'vacinas'.
 */
object VacinaContract {
    const val TABLE_NAME = "vacinas"

    object VacinaEntry : BaseColumns {
        const val COLUMN_CNS_INDIVIDUO = "cns_individuo" // FK para o Individuo
        const val COLUMN_VACINA_KEY = "vacina_key"       // Chave única (PENTA_1_DOSE)

        const val COLUMN_NOME_VACINA = "nome_vacina"
        const val COLUMN_DOSE = "dose"
        const val COLUMN_STATUS = "status"               // 'Aplicada' ou 'Pendente'

        const val COLUMN_DATA_APLICACAO = "data_aplicacao"
        const val COLUMN_LOTE = "lote"
        const val COLUMN_LAB_PRODUT = "lab_produt"
        const val COLUMN_UNIDADE = "unidade"
        const val COLUMN_ASSINATURA_ACS = "assinatura_acs"

        const val COLUMN_DATA_AGENDADA = "data_agendada"

        const val COLUMN_IS_SYNCHRONIZED = "is_synchronized"
        const val COLUMN_ULTIMA_ATUALIZACAO = "ultima_atualizacao"
    }

    // SQL para criação da tabela
    const val SQL_CREATE_TABLE =
        "CREATE TABLE $TABLE_NAME (" +
                "${BaseColumns._ID} INTEGER PRIMARY KEY," +
                "${VacinaEntry.COLUMN_CNS_INDIVIDUO} TEXT NOT NULL," +
                "${VacinaEntry.COLUMN_VACINA_KEY} TEXT NOT NULL," +
                "${VacinaEntry.COLUMN_NOME_VACINA} TEXT NOT NULL," +
                "${VacinaEntry.COLUMN_DOSE} TEXT NOT NULL," +
                "${VacinaEntry.COLUMN_STATUS} TEXT NOT NULL," +
                "${VacinaEntry.COLUMN_DATA_APLICACAO} TEXT," +
                "${VacinaEntry.COLUMN_LOTE} TEXT," +
                "${VacinaEntry.COLUMN_LAB_PRODUT} TEXT," +
                "${VacinaEntry.COLUMN_UNIDADE} TEXT," +
                "${VacinaEntry.COLUMN_ASSINATURA_ACS} TEXT," +
                "${VacinaEntry.COLUMN_DATA_AGENDADA} TEXT," +
                "${VacinaEntry.COLUMN_IS_SYNCHRONIZED} INTEGER NOT NULL DEFAULT 0," +
                "${VacinaEntry.COLUMN_ULTIMA_ATUALIZACAO} INTEGER NOT NULL," +
                "UNIQUE(${VacinaEntry.COLUMN_CNS_INDIVIDUO}, ${VacinaEntry.COLUMN_VACINA_KEY}) ON CONFLICT REPLACE)" // Chave Composta

    const val SQL_DELETE_TABLE = "DROP TABLE IF EXISTS $TABLE_NAME"
}