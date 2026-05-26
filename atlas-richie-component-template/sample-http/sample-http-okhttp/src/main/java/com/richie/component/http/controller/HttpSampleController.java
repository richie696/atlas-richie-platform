package com.richie.component.http.controller;

import com.richie.component.http.bean.AsyncCallback;
import com.richie.component.http.bean.HttpResponse;
import com.richie.component.http.client.HttpClientApi;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * API DOC 测试控制器
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-01 20:27:39
 */
@Slf4j
@Tag(name = "ApiDoc测试接口")
@RequestMapping("/api-doc")
@RestController
@RequiredArgsConstructor
public class HttpSampleController {

    private final HttpClientApi httpClientApi;

    @GetMapping("/test")
    public String test(@RequestParam(value = "url", required = false) String url) throws InterruptedException {
        // 公平锁
        final ReentrantLock lock = new ReentrantLock(true);
        // 锁的条件
        final Condition condition = lock.newCondition();
        // 超时定义
        final int WAIT_TIMEOUT = 15;
        final TimeUnit WAIT_TIMEOUT_UNIT = TimeUnit.SECONDS;
        // 结果
        String[] result = new String[1];

        Map<String, String> headers;
        Map<String, String> param;
        if (url == null) {
            url = "https://partner-api.stg-myteksi.com/grabfood-sandbox/partner/v1/order/prepare";
            headers = Map.of("Content-Type", "application/json", "Authorization", "1233");
            param = Map.of("orderID", "123", "toState", "345");
        } else {
            headers = Map.of();
            param = Map.of();
        }


        // 发送异步请求
        httpClientApi.doAsyncPost(String.class, url, param, headers, new AsyncCallback<>() {
            @Override
            public void onFailure(IOException exception) {
                lock.lock();
                // 获取需要返回的值
                result[0] = exception.getMessage();
                condition.signal();
                lock.unlock();
            }

            @Override
            public void onResponse(HttpResponse response, String data) {
                lock.lock();
                // 获取需要返回的值
                result[0] = String.valueOf(response.getCode());
                condition.signal();
                lock.unlock();
            }
        });
        // 对主线程上锁
        lock.lock();
        // 阻塞主线程
        boolean await = condition.await(WAIT_TIMEOUT, WAIT_TIMEOUT_UNIT);
        // 释放锁
        lock.unlock();
        // 如果返回false，则表示网络请求不通
        if (!await) {
            return "error";
        }
        // 否则返回异步请求获得的结果
        return result[0];
    }

}
