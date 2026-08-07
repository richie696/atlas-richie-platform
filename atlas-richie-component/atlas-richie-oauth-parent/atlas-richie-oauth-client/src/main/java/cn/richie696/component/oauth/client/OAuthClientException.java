package cn.richie696.component.oauth.client;

/** OAuth 标准客户端调用失败。 */
public class OAuthClientException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    public OAuthClientException(String message, int statusCode, String errorCode) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public OAuthClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.errorCode = null;
    }

    public int statusCode() {
        return statusCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
