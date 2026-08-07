package cn.richie696.component.oauth.resource;

import java.security.interfaces.RSAPublicKey;
import java.util.Map;

/**
 * 从启动期预置的 {@code kid → RSAPublicKey} 映射中取公钥的 {@link JwkSource} 实现。
 *
 * <p>处于 {@link DefaultJwtTokenVerifier} 与本进程内静态配置之间：上游验签流程传入
 * {@code kid}，本实现直接查 Map 返回公钥，整个过程不发起任何 HTTP 请求。它故意不接
 * 触 JWKS 文档、不感知密钥轮换语义，把所有"密钥已经由谁托管"的判断留给上游装配者。
 *
 * <p>解决"单元测试 / 离线部署 / 由外部 KMS 同步密钥的场景下还得拉一次 JWKS endpoint
 * 才能跑通 JWT 验签"的环境依赖问题，提供一个零网络、零缓存、可预测的最小实现，
 * 便于在 CI 与受控网络里直接断言验签行为，也方便上层把密钥托管在企业 KMS 中按
 * 周期刷新后重新构造本实例。
 *
 * @author richie696
 * @since 2026-08-07
 */
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
