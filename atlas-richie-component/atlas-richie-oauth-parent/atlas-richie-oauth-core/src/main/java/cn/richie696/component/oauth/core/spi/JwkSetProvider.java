package cn.richie696.component.oauth.core.spi;

import java.util.List;
import java.util.Map;

/**
 * Authorization Server 发布 JWKS 的稳定端口。
 * <p>
 * 返回可直接序列化为 RFC 7517 JWKS 文档的公钥列表,绝不包含私钥材料;OAuth Service 在 HTTP 适配层
 * 把这个列表渲染为标准的 {@code /.well-known/jwks.json} 端点,Resource Server 据此验证 JWT 签名。
 * </p>
 * <p>
 * 处于 oauth-core 的密钥发布位置:由 {@link cn.richie696.component.oauth.core.support.RsaAccessTokenSigner}
 * 同时实现该接口,密钥轮换时只需更换 keyId,旧 key 仍可发布以兼容未过期 token。
 * </p>
 * <p>
 * 解决的问题:把"如何把签名器的当前公钥暴露给 Resource Server"抽象为函数式端口,业务方可以接入
 * HSM、KMS 或密钥管理服务,而不必自行拼接 JWKS 文档;同时强制"只暴露公钥"这一安全底线。
 * </p>
 *
 * @author richie696
 * @since 2026-08-07
 */
@FunctionalInterface
public interface JwkSetProvider {

    /** 返回可直接序列化为 RFC 7517 JWKS 的 key 列表，不得包含私钥材料。 */
    List<Map<String, Object>> keys();
}
