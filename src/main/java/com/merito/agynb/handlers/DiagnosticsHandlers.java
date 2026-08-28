package com.merito.agynb.handlers;

import com.merito.agynb.NbDiagnosticsService;
import com.merito.agynb.core.AbstractJsonHandler;
import com.merito.agynb.core.BridgeResponse;
import com.sun.net.httpserver.HttpExchange;
import java.util.Map;

/**
 * Handlers HTTP para diagnósticos AST, inspeção estrutural e navegação semântica.
 */
public final class DiagnosticsHandlers {

    private DiagnosticsHandlers() {
    }

    public static class DiagnosticsHandler extends AbstractJsonHandler {
        public DiagnosticsHandler() {
            super(false);
        }

        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String file = getStringParam(params, "file", "filePath");
            if (file == null) {
                throw new IllegalArgumentException("Parâmetro 'file' é obrigatório.");
            }
            return BridgeResponse.of(NbDiagnosticsService.getInstance().getDiagnostics(file));
        }
    }

    public static class AstHandler extends AbstractJsonHandler {
        public AstHandler() {
            super(false);
        }

        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String file = getStringParam(params, "file", "filePath");
            int detail = getIntParam(params, 1, "detail_level");
            if (file == null) {
                throw new IllegalArgumentException("Parâmetro 'file' é obrigatório.");
            }
            return BridgeResponse.of(NbDiagnosticsService.getInstance().getAstStructure(file, detail));
        }
    }

    public static class GotoDefinitionHandler extends AbstractJsonHandler {
        public GotoDefinitionHandler() {
            super(false);
        }

        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String file = getStringParam(params, "file", "filePath");
            int line = getIntParam(params, 1, "line");
            int col = getIntParam(params, 1, "column");
            String symbol = getStringParam(params, "symbol", "symbolName");

            if (file == null) {
                throw new IllegalArgumentException("Parâmetro 'file' é obrigatório.");
            }
            return BridgeResponse.of(NbDiagnosticsService.getInstance().gotoDefinition(file, line, col, symbol));
        }
    }

    public static class FindUsagesHandler extends AbstractJsonHandler {
        public FindUsagesHandler() {
            super(false);
        }

        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String file = getStringParam(params, "file", "filePath");
            String symbol = getStringParam(params, "symbol", "symbolName");

            if (file == null || symbol == null || symbol.trim().isEmpty()) {
                throw new IllegalArgumentException("Parâmetros 'file' e 'symbol' são obrigatórios.");
            }
            return BridgeResponse.of(NbDiagnosticsService.getInstance().findUsages(file, symbol));
        }
    }
}
