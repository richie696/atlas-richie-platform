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
package com.richie.component.cache.redis.config.base;

import com.richie.component.cache.enums.KeyTypeEnum;
import com.richie.component.cache.redis.enums.RedisTypeEnum;
import com.richie.context.migration.MigrationWindow;
import io.lettuce.core.protocol.ProtocolVersion;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Redis缓存配置文件
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-01 21:43:56
 */
@Data
@Primary
@EqualsAndHashCode(callSuper = true)
@ConfigurationProperties(prefix = "spring.data.redis")
public class AtlasRedisProperties extends DataRedisProperties {


    /**
     * Redis服务器类型（可选值：standalone【默认】、sentinel、cluster）
     */
    private RedisTypeEnum serverType = RedisTypeEnum.STANDALONE;

    /**
     * 缓存子节点配置
     */
    private Map<String, AtlasRedisProperties> slaves;

    /**
     * lettuce配置
     */
    private LettuceExtension lettuce = new LettuceExtension();

    /**
     * 是否启用 Redis 二级缓存（默认：false）
     * <p>二级缓存将采用LocalCache实现进行缓存配置，LocalCache已实现 javax 的
     * JSR107 标准缓存接口，根据您配置的 LocalCache 的参数自动适配全局的本地缓存。
     * 默认：Caffeine
     */
    private Boolean enableL2Caching = false;

    /**
     * 二级缓存数据类型（默认：无）
     */
    private List<KeyTypeEnum> l2CachingData = new ArrayList<>(KeyTypeEnum.values().length);

    /**
     * 是否启用本地二级锁（默认：true）
     * <p>启用后，先在本地JVM内存中进行锁竞争，只有本地未持有锁时才会请求Redis分布式锁，缓解Redis压力。
     */
    private boolean enableLocalLock = true;

    /**
     * 是否在连接激活之前执行ping命令（默认：true）
     */
    private boolean pingBeforeActivateConnection = true;

    /**
     * 客户端驱动程序支持的本机协议的版本（默认：最新版本）
     * <ul>
     *     <li><strong>RESP2协议</strong>
     *         <ul>
     *             <li>适用版本: Redis 2 到 Redis 5</li>
     *             <li>特点: 这是传统的 Redis 协议版本</li>
     *             <li>兼容性: 几乎所有 Redis 版本都支持</li>
     *             <li>通信方式: 基于简单的文本协议</li>
     *         </ul>
     *     </li>
     *     <li><strong>RESP3协议</strong>
     *         <ul>
     *             <li>适用版本: Redis 6 及以上</li>
     *             <li>特点: 新一代 Redis 协议</li>
     *             <li>优势:
     *                 <ul>
     *                     <li>支持更丰富的数据类型，避免RESP2的纯字符串类型之后的数据转换，大大提升性能</li>
     *                     <li>更好的性能</li>
     *                     <li>支持客户端缓存</li>
     *                     <li>支持推送消息</li>
     *                 </ul>
     *             </li>
     *         </ul>
     *     </li>
     * </ul>
     * <p>注意：当Redis服务器禁用HELLO命令时，应使用RESP2协议避免连接失败
     */
    private ProtocolVersion protocolVersion = ProtocolVersion.newestSupported();

    /**
     * Redis 调用性能守卫（非 O(1) 告警、慢查询、可选阻断高复杂度操作）
     */
    private RedisPerf perf = new RedisPerf();

    /**
     * Redis 性能守卫配置（绑定前缀 {@code spring.data.redis.perf.*}）
     *
     * @author richie696
     * @version 1.0.0
     * @since 2026-04-03
     */
    @Data
    @ConfigurationProperties(prefix = "spring.data.redis.perf")
    public static class RedisPerf {
        /**
         * 是否启用性能守卫（默认 false，避免对存量环境产生日志噪声）。
         * <p><b>迁移窗口</b>：所有项目应于 {@code 2026-12-01} 前将本字段设为 {@code true}；
         * 之后此字段会被物理删除，新项目默认即为 {@code true}。详见
         * {@link com.richie.component.cache.redis.migration.MigrationWindowValidator}。
         */
        @MigrationWindow(
                until = "2026-12-01",
                removedIn = "1.0.0",
                owner = "richie696",
                reason = "性能守卫必须默认开启；存量项目需在截止日期前完成业务代码适配")
        private boolean enabled = false;

        /**
         * 非 O(1) 调用是否打 WARN（默认 true，仅在 enabled=true 时生效）
         */
        private boolean warnNonO1 = true;

        /**
         * ToC 软阈值（毫秒），超过则 WARN 提示优化
         */
        private long tocSoftMs = 8L;

        /**
         * ToC 硬阈值（毫秒），超过则 ERROR（疑似 BIGKEY / 热 key / 严重慢查询）
         */
        private long tocHardMs = 50L;

        /**
         * 是否对 LINEAR_N、WORSE 复杂度直接抛异常阻断（默认 false）。
         * <p><b>迁移窗口</b>：依赖 {@link #enabled} 开启后，存量项目需于 {@code 2026-12-01} 前
         * 把本字段设为 {@code true}（即默认阻断），否则到期后启动失败。
         */
        @MigrationWindow(
                until = "2026-12-01",
                removedIn = "1.0.0",
                owner = "richie696",
                reason = "LINEAR_N / WORSE 复杂度在 ToC 核心路径必须硬阻断，不应仅 WARN")
        private boolean blockForbiddenTiers = false;

        /**
         * 是否在非 O(1) 调用时输出 BIGKEY 探测建议（HLEN/LLEN 等，默认 true）
         */
        private boolean logBigKeyProbeHints = true;

        /**
         * 可选：ToC 允许的复杂度枚举名白名单（如 {@code O1}、{@code LOG_N}、{@code SCRIPT_OR_UNKNOWN}）。
         * 为空则不限制；非空时若当前调用分级不在列表中则 ERROR，且 {@link #blockForbiddenTiers} 为 true 时抛异常。
         */
        private List<String> tocAllowedComplexities = new ArrayList<>();

        /**
         * 是否检测向 Redis String 写入的值是否存在典型滥用（集合/Map/数组/大文本/疑似整包 JSON 等，默认 true，依赖 enabled）
         */
        private boolean warnStringPayloadAntiPatterns = true;

        /**
         * 疑似「整包 JSON」启发式：{@link CharSequence} 去空白后以 {@code {} 或 [} 开头且长度达到该阈值则 WARN
         */
        private int jsonLikeMinCharsForWarn = 128;

        /**
         * 是否启用上述 JSON 形状启发式（默认 true）
         */
        private boolean warnJsonLikeStringBlob = true;

        /**
         * String 字符数 WARN 阈值（近似体量，非 UTF-8 字节）
         */
        private int stringPayloadMaxCharsWarn = 100_000;

        /**
         * String 字符数 ERROR 阈值（超过则 ERROR，可与 {@link #blockStringPayloadViolations} 联动抛异常）
         */
        private int stringPayloadMaxCharsError = 1_000_000;

        /**
         * byte[] 写入 String 时的 WARN 阈值（字节）
         */
        private int stringPayloadMaxBytesWarn = 262_144;

        /**
         * byte[] 写入 String 时的 ERROR 阈值（字节）
         */
        private int stringPayloadMaxBytesError = 1_048_576;

        /**
         * 是否在 ERROR 级别 String 载荷问题时抛异常阻断写入（默认 false，仅打日志）。
         * <p><b>迁移窗口</b>：依赖 {@link #enabled} 开启后，存量项目需于 {@code 2026-12-01} 前
         * 把本字段设为 {@code true}（即默认阻断大 value），否则到期后启动失败。
         */
        @MigrationWindow(
                until = "2026-12-01",
                removedIn = "1.0.0",
                owner = "richie696",
                reason = "大 value 是 JVM GC 与网络热点的头号元凶，必须在写入边界硬阻断")
        private boolean blockStringPayloadViolations = false;
    }
}
