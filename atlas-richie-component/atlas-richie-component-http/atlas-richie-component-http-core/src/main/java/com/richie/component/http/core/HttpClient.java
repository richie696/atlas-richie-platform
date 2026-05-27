package com.richie.component.http.core;

import tools.jackson.core.type.TypeReference;

import java.util.concurrent.CompletableFuture;

/**
 * 统一的 HTTP 客户端门面接口。
 * <p>
 * 实现类通过 Spring Boot 自动配置按 {@code platform.component.http.provider} 加载，
 * 支持 OkHttp、HttpClient5、Spring RestClient 三种底层实现，调用方代码无需感知。
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 *   @Autowired
 *   private HttpClient http;
 *
 *   User user = http.get("https://api/users/123").execute(User.class);
 *   String json = http.post("https://api/users", newUser).execute();
 *
 *   // Future 方式
 *   CompletableFuture<User> future = http.get("https://api/users").future(User.class);
 *   future.thenAccept(user -> log.info("Got: {}", user));
 * }</pre>
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
public interface HttpClient {

    HttpRequest get(String url);

    HttpRequest post(String url, Object body);

    HttpRequest post(String url);

    HttpRequest put(String url, Object body);

    HttpRequest delete(String url, Object body);

    HttpRequest delete(String url);

    // ====== for HttpRequest delegation ======

    HttpResponse execute(HttpRequest request);

    <T> T execute(HttpRequest request, Class<T> type);

    <T> T execute(HttpRequest request, TypeReference<T> typeRef);

    <T> void async(HttpRequest request, AsyncCallback<T> callback, Class<T> type);

    <T> void async(HttpRequest request, AsyncCallback<T> callback, TypeReference<T> typeRef);

    <T> CompletableFuture<T> future(HttpRequest request, Class<T> type);

    <T> CompletableFuture<T> future(HttpRequest request, TypeReference<T> typeRef);

}
