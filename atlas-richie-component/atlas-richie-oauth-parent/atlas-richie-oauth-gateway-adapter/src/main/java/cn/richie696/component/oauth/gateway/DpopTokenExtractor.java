package cn.richie696.component.oauth.gateway;

/** 提取 RFC 9449 Authorization: DPoP token。 */
public final class DpopTokenExtractor {

    private DpopTokenExtractor() {
    }

    public static String extract(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        int separator = authorization.indexOf(' ');
        if (separator <= 0 || !"DPoP".equalsIgnoreCase(authorization.substring(0, separator))) {
            return null;
        }
        String token = authorization.substring(separator + 1).trim();
        return token.isBlank() || token.indexOf(' ') >= 0 ? null : token;
    }
}
