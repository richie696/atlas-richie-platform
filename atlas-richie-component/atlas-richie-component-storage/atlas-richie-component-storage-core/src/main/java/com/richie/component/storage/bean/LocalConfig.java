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
package com.richie.component.storage.bean;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.Serializable;

/**
 * 本地存储配置
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-04 13:35:11
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "platform.component.storage.local")
public class LocalConfig implements Serializable {

    /**
     * 是否启用本地存储（true：启用[默认]，false：禁用）
     */
    private Boolean enable = true;

    /**
     * 本地存储路径
     */
    private String path;

    /**
     * 缓存配置
     */
    private Cache cache = new Cache();

    /**
     * 冷数据清理配置
     */
    private Cleanup cleanup = new Cleanup();

    public LocalConfig() {
    }

    public LocalConfig(String path) {
        this.path = path;
    }

    /**
     * 本地存储缓存配置
     */
    @Data
    public static class Cache implements Serializable {

        /**
         * 文件存在性缓存过期时间（毫秒，默认：3600000 = 1小时）
         */
        private Long existsTtl = 3_600_000L;

        /**
         * 文件元数据缓存过期时间（毫秒，默认：1800000 = 30分钟）
         */
        private Long metadataTtl = 1_800_000L;

        /**
         * 文件内容缓存过期时间（毫秒，默认：600000 = 10分钟）
         */
        private Long contentTtl = 600_000L;

        /**
         * 文件内容缓存大小限制（字节，默认：1048576 = 1MB，仅缓存小文件）
         */
        private Long contentMaxSize = 1_048_576L;

        /**
         * 是否启用缓存统计（默认：true）
         */
        private Boolean statisticsEnabled = true;
    }

    /**
     * 冷数据清理配置
     */
    @Data
    public static class Cleanup implements Serializable {
        /** 是否启用清理（默认：false） */
        private Boolean enabled = false;
        /** 保留天数（默认：180天） */
        private Integer retentionDays = 180;
        /** 每次最大删除数量（默认：1000） */
        private Integer maxDeletePerRun = 1000;
        /** 是否仅打印SQL/操作而不执行（默认：false） */
        private Boolean dryRun = false;
        /** 默认执行时间：每天03:00（实际调度固定在03:00触发） */
        private String cron = "0 0 3 * * ?";
        /** 是否同时删除数据库元数据（默认：true） */
        private Boolean removeMetadata = true;
    }
}
