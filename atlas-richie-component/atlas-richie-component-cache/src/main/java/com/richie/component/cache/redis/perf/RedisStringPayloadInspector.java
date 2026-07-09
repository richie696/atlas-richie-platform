/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.cache.redis.perf;

import com.richie.component.cache.redis.config.base.AtlasRedisProperties;

import java.net.URI;
import java.net.URL;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 检测向 Redis <b>String</b> 类型写入的值是否存在典型滥用：集合/Map/数组/大对象整包序列化、超大文本、疑似 JSON 整包等。
 * <p>与 {@link RedisPerfGuard#checkStringWritePayload(String, String, String, Object)} 配合，仅在
 * {@code spring.data.redis.perf.enabled} 与 {@code warn-string-payload-anti-patterns} 开启时生效。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-04-03
 */
public final class RedisStringPayloadInspector {

    private RedisStringPayloadInspector() {
    }

    /** 对 String 写入值的告警级别。 */
    public enum Severity {
        OK,
        WARN,
        ERROR
    }

    /**
     * 载荷检查结果。
     *
     * @param severity OK / WARN / ERROR
     * @param detail   人类可读说明（英文日志前缀由调用方拼接）
     */
    public record Inspection(Severity severity, String detail) {

        static Inspection ok() {
            return new Inspection(Severity.OK, "");
        }
    }

    /**
     * 对即将写入 String 的值做静态形状与体量检查（不涉及真实 Redis 序列化字节数，以避免双份序列化）。
     */
    public static Inspection inspect(Object value, AtlasRedisProperties.RedisPerf perf) {
        if (value == null) {
            return Inspection.ok();
        }
        if (value instanceof Optional<?> opt) {
            return opt.isEmpty() ? Inspection.ok() : inspect(opt.get(), perf);
        }
        if (value instanceof Collection<?> || value instanceof Map<?, ?>) {
            return new Inspection(Severity.ERROR,
                    "集合或 Map 不应整包序列化进 Redis String，应使用 Hash/List/ZSet 等结构或拆分 key");
        }
        if (value.getClass().isArray()) {
            if (value instanceof byte[] bytes) {
                return inspectByteArray(bytes, perf);
            }
            return new Inspection(Severity.ERROR,
                    "数组类型不应整包写入 Redis String（易形成大 value/JSON 膨胀），应使用 List 或多 key");
        }
        if (isSimpleScalar(value)) {
            if (value instanceof CharSequence cs) {
                return inspectCharSequence(cs, perf);
            }
            return Inspection.ok();
        }
        return new Inspection(Severity.WARN,
                "疑似 JavaBean/复杂对象整包写入 String（将由序列化器打成单一大 value），建议 Hash 字段建模、拆 key 或专用存储");
    }

    private static Inspection inspectByteArray(byte[] bytes, AtlasRedisProperties.RedisPerf perf) {
        int len = bytes.length;
        if (len >= perf.getStringPayloadMaxBytesError()) {
            return new Inspection(Severity.ERROR,
                    "byte[] 长度过大 (%d bytes >= errorThreshold)，不宜作为 String value（易 BIGKEY/网络放大）".formatted(len));
        }
        if (len >= perf.getStringPayloadMaxBytesWarn()) {
            return new Inspection(Severity.WARN,
                    "byte[] 长度较大 (%d bytes >= warnThreshold)，请确认是否应使用独立二进制通道或拆分".formatted(len));
        }
        return Inspection.ok();
    }

    private static Inspection inspectCharSequence(CharSequence cs, AtlasRedisProperties.RedisPerf perf) {
        int len = cs.length();
        if (len >= perf.getStringPayloadMaxCharsError()) {
            return new Inspection(Severity.ERROR,
                    "字符长度过大 (%d >= errorThreshold)，String value 易形成 BIGKEY，请拆分或换结构".formatted(len));
        }
        if (len >= perf.getStringPayloadMaxCharsWarn()) {
            return new Inspection(Severity.WARN,
                    "字符长度较大 (%d >= warnThreshold)，请评估是否拆分或压缩".formatted(len));
        }
        if (perf.isWarnJsonLikeStringBlob() && len >= perf.getJsonLikeMinCharsForWarn()) {
            int i = 0;
            while (i < len && Character.isWhitespace(cs.charAt(i))) {
                i++;
            }
            if (i < len) {
                char c = cs.charAt(i);
                if (c == '{' || c == '[') {
                    return new Inspection(Severity.WARN,
                            "值以 '{' 或 '[' 开头且长度>=阈值，疑似将 JSON 对象/数组整包写入 String，请确认是否误用（更推荐 Hash/List）");
                }
            }
        }
        return Inspection.ok();
    }

    private static boolean isSimpleScalar(Object o) {
        return o instanceof CharSequence
                || o instanceof Number
                || o instanceof Boolean
                || o instanceof Character
                || o instanceof Enum<?>
                || o instanceof UUID
                || o instanceof URI
                || o instanceof URL
                || o instanceof Temporal
                || o instanceof java.util.Date
                || o instanceof java.util.Calendar
                || o instanceof java.time.ZoneId;
    }
}
