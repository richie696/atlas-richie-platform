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
package com.richie.component.mongodb.circuitbreaker;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MongoDB 断路器的配置属性。
 *
 * @author richie696
 * @version 1.0
 * @since 2026-06-07
 */
@ConfigurationProperties(prefix = "platform.component.mongodb.circuit-breaker")
public class MongodbCircuitBreakerProperties {

    private boolean enabled = true;
    private String nacosDataId = "mongodb-sentinel-degrade-rules";
    private String nacosGroup = "DEFAULT_GROUP";
    private Long maxRt = 100L;
    private Double slowRatioThreshold = 0.5;
    private Integer timeWindow = 10;
    private Integer minRequestAmount = 10;
    private Long statIntervalMs = 1000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNacosDataId() {
        return nacosDataId;
    }

    public void setNacosDataId(String nacosDataId) {
        this.nacosDataId = nacosDataId;
    }

    public String getNacosGroup() {
        return nacosGroup;
    }

    public void setNacosGroup(String nacosGroup) {
        this.nacosGroup = nacosGroup;
    }

    public Long getMaxRt() {
        return maxRt;
    }

    public void setMaxRt(Long maxRt) {
        this.maxRt = maxRt;
    }

    public Double getSlowRatioThreshold() {
        return slowRatioThreshold;
    }

    public void setSlowRatioThreshold(Double slowRatioThreshold) {
        this.slowRatioThreshold = slowRatioThreshold;
    }

    public Integer getTimeWindow() {
        return timeWindow;
    }

    public void setTimeWindow(Integer timeWindow) {
        this.timeWindow = timeWindow;
    }

    public Integer getMinRequestAmount() {
        return minRequestAmount;
    }

    public void setMinRequestAmount(Integer minRequestAmount) {
        this.minRequestAmount = minRequestAmount;
    }

    public Long getStatIntervalMs() {
        return statIntervalMs;
    }

    public void setStatIntervalMs(Long statIntervalMs) {
        this.statIntervalMs = statIntervalMs;
    }
}
