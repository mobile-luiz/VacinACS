package com.vacinas.vacina.util

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.vacinas.vacina.VacinaDose
import java.text.SimpleDateFormat
import java.util.*

object NotificationScheduler {

    private const val DATE_FORMAT = "dd/MM/yyyy"
    private const val TAG = "NotifScheduler"

    // ⭐️ CONSTANTE COMPARTILHADA: Nome da chave para passar o ID do alarme para o Receiver
    private const val NOTIFICATION_ID_EXTRA = "NOTIFICATION_UNIQUE_ID"

    // Constantes de Horário (Inalteradas)
    private const val VISITA_ALARM_HOUR = 8
    private const val VISITA_ALARM_MINUTE = 0
    private const val DOSE_ALARM_HOUR = 8
    private const val DOSE_ALARM_MINUTE = 0
    private const val ALARM_SECOND = 0


    // ----------------------------------------------------------------------
    // 🔑 FUNÇÕES DE ID ÚNICO (Mantidas as correções de colisão)
    // ----------------------------------------------------------------------

    /**
     * @brief Gera ID para lembrete de Dose. Multiplica por 100000 para separar do ID de Visita.
     */
    private fun getDoseNotificationId(dose: VacinaDose, cnsPaciente: String): Int {
        val uniqueString = "DOSE|${dose.nomeVacina}|${dose.dose}|$cnsPaciente"
        // Usa um grande offset para separar o range de IDs das doses.
        return (uniqueString.hashCode() * 100000) and 0x7FFFFFFF
    }

    /**
     * @brief Gera ID para lembrete de Visita Geral. Usa o hashCode base (sem offset grande).
     */
    private fun getVisitaNotificationId(cnsPaciente: String): Int {
        val uniqueString = "VISITA_GERAL|$cnsPaciente"
        // Apenas o hash code da visita (o offset grande na Dose evita colisão).
        return uniqueString.hashCode() and 0x7FFFFFFF
    }


    // ----------------------------------------------------------------------
    // 🛡️ LÓGICA DE PERMISSÃO E FALLBACK (Inalterada)
    // ----------------------------------------------------------------------

    private fun canScheduleExactAlarms(alarmManager: AlarmManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleAlarmWithFallback(
        alarmManager: AlarmManager,
        type: Int,
        triggerTimeMillis: Long,
        operation: PendingIntent,
        tag: String
    ) {
        if (canScheduleExactAlarms(alarmManager)) {
            alarmManager.setExactAndAllowWhileIdle(type, triggerTimeMillis, operation)
            Log.i(TAG, "$tag: Agendado com precisão (EXATO).")
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(type, triggerTimeMillis, operation)
            Log.w(TAG, "$tag: Agendado com FALLBACK (INEXATO). Permissão negada.")
        } else {
            alarmManager.set(type, triggerTimeMillis, operation)
            Log.w(TAG, "$tag: Agendado com FALLBACK (INEXATO). Permissão negada/versão antiga.")
        }
    }


    // ----------------------------------------------------------------------
    // 💉 AGENDA DE DOSE ESPECÍFICA (Adição do NOTIFICATION_ID_EXTRA)
    // ----------------------------------------------------------------------

    fun scheduleVaccineReminder(
        context: Context,
        dose: VacinaDose,
        pacienteNome: String,
        cnsPaciente: String
    ) {
        val scheduledDateString = dose.dataAgendada
        if (scheduledDateString.isNullOrEmpty()) {
            Log.d(TAG, "Dose sem data de agendamento. Ignorando: ${dose.nomeVacina}")
            cancelVaccineReminder(context, dose, cnsPaciente)
            return
        }

        try {
            val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
            val scheduledDate = sdf.parse(scheduledDateString) ?: return

            val calendar = Calendar.getInstance().apply {
                time = scheduledDate
                set(Calendar.HOUR_OF_DAY, DOSE_ALARM_HOUR)
                set(Calendar.MINUTE, DOSE_ALARM_MINUTE)
                set(Calendar.SECOND, ALARM_SECOND)
                set(Calendar.MILLISECOND, 0)
            }

            val alarmTimeMillis = calendar.timeInMillis
            val now = System.currentTimeMillis()

            if (alarmTimeMillis < now) {
                Log.w(TAG, "Dose agendada no passado. Ignorando: ${dose.nomeVacina}")
                cancelVaccineReminder(context, dose, cnsPaciente)
                return
            }

            val uniqueId = getDoseNotificationId(dose, cnsPaciente)
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("VACINA_NOME", dose.nomeVacina)
                putExtra("DOSE_TIPO", dose.dose)
                putExtra("DATA_DOSE", scheduledDateString)
                putExtra("PACIENTE_NOME", pacienteNome)
                putExtra("CNS_PACIENTE", cnsPaciente)
                // ⭐️ FIX: Adiciona o ID do alarme ao Intent para ser usado na notificação
                putExtra(NOTIFICATION_ID_EXTRA, uniqueId)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                uniqueId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            scheduleAlarmWithFallback(
                alarmManager,
                AlarmManager.RTC_WAKEUP,
                alarmTimeMillis,
                pendingIntent,
                "Lembrete de DOSE (ID: $uniqueId) p/ $DOSE_ALARM_HOUR:$DOSE_ALARM_MINUTE"
            )

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao agendar lembrete de DOSE para ${dose.nomeVacina}: ${e.message}")
        }
    }


    // ----------------------------------------------------------------------
    // 🏡 AGENDA DE VISITA GERAL (Adição do NOTIFICATION_ID_EXTRA)
    // ----------------------------------------------------------------------

    fun scheduleVisitReminder(
        context: Context,
        visitDateString: String,
        pacienteNome: String,
        cnsPaciente: String
    ) {
        if (visitDateString.isNullOrEmpty()) {
            Log.d(TAG, "Visita sem data de agendamento. Ignorando.")
            cancelVisitReminder(context, cnsPaciente)
            return
        }

        try {
            val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
            val scheduledDate = sdf.parse(visitDateString) ?: return

            val calendar = Calendar.getInstance().apply {
                time = scheduledDate
                set(Calendar.HOUR_OF_DAY, VISITA_ALARM_HOUR)
                set(Calendar.MINUTE, VISITA_ALARM_MINUTE)
                set(Calendar.SECOND, ALARM_SECOND)
                set(Calendar.MILLISECOND, 0)
            }

            val alarmTimeMillis = calendar.timeInMillis
            val now = System.currentTimeMillis()

            if (alarmTimeMillis < now) {
                Log.w(TAG, "Visita agendada no passado. Ignorando.")
                cancelVisitReminder(context, cnsPaciente)
                return
            }

            val uniqueId = getVisitaNotificationId(cnsPaciente)
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("PACIENTE_NOME", pacienteNome)
                putExtra("CNS_PACIENTE", cnsPaciente)
                putExtra("TIPO_LEMBRETE", "VISITA_GERAL")
                // ⭐️ FIX: Adiciona o ID do alarme ao Intent para ser usado na notificação
                putExtra(NOTIFICATION_ID_EXTRA, uniqueId)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                uniqueId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            scheduleAlarmWithFallback(
                alarmManager,
                AlarmManager.RTC_WAKEUP,
                alarmTimeMillis,
                pendingIntent,
                "Lembrete de VISITA GERAL (ID: $uniqueId) p/ $VISITA_ALARM_HOUR:$VISITA_ALARM_MINUTE"
            )

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao agendar lembrete de VISITA GERAL para $pacienteNome: ${e.message}")
        }
    }


    // ----------------------------------------------------------------------
    // ❌ FUNÇÕES DE CANCELAMENTO (Inalteradas)
    // ----------------------------------------------------------------------

    fun cancelVaccineReminder(context: Context, dose: VacinaDose, cnsPaciente: String) {
        val uniqueId = getDoseNotificationId(dose, cnsPaciente)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java)
        // Nota: Não é necessário putExtra para o cancelamento, apenas para o agendamento.

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            uniqueId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            Log.w(TAG, "Lembrete de DOSE (ID: $uniqueId) cancelado.")
        } else {
            Log.d(TAG, "Nenhum lembrete de DOSE ativo encontrado (ID: $uniqueId).")
        }
    }

    fun cancelVisitReminder(context: Context, cnsPaciente: String) {
        val uniqueId = getVisitaNotificationId(cnsPaciente)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            uniqueId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            Log.w(TAG, "Lembrete de VISITA GERAL (ID: $uniqueId) cancelado.")
        } else {
            Log.d(TAG, "Nenhum lembrete de VISITA GERAL ativo encontrado (ID: $uniqueId).")
        }
    }

}