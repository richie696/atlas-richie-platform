package com.richie.component.http.client;

import com.richie.context.utils.data.JsonUtils;
import com.richie.component.http.bean.AsyncCallback;
import com.richie.component.http.bean.HttpMethod;
import com.richie.component.http.bean.HttpResponse;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.tika.Tika;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import tools.jackson.core.type.TypeReference;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HttpClient5 实现（行为与 {@link OkHttpClientImpl} 对齐）。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-08-22 15:29:53
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "platform.component.http", name = "provider", havingValue = "httpclient5")
public class HttpClient5Impl implements HttpClientApi {

    private final HttpClient5Executor executor;
    private final ExecutorService asyncExecutor = Executors.newCachedThreadPool();

    public HttpClient5Impl(HttpClient httpClient5) {
        this.executor = new HttpClient5Executor(httpClient5);
    }

    @Override
    public HttpResponse doRequest(HttpMethod method, String url, Map<String, String> params, Map<String, String> headers, List<Throwable> returnErrors) {
        return switch (method) {
            case GET -> executor.executeAndReturnResponse(executor.buildGet(url, params, headers), returnErrors, null);
            case POST -> executor.executeAndReturnResponse(executor.buildFormPost(url, params, headers), returnErrors, null);
            case PUT -> executor.executeAndReturnResponse(executor.buildFormPut(url, params, headers), returnErrors, null);
            case DELETE -> executor.executeAndReturnResponse(
                    executor.buildDelete(url, JsonUtils.getInstance().serialize(params), headers), returnErrors, null);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: %s".formatted(method));
        };
    }

    @Override
    @Nullable
    public String doGet(String url) {
        return doGet(url, null, null);
    }

    @Override
    public void doAsyncGet(String url, AsyncCallback<String> asyncCallback) {
        doAsyncGet(String.class, url, null, null, asyncCallback);
    }

    @Override
    @Nullable
    public <T> T doGet(Class<T> cls, String url) {
        return deserialize(doGet(url, null, null), cls);
    }

    @Override
    @Nullable
    public <T> T doGet(Class<T> cls, String url, List<Throwable> returnErrors) {
        return deserialize(doGet(url, null, null, returnErrors), cls);
    }

    @Override
    public <T> void doAsyncGet(Class<T> cls, String url, AsyncCallback<T> asyncCallback) {
        doAsyncGet(cls, url, null, null, asyncCallback);
    }

    @Override
    @Nullable
    public String doGet(String url, Map<String, String> params) {
        return doGet(url, params, null);
    }

    @Override
    @Nullable
    public <T> T doGet(Class<T> cls, String url, Map<String, String> params) {
        return deserialize(doGet(url, params, null), cls);
    }

    @Override
    public <T> void doAsyncGet(Class<T> cls, String url, Map<String, String> params, AsyncCallback<T> asyncCallback) {
        doAsyncGet(cls, url, params, null, asyncCallback);
    }

    @Override
    @Nullable
    public String doGet(String url, Map<String, String> params, Map<String, String> headerMap) {
        return doGet(url, params, headerMap, null);
    }

    @Override
    @Nullable
    public String doGet(String url, Map<String, String> params, Map<String, String> headerMap, List<Throwable> returnErrors) {
        return executor.execute(executor.buildGet(url, params, headerMap), returnErrors, null);
    }

    @Override
    @Nullable
    public <T> T doGet(Class<T> cls, String url, Map<String, String> params, Map<String, String> headerMap) {
        return deserialize(doGet(url, params, headerMap), cls);
    }

    @Override
    public <T> void doAsyncGet(Class<T> cls, String url, Map<String, String> params, Map<String, String> headerMap, AsyncCallback<T> asyncCallback) {
        var request = executor.buildGet(url, params, headerMap);
        log.info("do get request and url[{}]", HttpClient5Executor.appendQuery(url, params));
        executeAsync(cls, request, null, asyncCallback);
    }

    @Override
    @Nullable
    public String doDelete(String url, String json, Map<String, String> headerMap) {
        return doDelete(url, json, headerMap, null);
    }

    @Override
    @Nullable
    public String doDelete(String url, String json, Map<String, String> headerMap, List<Throwable> returnErrors) {
        return executor.execute(executor.buildDelete(url, json, headerMap), returnErrors, null);
    }

    @Override
    @Nullable
    public <T> T doDelete(Class<T> cls, String url, String json, Map<String, String> headerMap) {
        return deserialize(doDelete(url, json, headerMap), cls);
    }

    @Override
    public <T> void doAsyncDelete(Class<T> cls, String url, String json, Map<String, String> headerMap, AsyncCallback<T> asyncCallback) {
        executeAsync(cls, executor.buildDelete(url, json, headerMap), null, asyncCallback);
    }

    @Override
    @Nullable
    public String doPost(String url, Map<String, String> params) {
        return doPost(url, params, new ArrayList<>());
    }

    @Override
    @Nullable
    public String doPost(String url, Map<String, String> params, List<Throwable> returnErrors) {
        return executor.execute(executor.buildFormPost(url, params, null), returnErrors, null);
    }

    @Override
    @Nullable
    public String doPost(String url, Map<String, String> params, Map<String, String> headerMap, List<Throwable> returnErrors) {
        return executor.execute(executor.buildFormPost(url, params, headerMap), returnErrors, null);
    }

    @Override
    public <T> T doPost(String url, Object params, TypeReference<T> typeReference) {
        return deserialize(doPost(url, JsonUtils.getInstance().serialize(params)), typeReference);
    }

    @Override
    public <T> T doPost(String url, Object params, Map<String, String> headerMap, TypeReference<T> typeReference) {
        return deserialize(doPost(url, params, headerMap), typeReference);
    }

    @Override
    public <T> T doPost(String url, Object params, Map<String, String> headerMap, Long timeout, TypeReference<T> typeReference) {
        var json = JsonUtils.getInstance().serialize(params);
        var payload = executePost(url, json, headerMap, HttpClient5Executor.JSON, null, timeout);
        return deserialize(payload, typeReference);
    }

    @Override
    public <T> T doPost(String url, Object params, Map<String, String> headerMap, Long timeout, List<Throwable> returnErrors, TypeReference<T> typeReference) {
        var json = JsonUtils.getInstance().serialize(params);
        var payload = executePost(url, json, headerMap, HttpClient5Executor.JSON, returnErrors, timeout);
        return deserialize(payload, typeReference);
    }

    @Override
    public <T> void doAsyncPost(Class<T> cls, String url, Object params, AsyncCallback<T> asyncCallback) {
        doAsyncPost(cls, url, params, null, null, asyncCallback);
    }

    @Override
    public <T> void doAsyncPost(Class<T> cls, String url, Object params, Long timeout, AsyncCallback<T> asyncCallback) {
        doAsyncPost(cls, url, params, null, null, timeout, asyncCallback);
    }

    @Override
    public <T> void doAsyncPost(Class<T> cls, String url, Object params, Map<String, String> headerMap, AsyncCallback<T> asyncCallback) {
        doAsyncPost(cls, url, params, headerMap, null, null, asyncCallback);
    }

    @Override
    public <T> void doAsyncPost(Class<T> cls, String url, Object params, Map<String, String> headerMap, Long timeout, AsyncCallback<T> asyncCallback) {
        doAsyncPost(cls, url, params, headerMap, null, timeout, asyncCallback);
    }

    @Override
    public <T> void doAsyncPost(Class<T> cls, String url, Object params, Map<String, String> headerMap, String contentType, Long timeout, AsyncCallback<T> asyncCallback) {
        ContentType mediaType = resolveContentType(contentType);
        String data = params instanceof String s ? s : JsonUtils.getInstance().serialize(params);
        Objects.requireNonNull(data, "Invalid request body: " + params);
        var request = executor.buildBodyPost(url, data, mediaType, headerMap);
        executeAsync(cls, request, timeout, asyncCallback);
    }

    @Override
    @Nullable
    public String doPost(String url, Object argument) {
        return executePost(url, JsonUtils.getInstance().serialize(argument), null, HttpClient5Executor.JSON, null, null);
    }

    @Override
    @Nullable
    public String doPost(String url, Object argument, List<Throwable> returnErrors) {
        return executePost(url, JsonUtils.getInstance().serialize(argument), null, HttpClient5Executor.JSON, returnErrors, null);
    }

    @Override
    @Nullable
    public String doPost(String url, Object argument, Long timeout) {
        return executePost(url, JsonUtils.getInstance().serialize(argument), Map.of(), HttpClient5Executor.JSON, null, timeout);
    }

    @Override
    @Nullable
    public String doPost(String url, Object argument, List<Throwable> returnErrors, Long timeout) {
        return executePost(url, JsonUtils.getInstance().serialize(argument), Map.of(), HttpClient5Executor.JSON, returnErrors, timeout);
    }

    @Override
    @Nullable
    public String doPost(String url, Object argument, Map<String, String> headerMap) {
        return executePost(url, JsonUtils.getInstance().serialize(argument), headerMap, HttpClient5Executor.JSON, null, null);
    }

    @Override
    @Nullable
    public String doPost(String url, Object argument, Map<String, String> headerMap, List<Throwable> returnErrors) {
        return executePost(url, JsonUtils.getInstance().serialize(argument), headerMap, HttpClient5Executor.JSON, returnErrors, null);
    }

    @Override
    @Nullable
    public <T> T doPost(Class<T> cls, String url, Object argument) {
        return deserialize(executePost(url, JsonUtils.getInstance().serialize(argument), null, HttpClient5Executor.JSON, null, null), cls);
    }

    @Override
    @Nullable
    public <T> T doPost(Class<T> cls, String url, Object argument, List<Throwable> returnErrors) {
        return deserialize(executePost(url, JsonUtils.getInstance().serialize(argument), null, HttpClient5Executor.JSON, returnErrors, null), cls);
    }

    @Override
    @Nullable
    public <T> T doPost(Class<T> cls, String url, Object argument, String contentType) {
        var json = JsonUtils.getInstance().serialize(argument);
        var payload = executePost(url, json, null, resolveContentType(contentType), null, null);
        return deserialize(payload, cls);
    }

    @Override
    @Nullable
    public <T> T doPost(Class<T> cls, String url, Object argument, String contentType, List<Throwable> returnErrors) {
        var json = JsonUtils.getInstance().serialize(argument);
        var payload = executePost(url, json, null, resolveContentType(contentType), returnErrors, null);
        return deserialize(payload, cls);
    }

    @Override
    @Nullable
    public <T> T doPost(Class<T> cls, String url, Object argument, Map<String, String> headerMap) {
        var json = JsonUtils.getInstance().serialize(argument);
        if (StringUtils.isBlank(json)) {
            throw new IllegalArgumentException("参数无效!");
        }
        return deserialize(executePost(url, json, headerMap, HttpClient5Executor.JSON, null, null), cls);
    }

    @Override
    @Nullable
    public <T> T doPost(Class<T> cls, String url, Object argument, Map<String, String> headerMap, String contentType) {
        return deserialize(doPost(url, JsonUtils.getInstance().serialize(argument), headerMap, contentType), cls);
    }

    @Override
    @Nullable
    public <T> T doPost(Class<T> cls, String url, Object argument, Map<String, String> headerMap, List<Throwable> returnErrors) {
        var json = JsonUtils.getInstance().serialize(argument);
        if (StringUtils.isBlank(json)) {
            throw new IllegalArgumentException("参数无效!");
        }
        return deserialize(executePost(url, json, headerMap, HttpClient5Executor.JSON, returnErrors, null), cls);
    }

    @Override
    @Nullable
    public String doPost(String url, Object argument, Map<String, String> headerMap, String contentType) {
        var json = JsonUtils.getInstance().serialize(argument);
        if (StringUtils.isAnyBlank(json, contentType)) {
            throw new IllegalArgumentException("contentType参数不能为空!");
        }
        return executePost(url, json, headerMap, resolveContentType(contentType), null, null);
    }

    @Override
    @Nullable
    public String doPost(String url, String json) {
        return executePost(url, json, null, HttpClient5Executor.JSON, null, null);
    }

    @Override
    @Nullable
    public String doPost(String url, Object argument, Map<String, String> headerMap, String contentType, List<Throwable> returnErrors) {
        var json = JsonUtils.getInstance().serialize(argument);
        if (StringUtils.isAnyBlank(json, contentType)) {
            throw new IllegalArgumentException("contentType参数不能为空!");
        }
        return executePost(url, json, headerMap, resolveContentType(contentType), returnErrors, null);
    }

    @Override
    @Nullable
    public String doPostSOAP1_2(String url, String xml) {
        return doPostSOAP1_2(url, xml, null);
    }

    @Override
    @Nullable
    public String doPostSOAP1_2(String url, String xml, List<Throwable> returnErrors) {
        log.info("do post request and url[{}]", url);
        return executePost(url, xml, null, HttpClient5Executor.SOAP12, returnErrors, null);
    }

    @Override
    @Nullable
    public <T> T doPostSOAP1_2(Class<T> cls, String url, String xml) {
        return doPostSOAP1_2(cls, url, xml, null);
    }

    @Override
    public <T> T doPostSOAP1_2(Class<T> cls, String url, String xml, List<Throwable> returnErrors) {
        return deserialize(executePost(url, xml, null, HttpClient5Executor.SOAP12, returnErrors, null), cls);
    }

    @Override
    public <T> void doAsyncPostSOAP1_2(Class<T> cls, String url, String xml, AsyncCallback<T> asyncCallback) {
        doAsyncPost(cls, url, xml, null, HttpClient5Executor.SOAP12.getMimeType(), null, asyncCallback);
    }

    @Override
    public <T> void doAsyncPostSOAP1_2(Class<T> cls, String url, String xml, Map<String, String> headerMap, AsyncCallback<T> asyncCallback) {
        doAsyncPost(cls, url, xml, headerMap, HttpClient5Executor.SOAP12.getMimeType(), null, asyncCallback);
    }

    @Override
    @Nullable
    public String doPostXml(String url, String xml) {
        return doPostXml(url, xml, null);
    }

    @Override
    @Nullable
    public String doPostXml(String url, String xml, List<Throwable> exceptions) {
        log.info("do post request and url[{}]", url);
        return executePost(url, xml, null, HttpClient5Executor.XML, exceptions, null);
    }

    @Override
    @Nullable
    public <T> T doPostXml(Class<T> cls, String url, String xml) {
        return doPostXml(cls, url, xml, null);
    }

    @Override
    public <T> T doPostXml(Class<T> cls, String url, String xml, List<Throwable> exceptions) {
        return deserialize(executePost(url, xml, null, HttpClient5Executor.XML, exceptions, null), cls);
    }

    @Override
    public <T> void doAsyncPostXml(Class<T> cls, String url, String xml, AsyncCallback<T> asyncCallback) {
        doAsyncPost(cls, url, xml, null, HttpClient5Executor.XML.getMimeType(), null, asyncCallback);
    }

    @Override
    public <T> void doAsyncPostXml(Class<T> cls, String url, String xml, Map<String, String> headerMap, AsyncCallback<T> asyncCallback) {
        doAsyncPost(cls, url, xml, headerMap, HttpClient5Executor.XML.getMimeType(), null, asyncCallback);
    }

    @Override
    @Nullable
    public <T> T doUploadInputStream(@Nonnull String url, @Nonnull UploadFileMetadata metadata, Map<String, String> params, Map<String, String> headerMap, TypeReference<T> typeReference) {
        return deserialize(doUploadInputStream(url, metadata, params, headerMap), typeReference);
    }

    @Override
    @Nullable
    public String doUploadInputStream(@Nonnull String url, @Nonnull UploadFileMetadata metadata, Map<String, String> params, Map<String, String> headerMap) {
        return doUploadInputStream(url, List.of(metadata), params, headerMap);
    }

    @Override
    public String doUploadInputStream(@Nonnull String url, @Nonnull List<UploadFileMetadata> metadataList) {
        return doUploadInputStream(url, metadataList, null, null);
    }

    @Override
    public String doUploadInputStream(@Nonnull String url, @Nonnull List<UploadFileMetadata> metadataList, Map<String, String> params) {
        return doUploadInputStream(url, metadataList, params, null);
    }

    @Override
    @Nullable
    public String doUploadInputStream(@Nonnull String url, @Nonnull List<UploadFileMetadata> metadataList, Map<String, String> params, Map<String, String> headerMap) {
        var multipart = executor.newMultipartBuilder();
        var tika = new Tika();
        for (var metadata : metadataList) {
            try (var inputStream = metadata.inputStream()) {
                var detected = tika.detect(inputStream);
                if (detected == null) {
                    log.error("文件类型错误，上传失败。");
                    continue;
                }
                var bytes = inputStream.readAllBytes();
                executor.addBytesPart(multipart, metadata.paramName(), metadata.fileName(), bytes, detected);
            } catch (IOException e) {
                log.error("获取文件ContentType出错");
            }
        }
        return executor.execute(executor.buildMultipartPost(url, params, headerMap, multipart), null, null);
    }

    @Override
    public String doUploadFile(String url, Map<String, File> fileMap, Map<String, String> params, Map<String, String> headerMap) {
        return doUploadFile(url, fileMap, params, headerMap, null);
    }

    @Override
    public String doUploadFile(String url, Map<String, File> fileMap, Map<String, String> params, Map<String, String> headerMap, List<Throwable> returnErrors) {
        Assert.notNull(url, "url不能为空");
        Assert.notEmpty(fileMap, "上传文件不能为空");
        var multipart = buildFileMultipart(fileMap);
        if (multipart == null) {
            return "";
        }
        return executor.execute(executor.buildMultipartPost(url, params, headerMap, multipart), returnErrors, null);
    }

    @Override
    public void doAsyncUploadFile(String url, Map<String, File> fileMap, Map<String, String> params, Map<String, String> headerMap, AsyncCallback<String> asyncCallback) {
        doAsyncUploadFile(url, fileMap, params, headerMap, null, asyncCallback);
    }

    @Override
    public void doAsyncUploadFile(String url, Map<String, File> fileMap, Map<String, String> params, Map<String, String> headerMap, Long timeout, AsyncCallback<String> asyncCallback) {
        Assert.notNull(url, "url不能为空");
        Assert.notEmpty(fileMap, "上传文件不能为空");
        var multipart = buildFileMultipart(fileMap);
        if (multipart == null) {
            log.error("上传文件失败，获取文件ContentType出错，url：{}", url);
            return;
        }
        executeAsync(String.class, executor.buildMultipartPost(url, params, headerMap, multipart), timeout, asyncCallback);
    }

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

    @Override
    public InputStream doDownloadFile(@Nonnull String url) throws IOException {
        var get = executor.buildGet(url, null, Map.of("connection", "keep-alive"));
        return executor.download(get);
    }

    @Override
    public void doAsyncDownloadFile(@Nonnull String url, AsyncCallback<InputStream> asyncCallback) throws IOException {
        doAsyncDownloadFile(url, Map.of(), asyncCallback);
    }

    @Override
    public void doAsyncDownloadFile(@Nonnull String url, Map<String, String> headers, AsyncCallback<InputStream> asyncCallback) {
        asyncExecutor.execute(() -> {
            try {
                var get = executor.buildGet(url, null, headers);
                get.addHeader("connection", "keep-alive");
                var outcome = executor.downloadWithResponse(get);
                asyncCallback.onResponse(outcome.response(), outcome.stream());
            } catch (Exception e) {
                asyncCallback.onFailure(e instanceof IOException io ? io : new IOException(e));
            }
        });
    }

    @Override
    public InputStream doDownloadFileByPost(String url, Object params) throws IOException {
        return doDownloadFileByPost(url, params, Map.of());
    }

    @Override
    public InputStream doDownloadFileByPost(String url, Object params, Map<String, String> headerMap) throws IOException {
        Assert.notNull(url, "url不能为空");
        var json = JsonUtils.getInstance().serialize(params);
        Objects.requireNonNull(json, "参数无效!");
        var post = executor.buildBodyPost(url, json, HttpClient5Executor.JSON, headerMap);
        post.addHeader("connection", "keep-alive");
        return executor.download(post);
    }

    @Override
    public void doAsyncDownloadFileByPost(String url, Object params, AsyncCallback<InputStream> asyncCallback) {
        doAsyncDownloadFileByPost(url, params, Map.of(), asyncCallback);
    }

    @Override
    public void doAsyncDownloadFileByPost(String url, Object params, Map<String, String> headers, AsyncCallback<InputStream> asyncCallback) {
        asyncExecutor.execute(() -> {
            try {
                var json = JsonUtils.getInstance().serialize(params);
                Objects.requireNonNull(json, "参数无效!");
                var post = executor.buildBodyPost(url, json, HttpClient5Executor.JSON, headers);
                post.addHeader("connection", "keep-alive");
                var outcome = executor.downloadWithResponse(post);
                asyncCallback.onResponse(outcome.response(), outcome.stream());
            } catch (Exception e) {
                asyncCallback.onFailure(e instanceof IOException io ? io : new IOException(e));
            }
        });
    }

    @Override
    @Nullable
    public <T> T doPostFormBody(Class<T> cls, String url, Map<String, String> params) {
        var result = executor.execute(executor.buildFormPost(url, params, null), new ArrayList<>(), null);
        return JsonUtils.getInstance().deserialize(result, cls);
    }

    private String executePost(String url, String data, Map<String, String> headerMap, ContentType contentType, List<Throwable> errors, Long timeout) {
        return executor.execute(executor.buildBodyPost(url, data, contentType, headerMap), errors, timeout);
    }

    private String executePost(String url, String data, ContentType contentType, List<Throwable> errors) {
        return executePost(url, data, null, contentType, errors, null);
    }

    @Nullable
    private MultipartEntityBuilder buildFileMultipart(Map<String, File> fileMap) {
        var builder = executor.newMultipartBuilder();
        var tika = new Tika();
        for (var entry : fileMap.entrySet()) {
            try {
                var contentType = tika.detect(entry.getValue());
                executor.addFilePart(builder, entry.getKey(), entry.getValue(), contentType);
            } catch (IOException e) {
                log.error("获取文件ContentType出错");
                return null;
            }
        }
        return builder;
    }

    private <T> void executeAsync(Class<T> cls, ClassicHttpRequest request, Long timeout, AsyncCallback<T> asyncCallback) {
        asyncExecutor.execute(() -> {
            try {
                HttpResponse httpResponse = executor.executeAndReturnResponse(request, null, timeout);
                if (httpResponse == null) {
                    asyncCallback.onFailure(new IOException("HTTP response is null"));
                    return;
                }
                log.info("异步请求成功");
                asyncCallback.onResponse(httpResponse, httpResponse.getBody(cls));
            } catch (Exception e) {
                log.error(ExceptionUtils.getStackTrace(e));
                asyncCallback.onFailure(new IOException(e));
            }
        });
    }

    /**
     * 解析可空的 Content-Type；未指定时默认 application/json（与 OkHttp 实现一致）。
     */
    private static ContentType resolveContentType(String contentType) {
        if (contentType == null) {
            return HttpClient5Executor.JSON;
        }
        var parsed = ContentType.parse(contentType);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid content type: %s".formatted(contentType));
        }
        return parsed;
    }

    @Nullable
    private static <T> T deserialize(@Nullable String payload, Class<T> cls) {
        if (StringUtils.isBlank(payload)) {
            return null;
        }
        return JsonUtils.getInstance().deserialize(payload, cls);
    }

    @Nullable
    private static <T> T deserialize(@Nullable String payload, TypeReference<T> typeReference) {
        if (StringUtils.isBlank(payload)) {
            return null;
        }
        return JsonUtils.getInstance().deserialize(payload, typeReference);
    }
}
