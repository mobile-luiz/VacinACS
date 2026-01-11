package com.vacinas.vacina.data

import android.provider.BaseColumns

/**
 * Define nomes de tabelas e colunas para o banco de dados de indivíduos.
 */
object IndividuoContract {

    // Nome da tabela
    const val TABLE_NAME = "individuo"

    // Colunas para a tabela de Indivíduos
    object IndividuoEntry : BaseColumns {

        // Colunas de dados existentes
        const val COLUMN_CNS = "cns"
        const val  COLUMN_NOME = "nome"
        const val COLUMN_DATA_NASCIMENTO = "data_nascimento"
        const val COLUMN_NOME_MAE = "nome_mae"
        const val COLUMN_NOME_PAI = "nome_pai"
        const val COLUMN_CELULAR = "celular"
        const val COLUMN_EMAIL = "email"
        const val COLUMN_ENDERECO = "endereco"
        const val COLUMN_PRONTUARIO_FAMILIA = "prontuario_familia"
        const val COLUMN_STATUS_VISITA = "status_visita"
        const val COLUMN_ULTIMA_ATUALIZACAO = "ultima_atualizacao"

        // ⭐️ NOVO CAMPO: Para armazenar a data da última atualização formatada (DD/MM/AAAA)
        const val COLUMN_ULTIMA_ATUALIZACAO_STR = "ultima_atualizacao_str"

        // Colunas de Sincronização e Auditoria (Adicionadas/Confirmadas)
        const val COLUMN_IS_SYNCHRONIZED = "is_synchronized"
        const val COLUMN_DELETE_PENDING = "delete_pending"
        // Coluna chave usada para vincular o indivíduo ao Agente Comunitário de Saúde (ACS)
        const val COLUMN_REGISTERED_BY_UID = "registered_by_uid"
    }
}