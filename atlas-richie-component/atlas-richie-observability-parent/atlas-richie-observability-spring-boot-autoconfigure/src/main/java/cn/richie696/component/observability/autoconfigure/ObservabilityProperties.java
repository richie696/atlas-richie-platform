/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Atlas Richie 可观测性应用级配置。
 */
@ConfigurationProperties(prefix = "atlas.observability")
public class ObservabilityProperties {

    /**
     * 总开关。默认开启，生产环境建议显式配置。
     */
    private boolean enabled = true;

    /**
     * 额外的 OTel service.namespace。为空时由标准 OTel Resource 配置决定。
     */
    private String serviceNamespace;

    /**
     * 应用重要性标签。
     */
    private String criticality = "normal";

    /**
     * 是否执行日志和 Span 属性脱敏。
     */
    private boolean redactionEnabled = true;

    /**
     * 单个观测属性最大长度。
     */
    private int maxAttributeLength = 512;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServiceNamespace() {
        return serviceNamespace;
    }

    public void setServiceNamespace(String serviceNamespace) {
        this.serviceNamespace = serviceNamespace;
    }

    public String getCriticality() {
        return criticality;
    }

    public void setCriticality(String criticality) {
        this.criticality = criticality;
    }

    public boolean isRedactionEnabled() {
        return redactionEnabled;
    }

    public void setRedactionEnabled(boolean redactionEnabled) {
        this.redactionEnabled = redactionEnabled;
    }

    public int getMaxAttributeLength() {
        return maxAttributeLength;
    }

    public void setMaxAttributeLength(int maxAttributeLength) {
        this.maxAttributeLength = maxAttributeLength;
    }
}
