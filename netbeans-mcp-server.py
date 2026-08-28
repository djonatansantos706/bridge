#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Antigravity NetBeans Bridge Suite - MCP Server (Stdio)
Expõe ferramentas para interação in-memory, depuração JPDA, logs de saída,
diagnósticos AST e controle de projetos no Apache NetBeans via MCP.
"""

import sys
import json
import urllib.request
import urllib.error

BRIDGE_URL = "http://127.0.0.1:8388"

TOOLS = [
    {
        "name": "nb_status",
        "description": "Verifica se a Antigravity Bridge Suite está ativa no NetBeans e respondendo.",
        "inputSchema": {
            "type": "object",
            "properties": {}
        }
    },
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
        "description": "Edita um arquivo diretamente no buffer do NetBeans sem salvar no disco (marca com * não salvo, preserva histórico local e encoding do projeto).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "file": {"type": "string", "description": "Caminho absoluto do arquivo"},
                "old_text": {"type": "string", "description": "Texto exato a ser substituído"},
                "new_text": {"type": "string", "description": "Novo texto substituto"},
                "allow_multiple": {"type": "boolean", "description": "Se true, substitui todas as ocorrências (padrão false)"}
            },
            "required": ["file", "old_text", "new_text"]
        }
    },
    {
        "name": "nb_replace_lines",
        "description": "Substitui um trecho dentro de um intervalo de linhas no buffer do NetBeans.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "file": {"type": "string", "description": "Caminho absoluto do arquivo"},
                "start_line": {"type": "integer", "description": "Linha inicial (1-indexada)"},
                "end_line": {"type": "integer", "description": "Linha final (1-indexada)"},
                "target_content": {"type": "string", "description": "Conteúdo alvo a ser substituído"},
                "replacement_content": {"type": "string", "description": "Conteúdo substituto"}
            },
            "required": ["file", "start_line", "end_line", "target_content", "replacement_content"]
        }
    },
    {
        "name": "nb_set_content",
        "description": "Substitui todo o conteúdo em buffer do arquivo no NetBeans.",
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
        "name": "nb_open_commit",
        "description": "Abre a tela nativa de commit do NetBeans (Subversion ou Git) com os arquivos especificados selecionados e mensagem pré-preenchida.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "files": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "Lista de caminhos absolutos dos arquivos ou diretórios para commit"
                },
                "message": {
                    "type": "string",
                    "description": "Mensagem descritiva de commit"
                }
            },
            "required": ["files"]
        }
    },
    {
        "name": "nb_debug_status",
        "description": "Verifica se há sessão JPDA ativa no NetBeans, estado da JVM (RUNNING, STOPPED, etc.) e thread atual.",
        "inputSchema": {
            "type": "object",
            "properties": {}
        }
    },
    {
        "name": "nb_debug_set_breakpoint",
        "description": "Cria um breakpoint no NetBeans JPDA Debugger em um arquivo e linha com condição opcional.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "file": {"type": "string", "description": "Caminho absoluto do arquivo Java"},
                "line": {"type": "integer", "description": "Número da linha (1-indexada)"},
                "condition": {"type": "string", "description": "Condição booleana em Java para disparo do breakpoint (ex: 'id > 10')"}
            },
            "required": ["file", "line"]
        }
    },
    {
        "name": "nb_debug_remove_breakpoint",
        "description": "Remove um breakpoint por ID hexadecimal ou por arquivo e linha.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "id": {"type": "string", "description": "ID do breakpoint (opcional se informado file e line)"},
                "file": {"type": "string", "description": "Caminho do arquivo Java"},
                "line": {"type": "integer", "description": "Número da linha"}
            }
        }
    },
    {
        "name": "nb_debug_list_breakpoints",
        "description": "Lista todos os breakpoints cadastrados no NetBeans com ID, tipo, arquivo, linha e condições.",
        "inputSchema": {
            "type": "object",
            "properties": {}
        }
    },
    {
        "name": "nb_debug_control",
        "description": "Controla a execução do depurador JPDA no NetBeans.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "enum": ["step_into", "step_over", "step_out", "continue", "pause", "stop"],
                    "description": "Ação de depuração a executar"
                }
            },
            "required": ["action"]
        }
    },
    {
        "name": "nb_debug_get_stack",
        "description": "Retorna o call stack (pilha de chamadas) e frames da thread atual ou de uma thread específica.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "thread": {"type": "string", "description": "Nome da thread (opcional, padrão thread atual selecionada)"}
            }
        }
    },
    {
        "name": "nb_debug_get_variables",
        "description": "Inspeciona variáveis locais e campos do objeto 'this' no frame da pilha selecionado.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "frame": {"type": "integer", "description": "Índice do frame na pilha (0 = topo da pilha)", "default": 0},
                "depth": {"type": "integer", "description": "Profundidade de inspeção de campos de objetos (padrão 2)", "default": 2}
            }
        }
    },
    {
        "name": "nb_debug_evaluate",
        "description": "Avalia uma expressão Java em tempo real no contexto da sessão de depuração suspensa.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "expression": {"type": "string", "description": "Expressão Java a ser avaliada (ex: 'usuario.getNome()')"},
                "frame": {"type": "integer", "description": "Índice do frame da pilha de chamadas (opcional, padrão 0)"}
            },
            "required": ["expression"]
        }
    },
    {
        "name": "nb_output_list_tabs",
        "description": "Lista todas as abas ativas na janela de saída (Output Window) do NetBeans.",
        "inputSchema": {
            "type": "object",
            "properties": {}
        }
    },
    {
        "name": "nb_output_get_text",
        "description": "Lê linhas de log de uma aba de saída do NetBeans com suporte a paginação incremental.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "tab": {"type": "string", "description": "Nome da aba de saída (opcional, lê aba principal se omitido)"},
                "since_line": {"type": "integer", "description": "Offset da linha inicial a partir da qual ler (0-indexado, padrão 0)", "default": 0},
                "max_lines": {"type": "integer", "description": "Quantidade máxima de linhas a retornar (padrão 500)", "default": 500}
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
                "project": {"type": "string", "description": "Caminho do diretório do projeto (opcional, padrão projeto principal ou projeto do arquivo)"},
                "file": {"type": "string", "description": "Arquivo alvo para ações do tipo test_single ou run_single"}
            },
            "required": ["action"]
        }
    },
    {
        "name": "nb_invoke_action",
        "description": "Executa uma ação global registrada no NetBeans a partir do seu ID de ação (Actions.forID).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "action_id": {"type": "string", "description": "Identificador da ação (ex: 'org.netbeans.modules.project.ui.actions.OpenProject')"},
                "category": {"type": "string", "description": "Categoria da ação (ex: 'File', 'Edit', 'Project', etc., opcional)"}
            },
            "required": ["action_id"]
        }
    }
]

def http_post(endpoint, payload=None):
    url = f"{BRIDGE_URL}{endpoint}"
    headers = {"Content-Type": "application/json; charset=utf-8"}
    data = None
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode('utf-8')
    req = urllib.request.Request(url, data=data, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return json.loads(resp.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode('utf-8'))
        except Exception:
            return {"ok": False, "error": f"HTTP Error {e.code}: {e.reason}"}
    except urllib.error.URLError as e:
        return {"ok": False, "error": f"Não foi possível conectar à NetBeans Bridge Suite (porta 8388). O NetBeans está aberto com o plugin ativo? ({e})"}
    except Exception as e:
        return {"ok": False, "error": str(e)}

def http_get(endpoint):
    url = f"{BRIDGE_URL}{endpoint}"
    req = urllib.request.Request(url)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return json.loads(resp.read().decode('utf-8'))
    except urllib.error.URLError as e:
        return {"ok": False, "error": f"Não foi possível conectar ao NetBeans na porta 8388 ({e})"}
    except Exception as e:
        return {"ok": False, "error": str(e)}

def execute_tool(name, args):
    if name == "nb_status":
        return http_get("/status")
    elif name == "nb_open_file":
        return http_post("/open", {"file": args.get("file"), "line": args.get("line", 1)})
    elif name == "nb_get_buffer":
        return http_post("/get-content", {"file": args.get("file")})
    elif name == "nb_edit_buffer":
        return http_post("/edit", {
            "file": args.get("file"),
            "old_text": args.get("old_text"),
            "new_text": args.get("new_text"),
            "allow_multiple": args.get("allow_multiple", False)
        })
    elif name == "nb_replace_lines":
        return http_post("/replace-lines", {
            "file": args.get("file"),
            "start_line": args.get("start_line", 1),
            "end_line": args.get("end_line", 999999),
            "target_content": args.get("target_content"),
            "replacement_content": args.get("replacement_content")
        })
    elif name == "nb_set_content":
        return http_post("/set-content", {
            "file": args.get("file"),
            "content": args.get("content")
        })
    elif name == "nb_open_commit":
        return http_post("/open-commit", {
            "files": args.get("files", []),
            "message": args.get("message", "")
        })
    # JPDA Debug
    elif name == "nb_debug_status":
        return http_get("/debug/status")
    elif name == "nb_debug_set_breakpoint":
        return http_post("/debug/set-breakpoint", {
            "file": args.get("file"),
            "line": args.get("line"),
            "condition": args.get("condition")
        })
    elif name == "nb_debug_remove_breakpoint":
        return http_post("/debug/remove-breakpoint", {
            "id": args.get("id"),
            "file": args.get("file"),
            "line": args.get("line")
        })
    elif name == "nb_debug_list_breakpoints":
        return http_get("/debug/list-breakpoints")
    elif name == "nb_debug_control":
        return http_post("/debug/control", {"action": args.get("action")})
    elif name == "nb_debug_get_stack":
        return http_post("/debug/stack", {"thread": args.get("thread")})
    elif name == "nb_debug_get_variables":
        return http_post("/debug/variables", {
            "frame": args.get("frame", 0),
            "depth": args.get("depth", 2)
        })
    elif name == "nb_debug_evaluate":
        return http_post("/debug/eval", {
            "expression": args.get("expression"),
            "frame": args.get("frame")
        })
    # Output & Console
    elif name == "nb_output_list_tabs":
        return http_get("/output/tabs")
    elif name == "nb_output_get_text":
        return http_post("/output/read", {
            "tab": args.get("tab"),
            "since_line": args.get("since_line", 0),
            "max_lines": args.get("max_lines", 500)
        })
    elif name == "nb_output_clear":
        return http_post("/output/clear", {"tab": args.get("tab")})
    # Diagnostics & AST
    elif name == "nb_diagnostics_get":
        return http_post("/diagnostics", {"file": args.get("file")})
    elif name == "nb_ast_get_structure":
        return http_post("/ast", {
            "file": args.get("file"),
            "detail_level": args.get("detail_level", 1)
        })
    # Projects & Actions
    elif name == "nb_project_list":
        return http_get("/projects/list")
    elif name == "nb_project_open":
        return http_post("/projects/open", {"path": args.get("path")})
    elif name == "nb_project_action":
        return http_post("/projects/action", {
            "project": args.get("project"),
            "action": args.get("action"),
            "file": args.get("file")
        })
    elif name == "nb_invoke_action":
        return http_post("/invoke", {
            "action_id": args.get("action_id"),
            "category": args.get("category")
        })
    else:
        return {"ok": False, "error": f"Ferramenta desconhecida: {name}"}

def main():
    while True:
        line = sys.stdin.readline()
        if not line:
            break
        line = line.strip()
        if not line:
            continue
        try:
            req = json.loads(line)
        except Exception:
            continue

        msg_id = req.get("id")
        method = req.get("method")
        params = req.get("params", {})

        if method == "initialize":
            resp = {
                "jsonrpc": "2.0",
                "id": msg_id,
                "result": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {
                        "tools": {}
                    },
                    "serverInfo": {
                        "name": "netbeans-bridge",
                        "version": "1.1.0"
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
