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
        _result.value = backupManager.export(context, uri, includePhotos)
        _busy.value = false
    }

    fun import(context: Context, uri: Uri) = viewModelScope.launch {
        _busy.value = true
        _result.value = backupManager.import(context, uri)
        _busy.value = false
    }

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
