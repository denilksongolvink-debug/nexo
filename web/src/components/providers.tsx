'use client';

import { ReactNode, useState, createContext, useContext } from 'react';
import { ThemeProvider } from 'next-themes';

// Context para estado global da app (auth, config, etc.)
interface AppContextType {
  isAuthenticated: boolean;
  setIsAuthenticated: (v: boolean) => void;
  config: Record<string, unknown>;
  setConfig: (c: Record<string, unknown>) => void;
  edgeReady: boolean;
  setEdgeReady: (v: boolean) => void;
}

const AppContext = createContext<AppContextType | null>(null);

export function useApp() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useApp deve ser usado dentro de AppProvider');
  return ctx;
}

export function AppProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [config, setConfig] = useState<Record<string, unknown>>({});
  const [edgeReady, setEdgeReady] = useState(false);

  return (
    <AppContext.Provider value={{ isAuthenticated, setIsAuthenticated, config, setConfig, edgeReady, setEdgeReady }}>
      <ThemeProvider attribute="class" defaultTheme="system" enableSystem>
        {children}
      </ThemeProvider>
    </AppContext.Provider>
  );
}