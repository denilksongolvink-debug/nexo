package dev.nexo.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Foreground Service que gerencia o ciclo de vida do backend (proot + supervisor) */
class BackendService : LifecycleService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private var bootstrapJob: Job? = null
    private var healthCheckJob: Job? = null
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
        
        // Estados de saúde expostos para UI
        sealed interface BackendHealth {
            data class Starting(val component: String) : BackendHealth
            data class ComponentReady(val component: String) : BackendHealth
            data class Healthy : BackendHealth
            data class Degraded(val message: String) : BackendHealth
            data class Unhealthy(val message: String) : BackendHealth
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        powerManager = getSystemService(PowerManager::class.java)
        
        // Wake lock para manter CPU acordado durante bootstrap
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Nexo::BackendBootstrap"
        ).apply { isReferenceCounted = false }
        
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
        
        // START_STICKY: sistema reinicia se matar por memória
        return START_STICKY
    }

    private fun startBackend() {
        // Adquire wake lock durante bootstrap
        wakeLock?.acquire(10 * 60 * 1000) // max 10 min
        
        showNotification("Iniciando backend...", BackendHealth.Starting("bootstrap"))
        
        bootstrapJob = scope.launch {
            try {
                // 1. Garante segredos
                secretStore!!.ensureSecretsGenerated()
                secretStore!!.rotateNonce()
                secretStore!!.writeRuntimeEnv()
                
                // 2. Bootstrap do rootfs (download/extract se necessário)
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
                            // 3. Inicia proot + supervisor
                            launch { startProot() }
                        }
                        is BootstrapState.Error -> {
                            showNotification("Erro: ${state.message}", BackendHealth.Unhealthy(state.message))
                        }
                    }
                }
            } catch (e: Exception) {
                showNotification("Erro no bootstrap: ${e.message}", BackendHealth.Unhealthy(e.message))
            }
        }
    }

    private suspend fun startProot() {
        showNotification("Iniciando supervisor...", BackendHealth.Starting("supervisor"))
        
        val started = prootRunner!!.start()
        if (!started) {
            showNotification("Falha ao iniciar supervisor", BackendHealth.Unhealthy("supervisor died"))
            // Tenta reiniciar com backoff
            scheduleRestart(2000)
            return
        }
        
        showNotification("Supervisor iniciado", BackendHealth.ComponentReady("supervisor"))
        
        // Inicia health check periódico
        startHealthCheck()
    }

    private fun startHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch {
            var consecutiveFailures = 0
            val maxFailures = 3
            
            while (true) {
                delay(5000) // check every 5s
                
                val healthy = checkHealth()
                if (healthy) {
                    consecutiveFailures = 0
                    showNotification("Backend saudável", BackendHealth.Healthy)
                } else {
                    consecutiveFailures++
                    showNotification("Backend instável ($consecutiveFailures/$maxFailures)", 
                        BackendHealth.Degraded("health check failed"))
                    
                    if (consecutiveFailures >= maxFailures) {
                        // Reinicia backend
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
            delay(1000)
            val started = prootRunner!!.start()
            if (!started) {
                scheduleRestart(5000)
            }
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        scope.launch {
            delay(delayMs)
            restartProot()
        }
    }

    private fun stopBackend() {
        healthCheckJob?.cancel()
        bootstrapJob?.cancel()
        prootRunner?.stop()
        wakeLock?.release()
        stopForeground(true)
        stopSelf()
    }

    private fun restartBackend() {
        stopBackend()
        scope.launch {
            delay(1000)
            startBackend()
        }
    }

    // --- Notification ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
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

        val (icon, color) = when (health) {
            is BackendHealth.Healthy -> R.drawable.ic_baseline_check_circle_24 to 0xFF0EA5E9
            is BackendHealth.ComponentReady -> R.drawable.ic_baseline_check_circle_24 to 0xFF0EA5E9
            is BackendHealth.Starting -> R.drawable.ic_baseline_hourglass_empty_24 to 0xFF0EA5E9
            is BackendHealth.Degraded -> R.drawable.ic_baseline_warning_24 to 0xFFF59E0B
            is BackendHealth.Unhealthy -> R.drawable.ic_baseline_error_24 to 0xFFEF4444
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setColor(color)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        stopBackend()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}