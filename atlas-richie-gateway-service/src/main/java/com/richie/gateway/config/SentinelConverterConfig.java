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
package com.richie.gateway.config;

import com.alibaba.cloud.sentinel.datasource.converter.JsonConverter;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SentinelConverterConfig {
//
//    /**
//     * 网关流控规则转换器
//     * 用于将Nacos配置中心的JSON格式网关流控规则转换为Sentinel可识别的GatewayFlowRule对象
//     * @return 网关流控规则转换器
//     */
//    @Bean("sentinel-json-gw-flow-converter")
//    public JsonConverter<GatewayFlowRule> gatewayFlowConverter() {
//        return new JsonConverter<>(JsonUtils.getInstance().cloneMapper(), GatewayFlowRule.class);
//    }

    /**
     * 网关流控规则转换器
     * 用于将Nacos配置中心的JSON格式网关流控规则转换为Sentinel可识别的GatewayFlowRule对象
     * @return 网关流控规则转换器
     */
    @Bean("sentinel-json-flow-converter")
    public JsonConverter<FlowRule> gatewayFlowConverter() {
        return new JsonConverter<>(new ObjectMapper(), FlowRule.class);
    }

    /**
     * 网关降级规则转换器
     * 用于将Nacos配置中心的JSON格式网关降级规则转换为Sentinel可识别的DegradeRule对象
     * @return 网关降级规则转换器
     */
    @Bean("sentinel-json-gw-degrade-converter")
    public JsonConverter<DegradeRule> gatewayDegradeConverter() {
        return new JsonConverter<>(new ObjectMapper(), DegradeRule.class);
    }

    /**
     * 网关热点参数规则转换器
     * 用于将Nacos配置中心的JSON格式网关热点参数规则转换为Sentinel可识别的ParamFlowRule对象
     * @return 网关热点参数规则转换器
     */
    @Bean("sentinel-json-gw-param-flow-converter")
    public JsonConverter<ParamFlowRule> gatewayParamFlowConverter() {
        return new JsonConverter<>(new ObjectMapper(), ParamFlowRule.class);
    }

    /**
     * 网关授权规则转换器
     * 用于将Nacos配置中心的JSON格式网关授权规则转换为Sentinel可识别的AuthorityRule对象
     * @return 网关授权规则转换器
     */
    @Bean("sentinel-json-gw-authority-converter")
    public JsonConverter<AuthorityRule> gatewayAuthorityConverter() {
        return new JsonConverter<>(new ObjectMapper(), AuthorityRule.class);
    }

}
