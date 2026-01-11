package com.vacinas.vacina

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class Login: AppCompatActivity() {

    // Variavel para o Firebase Authentication
    private lateinit var auth: FirebaseAuth

    // Declaração das Views
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var btnLogin: Button
    private lateinit var tvRegisterLink: TextView
    private lateinit var tvForgotPassword: TextView // 👈 ADICIONADO: TextView para "Esqueceu a senha?"

    // Variável para o ProgressDialog
    private lateinit var progressDialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Inicializa a instância do Firebase Auth
        auth = Firebase.auth

        // --- 1. Inicializa as Views usando findViewById ---
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        tilEmail = findViewById(R.id.til_email)
        tilPassword = findViewById(R.id.til_password)
        btnLogin = findViewById(R.id.btn_login)
        tvRegisterLink = findViewById(R.id.tv_register_link)
        tvForgotPassword = findViewById(R.id.tv_forgot_password) // 👈 INICIALIZAÇÃO

        // --- 2. Inicializa o ProgressDialog conforme solicitado ---
        progressDialog = ProgressDialog(this).apply {
            setMessage("Autenticando...")
            setCancelable(false)
        }

        // Verifica se o usuário já está logado
        checkCurrentUser()

        // --- 3. Configuração dos Listeners (Ouvintes de Clique) ---

        // Listener para o botão de Login
        btnLogin.setOnClickListener {
            performLogin()
        }

        // Listener para o link de Cadastro
        tvRegisterLink.setOnClickListener {
            startActivity(Intent(this, Cadastro::class.java))
        }

        // 🚨 NOVO LISTENER: Para chamar a tela de Recuperação de Senha
        tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, RecuperarSenhaActivity::class.java)) // 👈 CHAMA A ACTIVITY
        }
    }

    /**
     * Verifica se o usuário já está logado e, se sim, navega para a tela principal.
     */
    private fun checkCurrentUser() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            startActivity(Intent(this, Tela_home ::class.java))
            finish()
        }
    }

    /**
     * Realiza a autenticação do usuário com Firebase usando E-mail e Senha.
     */
    private fun performLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        // 1. Validação simples dos campos
        if (email.isEmpty()) {
            tilEmail.error = "E-mail é obrigatório."
            return
        }
        if (password.isEmpty()) {
            tilPassword.error = "Senha é obrigatória."
            return
        }

        // Limpa erros anteriores
        tilEmail.error = null
        tilPassword.error = null

        // 🚨 Exibe o ProgressDialog antes de iniciar a operação de rede
        progressDialog.show()
        btnLogin.isEnabled = false

        // 2. Chama o método de login do Firebase
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->

                // 🚨 Oculta o ProgressDialog assim que a operação for concluída (sucesso ou falha)
                progressDialog.dismiss()
                btnLogin.isEnabled = true

                if (task.isSuccessful) {
                    // Login bem-sucedido
                    Log.d("LOGIN_FIREBASE", "signInWithEmail:success")
                    Toast.makeText(this, "Login efetuado com sucesso!", Toast.LENGTH_SHORT).show()

                    // Navega para a tela principal
                    startActivity(Intent(this, Tela_home ::class.java))
                    finish()

                } else {
                    // Se o login falhar, exibe uma mensagem para o usuário.
                    Log.w("LOGIN_FIREBASE", "signInWithEmail:failure", task.exception)
                    Toast.makeText(
                        this,
                        "Falha na autenticação: Verifique e-mail e senha.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
}