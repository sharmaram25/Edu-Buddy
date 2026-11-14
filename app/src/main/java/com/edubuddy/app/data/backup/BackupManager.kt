package com.edubuddy.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.edubuddy.app.core.Clock
import com.edubuddy.app.data.db.AppDatabase
import com.edubuddy.app.data.db.AttendanceEntity
import com.edubuddy.app.data.db.BudgetEntity
import com.edubuddy.app.data.db.ClassSlotEntity
import com.edubuddy.app.data.db.EventEntity
import com.edubuddy.app.data.db.FocusSessionEntity
import com.edubuddy.app.data.db.HolidayEntity
import com.edubuddy.app.data.db.SubjectEntity
import com.edubuddy.app.data.db.SubtaskEntity
import com.edubuddy.app.data.db.TaskEntity
import com.edubuddy.app.data.db.TransactionEntity
import com.edubuddy.app.data.db.TxnType
import com.edubuddy.app.data.model.Categories
import com.edubuddy.app.data.prefs.Settings
import com.edubuddy.app.data.prefs.SettingsRepository
import com.edubuddy.app.reminders.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.LocalDate

@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: Long,
    val appVersion: String,
    val settings: Settings,
    val subjects: List<SubjectEntity> = emptyList(),
    val classSlots: List<ClassSlotEntity> = emptyList(),
    val events: List<EventEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val subtasks: List<SubtaskEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val attendance: List<AttendanceEntity> = emptyList(),
    val focusSessions: List<FocusSessionEntity> = emptyList(),
    val holidays: List<HolidayEntity> = emptyList(),
) {
    companion object {
        const val FORMAT = "edubuddy-backup"
        const val VERSION = 1
    }
}

data class BackupStats(val subjects: Int, val classes: Int, val tasks: Int, val events: Int, val transactions: Int)

class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val reminders: ReminderScheduler,
    private val clock: Clock,
    private val appVersion: String,
) {
    suspend fun snapshot(): BackupFile = BackupFile(
        exportedAt = clock.now().toEpochMilli(),
        appVersion = appVersion,
        settings = settings.current(),
        subjects = db.subjects().all(),
        classSlots = db.classSlots().all(),
        events = db.events().all(),
        tasks = db.tasks().all(),
        subtasks = db.tasks().allSubtasks(),
        transactions = db.money().all(),
        budgets = db.money().allBudgets(),
        attendance = db.attendance().all(),
        focusSessions = db.focus().all(),
        holidays = db.holidays().all(),
    )

    fun encode(b: BackupFile): String = BackupCodec.encode(b)
    fun decode(text: String): BackupFile = BackupCodec.decode(text)

    suspend fun exportTo(uri: Uri): BackupStats = withContext(Dispatchers.IO) {
        val b = snapshot()
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(encode(b).toByteArray()) }
            ?: throw BackupException("Couldn't write to the chosen file.")
        stats(b)
    }

    suspend fun readFrom(uri: Uri): BackupFile = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            // Guard against picking a huge unrelated file.
            val bytes = input.readBytes()
            if (bytes.size > 50 * 1024 * 1024) throw BackupException("That file is too large to be a backup.")
            String(bytes)
        } ?: throw BackupException("Couldn't open the chosen file.")
        decode(text)
    }

    /** Replaces ALL current data with the backup. Atomic: on any error nothing changes. */
    suspend fun restore(b: BackupFile) {
        reminders.cancelAll()
        try {
            db.withTransaction {
                db.attendance().clear()
                db.focus().clear()
                db.holidays().clear()
                db.classSlots().clear()
                db.events().clear()
                db.tasks().clear()          // subtasks cascade
                db.money().clear()
                db.money().clearBudgets()
                db.subjects().clear()

                db.subjects().insertAll(b.subjects)
                db.classSlots().insertAll(b.classSlots)
                db.events().insertAll(b.events)
                db.tasks().insertAll(b.tasks)
                db.tasks().insertSubtasks(b.subtasks)
                db.money().insertAll(b.transactions)
                db.money().insertBudgets(b.budgets)
                db.attendance().insertAll(b.attendance)
                db.focus().insertAll(b.focusSessions)
                db.holidays().insertAll(b.holidays)
            }
        } catch (e: Exception) {
            reminders.rescheduleAll() // put back alarms for the untouched current data
            throw BackupException("The backup has inconsistent data and wasn't restored. Your current data is unchanged.")
        }
        // Keep this device's lock preference; restore everything else.
        settings.update { current -> b.settings.copy(onboarded = true, appLock = current.appLock) }
        reminders.rescheduleAll()
    }

    fun stats(b: BackupFile) = BackupStats(b.subjects.size, b.classSlots.size, b.tasks.size, b.events.size, b.transactions.size)

    /** Money history as CSV, for spreadsheets. */
    suspend fun exportTransactionsCsv(uri: Uri): Int = withContext(Dispatchers.IO) {
        val rows = db.money().allNewestFirst()
        val sb = StringBuilder("Date,Type,Category,Title,Amount (INR),Note\n")
        rows.forEach { t ->
            sb.append(LocalDate.ofEpochDay(t.epochDay)).append(',')
                .append(if (t.type == TxnType.EXPENSE) "Expense" else "Income").append(',')
                .append(csv(Categories.of(t.category).label)).append(',')
                .append(csv(t.title)).append(',')
                .append(BigDecimal.valueOf(if (t.type == TxnType.EXPENSE) -t.amountPaise else t.amountPaise, 2).toPlainString()).append(',')
                .append(csv(t.note)).append('\n')
        }
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) // BOM so Excel reads ₹/UTF-8
            it.write(sb.toString().toByteArray())
        } ?: throw BackupException("Couldn't write to the chosen file.")
        rows.size
    }

    private fun csv(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    suspend fun wipeEverything() {
        reminders.cancelAll()
        db.withTransaction {
            db.attendance().clear(); db.focus().clear(); db.classSlots().clear(); db.events().clear()
            db.tasks().clear(); db.money().clear(); db.money().clearBudgets(); db.subjects().clear()
            db.holidays().clear()
        }
        settings.clearAll()
        reminders.rescheduleAll()
    }
}

class BackupException(message: String) : Exception(message)

/** Pure JSON encoding of backups (no Android dependencies, unit-tested). */
object BackupCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true   // tolerate fields added by newer versions
        encodeDefaults = true
    }

    fun encode(b: BackupFile): String = json.encodeToString(BackupFile.serializer(), b)

    fun decode(text: String): BackupFile {
        val b = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw BackupException("This file isn't an EduBuddy backup or is damaged.")
        }
        if (b.format != BackupFile.FORMAT) throw BackupException("This file isn't an EduBuddy backup.")
        if (b.version > BackupFile.VERSION) throw BackupException("This backup was made by a newer version of EduBuddy.")
        return b
    }
}
