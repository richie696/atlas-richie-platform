package cn.richie696.component.oauth.resource;

import java.time.Instant;

/** 经过签名、请求绑定和防重放校验后的 DPoP proof。 */
public record DpopProof(
        String jti,
        String htm,
        String htu,
        Instant issuedAt,
        String jwkThumbprint,
        String nonce) {
}
