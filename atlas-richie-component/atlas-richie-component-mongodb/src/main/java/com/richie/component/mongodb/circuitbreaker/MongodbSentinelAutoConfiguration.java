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
package com.richie.component.mongodb.circuitbreaker;

import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(MongodbCircuitBreakerProperties.class)
public class MongodbSentinelAutoConfiguration {

    private final MongodbCircuitBreakerProperties properties;
    private final Environment environment;

    public MongodbSentinelAutoConfiguration(MongodbCircuitBreakerProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Bean
    public MongodbSentinelAspect mongodbSentinelAspect() {
        return new MongodbSentinelAspect();
    }

    @Bean
    public NacosDataSource<List<DegradeRule>> nacosDegradeRuleDataSource() {
        String nacosAddress = environment.getProperty("spring.cloud.nacos.discovery.server-addr", "127.0.0.1:8848");
        String group = properties.getNacosGroup();
        String dataId = properties.getNacosDataId();

        Converter<String, List<DegradeRule>> converter = new SentinelDegradeRuleConverter();
        NacosDataSource<List<DegradeRule>> dataSource = new NacosDataSource<>(nacosAddress, group, dataId, converter);

        List<DegradeRule> defaultRules = createDefaultRules();
        DegradeRuleManager.loadRules(defaultRules);

        return dataSource;
    }

    private List<DegradeRule> createDefaultRules() {
        List<DegradeRule> rules = new ArrayList<>();

        String[] resources = {"mongodb.query", "mongodb.update", "mongodb.delete",
                "mongodb.insert", "mongodb.save", "mongodb.findById",
                "mongodb.existsById", "mongodb.deleteById", "mongodb.dropCollection"};

        for (String resource : resources) {
            DegradeRule rule = new DegradeRule();
            rule.setResource(resource);
            rule.setGrade(3);
            rule.setCount(properties.getMaxRt().doubleValue());
            rule.setSlowRatioThreshold(properties.getSlowRatioThreshold().floatValue());
            rule.setTimeWindow(properties.getTimeWindow());
            rule.setMinRequestAmount(properties.getMinRequestAmount());
            rule.setStatIntervalMs(properties.getStatIntervalMs().intValue());
            rules.add(rule);
        }

        return rules;
    }

    static class SentinelDegradeRuleConverter implements Converter<String, List<DegradeRule>> {
        @Override
        public List<DegradeRule> convert(String source) {
            if (source == null || source.isEmpty()) {
                return new ArrayList<>();
            }
            return JSON.parseObject(source, new TypeReference<List<DegradeRule>>() {});
        }
    }
}
