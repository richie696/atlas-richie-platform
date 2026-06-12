package com.richie.component.oauth.core.model;

/**
 * OAuth 2.1 Grant Type 枚举
 *
 * @author richie696
 * @since 2026-06-12
 */
public enum GrantType {

    CLIENT_CREDENTIALS("client_credentials"),
    REFRESH_TOKEN("refresh_token"),
    AUTHORIZATION_CODE("authorization_code");

    private final String value;

    GrantType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static GrantType fromValue(String value) {
        for (GrantType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported grant_type: " + value);
    }
}
