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
 * Encapsula leitura de body, parsing JSON, injeção de CORS, cabeçalhos e tratamento de erros.
 */
public abstract class AbstractJsonHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(AbstractJsonHandler.class.getName());
    private final boolean requirePost;

    public AbstractJsonHandler() {
        this(true);
    }

    public AbstractJsonHandler(boolean requirePost) {
        this.requirePost = requirePost;
    }

    @Override
    public final void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        // Tratar pre-flight CORS OPTIONS
        if ("OPTIONS".equalsIgnoreCase(method)) {
            sendResponse(exchange, 204, "");
            return;
        }

        // Validação de método POST obrigatório quando configurado
        if (requirePost && !"POST".equalsIgnoreCase(method)) {
            BridgeResponse err = BridgeResponse.error(405, "Método HTTP não permitido: " + method + ". Esperado: POST");
            sendResponse(exchange, err.getStatusCode(), err.toJson());
            return;
        }

        Map<String, Object> params = parseRequestBody(exchange);

        long startNanos = System.nanoTime();
        int statusCode = 500;
        try {
            BridgeResponse response = handleRequest(params, exchange);
            if (response == null) {
                response = BridgeResponse.ok();
            }
            statusCode = response.getStatusCode();
            sendResponse(exchange, statusCode, response.toJson());

        } catch (IllegalArgumentException ex) {
            BridgeResponse err = BridgeResponse.error(400, ex.getMessage());
            statusCode = err.getStatusCode();
            sendResponse(exchange, statusCode, err.toJson());

        } catch (IllegalStateException ex) {
            BridgeResponse err = BridgeResponse.error(409, ex.getMessage());
            statusCode = err.getStatusCode();
            sendResponse(exchange, statusCode, err.toJson());

        } catch (Throwable ex) {
            LOG.log(Level.WARNING, "Erro durante execução de " + exchange.getRequestURI(), ex);
            BridgeResponse err = BridgeResponse.error(500, ex.getMessage() != null ? ex.getMessage() : ex.toString());
            statusCode = err.getStatusCode();
            sendResponse(exchange, statusCode, err.toJson());

        } finally {
            String path = exchange.getRequestURI().getPath();
            if (!"/ping".equals(path)) {
                long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
                BridgeLog.request(path, getStringParam(params, "file", "path"), statusCode < 400, elapsedMs);
            }
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
        exchange.getResponseHeaders().set(BridgeConstants.HEADER_CONTENT_TYPE, BridgeConstants.MIME_JSON);
        exchange.getResponseHeaders().set(BridgeConstants.HEADER_ALLOW_ORIGIN, BridgeConstants.CORS_ORIGIN_ALL);
        exchange.getResponseHeaders().set(BridgeConstants.HEADER_ALLOW_METHODS, BridgeConstants.CORS_METHODS);
        exchange.getResponseHeaders().set(BridgeConstants.HEADER_ALLOW_HEADERS, BridgeConstants.CORS_HEADERS);

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
