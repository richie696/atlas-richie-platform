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
package com.richie.component.redis.streammq.monitor;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Redis Stream 链接清单端点
 *
 * <p>提供固定的 Redis Stream 监控链接清单，便于在 Actuator 根页面直接可见。
 *
 * <p>背景说明：
 * <ul>
 *   <li>Actuator 对使用 @Selector 的端点只展示模板化链接，不会枚举具体路径</li>
 *   <li>该端点汇总常用的具体链接及说明，方便点击与导航</li>
 * </ul>
 *
 * <p>主要功能：
 * <ul>
 *   <li>列出状态页与各类指标端点的固定地址</li>
 *   <li>为每个链接提供简要说明(desc)</li>
 *   <li>给出模板化路径示例以便替换 streamKey</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-09-16
 */
@Component
@Endpoint(id = "redis-stream-links")
@RequiredArgsConstructor
public class RedisStreamLinksEndpoint {

    /**
     * 返回固定的可点击链接清单
     *
     * @return 端点链接与描述
     */
    @ReadOperation
    public Map<String, Object> links() {
        Map<String, Object> links = new LinkedHashMap<>();

        // 总览
        links.put("status", Map.of(
                "href", "/actuator/redis-stream",
                "desc", "Redis Stream 总览：健康、指标摘要、系统信息"
        ));

        // 指标
        links.put("metrics-summary", Map.of(
                "href", "/actuator/redis-stream/metrics/summary",
                "desc", "指标汇总：业务、性能、系统、错误等关键指标的聚合快照"
        ));
        links.put("metrics-business", Map.of(
                "href", "/actuator/redis-stream/metrics/business",
                "desc", "业务指标：发布/消费/确认/失败/重试 计数"
        ));
        links.put("metrics-performance", Map.of(
                "href", "/actuator/redis-stream/metrics/performance",
                "desc", "性能指标：处理/拉取/发布 时延分布"
        ));
        links.put("metrics-system", Map.of(
                "href", "/actuator/redis-stream/metrics/system",
                "desc", "系统指标：活跃消费者/拉取器、积压、连接数"
        ));
        links.put("metrics-errors", Map.of(
                "href", "/actuator/redis-stream/metrics/errors",
                "desc", "错误指标：总错误、超时、连接、序列化等分类计数"
        ));
        links.put("metrics-backlog", Map.of(
                "href", "/actuator/redis-stream/metrics/backlog",
                "desc", "积压指标：各流 pending/backlog 统计（实时刷新）"
        ));

        // 健康刷新
        links.put("health-refresh-get", Map.of(
                "href", "/actuator/redis-stream/health/refresh",
                "desc", "刷新健康检查快照（GET 便捷方式）"
        ));

        // 模板化示例（给出提示说明）
        links.put("stream-info-template", Map.of(
                "href", "/actuator/redis-stream/{streamKey}",
                "desc", "特定 Stream 详情：长度、首末条、消费者组、拉取器状态等"
        ));
        links.put("stream-groups-template", Map.of(
                "href", "/actuator/redis-stream/{streamKey}/groups",
                "desc", "特定 Stream 的消费者组信息"
        ));

        return Map.of(
                "description", "Redis Stream 监控链接清单",
                "links", links
        );
    }
}


