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
package com.richie.component.liquibase.migration;

import com.richie.component.liquibase.config.LiquibaseProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 变更日志定位器：负责解析配置并找到所有 changelog 资源。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-15
 */
@Slf4j
public class ChangeLogResolver {

    /**
     * 默认构造函数（供 Spring 或调用方使用）。
     */
    public ChangeLogResolver() {
    }

    /**
     * 解析 changelog 列表：
     * 1) 优先使用 changeLogs（支持 classpath* 通配）
     * 2) 若为空，退回默认 db/changelog/db.changelog-master.yaml
     *
     * @param properties Liquibase 配置
     * @return 去重后的 changelog 路径列表
     */
    public List<String> resolveChangeLogs(LiquibaseProperties properties) {
        List<String> changeLogs = new ArrayList<>();

        if (properties.getChangeLogs() != null && !properties.getChangeLogs().isEmpty()) {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            for (String pattern : properties.getChangeLogs()) {
                try {
                    Resource[] resources = resolver.getResources(pattern);
                    for (Resource resource : resources) {
                        String uri = resource.getURI().toString();
                        // 保留原始 URI（含 classpath:/ 或 file:/），供 Liquibase 直接使用
                        changeLogs.add(uri.startsWith("file:") ? resource.getURL().toString() : uri);
                    }
                } catch (Exception e) {
                    log.warn("[liquibase] 解析 changelog 失败，pattern={}", pattern, e);
                }
            }
        }

        if (changeLogs.isEmpty()) {
            changeLogs.add("db/changelog/db.changelog-master.yaml");
        }

        // 去重并保持顺序
        return changeLogs.stream().distinct().collect(Collectors.toList());
    }
}
