package com.richie.component.http.controller;

import com.richie.component.http.core.AsyncCallback;
import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.HttpResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP 组件测试控制器。
 * <p>
 * 使用公开的 httpbin 地址验证各种调用方式与请求方法。
 *
 * @author richie696
 * @version 1.0
 * @since 1.0.0
 */
@Tag(name = "HTTP组件测试接口")
@RequestMapping("/http-sample")
@RestController
@RequiredArgsConstructor
public class HttpSampleController {

    private final HttpClient httpClient;

    @GetMapping("/methods")
    public Map<String, Object> methods() {

        HttpResponse getResp = httpClient.get("https://httpbin.org/get")
                .param("keyword", "atlas richie")
                .param("lang", "zh-CN")
                .execute();

        HttpResponse postResp = httpClient
                .post("https://httpbin.org/post", Map.of(
                        "name", "richie",
                        "from", "sample-http")
                )
                .header("Authorization", "Bearer sample-http")
                .timeout(Duration.ofSeconds(10))
                .asJson()
                .execute();
        HttpResponse putResp = httpClient.put("https://httpbin.org/put", Map.of("enabled", true))
                .execute();
        HttpResponse deleteResp = httpClient.delete("https://httpbin.org/delete")
                .execute();

        return Map.of(
                "getStatus", getResp.statusCode(),
                "postStatus", postResp.statusCode(),
                "putStatus", putResp.statusCode(),
                "deleteStatus", deleteResp.statusCode(),
                "getContainsArgs", getResp.bodyAsString() != null && getResp.bodyAsString().contains("\"args\""),
                "postContainsJson", postResp.bodyAsString() != null && postResp.bodyAsString().contains("\"json\""));
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        HttpResponse ok = httpClient.get("https://httpbin.org/status/200").execute();
        HttpResponse notFound = httpClient.get("https://httpbin.org/status/404").execute();
        return Map.of("status200", ok.statusCode(), "status404", notFound.statusCode());
    }

    @GetMapping("/typed")
    public Map<String, Object> typed() {
        var byClass = httpClient.get("https://httpbin.org/anything").execute(Map.class);
        Map<String, Object> byTypeRef = httpClient.get("https://httpbin.org/get")
                .param("page", "1")
                .execute(new TypeReference<Map<String, Object>>() {
                });
        return Map.of(
                "classTypeUrl", byClass.get("url"),
                "typeRefUrl", byTypeRef.get("url"),
                "classTypeMethod", byClass.get("method"));
    }

    @GetMapping("/async")
    public Map<String, Object> async() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Integer> status = new AtomicReference<>(-1);
        AtomicReference<String> error = new AtomicReference<>("");

        httpClient.post("https://httpbin.org/post", Map.of("mode", "async"))
                .async(new AsyncCallback<>() {
                    @Override
                    public void onFailure(IOException exception) {
                        error.set(exception.getMessage());
                        latch.countDown();
                    }

                    @Override
                    public void onResponse(HttpResponse response, Map<String, Object> data) {
                        status.set(response.statusCode());
                        latch.countDown();
                    }
                }, new TypeReference<Map<String, Object>>() {
                });

        boolean completed = latch.await(15, TimeUnit.SECONDS);
        return Map.of(
                "completed", completed,
                "status", status.get(),
                "error", error.get());
    }

    @GetMapping("/future")
    public Map<String, Object> future() {
        var classFuture = httpClient.get("https://httpbin.org/get").future(Map.class);
        var typeRefFuture = httpClient.post("https://httpbin.org/post", Map.of("mode", "future"))
                .future(new TypeReference<Map<String, Object>>() {
                });

        var classResult = classFuture.join();
        Map<String, Object> typeRefResult = typeRefFuture.join();
        return Map.of(
                "classFutureUrl", classResult.get("url"),
                "typeRefFutureUrl", typeRefResult.get("url"));
    }

    @GetMapping("/timeout")
    public Map<String, Object> timeout() {
        try {
            httpClient.get("https://httpbin.org/delay/3")
                    .timeout(Duration.ofSeconds(1))
                    .execute();
            return Map.of("timeoutTriggered", false);
        } catch (Exception e) {
            return Map.of("timeoutTriggered", true, "message", e.getMessage());
        }
    }
}
