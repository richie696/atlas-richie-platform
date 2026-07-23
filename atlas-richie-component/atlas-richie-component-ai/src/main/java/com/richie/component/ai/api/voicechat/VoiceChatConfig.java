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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 语音对话(双工流式)的统一配置 — 业务侧按 vendor 配置调用,实际差异由 vendor impl 消化。
 * <p>
 * 设计原则 J:业务侧不感知 vendor。配置模型字段覆盖所有 vendor 公共需求,具体 vendor
 * 专有字段放到 {@link #vendorOptions()} Map(impl 自己解析)。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-21
 */
public final class VoiceChatConfig {

    private final String vendor;
    private final String model;
    private final String voice;
    private final String language;
    private final VadMode vadMode;
    private final boolean interruptOnSpeech;
    private final int sampleRateHz;
    private final AudioFormat inputFormat;
    private final AudioFormat outputFormat;
    private final Map<String, String> vendorOptions;

    private VoiceChatConfig(Builder b) {
        this.vendor = b.vendor == null ? "" : b.vendor;
        this.model = Objects.requireNonNull(b.model, "model");
        this.voice = b.voice;
        this.language = b.language == null ? "zh-CN" : b.language;
        this.vadMode = b.vadMode == null ? VadMode.SERVER : b.vadMode;
        this.interruptOnSpeech = b.interruptOnSpeech;
        this.sampleRateHz = b.sampleRateHz <= 0 ? 24000 : b.sampleRateHz;
        this.inputFormat = b.inputFormat == null ? AudioFormat.PCM_16K_MONO : b.inputFormat;
        this.outputFormat = b.outputFormat == null ? AudioFormat.PCM_24K_MONO : b.outputFormat;
        this.vendorOptions = b.vendorOptions == null ? Map.of() : Map.copyOf(b.vendorOptions);
    }

    /**
     * 目标厂商标识,见 {@code StsTicket#VENDOR_*} 系列常量。
     *
     * <p>业务侧通常不直接设置此字段,而是通过 {@code VoiceChatService.open(businessName, config)} 注入 —
     * {@code businessName} 在配置文件中映射到具体 vendor。
     *
     * <p>设置此字段后, {@link VoiceChatModel#supports(VoiceChatConfig)} 会按 vendor 路由。
     */
    public String vendor() { return vendor; }

    public String model() { return model; }
    public String voice() { return voice; }
    public String language() { return language; }
    public VadMode vadMode() { return vadMode; }
    public boolean interruptOnSpeech() { return interruptOnSpeech; }
    public int sampleRateHz() { return sampleRateHz; }
    public AudioFormat inputFormat() { return inputFormat; }
    public AudioFormat outputFormat() { return outputFormat; }
    public Map<String, String> vendorOptions() { return vendorOptions; }

    public enum VadMode {
        /** 服务端 VAD(vendor 自行检测语音结束) */
        SERVER,
        /** 客户端 VAD(业务侧自行控制发送/结束) */
        CLIENT
    }

    public enum AudioFormat {
        PCM_8K_MONO(8000, 1, 16),
        PCM_16K_MONO(16000, 1, 16),
        PCM_24K_MONO(24000, 1, 16),
        PCM_48K_MONO(48000, 1, 16),
        WAV_16K_MONO(16000, 1, 16),
        MP3_24K_MONO(24000, 1, 0);

        private final int sampleRate;
        private final int channels;
        private final int bitsPerSample;

        AudioFormat(int sampleRate, int channels, int bitsPerSample) {
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.bitsPerSample = bitsPerSample;
        }

        public int sampleRate() { return sampleRate; }
        public int channels() { return channels; }
        public int bitsPerSample() { return bitsPerSample; }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Builder() {
        }
        private String vendor;
        private String model;
        private String voice;
        private String language;
        private VadMode vadMode;
        private boolean interruptOnSpeech;
        private int sampleRateHz;
        private AudioFormat inputFormat;
        private AudioFormat outputFormat;
        private Map<String, String> vendorOptions;

        /**
         * 设置目标厂商,通常由 {@code VoiceChatService} 注入,业务侧无需手动调用。
         */
        public Builder vendor(String vendor) {
            this.vendor = vendor;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder voice(String voice) {
            this.voice = voice;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder vadMode(VadMode mode) {
            this.vadMode = mode;
            return this;
        }

        public Builder interruptOnSpeech(boolean enabled) {
            this.interruptOnSpeech = enabled;
            return this;
        }

        public Builder sampleRateHz(int rate) {
            this.sampleRateHz = rate;
            return this;
        }

        public Builder inputFormat(AudioFormat format) {
            this.inputFormat = format;
            return this;
        }

        public Builder outputFormat(AudioFormat format) {
            this.outputFormat = format;
            return this;
        }

        public Builder vendorOption(String key, String value) {
            if (this.vendorOptions == null) {
                this.vendorOptions = new LinkedHashMap<>();
            }
            this.vendorOptions.put(key, value);
            return this;
        }

        public VoiceChatConfig build() {
            return new VoiceChatConfig(this);
        }
    }
}