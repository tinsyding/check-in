package com.jf.checkin.ui.zip

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jf.checkin.CheckInApplication
import com.jf.checkin.logic.zip.ImageZipBuilder
import com.jf.checkin.logic.zip.archiveFileName
import com.jf.checkin.logic.zip.datePrefix
import com.jf.checkin.util.imageUris
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

const val MAX_ZIP_IMAGES = 100

private const val EXPORT_DIR = "zip_exports"

data class ReadyArchive(
    val file: File,
    val name: String,
    val size: Long,
    val count: Int,
)

sealed interface BuildState {
    data object Idle : BuildState
    data class Building(val done: Int, val total: Int) : BuildState
    data class Ready(val archive: ReadyArchive) : BuildState
}

data class ZipUiState(
    val uris: List<Uri> = emptyList(),
    val name: String = "",
    val nameEdited: Boolean = false,
    val defaultName: String = "",
    val prefix: String = datePrefix(),
    val build: BuildState = BuildState.Idle,
    val message: String? = null,
)

class ZipViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = (application as CheckInApplication).prefRepository
    private val resolver = application.contentResolver

    private val _state = MutableStateFlow(ZipUiState())
    val state: StateFlow<ZipUiState> = _state.asStateFlow()

    private var buildJob: Job? = null
    private var saveJob: Job? = null

    init {
        clearExports()
        val savedName = prefs.zipDefaultName.ifBlank { prefs.teacherName }
        _state.update { current ->
            current.copy(
                defaultName = savedName,
                name = if (current.nameEdited) current.name else savedName,
            )
        }
    }

    /** 处理从其他 App 分享进来的图片；重复分享时替换当前列表。 */
    fun handleShareIntent(intent: Intent?) {
        val incoming = intent.imageUris()
        if (incoming.isEmpty()) return

        buildJob?.cancel()
        clearExports()
        val limited = incoming.take(MAX_ZIP_IMAGES)
        _state.update { current ->
            current.copy(
                uris = limited,
                name = current.defaultName,
                nameEdited = false,
                build = BuildState.Idle,
                message = if (incoming.size > MAX_ZIP_IMAGES) {
                    "最多打包 $MAX_ZIP_IMAGES 张图片，已保留前 $MAX_ZIP_IMAGES 张"
                } else {
                    null
                },
            )
        }
    }

    fun refreshDate() {
        val today = datePrefix()
        _state.update { current ->
            if (current.build is BuildState.Ready || current.prefix == today) {
                current
            } else {
                current.copy(prefix = today)
            }
        }
    }

    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        buildJob?.cancel()
        _state.update { current ->
            val merged = (current.uris + uris).distinct()
            val limited = merged.take(MAX_ZIP_IMAGES)
            current.copy(
                uris = limited,
                build = BuildState.Idle,
                message = if (merged.size > MAX_ZIP_IMAGES) {
                    "最多打包 $MAX_ZIP_IMAGES 张图片"
                } else {
                    null
                },
            )
        }
    }

    fun removeUri(uri: Uri) {
        buildJob?.cancel()
        _state.update { current ->
            current.copy(
                uris = current.uris - uri,
                build = BuildState.Idle,
            )
        }
    }

    fun clearUris() {
        buildJob?.cancel()
        clearExports()
        _state.update { current ->
            current.copy(
                uris = emptyList(),
                build = BuildState.Idle,
                message = null,
            )
        }
    }

    fun setName(value: String) {
        _state.update { current ->
            current.copy(name = value.take(24), nameEdited = true)
        }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(300)
            prefs.zipDefaultName = _state.value.name.trim()
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    fun generate() {
        val snapshot = _state.value
        if (snapshot.uris.isEmpty() || snapshot.build is BuildState.Building) return

        buildJob?.cancel()
        val prefix = datePrefix()
        val fileName = archiveFileName(prefix, snapshot.name)

        saveJob?.cancel()
        prefs.zipDefaultName = snapshot.name.trim()

        buildJob = viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    prefix = prefix,
                    build = BuildState.Building(0, snapshot.uris.size),
                    message = null,
                )
            }

            val directory = File(getApplication<Application>().cacheDir, EXPORT_DIR).apply {
                mkdirs()
            }
            directory.listFiles()?.forEach { it.delete() }

            val output = File(directory, fileName)
            try {
                ImageZipBuilder.build(resolver, snapshot.uris, prefix, output) { done ->
                    _state.update { current ->
                        val building = current.build
                        if (building is BuildState.Building) {
                            current.copy(build = building.copy(done = done))
                        } else {
                            current
                        }
                    }
                }

                if (!isActive) {
                    output.delete()
                    return@launch
                }

                _state.update {
                    it.copy(
                        build = BuildState.Ready(
                            ReadyArchive(
                                file = output,
                                name = fileName,
                                size = output.length(),
                                count = snapshot.uris.size,
                            ),
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                output.delete()
                throw cancelled
            } catch (error: Throwable) {
                output.delete()
                _state.update {
                    it.copy(build = BuildState.Idle, message = "生成失败，请重试")
                }
            }
        }
    }

    private fun clearExports() {
        File(getApplication<Application>().cacheDir, EXPORT_DIR)
            .listFiles()
            ?.forEach { it.delete() }
    }
}
