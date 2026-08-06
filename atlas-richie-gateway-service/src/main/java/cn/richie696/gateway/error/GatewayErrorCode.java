/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.gateway.error;

/** Stable error codes emitted by the gateway. */
public enum GatewayErrorCode {
    GW_AUTH_0001("GW-AUTH-0001", 401, false, "error.auth.token.invalid"),
    GW_AUTH_0002("GW-AUTH-0002", 401, false, "error.auth.token.missing"),
    GW_AUTH_0003("GW-AUTH-0003", 401, false, "error.auth.token.expired"),
    GW_AUTH_0004("GW-AUTH-0004", 403, false, "error.auth.permission.denied"),
    GW_TENANT_0001("GW-TENANT-0001", 400, false, "error.tenant.missing"),
    GW_TENANT_0002("GW-TENANT-0002", 403, false, "error.tenant.invalid"),
    GW_RATE_0001("GW-RATE-0001", 429, true, "error.rate.limited"),
    GW_UPSTREAM_0001("GW-UPSTREAM-0001", 502, true, "error.upstream.unavailable"),
    GW_UPSTREAM_0002("GW-UPSTREAM-0002", 504, true, "error.upstream.timeout"),
    GW_SYSTEM_0001("GW-SYSTEM-0001", 500, false, "error.system.internal"),
    GW_ROUTE_0001("GW-ROUTE-0001", 404, false, "error.route.not.found"),
    GW_REQ_0001("GW-REQ-0001", 400, false, "error.req.bad.request");

    private final String code;
    private final int httpStatus;
    private final boolean retryable;
    private final String i18nKey;

    GatewayErrorCode(String code, int httpStatus, boolean retryable, String i18nKey) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
        this.i18nKey = i18nKey;
    }
    public String getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
    public boolean isRetryable() { return retryable; }
    public String getI18nKey() { return i18nKey; }
}
