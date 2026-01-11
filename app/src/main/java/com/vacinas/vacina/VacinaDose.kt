package com.vacinas.vacina

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Data class que representa uma dose de vacina (aplicada ou agendada)
 * de um indivíduo para PERSISTÊNCIA LOCAL (SQLite/Room) E PARA SERVIÇOS (Firebase).
 */
@Parcelize
data class VacinaDose(
    // ID para o banco de dados local (geralmente gerado pelo Room, deve ser 0 para inserção)
    val id: Long = 0,

    // 🔑 Campos de Chave (Para o Firebase, o CNS é a chave principal do paciente)
    val cnsIndividuo: String = "",
    val vacinaKey: String = "",

    // ➡️ NOVO CAMPO INCLUÍDO PARA RASTREAR O PACIENTE NA LISTA MESTRA
    val pacienteNome: String = "",

    // 🌟 CAMPOS DE DETALHE DO PACIENTE ADICIONADOS (RESOLVENDO UNRESOLVED REFERENCE)
    val dataNascimento: String? = null,
    val pacienteEndereco: String? = null,
    val pacienteEmail: String? = null,

    // --------------------------------------------------------------------------

    // Dados da Vacina
    val nomeVacina: String = "",
    val dose: String = "",
    // Atualização do comentário para refletir todos os valores possíveis.
    val status: String = "", // "Aplicada", "Pendente" ou "Cancelado" ⭐️

    // Dados de Aplicação (preenchidos quando status="Aplicada")
    val dataAplicacao: String? = null,
    val lote: String? = null,
    val labProdut: String? = null,
    val unidade: String? = null,
    val assinaturaAcs: String? = null,

    // Dados de Agendamento (preenchidos quando status="Pendente")
    val dataAgendada: String? = null,

    // Dados de Sincronização
    val isSynchronized: Boolean = false,
    val ultimaAtualizacao: Long = 0 // Timestamp para controle de ordem e sincronização
) : Parcelable