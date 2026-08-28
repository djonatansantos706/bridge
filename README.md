# Antigravity NetBeans Bridge Suite

[![NetBeans](https://img.shields.io/badge/NetBeans-12%20a%2030+-blue.svg)](https://netbeans.apache.org/)
[![Java](https://img.shields.io/badge/Java-11%20%7C%2017%20%7C%2021+-orange.svg)](https://www.oracle.com/java/)
[![MCP](https://img.shields.io/badge/MCP-Protocol%202024--11--05-green.svg)](https://modelcontextprotocol.io/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

Suíte completa de integração bidirecional em tempo real entre o **Apache NetBeans** e assistentes de IA (como **Google Antigravity** e **Claude Code**) através do **Model Context Protocol (MCP)** e API HTTP REST de alta performance.

---

## Principais Recursos

### 1. Edição de Buffers em Memória
- **Edição sem tocar no disco:** Modifica o arquivo diretamente no buffer do NetBeans, marcando a aba com `*` (não salvo).
- **Preservação de Encoding:** Mantém o encoding nativo do projeto (ex: `ISO-8859-1`, `Windows-1252` e `UTF-8`) sem corrupção de caracteres especiais.
- **Histórico Local e Undo:** Preserva 100% o histórico local do NetBeans e controle total de `Ctrl+Z` para o desenvolvedor.

### 2. Depuração JPDA (Java Platform Debugger Architecture)
- **Gerenciamento de Breakpoints:** Criação, remoção e listagem de breakpoints de linha e condicionais (`LineBreakpoint`).
- **Controle de Execução:** Disparo de `step_into`, `step_over`, `step_out`, `continue`, `pause` e `stop`.
- **Inspeção de Memória:** Leitura de frames da pilha (`CallStackFrame`), variáveis locais, atributos do `this`, campos de objetos e elementos de listas/arrays.
- **Avaliação de Expressões (*Eval*):** Execução dinâmica de código Java em tempo de execução no frame pausado.

### 3. Captura da Aba de Saída / Console
- **Mapeamento de Abas:** Identifica todas as abas ativas do NetBeans Output (*Run*, *Debug*, *Maven*, *Tomcat*, etc.).
- **Streaming Incremental:** Leitura de linhas de log a partir de offsets (`since_line` e `max_lines`).
- **Limpeza de Console:** Reset do buffer de saída sob demanda.

### 4. Diagnósticos e AST em Tempo Real
- **Diagnósticos Instantâneos:** Consulta erros de compilação e warnings sintáticos/semânticos direto da AST do NetBeans sem build no disco (`JavaSource` / `CompilationController`).
- **Outline da Estrutura Java:** Extração de classes, interfaces, métodos, parâmetros, tipos de retorno, anotações e dependências de imports.

### 5. Orquestração de Projetos e Invocador Global
- **Inventário de Projetos:** Listagem de projetos abertos e detecção do projeto principal (*Main Project*).
- **Ciclo de Vida de Projetos:** Execução de `build`, `clean`, `clean_and_build`, `run`, `test`, `test_single`, `run_single`, `debug_single` via `ActionProvider`.
- **Invocador Universal de Ações:** Acionamento de qualquer ação registrada na IDE pelo Action ID (`Actions.forID`).

---

## Arquitetura

```mermaid
flowchart TD
    subgraph IA ["Assistentes de IA"]
        AGY["Google Antigravity"]
        CLAUDE["Claude Code"]
    end

    subgraph MCP ["Camada MCP (Model Context Protocol)"]
        MCPSRV["netbeans-mcp-server.py<br/>(24 ferramentas JSON-RPC via stdio)"]
    end

    subgraph IDE ["Apache NetBeans JVM"]
        HTTP["AgyBridgeServer (Porta 8388)"]
        
        subgraph Modulos ["Serviços Internos"]
            EDIT["NbEditorService & NbCommitService"]
            JPDA["NbDebugService (JPDA Debugger)"]
            OUT["NbOutputService (Output2 / IOProvider)"]
            AST["NbDiagnosticsService (JavaSource / AST)"]
            PROJ["NbProjectService (ProjectAPI / ActionProvider)"]
        end

        HTTP <--> EDIT
        HTTP <--> JPDA
        HTTP <--> OUT
        HTTP <--> AST
        HTTP <--> PROJ
    end

    AGY <-->|MCP stdio| MCPSRV
    CLAUDE <-->|MCP stdio| MCPSRV
    MCPSRV <-->|HTTP REST JSON (127.0.0.1:8388)| HTTP
```

---

## Como Construir e Instalar

### 1. Pré-requisitos
- JDK 11 ou superior (Java 11, 17, 21+)
- Apache Maven 3.6+
- Apache NetBeans 12, 18, 19, 20, 21, 22 ou superior

### 2. Compilação
```bash
# Compilar e gerar o pacote NBM
mvn clean install
```
O artefato `.nbm` será gerado em:
`target/nbm/agy-nb-bridge-1.1.0.nbm`

### 3. Instalação no NetBeans
1. No NetBeans, acesse **Tools > Plugins** (Ferramentas > Plugins).
2. Vá até a aba **Downloaded** (Baixados) e clique em **Add Plugins...** (Adicionar Plugins...).
3. Selecione o arquivo `target/nbm/agy-nb-bridge-1.1.0.nbm`.
4. Clique em **Install** e conclua o assistente.
5. A bridge será iniciada automaticamente na porta `8388`.

---

## Configuração com Assistentes de IA

### Google Antigravity
Adicione as ferramentas no seu catálogo MCP apontando para o script:
```json
{
  "mcpServers": {
    "netbeans-bridge": {
      "command": "python3",
      "args": ["/caminho/para/netbeans-mcp-server.py"]
    }
  }
}
```

### Claude Code
Basta executar:
```bash
claude mcp add netbeans-bridge python3 /caminho/para/netbeans-mcp-server.py
```

Ou no arquivo de configuração (`~/.claude.json`):
```json
{
  "mcpServers": {
    "netbeans-bridge": {
      "command": "python3",
      "args": ["/caminho/para/netbeans-mcp-server.py"]
    }
  }
}
```

---

## Catálogo de Ferramentas MCP (24 Ferramentas)

| Ferramenta | Descrição |
| :--- | :--- |
| `nb_status` | Status da bridge, versão e projetos abertos |
| `nb_open_file` | Abre arquivo na linha informada |
| `nb_get_buffer` | Lê buffer em memória |
| `nb_edit_buffer` | Substituição de texto exato no buffer |
| `nb_replace_lines` | Substituição em intervalo de linhas |
| `nb_set_content` | Substituição total do buffer |
| `nb_open_commit` | Abre tela nativa de commit (Git/SVN) |
| `nb_debug_status` | Estado da sessão de depuração JPDA |
| `nb_debug_set_breakpoint` | Cria breakpoint de linha e condição |
| `nb_debug_remove_breakpoint` | Remove breakpoint por ID ou linha |
| `nb_debug_list_breakpoints` | Lista breakpoints ativos |
| `nb_debug_control` | Controla fluxo (step_into, step_over, etc.) |
| `nb_debug_get_stack` | Pilha de chamadas e frames da thread |
| `nb_debug_get_variables` | Inspeciona variáveis locais e campos do `this` |
| `nb_debug_evaluate` | Avalia expressão Java no contexto do frame (*Eval*) |
| `nb_output_list_tabs` | Lista abas da janela de saída |
| `nb_output_get_text` | Lê logs com paginação incremental |
| `nb_output_clear` | Limpa buffer de uma aba de saída |
| `nb_diagnostics_get` | Erros e warnings de compilação da AST |
| `nb_ast_get_structure` | Outline estruturado de classes e métodos |
| `nb_project_list` | Lista projetos abertos e projeto principal |
| `nb_project_open` | Abre diretório como projeto |
| `nb_project_action` | Executa ação de build/run/test de projeto |
| `nb_invoke_action` | Invoca ação global pelo Action ID |

---

## Testes e Validação de QA

O repositório inclui a suíte de validação automatizada:
```bash
python3 qa_test.py
```

---

## Licença

Distribuído sob a licença **Apache 2.0**.
