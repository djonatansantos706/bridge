# Antigravity NetBeans Bridge Suite

[![NetBeans](https://img.shields.io/badge/NetBeans-12%20a%2030+-blue.svg)](https://netbeans.apache.org/)
[![Java](https://img.shields.io/badge/Java-11%20%7C%2017%20%7C%2021+-orange.svg)](https://www.oracle.com/java/)
[![MCP](https://img.shields.io/badge/MCP-Protocol%202024--11--05-green.svg)](https://modelcontextprotocol.io/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

Suíte completa de integração bidirecional em tempo real entre o **Apache NetBeans** e assistentes de IA (**Google Antigravity** e **Claude Code**) através do **Model Context Protocol (MCP)** e API HTTP REST de alta performance.

---

## 🚀 Principais Recursos

1. **Edição de Buffers em Memória:**
   - Edita arquivos diretamente no buffer do NetBeans sem salvar no disco (marca com `*` não salvo).
   - Preserva o encoding nativo do projeto (`ISO-8859-1`, `Windows-1252`, `UTF-8`).
   - Mantém o Histórico Local e Undo (`Ctrl+Z`) sob total controle do desenvolvedor.

2. **Depuração JPDA (Java Platform Debugger Architecture):**
   - Criação, remoção e listagem de breakpoints de linha e condicionais (`LineBreakpoint`).
   - Controle de fluxo: `step_into`, `step_over`, `step_out`, `continue`, `pause` e `stop`.
   - Inspeção de frames da pilha (`CallStackFrame`), variáveis locais, `this` e campos de objetos.
   - Avaliação dinâmica de expressões Java em tempo real (*Eval*).

3. **Captura da Aba de Saída / Console:**
   - Mapeamento e listagem de todas as abas ativas do NetBeans (*Run*, *Debug*, *Maven*, *Tomcat*, etc.).
   - Leitura de logs com streaming paginado por offset (`since_line` e `max_lines`).
   - Limpeza e reset de buffers do console.

4. **Diagnósticos e AST em Tempo Real:**
   - Extração de erros de compilação e warnings sintáticos/semânticos instantaneamente via AST sem build em disco (`JavaSource` / `CompilationController`).
   - Outline estruturado de classes, interfaces, métodos, parâmetros, tipos, anotações e imports.

5. **Orquestração de Projetos e Invocador Universal:**
   - Listagem de projetos abertos e detecção do projeto principal (*Main Project*).
   - Ciclo de vida: `build`, `clean`, `clean_and_build`, `run`, `test`, `test_single`, `run_single`, `debug_single` via `ActionProvider`.
   - Disparo de qualquer ação registrada na IDE pelo Action ID (`Actions.forID`).

---

## 📦 Passo a Passo de Instalação no Ubuntu / Linux

### 1. Instalar Pré-requisitos
Abra o terminal no Ubuntu e garanta que os pacotes necessários estão instalados:
```bash
sudo apt update
sudo apt install -y git openjdk-17-jdk maven python3
```

### 2. Clonar o Repositório e Compilar o Plugin
```bash
# Clonar o repositório
git clone https://github.com/djonatansantos706/bridge.git ~/bridge

# Entrar na pasta e compilar o pacote NBM
cd ~/bridge
mvn clean install
```
> O arquivo `.nbm` do plugin será gerado em: `~/bridge/target/nbm/agy-nb-bridge-1.1.0.nbm`

### 3. Instalar o Plugin no Apache NetBeans
1. Abra o **Apache NetBeans**.
2. Acesse o menu **Tools > Plugins** (ou *Ferramentas > Plugins*).
3. Vá até a aba **Downloaded** (ou *Baixados*) e clique em **Add Plugins...** (ou *Adicionar Plugins...*).
4. Selecione o arquivo gerado:
   `~/bridge/target/nbm/agy-nb-bridge-1.1.0.nbm`
5. Clique em **Install**, avance e conclua a instalação.
6. A mensagem `[Antigravity] Bridge Suite ativa na porta 8388` será exibida no rodapé do NetBeans.

---

## 🤖 Como Configurar nos Assistentes de IA

### Opção A: Configuração no Claude Code

#### Método 1 (Recomendado - Linha de Comando):
Execute no terminal da máquina do desenvolvedor:
```bash
claude mcp add netbeans-bridge python3 "$HOME/bridge/netbeans-mcp-server.py"
```

#### Método 2 (Via arquivo de configuração):
Adicione no arquivo `~/.claude.json` (ou `.claude/config.json`):
```json
{
  "mcpServers": {
    "netbeans-bridge": {
      "command": "python3",
      "args": ["/home/SEU_USUARIO/bridge/netbeans-mcp-server.py"]
    }
  }
}
```

---

### Opção B: Configuração no Google Antigravity

#### 1. Registrar o Servidor MCP no Antigravity:
Adicione a configuração no arquivo `~/.gemini/antigravity/mcp_config.json` (ou `~/.gemini/config/mcp.json`):
```json
{
  "mcpServers": {
    "netbeans-bridge": {
      "command": "python3",
      "args": ["/home/SEU_USUARIO/bridge/netbeans-mcp-server.py"]
    }
  }
}
```

#### 2. Copiar os Schemas JSON de Ferramentas:
Para permitir a descoberta lazy/eager de ferramentas no Antigravity:
```bash
mkdir -p ~/.gemini/antigravity/mcp/netbeans-bridge
cp ~/bridge/mcp-schemas/*.json ~/.gemini/antigravity/mcp/netbeans-bridge/
```

#### 3. Regra de Edição Recomendada (Antigravity Rule):
Adicione ao seu arquivo de regras do Antigravity (`~/.gemini/antigravity/rules` ou diretório de regras do projeto):
```markdown
# Regra de Edição via NetBeans Bridge
Sempre que realizar modificações em arquivos de código Java/JPosto:
1. Utilize SEMPRE a ponte do NetBeans (ferramentas nb_edit_buffer / nb_replace_lines / nb_set_content).
2. Motivo: Preserva o encoding nativo (ISO-8859-1 / Windows-1252), marca com '*' não salvo e mantém o histórico local / Ctrl+Z sob controle do desenvolvedor.
```

---

## 🧪 Testes e Validação

Execute a suíte de testes de integridade e encoding:
```bash
python3 ~/bridge/qa_test.py
```

Com o NetBeans aberto, todos os testes devem retornar `[APROVADO]`:
```text
======================================================================
    INICIANDO QA SUITE: ANTIGRAVITY NETBEANS BRIDGE SUITE 1.1.0
======================================================================
test_bridge_connectivity_if_running ... ok
test_latin1_encoding_preservation ... ok
test_mcp_schemas_completeness ... ok
test_plugin_nbm_artifacts ... ok

----------------------------------------------------------------------
Ran 4 tests in 0.004s

OK
======================================================================
    STATUS: [APROVADO] Todos os testes passaram com sucesso!
======================================================================
```

---

## 🛠️ Catálogo de Ferramentas MCP (24 Ferramentas)

| Ferramenta | Descrição |
| :--- | :--- |
| `nb_status` | Status da bridge, versão e projetos abertos |
| `nb_open_file` | Abre arquivo na linha informada no editor |
| `nb_get_buffer` | Lê buffer em memória de um arquivo |
| `nb_edit_buffer` | Substituição de texto exato no buffer |
| `nb_replace_lines` | Substituição em intervalo específico de linhas |
| `nb_set_content` | Substituição total do buffer em memória |
| `nb_open_commit` | Abre a tela nativa de commit (Git ou Subversion) |
| `nb_debug_status` | Estado da sessão de depuração JPDA |
| `nb_debug_set_breakpoint` | Cria breakpoint de linha e condição |
| `nb_debug_remove_breakpoint` | Remove breakpoint por ID ou linha |
| `nb_debug_list_breakpoints` | Lista todos os breakpoints ativos na IDE |
| `nb_debug_control` | Controla fluxo (`step_into`, `step_over`, `step_out`, `continue`, `pause`, `stop`) |
| `nb_debug_get_stack` | Pilha de chamadas e frames da thread |
| `nb_debug_get_variables` | Inspeciona variáveis locais e campos do `this` |
| `nb_debug_evaluate` | Avalia expressão Java em tempo de execução (*Eval*) |
| `nb_output_list_tabs` | Lista abas da janela de saída |
| `nb_output_get_text` | Lê logs com paginação incremental |
| `nb_output_clear` | Limpa buffer de uma aba de saída |
| `nb_diagnostics_get` | Erros e warnings de compilação da AST |
| `nb_ast_get_structure` | Outline estruturado de classes, métodos e campos |
| `nb_project_list` | Lista projetos abertos e projeto principal |
| `nb_project_open` | Abre diretório como projeto no NetBeans |
| `nb_project_action` | Executa ação de build/run/test no projeto |
| `nb_invoke_action` | Invoca ação global pelo Action ID |

---

## 📄 Licença

Distribuído sob a licença **Apache 2.0**.
