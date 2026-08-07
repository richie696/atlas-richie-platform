package cn.richie696.component.oauth.core.spi;

import java.util.List;
import java.util.Map;

/** Authorization Server 发布 JWKS 的稳定端口。 */
@FunctionalInterface
public interface JwkSetProvider {

    /** 返回可直接序列化为 RFC 7517 JWKS 的 key 列表，不得包含私钥材料。 */
    List<Map<String, Object>> keys();
}
