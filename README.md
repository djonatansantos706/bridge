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
> O arquivo `.nbm` do plugin será gerado em: `~/bridge/target/nbm/agy-nb-bridge-1.2.0.nbm`

### 3. Instalar o Plugin no Apache NetBeans
1. Abra o **Apache NetBeans**.
2. Acesse o menu **Tools > Plugins** (ou *Ferramentas > Plugins*).
3. Vá até a aba **Downloaded** (ou *Baixados*) e clique em **Add Plugins...** (ou *Adicionar Plugins...*).
4. Selecione o arquivo gerado:
   `~/bridge/target/nbm/agy-nb-bridge-1.2.0.nbm`
5. Clique em **Install**, avance e conclua a instalação.
6. A mensagem `[Antigravity] Bridge Suite ativa na porta 8388` será exibida no rodapé do NetBeans.

---

## 🔐 Autenticação (automática)

A bridge só aceita requisições autenticadas. Na primeira inicialização, o plugin gera um token aleatório e o grava em:

```text
~/.config/agy-nb-bridge/token   (permissão 0600 — somente o seu usuário lê)
```

Os clientes oficiais (`netbeans-mcp-server.py` e `agy_nb_client.py`) leem esse arquivo automaticamente e enviam o token no header `X-Bridge-Token` — **nenhuma configuração manual é necessária**. Requisições sem o token recebem `401`, e o servidor não envia headers CORS: isso impede que páginas web abertas no navegador acionem a porta 8388 (a porta já era restrita a `127.0.0.1`; o token fecha o vetor de scripts locais/navegador). Apenas o endpoint `/ping` fica aberto, para diagnóstico de conectividade.

Para integrar um cliente próprio, basta enviar o conteúdo do arquivo no header:

```bash
curl -s -X POST http://127.0.0.1:8388/status \
  -H "X-Bridge-Token: $(cat ~/.config/agy-nb-bridge/token)"
```

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
    INICIANDO QA SUITE: ANTIGRAVITY NETBEANS BRIDGE SUITE v1.2.0
======================================================================
test_bridge_connectivity_if_running ... ok
test_latin1_encoding_preservation ... ok
test_mcp_schemas_completeness ... ok
test_plugin_nbm_artifacts ... ok

----------------------------------------------------------------------
Ran 4 tests in 0.012s

OK
======================================================================
    STATUS: [APROVADO] Todos os testes passaram com sucesso!
======================================================================
```

---

## 🛠️ Catálogo de Ferramentas MCP (35 Ferramentas - Versão 1.2.0)

### 📝 Edição e Gerenciamento de Buffers (11 Ferramentas)

| Ferramenta | Descrição |
| :--- | :--- |
| `nb_status` | Verifica se a Antigravity Bridge Suite está ativa no NetBeans e respondendo. |
| `nb_open_file` | Abre um arquivo no editor do NetBeans em uma linha específica. |
| `nb_get_buffer` | Lê o conteúdo atual em memória (buffer) de um arquivo aberto no NetBeans. |
| `nb_edit_buffer` | Substitui texto exato no buffer do NetBeans sem salvar no disco (marca a aba com * e preserva histórico/Ctrl+Z). |
| `nb_replace_lines` | Substitui um trecho de código em um intervalo de linhas no buffer do NetBeans. |
| `nb_set_content` | Substitui todo o conteúdo do buffer do arquivo no NetBeans de forma atômica. |
| `nb_save_buffer` | Persiste as alterações do buffer de um arquivo no disco (salva o documento no NetBeans). |
| `nb_revert_buffer` | Descarta todas as alterações em memória no NetBeans e recarrega o buffer a partir do disco. |
| `nb_format_code` | Formata e reindenta o arquivo ou trecho de linhas no padrão de código nativo do NetBeans. |
| `nb_get_selection` | Obtém a posição do cursor (linha, coluna) e o texto atualmente selecionado no editor do NetBeans. |
| `nb_set_selection` | Define a seleção e posiciona o cursor no editor do NetBeans. |

### 🐞 Depuração JPDA Avançada (12 Ferramentas)

| Ferramenta | Descrição |
| :--- | :--- |
| `nb_debug_status` | Consulta o estado da sessão de depuração JPDA (RUNNING, STOPPED, STARTING, INACTIVE). |
| `nb_debug_set_breakpoint` | Insere um breakpoint de linha no NetBeans, com suporte a condição lógica opcional. |
| `nb_debug_remove_breakpoint` | Remove um breakpoint no NetBeans por ID ou por arquivo e linha. |
| `nb_debug_list_breakpoints` | Lista todos os breakpoints ativos na IDE NetBeans. |
| `nb_debug_control` | Controla a execução da depuração (step_into, step_over, step_out, continue, pause, stop). |
| `nb_debug_get_stack` | Retorna a pilha de chamadas (call stack) e frames da thread atualmente suspensa no debugger JPDA. |
| `nb_debug_get_variables` | Inspeciona variáveis locais, atributos do 'this', coleções (List, Map, Set) e arrays no frame da pilha. |
| `nb_debug_evaluate` | Avalia uma expressão Java em tempo de execução no contexto da JVM pausada no debugger (Eval). |
| `nb_debug_add_watch` | Adiciona uma expressão monitorada na aba Watches do NetBeans. |
| `nb_debug_list_watches` | Lista todas as expressões monitoradas na aba Watches com valores e tipos avaliados. |
| `nb_debug_remove_watch` | Remove uma expressão monitorada da aba Watches por ID, expressão ou 'all'. |
| `nb_debug_get_last_exception` | Captura informações detalhadas da última exceção/erro que interrompeu a execução no debugger JPDA. |

### 🔍 Diagnósticos, AST e Navegação Semântica (4 Ferramentas)

| Ferramenta | Descrição |
| :--- | :--- |
| `nb_diagnostics_get` | Obtém instantaneamente erros e warnings de compilação de um arquivo Java a partir da AST do NetBeans sem build em disco. |
| `nb_ast_get_structure` | Retorna o outline estruturado da AST (classes, interfaces, métodos, parâmetros, campos, anotações e imports) do arquivo Java. |
| `nb_goto_definition` | Navega semanticamente para a definição de uma classe, método ou símbolo a partir do arquivo e linha. |
| `nb_find_usages` | Localiza todas as ocorrências e referências de um símbolo no projeto NetBeans. |

### 📟 Console de Saída e Logs (3 Ferramentas)

| Ferramenta | Descrição |
| :--- | :--- |
| `nb_output_list_tabs` | Lista todas as abas abertas no console de saída do NetBeans (Run, Debug, Maven, etc.). |
| `nb_output_get_text` | Lê linhas de uma aba de saída do NetBeans com suporte a filtros Regex ou texto. |
| `nb_output_clear` | Limpa as linhas de uma aba de saída no NetBeans. |

### 🏗️ Gerenciamento de Projetos e Ações IDE (5 Ferramentas)

| Ferramenta | Descrição |
| :--- | :--- |
| `nb_project_list` | Lista todos os projetos abertos no NetBeans e indica qual é o projeto principal. |
| `nb_project_open` | Abre um diretório como projeto no NetBeans. |
| `nb_project_action` | Dispara uma ação de projeto (build, clean, clean_and_build, run, test, test_single, run_single, debug_single) no NetBeans. |
| `nb_invoke_action` | Invoca qualquer ação nativa ou global registrada no NetBeans a partir do Action ID (ex: 'org-netbeans-core-actions-SaveAllAction'). |
| `nb_open_commit` | Abre o diálogo nativo de commit do NetBeans (Git ou Subversion) com arquivos pré-selecionados. |

---

## 📄 Licença

Distribuído sob a licença **Apache 2.0**.
