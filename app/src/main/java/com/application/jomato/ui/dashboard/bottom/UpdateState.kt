package com.application.jomato.ui.dashboard.bottom

import java.io.File

sealed class UpdateState {
    object Idle : UpdateState()
    data class Available(val version: String, val apkUrl: String, val sha256: String) : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    object Verifying : UpdateState()
    data class ReadyToInstall(val file: File) : UpdateState()
    data class Error(val apkUrl: String, val sha256: String) : UpdateState()
}