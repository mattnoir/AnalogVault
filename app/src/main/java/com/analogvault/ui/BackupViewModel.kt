package com.analogvault.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.analogvault.data.backup.BackupManager
import com.analogvault.data.backup.BackupResult
import com.analogvault.data.export.ExportResult
import com.analogvault.data.export.RollExporter
import com.analogvault.data.repo.VaultRepository
import com.analogvault.util.AchCounters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val repo: VaultRepository,
    private val rollExporter: RollExporter
) : ViewModel() {

    private val _result = MutableStateFlow<BackupResult?>(null)
    val result: StateFlow<BackupResult?> = _result

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    fun export(context: Context, uri: Uri, includePhotos: Boolean = true) = viewModelScope.launch {
        _busy.value = true
        val res = backupManager.export(context, uri, includePhotos)
        if (res is BackupResult.Success) bumpAchCounter(AchCounters.BACKUP_EXPORTS)
        _result.value = res
        _busy.value = false
    }

    fun import(context: Context, uri: Uri) = viewModelScope.launch {
        _busy.value = true
        // An empty vault before the restore is a new phone or a fresh
        // install: the "Hello, Old Friend" achievement.
        val wasEmpty = repo.rolls.first().isEmpty() && repo.films.first().isEmpty() &&
            repo.cameras.first().isEmpty() && repo.lenses.first().isEmpty()
        val res = backupManager.import(context, uri)
        if (res is BackupResult.Success && wasEmpty) bumpAchCounter(AchCounters.FRESH_IMPORTS)
        _result.value = res
        _busy.value = false
    }

    // After the backup's own counters are merged in, so the restore cannot
    // take this count back down.
    private suspend fun bumpAchCounter(name: String) =
        repo.updateSetting(AchCounters.SETTING) { AchCounters.increment(it, listOf(name)) }

    /** Every shot from every roll as one flat CSV. */
    fun exportAllCsv(context: Context, uri: Uri) = viewModelScope.launch {
        _busy.value = true
        val res = rollExporter.writeAllCsv(
            context, uri,
            rolls = repo.rolls.first(),
            films = repo.films.first(),
            cameras = repo.cameras.first()
        )
        _result.value = when (res) {
            is ExportResult.Success -> BackupResult.Success(res.message)
            is ExportResult.Error   -> BackupResult.Error(res.message)
        }
        _busy.value = false
    }

    fun clearResult() { _result.value = null }
}
