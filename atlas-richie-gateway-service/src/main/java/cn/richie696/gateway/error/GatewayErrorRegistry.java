/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.gateway.error;

import java.util.Arrays;
import java.util.List;

/** Lookup functions shared by all gateway error exits and the public documentation. */
public final class GatewayErrorRegistry {
    public static final String INDEX_PATH = "/gateway/errors";
    public static final String JSON_PATH = "/gateway/errors.json";
    private GatewayErrorRegistry() { }

    public static List<GatewayErrorCode> all() { return List.of(GatewayErrorCode.values()); }

    public static GatewayErrorCode byCode(String code) {
        if (code == null) return null;
        return Arrays.stream(GatewayErrorCode.values())
                .filter(value -> value.getCode().equalsIgnoreCase(code)).findFirst().orElse(null);
    }

    /** The first declared code for a status is the generic fallback for that status. */
    public static GatewayErrorCode byHttpStatus(int status) {
        return Arrays.stream(GatewayErrorCode.values())
                .filter(value -> value.getHttpStatus() == status).findFirst()
                .orElse(GatewayErrorCode.GW_SYSTEM_0001);
    }

    public static String helpUrl(GatewayErrorCode code) {
        return INDEX_PATH + "/" + code.getCode();
    }
}
