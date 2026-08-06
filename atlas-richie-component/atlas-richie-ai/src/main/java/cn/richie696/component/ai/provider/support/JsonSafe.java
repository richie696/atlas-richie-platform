/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 */
package cn.richie696.component.ai.provider.support;

import cn.richie696.context.utils.data.JsonUtils;
import java.util.Collections;
import java.util.Map;

/**
 * JsonUtils 容错封装 — 在 vendor 流式协议解析中,经常遇到 keep-alive ping /
 * 非法 JSON / 编码异常等场景,直接 deserialize() 会返回 null。
 * <p>
 * 这里把所有 null 都转成默认值 (空 Map / 空 List / null),并打 debug 日志
 * 便于排查 — 业务侧调用方再也不会 NPE。
 */
public final class JsonSafe {

    private JsonSafe() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseMap(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = JsonUtils.getInstance().deserialize(text, Map.class);
        return result != null ? result : Collections.emptyMap();
    }

    public static <T> T parseObject(String text, Class<T> clazz) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return JsonUtils.getInstance().deserialize(text, clazz);
    }
}
