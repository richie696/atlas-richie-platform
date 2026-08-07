package cn.richie696.component.oauth.oidc;

/**
 * OIDC Backchannel Logout Token 的签名 SPI，把签名算法与密钥来源与协议编排解耦。
 *
 * <p>处于 {@link OidcBackchannelLogoutService} 与部署侧密钥实现之间：编排服务只决定
 * "对哪些 RP、签发哪些 Claims"，真正调用 KMS、HSM 或本地 KeyStore 由实现方在 OAuth Service
 * 启动阶段注入（生产可指向 {@link RsaOidcLogoutTokenSigner} 或外部 JCA Provider）。
 * 接口设计为 functional interface，方便单测里直接用 lambda 返回固定 token。
 *
 * <p>解决"OP 框架自带密钥导致与既有密钥托管体系冲突"的可替换性问题，让 Logout Token
 * 的签名实现既能与 ID Token 复用同一密钥，也能为高频注销事件选择独立密钥并按更短
 * 周期轮换，二者不互相绑架。
 *
 * @author richie696
 * @since 2026-08-07
 */
@FunctionalInterface
public interface OidcLogoutTokenSigner {

    String sign(OidcLogoutTokenRequest request);
}
