# 📘 Guia Definitivo: Instalação da Bridge e Mint

Este guia passo a passo foi elaborado para você e sua equipe instalarem e configurarem a **NetBeans Bridge Suite** e o **Mint (Miti)** tanto no **Google Antigravity** quanto no **Claude Code**.

---

## 🧭 Visão Geral da Arquitetura

```text
 Assistentes de IA:       [ Google Antigravity ]          [ Claude Code ]
                                     │                            │
                     ┌───────────────┴────────────┬───────────────┘
                     │                            │
                     ▼                            ▼
           [ NetBeans Bridge ]               [ Mint (Miti) ]
          (Servidor MCP Python)           (Servidor MCP Python)
                     │                            │
                     │ (Porta 8388)               │ (Gera código & chama Bridge)
                     ▼                            ▼
         ┌─────────────────────────────────────────────────┐
         │     Apache NetBeans (IDE com Plugin .nbm)       │
         │   - Edição de buffers em memória (Windows-1252) │
         │   - Histórico local / Undo (Ctrl+Z) mantido     │
         │   - Telas Swing MVP & CRUDs JBase               │
         └─────────────────────────────────────────────────┘
```

---

# 🤖 PARTE 1: Configuração no Google Antigravity

### 1.1. Instalar a NetBeans Bridge no Antigravity
Abra o terminal no Ubuntu / Linux e execute:

```bash
# 1. Clonar o repositório e executar o setup automático
git clone https://github.com/djonatansantos706/bridge.git ~/bridge
~/bridge/setup_antigravity.sh
```

**O que o setup faz sozinho:**
- Instala o plugin `.nbm` pré-compilado diretamente nas pastas do seu Apache NetBeans (`~/.netbeans/*/modules/`).
- Copia todos os schemas das 40 ferramentas e as instruções mestres para `~/.gemini/antigravity/mcp/netbeans-bridge/`.
- Instala a regra permanente de edição segura em memória e preview de telas no seu ambiente global.

> **Importante:** Se o NetBeans já estiver aberto, feche e abra-o novamente para carregar o plugin. A mensagem `[Antigravity] Bridge Suite ativa na porta 8388` aparecerá no rodapé da IDE.

---

### 1.2. Instalar o Mint (Miti) no Antigravity
Execute no terminal:

```bash
# 1. Clonar o repositório e executar o setup automático
git clone https://github.com/djonatansantos706/mint.git ~/mint
~/mint/setup_antigravity.sh
```

**O que o setup faz sozinho:**
- Registra o servidor MCP `mint` no seu arquivo `~/.gemini/config/mcp_config.json`.
- Copia os schemas das ferramentas (`mint_status`, `mint_parse_ods`, `mint_gerar_crud`, `mint_gerar_tela`, etc.) e diretrizes mestres para `~/.gemini/antigravity/mcp/mint/`.
- Valida as dependências Python e testa a comunicação com a NetBeans Bridge.

---

### 1.3. Como Atualizar no dia a dia (Antigravity):
Sempre que houver atualizações nos repositórios, basta rodar 1 comando em cada pasta:

```bash
# Atualizar a Bridge (código, schemas e plugin no NetBeans):
cd ~/bridge && ./update.sh

# Atualizar o Mint (código, schemas e testes):
cd ~/mint && ./update.sh
```

---

# 🟣 PARTE 2: Configuração no Claude Code

No Claude Code, você adiciona os servidores MCP diretamente pela CLI através do parâmetro `--scope user` (para ficar disponível globalmente em qualquer pasta).

### 2.1. Pré-requisito: Clonar os Repositórios
Se ainda não clonou os repositórios na máquina, execute:

```bash
git clone https://github.com/djonatansantos706/bridge.git ~/bridge
git clone https://github.com/djonatansantos706/mint.git ~/mint
```

> **Plugin no NetBeans:** Garanta que o plugin da Bridge está instalado no NetBeans rodando:  
> `~/bridge/setup_antigravity.sh` ou instalando o arquivo `~/bridge/dist/agy-nb-bridge-latest.nbm` pelo menu *Tools > Plugins > Downloaded*.

---

### 2.2. Registrar o MCP da Bridge no Claude Code
Execute no terminal:

```bash
claude mcp add --scope user netbeans-bridge python3 "$HOME/bridge/netbeans-mcp-server.py"
```

---

### 2.3. Registrar o MCP do Mint no Claude Code
Execute no terminal:

```bash
claude mcp add --scope user mint python3 "$HOME/mint/mint-mcp-server.py"
```

---

### 2.4. Validar a Conexão no Claude Code
Execute:

```bash
claude mcp list
```

A saída esperada deve listar os dois servidores com status conectado:
```text
Checking MCP server health…

netbeans-bridge: python3 /home/merito/bridge/netbeans-mcp-server.py (stdio) - ✔ Connected
mint: python3 /home/merito/mint/mint-mcp-server.py (stdio) - ✔ Connected
```

---

### 2.5. Regra Recomendada para o Claude Code (`CLAUDE.md`)
Crie ou adicione ao arquivo `~/.claude/CLAUDE.md` (ou no `CLAUDE.md` da raiz dos seus projetos Java):

```markdown
# Diretrizes de Operação - NetBeans Bridge e Mint

## 1. Edição em Memória (Obrigatória)
- Sempre utilize as ferramentas do `netbeans-bridge` (`nb_edit_buffer`, `nb_replace_lines`, `nb_set_content`) para editar arquivos de código Java.
- Não sobrescreva arquivos Java diretamente no disco para preservar o encoding nativo (Windows-1252 / ISO-8859-1) e o histórico local / Ctrl+Z na IDE.

## 2. Geração de Código com o Mint
- Utilize as ferramentas do `mint` (`mint_parse_ods`, `mint_gerar_crud`, `mint_gerar_tela`) para criar entidades, DAOs, Presenters e formulários Swing.
- Antes de gerar telas Swing (`mint_gerar_tela`), apresente a proposta visual da tela ao desenvolvedor.
```

---

# 🧪 PARTE 3: Teste Rápido de Verificação

Para ter 100% de certeza de que tudo está operando perfeitamente:

1. **Abra o Apache NetBeans.**
2. **Abra uma sessão no seu assistente (Antigravity ou Claude Code).**
3. **Envie a mensagem de teste:**
   > *"Verifique o status da NetBeans Bridge e do Mint."*
4. O assistente executará `nb_status` e `mint_status`:
   - A Bridge responderá: `{"ok": true, "version": "1.4.5", "service": "antigravity-netbeans-bridge"}`
   - O Mint responderá: `{"ok": true, "motor": "pronto", "bridge_status": "conectado"}`

---

# 📋 Resumo dos Comandos Úteis

| Ação | Comando |
| :--- | :--- |
| **Instalar Bridge (Antigravity)** | `git clone https://github.com/djonatansantos706/bridge.git ~/bridge && ~/bridge/setup_antigravity.sh` |
| **Instalar Mint (Antigravity)** | `git clone https://github.com/djonatansantos706/mint.git ~/mint && ~/mint/setup_antigravity.sh` |
| **Atualizar Bridge** | `cd ~/bridge && ./update.sh` |
| **Atualizar Mint** | `cd ~/mint && ./update.sh` |
| **Adicionar Bridge no Claude Code** | `claude mcp add --scope user netbeans-bridge python3 "$HOME/bridge/netbeans-mcp-server.py"` |
| **Adicionar Mint no Claude Code** | `claude mcp add --scope user mint python3 "$HOME/mint/mint-mcp-server.py"` |
| **Listar MCPs no Claude Code** | `claude mcp list` |
