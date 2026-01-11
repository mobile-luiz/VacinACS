package com.vacinas.vacina

data class Usuario(
    // Certifique-se de que os nomes das variáveis coincidem com os campos do seu Firebase
    var nome: String = "",
    var email: String = "",
    var ativo: Boolean = true,
    var data_criacao: Long = 0,
    var uid: String = ""
)