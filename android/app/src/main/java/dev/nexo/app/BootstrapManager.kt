package dev.nexo.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Estados do bootstrap */
sealed interface BootstrapState {
    data class NotInstalled(val reason: String) : BootstrapState
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : BootstrapState
    data class Verifying(val progress: Float) : BootstrapState
    data class Extracting(val progress: Float) : BootstrapState
    data object Ready : BootstrapState
    data class Error(val message: String) : BootstrapState
}

/** Gerencia download, verificação e extração do rootfs */
class BootstrapManager(
    private val context: Context,
    private val secretStore: SecretStore
) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Canal para emitir estados (buffer para último estado)
    private val stateChannel = Channel<BootstrapState>(Channel.BUFFERED)
    
    /** Fluxo de estados para UI observar */
    val stateFlow: kotlinx.coroutines.flow.Flow<BootstrapState> = stateChannel.receiveAsFlow()

    /** Versão esperada do rootfs (vem do versions.env no build) */
    private val expectedVersion = "v0.23.0"
    private val expectedSha256 = "" // TODO: injetar no build

    /** URL de download do rootfs (GitHub Releases) */
    private val downloadUrl = "https://github.com/seu-usuario/nexo/releases/download/rootfs-v0.23.0/rootfs-arm64.tar.zst"

    /** Inicia ou retoma o bootstrap */
    fun start() {
        scope.launch {
            try {
                if (isRootfsReady()) {
                    emit(BootstrapState.Ready)
                    return@launch
                }
                downloadAndExtract()
            } catch (e: Exception) {
                Log.e("BootstrapManager", "Bootstrap failed", e)
                emit(BootstrapState.Error(e.message ?: "Unknown error"))
            }
        }
    }

    /** Força re-download e re-extracão */
    fun forceReinstall() {
        scope.launch {
            clearRootfs()
            downloadAndExtract()
        }
    }

    private suspend fun downloadAndExtract() {
        val tempDir = context.cacheDir
        val tempFile = File(tempDir, "rootfs-arm64.tar.zst.tmp")
        val finalFile = File(tempDir, "rootfs-arm64.tar.zst")
        val extractDir = File(context.filesDir, "rootfs.tmp")
        val finalDir = File(context.filesDir, "rootfs")

        try {
            // 1. Download
            emit(BootstrapState.Downloading(0f, 0, 0))
            downloadFile(downloadUrl, tempFile) { progress, downloaded, total ->
                emit(BootstrapState.Downloading(progress, downloaded, total))
            }

            // 2. Verifica SHA256 (se definido)
            if (expectedSha256.isNotEmpty()) {
                emit(BootstrapState.Verifying(0f))
                val actualSha = sha256(tempFile)
                if (actualSha != expectedSha256) {
                    throw SecurityException("SHA256 mismatch: expected $expectedSha256, got $actualSha")
                }
                emit(BootstrapState.Verifying(1f))
            }

            // Move arquivo verificado
            tempFile.renameTo(finalFile)

            // 3. Extrai
            emit(BootstrapState.Extracting(0f))
            extractRootfs(finalFile, extractDir) { progress ->
                emit(BootstrapState.Extracting(progress))
            }

            // 4. Atomic rename
            if (finalDir.exists()) {
                deleteRecursively(finalDir)
            }
            extractDir.renameTo(finalDir)

            // Grava versão
            File(context.filesDir, "rootfs/.nexo-version").writeText(expectedVersion)

            emit(BootstrapState.Ready)

        } catch (e: Exception) {
            // Cleanup
            tempFile.delete()
            finalFile.delete()
            deleteRecursively(extractDir)
            throw e
        }
    }

    private suspend fun downloadFile(url: String, dest: File, progress: (Float, Long, Long) -> Unit) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("Download failed: ${response.code}")
            val total = response.body?.contentLength() ?: 0L
            val sink = FileOutputStream(dest)
            val source = response.body?.source()
            var downloaded = 0L
            val buffer = java.nio.ByteBuffer.allocate(8192)
            
            while (true) {
                val read = source?.read(buffer) ?: -1
                if (read == -1) break
                buffer.flip()
                sink.write(buffer.array(), 0, read)
                buffer.clear()
                downloaded += read
                if (total > 0) progress(downloaded.toFloat() / total, downloaded, total)
            }
            sink.flush()
        }
    }

    private suspend fun extractRootfs(archive: File, destDir: File, progress: (Float) -> Unit) {
        // Usa zstd + tar via ProcessBuilder (requer zstd e tar no sistema)
        // No APK final, podemos usar libzstd-jni + commons-compress
        // Por simplicidade, delegamos para comando shell se disponível
        val cmd = arrayOf("sh", "-c", "zstd -d -c \"${archive.absolutePath}\" | tar -x -C \"${destDir.absolutePath}\"")
        val process = ProcessBuilder(*cmd).start()
        
        // Monitora progresso via tamanho do diretório
        val totalSize = archive.length()
        while (process.isAlive) {
            Thread.sleep(500)
            val currentSize = getDirSize(destDir)
            progress((currentSize.toFloat() / totalSize).coerceAtMost(1f))
        }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val err = process.errorStream.readBytes().decodeToString()
            throw java.io.IOException("Extract failed (exit $exitCode): $err")
        }
        progress(1f)
    }

    private fun isRootfsReady(): Boolean {
        val versionFile = File(context.filesDir, "rootfs/.nexo-version")
        return versionFile.exists() && versionFile.readText().trim() == expectedVersion &&
               File(context.filesDir, "rootfs/opt/nexo/supervisor.sh").exists()
    }

    private suspend fun clearRootfs() {
        deleteRecursively(File(context.filesDir, "rootfs"))
        deleteRecursively(File(context.filesDir, "rootfs.tmp"))
    }

    private fun getDirSize(dir: File): Long {
        if (!dir.exists()) return 0
        return dir.walk().filter { it.isFile }.sumOf { it.length() }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val fis = java.io.FileInputStream(file)
        val buffer = ByteArray(8192)
        var read: Int
        while (fis.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
        fis.close()
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }

    private fun emit(state: BootstrapState) {
        scope.launch { stateChannel.send(state) }
    }
}