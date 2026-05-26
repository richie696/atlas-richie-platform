package com.richie.component.ai.controller;

import com.richie.component.ai.model.AiModelInfo;
import com.richie.component.ai.model.AiRequest;
import com.richie.component.ai.model.AiResponse;
import com.richie.component.ai.service.AiModelService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai")
public class AiSampleController {

    private final AiModelService aiModelService;

    @GetMapping("/models")
    public List<AiModelInfo> listModels() {
        return aiModelService.getAvailableModels();
    }

    @PostMapping(path = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> chat(@RequestBody ChatRequest req) {
        AiRequest request = toAiRequest(req);
        AiResponse response = StringUtils.hasText(req.getModel())
                ? aiModelService.callWithModel(req.getModel(), request)
                : aiModelService.call(request);

        return Map.of(
                "success", response.isSuccess(),
                "model", response.getModelName(),
                "provider", response.getProvider(),
                "duration", response.getDuration(),
                "usage", response.getUsage(),
                "content", response.getContent(),
                "errorCode", response.getErrorCode(),
                "errorMessage", response.getErrorMessage()
        );
    }

    @PostMapping(path = "/chat/async", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> chatAsync(@RequestBody ChatRequest req) {
        AiRequest request = toAiRequest(req);
        var future = StringUtils.hasText(req.getModel())
                ? aiModelService.callAsync(request) // 简化：演示异步，忽略指定模型版
                : aiModelService.callAsync(request);

        var response = future.join();
        return Map.of(
                "success", response.isSuccess(),
                "model", response.getModelName(),
                "provider", response.getProvider(),
                "duration", response.getDuration(),
                "usage", response.getUsage(),
                "content", response.getContent(),
                "errorCode", response.getErrorCode(),
                "errorMessage", response.getErrorMessage()
        );
    }

    private AiRequest toAiRequest(ChatRequest req) {
        AiRequest request = new AiRequest();
        if (req.getMessages() != null && !req.getMessages().isEmpty()) {
            request.setMessages(req.getMessages().stream().map(m -> {
                AiRequest.Message message = new AiRequest.Message();
                message.setRole(m.getRole());
                message.setContent(m.getContent());
                return message;
            }).toList());
        } else {
            request.setMessages(List.of(new AiRequest.Message().setRole("user").setContent(req.getUser())));
        }
        if (req.getOptions() != null) {
            AiRequest.ModelOptions options = new AiRequest.ModelOptions();
            options.setTemperature(req.getOptions().getTemperature());
            options.setMaxTokens(req.getOptions().getMaxTokens());
            options.setTopP(req.getOptions().getTopP());
            options.setTopK(req.getOptions().getTopK());
            request.setOptions(options);
        }
        request.setMetadata(req.getMetadata());
        return request;
    }

    @Data
    public static class ChatRequest {
        private String model;
        private String system;
        private String user;
        private List<Message> messages;
        private Options options;
        private Map<String, Object> metadata;

        @Data
        public static class Message {
            private String role;   // system/user/assistant
            private String content;
        }

        @Data
        public static class Options {
            private Double temperature;
            private Integer maxTokens;
            private Double topP;
            private Integer topK;
        }
    }
}


