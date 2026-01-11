package com.vacinas.vacina

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar // Importação adicionada
import com.google.android.material.textfield.TextInputEditText

// Importações do Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

// Importe a classe de acesso a dados local
import com.vacinas.vacina.data.IndividuoDao
// ✅ ADICIONE ESTE IMPORT DA SUA CLASSE DE MODELO


class FormularioIndividuoActivity : AppCompatActivity() {

    // Referências atualizadas e completas
    private lateinit var auth: FirebaseAuth
    private lateinit var databaseIndividuos: DatabaseReference
    private lateinit var databaseUsuarios: DatabaseReference // Mantida para o caso de precisar do UID
    private lateinit var individuoDao: IndividuoDao
    private lateinit var etNome: TextInputEditText
    private lateinit var etEndereco: TextInputEditText
    private lateinit var etProntuario: TextInputEditText
    private lateinit var etNascimento: TextInputEditText
    private lateinit var etCelular: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etCns: TextInputEditText
    private lateinit var etMae: TextInputEditText
    private lateinit var etPai: TextInputEditText
    private lateinit var btnSalvar: Button

    // Adicionado: Referência para a Toolbar
    private lateinit var toolbar: Toolbar


    // -------------------------------------------------------------------------
    // ON CREATE
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_formulario_individuo)



        auth = FirebaseAuth.getInstance()
        databaseIndividuos = FirebaseDatabase.getInstance().getReference("individuos")
        databaseUsuarios = FirebaseDatabase.getInstance().getReference("usuarios")
        individuoDao = IndividuoDao(applicationContext)

        initializeViews()
        setupToolbar() // CHAMADA ADICIONADA

        btnSalvar.setOnClickListener {
            saveIndividuo()
        }
    }

    // -------------------------------------------------------------------------
    // NOVO MÉTODO PARA CONFIGURAR A TOOLBAR (CORRIGIDO)
    // -------------------------------------------------------------------------

    private fun setupToolbar() {
        // 1. Define a toolbar como a Action Bar (OBRIGATÓRIO ser o primeiro)
        setSupportActionBar(toolbar)

        // 2. Define o Título (será alinhado à esquerda, ao lado da seta)
        supportActionBar?.title = " Cadastro de Indivíduo"

        // 3. Habilita a seta de retorno
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 4. Define o ícone de navegação (seta) para lidar com o evento de clique
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed() // Volta para a Activity anterior
        }
    }


    // -------------------------------------------------------------------------
    // INITIALIZE VIEWS (INALTERADO)
    // -------------------------------------------------------------------------

    private fun initializeViews() {
        // Referência à Toolbar
        toolbar = findViewById(R.id.toolbar_formulario)

        etNome = findViewById(R.id.et_nome)
        etEndereco = findViewById(R.id.et_endereco)
        etProntuario = findViewById(R.id.et_prontuario)
        etNascimento = findViewById(R.id.et_nascimento)
        etCelular = findViewById(R.id.et_celular)
        etEmail = findViewById(R.id.et_email)
        etCns = findViewById(R.id.et_cns)
        etMae = findViewById(R.id.et_mae)
        etPai = findViewById(R.id.et_pai)
        btnSalvar = findViewById(R.id.btn_salvar)
    }

    // -------------------------------------------------------------------------
    // NOVO MÉTODO PARA LIMPAR OS CAMPOS (INALTERADO)
    // -------------------------------------------------------------------------

    private fun clearFields() {
        etNome.setText("")
        etEndereco.setText("")
        etProntuario.setText("")
        etNascimento.setText("")
        etCelular.setText("")
        etEmail.setText("")
        etCns.setText("")
        etMae.setText("")
        etPai.setText("")
        etNome.requestFocus()
    }

    // -------------------------------------------------------------------------
    // SAVE INDIVIDUO (INALTERADO)
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // SAVE INDIVIDUO (Atualizado com validação de CNS duplicado/falha local)
    // -------------------------------------------------------------------------

    // ... (mantenha o código anterior inalterado)

    // -------------------------------------------------------------------------
    // SAVE INDIVIDUO (Atualizado com checagem de CNS duplicado)
    // -------------------------------------------------------------------------

    private fun saveIndividuo() {
        // 1. EXTRAÇÃO E VALIDAÇÃO DE DADOS
        val nome = etNome.text.toString().trim()
        val cns = etCns.text.toString().trim()
        val mae = etMae.text.toString().trim()
        val prontuario = etProntuario.text.toString().trim()
        val nascimento = etNascimento.text.toString().trim()
        val endereco = etEndereco.text.toString().trim()
        val celular = etCelular.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val pai = etPai.text.toString().trim()


        if (nome.isEmpty() || cns.isEmpty() || mae.isEmpty()) {
            Toast.makeText(
                this,
                "Por favor, preencha campos obrigatórios (Nome, CNS, Mãe).",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // 🚨 Validação de CNS Length (CNS deve ter 15 dígitos)
        if (cns.length != 15) {
            Toast.makeText(this, "O CNS deve conter 15 dígitos.", Toast.LENGTH_LONG).show()
            return
        }

        // ⭐️ NOVO: Checagem de Duplicidade no Banco Local antes de salvar
        // ESTA LINHA REQUER QUE IndividuoDao TENHA O MÉTODO findByCns(cns: String)
        val individuoExistente = individuoDao.findByCns(cns)

        if (individuoExistente != null) {
            // O CNS já está cadastrado. Apenas avisa e não prossegue.
            Toast.makeText(
                this,
                "Aviso: Indivíduo com o CNS '$cns' já está cadastrado no dispositivo. Cadastro não foi salvo ou substituído.",
                Toast.LENGTH_LONG
            ).show()
            // Limpa o campo do CNS para correção ou novo cadastro
            etCns.setText("")
            etCns.requestFocus()
            return
        }
        // ⭐️ FIM NOVO: Checagem de Duplicidade


        val userUid = auth.currentUser?.uid ?: run {
            Toast.makeText(
                this,
                "Erro: Usuário não autenticado. Faça login novamente.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // 2. CRIAÇÃO DO OBJETO DE DADOS
        val individuo = Individuo(
            cns = cns, nome = nome, nomeMae = mae, nomePai = pai,
            endereco = endereco, prontuarioFamilia = prontuario,
            dataNascimento = nascimento, celular = celular, email = email,
            statusVisita = "Não Visitado",
            ultimaAtualizacao = System.currentTimeMillis(),
            isSynchronized = false,
            registeredByUid = userUid,
            deletePending = false
        )

        // 3. SALVAMENTO LOCAL (SQLITE)
        // Como checamos a duplicidade acima, aqui deve ser um INSERT limpo.
        val sqliteResult = individuoDao.saveOrUpdate(individuo)

        if (sqliteResult > 0) {
            // SUCESSO LOCAL: O dado está seguro no dispositivo.

            // 4. INICIA TENTATIVA DE SINCRONIZAÇÃO EM SEGUNDO PLANO
            val individuoParaFirebase = individuo.copy(isSynchronized = true)
            val cnsFormatado = cns.replace(".", "").replace("-", "")

            databaseIndividuos.child(cnsFormatado).setValue(individuoParaFirebase)
                .addOnSuccessListener {
                    // SUCESSO: Atualiza o status de sincronização no SQLite.
                    individuoDao.updateSyncStatus(cns, true)
                    Log.i("FirebaseSync", "Sincronização imediata concluída para CNS: $cns")
                }
                .addOnFailureListener {
                    Log.w(
                        "FirebaseSync",
                        "Falha na sincronização imediata. Worker assumirá: ${it.message}"
                    )
                    // FALHA (Offline/Erro): O WorkManager/Worker vai cuidar.
                }

            // 5. FEEDBACK E FECHAMENTO RÁPIDO
            Toast.makeText(
                this,
                "Cadastro salvo localmente. Sincronização automática será realizada. ✅",
                Toast.LENGTH_LONG
            ).show()
            finish() // Fecha a Activity imediatamente.

        } else {
            // 6. FALHA LOCAL: Caso o saveOrUpdate falhe por outro motivo inesperado.
            Toast.makeText(
                this,
                "Erro: Ocorreu uma falha inesperada ao salvar o cadastro localmente.",
                Toast.LENGTH_LONG
            ).show()
        }
    }


}