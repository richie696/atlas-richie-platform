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
package com.richie.contract.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

/**
 * 金丝雀发布配置类
 * <p>
 * 灰度发布统一使用 ID 模式，支持自定义字段配置（通过 CanaryFieldConfig），
 * 可以提取任意类型的ID（门店ID、用户ID、版本号等）用于灰度路由。
 *
 * @author richie696
 * @version 2.0
 * @since 2024-01-09 15:16:31
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "platform.gateway.contract.deploy")
public class DeployConfig {

    /**
     * 是否启用金丝雀发布（默认：false）
     */
    private boolean enable;

    /**
     * 参与灰度的目标ID（可能是登录用户名、门店ID或任何其他能标识唯一身份的ID）
     */
    private Set<String> idList;

    /**
     * 参与灰度的服务列表（服务名称，如：order-service、payment-service）
     * <p>
     * 如果为空或包含 "*"，表示所有服务都参与灰度
     * 如果指定了服务列表，只有列表中的服务才会进行灰度路由
     * <p>
     * 使用场景：
     * - 某些服务不需要灰度版本，可以排除
     * - 按服务逐步灰度转正
     */
    private Set<String> serviceList;

    /**
     * 灰度字段名称配置
     * <p>
     * 支持自定义灰度字段名称，如果不配置则使用内置规则
     * 如果为 null，表示使用内置规则
     */
    private CanaryFieldConfig fieldConfig;


    /**
     * 检查指定服务是否参与灰度
     *
     * @param serviceId 服务ID（服务名称）
     * @return true 如果服务参与灰度
     */
    public boolean isServiceInCanary(String serviceId) {
        if (serviceList == null || serviceList.isEmpty()) {
            // 如果未配置服务列表，默认所有服务都参与灰度
            return true;
        }
        // 如果包含 "*"，表示所有服务都参与灰度
        if (serviceList.contains("*")) {
            return true;
        }
        // 检查服务是否在列表中
        return serviceList.contains(serviceId);
    }

    /**
     * 灰度字段名称配置类
     * <p>
     * 如果不配置某个字段，则使用内置规则
     */
    @Data
    public static class CanaryFieldConfig {
        /**
         * 请求头中的字段名称
         * <p>
         * 如果不配置，使用内置规则：x-rd-request-shopcode
         */
        private String headerFieldName;

        /**
         * JWT Token 中的字段名称列表（按优先级顺序）
         * <p>
         * 如果不配置，使用内置规则：storeId, shopCode
         */
        private List<String> tokenFieldNames;

        /**
         * 路径参数中的字段名称模式（用于正则匹配）
         * <p>
         * 如果不配置，使用内置规则：store|shop|门店
         */
        private String pathFieldPattern;

        /**
         * 查询参数中的字段名称
         * <p>
         * 如果不配置，使用内置规则：storeId
         */
        private String queryFieldName;

        /**
         * 请求体（JSON）中的字段名称列表（按优先级顺序）
         * <p>
         * 如果不配置，使用内置规则：storeId, shopId, shopCode
         */
        private List<String> bodyFieldNames;
    }

}
