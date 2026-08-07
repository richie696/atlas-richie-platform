package cn.richie696.component.oauth.cache;

/** 组件内部缓存 Key 命名空间，调用方不得自行拼接内部 Key。 */
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
