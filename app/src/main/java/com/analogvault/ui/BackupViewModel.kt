package com.analogvault.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.analogvault.data.backup.BackupManager
import com.analogvault.data.backup.BackupResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager
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

    fun clearResult() { _result.value = null }
}
