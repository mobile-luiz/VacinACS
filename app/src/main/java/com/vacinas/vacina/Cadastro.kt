package com.vacinas.vacina

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.database.FirebaseDatabase // Importação para Realtime Database
import com.google.firebase.database.database

import java.util.Date

class Cadastro : AppCompatActivity() {

    // Variavel para o Firebase Authentication
    private lateinit var auth: FirebaseAuth

    // NOVO: Variavel para o Firebase Realtime Database
    private lateinit var database: FirebaseDatabase

    // ... (Declaração das Views - mantidas iguais)
    private lateinit var etName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var tilName: TextInputLayout
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var btnRegister: Button
    private lateinit var tvLoginLink: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cadastro)

        // Inicializa a instância do Firebase Auth
        auth = Firebase.auth

        // NOVO: Inicializa a instância do Realtime Database
        database = Firebase.database

        // --- 1. Inicializa as Views usando findViewById ---
        etName = findViewById(R.id.et_name)
        etEmail = findViewById(R.id.et_email_register)
        etPassword = findViewById(R.id.et_password_register)
        tilName = findViewById(R.id.til_name)
        tilEmail = findViewById(R.id.til_email_register)
        tilPassword = findViewById(R.id.til_password_register)
        btnRegister = findViewById(R.id.btn_register)
        tvLoginLink = findViewById(R.id.tv_login_link)

        // --- 2. Configuração dos Listeners (Ouvintes de Clique) ---
        btnRegister.setOnClickListener {
            performRegistration()
        }

        tvLoginLink.setOnClickListener {
            finish()
        }
    }

    /**
     * Realiza o cadastro do novo usuário no Firebase Auth e salva seus dados no Realtime Database.
     */
    private fun performRegistration() {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        // 1. Validação simples dos campos (Mantida)
        if (name.isEmpty()) {
            tilName.error = "Nome é obrigatório."
            return
        }
        if (email.isEmpty()) {
            tilEmail.error = "E-mail é obrigatório."
            return
        }
        if (password.length < 6) {
            tilPassword.error = "A senha deve ter pelo menos 6 caracteres."
            return
        }

        // Limpa erros anteriores
        tilName.error = null
        tilEmail.error = null
        tilPassword.error = null

        // 2. Criação de usuário no Firebase Auth
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { authTask ->
                if (authTask.isSuccessful) {
                    // Auth bem-sucedida. Agora, salva os dados no Realtime Database.
                    val user = auth.currentUser
                    val userId = user?.uid

                    if (userId != null) {
                        // Constrói o mapa de dados do usuário
                        val userData = mapOf(
                            "uid" to userId,
                            "nome" to name,
                            "email" to email,
                            "data_criacao" to Date().time, // Salva o timestamp (milissegundos)
                            "ativo" to true
                        )

                        // 3. Salva os dados no nó 'usuarios', usando o UID como chave.
                        database.getReference("usuarios").child(userId).setValue(userData)
                            .addOnSuccessListener {
                                Log.d("REALTIME_DB", "Dados do usuário salvos com sucesso! UID: $userId")
                                Toast.makeText(this, "Conta criada e dados salvos!", Toast.LENGTH_LONG).show()

                                // Navega para a tela principal
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Log.w("REALTIME_DB", "Erro ao salvar dados no Realtime Database.", e)
                                Toast.makeText(this, "Erro ao salvar dados do usuário no banco.", Toast.LENGTH_LONG).show()
                            }

                    } else {
                        Toast.makeText(this, "Erro: Usuário autenticado, mas UID não encontrado.", Toast.LENGTH_LONG).show()
                    }

                } else {
                    // Se o registro no Auth falhar, exibe a mensagem de erro.
                    Log.w("REGISTER_FIREBASE", "createUserWithEmail:failure", authTask.exception)
                    Toast.makeText(
                        this,
                        "Falha no cadastro. E-mail já em uso ou erro de servidor.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
}