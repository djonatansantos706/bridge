#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Gerador de schemas da Antigravity NetBeans Bridge Suite.

A lista TOOLS de netbeans-mcp-server.py é a fonte única de verdade das
ferramentas da bridge. Este script deriva dela os artefatos que precisam
ficar em sincronia:

  - mcp-schemas/<tool>.json  (formato function-calling do Antigravity/Gemini,
    campo "parameters" no lugar do "inputSchema" do MCP)
  - tabela markdown do catálogo de ferramentas do README (sob demanda)

Uso:
  python3 generate_schemas.py                # (re)gera os arquivos de mcp-schemas/
  python3 generate_schemas.py --check        # só verifica; exit 1 se algo divergir
  python3 generate_schemas.py --readme-table # imprime a tabela markdown do catálogo
"""

import os
import sys
import json
import glob
import importlib.util

REPO_ROOT = os.path.dirname(os.path.abspath(__file__))
SCHEMAS_DIR = os.path.join(REPO_ROOT, "mcp-schemas")
MCP_SERVER_FILE = os.path.join(REPO_ROOT, "netbeans-mcp-server.py")


def load_tools():
    """Importa netbeans-mcp-server.py (o hífen impede import direto) e retorna TOOLS."""
    spec = importlib.util.spec_from_file_location("netbeans_mcp_server", MCP_SERVER_FILE)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.TOOLS


def to_gemini_schema(tool):
    """Converte a definição MCP (inputSchema) para o formato do Antigravity (parameters)."""
    return {
        "name": tool["name"],
        "description": tool["description"],
        "parameters": tool["inputSchema"],
    }


def render(schema):
    return json.dumps(schema, ensure_ascii=False, indent=2) + "\n"


def generate():
    tools = load_tools()
    os.makedirs(SCHEMAS_DIR, exist_ok=True)

    expected_files = set()
    written = 0
    for tool in tools:
        schema = to_gemini_schema(tool)
        path = os.path.join(SCHEMAS_DIR, f"{tool['name']}.json")
        expected_files.add(os.path.basename(path))
        content = render(schema)
        current = None
        if os.path.exists(path):
            with open(path, "r", encoding="utf-8") as f:
                current = f.read()
        if current != content:
            with open(path, "w", encoding="utf-8") as f:
                f.write(content)
            print(f"[gerado] {os.path.basename(path)}")
            written += 1

    orphans = sorted(
        os.path.basename(p)
        for p in glob.glob(os.path.join(SCHEMAS_DIR, "*.json"))
        if os.path.basename(p) not in expected_files
    )
    for orphan in orphans:
        print(f"[AVISO] schema órfão (sem ferramenta correspondente em TOOLS): {orphan}")

    print(f"OK: {len(tools)} ferramentas, {written} arquivo(s) atualizado(s), {len(orphans)} órfão(s).")
    return len(orphans) == 0


def check():
    tools = load_tools()
    problems = []

    expected = {f"{t['name']}.json": render(to_gemini_schema(t)) for t in tools}
    existing = {
        os.path.basename(p) for p in glob.glob(os.path.join(SCHEMAS_DIR, "*.json"))
    }

    for filename, content in sorted(expected.items()):
        path = os.path.join(SCHEMAS_DIR, filename)
        if not os.path.exists(path):
            problems.append(f"ausente: {filename}")
            continue
        with open(path, "r", encoding="utf-8") as f:
            if f.read() != content:
                problems.append(f"divergente de TOOLS: {filename}")

    for orphan in sorted(existing - set(expected)):
        problems.append(f"órfão (sem ferramenta em TOOLS): {orphan}")

    if problems:
        print("mcp-schemas/ fora de sincronia com o TOOLS de netbeans-mcp-server.py:")
        for p in problems:
            print(f"  - {p}")
        print("Rode 'python3 generate_schemas.py' para regenerar.")
        return False

    print(f"OK: {len(expected)} schemas em sincronia com TOOLS.")
    return True


# Categorias do catálogo do README, na ordem de exibição.
# Uma ferramenta pertence à primeira categoria cujo critério casar.
README_CATEGORIES = [
    ("🐞 Depuração JPDA Avançada", lambda n: n.startswith("nb_debug_")),
    ("🔍 Diagnósticos, AST e Navegação Semântica",
     lambda n: n.startswith(("nb_diagnostics_", "nb_ast_", "nb_goto_", "nb_find_"))),
    ("📟 Console de Saída e Logs", lambda n: n.startswith("nb_output_")),
    ("🏗️ Gerenciamento de Projetos e Ações IDE",
     lambda n: n.startswith("nb_project_") or n in ("nb_invoke_action", "nb_open_commit")),
    ("📝 Edição e Gerenciamento de Buffers", lambda n: True),
]


def readme_table():
    tools = load_tools()
    grouped = {title: [] for title, _ in README_CATEGORIES}
    for tool in tools:
        for title, matches in README_CATEGORIES:
            if matches(tool["name"]):
                grouped[title].append(tool)
                break

    # Edição primeiro no README; aqui reordenamos só para exibição
    display_order = [README_CATEGORIES[-1][0]] + [t for t, _ in README_CATEGORIES[:-1]]
    for title in display_order:
        tools_in_cat = grouped[title]
        print(f"### {title} ({len(tools_in_cat)} Ferramentas)\n")
        print("| Ferramenta | Descrição |")
        print("| :--- | :--- |")
        for tool in tools_in_cat:
            print(f"| `{tool['name']}` | {tool['description']} |")
        print()


def main():
    if "--check" in sys.argv:
        sys.exit(0 if check() else 1)
    if "--readme-table" in sys.argv:
        readme_table()
        sys.exit(0)
    sys.exit(0 if generate() else 1)


if __name__ == "__main__":
    main()
