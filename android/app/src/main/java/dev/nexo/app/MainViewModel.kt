package dev.nexo.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val _bootstrapState = MutableStateFlow<BootstrapState>(BootstrapState.NotInstalled("Iniciando..."))
    val bootstrapState = _bootstrapState.asStateFlow()

    private val _backendHealth = MutableStateFlow<BackendService.BackendHealth>(BackendService.BackendHealth.Starting("service"))
    val backendHealth = _backendHealth.asStateFlow()

    /** Inicia bootstrap observando BootstrapManager e BackendService */
    fun startObserving() {
        // O bootstrap real acontece no BackendService
        // Aqui só observamos via canais compartilhados
        // Por simplicidade, o MainActivity observa diretamente os flows
    }

    fun updateBootstrapState(state: BootstrapState) {
        viewModelScope.launch { _bootstrapState.value = state }
    }

    fun updateBackendHealth(health: BackendService.BackendHealth) {
        viewModelScope.launch { _backendHealth.value = health }
    }

    fun retryBootstrap() {
        _bootstrapState.value = BootstrapState.NotInstalled("Reiniciando...")
        // Dispara restart via BackendService
        val intent = Intent.getIntent(this)?.let { 
            Intent(it).setAction(BackendService.ACTION_RESTART) 
        } ?: Intent().setAction(BackendService.ACTION_RESTART)
        // Precisa de contexto - simplificado
    }
}

// Extension para conseguir contexto no ViewModel (simplificado)
private fun ViewModel.getIntent(): Intent? = null