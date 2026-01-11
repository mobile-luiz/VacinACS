package com.vacinas.vacina.ui.vacinas.adapter

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vacinas.vacina.R
import com.vacinas.vacina.VacinaDose
import java.util.Locale

class VacinaAdapter(
    var listaDoses: List<VacinaDose>,
    private val clickListener: VacinaItemClickListener,
    // Garante que o adapter possa ser usado em telas que exigem o nome do paciente (ex: Agenda)
    private val showPatientName: Boolean = false
) : RecyclerView.Adapter<VacinaAdapter.VacinaViewHolder>() {

    interface VacinaItemClickListener {
        fun onVacinaItemClick(dose: VacinaDose)
    }

    private val STATUS_APLICADA = "Aplicada"
    private val STATUS_PENDENTE = "Pendente"
    private val STATUS_AGENDADA = "Agendada"

    // Cores
    private val COLOR_APPLIED_BAR = Color.parseColor("#3F51B5")
    private val COLOR_APPLIED_DATA = Color.parseColor("#3F51B5")
    private val COLOR_PENDING_BAR = Color.parseColor("#FF9800")
    private val COLOR_SCHEDULED_TEXT = Color.parseColor("#FF9800")

    // Emojis/Caracteres Unicode
    private val EMOJI_PACIENTE = "👤 "
    private val EMOJI_VACINA = "💉 "
    private val EMOJI_DOSE = "🔢 "
    private val EMOJI_LOTE = "📦 "
    private val EMOJI_LAB = "🏭 "
    private val EMOJI_UNIDADE = "🏥 "
    private val EMOJI_DATA = "📅 "
    private val EMOJI_PROXIMA = "🗓️ "

    /**
     * Atualiza a lista de doses exibidas pelo RecyclerView (usado em busca ou reset).
     */
    fun updateList(newList: List<VacinaDose>) {
        listaDoses = newList
        notifyDataSetChanged()
    }

    /**
     * 🔑 NOVO: Adiciona itens ao final da lista existente (usado para paginação).
     */
    fun appendList(newDoses: List<VacinaDose>) {
        val startPosition = listaDoses.size
        // Concatena as listas (cria uma nova lista imutável com os itens adicionados)
        listaDoses = listaDoses + newDoses
        notifyItemRangeInserted(startPosition, newDoses.size)
        // Nota: O uso de DiffUtil seria mais performático, mas esta é a correção mais direta.
    }


    inner class VacinaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val indicatorBar: View = itemView.findViewById(R.id.status_indicator_bar)

        // Referência ao TextView do nome do paciente
        val textPacienteNome: TextView = itemView.findViewById(R.id.text_paciente_nome)

        val nomeVacina: TextView = itemView.findViewById(R.id.text_vacina_nome)
        val dose: TextView = itemView.findViewById(R.id.text_dose)
        val data: TextView = itemView.findViewById(R.id.text_data)
        val textDataProxima: TextView = itemView.findViewById(R.id.text_data_proxima)

        val textLote: TextView = itemView.findViewById(R.id.text_lote)
        val textLabProdut: TextView = itemView.findViewById(R.id.text_lab_produt)
        val textUnidade: TextView = itemView.findViewById(R.id.text_unidade)


        fun bind(doseItem: VacinaDose, context: Context, clickListener: VacinaItemClickListener) {

            // ----------------------------------------------------
            // 1. Lógica de Exibição do Nome do Paciente
            // ----------------------------------------------------

            if (showPatientName && doseItem.pacienteNome.isNotBlank()) {
                textPacienteNome.text = EMOJI_PACIENTE + "Paciente: ${doseItem.pacienteNome}"
                textPacienteNome.visibility = View.VISIBLE
            } else {
                textPacienteNome.visibility = View.GONE
            }

            nomeVacina.text = EMOJI_VACINA + doseItem.nomeVacina
            this.dose.text = EMOJI_DOSE + doseItem.dose

            // ----------------------------------------------------
            // 2. Lógica de Status, Data e Estilo
            // ----------------------------------------------------

            // Sempre reseta a visibilidade e cor de certos campos antes de rebindar
            data.visibility = View.VISIBLE
            textDataProxima.visibility = View.GONE
            data.setTextColor(Color.BLACK) // Cor padrão para garantir o reset

            textLote.visibility = View.GONE
            textLabProdut.visibility = View.GONE
            textUnidade.visibility = View.GONE


            if (doseItem.status == STATUS_APLICADA) {
                // STATUS: APLICADA 🟢

                // 2.1. Estilo
                indicatorBar.setBackgroundColor(COLOR_APPLIED_BAR)
                data.setTextColor(COLOR_APPLIED_DATA)

                // 2.2. Data Principal (Data de Aplicação)
                data.text = EMOJI_DATA + "Aplicada em: ${doseItem.dataAplicacao ?: "N/D"}"

                // 2.3. Detalhes (Visíveis)
                textLote.visibility = View.VISIBLE
                textLabProdut.visibility = View.VISIBLE
                textUnidade.visibility = View.VISIBLE

                textLote.text = EMOJI_LOTE + "Lote: ${doseItem.lote ?: "N/A"}"
                textLabProdut.text = EMOJI_LAB + "Lab.Produt: ${doseItem.labProdut ?: "N/A"}"
                textUnidade.text = EMOJI_UNIDADE + "Unidade: ${doseItem.unidade ?: "N/A"}"

                // 2.4. Lógica para mostrar a data de agendamento da próxima dose
                val position = adapterPosition
                // Verifica a próxima dose na lista (se houver)
                if (position != RecyclerView.NO_POSITION && position + 1 < listaDoses.size) {
                    val proximaDose = listaDoses[position + 1]

                    if (proximaDose.nomeVacina == doseItem.nomeVacina &&
                        (proximaDose.status == STATUS_PENDENTE || proximaDose.status == STATUS_AGENDADA) &&
                        !proximaDose.dataAgendada.isNullOrEmpty()) {

                        textDataProxima.text = EMOJI_PROXIMA + "Próxima Agendada: ${proximaDose.dataAgendada}"
                        textDataProxima.setTextColor(COLOR_SCHEDULED_TEXT)
                        textDataProxima.visibility = View.VISIBLE
                    }
                }

            } else {
                // STATUS: PENDENTE OU AGENDADA 🟠

                // 2.1. Estilo (Laranja/Amarelo)
                indicatorBar.setBackgroundColor(COLOR_PENDING_BAR)

                // 2.2. Lógica de Agendamento vs. Pendente Simples
                if (doseItem.dataAgendada != null && doseItem.dataAgendada.isNotEmpty()) {
                    // É AGENDADA (com data): Exibe a data de agendamento na linha principal
                    data.text = EMOJI_DATA + "Agendada p/: ${doseItem.dataAgendada}"
                    data.setTextColor(COLOR_SCHEDULED_TEXT)

                } else {
                    // É PENDENTE Simples (Sem data agendada)
                    data.text = EMOJI_DATA + STATUS_PENDENTE.uppercase(Locale.getDefault())
                    data.setTextColor(COLOR_PENDING_BAR)
                }
            }

            // Adiciona o listener de clique
            itemView.setOnClickListener {
                clickListener.onVacinaItemClick(doseItem)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VacinaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vacina_dose_card, parent, false)
        return VacinaViewHolder(view)
    }

    override fun getItemCount(): Int = listaDoses.size

    override fun onBindViewHolder(holder: VacinaViewHolder, position: Int) {
        holder.bind(listaDoses[position], holder.itemView.context, clickListener)
    }
}