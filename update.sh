#!/usr/bin/env bash
# ==============================================================================
# Update Antigravity NetBeans Bridge Suite
# Atualiza repositório, instala o plugin no NetBeans e atualiza o Antigravity
# ==============================================================================

set -e

# Cores para o terminal
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${BLUE}======================================================================${NC}"
echo -e "${CYAN}    ATUALIZADOR AUTOMÁTICO: ANTIGRAVITY NETBEANS BRIDGE SUITE${NC}"
echo -e "${BLUE}======================================================================${NC}"

# 1. Localizar ou clonar o repositório
REPO_DIR="$HOME/bridge"

if [ -d "$REPO_DIR/.git" ]; then
    echo -e "${GREEN}[1/4]${NC} Atualizando repositório Git em $REPO_DIR..."
    cd "$REPO_DIR"
    git pull origin main --quiet || {
        echo -e "${YELLOW}Aviso: Não foi possível atualizar via git pull. Continuando com arquivos locais.${NC}"
    }
else
    echo -e "${GREEN}[1/4]${NC} Clonando repositório para $REPO_DIR..."
    git clone https://github.com/djonatansantos706/bridge.git "$REPO_DIR"
    cd "$REPO_DIR"
fi

# 2. Sincronizar Schemas MCP, Instruções e Regras no Antigravity
echo -e "${GREEN}[2/4]${NC} Sincronizando ferramentas MCP e regras com o Antigravity..."
MCP_DIR="$HOME/.gemini/antigravity/mcp/netbeans-bridge"
RULES_DIR="$HOME/.gemini/config/rules"

mkdir -p "$MCP_DIR" "$RULES_DIR"
cp -f "$REPO_DIR"/mcp-schemas/*.json "$MCP_DIR/" 2>/dev/null || true
cp -f "$REPO_DIR"/mcp-schemas/instructions.md "$MCP_DIR/" 2>/dev/null || true
cp -f "$REPO_DIR"/rules/netbeans_bridge.md "$RULES_DIR/" 2>/dev/null || true

# 3. Atualizar o plugin no NetBeans diretamente a partir do .nbm
echo -e "${GREEN}[3/4]${NC} Verificando instalações do Apache NetBeans..."
NBM_FILE="$REPO_DIR/dist/agy-nb-bridge-latest.nbm"

if [ ! -f "$NBM_FILE" ]; then
    echo -e "  -> Baixando pacote mais recente do GitHub Releases..."
    mkdir -p "$REPO_DIR/dist"
    curl -sSL "https://github.com/djonatansantos706/bridge/releases/latest/download/agy-nb-bridge-latest.nbm" -o "$NBM_FILE" 2>/dev/null || true
fi

if [ -f "$NBM_FILE" ]; then
    TEMP_DIR=$(mktemp -d)
    unzip -q -o "$NBM_FILE" -d "$TEMP_DIR" 2>/dev/null || true

    UPDATED_COUNT=0
    # Procura todas as versões do NetBeans no diretório ~/.netbeans/
    if [ -d "$HOME/.netbeans" ]; then
        for nb_version in "$HOME/.netbeans"/*; do
            if [ -d "$nb_version" ] && [[ "$(basename "$nb_version")" =~ ^[0-9]+ ]]; then
                TARGET_MODULES="$nb_version/modules"
                TARGET_CONFIG="$nb_version/config/Modules"

                mkdir -p "$TARGET_MODULES" "$TARGET_CONFIG"

                if [ -f "$TEMP_DIR/netbeans/modules/com-merito-agy-nb-bridge.jar" ]; then
                    cp -f "$TEMP_DIR/netbeans/modules/com-merito-agy-nb-bridge.jar" "$TARGET_MODULES/"
                fi

                if [ -f "$TEMP_DIR/netbeans/config/Modules/com-merito-agy-nb-bridge.xml" ]; then
                    cp -f "$TEMP_DIR/netbeans/config/Modules/com-merito-agy-nb-bridge.xml" "$TARGET_CONFIG/"
                fi

                echo -e "  -> Plugin atualizado com sucesso no NetBeans $(basename "$nb_version")!"
                UPDATED_COUNT=$((UPDATED_COUNT + 1))
            fi
        done
    fi

    rm -rf "$TEMP_DIR"

    if [ $UPDATED_COUNT -eq 0 ]; then
        echo -e "  -> Nenhuma pasta ~/.netbeans/<versao> encontrada ainda."
        echo -e "     Para instalar manualmente no NetBeans: Vá em ${CYAN}Tools > Plugins > Downloaded > Add Plugins${NC}"
        echo -e "     e selecione o arquivo: ${YELLOW}$NBM_FILE${NC}"
    fi
else
    echo -e "${YELLOW}Aviso: Arquivo $NBM_FILE não encontrado.${NC}"
fi

# 4. Verificar status da ponte no NetBeans
echo -e "${GREEN}[4/4]${NC} Testando conectividade com o NetBeans (porta 8388)..."
if curl -s --max-time 1 http://127.0.0.1:8388/ping > /dev/null 2>&1; then
    echo -e "  -> ${GREEN}NetBeans Bridge ativa e respondendo na porta 8388!${NC}"
    echo -e "  -> ${YELLOW}Nota: Se o NetBeans já estava aberto, reinicie-o caso tenha atualizado uma nova versão do plugin.${NC}"
else
    echo -e "  -> ${CYAN}NetBeans offline. O plugin será carregado assim que o NetBeans for iniciado.${NC}"
fi

echo -e "${BLUE}======================================================================${NC}"
echo -e "${GREEN}  ✓ TUDO ATUALIZADO COM SUCESSO!${NC}"
echo -e "  - Repositório:        $REPO_DIR"
echo -e "  - Pacote .nbm pronto: $NBM_FILE"
echo -e "  - Antigravity MCP:    $MCP_DIR"
echo -e "${BLUE}======================================================================${NC}"
