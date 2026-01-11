package com.vacinas.vacina.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.vacinas.vacina.Individuo
import com.vacinas.vacina.R
import java.text.SimpleDateFormat
import java.util.*

class IndividuoAdapter(
    individuos: List<Individuo>,
    // 🚨 ATUALIZAÇÃO DO CONSTRUTOR: Renomeado onItemClicked para onItemClick
    private val onItemClick: (Individuo) -> Unit,
    // ✅ NOVO: Adicionado o listener de clique longo para exclusão
    private val onItemLongClick: (Individuo) -> Boolean
) : RecyclerView.Adapter<IndividuoAdapter.IndividuoViewHolder>() {

    // Lista interna mutável para manipulação de dados (paginação e exclusão)
    private var currentIndividuos: MutableList<Individuo> = individuos.toMutableList()

    // -------------------------------------------------------------------------
    // Emojis/Caracteres Unicode
    // -------------------------------------------------------------------------
    private val EMOJI_CNS = "🆔 "
    private val EMOJI_MAE = "👩‍👧 "
    private val EMOJI_CELULAR = "📱 "
    private val EMOJI_CADASTRO = "📅 " // Usando para o campo de Cadastro/Email
    private val EMOJI_VISITADO = "✅ "
    private val EMOJI_AGENDADO = "⏰ "
    private val EMOJI_PENDENTE = "⌛ "

    class IndividuoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textNome: TextView = itemView.findViewById(R.id.text_nome)
        val textCns: TextView = itemView.findViewById(R.id.text_cns)
        val textStatus: TextView = itemView.findViewById(R.id.text_status)
        val textNomeMae: TextView = itemView.findViewById(R.id.text_nome_mae)
        val textCelular: TextView = itemView.findViewById(R.id.text_celular)
        val textEmail: TextView = itemView.findViewById(R.id.text_email)
        val statusIndicatorBar: View = itemView.findViewById(R.id.status_indicator_bar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IndividuoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_individuo, parent, false)
        return IndividuoViewHolder(view)
    }

    /**
     * 🔑 CORREÇÃO CRÍTICA DE FUSO HORÁRIO 🔑
     * Formata o timestamp, usando o fuso horário local para visitas e UTC para agendamentos.
     */
    private fun formatTimestampToDate(timestamp: Long, includeTime: Boolean = false): String {
        // Retorna string vazia se o timestamp for 0 ou nulo
        if (timestamp <= 0L) return "Data não definida"

        // Define o formato: 'dd/MM/yyyy HH:mm' para visitas (com hora), 'dd/MM/yyyy' para agendamento
        val formatString = if (includeTime) "dd/MM/yyyy HH:mm" else "dd/MM/yyyy"
        val sdf = SimpleDateFormat(formatString, Locale("pt", "BR"))

        // 🚨 Lógica Condicional para o Fuso Horário
        if (includeTime) {
            // Para 'Visitado': Usa o fuso horário local (BRT/BRST) para exibir a hora correta.
            sdf.timeZone = TimeZone.getDefault()
        } else {
            // Para 'Agendado': Usa UTC para garantir que o dia salvo (meia-noite UTC) seja exibido corretamente.
            sdf.timeZone = TimeZone.getTimeZone("UTC")
        }

        return sdf.format(Date(timestamp))
    }

    override fun onBindViewHolder(holder: IndividuoViewHolder, position: Int) {
        val individuo = currentIndividuos[position]
        val context = holder.itemView.context

        // -------------------------------------------------------------------------
        // Preenchimento dos dados com Emojis
        // -------------------------------------------------------------------------
        holder.textNome.text = individuo.nome.uppercase()
        holder.textCns.text = EMOJI_CNS + "CNS: ${individuo.cns}"
        holder.textNomeMae.text = EMOJI_MAE + "Mãe: ${individuo.nomeMae.ifEmpty { "Não informado" }}"
        holder.textCelular.text = EMOJI_CELULAR + "Tel: ${individuo.celular.ifEmpty { "Não informado" }}"
        // Usando o emoji de cadastro no campo "Cadastro em"
        holder.textEmail.text = EMOJI_CADASTRO + "Cadastro em: ${individuo.email.ifEmpty { "Não cadastrado" }}"

        // -------------------------------------------------------------------------
        // Lógica de cores e texto de status de visita
        // -------------------------------------------------------------------------
        val (statusText, statusColorResId) = when (individuo.statusVisita.lowercase()) {
            "visitado" -> {
                // Visitado: chama a função que usará o fuso local
                val formattedDate = formatTimestampToDate(individuo.ultimaAtualizacao, includeTime = true)
                // Adiciona emoji de Visitado
                EMOJI_VISITADO + "Visitado em $formattedDate" to android.R.color.holo_green_dark
            }
            "agendado" -> {
                // Agendado: chama a função que usará o UTC (apenas dia)
                val formattedDate = formatTimestampToDate(individuo.ultimaAtualizacao, includeTime = false)
                // Adiciona emoji de Agendado
                EMOJI_AGENDADO + "Agendado p/ $formattedDate" to android.R.color.holo_orange_dark
            }
            else -> {
                // Pendente: usa cinza
                // Adiciona emoji de Pendente
                EMOJI_PENDENTE + "Pendente" to android.R.color.darker_gray
            }
        }

        holder.textStatus.text = statusText
        holder.textStatus.setTextColor(ContextCompat.getColor(context, statusColorResId))

        // Indicador de Sincronização (Barra Lateral)
        val syncColorResId = if (individuo.isSynchronized) {
            R.color.accent_success // Assumindo que R.color.accent_success é o verde claro que você usa
        } else {
            android.R.color.holo_red_dark
        }
        holder.statusIndicatorBar.setBackgroundColor(ContextCompat.getColor(context, syncColorResId))

        // -------------------------------------------------------------------------
        // LISTENERS DE CLIQUE
        // -------------------------------------------------------------------------

        // Listener de clique curto (para ir para Detalhes)
        holder.itemView.setOnClickListener {
            onItemClick(individuo)
        }

        // ✅ NOVO: Listener de clique longo (para Exclusão)
        holder.itemView.setOnLongClickListener {
            onItemLongClick(individuo)
        }
    }

    override fun getItemCount(): Int = currentIndividuos.size

    // -------------------------------------------------------------------------
    // MÉTODOS DE MANIPULAÇÃO DE LISTA (PAGINAÇÃO E EXCLUSÃO)
    // -------------------------------------------------------------------------

    /**
     * Atualiza a lista completa (usada para carregar a 1ª página ou filtrar)
     */
    fun updateList(newIndividuos: List<Individuo>) {
        currentIndividuos.clear()
        currentIndividuos.addAll(newIndividuos)
        // Notifica o adapter que a lista mudou para redesenhar todos os itens
        notifyDataSetChanged()
    }

    /**
     * Adiciona uma nova lista de indivíduos ao final da lista existente.
     */
    fun appendList(newIndividuos: List<Individuo>) {
        val startPosition = currentIndividuos.size // Posição onde os novos itens serão inseridos
        currentIndividuos.addAll(newIndividuos)
        // Notifica o adapter para inserir os novos itens, evitando a recarga de toda a lista.
        notifyItemRangeInserted(startPosition, newIndividuos.size)
    }

    /**
     * Retorna o objeto na posição (usado pela Activity para saber o que excluir/desfazer)
     */
    fun getIndividuoAt(position: Int): Individuo {
        return currentIndividuos[position]
    }

    /**
     * ✅ NOVO: Retorna o índice de um indivíduo com base no seu CNS.
     * Usado pela Activity no evento de clique longo para encontrar a posição antes de remover.
     */
    fun indexOf(individuo: Individuo): Int {
        // Assume que o CNS é a chave única
        return currentIndividuos.indexOfFirst { it.cns == individuo.cns }
    }


    /**
     * Remove visualmente o item da lista (para exclusão)
     */
    fun removeItem(position: Int) {
        currentIndividuos.removeAt(position)
        notifyItemRemoved(position)
    }

    /**
     * Restaura o item na posição original (para o Desfazer do Snackbar)
     */
    fun restoreItem(individuo: Individuo, position: Int) {
        currentIndividuos.add(position, individuo)
        notifyItemInserted(position)
    }
}