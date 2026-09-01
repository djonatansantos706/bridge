package com.merito.agynb.core;

import com.merito.agynb.JsonUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handler base com Template Method para tratamento unificado de requisições HTTP REST.
 * Encapsula leitura de body, parsing JSON, autenticação por token, cabeçalhos e tratamento de erros.
 */
public abstract class AbstractJsonHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(AbstractJsonHandler.class.getName());
    private final boolean requirePost;
    private final boolean requireAuth;

    public AbstractJsonHandler() {
        this(true, true);
    }

    public AbstractJsonHandler(boolean requirePost) {
        this(requirePost, true);
    }

    public AbstractJsonHandler(boolean requirePost, boolean requireAuth) {
        this.requirePost = requirePost;
        this.requireAuth = requireAuth;
    }

    @Override
    public final void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        // Autenticação: exige o token local em todos os endpoints (exceto /ping)
        if (requireAuth) {
            String candidate = exchange.getRequestHeaders().getFirst(BridgeConstants.HEADER_TOKEN);
            if (!BridgeToken.isValid(candidate)) {
                BridgeResponse err = BridgeResponse.error(401,
                        "Token de autenticação ausente ou inválido. Envie o header " + BridgeConstants.HEADER_TOKEN
                        + " com o conteúdo de " + BridgeToken.tokenFile());
                sendResponse(exchange, err.getStatusCode(), err.toJson());
                return;
            }
        }

        // Validação de método POST obrigatório quando configurado
        if (requirePost && !"POST".equalsIgnoreCase(method)) {
            BridgeResponse err = BridgeResponse.error(405, "Método HTTP não permitido: " + method + ". Esperado: POST");
            sendResponse(exchange, err.getStatusCode(), err.toJson());
            return;
        }

        Map<String, Object> params = parseRequestBody(exchange);

        try {
            BridgeResponse response = handleRequest(params, exchange);
            if (response == null) {
                response = BridgeResponse.ok();
            }
            sendResponse(exchange, response.getStatusCode(), response.toJson());

        } catch (IllegalArgumentException ex) {
            BridgeResponse err = BridgeResponse.error(400, ex.getMessage());
            sendResponse(exchange, err.getStatusCode(), err.toJson());

        } catch (IllegalStateException ex) {
            BridgeResponse err = BridgeResponse.error(409, ex.getMessage());
            sendResponse(exchange, err.getStatusCode(), err.toJson());

        } catch (Throwable ex) {
            LOG.log(Level.WARNING, "Erro durante execução de " + exchange.getRequestURI(), ex);
            BridgeResponse err = BridgeResponse.error(500, ex.getMessage() != null ? ex.getMessage() : ex.toString());
            sendResponse(exchange, err.getStatusCode(), err.toJson());
        }
    }

    /**
     * Método abstrato a ser implementado por cada handler especialista.
     */
    protected abstract BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception;

    protected Map<String, Object> parseRequestBody(HttpExchange exchange) {
        try {
            InputStream is = exchange.getRequestBody();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            String body = new String(baos.toByteArray(), StandardCharsets.UTF_8);
            if (body.trim().isEmpty()) {
                return new HashMap<>();
            }
            return JsonUtils.parseObject(body);
        } catch (Exception ex) {
            LOG.log(Level.FINE, "Aviso ao decodificar body da requisição: " + ex.getMessage());
            return new HashMap<>();
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String responseBody) throws IOException {
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        // Sem headers CORS: os clientes da bridge são processos locais (MCP/CLI),
        // nunca navegadores — negar CORS impede que páginas web acionem a porta.
        exchange.getResponseHeaders().set(BridgeConstants.HEADER_CONTENT_TYPE, BridgeConstants.MIME_JSON);

        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    // --- Utilitários de Extração de Parâmetros com Type-Safety ---

    protected String getStringParam(Map<String, Object> params, String... keys) {
        for (String k : keys) {
            if (params.containsKey(k) && params.get(k) instanceof String) {
                return (String) params.get(k);
            }
        }
        return null;
    }

    protected Integer getIntParam(Map<String, Object> params, Integer defaultValue, String... keys) {
        for (String k : keys) {
            if (params.containsKey(k) && params.get(k) instanceof Number) {
                return ((Number) params.get(k)).intValue();
            }
        }
        return defaultValue;
    }

    protected Boolean getBoolParam(Map<String, Object> params, Boolean defaultValue, String... keys) {
        for (String k : keys) {
            if (params.containsKey(k) && params.get(k) instanceof Boolean) {
                return (Boolean) params.get(k);
            }
        }
        return defaultValue;
    }
}
