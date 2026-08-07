package cn.richie696.component.oauth.resource;

import java.security.interfaces.RSAPublicKey;

/**
 * Resource Server 按 JWT header 的 {@code kid} 获取对应签名公钥的端口抽象。
 *
 * <p>处于 {@link DefaultJwtTokenVerifier} 与公钥来源（HTTP JWKS endpoint / 静态配置 /
 * 外部 KMS）之间：上游验签流程传入 header 中的 {@code kid}，下游任意实现负责把
 * {@code kid} 解析为 {@link RSAPublicKey}。本接口不绑定缓存策略、不感知 JWKS 文档
 * 结构，所有复杂度由具体实现封装。
 *
 * <p>解决"Resource Server 必须依赖远程 JWKS HTTP 拉取，但在测试 / 离线 / 静态部署
 * 场景下没有 HTTP 入口"的多场景适配问题，让同一份 JWT 验签逻辑既能跑在
 * {@link HttpJwkSource}，也能跑在 {@link StaticJwkSource}。
 *
 * @author richie696
 * @since 2026-08-07
 */
public interface JwkSource {

    RSAPublicKey find(String keyId);
}
