#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Antigravity NetBeans Bridge Suite v1.2.0 - MCP Server (Stdio)
Expõe ferramentas para interação in-memory, depuração JPDA avançada, logs de saída,
diagnósticos AST, navegação semântica e controle de projetos no Apache NetBeans via MCP.
"""

import sys
import json
import urllib.request
import urllib.error

BRIDGE_URL = "http://127.0.0.1:8388"

TOOLS = [
    # --- Status & Core ---
    {
        "name": "nb_status",
        "description": "Verifica se a Antigravity Bridge Suite está ativa no NetBeans e respondendo.",
        "inputSchema": {
            "type": "object",
            "properties": {}
        }
    },

    # --- Editor & Document Buffer Manipulation ---
    {
        "name": "nb_open_file",
        "description": "Abre um arquivo no editor do NetBeans em uma linha específica.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "file": {"type": "string", "description": "Caminho absoluto do arquivo a ser aberto"},
                "line": {"type": "integer", "description": "Número da linha (opcional, padrão 1)"}
            },
            "required": ["file"]
        }
    },
    {
        "name": "nb_get_buffer",
        "description": "Lê o conteúdo atual em memória (buffer) de um arquivo aberto no NetBeans.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "file": {"type": "string", "description": "Caminho absoluto do arquivo"}
            },
            "required": ["file"]
        }
    },
    {
        "name": "nb_edit_buffer",
        "description": "Substitui texto exato no buffer do NetBeans sem salvar no disco (marca a aba com * e preserva histórico/Ctrl+Z).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "file": {"type": "string", "description": "Caminho absoluto do arquivo"},
                "old_text": {"type": "string", "description": "Texto exato a ser substituído"},
                "new_text": {"type": "string", "description": "Novo texto"},
                "allow_multiple": {"type": "boolean", "description": "Permite substituir múltiplas ocorrências (padrão false)"}
            },
            "required": ["file", "old_text", "new_text"]
        }
    },
    {
        "name": "nb_replace_lines",
        "description": "Substitui um trecho de código em um intervalo de linhas no buffer do NetBeans.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "file": {"type": "string", "description": "Caminho absoluto do arquivo"},
                "start_line": {"type": "integer", "description": "Linha inicial (1-indexada)"},
                "end_line": {"type": "integer", "description": "Linha final (1-indexada)"},
                "target_content": {"type": "string", "description": "Texto exato existente a ser substituído"},
                "replacement_content": {"type": "string", "description": "Conteúdo de substituição"}
            },
            "required": ["file", "start_line", "end_line", "target_content", "replacement_content"]
        }
    },
    {
        "name": "nb_set_content",
        "description": "Substitui todo o conteúdo do buffer do arquivo no NetBeans de forma atômica.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "file": {"type": "string", "description": "Caminho absoluto do arquivo"},
                "content": {"type": "string", "description": "Novo conteúdo completo do arquivo"}
            },
            "required": ["file", "content"]
        }
    },
    {
        "name": "nb_save_buffer",
        "description": "Persiste as alterações do buffer de um arquivo no disco (salva o documento no NetBeans).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "file": {"type": "string", "description": "Caminho absoluto do arquivo"}
            },
            "required": ["file"]
        }
    },
    {
        "name": "nb_revert_buffer",
        "description": "Descarta todas as alterações em memória no NetBeans e recarrega o buffer a partir do disco.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "file": {"type": "string", "description": "Caminho absoluto do arquivo"}
            },
            "required": ["file"]
        }
    },
    {
        "name": "nb_format_code",
        "description": "Formata e reindenta o arquivo ou trecho de linhas no padrão de código nativo do NetBeans.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "file": {"type": "string", "description": "Caminho absoluto do arquivo"},
                "start_line": {"type": "integer", "description": "Linha inicial (opcional, padrão formata arquivo inteiro)"},
                "end_line": {"type": "integer", "description": "Linha final (opcional)"}
            },
            "required": ["file"]
        }
    },
    {
        "name": "nb_get_selection",
        "description": "Obtém a posição do cursor (linha, coluna) e o texto atualmente selecionado no editor do NetBeans.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "file": {"type": "string", "description": "Caminho absoluto do arquivo aberto no editor"}
            },
            "required": ["file"]
        }
    },
    {
        "name": "nb_set_selection",
        "description": "Define a seleção e posiciona o cursor no editor do NetBeans.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "file": {"type": "string", "description": "Caminho absoluto do arquivo"},
                "start_line": {"type": "integer", "description": "Linha de início da seleção (1-indexada)"},
                "start_column": {"type": "integer", "description": "Coluna de início da seleção (1-indexada)"},
                "end_line": {"type": "integer", "description": "Linha de término da seleção"},
                "end_column": {"type": "integer", "description": "Coluna de término da seleção"}
            },
            "required": ["file", "start_line", "start_column", "end_line", "end_column"]
        }
    },
    {
        "name": "nb_open_commit",
        "description": "Abre o diálogo nativo de commit do NetBeans (Git ou Subversion) com arquivos pré-selecionados.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "files": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "Lista de caminhos absolutos dos arquivos a incluir no commit"
                },
                "message": {"type": "string", "description": "Mensagem prévia de commit (opcional)"}
            },
            "required": ["files"]
        }
    },

    # --- JPDA Debugger Tools ---
    {
        "name": "nb_debug_status",
        "description": "Consulta o estado da sessão de depuração JPDA (RUNNING, STOPPED, STARTING, INACTIVE).",
        "inputSchema": {
            "type": "object",
            "properties": {}
        }
    },
    {
        "name": "nb_debug_set_breakpoint",
        "description": "Insere um breakpoint de linha no NetBeans, com suporte a condição lógica opcional.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "file": {"type": "string", "description": "Caminho absoluto do arquivo Java"},
                "line": {"type": "integer", "description": "Número da linha para o breakpoint"},
                "condition": {"type": "string", "description": "Condição em Java para disparo (opcional)"}
            },
            "required": ["file", "line"]
        }
    },
    {
        "name": "nb_debug_remove_breakpoint",
        "description": "Remove um breakpoint no NetBeans por ID ou por arquivo e linha.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "id": {"type": "string", "description": "ID do breakpoint a remover"},
                "file": {"type": "string", "description": "Caminho absoluto do arquivo (se não informar ID)"},
                "line": {"type": "integer", "description": "Número da linha (se não informar ID)"}
            }
        }
    },
    {
        "name": "nb_debug_list_breakpoints",
        "description": "Lista todos os breakpoints ativos na IDE NetBeans.",
        "inputSchema": {
            "type": "object",
            "properties": {}
        }
    },
    {
        "name": "nb_debug_control",
        "description": "Controla a execução da depuração (step_into, step_over, step_out, continue, pause, stop).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "description": "Ação: 'step_into', 'step_over', 'step_out', 'continue', 'pause', 'stop'"
                }
            },
            "required": ["action"]
        }
    },
    {
        "name": "nb_debug_get_stack",
        "description": "Retorna a pilha de chamadas (call stack) e frames da thread atualmente suspensa no debugger JPDA.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "thread": {"type": "string", "description": "Nome da thread (opcional, padrão thread atual suspensa)"}
            }
        }
    },
    {
        "name": "nb_debug_get_variables",
        "description": "Inspeciona variáveis locais, atributos do 'this', coleções (List, Map, Set) e arrays no frame da pilha.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "frame": {"type": "integer", "description": "Índice do frame na pilha (0 = topo/atual, padrão 0)"},
                "depth": {"type": "integer", "description": "Profundidade de inspeção de campos (padrão 2)"}
            }
        }
    },
    {
        "name": "nb_debug_evaluate",
        "description": "Avalia uma expressão Java em tempo de execução no contexto da JVM pausada no debugger.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "expression": {"type": "string", "description": "Expressão Java a ser avaliada"},
                "frame": {"type": "integer", "description": "Índice do frame na pilha (opcional)"}
            },
            "required": ["expression"]
        }
    },
    {
        "name": "nb_debug_add_watch",
        "description": "Adiciona uma expressão monitorada na aba Watches do NetBeans.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "expression": {"type": "string", "description": "Expressão Java a ser monitorada"}
            },
            "required": ["expression"]
        }
    },
    {
        "name": "nb_debug_list_watches",
        "description": "Lista todas as expressões monitoradas na aba Watches com valores e tipos avaliados.",
        "inputSchema": {
            "type": "object",
            "properties": {}
        }
    },
    {
        "name": "nb_debug_remove_watch",
        "description": "Remove uma expressão monitorada da aba Watches por ID, expressão ou 'all'.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "id": {"type": "string", "description": "ID ou expressão da watch a remover ('all' para todas)"}
            },
            "required": ["id"]
        }
    },
    {
        "name": "nb_debug_get_last_exception",
        "description": "Captura informações detalhadas da última exceção/erro que interrompeu a execução no debugger JPDA.",
        "inputSchema": {
            "type": "object",
            "properties": {}
        }
    },

    # --- Output Console Tools ---
    {
        "name": "nb_output_list_tabs",
        "description": "Lista todas as abas abertas no console de saída do NetBeans (Run, Debug, Maven, etc.).",
        "inputSchema": {
            "type": "object",
            "properties": {}
        }
    },
    {
        "name": "nb_output_get_text",
        "description": "Lê linhas de uma aba de saída do NetBeans com suporte a filtros Regex ou texto.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "tab": {"type": "string", "description": "Nome da aba de saída (opcional, padrão aba ativa)"},
                "since_line": {"type": "integer", "description": "Offset da linha inicial a partir da qual ler (0-indexado, padrão 0)", "default": 0},
                "max_lines": {"type": "integer", "description": "Quantidade máxima de linhas a retornar (padrão 500)", "default": 500},
                "filter": {"type": "string", "description": "Expressão regular ou texto para filtrar linhas relevantes (ex: 'ERROR|Exception')"},
                "case_sensitive": {"type": "boolean", "description": "Diferenciar maiúsculas/minúsculas no filtro (padrão false)"}
            }
        }
    },
    {
        "name": "nb_output_clear",
        "description": "Limpa as linhas de uma aba de saída no NetBeans.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "tab": {"type": "string", "description": "Nome da aba de saída a ser limpa"}
            },
            "required": ["tab"]
        }
    },

    # --- Diagnostics, AST & Semantic Navigation ---
    {
        "name": "nb_diagnostics_get",
        "description": "Obtém instantaneamente erros e warnings de compilação de um arquivo Java a partir da AST do NetBeans sem build em disco.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "file": {"type": "string", "description": "Caminho absoluto do arquivo Java"}
            },
            "required": ["file"]
        }
    },
    {
        "name": "nb_ast_get_structure",
        "description": "Retorna o outline estruturado da AST (classes, interfaces, métodos, parâmetros, campos, anotações e imports) do arquivo Java.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "file": {"type": "string", "description": "Caminho absoluto do arquivo Java"},
                "detail_level": {"type": "integer", "description": "Nível de detalhe (1 = básico, 2 = detalhado)", "default": 1}
            },
            "required": ["file"]
        }
    },
    {
        "name": "nb_goto_definition",
        "description": "Navega semanticamente para a definição de uma classe, método ou símbolo a partir do arquivo e linha.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "file": {"type": "string", "description": "Caminho absoluto do arquivo Java"},
                "line": {"type": "integer", "description": "Linha do símbolo no editor"},
                "column": {"type": "integer", "description": "Coluna do símbolo no editor (opcional, padrão 1)"},
                "symbol": {"type": "string", "description": "Nome do símbolo (opcional)"}
            },
            "required": ["file", "line"]
        }
    },
    {
        "name": "nb_find_usages",
        "description": "Localiza todas as ocorrências e referências de um símbolo no projeto NetBeans.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "file": {"type": "string", "description": "Caminho absoluto do arquivo Java onde o símbolo está definido"},
                "symbol": {"type": "string", "description": "Nome do símbolo a buscar"}
            },
            "required": ["file", "symbol"]
        }
    },

    # --- Project & Action Invocation Tools ---
    {
        "name": "nb_project_list",
        "description": "Lista todos os projetos abertos no NetBeans e indica qual é o projeto principal.",
        "inputSchema": {
            "type": "object",
            "properties": {}
        }
    },
    {
        "name": "nb_project_open",
        "description": "Abre um diretório como projeto no NetBeans.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "path": {"type": "string", "description": "Caminho absoluto do diretório do projeto"}
            },
            "required": ["path"]
        }
    },
    {
        "name": "nb_project_action",
        "description": "Dispara uma ação de projeto (build, clean, clean_and_build, run, test, test_single, run_single, debug_single) no NetBeans.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "action": {"type": "string", "description": "Ação a executar: 'build', 'clean', 'clean_and_build', 'run', 'test', 'test_single', 'run_single', 'debug_single'"},
                "project": {"type": "string", "description": "Caminho do diretório do projeto (opcional, padrão projeto principal)"},
                "file": {"type": "string", "description": "Arquivo alvo para ações do tipo test_single ou run_single"}
            },
            "required": ["action"]
        }
    },
    {
        "name": "nb_invoke_action",
        "description": "Invoca qualquer ação nativa ou global registrada no NetBeans a partir do Action ID (ex: 'org-netbeans-core-actions-SaveAllAction').",
        "inputSchema": {
            "type": "object",
            "properties": {
                "action_id": {"type": "string", "description": "ID da ação registrado no NetBeans"},
                "category": {"type": "string", "description": "Categoria da ação (opcional)"}
            },
            "required": ["action_id"]
        }
    }
]

def call_bridge(endpoint, payload=None):
    url = f"{BRIDGE_URL}{endpoint}"
    data = None
    headers = {"Content-Type": "application/json; charset=utf-8"}
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode('utf-8')
    req = urllib.request.Request(url, data=data, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            res_body = response.read().decode('utf-8')
            return json.loads(res_body)
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode('utf-8')
        try:
            return json.loads(err_msg)
        except Exception:
            return {"ok": False, "error": f"HTTP {e.code}: {err_msg}"}
    except urllib.error.URLError as e:
        return {"ok": False, "error": f"Não foi possível conectar ao NetBeans na porta 8388 ({e}). O NetBeans está aberto com o plugin instalado?"}
    except Exception as e:
        return {"ok": False, "error": str(e)}

def execute_tool(tool_name, args):
    if tool_name == "nb_status":
        return call_bridge("/status")
    elif tool_name == "nb_open_file":
        return call_bridge("/open", {"file": args.get("file"), "line": args.get("line", 1)})
    elif tool_name == "nb_get_buffer":
        return call_bridge("/get-content", {"file": args.get("file")})
    elif tool_name == "nb_edit_buffer":
        return call_bridge("/edit", args)
    elif tool_name == "nb_replace_lines":
        return call_bridge("/replace-lines", args)
    elif tool_name == "nb_set_content":
        return call_bridge("/set-content", args)
    elif tool_name == "nb_save_buffer":
        return call_bridge("/save", {"file": args.get("file")})
    elif tool_name == "nb_revert_buffer":
        return call_bridge("/revert", {"file": args.get("file")})
    elif tool_name == "nb_format_code":
        return call_bridge("/format", args)
    elif tool_name == "nb_get_selection":
        return call_bridge("/get-selection", {"file": args.get("file")})
    elif tool_name == "nb_set_selection":
        return call_bridge("/set-selection", args)
    elif tool_name == "nb_open_commit":
        return call_bridge("/open-commit", args)

    elif tool_name == "nb_debug_status":
        return call_bridge("/debug/status")
    elif tool_name == "nb_debug_set_breakpoint":
        return call_bridge("/debug/set-breakpoint", args)
    elif tool_name == "nb_debug_remove_breakpoint":
        return call_bridge("/debug/remove-breakpoint", args)
    elif tool_name == "nb_debug_list_breakpoints":
        return call_bridge("/debug/list-breakpoints")
    elif tool_name == "nb_debug_control":
        return call_bridge("/debug/control", args)
    elif tool_name == "nb_debug_get_stack":
        return call_bridge("/debug/stack", args)
    elif tool_name == "nb_debug_get_variables":
        return call_bridge("/debug/variables", args)
    elif tool_name == "nb_debug_evaluate":
        return call_bridge("/debug/eval", args)
    elif tool_name == "nb_debug_add_watch":
        return call_bridge("/debug/watches/add", args)
    elif tool_name == "nb_debug_list_watches":
        return call_bridge("/debug/watches/list")
    elif tool_name == "nb_debug_remove_watch":
        return call_bridge("/debug/watches/remove", args)
    elif tool_name == "nb_debug_get_last_exception":
        return call_bridge("/debug/last-exception")

    elif tool_name == "nb_output_list_tabs":
        return call_bridge("/output/tabs")
    elif tool_name == "nb_output_get_text":
        return call_bridge("/output/read", args)
    elif tool_name == "nb_output_clear":
        return call_bridge("/output/clear", args)

    elif tool_name == "nb_diagnostics_get":
        return call_bridge("/diagnostics", args)
    elif tool_name == "nb_ast_get_structure":
        return call_bridge("/ast", args)
    elif tool_name == "nb_goto_definition":
        return call_bridge("/goto-definition", args)
    elif tool_name == "nb_find_usages":
        return call_bridge("/find-usages", args)

    elif tool_name == "nb_project_list":
        return call_bridge("/projects/list")
    elif tool_name == "nb_project_open":
        return call_bridge("/projects/open", args)
    elif tool_name == "nb_project_action":
        return call_bridge("/projects/action", args)
    elif tool_name == "nb_invoke_action":
        return call_bridge("/invoke", args)

    return {"ok": False, "error": f"Ferramenta desconhecida: {tool_name}"}

def main():
    while True:
        try:
            line = sys.stdin.readline()
            if not line:
                break
            msg = json.loads(line)
        except Exception:
            break

        method = msg.get("method")
        msg_id = msg.get("id")
        params = msg.get("params", {})

        if method == "initialize":
            resp = {
                "jsonrpc": "2.0",
                "id": msg_id,
                "result": {
                    "protocolVersion": "2024-11-05",
                    "serverInfo": {
                        "name": "antigravity-netbeans-bridge",
                        "version": "1.2.0"
                    },
                    "capabilities": {
                        "tools": {}
                    }
                }
            }
        elif method == "notifications/initialized":
            continue
        elif method == "tools/list":
            resp = {
                "jsonrpc": "2.0",
                "id": msg_id,
                "result": {
                    "tools": TOOLS
                }
            }
        elif method == "tools/call":
            tool_name = params.get("name")
            tool_args = params.get("arguments", {})
            tool_res = execute_tool(tool_name, tool_args)
            is_err = not tool_res.get("ok", True) if isinstance(tool_res, dict) else False
            resp = {
                "jsonrpc": "2.0",
                "id": msg_id,
                "result": {
                    "content": [
                        {
                            "type": "text",
                            "text": json.dumps(tool_res, ensure_ascii=False, indent=2)
                        }
                    ],
                    "isError": is_err
                }
            }
        elif method == "ping":
            resp = {
                "jsonrpc": "2.0",
                "id": msg_id,
                "result": {}
            }
        else:
            resp = {
                "jsonrpc": "2.0",
                "id": msg_id,
                "error": {
                    "code": -32601,
                    "message": f"Method not found: {method}"
                }
            }

        sys.stdout.write(json.dumps(resp, ensure_ascii=False) + "\n")
        sys.stdout.flush()

if __name__ == "__main__":
    main()
