#!/usr/bin/env bash
# ==============================================================================
# Setup Antigravity NetBeans Bridge Suite
# Configura schemas MCP, instruções mestres, regras e templates de tela
# ==============================================================================

set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MCP_DIR="$HOME/.gemini/antigravity/mcp/netbeans-bridge"
RULES_DIR="$HOME/.gemini/config/rules"
CONFIG_DIR="$HOME/.gemini/config"

echo "======================================================================"
echo "    INSTALANDO ANTIGRAVITY NETBEANS BRIDGE SUITE & PROTOCOLO DE TELAS"
echo "======================================================================"

# 1. Copiar Schemas e Instruções do MCP
echo "[1/4] Instalando schemas MCP e instruções mestres..."
mkdir -p "$MCP_DIR"
cp -f "$DIR"/mcp-schemas/*.json "$MCP_DIR/"
cp -f "$DIR"/mcp-schemas/instructions.md "$MCP_DIR/"

# 2. Instalar Regra Permanente do Antigravity
echo "[2/4] Instalando regra permanente (Buffer in-memory + Preview de Telas)..."
mkdir -p "$RULES_DIR"
cp -f "$DIR"/rules/netbeans_bridge.md "$RULES_DIR/"

# 3. Garantir cópia local do servidor se necessário
echo "[3/4] Validando servidor MCP Python..."
python3 -c "import urllib.request, json; print('  -> Python 3 OK')"

# 4. Validar conectividade com o NetBeans (se aberto)
echo "[4/4] Verificando status da ponte no NetBeans (127.0.0.1:8388)..."
if curl -s --max-time 1 http://127.0.0.1:8388/ping > /dev/null 2>&1; then
    echo "  -> NetBeans Bridge ativa e respondendo na porta 8388!"
else
    echo "  -> NetBeans Bridge offline (abra o NetBeans para habilitar a conexão)."
fi

echo "======================================================================"
echo "  SUCESSO: Instalação concluída com êxito!"
echo "  - Schemas e Instruções: $MCP_DIR"
echo "  - Regra Ativa:          $RULES_DIR/netbeans_bridge.md"
echo "  - Template de Telas:    $DIR/templates/template_preview_swing.html"
echo "======================================================================"
