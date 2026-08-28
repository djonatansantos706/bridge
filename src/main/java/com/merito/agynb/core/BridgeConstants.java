package com.merito.agynb.core;

/**
 * Constantes globais e imutáveis da Antigravity NetBeans Bridge Suite.
 */
public final class BridgeConstants {

    private BridgeConstants() {
    }

    public static final String SERVICE_NAME = "antigravity-netbeans-bridge";
    public static final String VERSION = "1.2.0";
    public static final int DEFAULT_PORT = 8388;
    public static final String DEFAULT_HOST = "127.0.0.1";

    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    public static final String HEADER_ALLOW_METHODS = "Access-Control-Allow-Methods";
    public static final String HEADER_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    public static final String HEADER_TOKEN = "X-Bridge-Token";

    public static final String MIME_JSON = "application/json; charset=utf-8";
    public static final String CORS_ORIGIN_ALL = "*";
    public static final String CORS_METHODS = "GET, POST, OPTIONS";
    public static final String CORS_HEADERS = "Content-Type, Authorization, X-Bridge-Token";
}
