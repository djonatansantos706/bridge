package com.merito.agynb;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Testes do parser/serializador JSON caseiro da bridge.
 *
 * O JsonUtils está no caminho de TODA requisição da bridge (body de entrada e
 * resposta de saída), então qualquer regressão aqui corrompe silenciosamente
 * edições de buffer, avaliações de debug e diagnósticos. Estes testes cobrem
 * o roundtrip completo, os escapes exigidos pela RFC 8259 e — crucial para os
 * projetos que a bridge atende — texto acentuado em português (ISO-8859-1 /
 * Windows-1252 convertido para String Java).
 */
public class JsonUtilsTest {

    // --- Roundtrip (toJson -> parse) ---

    @Test
    public void roundtripObjetoAninhado() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("linha", 42);
        inner.put("ativo", true);
        inner.put("nada", null);

        List<Object> lista = new ArrayList<>();
        lista.add("a");
        lista.add(1);
        lista.add(false);
        lista.add(inner);

        Map<String, Object> raiz = new LinkedHashMap<>();
        raiz.put("arquivo", "/tmp/Teste.java");
        raiz.put("itens", lista);
        raiz.put("meta", inner);

        String json = JsonUtils.toJson(raiz);
        Map<String, Object> reparsed = JsonUtils.parseObject(json);

        assertEquals("/tmp/Teste.java", reparsed.get("arquivo"));
        List<?> itensReparsed = (List<?>) reparsed.get("itens");
        assertEquals(4, itensReparsed.size());
        assertEquals("a", itensReparsed.get(0));
        assertEquals(1, itensReparsed.get(1));
        assertEquals(false, itensReparsed.get(2));
        Map<?, ?> metaReparsed = (Map<?, ?>) reparsed.get("meta");
        assertEquals(42, metaReparsed.get("linha"));
        assertEquals(true, metaReparsed.get("ativo"));
        assertTrue(metaReparsed.containsKey("nada"));
        assertNull(metaReparsed.get("nada"));
    }

    @Test
    public void roundtripAcentuacaoPortugues() {
        // Texto típico dos ERPs atendidos pela bridge (origem ISO-8859-1/Windows-1252)
        String texto = "Atenção: Transação Nº 12345 — R$ 99,50 (Acréscimo/Desconto) ção í ú ã õ ç";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("new_text", texto);

        String json = JsonUtils.toJson(payload);
        Map<String, Object> reparsed = JsonUtils.parseObject(json);

        assertEquals(texto, reparsed.get("new_text"));
    }

    @Test
    public void roundtripCaracteresDeControleEEscapes() {
        String texto = "linha1\nlinha2\ttab \"aspas\" barra\\ /barra2 \b\f\r fim";
        String json = JsonUtils.toJson(texto);
        assertEquals(texto, JsonUtils.parse(json));
    }

    @Test
    public void roundtripEmojiSurrogatePair() {
        String texto = "ok \uD83D\uDE00 fim";
        String json = JsonUtils.toJson(texto);
        assertEquals(texto, JsonUtils.parse(json));
    }

    @Test
    public void roundtripArrayPrimitivo() {
        assertEquals("[1,2,3]", JsonUtils.toJson(new int[]{1, 2, 3}));
        assertEquals("null", JsonUtils.toJson(null));
    }

    // --- Parse de literais e números ---

    @Test
    public void parseTiposNumericos() {
        assertEquals(42, JsonUtils.parse("42"));
        assertEquals(-7, JsonUtils.parse("-7"));
        assertEquals(3000000000L, JsonUtils.parse("3000000000"));
        assertEquals(1.5d, JsonUtils.parse("1.5"));
        assertEquals(1000.0d, JsonUtils.parse("1e3"));
        assertEquals(-0.25d, JsonUtils.parse("-2.5e-1"));
    }

    @Test
    public void parseLiterais() {
        assertEquals(Boolean.TRUE, JsonUtils.parse("true"));
        assertEquals(Boolean.FALSE, JsonUtils.parse("false"));
        assertNull(JsonUtils.parse("null"));
        assertNull(JsonUtils.parse(""));
        assertNull(JsonUtils.parse("   "));
    }

    @Test
    public void parseEscapesUnicode() {
        assertEquals("ção", JsonUtils.parse("\"\\u00e7\\u00e3o\""));
        assertEquals("\uD83D\uDE00", JsonUtils.parse("\"\\ud83d\\ude00\""));
    }

    @Test
    public void parseEstruturasVazias() {
        assertEquals(0, JsonUtils.parseObject("{}").size());
        assertEquals(0, JsonUtils.parseArray("[]").size());
    }

    @Test
    public void parseObjectComJsonFormatado() {
        String json = "{\n  \"file\" : \"/tmp/A.java\",\n  \"line\" : 10,\n  \"tags\" : [ \"a\", \"b\" ]\n}";
        Map<String, Object> parsed = JsonUtils.parseObject(json);
        assertEquals("/tmp/A.java", parsed.get("file"));
        assertEquals(10, parsed.get("line"));
        assertEquals(2, ((List<?>) parsed.get("tags")).size());
    }

    @Test
    public void parseArrayHeterogeneo() {
        List<Object> parsed = JsonUtils.parseArray("[1, \"a\", true, null, {\"k\": 2}, [3]]");
        assertEquals(6, parsed.size());
        assertEquals(1, parsed.get(0));
        assertEquals("a", parsed.get(1));
        assertEquals(Boolean.TRUE, parsed.get(2));
        assertNull(parsed.get(3));
        assertEquals(2, ((Map<?, ?>) parsed.get(4)).get("k"));
        assertEquals(3, ((List<?>) parsed.get(5)).get(0));
    }

    @Test
    public void parseAninhamentoProfundo() {
        StringBuilder json = new StringBuilder();
        int depth = 50;
        for (int i = 0; i < depth; i++) {
            json.append("{\"n\":");
        }
        json.append("1");
        for (int i = 0; i < depth; i++) {
            json.append("}");
        }
        Map<String, Object> parsed = JsonUtils.parseObject(json.toString());
        for (int i = 0; i < depth - 1; i++) {
            parsed = castMap(parsed.get("n"));
        }
        assertEquals(1, parsed.get("n"));
    }

    // --- Contratos de conveniência usados pelos handlers ---

    @Test
    public void parseObjectRetornaMapVazioParaNaoObjeto() {
        // Os handlers dependem deste contrato: body que não é objeto vira Map vazio, nunca null
        assertEquals(0, JsonUtils.parseObject("[1,2]").size());
        assertEquals(0, JsonUtils.parseObject("\"texto\"").size());
        assertEquals(0, JsonUtils.parseObject(null).size());
    }

    @Test
    public void parseArrayRetornaListaVaziaParaNaoArray() {
        assertEquals(0, JsonUtils.parseArray("{\"a\":1}").size());
        assertEquals(0, JsonUtils.parseArray(null).size());
    }

    // --- Entradas inválidas ---

    @Test
    public void parseJsonInvalidoLancaExcecao() {
        assertParseFails("{\"a\":}");
        assertParseFails("{\"a\" 1}");
        assertParseFails("[1 2]");
        assertParseFails("\"string sem fim");
        assertParseFails("@invalido");
        assertParseFails("\"escape unicode quebrado \\u00g1\"");
    }

    @Test
    public void escapeJsonProduzSaidaRfc8259() {
        assertEquals("a\\\"b\\\\c\\nd\\te", JsonUtils.escapeJson("a\"b\\c\nd\te"));
        // Caracteres de controle fora da tabela de atalhos viram escape unicode de 4 dígitos
        assertEquals("\\u0001", JsonUtils.escapeJson("\u0001"));
        assertEquals("", JsonUtils.escapeJson(null));
    }

    // --- Apoio ---

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object obj) {
        assertTrue("Esperado Map aninhado, veio: " + obj, obj instanceof Map);
        return (Map<String, Object>) obj;
    }

    private static void assertParseFails(String json) {
        try {
            Object result = JsonUtils.parse(json);
            fail("Esperada IllegalArgumentException para <" + json + ">, retornou: " + result);
        } catch (IllegalArgumentException expected) {
            assertFalse(String.valueOf(expected.getMessage()).isEmpty());
        }
    }
}
