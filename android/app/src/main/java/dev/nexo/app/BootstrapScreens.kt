package dev.nexo.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tela de progresso do bootstrap */
@Composable
fun BootstrapProgressScreen(
    state: BootstrapState,
    health: BackendService.BackendHealth
) {
    val (progress, message) = when (state) {
        is BootstrapState.NotInstalled -> 0f to state.reason
        is BootstrapState.Downloading -> state.progress to "Baixando rootfs: ${(state.progress * 100).toInt()}%"
        is BootstrapState.Verifying -> state.progress to "Verificando integridade..."
        is BootstrapState.Extracting -> state.progress to "Extraindo rootfs: ${(state.progress * 100).toInt()}%"
        is BootstrapState.Ready -> 1f to "Pronto!"
        is BootstrapState.Error -> 0f to "Erro: ${state.message}"
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo animado
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.size(80.dp),
            strokeWidth = 4.dp,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(Modifier.padding(24.dp))
        
        Text(
            text = "Nexo",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(Modifier.padding(8.dp))
        
        Text(
            text = message,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.TextAlign.Center
        )
        
        Spacer(Modifier.padding(24.dp))
        
        // Status dos componentes
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ComponentStatus("Edge Server", health)
            ComponentStatus("Gateway (8642)", health)
            ComponentStatus("Dashboard (9119)", health)
        }
    }
}

@Composable
fun ComponentStatus(name: String, health: BackendService.BackendHealth) {
    val (ready, color) = when (health) {
        is BackendService.BackendHealth.Healthy -> true to MaterialTheme.colorScheme.primary
        is BackendService.BackendHealth.ComponentReady -> true to MaterialTheme.colorScheme.primary
        is BackendService.BackendHealth.Starting -> false to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        is BackendService.BackendHealth.Degraded -> false to MaterialTheme.colorScheme.warning
        is BackendService.BackendHealth.Unhealthy -> false to MaterialTheme.colorScheme.error
    }
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(name, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            modifier = Modifier.size(12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (ready) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = color
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = color
                )
            }
        }
    }
}

/** Tela de erro com botão de retry */
@Composable
fun BootstrapErrorScreen(
    state: BootstrapState.Error,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = androidx.compose.material.icons.Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(Modifier.padding(16.dp))
        
        Text(
            text = "Erro no bootstrap",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(Modifier.padding(8.dp))
        
        Text(
            text = state.message,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.TextAlign.Center,
            maxLines = 5
        )
        
        Spacer(Modifier.padding(32.dp))
        
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )) {
            Text("Tentar novamente", fontSize = 16.sp)
        }
    }
}