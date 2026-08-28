package com.merito.agynb.core;

import com.merito.agynb.JsonUtils;
import java.util.HashMap;
import java.util.Map;

/**
 * Builder fluente para padronização de respostas JSON na Bridge Suite.
 */
public class BridgeResponse {

    private final int statusCode;
    private final Map<String, Object> data = new HashMap<>();

    public BridgeResponse(int statusCode) {
        this.statusCode = statusCode;
    }

    public static BridgeResponse ok() {
        BridgeResponse resp = new BridgeResponse(200);
        resp.data.put("ok", true);
        return resp;
    }

    public static BridgeResponse ok(String key, Object value) {
        BridgeResponse resp = ok();
        resp.data.put(key, value);
        return resp;
    }

    public static BridgeResponse of(Map<String, Object> map) {
        BridgeResponse resp = new BridgeResponse(200);
        if (map != null) {
            resp.data.putAll(map);
        }
        if (!resp.data.containsKey("ok")) {
            resp.data.put("ok", true);
        }
        return resp;
    }

    public static BridgeResponse error(int statusCode, String errorMessage) {
        BridgeResponse resp = new BridgeResponse(statusCode);
        resp.data.put("ok", false);
        resp.data.put("error", errorMessage != null ? errorMessage : "Erro desconhecido");
        return resp;
    }

    public static BridgeResponse error(int statusCode, Throwable throwable) {
        String msg = (throwable != null && throwable.getMessage() != null) ? throwable.getMessage() : String.valueOf(throwable);
        return error(statusCode, msg);
    }

    public BridgeResponse put(String key, Object value) {
        data.put(key, value);
        return this;
    }

    public BridgeResponse putAll(Map<String, ?> map) {
        if (map != null) {
            data.putAll(map);
        }
        return this;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public String toJson() {
        return JsonUtils.toJson(data);
    }
}
