/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.vector.config;

import com.richie.component.vector.service.VectorService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class VectorMultiProviderGuard {

    @Autowired
    private ApplicationContext applicationContext;

    @PostConstruct
    public void guard() {
        Map<String, VectorService> beansOfType = applicationContext.getBeansOfType(VectorService.class);

        if (beansOfType.size() <= 1) {
            return;
        }

        List<String> providerNames = new ArrayList<>();
        for (Map.Entry<String, VectorService> entry : beansOfType.entrySet()) {
            String beanName = entry.getKey();
            Class<?> implClass = entry.getValue().getClass();
            String className = implClass.getName();

            if (className.contains("$Proxy") || className.contains("$$")) {
                providerNames.add(beanName + " (" + implClass.getSuperclass().getSimpleName() + ")");
            } else {
                providerNames.add(beanName + " (" + implClass.getSimpleName() + ")");
            }
        }

        String message = """
                检测到多个 VectorService 实现被同时引入，这是不允许的。

                向量库采用单 provider 架构，同一时刻只能使用一种向量数据库实现。
                请确保项目中只引入一个向量库 provider 依赖，并移除其他 provider 依赖。

                检测到的 VectorService 实现：
                %s

                当前支持的 provider（请只选择其中之一）：
                  - richie-component-vector-redis        (Redis)
                  - richie-component-vector-milvus       (Milvus)
                  - richie-component-vector-mongodb-atlas (MongoDB Atlas)
                  - richie-component-vector-postgresql   (PostgreSQL)
                  - richie-component-vector-qdrant      (Qdrant)
                  - richie-component-vector-neo4j        (Neo4j)
                  - richie-component-vector-elasticsearch (Elasticsearch)
                  - richie-component-vector-weaviate     (Weaviate)

                如需切换 provider，请：
                1. 移除当前 provider 依赖
                2. 添加目标 provider 依赖
                3. 修改配置：platform.component.vector.provider=目标provider
                """.formatted(String.join("\n  - ", providerNames));

        throw new IllegalStateException(message);
    }
}