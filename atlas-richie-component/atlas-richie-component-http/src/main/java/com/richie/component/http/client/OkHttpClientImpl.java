package com.richie.component.http.client;

import com.richie.component.http.bean.*;
import com.richie.context.utils.data.Collections;
import com.richie.context.utils.data.JsonUtils;
import com.richie.component.http.bean.*;
import tools.jackson.core.type.TypeReference;
import com.google.common.collect.Maps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.tika.Tika;
import jakarta.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;


/**
 * OKHttp客户端
 *
 * @author richie696
 * @version 1.3
 * @since 2023/08/23 18:08
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "platform.component.http", name = "provider", havingValue = "okhttp", matchIfMissing = true)
@RequiredArgsConstructor
public class OkHttpClientImpl implements HttpClientApi {

    /**
     * JSON格式媒体类型
     */
    private static final MediaType JSON_MEDIA_TYPE = Objects.requireNonNull(MediaType.parse("application/json; charset=utf-8"));
    /**
     * XML格式媒体类型
     */
    private static final MediaType XML_MEDIA_TYPE = Objects.requireNonNull(MediaType.parse("application/xml; charset=utf-8"));
    /**
     * SOAP1.2格式媒体类型
     */
    private static final MediaType SOAP12_MEDIA_TYPE = Objects.requireNonNull(MediaType.parse("application/soap+xml"));
    /**
     * OKHttp客户端对象
     */
    @Qualifier("httpComponent")
    private final OkHttpClient okHttpClient;
    /**
     * 绑定自定义超时时间的OKHttp客户端对象
     */
    private final ConcurrentMap<Long, OkHttpClient> CUSTOM_CLIENTS = Maps.newConcurrentMap();

    private static @Nonnull Request deleteRequest(String url, String json, Map<String, String> headerMap) {
        var requestBody = RequestBody.create(json, JSON_MEDIA_TYPE);

        var builder = new Request.Builder();
        if (MapUtils.isNotEmpty(headerMap)) {
            headerMap.forEach(builder::addHeader);
        }

        return builder.delete(requestBody).url(url).build();
    }

    private static @Nonnull Request postRequest(String url, Map<String, String> params, Map<String, String> headerMap) {
        var builder = new FormBody.Builder();
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, String> et : params.entrySet()) {
                builder.add(et.getKey(), et.getValue());
            }
        }
        var requestBuilder = new Request.Builder();
        if (MapUtils.isNotEmpty(headerMap)) {
            headerMap.forEach(requestBuilder::addHeader);
        }
        return requestBuilder.url(url).post(builder.build()).build();
    }

    @Override
    public HttpResponse doRequest(HttpMethod method, String url, Map<String, String> params, Map<String, String> headers, List<Throwable> returnErrors) {
        return switch (method) {
            case GET: {
                var request = getRequest(url, params, headers);
                yield executeAndReturnResponse(request, returnErrors);
            }
            case POST: {
                var request = postRequest(url, params, headers);
                yield executeAndReturnResponse(request, returnErrors);
            }
            case PUT: {
                var request = putRequest(url, params, headers);
                yield executeAndReturnResponse(request, returnErrors);
            }
            case DELETE: {
                var request = deleteRequest(url, JsonUtils.getInstance().serialize(params), headers);
                yield executeAndReturnResponse(request, returnErrors);
            }
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        };
    }

    /**
     * 无参数GET请求方法
     *
     * @param url 请求URL地址
     * @return 返回结果报文字符串
     */
    @Override
    public String doGet(String url) {
        return doGet(url, null, null);
    }

    /**
     * 带参数GET请求方法
     *
     * @param url    请求URL地址
     * @param params 请求参数集合
     * @return 返回结果报文字符串
     */
    @Override
    public String doGet(String url, Map<String, String> params) {
        return doGet(url, params, null);
    }

    /**
     * 异步无参数GET请求方法
     *
     * @param url           请求URL地址
     * @param asyncCallback 异步回调接口
     */
    @Override
    public void doAsyncGet(String url, AsyncCallback<String> asyncCallback) {
        doAsyncGet(String.class, url, null, null, asyncCallback);
    }

    /**
     * 无参数GET请求方法
     *
     * @param cls 报文类型
     * @param url 请求URL地址
     * @param <T> 报文的具体类型
     * @return 返回结果报文对象
     */
    @Override
    @Nullable
    public <T> T doGet(Class<T> cls, String url) {
        var payloadString = doGet(url, null, null);
        if (StringUtils.isBlank(payloadString)) {
            return null;
        }
        return JsonUtils.getInstance().deserialize(payloadString, cls);
    }

    /**
     * 无参数GET请求方法
     *
     * @param cls          报文类型
     * @param url          请求URL地址
     * @param returnErrors 返回错误信息
     * @param <T>          报文的具体类型
     * @return 返回结果报文对象
     */
    @Override
    @Nullable
    public <T> T doGet(Class<T> cls, String url, List<Throwable> returnErrors) {
        var payloadString = doGet(url, null, null, returnErrors);
        if (StringUtils.isBlank(payloadString)) {
            return null;
        }
        return JsonUtils.getInstance().deserialize(payloadString, cls);
    }

    /**
     * 异步无参数GET请求方法
     *
     * @param cls           报文类型
     * @param url           请求URL地址
     * @param asyncCallback 异步回调接口
     * @param <T>           报文的具体类型
     */
    @Override
    public <T> void doAsyncGet(Class<T> cls, String url, AsyncCallback<T> asyncCallback) {
        doAsyncGet(cls, url, null, null, asyncCallback);
    }

    /**
     * 带参数GET请求方法
     *
     * @param cls    报文类型
     * @param url    请求URL地址
     * @param params 请求参数集合
     * @param <T>    报文的具体类型
     * @return 返回结果报文对象
     */
    @Override
    @Nullable
    public <T> T doGet(Class<T> cls, String url, Map<String, String> params) {
        var payloadString = doGet(url, params, null);
        if (StringUtils.isBlank(payloadString)) {
            return null;
        }
        return JsonUtils.getInstance().deserialize(payloadString, cls);
    }

    /**
     * 异步带参数GET请求方法
     *
     * @param cls           报文类型
     * @param url           请求URL地址
     * @param params        请求参数集合
     * @param asyncCallback 异步回调
     * @param <T>           报文的具体类型
     */
    @Override
    public <T> void doAsyncGet(Class<T> cls, String url, Map<String, String> params, AsyncCallback<T> asyncCallback) {
        doAsyncGet(cls, url, params, null, asyncCallback);
    }

    /**
     * 带头信息和请求参数的GET请求方法
     *
     * @param url       请求URL地址
     * @param params    请求参数集合
     * @param headerMap 请求头字段集合
     * @return 返回结果报文字符串
     */
    @Override
    @Nullable
    public String doGet(String url, Map<String, String> params, Map<String, String> headerMap) {
        return doGet(url, params, headerMap, null);
    }

    /**
     * 带头信息和请求参数的GET请求方法
     *
     * @param url       请求URL地址
     * @param params    请求参数集合
     * @param headerMap 请求头字段集合
     * @return 返回结果报文字符串
     */
    @Override
    @Nullable
    public String doGet(String url, Map<String, String> params, Map<String, String> headerMap, List<Throwable> returnErrors) {
        var request = getRequest(url, params, headerMap);
        return execute(request, returnErrors, null);
    }

    private @Nonnull Request getRequest(String url, Map<String, String> params, Map<String, String> headerMap) {
        var finalUrl = new StringBuilder(url);
        // 生成最终的URL地址
        generateUrl(params, finalUrl);
        // 构建头信息
        var builder = new Request.Builder();
        if (MapUtils.isNotEmpty(headerMap)) {
            headerMap.forEach(builder::addHeader);
        }
        // 发送请求
        return builder.url(finalUrl.toString()).build();
    }

    /**
     * 带头信息和请求参数的GET请求方法
     *
     * @param cls       报文类型
     * @param url       请求URL地址
     * @param params    请求参数集合
     * @param headerMap 请求头字段集合
     * @param <T>       报文的具体类型
     * @return 返回结果报文对象
     */
    @Override
    @Nullable
    public <T> T doGet(Class<T> cls, String url, Map<String, String> params, Map<String, String> headerMap) {
        var payloadString = doGet(url, params, headerMap);
        if (StringUtils.isBlank(payloadString)) {
            return null;
        }
        return JsonUtils.getInstance().deserialize(payloadString, cls);
    }

    /**
     * 带头信息和请求参数的异步GET请求方法
     *
     * @param cls           报文类型
     * @param url           请求URL地址
     * @param params        请求参数集合
     * @param headerMap     请求头字段集合
     * @param asyncCallback 异步回调
     * @param <T>           报文的具体类型
     */
    @Override
    public <T> void doAsyncGet(Class<T> cls, String url, Map<String, String> params, Map<String, String> headerMap, AsyncCallback<T> asyncCallback) {
        var finalUrl = new StringBuilder(url);
        generateUrl(params, finalUrl);
        var builder = new Request.Builder();
        if (headerMap == null) {
            headerMap = Map.of();
        }
        headerMap.forEach(builder::addHeader);
        var request = builder.url(finalUrl.toString()).build();
        log.info("do get request and url[{}]", finalUrl);
        executeAsync(cls, request, null, asyncCallback);
    }

    /**
     * 执行删除操作的方法
     *
     * @param url       请求URL地址
     * @param json      执行删除的报文参数JSON体
     * @param headerMap 请求头字段集合
     * @return 返回结果报文字符串
     */
    @Override
    @Nullable
    public String doDelete(String url, String json, Map<String, String> headerMap) {
        return doDelete(url, json, headerMap, null);
    }

    /**
     * 执行删除操作的方法
     *
     * @param url          请求URL地址
     * @param json         执行删除的报文参数JSON体
     * @param headerMap    请求头字段集合
     * @param returnErrors 返回错误信息
     * @return 返回结果报文字符串
     */
    @Override
    @Nullable
    public String doDelete(String url, String json, Map<String, String> headerMap, List<Throwable> returnErrors) {
        var request = deleteRequest(url, json, headerMap);
        return execute(request, returnErrors, null);
    }

    /**
     * 执行删除操作的方法
     *
     * @param cls       报文类型
     * @param url       请求URL地址
     * @param json      执行删除的报文参数JSON体
     * @param headerMap 请求头字段集合
     * @param <T>       报文的具体类型
     * @return 返回结果报文对象
     */
    @Override
    @Nullable
    public <T> T doDelete(Class<T> cls, String url, String json, Map<String, String> headerMap) {
        var payloadString = doDelete(url, json, headerMap);
        if (StringUtils.isBlank(payloadString)) {
            return null;
        }
        return JsonUtils.getInstance().deserialize(payloadString, cls);
    }

    /**
     * 执行删除操作的方法
     *
     * @param cls           报文类型
     * @param url           请求URL地址
     * @param json          执行删除的报文参数JSON体
     * @param headerMap     请求头字段集合
     * @param <T>           报文的具体类型
     * @param asyncCallback 异步回调
     */
    @Override
    public <T> void doAsyncDelete(Class<T> cls, String url, String json, Map<String, String> headerMap, AsyncCallback<T> asyncCallback) {
        var requestBody = RequestBody.create(json, JSON_MEDIA_TYPE);

        var builder = new Request.Builder();
        if (headerMap == null) {
            headerMap = Map.of();
        }
        headerMap.forEach(builder::addHeader);
        var request = builder.delete(requestBody).url(url).build();
        executeAsync(cls, request, null, asyncCallback);
    }


    /**
     * 执行 POST 请求的方法
     *
     * @param url    请求URL地址
     * @param params 请求参数集合
     * @return 返回结果报文字符串
     */
    @Nullable
    @Override
    public String doPost(String url, Map<String, String> params) {
        return doPost(url, params, new ArrayList<>());
    }


    /**
     * 执行 POST 请求的方法
     *
     * @param url    请求URL地址
     * @param params 请求参数集合
     * @return 返回结果报文字符串
     */
    @Nullable
    @Override
    public String doPost(String url, Map<String, String> params, List<Throwable> returnErrors) {
        var builder = new FormBody.Builder();

        if (params != null && !params.isEmpty()) {

            for (Map.Entry<String, String> et : params.entrySet()) {
                builder.add(et.getKey(), et.getValue());
            }
        }
        var request = new Request.Builder().url(url).post(builder.build()).build();
        return execute(request, returnErrors, null);
    }

    /**
     * 执行 POST 请求的方法
     *
     * @param url          请求URL地址
     * @param params       请求参数集合
     * @param returnErrors 返回错误信息
     * @return 返回结果报文字符串
     */
    @Nullable
    @Override
    public String doPost(String url, Map<String, String> params, Map<String, String> headerMap, List<Throwable> returnErrors) {
        var request = postRequest(url, params, headerMap);
        return execute(request, returnErrors, null);
    }

    private Request putRequest(String url, Map<String, String> params, Map<String, String> headers) {
        var builder = new FormBody.Builder();
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, String> et : params.entrySet()) {
                builder.add(et.getKey(), et.getValue());
            }
        }
        var requestBuilder = new Request.Builder();
        if (MapUtils.isNotEmpty(headers)) {
            headers.forEach(requestBuilder::addHeader);
        }
        return requestBuilder.url(url).put(builder.build()).build();
    }

    /**
     * 执行 POST 请求的方法
     *
     * @param url           请求URL地址
     * @param params        请求参数集合
     * @param typeReference 报文类型
     * @param <T>           报文的具体类型
     * @return 返回结果报文字符串
     */
    @Override
    public <T> T doPost(String url, Object params, TypeReference<T> typeReference) {
        var payloadString = doPost(url, JsonUtils.getInstance().serialize(params));
        if (StringUtils.isBlank(payloadString)) {
            return null;
        }
        return JsonUtils.getInstance().deserialize(payloadString, typeReference);
    }

    /**
     * 执行 POST 请求的方法
     *
     * @param url           请求URL地址
     * @param params        请求参数集合
     * @param headerMap     请求头字段集合
     * @param timeout       超时时长
     * @param returnErrors  返回错误信息
     * @param typeReference 报文类型
     * @param <T>           报文的具体类型
     * @return 返回结果报文字符串
     */
    @Override
    public <T> T doPost(String url, Object params, Map<String, String> headerMap, Long timeout, List<Throwable> returnErrors, TypeReference<T> typeReference) {
        var json = JsonUtils.getInstance().serialize(params);
        var payloadString = executePost(url, json, headerMap, JSON_MEDIA_TYPE, returnErrors, timeout);
        if (StringUtils.isBlank(payloadString)) {
            return null;
        }
        return JsonUtils.getInstance().deserialize(payloadString, typeReference);
    }

    /**
     * 执行 POST 请求的方法
     *
     * @param url           请求URL地址
     * @param params        请求参数集合
     * @param headerMap     请求头字段集合
     * @param typeReference 报文类型
     * @param <T>           报文的具体类型
     * @return 返回结果报文字符串
     */
    @Override
    public <T> T doPost(String url, Object params, Map<String, String> headerMap, TypeReference<T> typeReference) {
        var payloadString = doPost(url, params, headerMap);
        if (StringUtils.isBlank(payloadString)) {
            return null;
        }
        return JsonUtils.getInstance().deserialize(payloadString, typeReference);
    }

    /**
     * 执行 POST 请求的方法
     *
     * @param url           请求URL地址
     * @param params        请求参数集合
     * @param headerMap     请求头字段集合
     * @param timeout       超时时长
     * @param typeReference 报文类型
     * @param <T>           报文的具体类型
     * @return 返回结果报文字符串
     */
    @Override
    public <T> T doPost(String url, Object params, Map<String, String> headerMap, Long timeout, TypeReference<T> typeReference) {
        var json = JsonUtils.getInstance().serialize(params);
        var payloadString = executePost(url, json, headerMap, JSON_MEDIA_TYPE, null, timeout);
        if (StringUtils.isBlank(payloadString)) {
            return null;
        }
        return JsonUtils.getInstance().deserialize(payloadString, typeReference);
    }

    /**
     * 执行异步 POST 请求的方法
     *
     * @param cls           报文类型
     * @param url           请求URL地址
     * @param params        请求参数集合
     * @param asyncCallback 异步回调
     * @param <T>           报文的具体类型
     */
    @Override
    public <T> void doAsyncPost(Class<T> cls, String url, Object params, AsyncCallback<T> asyncCallback) {
        doAsyncPost(cls, url, params, null, null, asyncCallback);
    }

    @Override
    public <T> void doAsyncPost(Class<T> cls, String url, Object params, Long timeout, AsyncCallback<T> asyncCallback) {
        doAsyncPost(cls, url, params, null, null, timeout, asyncCallback);
    }

    /**
     * 执行异步 POST 请求的方法
     *
     * @param cls           报文类型
     * @param url           请求URL地址
     * @param params        请求参数集合
     * @param headerMap     请求头字段集合
     * @param asyncCallback 异步回调
     * @param <T>           报文的具体类型
     */
    @Override
    public <T> void doAsyncPost(Class<T> cls, String url, Object params, Map<String, String> headerMap, AsyncCallback<T> asyncCallback) {
        doAsyncPost(cls, url, params, headerMap, null, null, asyncCallback);
    }

    @Override
    public <T> void doAsyncPost(Class<T> cls, String url, Object params, Map<String, String> headerMap, Long timeout, AsyncCallback<T> asyncCallback) {
        doAsyncPost(cls, url, params, headerMap, null, timeout, asyncCallback);
    }

    /**
     * 执行 POST 请求的方法
     *
     * @param cls           回调报文类型
     * @param url           请求URL
     * @param params        请求参数对象
     * @param headerMap     请求头
     * @param contentType   内容类型
     * @param asyncCallback 异步回调函数
     */
    @Override
    public <T> void doAsyncPost(Class<T> cls, String url, Object params, Map<String, String> headerMap, String contentType, Long timeout, AsyncCallback<T> asyncCallback) {
        RequestBody requestBody;
        MediaType mediaType;
        if (contentType == null) {
            mediaType = JSON_MEDIA_TYPE;
        } else {
            mediaType = MediaType.parse(contentType);
            Objects.requireNonNull(mediaType, "Invalid content type: " + contentType);
        }
        String data;
        if (params instanceof String s) {
            data = s;
        } else {
            data = JsonUtils.getInstance().serialize(params);
        }
        Objects.requireNonNull(data, "Invalid request body: " + params);
        if (Objects.isNull(mediaType.charset())) {
            requestBody = RequestBody.create(data.getBytes(StandardCharsets.UTF_8), mediaType);
        } else {
            requestBody = RequestBody.create(data, mediaType);
        }
        var requestBuilder = new Request.Builder();
        if (MapUtils.isNotEmpty(headerMap)) {
            headerMap.forEach(requestBuilder::addHeader);
        }
        var request = requestBuilder.url(url).post(requestBody).build();
        executeAsync(cls, request, timeout, asyncCallback);
    }

    /**
     * 执行 POST 请求的方法（请求参数为参数对象）
     *
     * @param url      请求URL地址
     * @param argument 请求参数对象
     * @return 返回结果报文字符串
     */
    @Override
    @Nullable
    public String doPost(String url, Object argument) {
        var json = JsonUtils.getInstance().serialize(argument);
        return executePost(url, json, JSON_MEDIA_TYPE, null);
    }

    /**
     * 执行 POST 请求的方法（请求参数为参数对象）
     *
     * @param url          请求URL地址
     * @param argument     请求参数对象
     * @param returnErrors 返回错误信息
     * @return 返回结果报文字符串
     */
    @Override
    @Nullable
    public String doPost(String url, Object argument, List<Throwable> returnErrors) {
        var json = JsonUtils.getInstance().serialize(argument);
        return executePost(url, json, JSON_MEDIA_TYPE, returnErrors);
    }

    /**
     * 执行 POST 请求的方法（请求参数为参数对象）
     *
     * @param url      请求URL地址
     * @param argument 请求参数对象
     * @return 返回结果报文字符串
     */
    @Override
    @Nullable
    public String doPost(String url, Object argument, Long timeout) {
        var json = JsonUtils.getInstance().serialize(argument);
        return executePost(url, json, Map.of(), JSON_MEDIA_TYPE, null, timeout);
    }

    /**
     * 执行 POST 请求的方法（请求参数为参数对象）
     *
     * @param url          请求URL地址
     * @param argument     请求参数对象
     * @param returnErrors 返回错误信息
     * @return 返回结果报文字符串
     */
    @Override
    @Nullable
    public String doPost(String url, Object argument, List<Throwable> returnErrors, Long timeout) {
        var json = JsonUtils.getInstance().serialize(argument);
        return executePost(url, json, Map.of(), JSON_MEDIA_TYPE, returnErrors, timeout);
    }

    /**
     * 执行 POST 请求的方法（请求参数为参数对象）
     *
     * @param cls      报文类型
     * @param url      请求URL地址
     * @param argument 请求参数对象
     * @param <T>      报文的具体类型
     * @return 返回结果报文字符串
     */
    @Override
    @Nullable
    public <T> T doPost(Class<T> cls, String url, Object argument) {
        var json = JsonUtils.getInstance().serialize(argument);
        var payloadString = executePost(url, json, JSON_MEDIA_TYPE, null);
        if (StringUtils.isBlank(payloadString)) {
            return null;
        }
        return JsonUtils.getInstance().deserialize(payloadString, cls);
    }

    /**
     * 执行 POST 请求的方法（请求参数为参数对象）
     *
     * @param cls          报文类型
     * @param url          请求URL地址
     * @param argument     请求参数对象
     * @param returnErrors 返回错误信息
     * @param <T>          报文的具体类型
     * @return 返回结果报文字符串
     */
    @Override
    @Nullable
    public <T> T doPost(Class<T> cls, String url, Object argument, List<Throwable> returnErrors) {
        var json = JsonUtils.getInstance().serialize(argument);
        var payloadString = executePost(url, json, JSON_MEDIA_TYPE, returnErrors);
        if (StringUtils.isBlank(payloadString)) {
            return null;
        }
        return JsonUtils.getInstance().deserialize(payloadString, cls);
    }

    /**
     * 执行 POST 请求的方法（请求参数为参数对象）
     *
     * @param cls         报文类型
     * @param url         请求URL地址
     * @param argument    请求参数对象
     * @param contentType 请求内容类型
     * @param <T>         报文的具体类型
     * @return 返回结果报文字符串
     */
    @Override
    @Nullable
    public <T> T doPost(Class<T> cls, String url, Object argument, String contentType) {
        var json = JsonUtils.getInstance().serialize(argument);
        var mediaType = MediaType.parse(contentType);
        if (mediaType == null) {
            throw new IllegalArgumentException("Invalid content type: " + contentType);
        }
        var payloadString = executePost(url, json, mediaType, null);
        if (StringUtils.isBlank(payloadString)) {
            return null;
        }
        return JsonUtils.getInstance().deserialize(payloadString, cls);
    }

    /**
     * 执行 POST 请求的方法（请求参数为参数对象）
     *
     * @param cls          报文类型
     * @param url          请求URL地址
     * @param argument     请求参数对象
     * @param contentType  请求内容类型
     * @param returnErrors 返回错误信息
     * @param <T>          报文的具体类型
     * @return 返回结果报文字符串
     */
    @Override
    @Nullable
    public <T> T doPost(Class<T> cls, String url, Object argument, String contentType, List<Throwable> returnErrors) {
        var json = JsonUtils.getInstance().serialize(argument);
        var mediaType = MediaType.parse(contentType);
        if (mediaType == null) {
            throw new IllegalArgumentException("Invalid content type: " + contentType);
        }
        var payloadString = executePost(url, json, mediaType, returnErrors);
        if (StringUtils.isBlank(payloadString)) {
            return null;
        }
        return JsonUtils.getInstance().deserialize(payloadString, cls);
    }

    /**
     * 执行 POST 请求的方法（请求参数为参数对象）
     *
     * @param url       请求URL地址
     * @param argument  请求参数对象
     * @param headerMap 请求头字段集合
     * @return 返回请求结果
     */
    @Override
    @Nullable
    public String doPost(String url, Object argument, Map<String, String> headerMap) {
        var json = JsonUtils.getInstance().serialize(argument);
        return executePost(url, json, headerMap, JSON_MEDIA_TYPE, null, null);
    }

    /**
     * 执行 POST 请求的方法（请求参数为参数对象）
     *
     * @param url          请求URL地址
     * @param argument     请求参数对象
     * @param headerMap    请求头字段集合
     * @param returnErrors 返回错误信息
     * @return 返回请求结果
     */
    @Override
    @Nullable
    public String doPost(String url, Object argument, Map<String, String> headerMap, List<Throwable> returnErrors) {
        var json = JsonUtils.getInstance().serialize(argument);
        return executePost(url, json, headerMap, JSON_MEDIA_TYPE, returnErrors, null);
    }

    /**
     * 执行 POST 请求的方法（请求参数为参数对象）
     *
     * @param cls       报文类型
     * @param url       请求URL地址
     * @param argument  请求参数对象
     * @param headerMap 请求头字段集合
     * @param <T>       报文的具体类型
     * @return 返回请求结果
     */
    @Override
    @Nullable
    public <T> T doPost(Class<T> cls, String url, Object argument, Map<String, String> headerMap) {
        var json = JsonUtils.getInstance().serialize(argument);
        if (StringUtils.isBlank(json)) {
            throw new IllegalArgumentException("参数无效!");
        }
        var payloadString = executePost(url, json, headerMap, Objects.requireNonNull(JSON_MEDIA_TYPE), null, null);
        if (StringUtils.isBlank(payloadString)) {
            return null;
        }
        return JsonUtils.getInstance().deserialize(payloadString, cls);
    }

    /**
     * 执行 POST 请求的方法（请求参数为参数对象）
     *
     * @param cls          报文类型
     * @param url          请求URL地址
     * @param argument     请求参数对象
     * @param headerMap    请求头字段集合
     * @param returnErrors 返回错误信息
     * @param <T>          报文的具体类型
     * @return 返回请求结果
     */
    @Override
    @Nullable
    public <T> T doPost(Class<T> cls, String url, Object argument, Map<String, String> headerMap, List<Throwable> returnErrors) {
        var json = JsonUtils.getInstance().serialize(argument);
        if (StringUtils.isBlank(json)) {
            throw new IllegalArgumentException("参数无效!");
        }
        var payloadString = executePost(url, json, headerMap, Objects.requireNonNull(JSON_MEDIA_TYPE), returnErrors, null);
        if (StringUtils.isBlank(payloadString)) {
            return null;
        }
        return JsonUtils.getInstance().deserialize(payloadString, cls);
    }

    /**
     * 执行 POST 请求的方法（允许自定义请求内容类型）
     *
     * @param url         请求URL地址
     * @param argument    请求参数对象
     * @param headerMap   请求头
     * @param contentType 请求头MIME
     * @return 返回请求结果
     */
    @Override
    @Nullable
    public String doPost(String url, Object argument, Map<String, String> headerMap, String contentType) {
        var json = JsonUtils.getInstance().serialize(argument);
        if (StringUtils.isAnyBlank(json, contentType)) {
            throw new IllegalArgumentException("contentType参数不能为空!");
        }
        var parse = MediaType.parse(contentType);
        return executePost(url, json, headerMap, Objects.requireNonNull(parse), null, null);
    }

    /**
     * 执行 POST 请求的方法（请求参数为JSON字符串）
     *
     * @param url  请求URL地址
     * @param json 请求参数对象 JSON 字符串
     * @return 返回请求结果
     */
    @Nullable
    @Override
    public String doPost(String url, String json) {
        return executePost(url, json, JSON_MEDIA_TYPE, null);
    }

    /**
     * 执行 POST 请求的方法（允许自定义请求内容类型）
     *
     * @param url          请求URL地址
     * @param argument     请求参数对象
     * @param headerMap    请求头
     * @param contentType  请求头MIME
     * @param returnErrors 返回错误信息
     * @return 返回请求结果
     */
    @Override
    @Nullable
    public String doPost(String url, Object argument, Map<String, String> headerMap, String contentType, List<Throwable> returnErrors) {
        var json = JsonUtils.getInstance().serialize(argument);
        if (StringUtils.isAnyBlank(json, contentType)) {
            throw new IllegalArgumentException("contentType参数不能为空!");
        }
        var parse = MediaType.parse(contentType);
        return executePost(url, json, headerMap, Objects.requireNonNull(parse), returnErrors, null);
    }

    /**
     * 执行 POST 请求的方法（允许自定义请求内容类型）
     *
     * @param cls         报文类型
     * @param url         请求URL地址
     * @param argument    请求参数对象
     * @param headerMap   请求头
     * @param contentType 请求头MIME
     * @param <T>         报文的具体类型
     * @return 返回请求结果
     */
    @Override
    @Nullable
    public <T> T doPost(Class<T> cls, String url, Object argument, Map<String, String> headerMap, String contentType) {
        var json = JsonUtils.getInstance().serialize(argument);
        var payloadString = doPost(url, json, headerMap, contentType);
        if (StringUtils.isBlank(payloadString)) {
            return null;
        }
        return JsonUtils.getInstance().deserialize(payloadString, cls);
    }

    /**
     * 执行 POST 请求的方法（请求参数为 XML 格式）
     *
     * @param url 请求URL地址
     * @param xml 请求数据 xml 字符串
     * @return 返回请求结果
     */
    @Override
    @Nullable
    public String doPostSOAP1_2(String url, String xml) {
        return doPostSOAP1_2(url, xml, null);
    }

    /**
     * 执行 POST 请求的方法（请求参数为 XML 格式）
     *
     * @param url 请求URL地址
     * @param xml 请求数据 xml 字符串
     * @return 返回请求结果
     */
    @Override
    @Nullable
    public String doPostSOAP1_2(String url, String xml, List<Throwable> returnErrors) {
        log.info("do post request and url[{}]", url);
        return executePost(url, xml, SOAP12_MEDIA_TYPE, returnErrors);
    }

    /**
     * 执行 POST 请求的方法（请求参数为 XML 格式）
     *
     * @param cls 报文类型
     * @param url 请求URL地址
     * @param xml 请求数据 xml 字符串
     * @param <T> 报文的具体类型
     * @return 返回请求结果
     */
    @Override
    @Nullable
    public <T> T doPostSOAP1_2(Class<T> cls, String url, String xml) {
        return doPostSOAP1_2(cls, url, xml, null);
    }

    @Override
    public <T> T doPostSOAP1_2(Class<T> cls, String url, String xml, List<Throwable> returnErrors) {
        var payloadString = executePost(url, xml, SOAP12_MEDIA_TYPE, returnErrors);
        if (StringUtils.isBlank(payloadString)) {
            return null;
        }
        return JsonUtils.getInstance().deserialize(payloadString, cls);
    }

    /**
     * 执行 POST 请求的方法（请求参数为 XML 格式）
     *
     * @param cls           报文类型
     * @param url           请求URL地址
     * @param xml           请求数据 xml 字符串
     * @param asyncCallback 异步回调
     * @param <T>           报文的具体类型
     */
    @Override
    public <T> void doAsyncPostSOAP1_2(Class<T> cls, String url, String xml, AsyncCallback<T> asyncCallback) {
        doAsyncPost(cls, url, xml, null, SOAP12_MEDIA_TYPE.toString(), null, asyncCallback);
    }

    /**
     * 执行 POST 请求的方法（请求参数为 XML 格式）
     *
     * @param cls           报文类型
     * @param url           请求URL地址
     * @param xml           请求数据 xml 字符串
     * @param asyncCallback 异步回调
     * @param <T>           报文的具体类型
     */
    @Override
    public <T> void doAsyncPostSOAP1_2(Class<T> cls, String url, String xml, Map<String, String> headerMap, AsyncCallback<T> asyncCallback) {
        doAsyncPost(cls, url, xml, headerMap, SOAP12_MEDIA_TYPE.toString(), null, asyncCallback);
    }

    /**
     * 执行 POST 请求的方法（请求参数为 XML 格式）
     *
     * @param url 请求URL地址
     * @param xml 请求数据 xml 字符串
     * @return 返回请求结果
     */
    @Override
    @Nullable
    public String doPostXml(String url, String xml) {
        return doPostXml(url, xml, null);
    }

    /**
     * 执行 POST 请求的方法（请求参数为 XML 格式）
     *
     * @param url 请求URL地址
     * @param xml 请求数据 xml 字符串
     * @return 返回请求结果
     */
    @Override
    @Nullable
    public String doPostXml(String url, String xml, List<Throwable> returnErrors) {
        log.info("do post request and url[{}]", url);
        return executePost(url, xml, XML_MEDIA_TYPE, returnErrors);
    }

    /**
     * 执行 POST 请求的方法（请求参数为 XML 格式）
     *
     * @param cls 报文类型
     * @param url 请求URL地址
     * @param xml 请求数据 xml 字符串
     * @param <T> 报文的具体类型
     * @return 返回请求结果
     */
    @Override
    @Nullable
    public <T> T doPostXml(Class<T> cls, String url, String xml) {
        return doPostXml(cls, url, xml, null);
    }

    @Override
    public <T> T doPostXml(Class<T> cls, String url, String xml, List<Throwable> returnErrors) {
        log.info("do post request and url[{}]", url);
        var payloadString = executePost(url, xml, XML_MEDIA_TYPE, returnErrors);
        if (StringUtils.isBlank(payloadString)) {
            return null;
        }
        return JsonUtils.getInstance().deserialize(payloadString, cls);
    }

    /**
     * 执行 POST 请求的方法（请求参数为 XML 格式）
     *
     * @param cls           报文类型
     * @param url           请求URL地址
     * @param xml           请求数据 xml 字符串
     * @param asyncCallback 异步回调
     * @param <T>           报文的具体类型
     */
    @Override
    public <T> void doAsyncPostXml(Class<T> cls, String url, String xml, AsyncCallback<T> asyncCallback) {
        doAsyncPost(cls, url, xml, null, XML_MEDIA_TYPE.toString(), null, asyncCallback);
    }

    /**
     * 执行 POST 请求的方法（请求参数为 XML 格式）
     *
     * @param cls           报文类型
     * @param url           请求URL地址
     * @param xml           请求数据 xml 字符串
     * @param asyncCallback 异步回调
     * @param <T>           报文的具体类型
     */
    @Override
    public <T> void doAsyncPostXml(Class<T> cls, String url, String xml, Map<String, String> headerMap, AsyncCallback<T> asyncCallback) {
        doAsyncPost(cls, url, xml, headerMap, XML_MEDIA_TYPE.toString(), null, asyncCallback);
    }

    /**
     * 上传文件
     *
     * @param url           文件URL
     * @param metadata      文件元数据信息
     * @param params        请求参数
     * @param headerMap     请求头参数
     * @param typeReference 应答报文转换内省对象
     * @param <T>           应答报文类型
     * @return 返回上传结果
     */
    @Nullable
    @Override
    public <T> T doUploadInputStream(@Nonnull String url, @Nonnull UploadFileMetadata metadata, Map<String, String> params, Map<String, String> headerMap, TypeReference<T> typeReference) {
        String responseText = doUploadInputStream(url, metadata, params, headerMap);
        if (StringUtils.isBlank(responseText)) {
            return null;
        }
        return JsonUtils.getInstance().deserialize(responseText, typeReference);
    }

    /**
     * 上传文件
     *
     * @param url       文件URL
     * @param metadata  文件元数据信息
     * @param params    请求参数
     * @param headerMap 请求头参数
     * @return 返回上传结果
     */
    @Nullable
    @Override
    public String doUploadInputStream(@Nonnull String url, @Nonnull UploadFileMetadata metadata,
                                      Map<String, String> params, Map<String, String> headerMap) {
        return doUploadInputStream(url, List.of(metadata), params, headerMap);
    }

    /**
     * 批量上传文件
     *
     * @param url          文件URL
     * @param metadataList 文件元数据信息
     * @return 返回上传结果
     */
    @Override
    public String doUploadInputStream(@Nonnull String url, @Nonnull List<UploadFileMetadata> metadataList) {
        return doUploadInputStream(url, metadataList, null, null);
    }

    /**
     * 批量上传文件
     *
     * @param url          文件URL
     * @param metadataList 文件元数据信息
     * @param params       请求参数
     * @return 返回上传结果
     */
    @Override
    public String doUploadInputStream(@Nonnull String url, @Nonnull List<UploadFileMetadata> metadataList, Map<String, String> params) {
        return doUploadInputStream(url, metadataList, params, null);
    }

    /**
     * 上传文件
     *
     * @param url          文件URL
     * @param metadataList 文件元数据信息
     * @param params       请求参数
     * @param headerMap    请求头参数
     * @return 返回上传结果
     */
    @Nullable
    @Override
    public String doUploadInputStream(@Nonnull String url, @Nonnull List<UploadFileMetadata> metadataList,
                                      Map<String, String> params, Map<String, String> headerMap) {
        var builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
        // 添加请求参数
        if (MapUtils.isNotEmpty(params)) {
            params.forEach(builder::addFormDataPart);
        }
        // 添加请求头
        var requestBuilder = new Request.Builder();
        if (MapUtils.isNotEmpty(headerMap)) {
            headerMap.forEach(requestBuilder::addHeader);
        }
        // 批量添加文件
        var tika = new Tika();
        metadataList.forEach(metadata -> {
            byte[] imageBytes;
            MediaType mediaType;
            try {
                var inputStream = metadata.inputStream();
                mediaType = MediaType.parse(tika.detect(inputStream));
                if (mediaType == null) {
                    log.error("文件类型错误，上传失败。");
                    return;
                }
                imageBytes = inputStream.readAllBytes();
            } catch (IOException e) {
                log.error("获取文件ContentType出错");
                return;
            }
            builder.addFormDataPart(
                    metadata.paramName(),
                    metadata.fileName(),
                    RequestBody.create(imageBytes, mediaType)
            );
        });
        return execute(requestBuilder.url(url).post(builder.build()).build(), null, null);
    }

    /**
     * 上传文件
     *
     * @param url       文件URL
     * @param fileMap   文件集合
     * @param params    请求参数
     * @param headerMap 请求头参数
     * @return 返回上传结果
     */
    @Override
    public String doUploadFile(String url, Map<String, File> fileMap,
                               Map<String, String> params, Map<String, String> headerMap) {
        return doUploadFile(url, fileMap, params, headerMap, null);
    }

    @Override
    public String doUploadFile(String url, Map<String, File> fileMap, Map<String, String> params, Map<String, String> headerMap, List<Throwable> returnErrors) {
        Assert.notNull(url, "url不能为空");
        Assert.notEmpty(fileMap, "上传文件不能为空");
        var result = getResult(fileMap, params, headerMap);
        if (result == null) {
            return "";
        }
        return execute(result.requestBuilder().url(url).post(result.builder()).build(), returnErrors, null);
    }

    @Override
    public void doAsyncUploadFile(String url, Map<String, File> fileMap, Map<String, String> params, Map<String, String> headerMap, AsyncCallback<String> asyncCallback) {
        doAsyncUploadFile(url, fileMap, params, headerMap, null, asyncCallback);
    }

    /**
     * 异步上传文件
     *
     * @param url           文件URL
     * @param fileMap       文件集合
     * @param params        请求参数
     * @param headerMap     请求头参数
     * @param asyncCallback 异步回调
     */
    @Override
    public void doAsyncUploadFile(String url, Map<String, File> fileMap,
                                  Map<String, String> params, Map<String, String> headerMap, Long timeout, AsyncCallback<String> asyncCallback) {
        Assert.notNull(url, "url不能为空");
        Assert.notEmpty(fileMap, "上传文件不能为空");
        var result = getResult(fileMap, params, headerMap);
        if (result == null) {
            log.error("上传文件失败，获取文件ContentType出错，url：" + url);
            return;
        }
        var request = result.requestBuilder().url(url).post(result.builder()).build();
        executeAsync(String.class, request, timeout, asyncCallback);
    }

    /**
     * 下载文件
     *
     * @param url          文件URL
     * @param destFilePath 存储文件路径
     * @return 返回文件
     * @throws Exception 异常
     */
    @Override
    public File doDownloadFile(String url, String destFilePath) throws Exception {
        Assert.hasLength(url, "文件URL 不能为空");
        Assert.hasLength(destFilePath, "存储文件不能为空");
        var destFile = new File(destFilePath);
        if (!destFile.exists()) {
            if (!destFile.getParentFile().exists()) {
                Assert.isTrue(destFile.getParentFile().mkdirs(), "创建对象存储目录失败");
            }
            if (!destFile.createNewFile()) {
                log.warn("创建临时文件失败");
            }
        }

        try (var result = doDownloadFile(url)) {
            if (result != null) {
                FileUtils.copyInputStreamToFile(result, destFile);
            } else {
                log.error("下载文件失败，url：{}", url);
                return null;
            }
        }
        return destFile;
    }

    /**
     * 下载文件
     *
     * @param url 文件URL
     * @return 返回文件
     * @throws IOException 异常
     */
    @Override
    public InputStream doDownloadFile(@Nonnull String url) throws IOException {
        var request = new Request.Builder()
                .addHeader("connection", "keep-alive")
                .url(url).build();
        var response = okHttpClient.newCall(request).execute();
        if (response.code() != 200) {
            return null;
        }
        return getInputStreamFromResponse(response);
    }

    /**
     * 异步下载文件
     *
     * @param url           文件URL
     * @param asyncCallback 异步回调
     * @throws IOException 异常
     */
    @Override
    public void doAsyncDownloadFile(@Nonnull String url, AsyncCallback<InputStream> asyncCallback) throws IOException {
        doAsyncDownloadFile(url, Map.of(), asyncCallback);
    }

    /**
     * get 提交下载请求
     *
     * @param url           请求地址
     * @param headers       请求头
     * @param asyncCallback 异步回调
     * @throws IOException io异常
     */
    @Override
    public void doAsyncDownloadFile(@Nonnull String url, Map<String, String> headers, AsyncCallback<InputStream> asyncCallback) throws IOException {
        var requestBuilder = new Request.Builder()
                .addHeader("connection", "keep-alive")
                .url(url);
        if (MapUtils.isNotEmpty(headers)) {
            headers.forEach(requestBuilder::addHeader);
        }
        okHttpClient.newCall(requestBuilder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(@Nonnull Call call, @Nonnull IOException e) {
                asyncCallback.onFailure(e);
            }

            @Override
            public void onResponse(@Nonnull Call call, @Nonnull Response response) throws IOException {
                InputStream inputStream = getInputStreamFromResponse(response);
                HttpResponse httpResponse = handleResponse(response);
                asyncCallback.onResponse(httpResponse, inputStream);
            }
        });
    }

    /**
     * post提交下载请求
     *
     * @param url    请求地址
     * @param params 请求参数
     * @return 返回文件流
     * @throws IOException io异常
     */
    @Override
    public InputStream doDownloadFileByPost(String url, Object params) throws IOException {
        return doDownloadFileByPost(url, params, Map.of());
    }

    /**
     * post提交下载请求
     *
     * @param url       请求地址
     * @param params    请求参数
     * @param headerMap 请求头
     * @return 返回文件流
     * @throws IOException io异常
     */
    @Override
    public InputStream doDownloadFileByPost(String url, Object params, Map<String, String> headerMap) throws IOException {
        Assert.notNull(url, "url不能为空");
        var json = JsonUtils.getInstance().serialize(params);
        Objects.requireNonNull(json, "参数无效!");
        var requestBody = RequestBody.create(json, JSON_MEDIA_TYPE);
        var requestBuilder = new Request.Builder();
        if (MapUtils.isNotEmpty(headerMap)) {
            headerMap.forEach(requestBuilder::addHeader);
        }
        requestBuilder.addHeader("connection", "keep-alive");
        var response = okHttpClient.newCall(requestBuilder.url(url).post(requestBody).build()).execute();
        return getInputStreamFromResponse(response);
    }

    /**
     * post提交下载请求
     *
     * @param url           请求地址
     * @param params        请求参数
     * @param asyncCallback 异步回调
     * @throws IOException io异常
     */
    @Override
    public void doAsyncDownloadFileByPost(String url, Object params, AsyncCallback<InputStream> asyncCallback) throws IOException {
        doAsyncDownloadFileByPost(url, params, Map.of(), asyncCallback);
    }

    /**
     * post提交下载请求
     *
     * @param url           请求地址
     * @param params        请求参数
     * @param headers       请求头
     * @param asyncCallback 异步回调
     * @throws IOException io异常
     */
    @Override
    public void doAsyncDownloadFileByPost(String url, Object params, Map<String, String> headers, AsyncCallback<InputStream> asyncCallback) throws IOException {
        var json = JsonUtils.getInstance().serialize(params);
        Objects.requireNonNull(json, "参数无效!");
        var requestBody = RequestBody.create(json, JSON_MEDIA_TYPE);
        var requestBuilder = new Request.Builder();
        if (MapUtils.isNotEmpty(headers)) {
            headers.forEach(requestBuilder::addHeader);
        }
        var request = requestBuilder.addHeader("connection", "keep-alive")
                .url(url).post(requestBody).build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@Nonnull Call call, @Nonnull IOException e) {
                asyncCallback.onFailure(e);
            }

            @Override
            public void onResponse(@Nonnull Call call, @Nonnull Response response) throws IOException {
                HttpResponse httpResponse = handleResponse(response);
                InputStream inputStream = getInputStreamFromResponse(response);
                asyncCallback.onResponse(httpResponse, inputStream);
            }
        });
    }

    private InputStream getInputStreamFromResponse(Response response) throws IOException {
        var contentTypeHeader = response.header("Content-Type");
        if (Objects.isNull(contentTypeHeader) || contentTypeHeader.contains("application/json")) {
            var responseContent = Objects.requireNonNull(response.body()).string();
            response.close();
            log.error("下载外文件失败，内容：{}", responseContent);
            return null;
        }
        try (response) {
            return new ByteArrayInputStream(Objects.requireNonNull(response.body()).bytes());
        }
    }

    /**
     * 执行 POST 请求的方法
     *
     * @param url         请求URL
     * @param data        请求参数 JSON 格式
     * @param headerMap   请求头
     * @param contentType 内容类型
     * @param errors      错误信息
     * @param timeout     超时时间
     * @return 返回发送结果
     */
    private String executePost(String url, String data, Map<String, String> headerMap, MediaType contentType, List<Throwable> errors, Long timeout) {
        RequestBody requestBody;
        if (Objects.isNull(contentType.charset())) {
            requestBody = RequestBody.create(data.getBytes(StandardCharsets.UTF_8), contentType);
        } else {
            requestBody = RequestBody.create(data, contentType);
        }
        var requestBuilder = new Request.Builder();
        if (MapUtils.isNotEmpty(headerMap)) {
            headerMap.forEach(requestBuilder::addHeader);
        }
        var request = requestBuilder.url(url).post(requestBody).build();

        return execute(request, errors, timeout);
    }

    /**
     * 执行 POST 请求的方法
     *
     * @param url         请求URL
     * @param data        参数体
     * @param contentType 参数体 MIME 类型
     * @param errors      错误信息
     * @return 返回发送结果
     */
    private String executePost(String url, String data, MediaType contentType, List<Throwable> errors) {
        var requestBody = RequestBody.create(data, contentType);
        var request = new Request.Builder().url(url).post(requestBody).build();
        return execute(request, errors, null);
    }

    /**
     * 解析请求结果并返回 Payload 消息内容的方法
     *
     * @param request 请求对象
     * @return 返回 Payload 消息内容
     */
    private String execute(Request request, List<Throwable> errors, Long timeout) {
        var client = okHttpClient;
        if (timeout != null && timeout > 0L) {
            if (CUSTOM_CLIENTS.containsKey(timeout)) {
                client = CUSTOM_CLIENTS.get(timeout);
            } else {
                client = okHttpClient.newBuilder().callTimeout(timeout, TimeUnit.MILLISECONDS).build();
                CUSTOM_CLIENTS.put(timeout, client);
            }
        }
        try (var response = client.newCall(request).execute()) {
            return Objects.requireNonNull(response.body()).string();
        } catch (IOException e) {
            log.error(ExceptionUtils.getStackTrace(e));
            if (errors != null) {
                errors.add(e);
            }
        }
        return "";
    }

    /**
     * 解析请求结果并返回 Payload 消息内容的方法
     *
     * @param request 请求对象
     * @return 返回 Payload 消息内容
     */
    private HttpResponse executeAndReturnResponse(Request request, List<Throwable> errors) {
        try (var response = okHttpClient.newCall(request).execute()) {
            return handleResponse(response);
        } catch (IOException e) {
            log.error(ExceptionUtils.getStackTrace(e));
            if (errors != null) {
                errors.add(e);
            }
            return null;
        }
    }

    /**
     * 执行异步请求的方法
     *
     * @param cls           应答报文类型
     * @param request       请求对象
     * @param timeout       超时时间（单位：毫秒）
     * @param asyncCallback 应答结果的异步回调函数
     * @param <T>           应答报文的具体类型
     */
    private <T> void executeAsync(Class<T> cls, Request request, Long timeout, AsyncCallback<T> asyncCallback) {
        var client = okHttpClient;
        if (timeout != null && timeout > 0L) {
            if (CUSTOM_CLIENTS.containsKey(timeout)) {
                client = CUSTOM_CLIENTS.get(timeout);
            } else {
                client = okHttpClient.newBuilder().callTimeout(timeout, TimeUnit.MILLISECONDS).build();
                CUSTOM_CLIENTS.put(timeout, client);
            }
        }
        client.newCall(request).enqueue(new Callback() {

            public void onFailure(@Nonnull Call call, @Nonnull IOException e) {
                OkHttpClientImpl.log.error(ExceptionUtils.getStackTrace(e));
                asyncCallback.onFailure(e);
            }


            public void onResponse(@Nonnull Call call, @Nonnull Response response) throws IOException {
                OkHttpClientImpl.log.info("异步请求成功");
                HttpResponse httpResponse = handleResponse(response);
                asyncCallback.onResponse(httpResponse, httpResponse.getBody(cls));
            }
        });
    }

    private HttpResponse handleResponse(@Nonnull Response response) throws IOException {
        var code = response.code();
        var message = response.message();
        var headers = response.headers();
        var redirect = response.isRedirect();
        var successful = response.isSuccessful();
        var protocol = response.protocol();
        var body = response.body();
        Map<String, String> pairs = Collections.mapOf();
        headers.iterator().forEachRemaining(header -> pairs.put(header.getFirst(), header.getSecond()));
        var httpHeader = new HttpHeader(pairs);
        var builder = HttpResponse.builder()
                .code(code)
                .message(message)
                .redirect(redirect)
                .successful(successful)
                .protocol(HttpProtocol.convert(protocol))
                .headers(httpHeader);
        // 否则封装响应体
        var mediaType = body.contentType();
        if (mediaType != null) {
            builder.contentType(mediaType.toString());
        }
        builder.contentLength(body.contentLength());
        try {
            builder.body(body.bytes());
        } catch (IOException _) {
        }
        builder.byteStream(body.byteStream());
        return builder.build();
    }

    private void generateUrl(Map<String, String> params, StringBuilder sb) {
        if (params != null && !params.isEmpty()) {
            var firstFlag = true;

            for (var et : params.entrySet()) {
                if (firstFlag) {
                    sb.append("?").append(et.getKey()).append("=").append(et.getValue());
                    firstFlag = false;
                } else {
                    sb.append("&").append(et.getKey()).append("=").append(et.getValue());
                }
            }
        }
    }

    @Nullable
    private static UploadHandleResult getResult(Map<String, File> fileMap, Map<String, String> params, Map<String, String> headerMap) {
        var builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
        for (var entry : fileMap.entrySet()) {
            var key = entry.getKey();
            var file = entry.getValue();
            String contentType;
            try {
                var tika = new Tika();
                contentType = tika.detect(file);
            } catch (IOException e) {
                log.error("获取文件ContentType出错");
                return null;
            }
            builder.addFormDataPart(key, file.getName(), RequestBody.create(file, MediaType.parse(contentType)));
        }
        if (MapUtils.isNotEmpty(params)) {
            params.forEach(builder::addFormDataPart);
        }
        var requestBuilder = new Request.Builder();
        if (MapUtils.isNotEmpty(headerMap)) {
            headerMap.forEach(requestBuilder::addHeader);
        }
        return new UploadHandleResult(builder.build(), requestBuilder);
    }

    private record UploadHandleResult(MultipartBody builder, Request.Builder requestBuilder) {
    }

    @Override
    @Nullable
    public <T> T doPostFormBody(Class<T> cls, String url, Map<String, String> params) {
        var request = postRequest(url, params,null);
        var result = execute(request, new ArrayList<>(), null);
        return JsonUtils.getInstance().deserialize(result, cls);
    }
}
