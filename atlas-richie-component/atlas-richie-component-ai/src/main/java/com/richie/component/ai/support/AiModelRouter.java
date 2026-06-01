package com.richie.component.ai.support;

import com.richie.component.ai.config.AiModelProperties;
import com.richie.component.ai.model.AiRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 解析模型调用链：主模型 + 场景路由 + fallback 降级。
 */
@Component
public class AiModelRouter {

    public List<String> resolveModelChain(AiRequest request,
                                          String defaultModel,
                                          Map<String, ?> availableClients,
                                          AiModelProperties properties) {
        List<String> chain = new ArrayList<>();
        AiModelProperties.RoutingConfig routing = properties.getRouting();

        if (request != null && request.getModelName() != null && !request.getModelName().isBlank()) {
            chain.add(request.getModelName());
        } else if (routing.isEnabled()) {
            appendSceneModels(chain, request, routing);
        }

        if (chain.isEmpty() && defaultModel != null && !defaultModel.isBlank()) {
            chain.add(defaultModel);
        }

        if (routing.isFallbackEnabled()) {
            appendFallbacks(chain, request, routing);
        }

        return filterAvailable(distinct(chain), availableClients);
    }

    private void appendSceneModels(List<String> chain, AiRequest request, AiModelProperties.RoutingConfig routing) {
        if (request == null || request.getScene() == null || request.getScene().isBlank()) {
            return;
        }
        List<String> sceneModels = routing.getSceneRules().get(request.getScene());
        if (sceneModels != null) {
            chain.addAll(sceneModels);
        }
    }

    private void appendFallbacks(List<String> chain, AiRequest request, AiModelProperties.RoutingConfig routing) {
        if (request != null && request.getFallbackModelNames() != null) {
            chain.addAll(request.getFallbackModelNames());
        }
        if (routing.getFallbackModels() != null) {
            chain.addAll(routing.getFallbackModels());
        }
    }

    private List<String> distinct(List<String> chain) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String model : chain) {
            if (model == null || model.isBlank()) {
                continue;
            }
            if (seen.add(model)) {
                result.add(model);
            }
        }
        return result;
    }

    private List<String> filterAvailable(List<String> chain, Map<String, ?> availableClients) {
        if (availableClients == null || availableClients.isEmpty()) {
            return chain;
        }
        return chain.stream().filter(availableClients::containsKey).toList();
    }
}
