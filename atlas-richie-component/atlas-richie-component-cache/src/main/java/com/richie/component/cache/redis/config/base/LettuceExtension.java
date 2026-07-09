/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.cache.redis.config.base;

import com.richie.component.cache.redis.enums.MemoryReleasePolicy;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.context.annotation.Primary;

import java.time.temporal.ChronoUnit;

/**
 * Lettuce扩展配置
 *
 * @author richie696
 * @version 1.0.0
 * @since 2024-03-04 14:42:34
 */
@Data
@Primary
@EqualsAndHashCode(callSuper = true)
@ConfigurationProperties(prefix = "spring.data.redis.lettuce")
public class LettuceExtension extends DataRedisProperties.Lettuce {

    /** 默认构造函数，供配置绑定使用。 */
    public LettuceExtension() {
    }

    /**
     * Socket连接保活策略配置（注意：此配置仅Linux环境下生效，支持的CPU型号包括：x86_64、aarch_64、riscv64）
     */
    private EpollKeepAliveProperties keepAlive = new EpollKeepAliveProperties();

    /**
     * 内存释放策略配置
     */
    private MemoryReleasePolicy memoryReleasePolicy = MemoryReleasePolicy.USAGE_RADIO;

    /**
     * 内存使用率释放比率
     * <ul>
     *     <li>当缓冲区使用率达到 ratio / (ratio + 1) 时触发内存释放</li>
     *     <li>当值为1时，缓冲区使用率达到50%时触发释放</li>
     *     <li>当值为2时，缓冲区使用率达到65%时触发释放</li>
     *     <li>当值为3时，缓冲区使用率达到75%时触发释放</li>
     * </ul>
     * <p>适用场景：平衡CPU和内存使用，数值越大内存使用越多但CPU负担越低
     * <p>推荐值：1-10之间，对应50%-90%的触发阈值
     * <p>注意：此配置仅在memoryReleasePolicy为USAGE_RADIO时生效
     */
    private Integer memoryReleaseRatio = 3;

    /**
     * Socket连接保活策略配置
     * <p style="color: red">（注意：此配置仅Linux环境下可用，支持的CPU型号包括：x86_64、aarch_64、riscv64）
     *
     * @author richie696
     * @version 1.0
     * @since 2024-03-04 14:42:34
     */
    @Data
    public static class EpollKeepAliveProperties {

        /** 默认构造函数，供配置绑定使用。 */
        public EpollKeepAliveProperties() {
        }

        /**
         * 是否启用Socket连接保活策略
         */
        private boolean enabled = false;
        /**
         * 启用保活后，在TCP开始发送保活探针之前，连接需要保持空闲的时间【默认：2】
         */
        private Integer idle = 2;
        /**
         * 空闲时间单位【默认：小时】
         */
        private ChronoUnit idleUnit = ChronoUnit.HOURS;
        /**
         * 启用保活后，每个保活探针之间的时间间隔【默认：75秒】
         */
        private Integer interval = 75;
        /**
         * 间隔时间单位【默认：秒】
         */
        private ChronoUnit intervalUnit = ChronoUnit.SECONDS;
        /**
         * TCP在彻底断开连接之前，至少发送的最大保活探针的数量【默认：9】。
         */
        private Integer count = 9;
        /**
         * TCP用户超时阈值时长【默认：7875秒】
         * <p>本配置来自 <a href="https://datatracker.ietf.org/doc/rfc5482/">RFC5482</a>
         * <p>推荐配置：idle + interval * count = TCP_USER_TIMEOUT
         */
        private Integer tcpUserTimeout = 7875;
        /**
         * TCP用户超时阈值时长单位【默认：秒】
         */
        private ChronoUnit tcpUserTimeoutUnit = ChronoUnit.SECONDS;
    }
}
