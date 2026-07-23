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

import java.util.Map;

/**
 * 语音对话事件 — 双工流式统一事件类型。业务侧按 type 分发,不感知 vendor 私有协议。
 * <p>
 * 设计原则 J:vendor 私有协议字段收敛到 {@link #vendorPayload()},业务侧按 {@link #type()} 消费。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-21
 */
public final class VoiceChatEvent {

    public enum Type {
        /** 语音识别中间结果 */
        TRANSCRIPT_PARTIAL,
        /** 语音识别最终结果 */
        TRANSCRIPT_FINAL,
        /** TTS 音频块 */
        AUDIO_CHUNK,
        /** TTS 句子合成完成 */
        AUDIO_SENTENCE_END,
        /** 工具调用请求 */
        TOOL_CALL_REQUEST,
        /** 用户语音打断 */
        USER_INTERRUPTED,
        /** 服务端错误 */
        ERROR,
        /** 会话结束 */
        SESSION_END
    }

    private final Type type;
    private final String text;
    private final AudioFrame audio;
    private final String toolName;
    private final Map<String, Object> toolArguments;
    private final String errorMessage;
    private final Map<String, Object> vendorPayload;

    private VoiceChatEvent(Builder b) {
        this.type = b.type;
        this.text = b.text;
        this.audio = b.audio;
        this.toolName = b.toolName;
        this.toolArguments = b.toolArguments == null ? Map.of() : Map.copyOf(b.toolArguments);
        this.errorMessage = b.errorMessage;
        this.vendorPayload = b.vendorPayload == null ? Map.of() : Map.copyOf(b.vendorPayload);
    }

    public Type type() { return type; }
    public String text() { return text; }
    public AudioFrame audio() { return audio; }
    public String toolName() { return toolName; }
    public Map<String, Object> toolArguments() { return toolArguments; }
    public String errorMessage() { return errorMessage; }
    public Map<String, Object> vendorPayload() { return vendorPayload; }

    public static Builder builder(Type type) {
        return new Builder(type);
    }

    /**
     * 音频帧载体。
     *
     * <p>封装一段 PCM/WAV/MP3 原始字节流,业务侧不感知 vendor 私有编码差异。
     * 跨 vendor 转码 (例如 PCM ↔ Opus) 由 vendor impl 在内部完成,业务侧只消费统一格式。
     */
    public static final class AudioFrame {
        private final byte[] data;
        private final int sampleRate;
        private final int bitsPerSample;
        private final int channels;
        private final long timestampMs;
        private final String codec;

        public AudioFrame(byte[] data, int sampleRate, int bitsPerSample, int channels, String codec) {
            this.data = data == null ? new byte[0] : data;
            this.sampleRate = sampleRate;
            this.bitsPerSample = bitsPerSample;
            this.channels = channels;
            this.codec = codec == null ? "pcm" : codec;
            this.timestampMs = System.currentTimeMillis();
        }

        public byte[] data() { return data; }
        public int sampleRate() { return sampleRate; }
        public int bitsPerSample() { return bitsPerSample; }
        public int channels() { return channels; }
        public long timestampMs() { return timestampMs; }
        public String codec() { return codec; }
    }

    public static final class Builder {
        private final Type type;
        private String text;
        private AudioFrame audio;
        private String toolName;
        private Map<String, Object> toolArguments;
        private String errorMessage;
        private Map<String, Object> vendorPayload;

        private Builder(Type type) {
            this.type = type;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder audio(AudioFrame audio) {
            this.audio = audio;
            return this;
        }

        public Builder toolCall(String name, Map<String, Object> arguments) {
            this.toolName = name;
            this.toolArguments = arguments;
            return this;
        }

        public Builder error(String message) {
            this.errorMessage = message;
            return this;
        }

        public Builder vendorPayload(Map<String, Object> payload) {
            this.vendorPayload = payload;
            return this;
        }

        public VoiceChatEvent build() {
            return new VoiceChatEvent(this);
        }
    }
}