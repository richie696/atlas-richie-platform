package cn.richie696.component.oauth.resource;

import java.time.Instant;

/**
 * 经过签名、请求绑定与防重放校验后的 DPoP proof 视图，仅承载下游业务侧真正需要的字段。
 *
 * <p>处于 {@link DpopProofValidator} 与 {@link ResourceServerAuthenticator} 之间：
 * 上游校验器把所有 raw JWT Claims 解析、验签、比对 htm/htu/ath/nonce/jti 之后产出本
 * record，下游 Resource Server 把其中的 jwkThumbprint 与 accessToken cnf.jkt 对齐、
 * 写入审计日志或传递给业务层。它故意不携带签名原始字节，避免上层误以为还能二次验签。
 *
 * <p>解决"DPoP proof 是裸 Map、业务侧读取字段时缺少类型约束"的易错场景，让下游拿到
 * 一个不可变的、字段语义明确的 record，从而把 RFC 9449 协议校验的产物收敛成一个可
 * 跨模块传递的领域对象。
 *
 * @author richie696
 * @since 2026-08-07
 */
public record DpopProof(
        String jti,
        String htm,
        String htu,
        Instant issuedAt,
        String jwkThumbprint,
        String nonce) {
}
