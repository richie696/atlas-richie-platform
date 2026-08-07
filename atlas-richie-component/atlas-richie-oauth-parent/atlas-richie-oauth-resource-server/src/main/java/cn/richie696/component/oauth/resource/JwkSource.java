package cn.richie696.component.oauth.resource;

import java.security.interfaces.RSAPublicKey;

/** 根据 JWT header 的 kid 提供签名公钥。 */
public interface JwkSource {

    RSAPublicKey find(String keyId);
}
