package dev.nexo.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Foreground Service que gerencia o ciclo de vida do backend (proot + supervisor) */
class BackendService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var healthCheckJob: kotlinx.coroutines.Job? = null
    private var prootRunner: ProotRunner? = null
    private var bootstrapManager: BootstrapManager? = null
    private var secretStore: SecretStore? = null

    private lateinit var notificationManager: NotificationManager
    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "nexo_backend_channel"
        const val ACTION_START = "dev.nexo.app.START_BACKEND"
        const val ACTION_STOP = "dev.nexo.app.STOP_BACKEND"
        const val ACTION_RESTART = "dev.nexo.app.RESTART_BACKEND"

        sealed interface BackendHealth {
            data class Starting(val component: String) : BackendHealth
            data class ComponentReady(val component: String) : BackendHealth
            object Healthy : BackendHealth
            data class Degraded(val message: String) : BackendHealth
            data class Unhealthy(val message: String) : BackendHealth
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        powerManager = getSystemService(PowerManager::class.java)

        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Nexo::BackendBootstrap"
        ).apply { setReferenceCounted(false) }

        createNotificationChannel()

        secretStore = SecretStore(this)
        bootstrapManager = BootstrapManager(this, secretStore!!)
        prootRunner = ProotRunner(this, secretStore!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> startBackend()
            ACTION_STOP -> stopBackend()
            ACTION_RESTART -> restartBackend()
        }

        return START_STICKY
    }

    private fun startBackend() {
        wakeLock?.acquire(10 * 60 * 1000)

        showNotification("Iniciando backend...", BackendHealth.Starting("bootstrap"))

        scope.launch {
            try {
                secretStore!!.ensureSecretsGenerated()
                secretStore!!.rotateNonce()
                secretStore!!.writeRuntimeEnv()

                bootstrapManager!!.start()
                bootstrapManager!!.stateFlow.collect { state ->
                    when (state) {
                        is BootstrapState.Downloading ->
                            showNotification("Baixando rootfs: ${(state.progress * 100).toInt()}%",
                                BackendHealth.Starting("download"))
                        is BootstrapState.Extracting ->
                            showNotification("Extraindo rootfs: ${(state.progress * 100).toInt()}%",
                                BackendHealth.Starting("extract"))
                        is BootstrapState.Ready -> {
                            launch { startProot() }
                        }
                        is BootstrapState.Error -> {
                            showNotification("Erro: ${state.message}", BackendHealth.Unhealthy(state.message))
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                showNotification("Erro no bootstrap: ${e.message}", BackendHealth.Unhealthy(e.message ?: "unknown"))
            }
        }
    }

    private suspend fun startProot() {
        showNotification("Iniciando supervisor...", BackendHealth.Starting("supervisor"))

        val started = prootRunner!!.start()
        if (!started) {
            showNotification("Falha ao iniciar supervisor", BackendHealth.Unhealthy("supervisor died"))
            scheduleRestart(2000)
            return
        }

        showNotification("Supervisor iniciado", BackendHealth.ComponentReady("supervisor"))
        startHealthCheck()
    }

    private fun startHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch {
            var consecutiveFailures = 0
            val maxFailures = 3

            while (true) {
                kotlinx.coroutines.delay(5000)

                val healthy = checkHealth()
                if (healthy) {
                    consecutiveFailures = 0
                    showNotification("Backend saudável", BackendHealth.Healthy)
                } else {
                    consecutiveFailures++
                    showNotification("Backend instável ($consecutiveFailures/$maxFailures)",
                        BackendHealth.Degraded("health check failed"))

                    if (consecutiveFailures >= maxFailures) {
                        showNotification("Reiniciando backend...", BackendHealth.Starting("restart"))
                        restartProot()
                        consecutiveFailures = 0
                    }
                }
            }
        }
    }

    private suspend fun checkHealth(): Boolean {
        return try {
            val edgePort = secretStore!!.getEdgePort()
            val url = "http://127.0.0.1:$edgePort/healthz"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            response.use {
                it.isSuccessful && it.body?.string()?.contains("true") == true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun restartProot() {
        scope.launch {
            prootRunner?.stop()
            kotlinx.coroutines.delay(1000)
            val started = prootRunner!!.start()
            if (!started) {
                scheduleRestart(5000)
            }
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        scope.launch {
            kotlinx.coroutines.delay(delayMs)
            restartProot()
        }
    }

    private fun stopBackend() {
        healthCheckJob?.cancel()
        scope.launch {
            prootRunner?.stop()
            wakeLock?.release()
            stopForeground(true)
            stopSelf()
        }
    }

    private fun restartBackend() {
        stopBackend()
        scope.launch {
            kotlinx.coroutines.delay(1000)
            startBackend()
        }
    }

    // --- Notification ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Backend Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hermes backend running in background"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(text: String, health: BackendHealth) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Nexo Backend")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        stopBackend()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}