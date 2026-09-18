#!/usr/bin/env bash
# ==============================================================================
# Setup Antigravity NetBeans Bridge Suite
# Configura schemas MCP, instruções mestres, regras, templates e instala o plugin
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
echo "[1/5] Instalando schemas MCP e instruções mestres..."
mkdir -p "$MCP_DIR"
cp -f "$DIR"/mcp-schemas/*.json "$MCP_DIR/"
cp -f "$DIR"/mcp-schemas/instructions.md "$MCP_DIR/"

# 2. Instalar Regra Permanente do Antigravity
echo "[2/5] Instalando regra permanente (Buffer in-memory + Preview de Telas)..."
mkdir -p "$RULES_DIR"
cp -f "$DIR"/rules/netbeans_bridge.md "$RULES_DIR/"

# 3. Instalar o Plugin no NetBeans a partir do .nbm pronto em dist/
echo "[3/5] Instalando plugin no Apache NetBeans..."
NBM_FILE="$DIR/dist/agy-nb-bridge-latest.nbm"
if [ -f "$NBM_FILE" ] && [ -d "$HOME/.netbeans" ]; then
    TEMP_DIR=$(mktemp -d)
    unzip -q -o "$NBM_FILE" -d "$TEMP_DIR" 2>/dev/null || true
    for nb_version in "$HOME/.netbeans"/*; do
        if [ -d "$nb_version" ] && [[ "$(basename "$nb_version")" =~ ^[0-9]+ ]]; then
            mkdir -p "$nb_version/modules" "$nb_version/config/Modules"
            if [ -f "$TEMP_DIR/netbeans/modules/com-merito-agy-nb-bridge.jar" ]; then
                cp -f "$TEMP_DIR/netbeans/modules/com-merito-agy-nb-bridge.jar" "$nb_version/modules/"
            fi
            if [ -f "$TEMP_DIR/netbeans/config/Modules/com-merito-agy-nb-bridge.xml" ]; then
                cp -f "$TEMP_DIR/netbeans/config/Modules/com-merito-agy-nb-bridge.xml" "$nb_version/config/Modules/"
            fi
            echo "  -> Plugin instalado no NetBeans $(basename "$nb_version")!"
        fi
    done
    rm -rf "$TEMP_DIR"
fi

# 4. Validar servidor MCP Python
echo "[4/5] Validando servidor MCP Python..."
python3 -c "import urllib.request, json; print('  -> Python 3 OK')"

# 5. Validar conectividade com o NetBeans (se aberto)
echo "[5/5] Verificando status da ponte no NetBeans (127.0.0.1:8388)..."
if curl -s --max-time 1 http://127.0.0.1:8388/ping > /dev/null 2>&1; then
    echo "  -> NetBeans Bridge ativa e respondendo na porta 8388!"
else
    echo "  -> NetBeans Bridge offline (abra o NetBeans para habilitar a conexão)."
fi

echo "======================================================================"
echo "  SUCESSO: Instalação concluída com êxito!"
echo "  - Plugin .nbm:          $NBM_FILE"
echo "  - Schemas e Instruções: $MCP_DIR"
echo "  - Regra Ativa:          $RULES_DIR/netbeans_bridge.md"
echo "  - Template de Telas:    $DIR/templates/template_preview_swing.html"
echo "======================================================================"
