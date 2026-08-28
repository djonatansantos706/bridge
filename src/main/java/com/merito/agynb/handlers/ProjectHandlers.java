package com.merito.agynb.handlers;

import com.merito.agynb.NbProjectService;
import com.merito.agynb.core.AbstractJsonHandler;
import com.merito.agynb.core.BridgeResponse;
import com.sun.net.httpserver.HttpExchange;
import java.util.List;
import java.util.Map;

/**
 * Handlers HTTP para inventário, ciclo de vida de projetos e invocador de ações da IDE.
 */
public final class ProjectHandlers {

    private ProjectHandlers() {
    }

    public static class ProjectListHandler extends AbstractJsonHandler {
        public ProjectListHandler() {
            super(false);
        }

        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            List<Map<String, Object>> list = NbProjectService.getInstance().listProjects();
            return BridgeResponse.ok()
                    .put("count", list.size())
                    .put("projects", list);
        }
    }

    public static class ProjectOpenHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String path = getStringParam(params, "path", "projectPath");
            if (path == null) {
                throw new IllegalArgumentException("Parâmetro 'path' é obrigatório.");
            }
            return BridgeResponse.of(NbProjectService.getInstance().openProject(path));
        }
    }

    public static class ProjectActionHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String project = getStringParam(params, "project");
            String action = getStringParam(params, "action", "command");
            String file = getStringParam(params, "file", "target_file");

            if (action == null || action.trim().isEmpty()) {
                throw new IllegalArgumentException("Parâmetro 'action' é obrigatório.");
            }
            return BridgeResponse.of(NbProjectService.getInstance().runProjectAction(project, action, file));
        }
    }

    public static class InvokeActionHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String actionId = getStringParam(params, "action_id", "id");
            String category = getStringParam(params, "category");

            if (actionId == null || actionId.trim().isEmpty()) {
                throw new IllegalArgumentException("Parâmetro 'action_id' é obrigatório.");
            }
            return BridgeResponse.of(NbProjectService.getInstance().invokeGlobalAction(category, actionId));
        }
    }
}
