package com.merito.agynb;

import com.merito.agynb.core.BridgeConstants;
import com.merito.agynb.core.BridgeResponse;
import com.merito.agynb.core.BridgeToken;
import com.merito.agynb.core.AbstractJsonHandler;
import com.merito.agynb.handlers.DebugHandlers;
import com.merito.agynb.handlers.DiagnosticsHandlers;
import com.merito.agynb.handlers.EditorHandlers;
import com.merito.agynb.handlers.OutputHandlers;
import com.merito.agynb.handlers.ProjectHandlers;
import com.merito.agynb.handlers.FormHandlers;
import com.merito.agynb.core.BridgeLog;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.awt.StatusDisplayer;

/**
 * Servidor HTTP embutido da Antigravity NetBeans Bridge Suite.
 * Responsável estritamente pelo ciclo de vida da porta 8388 e roteamento REST.
 */
public class AgyBridgeServer {

    private static final Logger LOG = Logger.getLogger(AgyBridgeServer.class.getName());
    private static final AgyBridgeServer INSTANCE = new AgyBridgeServer();

    private HttpServer server;
    private ExecutorService executor;
    private boolean running = false;

    public static AgyBridgeServer getInstance() {
        return INSTANCE;
    }

    private AgyBridgeServer() {
    }

    public int getPort() {
        return BridgeConstants.DEFAULT_PORT;
    }

    public synchronized void start() {
        if (running) {
            return;
        }

        try {
            // Garante que o token exista em disco antes de qualquer cliente conectar
            BridgeToken.getOrCreate();

            server = HttpServer.create(new InetSocketAddress(BridgeConstants.DEFAULT_HOST, BridgeConstants.DEFAULT_PORT), 0);
            executor = Executors.newCachedThreadPool();
            server.setExecutor(executor);

            // Core & Status
            server.createContext("/ping", new PingHandler());
            server.createContext("/status", new StatusHandler());

            // Editor & Buffers
            server.createContext("/open", new EditorHandlers.OpenHandler());
            server.createContext("/get-content", new EditorHandlers.GetContentHandler());
            server.createContext("/edit", new EditorHandlers.EditHandler());
            server.createContext("/replace-lines", new EditorHandlers.ReplaceLinesHandler());
            server.createContext("/set-content", new EditorHandlers.SetContentHandler());
            server.createContext("/save", new EditorHandlers.SaveDocumentHandler());
            server.createContext("/revert", new EditorHandlers.RevertDocumentHandler());
            server.createContext("/format", new EditorHandlers.FormatCodeHandler());
            server.createContext("/get-selection", new EditorHandlers.GetSelectionHandler());
            server.createContext("/set-selection", new EditorHandlers.SetSelectionHandler());
            server.createContext("/commit", new EditorHandlers.CommitHandler());
            server.createContext("/open-commit", new EditorHandlers.CommitHandler());
            server.createContext("/create-folder", new EditorHandlers.CreateFolderHandler());
            server.createContext("/create-file", new EditorHandlers.CreateFileHandler());

            // JPDA Debugger
            server.createContext("/debug/status", new DebugHandlers.DebugStatusHandler());
            server.createContext("/debug/set-breakpoint", new DebugHandlers.DebugSetBreakpointHandler());
            server.createContext("/debug/remove-breakpoint", new DebugHandlers.DebugRemoveBreakpointHandler());
            server.createContext("/debug/list-breakpoints", new DebugHandlers.DebugListBreakpointsHandler());
            server.createContext("/debug/control", new DebugHandlers.DebugControlHandler());
            server.createContext("/debug/stack", new DebugHandlers.DebugStackHandler());
            server.createContext("/debug/variables", new DebugHandlers.DebugVariablesHandler());
            server.createContext("/debug/eval", new DebugHandlers.DebugEvalHandler());
            server.createContext("/debug/watches/add", new DebugHandlers.DebugAddWatchHandler());
            server.createContext("/debug/watches/list", new DebugHandlers.DebugListWatchesHandler());
            server.createContext("/debug/watches/remove", new DebugHandlers.DebugRemoveWatchHandler());
            server.createContext("/debug/last-exception", new DebugHandlers.DebugLastExceptionHandler());

            // Output Console
            server.createContext("/output/tabs", new OutputHandlers.OutputTabsHandler());
            server.createContext("/output/read", new OutputHandlers.OutputReadHandler());
            server.createContext("/output/clear", new OutputHandlers.OutputClearHandler());

            // Diagnostics & AST & Semantic Navigation
            server.createContext("/diagnostics", new DiagnosticsHandlers.DiagnosticsHandler());
            server.createContext("/ast", new DiagnosticsHandlers.AstHandler());
            server.createContext("/goto-definition", new DiagnosticsHandlers.GotoDefinitionHandler());
            server.createContext("/find-usages", new DiagnosticsHandlers.FindUsagesHandler());

            // Projects & Global Actions
            server.createContext("/projects/list", new ProjectHandlers.ProjectListHandler());
            server.createContext("/projects/open", new ProjectHandlers.ProjectOpenHandler());
            server.createContext("/projects/action", new ProjectHandlers.ProjectActionHandler());
            server.createContext("/invoke", new ProjectHandlers.InvokeActionHandler());

            // Swing Forms & Matisse Engine
            server.createContext("/form/inspect", new FormHandlers.FormInspectHandler());
            server.createContext("/form/set-property", new FormHandlers.FormSetPropertyHandler());
            server.createContext("/form/create-blueprint", new FormHandlers.FormCreateBlueprintHandler());

            server.start();
            running = true;
            LOG.info("[Antigravity] Bridge Suite v" + BridgeConstants.VERSION + " iniciada na porta " + BridgeConstants.DEFAULT_PORT);
            StatusDisplayer.getDefault().setStatusText("[Antigravity] Bridge Suite v" + BridgeConstants.VERSION + " ativa na porta " + BridgeConstants.DEFAULT_PORT);
            BridgeLog.event("Bridge Suite v" + BridgeConstants.VERSION + " ativa na porta " + BridgeConstants.DEFAULT_PORT);

        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "[Antigravity] Falha ao iniciar servidor HTTP na porta " + BridgeConstants.DEFAULT_PORT, ex);
        }
    }

    public synchronized void stop() {
        if (!running || server == null) {
            return;
        }
        try {
            server.stop(1);
            if (executor != null) {
                executor.shutdown();
                executor = null;
            }
            running = false;
            LOG.info("[Antigravity] Bridge Suite finalizada.");
            StatusDisplayer.getDefault().setStatusText("[Antigravity] Bridge finalizada.");
            BridgeLog.event("Bridge PAUSADA — nenhum agente consegue acessar a IDE até retomar");
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Erro ao parar servidor HTTP", ex);
        }
    }

    public boolean isRunning() {
        return running;
    }

    // --- Handlers de Status & Ping ---

    private static class PingHandler extends AbstractJsonHandler {
        public PingHandler() {
            // Sem POST e sem token: /ping serve só para diagnóstico de conectividade
            super(false, false);
        }

        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) {
            return BridgeResponse.ok()
                    .put("status", "ok")
                    .put("service", BridgeConstants.SERVICE_NAME)
                    .put("version", BridgeConstants.VERSION);
        }
    }

    private static class StatusHandler extends AbstractJsonHandler {
        public StatusHandler() {
            super(false);
        }

        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) {
            BridgeResponse resp = BridgeResponse.ok()
                    .put("service", BridgeConstants.SERVICE_NAME)
                    .put("version", BridgeConstants.VERSION)
                    .put("status", "running")
                    .put("port", BridgeConstants.DEFAULT_PORT)
                    .put("debugger_active", NbDebugService.getInstance().getCurrentDebugger() != null);
            try {
                resp.put("projects", NbProjectService.getInstance().listProjects());
            } catch (Exception ignored) {
            }
            return resp;
        }
    }
}
