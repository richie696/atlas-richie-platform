package cn.richie696.component.oauth.oidc;

/**
 * OIDC ID Token 的签名 SPI，把"用何种算法与密钥签发 ID Token"从协议核心里抽出来。
 *
 * <p>处于 {@link OidcIdTokenService} 与部署侧的密钥托管实现之间：上游 ID Token 域外观
 * 按协议规则组织好 Claims 后调用本接口，下游由 OAuth Service 在启动阶段注入
 * {@link RsaOidcIdTokenSigner} 或其它 RSA/EC 实现。组件不绑定任何 JDK KeyStore、
 * KMS 或 HSM，由业务侧决定私钥来源与轮换策略。
 *
 * <p>解决"OIDC 组件自带密钥实现会和各企业既有 KMS / Vault / 国密改造冲突"的可替换性问题，
 * 让同一份协议核心既能跑在裸机 RSA 私钥上，也能跑在云端 HSM 托管的 EC 密钥上，
 * 测试场景下还能直接用 lambda 返回固定字符串。
 *
 * @author richie696
 * @since 2026-08-07
 */
public interface OidcIdTokenSigner {

    String sign(OidcIdTokenRequest request);
}
