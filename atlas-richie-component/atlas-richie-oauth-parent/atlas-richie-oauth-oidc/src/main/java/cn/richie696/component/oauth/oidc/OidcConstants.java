package cn.richie696.component.oauth.oidc;

/** OIDC Core 和 Discovery 使用的稳定字段名。 */
public final class OidcConstants {

    public static final String OPENID_SCOPE = "openid";
    public static final String PROFILE_SCOPE = "profile";
    public static final String EMAIL_SCOPE = "email";
    public static final String ADDRESS_SCOPE = "address";
    public static final String PHONE_SCOPE = "phone";

    public static final String CLAIM_ISSUER = "iss";
    public static final String CLAIM_SUBJECT = "sub";
    public static final String CLAIM_AUDIENCE = "aud";
    public static final String CLAIM_EXPIRATION = "exp";
    public static final String CLAIM_ISSUED_AT = "iat";
    public static final String CLAIM_AUTH_TIME = "auth_time";
    public static final String CLAIM_NONCE = "nonce";
    public static final String CLAIM_ACR = "acr";
    public static final String CLAIM_AMR = "amr";
    public static final String CLAIM_AT_HASH = "at_hash";
    public static final String CLAIM_EVENTS = "events";
    public static final String CLAIM_SID = "sid";

    private OidcConstants() {
    }
}
