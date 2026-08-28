package com.merito.agynb;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.awt.StatusDisplayer;

public class AgyBridgeServer {

    private static final Logger LOG = Logger.getLogger(AgyBridgeServer.class.getName());
    private static final int PORT = 8388;
    private static final AgyBridgeServer INSTANCE = new AgyBridgeServer();

    private HttpServer server;
    private boolean running = false;

    public static AgyBridgeServer getInstance() {
        return INSTANCE;
    }

    private AgyBridgeServer() {
    }

    public synchronized void start() {
        if (running) {
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            server.setExecutor(Executors.newCachedThreadPool());

            // Core & Editor
            server.createContext("/ping", new PingHandler());
            server.createContext("/status", new StatusHandler());
            server.createContext("/open", new OpenHandler());
            server.createContext("/get-content", new GetContentHandler());
            server.createContext("/edit", new EditHandler());
            server.createContext("/replace-lines", new ReplaceLinesHandler());
            server.createContext("/set-content", new SetContentHandler());
            server.createContext("/commit", new OpenCommitHandler());
            server.createContext("/open-commit", new OpenCommitHandler());

            // JPDA Debugger
            server.createContext("/debug/status", new DebugStatusHandler());
            server.createContext("/debug/set-breakpoint", new DebugSetBreakpointHandler());
            server.createContext("/debug/remove-breakpoint", new DebugRemoveBreakpointHandler());
            server.createContext("/debug/list-breakpoints", new DebugListBreakpointsHandler());
            server.createContext("/debug/control", new DebugControlHandler());
            server.createContext("/debug/stack", new DebugStackHandler());
            server.createContext("/debug/variables", new DebugVariablesHandler());
            server.createContext("/debug/eval", new DebugEvalHandler());

            // Output & Console
            server.createContext("/output/tabs", new OutputListTabsHandler());
            server.createContext("/output/read", new OutputReadHandler());
            server.createContext("/output/clear", new OutputClearHandler());

            // Diagnostics & AST
            server.createContext("/diagnostics", new DiagnosticsHandler());
            server.createContext("/ast", new AstHandler());

            // Projects & Actions
            server.createContext("/projects/list", new ProjectListHandler());
            server.createContext("/projects/open", new ProjectOpenHandler());
            server.createContext("/projects/action", new ProjectActionHandler());
            server.createContext("/invoke", new InvokeActionHandler());

            server.start();
            running = true;
            LOG.info("[Antigravity NetBeans Bridge] Servidor HTTP iniciado em http://127.0.0.1:" + PORT);
            StatusDisplayer.getDefault().setStatusText("[Antigravity] Bridge Suite ativa na porta " + PORT);
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "[Antigravity NetBeans Bridge] Falha ao iniciar servidor HTTP", ex);
        }
    }

    public synchronized void stop() {
        if (!running || server == null) {
            return;
        }
        try {
            server.stop(1);
            running = false;
            LOG.info("[Antigravity NetBeans Bridge] Servidor HTTP finalizado.");
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "[Antigravity NetBeans Bridge] Erro ao finalizar servidor", ex);
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return PORT;
    }

    public static void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonResponse) throws IOException {
        byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int r;
            while ((r = is.read(buf)) != -1) {
                baos.write(buf, 0, r);
            }
            return baos.toString(StandardCharsets.UTF_8.name());
        }
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < 0x20 || (ch >= 0x7F && ch <= 0x9F)) {
                        String hex = Integer.toHexString(ch);
                        sb.append("\\u");
                        for (int k = 0; k < 4 - hex.length(); k++) sb.append('0');
                        sb.append(hex);
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + escapeJson((String) obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(String.valueOf(entry.getKey()))).append("\":");
                sb.append(toJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escapeJson(obj.toString()) + "\"";
    }

    public static Map<String, Object> parseSimpleJson(String json) {
        Map<String, Object> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) {
            return map;
        }
        String s = json.trim();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);

        int pos = 0;
        int len = s.length();

        while (pos < len) {
            while (pos < len && (Character.isWhitespace(s.charAt(pos)) || s.charAt(pos) == ',')) {
                pos++;
            }
            if (pos >= len) break;

            if (s.charAt(pos) != '"') {
                pos++;
                continue;
            }
            pos++;
            int keyStart = pos;
            while (pos < len && s.charAt(pos) != '"') {
                if (s.charAt(pos) == '\\') pos++;
                pos++;
            }
            String key = s.substring(keyStart, pos);
            pos++; // skip closing quote

            while (pos < len && (Character.isWhitespace(s.charAt(pos)) || s.charAt(pos) == ':')) {
                pos++;
            }
            if (pos >= len) break;

            char firstValChar = s.charAt(pos);
            if (firstValChar == '[') {
                pos++;
                List<String> list = new ArrayList<>();
                while (pos < len) {
                    while (pos < len && (Character.isWhitespace(s.charAt(pos)) || s.charAt(pos) == ',')) {
                        pos++;
                    }
                    if (pos >= len || s.charAt(pos) == ']') {
                        if (pos < len && s.charAt(pos) == ']') pos++;
                        break;
                    }
                    if (s.charAt(pos) == '"') {
                        pos++;
                        StringBuilder item = new StringBuilder();
                        while (pos < len) {
                            char c = s.charAt(pos);
                            if (c == '\\' && pos + 1 < len) {
                                pos++;
                                char next = s.charAt(pos);
                                switch (next) {
                                    case '"': item.append('"'); break;
                                    case '\\': item.append('\\'); break;
                                    case 'n': item.append('\n'); break;
                                    case 'r': item.append('\r'); break;
                                    case 't': item.append('\t'); break;
                                    default: item.append(next); break;
                                }
                            } else if (c == '"') {
                                pos++;
                                break;
                            } else {
                                item.append(c);
                            }
                            pos++;
                        }
                        list.add(item.toString());
                    } else {
                        int elemStart = pos;
                        while (pos < len && s.charAt(pos) != ',' && s.charAt(pos) != ']') {
                            pos++;
                        }
                        String elem = s.substring(elemStart, pos).trim();
                        if (!elem.isEmpty()) {
                            list.add(elem);
                        }
                    }
                }
                map.put(key, list);
            } else if (firstValChar == '"') {
                pos++;
                StringBuilder valBuilder = new StringBuilder();
                while (pos < len) {
                    char c = s.charAt(pos);
                    if (c == '\\' && pos + 1 < len) {
                        pos++;
                        char next = s.charAt(pos);
                        switch (next) {
                            case '"': valBuilder.append('"'); break;
                            case '\\': valBuilder.append('\\'); break;
                            case 'n': valBuilder.append('\n'); break;
                            case 'r': valBuilder.append('\r'); break;
                            case 't': valBuilder.append('\t'); break;
                            case 'b': valBuilder.append('\b'); break;
                            case 'f': valBuilder.append('\f'); break;
                            case 'u':
                                if (pos + 4 < len) {
                                    String hex = s.substring(pos + 1, pos + 5);
                                    try {
                                        valBuilder.append((char) Integer.parseInt(hex, 16));
                                        pos += 4;
                                    } catch (Exception e) {
                                        valBuilder.append("\\u").append(hex);
                                    }
                                }
                                break;
                            default: valBuilder.append(next); break;
                        }
                    } else if (c == '"') {
                        pos++;
                        break;
                    } else {
                        valBuilder.append(c);
                    }
                    pos++;
                }
                map.put(key, valBuilder.toString());
            } else {
                int valStart = pos;
                while (pos < len && s.charAt(pos) != ',' && s.charAt(pos) != '}') {
                    pos++;
                }
                String rawVal = s.substring(valStart, pos).trim();
                if ("true".equalsIgnoreCase(rawVal)) {
                    map.put(key, Boolean.TRUE);
                } else if ("false".equalsIgnoreCase(rawVal)) {
                    map.put(key, Boolean.FALSE);
                } else {
                    try {
                        if (rawVal.contains(".")) {
                            map.put(key, Double.parseDouble(rawVal));
                        } else {
                            map.put(key, Long.parseLong(rawVal));
                        }
                    } catch (NumberFormatException e) {
                        map.put(key, rawVal);
                    }
                }
            }
        }
        return map;
    }

    // --- Handlers Básicos ---

    private static class PingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String json = "{\"status\":\"ok\",\"service\":\"antigravity-netbeans-bridge-suite\",\"version\":\"1.1.0\"}";
            sendJsonResponse(exchange, 200, json);
        }
    }

    private static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> debugStat = NbDebugService.getInstance().getStatus();
            Map<String, Object> map = new HashMap<>();
            map.put("status", "running");
            map.put("version", "1.1.0");
            map.put("port", PORT);
            map.put("suite", "Antigravity IDE Bridge Suite (NetBeans 18)");
            map.put("debugActive", debugStat.get("active"));
            map.put("openProjects", NbProjectService.getInstance().listProjects().size());
            sendJsonResponse(exchange, 200, toJson(map));
        }
    }

    private static class OpenHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"ok\":false,\"error\":\"Method Not Allowed\"}");
                return;
            }
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String file = (String) params.get("file");
            if (file == null && params.containsKey("filePath")) file = (String) params.get("filePath");
            if (file == null || file.trim().isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"Parâmetro 'file' é obrigatório\"}");
                return;
            }
            int line = 1;
            if (params.get("line") instanceof Number) line = ((Number) params.get("line")).intValue();

            try {
                boolean opened = NbEditorService.getInstance().openFileAtLine(file, line);
                if (opened) {
                    sendJsonResponse(exchange, 200, "{\"ok\":true,\"message\":\"Arquivo aberto no editor do NetBeans\"}");
                } else {
                    sendJsonResponse(exchange, 404, "{\"ok\":false,\"error\":\"Arquivo não encontrado no disco\"}");
                }
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class GetContentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"ok\":false,\"error\":\"Method Not Allowed\"}");
                return;
            }
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String file = (String) params.get("file");
            if (file == null && params.containsKey("filePath")) file = (String) params.get("filePath");
            if (file == null) {
                sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"Parâmetro 'file' é obrigatório\"}");
                return;
            }
            try {
                String content = NbEditorService.getInstance().getDocumentContent(file);
                String json = "{\"ok\":true,\"file\":\"" + escapeJson(file) + "\",\"length\":" + content.length() + ",\"content\":\"" + escapeJson(content) + "\"}";
                sendJsonResponse(exchange, 200, json);
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class EditHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"ok\":false,\"error\":\"Method Not Allowed\"}");
                return;
            }
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String file = (String) params.get("file");
            if (file == null && params.containsKey("filePath")) file = (String) params.get("filePath");
            String targetContent = (String) params.get("target_content");
            if (targetContent == null && params.containsKey("old_text")) targetContent = (String) params.get("old_text");
            String replacementContent = (String) params.get("replacement_content");
            if (replacementContent == null && params.containsKey("new_text")) replacementContent = (String) params.get("new_text");
            boolean allowMultiple = Boolean.TRUE.equals(params.get("allow_multiple"));

            if (file == null || targetContent == null || replacementContent == null) {
                sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"Parâmetros 'file', 'target_content' e 'replacement_content' são obrigatórios\"}");
                return;
            }
            try {
                NbEditorService.EditResult res = NbEditorService.getInstance().replaceExact(file, targetContent, replacementContent, allowMultiple);
                if (res.success) {
                    sendJsonResponse(exchange, 200, "{\"ok\":true,\"replaced\":" + res.replacementsCount + ",\"message\":\"" + escapeJson(res.message) + "\",\"documentLength\":" + res.documentLength + "}");
                } else {
                    sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"" + escapeJson(res.message) + "\"}");
                }
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class ReplaceLinesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"ok\":false,\"error\":\"Method Not Allowed\"}");
                return;
            }
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String file = (String) params.get("file");
            if (file == null && params.containsKey("filePath")) file = (String) params.get("filePath");
            String targetContent = (String) params.get("target_content");
            String replacementContent = (String) params.get("replacement_content");
            int startLine = (params.get("start_line") instanceof Number) ? ((Number) params.get("start_line")).intValue() : 1;
            int endLine = (params.get("end_line") instanceof Number) ? ((Number) params.get("end_line")).intValue() : Integer.MAX_VALUE;

            if (file == null || targetContent == null || replacementContent == null) {
                sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"Parâmetros 'file', 'target_content' e 'replacement_content' são obrigatórios\"}");
                return;
            }
            try {
                NbEditorService.EditResult res = NbEditorService.getInstance().replaceLineRange(file, startLine, endLine, targetContent, replacementContent);
                if (res.success) {
                    sendJsonResponse(exchange, 200, "{\"ok\":true,\"replaced\":" + res.replacementsCount + ",\"message\":\"" + escapeJson(res.message) + "\",\"documentLength\":" + res.documentLength + "}");
                } else {
                    sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"" + escapeJson(res.message) + "\"}");
                }
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class SetContentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"ok\":false,\"error\":\"Method Not Allowed\"}");
                return;
            }
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String file = (String) params.get("file");
            if (file == null && params.containsKey("filePath")) file = (String) params.get("filePath");
            String content = (String) params.get("content");
            if (file == null || content == null) {
                sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"Parâmetros 'file' e 'content' são obrigatórios\"}");
                return;
            }
            try {
                NbEditorService.EditResult res = NbEditorService.getInstance().setFullContent(file, content);
                if (res.success) {
                    sendJsonResponse(exchange, 200, "{\"ok\":true,\"message\":\"" + escapeJson(res.message) + "\",\"documentLength\":" + res.documentLength + "}");
                } else {
                    sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"" + escapeJson(res.message) + "\"}");
                }
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class OpenCommitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"ok\":false,\"error\":\"Method Not Allowed\"}");
                return;
            }
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            Object filesObj = params.get("files");
            List<String> filesList = new ArrayList<>();
            if (filesObj instanceof List) {
                for (Object o : (List<?>) filesObj) if (o != null) filesList.add(o.toString());
            } else if (filesObj instanceof String) {
                filesList.add((String) filesObj);
            } else if (params.containsKey("file")) {
                filesList.add((String) params.get("file"));
            }
            String message = (String) params.get("message");
            if (message == null && params.containsKey("msg")) message = (String) params.get("msg");

            if (filesList.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"Parâmetro 'files' é obrigatório\"}");
                return;
            }
            try {
                NbCommitService.CommitResult res = NbCommitService.getInstance().openCommitDialog(filesList, message);
                if (res.success) {
                    sendJsonResponse(exchange, 200, "{\"ok\":true,\"vcs\":\"" + escapeJson(res.vcs) + "\",\"filesCount\":" + res.filesCount + ",\"message\":\"" + escapeJson(res.message) + "\"}");
                } else {
                    sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"" + escapeJson(res.message) + "\"}");
                }
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    // --- JPDA Debug Handlers ---

    private static class DebugStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> map = NbDebugService.getInstance().getStatus();
            sendJsonResponse(exchange, 200, toJson(map));
        }
    }

    private static class DebugSetBreakpointHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String file = (String) params.get("file");
            if (file == null && params.containsKey("filePath")) file = (String) params.get("filePath");
            int line = (params.get("line") instanceof Number) ? ((Number) params.get("line")).intValue() : 1;
            String condition = (String) params.get("condition");

            if (file == null) {
                sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"Parâmetro 'file' é obrigatório\"}");
                return;
            }
            try {
                Map<String, Object> res = NbDebugService.getInstance().setBreakpoint(file, line, condition);
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class DebugRemoveBreakpointHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String id = (String) params.get("id");
            if (id == null && params.containsKey("breakpointId")) id = (String) params.get("breakpointId");
            String file = (String) params.get("file");
            Integer line = (params.get("line") instanceof Number) ? ((Number) params.get("line")).intValue() : null;

            try {
                Map<String, Object> res = NbDebugService.getInstance().removeBreakpoint(id, file, line);
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class DebugListBreakpointsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<Map<String, Object>> list = NbDebugService.getInstance().listBreakpoints();
            Map<String, Object> res = new HashMap<>();
            res.put("ok", true);
            res.put("count", list.size());
            res.put("breakpoints", list);
            sendJsonResponse(exchange, 200, toJson(res));
        }
    }

    private static class DebugControlHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String action = (String) params.get("action");
            if (action == null && params.containsKey("command")) action = (String) params.get("command");
            try {
                Map<String, Object> res = NbDebugService.getInstance().control(action);
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class DebugStackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String thread = (String) params.get("thread");
            if (thread == null && params.containsKey("threadName")) thread = (String) params.get("threadName");
            try {
                Map<String, Object> res = NbDebugService.getInstance().getCallStack(thread);
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class DebugVariablesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            int frame = (params.get("frame") instanceof Number) ? ((Number) params.get("frame")).intValue() : 0;
            if (params.containsKey("frameIndex") && params.get("frameIndex") instanceof Number) {
                frame = ((Number) params.get("frameIndex")).intValue();
            }
            int depth = (params.get("depth") instanceof Number) ? ((Number) params.get("depth")).intValue() : 2;

            try {
                Map<String, Object> res = NbDebugService.getInstance().getVariables(frame, depth);
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class DebugEvalHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String expr = (String) params.get("expression");
            if (expr == null && params.containsKey("expr")) expr = (String) params.get("expr");
            Integer frame = (params.get("frame") instanceof Number) ? ((Number) params.get("frame")).intValue() : null;

            try {
                Map<String, Object> res = NbDebugService.getInstance().evaluate(expr, frame);
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    // --- Output & Console Handlers ---

    private static class OutputListTabsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<Map<String, Object>> tabs = NbOutputService.getInstance().listTabs();
            Map<String, Object> res = new HashMap<>();
            res.put("ok", true);
            res.put("count", tabs.size());
            res.put("tabs", tabs);
            sendJsonResponse(exchange, 200, toJson(res));
        }
    }

    private static class OutputReadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String tab = (String) params.get("tab");
            if (tab == null && params.containsKey("tabName")) tab = (String) params.get("tabName");
            int since = (params.get("since_line") instanceof Number) ? ((Number) params.get("since_line")).intValue() : 0;
            int max = (params.get("max_lines") instanceof Number) ? ((Number) params.get("max_lines")).intValue() : 500;

            try {
                Map<String, Object> res = NbOutputService.getInstance().getTabLines(tab, since, max);
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class OutputClearHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String tab = (String) params.get("tab");
            if (tab == null && params.containsKey("tabName")) tab = (String) params.get("tabName");

            try {
                Map<String, Object> res = NbOutputService.getInstance().clearTab(tab);
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    // --- Diagnostics & AST Handlers ---

    private static class DiagnosticsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String file = (String) params.get("file");
            if (file == null && params.containsKey("filePath")) file = (String) params.get("filePath");
            if (file == null) {
                sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"Parâmetro 'file' é obrigatório\"}");
                return;
            }
            try {
                Map<String, Object> res = NbDiagnosticsService.getInstance().getDiagnostics(file);
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class AstHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String file = (String) params.get("file");
            if (file == null && params.containsKey("filePath")) file = (String) params.get("filePath");
            int detail = (params.get("detail_level") instanceof Number) ? ((Number) params.get("detail_level")).intValue() : 1;

            if (file == null) {
                sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"Parâmetro 'file' é obrigatório\"}");
                return;
            }
            try {
                Map<String, Object> res = NbDiagnosticsService.getInstance().getAstStructure(file, detail);
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    // --- Projects & Actions Handlers ---

    private static class ProjectListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<Map<String, Object>> list = NbProjectService.getInstance().listProjects();
            Map<String, Object> res = new HashMap<>();
            res.put("ok", true);
            res.put("count", list.size());
            res.put("projects", list);
            sendJsonResponse(exchange, 200, toJson(res));
        }
    }

    private static class ProjectOpenHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String dir = (String) params.get("path");
            if (dir == null && params.containsKey("project_path")) dir = (String) params.get("project_path");
            if (dir == null && params.containsKey("directory")) dir = (String) params.get("directory");

            if (dir == null) {
                sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"Parâmetro 'path' é obrigatório\"}");
                return;
            }
            try {
                Map<String, Object> res = NbProjectService.getInstance().openProject(dir);
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class ProjectActionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String project = (String) params.get("project");
            if (project == null && params.containsKey("project_path")) project = (String) params.get("project_path");
            String action = (String) params.get("action");
            if (action == null && params.containsKey("command")) action = (String) params.get("command");
            String file = (String) params.get("file");
            if (file == null && params.containsKey("target_file")) file = (String) params.get("target_file");

            try {
                Map<String, Object> res = NbProjectService.getInstance().runProjectAction(project, action, file);
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class InvokeActionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String actionId = (String) params.get("action_id");
            if (actionId == null && params.containsKey("id")) actionId = (String) params.get("id");
            String category = (String) params.get("category");

            if (actionId == null) {
                sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"Parâmetro 'action_id' é obrigatório\"}");
                return;
            }
            try {
                Map<String, Object> res = NbProjectService.getInstance().invokeGlobalAction(category, actionId);
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }
}
