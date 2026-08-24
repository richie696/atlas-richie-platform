/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.starter;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * 为刷新 prepare 阶段构造隔离的候选 Environment。
 *
 * <p>业务参与者只能看到候选值，真实 Environment 在所有参与者 prepare/commit 成功前
 * 保持上一份 PropertySource，避免失败候选被并发读取。</p>
 */
final class SecretCandidateEnvironment extends StandardEnvironment {

    SecretCandidateEnvironment(ConfigurableEnvironment source) {
        MutablePropertySources target = getPropertySources();
        target.stream().map(PropertySource::getName).toList().forEach(target::remove);
        for (PropertySource<?> propertySource : source.getPropertySources()) {
            target.addLast(propertySource);
        }
        setActiveProfiles(source.getActiveProfiles());
        setDefaultProfiles(source.getDefaultProfiles());
        setConversionService(source.getConversionService());
    }
}
