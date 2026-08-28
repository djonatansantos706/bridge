#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Antigravity NetBeans Bridge Client & CLI Tool
Permite que o Antigravity interaja diretamente com os buffers em memória do NetBeans.
"""

import sys
import json
import urllib.request
import urllib.error

DEFAULT_URL = "http://127.0.0.1:8388"

def send_request(endpoint, payload=None):
    url = f"{DEFAULT_URL}{endpoint}"
    headers = {"Content-Type": "application/json; charset=utf-8"}
    
    data = None
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode('utf-8')
        
    req = urllib.request.Request(url, data=data, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=5) as response:
            res_body = response.read().decode('utf-8')
            return json.loads(res_body)
    except urllib.error.URLError as e:
        return {"ok": False, "error": f"Erro de conexão com o NetBeans (o NetBeans está aberto com o plugin instalado?): {e}"}
    except Exception as e:
        return {"ok": False, "error": str(e)}

def check_status():
    return send_request("/status")

def open_file(file_path, line=1):
    return send_request("/open", {"file": file_path, "line": line})

def get_buffer(file_path):
    return send_request("/get-content", {"file": file_path})

def edit_buffer(file_path, old_text, new_text, allow_multiple=False):
    return send_request("/edit", {
        "file": file_path,
        "old_text": old_text,
        "new_text": new_text,
        "allow_multiple": allow_multiple
    })

def replace_lines(file_path, start_line, end_line, target_content, replacement_content):
    return send_request("/replace-lines", {
        "file": file_path,
        "start_line": start_line,
        "end_line": end_line,
        "target_content": target_content,
        "replacement_content": replacement_content
    })

def set_full_buffer(file_path, content):
    return send_request("/set-content", {
        "file": file_path,
        "content": content
    })

def format_code(file_path, start_line=None, end_line=None):
    payload = {"file": file_path}
    if start_line is not None:
        payload["start_line"] = start_line
    if end_line is not None:
        payload["end_line"] = end_line
    return send_request("/format", payload)

def get_selection(file_path):
    return send_request("/selection/get", {"file": file_path})

def set_selection(file_path, start_line, start_col=1, end_line=None, end_col=None):
    return send_request("/selection/set", {
        "file": file_path,
        "start_line": start_line,
        "start_column": start_col,
        "end_line": end_line if end_line is not None else start_line,
        "end_column": end_col if end_col is not None else start_col
    })

def goto_definition(file_path, line=0, column=0, symbol=None):
    payload = {"file": file_path}
    if line: payload["line"] = line
    if column: payload["column"] = column
    if symbol: payload["symbol"] = symbol
    return send_request("/definition", payload)

def find_usages(file_path, symbol):
    return send_request("/usages", {"file": file_path, "symbol": symbol})

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Uso: python3 agy_nb_client.py [status|open|get|edit|format|selection|definition|usages] [argumentos...]")
        sys.exit(1)
        
    cmd = sys.argv[1]
    if cmd == "status":
        print(json.dumps(check_status(), indent=2, ensure_ascii=False))
    elif cmd == "open" and len(sys.argv) >= 3:
        line = int(sys.argv[3]) if len(sys.argv) > 3 else 1
        print(json.dumps(open_file(sys.argv[2], line), indent=2, ensure_ascii=False))
    elif cmd == "get" and len(sys.argv) >= 3:
        print(json.dumps(get_buffer(sys.argv[2]), indent=2, ensure_ascii=False))
    elif cmd == "format" and len(sys.argv) >= 3:
        start_line = int(sys.argv[3]) if len(sys.argv) > 3 else None
        end_line = int(sys.argv[4]) if len(sys.argv) > 4 else None
        print(json.dumps(format_code(sys.argv[2], start_line, end_line), indent=2, ensure_ascii=False))
    elif cmd == "selection" and len(sys.argv) >= 3:
        print(json.dumps(get_selection(sys.argv[2]), indent=2, ensure_ascii=False))
    elif cmd == "definition" and len(sys.argv) >= 3:
        sym = sys.argv[3] if len(sys.argv) > 3 else None
        print(json.dumps(goto_definition(sys.argv[2], symbol=sym), indent=2, ensure_ascii=False))
    elif cmd == "usages" and len(sys.argv) >= 4:
        print(json.dumps(find_usages(sys.argv[2], sys.argv[3]), indent=2, ensure_ascii=False))
    else:
        print("Comando não reconhecido ou parâmetros insuficientes.")
