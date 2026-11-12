/** @type {import('next').NextConfig} */
const nextConfig = {
  // Static export para servir via nexo_edge.py
  output: 'export',
  trailingSlash: true,
  images: {
    unoptimized: true,
  },
  // Desabilita server-side features
  // Não usar: API routes, server actions, middleware, getServerSideProps
  // Tudo roda client-side via /hx/api/* e /hx/dash/* proxies
  
  // Base path vazio (servido na raiz do edge)
  // assetPrefix: '', // não necessário para export estático
  
  // Configuração de headers para desenvolvimento
  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'X-Frame-Options', value: 'DENY' },
          { key: 'X-XSS-Protection', value: '1; mode=block' },
        ],
      },
    ];
  },
};

module.exports = nextConfig;