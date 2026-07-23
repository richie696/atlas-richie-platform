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
package com.richie.component.ai.support;

import com.anthropic.models.messages.Model;
import com.richie.component.ai.config.chat.AiChatModelOptions;
import com.richie.component.ai.config.chat.LlmProvider;
import com.richie.component.ai.model.AiRequest;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * 合并模型默认参数与请求级参数，并按 Provider 转换为 Spring AI {@link ChatOptions}。
 */
@Component
public class AiChatOptionsResolver {

    /**
     * 将请求级参数覆盖到模型默认参数之上（请求中非 null 字段优先）。
     */
    public AiChatModelOptions mergeOptions(AiChatModelOptions base,
                                                         AiRequest.ModelOptions requestOverride) {
        if (requestOverride == null) {
            return base;
        }
        AiChatModelOptions merged = copyOptions(base);
        if (requestOverride.getModel() != null) {
            merged.setModel(requestOverride.getModel());
        }
        if (requestOverride.getMaxTokens() != null) {
            merged.setMaxTokens(requestOverride.getMaxTokens());
        }
        if (requestOverride.getTemperature() != null) {
            merged.setTemperature(requestOverride.getTemperature());
        }
        if (requestOverride.getTopP() != null) {
            merged.setTopP(requestOverride.getTopP());
        }
        if (requestOverride.getTopK() != null) {
            merged.setTopK(requestOverride.getTopK());
        }
        if (requestOverride.getFrequencyPenalty() != null) {
            merged.setFrequencyPenalty(requestOverride.getFrequencyPenalty());
        }
        if (requestOverride.getPresencePenalty() != null) {
            merged.setPresencePenalty(requestOverride.getPresencePenalty());
        }
        if (requestOverride.getStop() != null) {
            merged.setStop(requestOverride.getStop());
        }
        if (requestOverride.getLogprobs() != null) {
            merged.setLogprobs(requestOverride.getLogprobs());
        }
        if (requestOverride.getTopLogprobs() != null) {
            merged.setTopLogprobs(requestOverride.getTopLogprobs());
        }
        if (requestOverride.getEnableThinking() != null) {
            merged.setEnableThinking(requestOverride.getEnableThinking());
        }
        if (requestOverride.getThinkingBudgetTokens() != null) {
            merged.setThinkingBudgetTokens(requestOverride.getThinkingBudgetTokens());
        }
        return merged;
    }

    /**
     * 是否存在需要在本次请求中显式传入的运行时参数。
     */
    public boolean hasRequestLevelOverride(AiRequest request) {
        return request != null && request.getOptions() != null;
    }

    public ChatOptions toChatOptions(LlmProvider provider,
                                     AiChatModelOptions options) {
        return switch (provider) {
            case OPENAI, ZHIPUAI, MOONSHOT, MINIMAX -> toOpenAiChatOptions(options);
            case DEEPSEEK -> toDeepSeekChatOptions(options);
            case ANTHROPIC -> toAnthropicChatOptions(options);
            case OLLAMA -> toOllamaChatOptions(options);
        };
    }

    public OpenAiChatOptions toOpenAiChatOptions(AiChatModelOptions options) {
        return buildOpenAi(options);
    }

    public DeepSeekChatOptions toDeepSeekChatOptions(AiChatModelOptions options) {
        return buildDeepSeek(options);
    }

    public AnthropicChatOptions toAnthropicChatOptions(AiChatModelOptions options) {
        return buildAnthropic(options);
    }

    public OllamaChatOptions toOllamaChatOptions(AiChatModelOptions options) {
        return buildOllama(options);
    }

    private AiChatModelOptions copyOptions(AiChatModelOptions base) {
        AiChatModelOptions copy = new AiChatModelOptions();
        if (base == null) {
            return copy;
        }
        copy.setModel(base.getModel());
        copy.setMaxTokens(base.getMaxTokens());
        copy.setTemperature(base.getTemperature());
        copy.setTopP(base.getTopP());
        copy.setTopK(base.getTopK());
        copy.setFrequencyPenalty(base.getFrequencyPenalty());
        copy.setPresencePenalty(base.getPresencePenalty());
        if (base.getStop() != null) {
            copy.setStop(new ArrayList<>(base.getStop()));
        }
        copy.setLogprobs(base.getLogprobs());
        copy.setTopLogprobs(base.getTopLogprobs());
        copy.setEnableThinking(base.getEnableThinking());
        copy.setThinkingBudgetTokens(base.getThinkingBudgetTokens());
        return copy;
    }

    private OpenAiChatOptions buildOpenAi(AiChatModelOptions options) {
        if (options == null) {
            return OpenAiChatOptions.builder().build();
        }
        var builder = OpenAiChatOptions.builder();
        applyModelName(builder, options.getModel());
        if (options.getMaxTokens() != null) {
            builder.maxTokens(options.getMaxTokens());
        }
        if (options.getTemperature() != null) {
            builder.temperature(options.getTemperature());
        }
        if (options.getTopP() != null) {
            builder.topP(options.getTopP());
        }
        if (options.getFrequencyPenalty() != null) {
            builder.frequencyPenalty(options.getFrequencyPenalty());
        }
        if (options.getPresencePenalty() != null) {
            builder.presencePenalty(options.getPresencePenalty());
        }
        if (options.getStop() != null && !options.getStop().isEmpty()) {
            builder.stopSequences(options.getStop());
        }
        if (options.getLogprobs() != null) {
            builder.logprobs(options.getLogprobs());
        }
        if (options.getTopLogprobs() != null) {
            builder.topLogprobs(options.getTopLogprobs());
        }
        return builder.build();
    }

    private DeepSeekChatOptions buildDeepSeek(AiChatModelOptions options) {
        if (options == null) {
            return DeepSeekChatOptions.builder().build();
        }
        var builder = DeepSeekChatOptions.builder();
        applyDeepSeekModel(builder, options.getModel());
        if (options.getMaxTokens() != null) {
            builder.maxTokens(options.getMaxTokens());
        }
        if (options.getTemperature() != null) {
            builder.temperature(options.getTemperature());
        }
        if (options.getTopP() != null) {
            builder.topP(options.getTopP());
        }
        if (options.getFrequencyPenalty() != null) {
            builder.frequencyPenalty(options.getFrequencyPenalty());
        }
        if (options.getPresencePenalty() != null) {
            builder.presencePenalty(options.getPresencePenalty());
        }
        if (options.getStop() != null && !options.getStop().isEmpty()) {
            builder.stop(options.getStop());
        }
        if (options.getLogprobs() != null) {
            builder.logprobs(options.getLogprobs());
        }
        if (options.getTopLogprobs() != null) {
            builder.topLogprobs(options.getTopLogprobs());
        }
        return builder.build();
    }

    private AnthropicChatOptions buildAnthropic(AiChatModelOptions options) {
        if (options == null) {
            return AnthropicChatOptions.builder().build();
        }
        var builder = AnthropicChatOptions.builder();
        if (options.getModel() != null) {
            builder.model(Model.Companion.of(options.getModel()));
        }
        if (options.getMaxTokens() != null) {
            builder.maxTokens(options.getMaxTokens());
        }
        if (options.getTemperature() != null) {
            builder.temperature(options.getTemperature());
        }
        if (options.getTopP() != null) {
            builder.topP(options.getTopP());
        }
        if (options.getTopK() != null) {
            builder.topK(options.getTopK());
        }
        return builder.build();
    }

    private OllamaChatOptions buildOllama(AiChatModelOptions options) {
        if (options == null) {
            return OllamaChatOptions.builder().build();
        }
        var builder = OllamaChatOptions.builder();
        applyOllamaModel(builder, options.getModel());
        if (options.getMaxTokens() != null) {
            builder.numPredict(options.getMaxTokens());
        }
        if (options.getTemperature() != null) {
            builder.temperature(options.getTemperature());
        }
        if (options.getTopP() != null) {
            builder.topP(options.getTopP());
        }
        if (options.getStop() != null && !options.getStop().isEmpty()) {
            builder.stop(options.getStop());
        }
        return builder.build();
    }

    private void applyModelName(org.springframework.ai.model.tool.DefaultToolCallingChatOptions.Builder<?> builder,
                                String modelName) {
        if (modelName != null) {
            builder.model(modelName);
        }
    }

    private void applyDeepSeekModel(DeepSeekChatOptions.Builder builder, String modelName) {
        if (modelName == null) {
            return;
        }
        for (DeepSeekApi.ChatModel chatModel : DeepSeekApi.ChatModel.values()) {
            if (modelName.equals(chatModel.getValue()) || modelName.equalsIgnoreCase(chatModel.name())) {
                builder.model(chatModel);
                return;
            }
        }
        builder.model(modelName);
    }

    private void applyOllamaModel(OllamaChatOptions.Builder builder, String modelName) {
        if (modelName == null) {
            return;
        }
        for (OllamaModel ollamaModel : OllamaModel.values()) {
            if (modelName.equals(ollamaModel.id()) || modelName.equalsIgnoreCase(ollamaModel.name())) {
                builder.model(ollamaModel);
                return;
            }
        }
        builder.model(modelName);
    }
}
