package com.vacinas.vacina

import android.os.Parcelable
import com.google.firebase.database.Exclude
import com.google.firebase.database.PropertyName
import kotlinx.parcelize.Parcelize

/**
 * Data class para o indivíduo.
 * Contém todos os campos de dados e status.
 */
@Parcelize
data class Individuo(
    // Identificador interno do SQLite/Room (DEVE ser o CNS formatado)
    // Usamos 'var' para que o Room possa injetar o valor.
    // É excluído do Firebase para não salvar a chave duas vezes.
    @get:Exclude
    var id: String = "",

    // Identificador principal
    var cns: String = "", // CNS no formato original (com pontos/traços)

    // Dados Pessoais
    var nome: String = "",
    var dataNascimento: String = "",
    var nomeMae: String = "",
    var nomePai: String = "",

    // Contato
    var celular: String = "",
    var email: String = "",

    // Endereço e Família
    var endereco: String = "",
    var prontuarioFamilia: String = "",

    // CAMPOS DE STATUS DE VISITA
    var statusVisita: String = "Sem visita",
    var ultimaAtualizacao: Long = System.currentTimeMillis(),

    // ⭐️ NOVO CAMPO: Salva a data formatada (DD/MM/AAAA) para leitura humana no DB.
    var ultimaAtualizacaoStr: String = "",

    // CAMPO DE VINCULAÇÃO DE USUÁRIO (ACS)
    var registeredByUid: String = "",

    // Status de Sincronização e Deleção
    @get:PropertyName("synchronized")
    @set:PropertyName("synchronized")
    var isSynchronized: Boolean = false,

    var deletePending: Boolean = false,

    // CAMPO PARA SER PREENCHIDO COM DADOS DO ROOM/SQLITE (NÃO VAI PARA O FIREBASE)
    @get:Exclude
    val vacinasLocais: List<VacinaDose> = emptyList(),

    // -------------------------------------------------------------------------
    // 💉 NOVOS CAMPOS DE STATUS DE VACINA (Temporários/Cálculos) 💉
    // -------------------------------------------------------------------------
    @get:Exclude // Excluído do Firebase
    var proximaVacinaNome: String? = null, // Ex: "Penta (2ª Dose)"

    // O tipo é Map<String, VacinaDose> porque 'vacinas' é um nó de objetos com chaves automáticas.
    var vacinas: Map<String, VacinaDose>? = null,


    @get:Exclude // Excluído do Firebase
    var dataAgendadaProximaDose: Long? = null // Timestamp da próxima dose (meia-noite UTC)

) : Parcelable