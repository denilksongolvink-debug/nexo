package dev.nexo.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

/** Executa o proot com o rootfs e supervisor.sh */
class ProotRunner(
    private val context: Context,
    private val secretStore: SecretStore
) {

    private var process: Process? = null
    private var logReader: Thread? = null

    /** Inicia o backend no proot */
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        val rootfsDir = File(context.filesDir, "rootfs")
        val homeDir = File(context.filesDir, "home")
        val tmpDir = File(context.filesDir, "tmp")
        val prootTmpDir = File(context.filesDir, "proot-tmp")
        val logFile = File(context.filesDir, "logs/backend.log")

        // Cria diretórios
        homeDir.mkdirs()
        tmpDir.mkdirs()
        prootTmpDir.mkdirs()
        logFile.parentFile.mkdirs()

        // Binários do proot (extraídos de jniLibs para files/proot-bin)
        val prootBin = extractProotBinary("libproot.so")
        val prootLoader = extractProotBinary("libproot-loader.so")

        // Prepara comando
        val cmd = mutableListOf<String>().apply {
            add(prootBin.absolutePath)
            add("--kill-on-exit")
            add("--link2symlink")
            add("-0")
            add("-r")
            add(rootfsDir.absolutePath)
            add("-b")
            add("/dev")
            add("-b")
            add("/proc")
            add("-b")
            add("/sys")
            add("-b")
            add("${homeDir.absolutePath}:/root")
            add("-b")
            add("${tmpDir.absolutePath}:/tmp")
            add("-w")
            add("/root")
            add("/usr/bin/env")
            add("-i")
            add("HOME=/root")
            add("LANG=C.UTF-8")
            add("TERM=xterm-256color")
            add("PATH=/opt/hermes/venv/bin:/usr/local/bin:/usr/bin:/bin")
            add("/opt/nexo/supervisor.sh")
        }

        val pb = ProcessBuilder(cmd)
        pb.environment()["PROOT_LOADER"] = prootLoader.absolutePath
        pb.environment()["PROOT_TMP_DIR"] = prootTmpDir.absolutePath
        pb.redirectErrorStream(true)

        Log.d("ProotRunner", "Starting proot: ${cmd.joinToString(" ")}")

        try {
            process = pb.start()
            
            // Redireciona stdout/stderr para logFile
            logReader = Thread {
                val reader = process!!.inputStream.bufferedReader()
                val writer = OutputStreamWriter(FileOutputStream(logFile, true))
                reader.use { r ->
                    writer.use { w ->
                        r.forEachLine { line ->
                            w.write("$line\n")
                            w.flush()
                        }
                    }
                }
            }.apply { start() }

            // Aguarda um pouco para ver se o processo morre imediatamente
            Thread.sleep(2000)
            if (process!!.isAlive) {
                Log.d("ProotRunner", "Backend started successfully (PID: ${process!!.pid()})")
                return@withContext true
            } else {
                val exitCode = process!!.exitValue()
                Log.e("ProotRunner", "Backend died immediately with exit code $exitCode")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e("ProotRunner", "Failed to start backend", e)
            return@withContext false
        }
    }

    /** Para o backend graciosamente */
    suspend fun stop() = withContext(Dispatchers.IO) {
        process?.destroy() // SIGTERM
        try {
            if (!process!!.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process?.destroyForcibly() // SIGKILL
                process?.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
            }
        } catch (e: InterruptedException) {
            process?.destroyForcibly()
        }
        logReader?.interrupt()
        process = null
        Log.d("ProotRunner", "Backend stopped")
    }

    /** Verifica se o backend está rodando */
    fun isRunning(): Boolean = process?.isAlive == true

    /** Extrai binário nativo de jniLibs para files/proot-bin */
    private fun extractProotBinary(name: String): File {
        val destDir = File(context.filesDir, "proot-bin")
        destDir.mkdirs()
        val dest = File(destDir, name)
        
        if (dest.exists() && dest.length() > 0) return dest
        
        // Copia de assets (onde colocamos os .so no build)
        context.assets.open(name).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
        dest.setExecutable(true, false)
        return dest
    }
}