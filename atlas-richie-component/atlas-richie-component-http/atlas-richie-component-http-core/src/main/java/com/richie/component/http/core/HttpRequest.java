package com.richie.component.http.core;

import tools.jackson.core.type.TypeReference;

import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP 请求配置的链式 Builder。
 * <p>
 * 通过 {@link HttpClient#get(String)} / {@link HttpClient#post(String, Object)} 等方法获得实例，
 * 链式配置请求参数、请求头、超时等，最后调用 {@link #execute()} 或 {@link #async(AsyncCallback, Class)} 执行。
 *
 * <h2>典型用法</h2>
 * <pre>{@code
 *   // 简单 GET
 *   User user = http.get("https://api/users/123").execute(User.class);
 *
 *   // 带参数、请求头、超时的 POST
 *   http.post("https://api/users", newUser)
 *       .header("Authorization", "Bearer xxx")
 *       .timeout(Duration.ofSeconds(30))
 *       .execute();
 *
 *   // SOAP 请求
 *   http.post("https://api/soap", soapXml)
 *       .asSoap()
 *       .execute(SoapResponse.class);
 *
 *   // 异步
 *   http.get("https://api/users")
 *       .params(Map.of("page", "1"))
 *       .async(callback, User.class);
 * }</pre>
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
public class HttpRequest {

    private final String url;
    private final HttpMethod method;
    private final Object body;

    private ContentType contentType = ContentType.DEFAULT;
    private Map<String, String> params;
    private Map<String, String> headerMap;
    private Duration timeout;

    // multipart fields
    private String multipartFieldName;
    private String multipartFileName;
    private InputStream multipartData;

    public HttpRequest(String url, HttpMethod method, Object body) {
        this.url = Objects.requireNonNull(url, "url must not be null");
        this.method = Objects.requireNonNull(method, "method must not be null");
        this.body = body;
    }

    // ====== Request Configuration ======

    /**
     * 批量设置 URL 查询参数。
     */
    public HttpRequest params(Map<String, String> params) {
        if (this.params == null) this.params = new HashMap<>();
        this.params.putAll(params);
        return this;
    }

    /**
     * 添加单个 URL 查询参数。
     */
    public HttpRequest param(String key, String value) {
        if (this.params == null) this.params = new HashMap<>();
        this.params.put(key, value);
        return this;
    }

    /**
     * 添加单个 HTTP 请求头。
     */
    public HttpRequest header(String key, String value) {
        if (this.headerMap == null) this.headerMap = new HashMap<>();
        this.headerMap.put(key, value);
        return this;
    }

    /**
     * 批量设置 HTTP 请求头。
     */
    public HttpRequest headers(Map<String, String> headers) {
        if (this.headerMap == null) this.headerMap = new HashMap<>();
        this.headerMap.putAll(headers);
        return this;
    }

    /**
     * 设置本次请求的超时时间，覆盖全局默认值。
     */
    public HttpRequest timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    // ====== Business Semantics ======

    /**
     * 指定请求体为 JSON 格式（默认行为，通常无需显式调用）。
     */
    public HttpRequest asJson()   { this.contentType = ContentType.JSON; return this; }

    /**
     * 指定请求体为 XML 格式。
     */
    public HttpRequest asXml()    { this.contentType = ContentType.XML; return this; }

    /**
     * 指定请求体为 SOAP 1.2 格式。
     */
    public HttpRequest asSoap()   { this.contentType = ContentType.SOAP; return this; }

    /**
     * 指定请求体为 {@code application/x-www-form-urlencoded} 格式。
     */
    public HttpRequest asForm()   { this.contentType = ContentType.FORM; return this; }

    // ====== Multipart ======

    /**
     * 添加一个 multipart/form-data 文件字段。
     *
     * @param fieldName 表单字段名
     * @param fileName  上传文件名
     * @param data      文件输入流
     */
    public HttpRequest multipart(String fieldName, String fileName, InputStream data) {
        this.contentType = ContentType.MULTIPART;
        this.multipartFieldName = fieldName;
        this.multipartFileName = fileName;
        this.multipartData = data;
        return this;
    }

    // ====== Execution ======

    /**
     * 同步执行请求，返回原始 HTTP 响应。
     */
    public HttpResponse execute() {
        return client().execute(this);
    }

    /**
     * 同步执行请求，自动将响应体反序列化为指定类型。
     */
    public <T> T execute(Class<T> type) {
        return client().execute(this, type);
    }

    /**
     * 同步执行请求，自动将响应体反序列化为泛型类型。
     */
    public <T> T execute(TypeReference<T> typeRef) {
        return client().execute(this, typeRef);
    }

    /**
     * 异步执行请求，自动将响应体反序列化为指定类型。
     */
    public <T> void async(AsyncCallback<T> callback, Class<T> type) {
        client().async(this, callback, type);
    }

    /**
     * 异步执行请求，自动将响应体反序列化为泛型类型。
     */
    public <T> void async(AsyncCallback<T> callback, TypeReference<T> typeRef) {
        client().async(this, callback, typeRef);
    }

    /**
     * 以 {@link CompletableFuture} 方式执行请求，自动反序列化为指定类型。
     * <p>
     * 调用方可以自行决定阻塞等待或链式处理：
     * <pre>{@code
     *   CompletableFuture<User> f = http.get(url).future(User.class);
     *   User user = f.get(5, TimeUnit.SECONDS);          // 阻塞等待
     *   f.thenAccept(u -> cache.put(u.getId(), u));      // 链式处理
     *   CompletableFuture.allOf(f1, f2, f3).join();      // 批量等待
     * }</pre>
     */
    public <T> CompletableFuture<T> future(Class<T> type) {
        return client().future(this, type);
    }

    /**
     * 以 {@link CompletableFuture} 方式执行请求，自动反序列化为泛型类型。
     */
    public <T> CompletableFuture<T> future(TypeReference<T> typeRef) {
        return client().future(this, typeRef);
    }

    // ====== Internal Getters ======

    public String url()              { return url; }
    public HttpMethod method()       { return method; }
    public Object body()             { return body; }
    public ContentType contentType() { return contentType; }
    public String contentTypeMime()  { return contentType.mime(); }
    public Map<String, String> params()         { return params; }
    public Map<String, String> headers()        { return headerMap; }
    public Duration timeout()                   { return timeout; }
    public String multipartFieldName()          { return multipartFieldName; }
    public String multipartFileName()           { return multipartFileName; }
    public InputStream multipartData()          { return multipartData; }

    // hidden reference to the actual HTTP client that executes this request
    private HttpClient client;

    public HttpRequest client(HttpClient client) {
        this.client = client;
        return this;
    }

    private HttpClient client() {
        if (client == null) {
            throw new IllegalStateException("No HttpClient bound to this request");
        }
        return client;
    }

}
