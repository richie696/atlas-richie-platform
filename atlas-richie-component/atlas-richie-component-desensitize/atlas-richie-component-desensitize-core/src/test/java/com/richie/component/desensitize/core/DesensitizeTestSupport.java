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
package com.richie.component.desensitize.core;

import com.richie.component.desensitize.core.config.DesensitizeProperties;
import com.richie.component.desensitize.core.model.MaskType;
import com.richie.component.desensitize.core.permission.DefaultMaskPermissionEvaluator;
import com.richie.component.desensitize.core.registry.MaskRuleRegistry;
import com.richie.component.desensitize.core.registry.SensitiveKeyRegistry;
import com.richie.component.desensitize.core.serializer.SafeLogSerializer;
import com.richie.component.desensitize.core.service.DefaultMaskingService;
import com.richie.component.desensitize.core.service.DefaultObjectMaskingService;
import com.richie.component.desensitize.core.service.MaskingService;
import com.richie.component.desensitize.core.service.ObjectMaskingService;
import com.richie.component.desensitize.core.strategy.AddressMaskingStrategy;
import com.richie.component.desensitize.core.strategy.BankCardMaskingStrategy;
import com.richie.component.desensitize.core.strategy.EmailMaskingStrategy;
import com.richie.component.desensitize.core.strategy.IdCardMaskingStrategy;
import com.richie.component.desensitize.core.strategy.MaskingStrategyRegistry;
import com.richie.component.desensitize.core.strategy.NameMaskingStrategy;
import com.richie.component.desensitize.core.strategy.PasswordMaskingStrategy;
import com.richie.component.desensitize.core.strategy.PhoneMaskingStrategy;
import com.richie.component.desensitize.core.util.DesensitizeUtils;

import java.util.List;
import java.util.Map;
/**
 * DesensitizeTestSupport 测试类。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
public final class DesensitizeTestSupport {

    private DesensitizeTestSupport() {
    }
    /**
     * defaultProperties。
     * @return 处理结果
     */
    public static DesensitizeProperties defaultProperties() {
        DesensitizeProperties properties = new DesensitizeProperties();
        properties.getSensitiveKeys().put("phone", MaskType.PHONE);
        properties.getSensitiveKeys().put("mobile", MaskType.PHONE);
        properties.getSensitiveKeys().put("idCard", MaskType.ID_CARD);
        properties.getSensitiveKeys().put("id_card", MaskType.ID_CARD);
        return properties;
    }
    /**
     * maskingService。
     * @param properties 参数
     * @return 处理结果
     */
    public static MaskingService maskingService(DesensitizeProperties properties) {
        MaskingStrategyRegistry strategyRegistry = new MaskingStrategyRegistry(defaultStrategies());
        MaskRuleRegistry ruleRegistry = new MaskRuleRegistry(properties);
        SensitiveKeyRegistry keyRegistry = new SensitiveKeyRegistry(properties);
        return new DefaultMaskingService(
                properties,
                strategyRegistry,
                ruleRegistry,
                keyRegistry,
                new DefaultMaskPermissionEvaluator(properties));
    }
    /**
     * defaultMaskingService。
     * @return 处理结果
     */
    public static MaskingService defaultMaskingService() {
        return maskingService(defaultProperties());
    }
    /**
     * defaultObjectMaskingService。
     * @param maskingService 参数
     * @param properties 参数
     * @return 处理结果
     */
    public static ObjectMaskingService defaultObjectMaskingService(MaskingService maskingService, DesensitizeProperties properties) {
        MaskRuleRegistry ruleRegistry = new MaskRuleRegistry(properties);
        return new DefaultObjectMaskingService(new SafeLogSerializer(maskingService, ruleRegistry));
    }
    /**
     * bindUtils。
     * @param maskingService 参数
     * @param objectMaskingService 参数
     * @return 处理结果
     */
    public static void bindUtils(MaskingService maskingService, ObjectMaskingService objectMaskingService) {
        DesensitizeUtils.bind(maskingService, objectMaskingService);
    }
    /**
     * bindDefaults。
     * @return 处理结果
     */
    public static void bindDefaults() {
        DesensitizeProperties properties = defaultProperties();
        MaskingService maskingService = maskingService(properties);
        bindUtils(maskingService, defaultObjectMaskingService(maskingService, properties));
    }
    /**
     * clearUtils。
     * @return 处理结果
     */
    public static void clearUtils() {
        DesensitizeUtils.clear();
    }
    /**
     * defaultStrategies。
     * @return 处理结果
     */
    public static List<com.richie.component.desensitize.core.strategy.MaskingStrategy> defaultStrategies() {
        return List.of(
                new PhoneMaskingStrategy(),
                new IdCardMaskingStrategy(),
                new BankCardMaskingStrategy(),
                new EmailMaskingStrategy(),
                new NameMaskingStrategy(),
                new PasswordMaskingStrategy(),
                new AddressMaskingStrategy()
        );
    }
    /**
     * sampleRow。
     * @return 处理结果
     */
    public static Map<String, Object> sampleRow() {
        return Map.of(
                "phone", "13812348000",
                "orderId", "13812348000",
                "name", "张三丰"
        );
    }
}
