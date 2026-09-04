package com.merito.agynb.handlers;

import com.merito.agynb.core.AbstractJsonHandler;
import com.merito.agynb.core.BridgeResponse;
import com.merito.agynb.form.NbFormService;
import com.sun.net.httpserver.HttpExchange;
import java.util.Map;

/**
 * Handlers HTTP para manipulação, inspeção e criação de formulários Swing (.form e .java).
 */
public final class FormHandlers {

    private FormHandlers() {
    }

    public static class FormInspectHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String file = getStringParam(params, "file", "filePath");
            Map<String, Object> res = NbFormService.getInstance().inspect(file);
            return BridgeResponse.of(res);
        }
    }

    public static class FormSetPropertyHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String file = getStringParam(params, "file", "filePath");
            String component = getStringParam(params, "component", "componentName");
            String propName = getStringParam(params, "property", "propertyName");
            String propType = getStringParam(params, "type", "propertyType");
            String value = getStringParam(params, "value");

            Map<String, Object> res = NbFormService.getInstance().setProperty(file, component, propName, propType, value);
            return BridgeResponse.of(res);
        }
    }

    public static class FormCreateBlueprintHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String targetDir = getStringParam(params, "targetDir", "target_dir");
            String packageName = getStringParam(params, "packageName", "package_name");
            String className = getStringParam(params, "className", "class_name");

            @SuppressWarnings("unchecked")
            Map<String, Object> blueprint = (Map<String, Object>) params.get("blueprint");
            if (blueprint == null) {
                blueprint = params;
            }

            Map<String, Object> res = NbFormService.getInstance().createBlueprint(targetDir, packageName, className, blueprint);
            return BridgeResponse.of(res);
        }
    }
}
