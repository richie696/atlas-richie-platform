package com.richie.component.http.client;

import com.richie.context.utils.data.Collections;
import com.richie.component.http.bean.HttpHeader;
import com.richie.component.http.bean.HttpProtocol;
import com.richie.component.http.bean.HttpResponse;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Timeout;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Apache HttpClient 5 同步请求执行器。
 */
@Slf4j
final class HttpClient5Executor {

    static final ContentType JSON = ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8);
    static final ContentType XML = ContentType.APPLICATION_XML.withCharset(StandardCharsets.UTF_8);
    static final ContentType SOAP12 = ContentType.create("application/soap+xml", StandardCharsets.UTF_8);

    private final HttpClient httpClient;

    HttpClient5Executor(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    String execute(ClassicHttpRequest request, List<Throwable> errors, Long timeoutMs) {
        applyTimeout(request, timeoutMs);
        try {
            return httpClient.execute(request, this::readBodyString);
        } catch (IOException e) {
            log.error(ExceptionUtils.getStackTrace(e));
            if (errors != null) {
                errors.add(e);
            }
            return "";
        }
    }

    HttpResponse executeAndReturnResponse(ClassicHttpRequest request, List<Throwable> errors, Long timeoutMs) {
        applyTimeout(request, timeoutMs);
        try {
            return httpClient.execute(request, response -> {
                try {
                    return toHttpResponse(response);
                } catch (ParseException e) {
                    throw new IOException(e);
                }
            });
        } catch (IOException e) {
            log.error(ExceptionUtils.getStackTrace(e));
            if (errors != null) {
                errors.add(e);
            }
            return null;
        }
    }

    InputStream download(ClassicHttpRequest request) throws IOException {
        applyTimeout(request, null);
        return httpClient.execute(request, response -> {
            try {
                return toDownloadStream(response);
            } catch (ParseException e) {
                throw new IOException(e);
            }
        });
    }

    StreamDownload downloadWithResponse(ClassicHttpRequest request) throws IOException {
        applyTimeout(request, null);
        return httpClient.execute(request, response -> {
            var contentTypeHeader = response.getFirstHeader("Content-Type");
            String contentTypeValue = contentTypeHeader == null ? null : contentTypeHeader.getValue();
            if (contentTypeValue == null || contentTypeValue.contains("application/json")) {
                String responseContent = readBodyString(response);
                log.error("下载外文件失败，内容：{}", responseContent);
                return new StreamDownload(toHttpResponse(response), null);
            }
            HttpEntity entity = response.getEntity();
            byte[] bytes = entity == null ? new byte[0] : EntityUtils.toByteArray(entity);
            var httpResponse = buildHttpResponse(response, bytes);
            return new StreamDownload(httpResponse, new ByteArrayInputStream(bytes));
        });
    }

    record StreamDownload(HttpResponse response, InputStream stream) {
    }

    HttpGet buildGet(String url, Map<String, String> params, Map<String, String> headerMap) {
        var get = new HttpGet(appendQuery(url, params));
        applyHeaders(get, headerMap);
        return get;
    }

    HttpPost buildFormPost(String url, Map<String, String> params, Map<String, String> headerMap) {
        var post = new HttpPost(url);
        applyHeaders(post, headerMap);
        if (MapUtils.isNotEmpty(params)) {
            List<NameValuePair> pairs = new ArrayList<>();
            params.forEach((k, v) -> pairs.add(new BasicNameValuePair(k, v)));
            post.setEntity(new UrlEncodedFormEntity(pairs, StandardCharsets.UTF_8));
        }
        return post;
    }

    HttpPut buildFormPut(String url, Map<String, String> params, Map<String, String> headerMap) {
        var put = new HttpPut(url);
        applyHeaders(put, headerMap);
        if (MapUtils.isNotEmpty(params)) {
            List<NameValuePair> pairs = new ArrayList<>();
            params.forEach((k, v) -> pairs.add(new BasicNameValuePair(k, v)));
            put.setEntity(new UrlEncodedFormEntity(pairs, StandardCharsets.UTF_8));
        }
        return put;
    }

    HttpPost buildBodyPost(String url, String body, ContentType contentType, Map<String, String> headerMap) {
        var post = new HttpPost(url);
        applyHeaders(post, headerMap);
        post.setEntity(new StringEntity(body, contentType));
        return post;
    }

    HttpDelete buildDelete(String url, String json, Map<String, String> headerMap) {
        var delete = new HttpDelete(url);
        applyHeaders(delete, headerMap);
        if (StringUtils.isNotBlank(json)) {
            delete.setEntity(new StringEntity(json, JSON));
        }
        return delete;
    }

    HttpPost buildMultipartPost(String url, Map<String, String> params, Map<String, String> headerMap,
                                MultipartEntityBuilder multipartBuilder) {
        var post = new HttpPost(url);
        applyHeaders(post, headerMap);
        if (MapUtils.isNotEmpty(params)) {
            params.forEach(multipartBuilder::addTextBody);
        }
        post.setEntity(multipartBuilder.build());
        return post;
    }

    static String appendQuery(String url, Map<String, String> params) {
        if (MapUtils.isEmpty(params)) {
            return url;
        }
        var sb = new StringBuilder(url);
        var first = true;
        for (var entry : params.entrySet()) {
            sb.append(first ? '?' : '&').append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }

    static void applyHeaders(HttpUriRequestBase request, Map<String, String> headerMap) {
        if (MapUtils.isNotEmpty(headerMap)) {
            headerMap.forEach(request::addHeader);
        }
    }

    private void applyTimeout(ClassicHttpRequest request, Long timeoutMs) {
        if (timeoutMs == null || timeoutMs <= 0L || !(request instanceof HttpUriRequestBase base)) {
            return;
        }
        RequestConfig config = RequestConfig.custom()
                .setResponseTimeout(Timeout.ofMilliseconds(timeoutMs))
                .build();
        base.setConfig(config);
    }

    private String readBodyString(ClassicHttpResponse response) throws IOException, ParseException {
        HttpEntity entity = response.getEntity();
        if (entity == null) {
            return "";
        }
        return EntityUtils.toString(entity, StandardCharsets.UTF_8);
    }

    private HttpResponse toHttpResponse(ClassicHttpResponse response) throws IOException, ParseException {
        HttpEntity entity = response.getEntity();
        byte[] bodyBytes = entity == null ? new byte[0] : EntityUtils.toByteArray(entity);
        return buildHttpResponse(response, bodyBytes);
    }

    private HttpResponse buildHttpResponse(ClassicHttpResponse response, byte[] bodyBytes) {
        var headerMap = Collections.<String, String>mapOf();
        for (var header : response.getHeaders()) {
            headerMap.put(header.getName(), header.getValue());
        }
        var builder = HttpResponse.builder()
                .code(response.getCode())
                .message(response.getReasonPhrase())
                .redirect(response.getCode() >= 300 && response.getCode() < 400)
                .successful(response.getCode() >= 200 && response.getCode() < 300)
                .protocol(toHttpProtocol(response.getVersion()))
                .headers(new HttpHeader(headerMap))
                .body(bodyBytes)
                .byteStream(bodyBytes.length > 0 ? new ByteArrayInputStream(bodyBytes) : InputStream.nullInputStream());
        HttpEntity entity = response.getEntity();
        if (entity != null && entity.getContentType() != null) {
            builder.contentType(entity.getContentType());
            builder.contentLength(entity.getContentLength());
        }
        return builder.build();
    }

    private InputStream toDownloadStream(ClassicHttpResponse response) throws IOException, ParseException {
        var contentType = response.getFirstHeader("Content-Type");
        String contentTypeValue = contentType == null ? null : contentType.getValue();
        if (contentTypeValue == null || contentTypeValue.contains("application/json")) {
            String responseContent = readBodyString(response);
            log.error("下载外文件失败，内容：{}", responseContent);
            return null;
        }
        HttpEntity entity = response.getEntity();
        if (entity == null) {
            return null;
        }
        return new ByteArrayInputStream(EntityUtils.toByteArray(entity));
    }

    private static HttpProtocol toHttpProtocol(org.apache.hc.core5.http.ProtocolVersion version) {
        if (version == null) {
            return HttpProtocol.HTTP_1_1;
        }
        var text = version.format().toLowerCase();
        if (text.contains("1.0")) {
            return HttpProtocol.HTTP_1_0;
        }
        if (text.contains("2")) {
            return HttpProtocol.HTTP_2;
        }
        return HttpProtocol.HTTP_1_1;
    }

    MultipartEntityBuilder newMultipartBuilder() {
        return MultipartEntityBuilder.create();
    }

    void addFilePart(MultipartEntityBuilder builder, String fieldName, @Nonnull File file, String contentType) {
        builder.addBinaryBody(fieldName, file, ContentType.parse(contentType), file.getName());
    }

    void addBytesPart(MultipartEntityBuilder builder, String fieldName, String fileName, byte[] bytes, String contentType) {
        builder.addBinaryBody(fieldName, bytes, ContentType.parse(contentType), fileName);
    }
}
