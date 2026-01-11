package com.vacinas.vacina.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vacinas.vacina.R

class NotificationReceiver : BroadcastReceiver() {

    private val CHANNEL_ID = "VACINA_REMINDER_CHANNEL"
    private val TAG = "NotificationReceiver"

    // ⭐️ CONSTANTE PARA O EXTRA DO ID
    private val NOTIFICATION_ID_EXTRA = "NOTIFICATION_UNIQUE_ID"

    override fun onReceive(context: Context, intent: Intent) {

        // 1. Extração de Dados e ID Único (Novo)
        // O ID único do PendingIntent (usado para o alarme) agora é passado no Intent.
        val notificationId = intent.getIntExtra(NOTIFICATION_ID_EXTRA, System.currentTimeMillis().toInt())
        val pacienteNome = intent.getStringExtra("PACIENTE_NOME") ?: "O paciente"
        val cnsPaciente = intent.getStringExtra("CNS_PACIENTE") ?: "N/D"
        val tipoLembrete = intent.getStringExtra("TIPO_LEMBRETE") ?: "DOSE_ESPECIFICA" // Padrão: Dose

        Log.i(TAG, "Notificação Recebida! Tipo: $tipoLembrete | ID: $notificationId")

        // 2. Determinação do Conteúdo (Título e Corpo)
        val notificationTitle: String
        val notificationBody: String

        if (tipoLembrete == "VISITA_GERAL") {
            // CONTEÚDO PARA VISITA GERAL
            notificationTitle = "Lembrete: Rota do Dia"
            notificationBody = "Você tem uma visita agendada para $pacienteNome (CNS: $cnsPaciente) hoje. Prepare a documentação e as vacinas necessárias!"
        } else {
            // CONTEÚDO PARA DOSE ESPECÍFICA
            val vacinaNome = intent.getStringExtra("VACINA_NOME") ?: "Vacina"
            val doseTipo = intent.getStringExtra("DOSE_TIPO") ?: "Dose"
            val dataDose = intent.getStringExtra("DATA_DOSE") ?: "hoje"

            notificationTitle = "Lembrete: Aplicação de Vacina"
            notificationBody = "$pacienteNome (CNS: $cnsPaciente) tem a dose **$doseTipo** da vacina **$vacinaNome** agendada para $dataDose. Não se esqueça de registrar a aplicação!"
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Cria o canal de notificação
        createNotificationChannel(notificationManager)

        // 3. Cria e exibe a notificação
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcherrr)
            .setContentTitle(notificationTitle)
            .setContentText(notificationBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        try {
            // ⭐️ USO DO ID ÚNICO DO ALARME: Permite controle sobre a notificação
            notificationManager.notify(notificationId, builder.build())
            Log.i(TAG, "Notificação para $pacienteNome do tipo $tipoLembrete exibida com sucesso com ID: $notificationId.")
        } catch (e: SecurityException) {
            Log.e(TAG, "ERRO DE SEGURANÇA: Falha ao notificar. Permissão negada! ${e.message}", e)
        }
    }

    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Lembretes de Vacinas e Visitas"
            val descriptionText = "Notificações para doses de vacinas e visitas agendadas."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}