package cn.richie696.component.oauth.gateway;

/** 严格提取 RFC 6750 Authorization Bearer Token。 */
public final class BearerTokenExtractor {

    private BearerTokenExtractor() {
    }

    public static String extract(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        int separator = authorization.indexOf(' ');
        if (separator <= 0 || !"Bearer".equalsIgnoreCase(authorization.substring(0, separator))) {
            return null;
        }
        String token = authorization.substring(separator + 1).trim();
        return token.isBlank() || token.indexOf(' ') >= 0 ? null : token;
    }
}
