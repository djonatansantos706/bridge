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

    public int getPort() {
        return PORT;
    }

    public synchronized void start() {
        if (running) {
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            server.setExecutor(Executors.newCachedThreadPool());

            // Core & Status
            server.createContext("/ping", new PingHandler());
            server.createContext("/status", new StatusHandler());

            // Editor & Buffers
            server.createContext("/open", new OpenHandler());
            server.createContext("/get-content", new GetContentHandler());
            server.createContext("/edit", new EditHandler());
            server.createContext("/replace-lines", new ReplaceLinesHandler());
            server.createContext("/set-content", new SetContentHandler());
            server.createContext("/save", new SaveDocumentHandler());
            server.createContext("/revert", new RevertDocumentHandler());
            server.createContext("/format", new FormatCodeHandler());
            server.createContext("/get-selection", new GetSelectionHandler());
            server.createContext("/set-selection", new SetSelectionHandler());
            server.createContext("/commit", new CommitHandler());
            server.createContext("/open-commit", new CommitHandler());

            // JPDA Debugger
            server.createContext("/debug/status", new DebugStatusHandler());
            server.createContext("/debug/set-breakpoint", new DebugSetBreakpointHandler());
            server.createContext("/debug/remove-breakpoint", new DebugRemoveBreakpointHandler());
            server.createContext("/debug/list-breakpoints", new DebugListBreakpointsHandler());
            server.createContext("/debug/control", new DebugControlHandler());
            server.createContext("/debug/stack", new DebugStackHandler());
            server.createContext("/debug/variables", new DebugVariablesHandler());
            server.createContext("/debug/eval", new DebugEvalHandler());
            server.createContext("/debug/watches/add", new DebugAddWatchHandler());
            server.createContext("/debug/watches/list", new DebugListWatchesHandler());
            server.createContext("/debug/watches/remove", new DebugRemoveWatchHandler());
            server.createContext("/debug/last-exception", new DebugLastExceptionHandler());

            // Output Console
            server.createContext("/output/tabs", new OutputTabsHandler());
            server.createContext("/output/read", new OutputReadHandler());
            server.createContext("/output/clear", new OutputClearHandler());

            // Diagnostics & AST & Semantic Navigation
            server.createContext("/diagnostics", new DiagnosticsHandler());
            server.createContext("/ast", new AstHandler());
            server.createContext("/goto-definition", new GotoDefinitionHandler());
            server.createContext("/find-usages", new FindUsagesHandler());

            // Projects & Global Actions
            server.createContext("/projects/list", new ProjectListHandler());
            server.createContext("/projects/open", new ProjectOpenHandler());
            server.createContext("/projects/action", new ProjectActionHandler());
            server.createContext("/invoke", new InvokeActionHandler());

            server.start();
            running = true;
            LOG.info("[Antigravity] Bridge Suite v1.2.0 iniciada com sucesso na porta " + PORT);
            StatusDisplayer.getDefault().setStatusText("[Antigravity] Bridge Suite v1.2.0 ativa na porta " + PORT);

        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "[Antigravity] Falha ao iniciar servidor HTTP na porta " + PORT, ex);
        }
    }

    public synchronized void stop() {
        if (!running || server == null) {
            return;
        }
        try {
            server.stop(1);
            running = false;
            LOG.info("[Antigravity] Bridge Suite finalizada.");
            StatusDisplayer.getDefault().setStatusText("[Antigravity] Bridge finalizada.");
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Erro ao parar servidor HTTP", ex);
        }
    }

    public boolean isRunning() {
        return running;
    }

    // --- JSON & HTTP Utilities ---

    public static String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = is.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    public static void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonResponse) throws IOException {
        byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Bridge-Token");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    public static Map<String, Object> parseSimpleJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            return JsonUtils.parseObject(json);
        } catch (Exception e) {
            LOG.log(Level.FINE, "Erro no parse JSON via JsonUtils: " + e.getMessage());
            return new HashMap<>();
        }
    }

    public static String escapeJson(String s) {
        return JsonUtils.escapeJson(s);
    }

    public static String toJson(Object obj) {
        return JsonUtils.toJson(obj);
    }

    // --- Handlers: Core & Editor ---

    private static class PingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendJsonResponse(exchange, 200, "{\"status\":\"ok\",\"service\":\"antigravity-netbeans-bridge\",\"version\":\"1.2.0\"}");
        }
    }

    private static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> res = new HashMap<>();
            res.put("ok", true);
            res.put("service", "antigravity-netbeans-bridge");
            res.put("version", "1.2.0");
            res.put("status", "running");
            res.put("port", PORT);
            res.put("debugger_active", NbDebugService.getInstance().getCurrentDebugger() != null);
            try {
                res.put("projects", NbProjectService.getInstance().listProjects());
            } catch (Exception ignored) {
            }
            sendJsonResponse(exchange, 200, toJson(res));
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
                Map<String, Object> res = new HashMap<>();
                res.put("ok", true);
                res.put("file", file);
                res.put("length", content.length());
                res.put("content", content);
                sendJsonResponse(exchange, 200, toJson(res));
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
            String oldText = (String) params.get("old_text");
            String newText = (String) params.get("new_text");
            boolean allowMultiple = Boolean.TRUE.equals(params.get("allow_multiple"));

            if (file == null || oldText == null || newText == null) {
                sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"Parâmetros 'file', 'old_text' e 'new_text' são obrigatórios\"}");
                return;
            }
            try {
                boolean success = NbEditorService.getInstance().editBuffer(file, oldText, newText, allowMultiple);
                sendJsonResponse(exchange, 200, "{\"ok\":" + success + ",\"message\":\"Buffer do NetBeans atualizado com sucesso (marcação * pendente de salvar)\"}");
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
            int startLine = (params.get("start_line") instanceof Number) ? ((Number) params.get("start_line")).intValue() : -1;
            int endLine = (params.get("end_line") instanceof Number) ? ((Number) params.get("end_line")).intValue() : -1;
            String targetContent = (String) params.get("target_content");
            String replacementContent = (String) params.get("replacement_content");

            if (file == null || startLine <= 0 || endLine < startLine || targetContent == null || replacementContent == null) {
                sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"Parâmetros inválidos para replace-lines\"}");
                return;
            }
            try {
                boolean success = NbEditorService.getInstance().replaceLines(file, startLine, endLine, targetContent, replacementContent);
                sendJsonResponse(exchange, 200, "{\"ok\":" + success + ",\"message\":\"Linhas substituídas com sucesso no buffer do NetBeans\"}");
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
            String content = (String) params.get("content");
            if (file == null || content == null) {
                sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"Parâmetros 'file' e 'content' são obrigatórios\"}");
                return;
            }
            try {
                boolean success = NbEditorService.getInstance().setFullBuffer(file, content);
                sendJsonResponse(exchange, 200, "{\"ok\":" + success + ",\"message\":\"Conteúdo completo do buffer substituído com sucesso\"}");
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class SaveDocumentHandler implements HttpHandler {
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
                boolean saved = NbEditorService.getInstance().saveDocument(file);
                sendJsonResponse(exchange, 200, "{\"ok\":" + saved + ",\"message\":\"Documento salvo no disco com sucesso\"}");
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class RevertDocumentHandler implements HttpHandler {
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
                boolean reverted = NbEditorService.getInstance().revertDocument(file);
                sendJsonResponse(exchange, 200, "{\"ok\":" + reverted + ",\"message\":\"Buffer revertido a partir do disco com sucesso\"}");
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class FormatCodeHandler implements HttpHandler {
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
            Integer startLine = (params.get("start_line") instanceof Number) ? ((Number) params.get("start_line")).intValue() : null;
            Integer endLine = (params.get("end_line") instanceof Number) ? ((Number) params.get("end_line")).intValue() : null;

            try {
                boolean formatted = NbEditorService.getInstance().formatCode(file, startLine, endLine);
                sendJsonResponse(exchange, 200, "{\"ok\":" + formatted + ",\"message\":\"Código formatado com sucesso no padrão NetBeans\"}");
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class GetSelectionHandler implements HttpHandler {
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
                Map<String, Object> res = NbEditorService.getInstance().getSelection(file);
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class SetSelectionHandler implements HttpHandler {
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
            int startLine = (params.get("start_line") instanceof Number) ? ((Number) params.get("start_line")).intValue() : 1;
            int startCol = (params.get("start_column") instanceof Number) ? ((Number) params.get("start_column")).intValue() : 1;
            int endLine = (params.get("end_line") instanceof Number) ? ((Number) params.get("end_line")).intValue() : startLine;
            int endCol = (params.get("end_column") instanceof Number) ? ((Number) params.get("end_column")).intValue() : startCol;

            try {
                boolean success = NbEditorService.getInstance().setSelection(file, startLine, startCol, endLine, endCol);
                sendJsonResponse(exchange, 200, "{\"ok\":" + success + ",\"message\":\"Seleção aplicada no editor do NetBeans\"}");
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class CommitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"ok\":false,\"error\":\"Method Not Allowed\"}");
                return;
            }
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            List<String> files = new ArrayList<>();
            if (params.get("files") instanceof List) {
                for (Object item : (List<?>) params.get("files")) {
                    if (item instanceof String) files.add((String) item);
                }
            } else if (params.get("file") instanceof String) {
                files.add((String) params.get("file"));
            }
            String message = (String) params.get("message");
            try {
                NbCommitService.CommitResult res = NbCommitService.getInstance().openCommitDialog(files, message);
                Map<String, Object> map = new HashMap<>();
                map.put("ok", res.success);
                map.put("vcs", res.vcs);
                map.put("filesCount", res.filesCount);
                map.put("message", res.message);
                sendJsonResponse(exchange, 200, toJson(map));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    // --- Handlers: JPDA Debugger ---

    private static class DebugStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                Map<String, Object> res = NbDebugService.getInstance().getStatus();
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class DebugSetBreakpointHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"ok\":false,\"error\":\"Method Not Allowed\"}");
                return;
            }
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String file = (String) params.get("file");
            if (file == null && params.containsKey("filePath")) file = (String) params.get("filePath");
            int line = (params.get("line") instanceof Number) ? ((Number) params.get("line")).intValue() : -1;
            String condition = (String) params.get("condition");

            if (file == null || line <= 0) {
                sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"Parâmetros 'file' e 'line' (>0) são obrigatórios\"}");
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
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"ok\":false,\"error\":\"Method Not Allowed\"}");
                return;
            }
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
            try {
                List<Map<String, Object>> list = NbDebugService.getInstance().listBreakpoints();
                Map<String, Object> res = new HashMap<>();
                res.put("ok", true);
                res.put("count", list.size());
                res.put("breakpoints", list);
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class DebugControlHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"ok\":false,\"error\":\"Method Not Allowed\"}");
                return;
            }
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

    private static class DebugAddWatchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"ok\":false,\"error\":\"Method Not Allowed\"}");
                return;
            }
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String expr = (String) params.get("expression");
            if (expr == null && params.containsKey("expr")) expr = (String) params.get("expr");
            if (expr == null || expr.trim().isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"Parâmetro 'expression' é obrigatório\"}");
                return;
            }
            try {
                Map<String, Object> res = NbDebugService.getInstance().addWatch(expr);
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class DebugListWatchesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                List<Map<String, Object>> list = NbDebugService.getInstance().listWatches();
                Map<String, Object> res = new HashMap<>();
                res.put("ok", true);
                res.put("count", list.size());
                res.put("watches", list);
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class DebugRemoveWatchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"ok\":false,\"error\":\"Method Not Allowed\"}");
                return;
            }
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String id = (String) params.get("id");
            if (id == null && params.containsKey("expression")) id = (String) params.get("expression");
            if (id == null) {
                sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"Parâmetro 'id' ou 'expression' é obrigatório\"}");
                return;
            }
            try {
                Map<String, Object> res = NbDebugService.getInstance().removeWatch(id);
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class DebugLastExceptionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                Map<String, Object> res = NbDebugService.getInstance().getLastException();
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    // --- Handlers: Output Console ---

    private static class OutputTabsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                List<Map<String, Object>> list = NbOutputService.getInstance().listTabs();
                Map<String, Object> res = new HashMap<>();
                res.put("ok", true);
                res.put("count", list.size());
                res.put("tabs", list);
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class OutputReadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String tab = (String) params.get("tab");
            if (tab == null && params.containsKey("tabName")) tab = (String) params.get("tabName");
            int sinceLine = (params.get("since_line") instanceof Number) ? ((Number) params.get("since_line")).intValue() : 0;
            int maxLines = (params.get("max_lines") instanceof Number) ? ((Number) params.get("max_lines")).intValue() : 500;
            String filter = (String) params.get("filter");
            boolean caseSensitive = Boolean.TRUE.equals(params.get("case_sensitive"));

            try {
                Map<String, Object> res = NbOutputService.getInstance().getTabLines(tab, sinceLine, maxLines, filter, caseSensitive);
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

    // --- Handlers: Diagnostics & AST & Semantic Navigation ---

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

    private static class GotoDefinitionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String file = (String) params.get("file");
            if (file == null && params.containsKey("filePath")) file = (String) params.get("filePath");
            int line = (params.get("line") instanceof Number) ? ((Number) params.get("line")).intValue() : 1;
            int col = (params.get("column") instanceof Number) ? ((Number) params.get("column")).intValue() : 1;
            String symbol = (String) params.get("symbol");
            if (symbol == null && params.containsKey("symbolName")) symbol = (String) params.get("symbolName");

            if (file == null) {
                sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"Parâmetro 'file' é obrigatório\"}");
                return;
            }
            try {
                Map<String, Object> res = NbDiagnosticsService.getInstance().gotoDefinition(file, line, col, symbol);
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class FindUsagesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String file = (String) params.get("file");
            if (file == null && params.containsKey("filePath")) file = (String) params.get("filePath");
            String symbol = (String) params.get("symbol");
            if (symbol == null && params.containsKey("symbolName")) symbol = (String) params.get("symbolName");

            if (file == null || symbol == null || symbol.trim().isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"Parâmetros 'file' e 'symbol' são obrigatórios\"}");
                return;
            }
            try {
                Map<String, Object> res = NbDiagnosticsService.getInstance().findUsages(file, symbol);
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    // --- Handlers: Projects & Invoker ---

    private static class ProjectListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                List<Map<String, Object>> list = NbProjectService.getInstance().listProjects();
                Map<String, Object> res = new HashMap<>();
                res.put("ok", true);
                res.put("count", list.size());
                res.put("projects", list);
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class ProjectOpenHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"ok\":false,\"error\":\"Method Not Allowed\"}");
                return;
            }
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String path = (String) params.get("path");
            if (path == null && params.containsKey("projectPath")) path = (String) params.get("projectPath");

            if (path == null) {
                sendJsonResponse(exchange, 400, "{\"ok\":false,\"error\":\"Parâmetro 'path' é obrigatório\"}");
                return;
            }
            try {
                Map<String, Object> res = NbProjectService.getInstance().openProject(path);
                sendJsonResponse(exchange, 200, toJson(res));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"ok\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private static class ProjectActionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"ok\":false,\"error\":\"Method Not Allowed\"}");
                return;
            }
            Map<String, Object> params = parseSimpleJson(readRequestBody(exchange));
            String project = (String) params.get("project");
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
