package cn.richie696.component.oauth.cache;

/**
 * OAuth 内部缓存 Key 的命名空间工具, 把上游传入的业务 key 与组件级 prefix 拼成唯一 Key, 并校验两侧非空。
 * <p>
 * 处于 OAuth 缓存适配层的 "命名约束" 一环, 是 {@link OAuthCache} 实现读取前的统一入口; 上游 OAuth 业务模块通过它间接产出最终 Key, 禁止直接拼接含内部前缀的字符串。
 * 解决"多个 OAuth 子模块各自拼接 Key, 升级或排查时无法快速判断 Key 归属"的问题, 用集中命名空间为日后做 prefix 级清理、租户级隔离、灰度切流保留抓手。
 *
 * @author richie696
 * @since 2026-08-07
 */
public final class OAuthCacheKeys {

    private OAuthCacheKeys() {
    }

    public static String namespace(String prefix, String key) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("OAuth cache prefix 不能为空");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("OAuth cache key 不能为空");
        }
        return prefix + ":" + key;
    }
}
