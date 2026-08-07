package cn.richie696.component.oauth.resource;

import java.security.interfaces.RSAPublicKey;
import java.util.Map;

/** 测试、离线部署和已由外部系统托管 JWKS 时使用的公钥源。 */
public class StaticJwkSource implements JwkSource {

    private final Map<String, RSAPublicKey> keys;

    public StaticJwkSource(Map<String, RSAPublicKey> keys) {
        this.keys = Map.copyOf(keys);
    }

    @Override
    public RSAPublicKey find(String keyId) {
        return keys.get(keyId);
    }
}
