'use client';

import { useEffect, useState } from 'react';
import { Bot, Settings, MessageSquare, Terminal, Wifi, AlertCircle, CheckCircle } from 'lucide-react';
import { useApp } from '@/components/providers';

export default function Home() {
  const { isAuthenticated, setIsAuthenticated, edgeReady, setEdgeReady } = useApp();
  const [status, setStatus] = useState<'checking' | 'ready' | 'error'>('checking');
  const [errorMsg, setErrorMsg] = useState('');

  useEffect(() => {
    // Verifica se o edge está respondendo
    const checkEdge = async () => {
      try {
        const res = await fetch('/healthz');
        if (res.ok) {
          const data = await res.json();
          if (data.edge && data.gateway && data.dashboard) {
            setEdgeReady(true);
            setIsAuthenticated(true);
            setStatus('ready');
          } else {
            setStatus('error');
            setErrorMsg('Serviços não prontos: ' + JSON.stringify(data));
          }
        } else {
          setStatus('error');
          setErrorMsg(`Health check falhou: ${res.status}`);
        }
      } catch (e) {
        setStatus('error');
        setErrorMsg('Erro de conexão: ' + (e as Error).message);
      }
    };

    checkEdge();
    const interval = setInterval(checkEdge, 5000);
    return () => clearInterval(interval);
  }, [setEdgeReady, setIsAuthenticated]);

  if (status === 'checking') {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center gap-6 p-6 bg-background">
        <div className="animate-pulse-soft flex flex-col items-center gap-4">
          <Bot className="h-16 w-16 text-primary" />
          <div className="text-center">
            <h1 className="text-3xl font-bold tracking-tight">Nexo</h1>
            <p className="text-muted-foreground mt-1">Iniciando backend Hermes...</p>
          </div>
          <div className="flex gap-2">
            <div className="w-2 h-2 bg-primary rounded-full animate-bounce" style={{animationDelay: '0ms'}} />
            <div className="w-2 h-2 bg-primary rounded-full animate-bounce" style={{animationDelay: '150ms'}} />
            <div className="w-2 h-2 bg-primary rounded-full animate-bounce" style={{animationDelay: '300ms'}} />
          </div>
        </div>
      </div>
    );
  }

  if (status === 'error') {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center gap-6 p-6 bg-background">
        <AlertCircle className="h-16 w-16 text-destructive" />
        <div className="text-center max-w-md">
          <h1 className="text-2xl font-bold">Erro ao conectar</h1>
          <p className="text-muted-foreground mt-2">{errorMsg}</p>
          <p className="text-sm text-muted-foreground mt-4">
            Verifique se o app tem permissão de rede e tente reiniciar.
          </p>
          <button
            onClick={() => window.location.reload()}
            className="mt-6 px-6 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90"
          >
            Tentar novamente
          </button>
        </div>
      </div>
    );
  }

  // Status ready - mostra dashboard principal
  return (
    <div className="min-h-screen bg-background">
      <header className="border-b bg-card/50 backdrop-blur-sm sticky top-0 z-40">
        <div className="container mx-auto px-4 py-3 flex items-center justify-between">
          <h1 className="text-xl font-bold flex items-center gap-2">
            <Bot className="h-5 w-5 text-primary" />
            Nexo
          </h1>
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <CheckCircle className="h-3 w-3 text-green-500" />
              <span>Edge</span>
            </div>
            <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <CheckCircle className="h-3 w-3 text-green-500" />
              <span>Gateway</span>
            </div>
            <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <CheckCircle className="h-3 w-3 text-green-500" />
              <span>Dashboard</span>
            </div>
          </div>
        </div>
      </header>

      <main className="container mx-auto px-4 py-6">
        <nav className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          <a
            href="/chat"
            className="group p-5 rounded-xl bg-card border border-border hover:border-primary/50 transition-colors"
          >
            <div className="flex items-center gap-3">
              <div className="p-3 rounded-lg bg-primary/10 group-hover:bg-primary/20 transition-colors">
                <MessageSquare className="h-6 w-6 text-primary" />
              </div>
              <div>
                <h3 className="font-semibold">Chat</h3>
                <p className="text-sm text-muted-foreground">Conversar com o agente</p>
              </div>
            </div>
          </a>

          <a
            href="/sessions"
            className="group p-5 rounded-xl bg-card border border-border hover:border-primary/50 transition-colors"
          >
            <div className="flex items-center gap-3">
              <div className="p-3 rounded-lg bg-primary/10 group-hover:bg-primary/20 transition-colors">
                <Terminal className="h-6 w-6 text-primary" />
              </div>
              <div>
                <h3 className="font-semibold">Sessões</h3>
                <p className="text-sm text-muted-foreground">Histórico e fork</p>
              </div>
            </div>
          </a>

          <a
            href="/settings"
            className="group p-5 rounded-xl bg-card border border-border hover:border-primary/50 transition-colors"
          >
            <div className="flex items-center gap-3">
              <div className="p-3 rounded-lg bg-primary/10 group-hover:bg-primary/20 transition-colors">
                <Settings className="h-6 w-6 text-primary" />
              </div>
              <div>
                <h3 className="font-semibold">Configurações</h3>
                <p className="text-sm text-muted-foreground">Modelos, skills, MCP</p>
              </div>
            </div>
          </a>

          <a
            href="/skills"
            className="group p-5 rounded-xl bg-card border border-border hover:border-primary/50 transition-colors"
          >
            <div className="flex items-center gap-3">
              <div className="p-3 rounded-lg bg-primary/10 group-hover:bg-primary/20 transition-colors">
                <Bot className="h-6 w-6 text-primary" />
              </div>
              <div>
                <h3 className="font-semibold">Skills</h3>
                <p className="text-sm text-muted-foreground">Gerenciar skills</p>
              </div>
            </div>
          </a>

          <a
            href="/mcp"
            className="group p-5 rounded-xl bg-card border border-border hover:border-primary/50 transition-colors"
          >
            <div className="flex items-center gap-3">
              <div className="p-3 rounded-lg bg-primary/10 group-hover:bg-primary/20 transition-colors">
                <Wifi className="h-6 w-6 text-primary" />
              </div>
              <div>
                <h3 className="font-semibold">MCP</h3>
                <p className="text-sm text-muted-foreground">Servidores MCP</p>
              </div>
            </div>
          </a>

          <a
            href="/cron"
            className="group p-5 rounded-xl bg-card border border-border hover:border-primary/50 transition-colors"
          >
            <div className="flex items-center gap-3">
              <div className="p-3 rounded-lg bg-primary/10 group-hover:bg-primary/20 transition-colors">
                <Terminal className="h-6 w-6 text-primary" />
              </div>
              <div>
                <h3 className="font-semibold">Agendamentos</h3>
                <p className="text-sm text-muted-foreground">Cron jobs</p>
              </div>
            </div>
          </a>
        </nav>
      </main>
    </div>
  );
}