package com.richie.component.messaging.controller;

import com.richie.contract.model.ApiResult;
import com.richie.component.messaging.service.MessagingDemoService;
import com.richie.component.messaging.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 消息组件功能测试接口
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-02 10:24:08
 */
@Tag(name = "消息组件功能测试接口")
@RestController
@RequestMapping("/messaging")
@RequiredArgsConstructor
public class MessagingDemoController {

    private final MessagingDemoService messagingDemoService;
    private final UserService userService;

    public record SendRequest(String topic, String content) {
    }

    @Operation(summary = "下发普通消息的接口", description = "指定消息体内容，通过本接口给所有队列订阅方发下消息的接口。")
    @PostMapping("/send")
    public ApiResult<Void> doSend(@RequestBody SendRequest request) {
        return messagingDemoService.doSend(request.topic, request.content);
    }

    public record BinderSendRequest(String topic, String binder, String content) {
    }

    @Operation(summary = "指定binderName进行普通消息下发的接口", description = "多消息队列实例需要指定发给哪一个队列时使用。")
    @PostMapping("/send_by_binder")
    public ApiResult<Void> doSendByKey(@RequestBody BinderSendRequest request) {
        return messagingDemoService.doSend(request.topic, request.binder, request.content);
    }

    @Operation(summary = "下发延时消息的接口", description = "指定消息体内容，通过本接口给所有队列订阅方发下延时消息的接口。")
    @PostMapping("/delay/send")
    public ApiResult<Void> doDelaySend(
            @Parameter(name = "要发送的Topic主题", required = true) @RequestParam("topic") String topic,
            @Parameter(name = "要发送的消息内容", required = true) @RequestParam("content") String content,
            @Parameter(name = "延时发送时长（单位：毫秒）", required = true) @RequestParam("delay") long delayTime) {
        return messagingDemoService.doDelaySend(topic, content, delayTime);
    }

    @GetMapping("/say")
    public String doSayHello(@RequestParam("name") String name) {
        return userService.sayHello(name);
    }
}
