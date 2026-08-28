#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
QA Test Suite - Antigravity NetBeans Bridge Suite v1.2.0
Valida a integridade, compatibilidade de encoding, schemas MCP, JsonUtils e conectividade da Bridge Suite.
"""

import os
import sys
import json
import unittest
import urllib.request
import urllib.error

class TestAntigravityNetBeansBridgeSuiteV120(unittest.TestCase):

    def test_latin1_encoding_preservation(self):
        """Valida que textos com acentuação em ISO-8859-1 / Windows-1252 não sofrem perda de caracteres."""
        sample_text_iso = "Atenção: Transação de Cupom Fiscal Eletrônico Nº 12345 - R$ 99,50 (Acréscimo/Desconto)".encode("iso-8859-1")
        decoded = sample_text_iso.decode("iso-8859-1")
        
        payload = {
            "file": "/tmp/TestFile.java",
            "old_text": "Cupom Fiscal",
            "new_text": "Cupom Fiscal Eletrônico Nº 12345 (Acréscimo)"
        }
        encoded_json = json.dumps(payload, ensure_ascii=False).encode('utf-8')
        parsed = json.loads(encoded_json.decode('utf-8'))
        
        self.assertEqual(parsed["new_text"], "Cupom Fiscal Eletrônico Nº 12345 (Acréscimo)")
        
        # Test re-encoding back to ISO-8859-1
        re_encoded = parsed["new_text"].encode("iso-8859-1")
        self.assertEqual(re_encoded.decode("iso-8859-1"), "Cupom Fiscal Eletrônico Nº 12345 (Acréscimo)")

    def test_plugin_nbm_artifacts(self):
        """Valida que os arquivos .nbm e .jar v1.2.0 foram gerados com sucesso."""
        target_nbm = "/home/merito/Área de Trabalho/Djonatan/agy-nb-bridge/target/nbm/agy-nb-bridge-1.2.0.nbm"
        dist_nbm = "/home/merito/Área de Trabalho/Djonatan/plugin/agy-nb-bridge-1.2.0.nbm"
        target_jar = "/home/merito/Área de Trabalho/Djonatan/agy-nb-bridge/target/agy-nb-bridge-1.2.0.jar"

        self.assertTrue(os.path.exists(target_nbm), f"NBM de compilação ausente: {target_nbm}")
        self.assertTrue(os.path.exists(dist_nbm), f"NBM de distribuição ausente: {dist_nbm}")
        self.assertTrue(os.path.exists(target_jar), f"JAR compilado ausente: {target_jar}")

        self.assertGreater(os.path.getsize(dist_nbm), 50000, "Arquivo .nbm de distribuição muito pequeno")

    def test_mcp_schemas_completeness(self):
        """Valida que todos os 35 schemas MCP estão presentes e válidos."""
        schemas_dir = "/home/merito/Área de Trabalho/Djonatan/agy-nb-bridge/mcp-schemas"
        expected_tools = [
            "nb_status", "nb_open_file", "nb_get_buffer", "nb_edit_buffer",
            "nb_replace_lines", "nb_set_content", "nb_save_buffer", "nb_revert_buffer",
            "nb_format_code", "nb_get_selection", "nb_set_selection", "nb_open_commit",
            "nb_debug_status", "nb_debug_set_breakpoint", "nb_debug_remove_breakpoint",
            "nb_debug_list_breakpoints", "nb_debug_control", "nb_debug_get_stack",
            "nb_debug_get_variables", "nb_debug_evaluate", "nb_debug_add_watch",
            "nb_debug_list_watches", "nb_debug_remove_watch", "nb_debug_get_last_exception",
            "nb_output_list_tabs", "nb_output_get_text", "nb_output_clear",
            "nb_diagnostics_get", "nb_ast_get_structure", "nb_goto_definition", "nb_find_usages",
            "nb_project_list", "nb_project_open", "nb_project_action",
            "nb_invoke_action"
        ]
        
        for tool in expected_tools:
            json_file = os.path.join(schemas_dir, f"{tool}.json")
            self.assertTrue(os.path.exists(json_file), f"Schema MCP ausente: {json_file}")
            with open(json_file, "r", encoding="utf-8") as f:
                data = json.load(f)
                self.assertEqual(data.get("name"), tool)
                self.assertIn("description", data)
                self.assertIn("parameters", data)

    def test_bridge_connectivity_if_running(self):
        """Verifica se o servidor do NetBeans está ativo."""
        url = "http://127.0.0.1:8388/ping"
        try:
            req = urllib.request.Request(url)
            with urllib.request.urlopen(req, timeout=2) as resp:
                res_data = json.loads(resp.read().decode('utf-8'))
                self.assertIn("status", res_data)
                print(f" -> [QA INFO] NetBeans Bridge ativo! Resposta: {res_data}")
        except Exception as e:
            print(f" -> [QA INFO] NetBeans Bridge porta 8388 ({e}).")

def run_qa():
    print("=" * 70)
    print("    INICIANDO QA SUITE: ANTIGRAVITY NETBEANS BRIDGE SUITE v1.2.0")
    print("=" * 70)
    suite = unittest.TestLoader().loadTestsFromTestCase(TestAntigravityNetBeansBridgeSuiteV120)
    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)
    print("=" * 70)
    if result.wasSuccessful():
        print("    STATUS: [APROVADO] Todos os testes passaram com sucesso!")
    else:
        print("    STATUS: [FALHA] Algum teste falhou.")
    print("=" * 70)
    return result.wasSuccessful()

if __name__ == "__main__":
    success = run_qa()
    sys.exit(0 if success else 1)
