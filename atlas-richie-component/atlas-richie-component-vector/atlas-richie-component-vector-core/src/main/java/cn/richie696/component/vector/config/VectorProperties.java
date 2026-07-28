/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.vector.config;

import cn.richie696.component.vector.enums.VectorProvider;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/** 向量存储组件配置；模型厂商和 API 凭据由 AI 组件管理。 */
@Data
@ConfigurationProperties(prefix = "platform.component.vector")
public class VectorProperties {

    /** 当前启用的单一向量 provider。 */
    private VectorProvider provider = VectorProvider.MILVUS;

    /** 默认索引/collection 名称。 */
    private String defaultIndex = "documents";

    /** 索引级声明配置。 */
    private Map<String, IndexConfig> indexes;

    /** 仅在使用者验证该 provider 支持 Spring AI filter DSL 后开启。 */
    private boolean springAiFilterDslEnabled;

    /** 批量入库的背压、并发及刷盘参数。 */
    private Bulk bulk = new Bulk();

    @Data
    @Accessors(chain = true)
    public static class IndexConfig {
        private String name;
        private Integer dimension = 1536;
        private String metric = "cosine";
        private String indexType = "hnsw";
        private Integer replicas = 1;
        private Integer shards = 1;
        private Map<String, Object> additionalFields;
        private Map<String, Object> indexParams;
    }

    @Data
    @Accessors(chain = true)
    public static class Bulk {
        /** 同时进行的 embedding 调用数。 */
        private int embeddingConcurrency = 8;
        /** 单次向量库写入的记录数。 */
        private int writeBatchSize = 100;
        /** 同时进行的向量库写入数。 */
        private int writeConcurrency = 4;
        /** 不足一个写入批次时的最长等待时间。 */
        private long writeFlushIntervalMs = 1_000;
    }
}
