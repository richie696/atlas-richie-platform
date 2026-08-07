package cn.richie696.component.oauth.resource;

/** Resource Server 校验失败。 */
public class ResourceServerException extends RuntimeException {

    public ResourceServerException(String message) {
        super(message);
    }

    public ResourceServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
