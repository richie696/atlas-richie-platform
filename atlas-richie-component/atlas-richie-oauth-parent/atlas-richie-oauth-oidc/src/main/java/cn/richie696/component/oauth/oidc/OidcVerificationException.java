package cn.richie696.component.oauth.oidc;

/** OIDC ID Token 或协议校验失败。 */
public class OidcVerificationException extends RuntimeException {

    public OidcVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
