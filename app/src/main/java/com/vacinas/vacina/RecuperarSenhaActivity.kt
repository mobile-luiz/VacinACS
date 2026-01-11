package com.vacinas.vacina

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class RecuperarSenhaActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var editTextEmailRecuperacao: EditText
    private lateinit var buttonEnviarLink: Button
    private lateinit var progressBarRecuperacao: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recuperar_senha)

        // Inicializa o Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Inicializa as Views (usando findViewById, conforme seu padrão)
        editTextEmailRecuperacao = findViewById(R.id.editTextEmailRecuperacao)
        buttonEnviarLink = findViewById(R.id.buttonEnviarLink)
        progressBarRecuperacao = findViewById(R.id.progressBarRecuperacao)

        // Configura o clique do botão
        buttonEnviarLink.setOnClickListener {
            enviarLinkDeRedefinicao()
        }
    }

    private fun enviarLinkDeRedefinicao() {
        val email = editTextEmailRecuperacao.text.toString().trim()

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editTextEmailRecuperacao.error = "Insira um e-mail válido."
            return
        }

        // 1. Mostrar carregamento e desabilitar botão
        progressBarRecuperacao.visibility = View.VISIBLE
        buttonEnviarLink.isEnabled = false

        // 2. Chama a função do Firebase para enviar o email
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                // 3. Esconder carregamento e reabilitar botão
                progressBarRecuperacao.visibility = View.GONE
                buttonEnviarLink.isEnabled = true

                if (task.isSuccessful) {
                    Toast.makeText(
                        this,
                        "📧 Link de redefinição enviado para $email. Verifique sua caixa de entrada!",
                        Toast.LENGTH_LONG
                    ).show()
                    // Opcional: Voltar para a tela de login
                    finish()
                } else {
                    // Exibir erro (ex: e-mail não cadastrado)
                    Toast.makeText(
                        this,
                        "❌ Erro ao enviar: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
}