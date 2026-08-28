package com.merito.agynb.handlers;

import com.merito.agynb.NbDebugService;
import com.merito.agynb.core.AbstractJsonHandler;
import com.merito.agynb.core.BridgeResponse;
import com.sun.net.httpserver.HttpExchange;
import java.util.List;
import java.util.Map;

/**
 * Handlers HTTP para o depurador JPDA (Breakpoints, Stack, Variables, Eval, Watches, Exceptions).
 */
public final class DebugHandlers {

    private DebugHandlers() {
    }

    public static class DebugStatusHandler extends AbstractJsonHandler {
        public DebugStatusHandler() {
            super(false);
        }

        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            return BridgeResponse.of(NbDebugService.getInstance().getStatus());
        }
    }

    public static class DebugSetBreakpointHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String file = getStringParam(params, "file", "filePath");
            int line = getIntParam(params, -1, "line");
            String condition = getStringParam(params, "condition");

            if (file == null || line <= 0) {
                throw new IllegalArgumentException("Parâmetros 'file' e 'line' (>0) são obrigatórios.");
            }
            return BridgeResponse.of(NbDebugService.getInstance().setBreakpoint(file, line, condition));
        }
    }

    public static class DebugRemoveBreakpointHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String id = getStringParam(params, "id", "breakpointId");
            String file = getStringParam(params, "file");
            Integer line = getIntParam(params, null, "line");

            return BridgeResponse.of(NbDebugService.getInstance().removeBreakpoint(id, file, line));
        }
    }

    public static class DebugListBreakpointsHandler extends AbstractJsonHandler {
        public DebugListBreakpointsHandler() {
            super(false);
        }

        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            List<Map<String, Object>> list = NbDebugService.getInstance().listBreakpoints();
            return BridgeResponse.ok()
                    .put("count", list.size())
                    .put("breakpoints", list);
        }
    }

    public static class DebugControlHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String action = getStringParam(params, "action", "command");
            return BridgeResponse.of(NbDebugService.getInstance().control(action));
        }
    }

    public static class DebugStackHandler extends AbstractJsonHandler {
        public DebugStackHandler() {
            super(false);
        }

        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String thread = getStringParam(params, "thread", "threadName");
            return BridgeResponse.of(NbDebugService.getInstance().getCallStack(thread));
        }
    }

    public static class DebugVariablesHandler extends AbstractJsonHandler {
        public DebugVariablesHandler() {
            super(false);
        }

        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            int frame = getIntParam(params, 0, "frame", "frameIndex");
            int depth = getIntParam(params, 2, "depth");
            return BridgeResponse.of(NbDebugService.getInstance().getVariables(frame, depth));
        }
    }

    public static class DebugEvalHandler extends AbstractJsonHandler {
        public DebugEvalHandler() {
            super(false);
        }

        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String expr = getStringParam(params, "expression", "expr");
            Integer frame = getIntParam(params, null, "frame", "frameIndex");
            return BridgeResponse.of(NbDebugService.getInstance().evaluate(expr, frame));
        }
    }

    public static class DebugAddWatchHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String expr = getStringParam(params, "expression", "expr");
            if (expr == null || expr.trim().isEmpty()) {
                throw new IllegalArgumentException("Parâmetro 'expression' é obrigatório.");
            }
            return BridgeResponse.of(NbDebugService.getInstance().addWatch(expr));
        }
    }

    public static class DebugListWatchesHandler extends AbstractJsonHandler {
        public DebugListWatchesHandler() {
            super(false);
        }

        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            List<Map<String, Object>> list = NbDebugService.getInstance().listWatches();
            return BridgeResponse.ok()
                    .put("count", list.size())
                    .put("watches", list);
        }
    }

    public static class DebugRemoveWatchHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String id = getStringParam(params, "id", "expression");
            if (id == null) {
                throw new IllegalArgumentException("Parâmetro 'id' ou 'expression' é obrigatório.");
            }
            return BridgeResponse.of(NbDebugService.getInstance().removeWatch(id));
        }
    }

    public static class DebugLastExceptionHandler extends AbstractJsonHandler {
        public DebugLastExceptionHandler() {
            super(false);
        }

        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            return BridgeResponse.of(NbDebugService.getInstance().getLastException());
        }
    }
}
