package com.vacinas.vacina

import android.os.Bundle
import android.view.MenuItem // 💡 Novo: Para tratar o clique no botão "Voltar"
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar // 💡 Novo: Importar a Toolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener


class EditarPerfilActivity : AppCompatActivity() {

    // 💡 Declaração das Views
    private lateinit var toolbar: Toolbar // 💡 Novo: Referência à Toolbar
    private lateinit var editTextNome: EditText
    private lateinit var editTextEmail: EditText
    private lateinit var buttonSalvar: Button
    private lateinit var progressBar: ProgressBar

    // 💡 Configuração do Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var userRef: DatabaseReference
    private var userId: String? = null

    // -------------------------------------------------------------------------
    // ON CREATE (LOGICA DA TOOLBAR INCLUIDA)
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editar_perfil) // Assumindo este é o nome do seu layout XML

        // 1. Inicializa o Auth e obtém o UID logado
        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            Toast.makeText(this, "Você precisa estar logado.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        userId = currentUser.uid

        // 2. Inicializa as Views, incluindo a Toolbar
        initializeViews()

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)  // 3. Mostra a seta (branca, se o XML estiver correto)
        toolbar.navigationIcon?.setTint(android.graphics.Color.WHITE)
        // Opcional: define o título, se não estiver configurado no XML/Manifest
        supportActionBar?.title = "Editar Perfil"
        // ----------------------------------------------------------------------

        // 3. Inicializa o Firebase Realtime Database
        database = FirebaseDatabase.getInstance()
        userRef = database.getReference("usuarios").child(userId!!)

        // 4. Carrega os dados atuais do Firebase
        loadUserData()

        // 5. Configura o Listener do Botão Salvar
        buttonSalvar.setOnClickListener {
            saveChanges()
        }
    }

    // -------------------------------------------------------------------------
    // INITIALIZE VIEWS (TOOLBAR ADICIONADA)
    // -------------------------------------------------------------------------

    private fun initializeViews() {
        // Inicializa a Toolbar
        toolbar = findViewById(R.id.toolbar) // O ID 'toolbar' deve vir do seu XML

        editTextNome = findViewById(R.id.editTextNome)
        // Nota: O email é apenas para exibição e não deve ser editável.
        editTextEmail = findViewById(R.id.editTextEmail)
        buttonSalvar = findViewById(R.id.buttonSalvar)
        progressBar = findViewById(R.id.progressBar)
    }

    // -------------------------------------------------------------------------
    // ON OPTIONS ITEM SELECTED (TRATAMENTO DO BOTÃO VOLTAR)
    // -------------------------------------------------------------------------

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Verifica se o item clicado é o botão de navegação "Up" (Voltar)
        return if (item.itemId == android.R.id.home) {
            finish() // Fecha a Activity, simulando o comportamento padrão do botão Voltar
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    // -------------------------------------------------------------------------
    // LOAD USER DATA (INALTERADO)
    // -------------------------------------------------------------------------

    private fun loadUserData() {
        progressBar.visibility = View.VISIBLE

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                progressBar.visibility = View.GONE

                val usuario = snapshot.getValue(Usuario::class.java)

                if (usuario != null) {
                    editTextNome.setText(usuario.nome)
                    // O email é carregado, mas deve ser inalterável (enabled=false) no XML,
                    // pois a alteração de email requer reautenticação no Firebase Auth.
                    editTextEmail.setText(usuario.email)
                } else {
                    Toast.makeText(this@EditarPerfilActivity, "Usuário não encontrado.", Toast.LENGTH_LONG).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@EditarPerfilActivity, "Falha ao carregar: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    // -------------------------------------------------------------------------
    // SAVE CHANGES (INALTERADO)
    // -------------------------------------------------------------------------

    private fun saveChanges() {
        val novoNome = editTextNome.text.toString().trim()

        if (novoNome.isEmpty()) {
            editTextNome.error = "O nome não pode ser vazio."
            return
        }

        progressBar.visibility = View.VISIBLE

        val updates = hashMapOf<String, Any>(
            "nome" to novoNome
        )

        userRef.updateChildren(updates)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Perfil atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Erro ao atualizar: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}