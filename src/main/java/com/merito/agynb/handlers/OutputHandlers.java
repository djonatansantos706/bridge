package com.merito.agynb.handlers;

import com.merito.agynb.NbOutputService;
import com.merito.agynb.core.AbstractJsonHandler;
import com.merito.agynb.core.BridgeResponse;
import com.sun.net.httpserver.HttpExchange;
import java.util.List;
import java.util.Map;

/**
 * Handlers HTTP para o console de saída e gerenciamento de abas do NetBeans Output.
 */
public final class OutputHandlers {

    private OutputHandlers() {
    }

    public static class OutputTabsHandler extends AbstractJsonHandler {
        public OutputTabsHandler() {
            super(false);
        }

        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            List<Map<String, Object>> list = NbOutputService.getInstance().listTabs();
            return BridgeResponse.ok()
                    .put("count", list.size())
                    .put("tabs", list);
        }
    }

    public static class OutputReadHandler extends AbstractJsonHandler {
        public OutputReadHandler() {
            super(false);
        }

        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String tab = getStringParam(params, "tab", "tabName");
            int sinceLine = getIntParam(params, 0, "since_line");
            int maxLines = getIntParam(params, 500, "max_lines");
            String filter = getStringParam(params, "filter");
            boolean caseSensitive = getBoolParam(params, false, "case_sensitive");

            return BridgeResponse.of(NbOutputService.getInstance().getTabLines(tab, sinceLine, maxLines, filter, caseSensitive));
        }
    }

    public static class OutputClearHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String tab = getStringParam(params, "tab", "tabName");
            return BridgeResponse.of(NbOutputService.getInstance().clearTab(tab));
        }
    }
}
