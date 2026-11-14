package com.roadpulse.app.nav

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.roadpulse.app.MainActivity
import com.roadpulse.app.data.PlacesStore
import com.roadpulse.app.data.PlannedDrive
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * "Time to leave" reminders for planned drives. Uses Android alarms (no server): exact when the
 * phone allows it, otherwise Android may deliver a few minutes late, which is why the reminder is
 * set [PlannedDrive.REMIND_EARLY_MINUTES] before the leave time. Alarms are set again after a restart.
 */
object LeaveReminders {
    const val EXTRA_PLANNED_ID = "com.roadpulse.app.PLANNED_ID"
    private const val ACTION_REMIND = "com.roadpulse.app.LEAVE_REMINDER"
    private const val CHANNEL_ID = "leave_reminders"

    /** A planned drive whose reminder was tapped; the map screen opens its route, then clears it. */
    val openRequest = MutableStateFlow<Long?>(null)

    fun store(context: Context) = PlacesStore(File(context.filesDir, "places.json"))

    fun schedule(context: Context, drive: PlannedDrive, nowMillis: Long = System.currentTimeMillis()) {
        val at = maxOf(drive.remindAtMillis, nowMillis + 5_000)
        if (drive.leaveAtMillis < nowMillis - 60_000) return // already past: nothing to remind
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(context, drive.id)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exactAllowed) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(context, id))
    }

    /** After a phone restart (alarms are lost) or app update. */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        store(context).load().planned.forEach { schedule(context, it, now) }
    }

    private fun alarmIntent(context: Context, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, id.hashCode(),
            Intent(context, LeaveReminderReceiver::class.java).setAction(ACTION_REMIND).putExtra(EXTRA_PLANNED_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    internal fun notify(context: Context, id: Long) {
        val drive = store(context).load().planned.firstOrNull { it.id == id } ?: return
        val s = com.roadpulse.app.i18n.stringsFor(VoiceSettings.language.value)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, s.leaveRemindersChannel, NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(
            context, id.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_PLANNED_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val time = DateFormat.getTimeInstance(DateFormat.SHORT)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(s.timeToLeave(drive.destination.name))
            .setContentText(s.timeToLeaveText(drive.driveMinutes, time.format(Date(drive.arriveByMillis))))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(id.hashCode(), notification) } // no permission: nothing shown
    }
}

/** Shows the reminder when its alarm goes off. */
class LeaveReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        VoiceSettings.init(context)
        LeaveReminders.notify(context, intent.getLongExtra(LeaveReminders.EXTRA_PLANNED_ID, -1))
    }
}

/** Alarms do not survive a restart: set them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            LeaveReminders.rescheduleAll(context)
        }
    }
}
