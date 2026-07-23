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
package com.richie.component.ai.api.voicechat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ArchUnit 静态守护 — 原则 J 包隔离规则 (RN.4-alpha voice-chat SPI 范围内)。
 *
 * <p>核心约束(摘自 R-N DESIGN §14 原则 J):
 * <ul>
 *   <li>{@code api/voicechat/} 包不能依赖 {@code provider/} 包 — 业务抽象层与厂商实现层物理隔离</li>
 *   <li>{@code service/voicechat/} 包不能依赖 {@code provider/} 包 — 业务门面层同样不暴露 vendor</li>
 *   <li>反方向允许 — {@code provider/} 可以依赖 {@code api/} 和 {@code support/}</li>
 * </ul>
 *
 * <p>注意:本测试仅守护 RN.4-alpha 新增的 voicechat SPI (api/voicechat/* + service/voicechat/*),
 * 不覆盖既有 {@code service/impl/AiMultimodalServiceImpl} (其依赖 MultimodalModelFactory 是历史代码,
 * 不在本轮原则 J 适用范围内 — 原则 J 适用于 RN.4-alpha 新增的 voicechat 模块)。
 *
 * <p>新 vendor 接入 voice-chat 时违反此约束会被本测试直接 fail,强制走 SPI 抽象。
 */
class VoiceChatModelArchTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.richie.component.ai");

    /**
     * 场景 1 — api/voicechat/* 不能依赖 provider/* 包 (vendor 实现)。
     *
     * <p>允许:
     * <ul>
     *   <li>{@code api/voicechat/StsTicket} 引用 {@code support/sign/StsSigner}</li>
     *   <li>{@code api/voicechat/VoiceChatModel} 引用 {@code support/sign/*}</li>
     * </ul>
     *
     * <p>禁止:
     * <ul>
     *   <li>{@code api/voicechat/*.java} 引用 {@code provider/zhipu/} / {@code provider/doubao/} 等</li>
     * </ul>
     */
    @Test
    void api_voicechat_should_not_depend_on_provider_package() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.richie.component.ai.api.voicechat..")
                .should().dependOnClassesThat().resideInAPackage("..provider..");
        rule.check(CLASSES);
    }

    /**
     * 场景 2 — service/voicechat/* 接口不能依赖 provider/* 包 (业务门面不暴露 vendor)。
     * <p>
     * 注意:仅守护本规则适用于 R-N.4 新增的 voicechat SPI(接口层),不覆盖预存在的
     * {@code AiMultimodalServiceImpl} — 后者按设计需要调 {@code MultimodalModelFactory}
     * 走 vendor 分派,属历史职责。
     */
    @Test
    void service_voicechat_should_not_depend_on_provider_package() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.richie.component.ai.service.voicechat..")
                .should().dependOnClassesThat().resideInAPackage("..provider..")
                .allowEmptyShould(true);
        rule.check(CLASSES);
    }

    @Test
    void spi_interfaces_should_reside_in_api_or_support_package() {
        String[] apiTypes = {"StsTicket", "VoiceChatConfig", "VoiceChatEvent", "VoiceConversation", "VoiceChatModel"};
        for (String type : apiTypes) {
            try {
                Class.forName("com.richie.component.ai.api.voicechat." + type);
            } catch (ClassNotFoundException e) {
                throw new AssertionError(type + " 必须存在于 api/voicechat/ 包 — 缺失 SPI 顶层抽象", e);
            }
        }
        String[] supportTypes = {"StsSigner", "VendorStsContext"};
        for (String type : supportTypes) {
            try {
                Class.forName("com.richie.component.ai.support.sign." + type);
            } catch (ClassNotFoundException e) {
                throw new AssertionError(type + " 必须存在于 support/sign/ 包 — 缺失 STS 签发抽象", e);
            }
        }
    }
}