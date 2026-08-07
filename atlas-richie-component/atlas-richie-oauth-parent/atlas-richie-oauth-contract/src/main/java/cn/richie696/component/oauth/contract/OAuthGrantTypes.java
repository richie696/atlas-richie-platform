package cn.richie696.component.oauth.contract;

/** OAuth grant_type 常量。 */
public final class OAuthGrantTypes {

    public static final String CLIENT_CREDENTIALS = "client_credentials";
    public static final String AUTHORIZATION_CODE = "authorization_code";
    public static final String REFRESH_TOKEN = "refresh_token";
    public static final String DEVICE_CODE = "urn:ietf:params:oauth:grant-type:device_code";

    private OAuthGrantTypes() {
    }
}
