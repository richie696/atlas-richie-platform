package cn.richie696.component.oauth.oidc;

/**
 * OIDC Core 与 Discovery 用到的稳定字段名集中点，覆盖 scope、Claims 与事件类型。
 *
 * <p>处于 OIDC 模块的最底层（纯常量容器），被本包内所有协议服务（ID Token、Logout、UserInfo、
 * Discovery）以及上游 OAuth Service 一同引用。把这些字符串集中到一处可以避免"散落在各处的
 * magic string 拼写漂移"导致签名错位或 Discovery Metadata 与 AS 实际行为不一致。
 *
 * <p>解决"OIDC 协议字段名（iss/sub/aud/nonce/events）在多处硬编码、升级规范时难以全量回归"
 * 的维护痛点，让所有引用方都通过常量拿到同一份拼写，从而把"协议字段字面值"这一类易变
 * 信息压缩到唯一出口。
 *
 * @author richie696
 * @since 2026-08-07
 */
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
