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
package com.richie.component.web.core.config.hang;

import com.richie.component.web.core.hang.HangDetectionInterceptor;
import com.richie.component.web.core.hang.WatchdogScheduler;
import com.richie.component.web.core.hook.DefaultHookBus;
import com.richie.component.web.core.hook.HookBus;
import com.richie.component.web.core.metrics.WebMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@Slf4j
@AutoConfiguration
@ConditionalOnClass(HangDetectionInterceptor.class)
@EnableConfigurationProperties(HangDetectionProperties.class)
public class HangAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public WatchdogScheduler watchdogScheduler() {
        log.info("HangAutoConfiguration: creating default WatchdogScheduler");
        return WatchdogScheduler.getDefault();
    }

    @Bean
    @ConditionalOnMissingBean
    public HookBus hookBus() {
        log.info("HangAutoConfiguration: creating default HookBus");
        return new DefaultHookBus();
    }

    /**
     * 三档阈值链：{@code warnMs ≤ dumpMs ≤ killSwitchMs}。若用户仅配 {@code thresholdMillis}（旧字段），
     * 等价于 {@code warnMs=thresholdMillis, dumpMs=warnMs+10000, killSwitchMs=warnMs+60000}（向后兼容）。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "platform.component.web.hang", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public HangDetectionInterceptor hangDetectionInterceptor(
            HangDetectionProperties properties,
            WatchdogScheduler scheduler,
            HookBus hookBus,
            WebMetrics webMetrics) {
        long warnMs = effectiveWarnMs(properties);
        long dumpMs = effectiveDumpMs(properties, warnMs);
        long killSwitchMs = effectiveKillSwitchMs(properties, warnMs);
        log.info("HangAutoConfiguration: HangDetectionInterceptor threshold-chain warn={}ms dump={}ms killSwitch={}ms dumpEnabled={}",
                warnMs, dumpMs, killSwitchMs, properties.isDumpEnabled());
        return new HangDetectionInterceptor(
                warnMs, dumpMs, killSwitchMs, properties.isDumpEnabled(),
                scheduler, hookBus, webMetrics);
    }

    private static long effectiveWarnMs(HangDetectionProperties p) {
        return p.getWarnMs() > 0 ? p.getWarnMs() : p.getThresholdMillis();
    }

    private static long effectiveDumpMs(HangDetectionProperties p, long warnMs) {
        long dump = p.getDumpMs();
        return dump > 0 ? dump : warnMs + 10_000L;
    }

    private static long effectiveKillSwitchMs(HangDetectionProperties p, long warnMs) {
        long kill = p.getKillSwitchMs();
        return kill > 0 ? kill : warnMs + 60_000L;
    }
}