package cn.richie696.component.oauth.contract;

/** OAuth 协议核心常量，避免协议内核依赖 Gateway 工程的合约包。 */
public interface OAuth2Constants {

    String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";
    String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";
    String GRANT_TYPE_ACCESS_TOKEN = "access_token";
    String GRANT_TYPE_DEVICE_CODE = "urn:ietf:params:oauth:grant-type:device_code";
    String TOKEN_TYPE_BEARER = "Bearer";

    String ERROR_INVALID_REQUEST = "invalid_request";
    String ERROR_INVALID_CLIENT = "invalid_client";
    String ERROR_INVALID_GRANT = "invalid_grant";
    String ERROR_INVALID_TOKEN = "invalid_token";
    String ERROR_INVALID_SCOPE = "invalid_scope";
    String ERROR_UNSUPPORTED_GRANT_TYPE = "unsupported_grant_type";
    String ERROR_UNAUTHORIZED_CLIENT = "unauthorized_client";
    String ERROR_IP_NOT_ALLOWED = "ip_not_allowed";
    String ERROR_CLIENT_DISABLED = "client_disabled";
    String ERROR_RATE_LIMIT_EXCEEDED = "rate_limit_exceeded";
    String ERROR_SERVER_ERROR = "server_error";
    String ERROR_INVALID_CONFIG = "invalid_config";

    String JWT_CLAIM_CLIENT_ID = "clientId";
    String JWT_CLAIM_TYPE = "type";
    String JWT_CLAIM_TYPE_THIRD_PARTY = "third_party";
    String JWT_CLAIM_SCOPE = "scope";
    String JWT_CLAIM_USERNAME = "username";
    String JWT_SUBJECT_THIRD_PARTY_ACCESS_TOKEN = "Third Party Access Token";

    long DEFAULT_ACCESS_TOKEN_EXPIRES_IN = 3600L;
    long DEFAULT_REFRESH_TOKEN_EXPIRES_IN = 2592000L;
}
