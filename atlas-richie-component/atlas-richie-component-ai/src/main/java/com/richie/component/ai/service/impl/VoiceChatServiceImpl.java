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
package com.richie.component.ai.service.impl;

import com.richie.component.ai.api.voicechat.VoiceChatConfig;
import com.richie.component.ai.api.voicechat.VoiceChatModel;
import com.richie.component.ai.api.voicechat.VoiceConversation;
import com.richie.component.ai.service.VoiceChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * {@link VoiceChatService} 默认实现。
 *
 * <h2>路由策略</h2>
 * <ol>
 *   <li>优先按 {@code config.vendor()} 精确匹配 {@link VoiceChatModel#vendor()}</li>
 *   <li>次选按 {@link VoiceChatModel#supports(VoiceChatConfig)} 检查 vendor + model 双匹配</li>
 *   <li>未找到任何模型时抛 {@link IllegalStateException}(业务侧 fail-fast)</li>
 * </ol>
 *
 * @author richie696
 * @since 2026-07-21
 */
@Service
public class VoiceChatServiceImpl implements VoiceChatService {

    private static final Logger log = LoggerFactory.getLogger(VoiceChatServiceImpl.class);

    private final List<VoiceChatModel> models;

    public VoiceChatServiceImpl(List<VoiceChatModel> models) {
        this.models = models == null ? List.of() : List.copyOf(models);
        log.info("VoiceChatService 初始化完成,已注册 {} 个 VoiceChatModel 实现: {}",
                this.models.size(), this.models.stream().map(VoiceChatModel::vendor).toList());
    }

    @Override
    public VoiceConversation open(VoiceChatConfig config) {
        Objects.requireNonNull(config, "config 不能为空");
        if (config.vendor() == null || config.vendor().isEmpty()) {
            throw new IllegalArgumentException(
                    "config.vendor() 不能为空,请通过 open(businessName, config) 走配置映射,或显式设置 vendor 字段");
        }
        VoiceChatModel model = resolve(config);
        log.info("VoiceChatService 打开会话: vendor={}, model={}, voice={}",
                model.vendor(), config.model(), config.voice());
        return model.open(config);
    }

    @Override
    public VoiceConversation open(String businessName, VoiceChatConfig config) {
        Objects.requireNonNull(businessName, "businessName 不能为空");
        Objects.requireNonNull(config, "config 不能为空");
        String vendor = resolveBusinessName(businessName);
        VoiceChatConfig.Builder builder = VoiceChatConfig.builder()
                .vendor(vendor)
                .model(config.model())
                .voice(config.voice())
                .language(config.language())
                .vadMode(config.vadMode())
                .interruptOnSpeech(config.interruptOnSpeech())
                .sampleRateHz(config.sampleRateHz())
                .inputFormat(config.inputFormat())
                .outputFormat(config.outputFormat());
        if (config.vendorOptions() != null) {
            for (var e : config.vendorOptions().entrySet()) {
                builder.vendorOption(e.getKey(), e.getValue());
            }
        }
        return open(builder.build());
    }

    @Override
    public List<String> listRegisteredVendors() {
        List<String> vendors = new ArrayList<>(models.size());
        for (VoiceChatModel m : models) {
            vendors.add(m.vendor());
        }
        return Collections.unmodifiableList(vendors);
    }

    @Override
    public int modelCount() {
        return models.size();
    }

    private VoiceChatModel resolve(VoiceChatConfig config) {
        for (VoiceChatModel m : models) {
            if (m.vendor().equals(config.vendor())) {
                return m;
            }
        }
        for (VoiceChatModel m : models) {
            if (m.supports(config)) {
                return m;
            }
        }
        throw new IllegalStateException(String.format(
                "VoiceChatService 未找到能处理 config 的 VoiceChatModel: vendor=%s, model=%s。"
                        + "请检查 application.yml 是否配置了对应 vendor 的 model 实现。已注册 vendor: %s",
                config.vendor(), config.model(), listRegisteredVendors()));
    }

    private String resolveBusinessName(String businessName) {
        return businessName;
    }
}