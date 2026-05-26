package com.richie.component.logging.interceptors;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.Charset;

/**
 * HttpServletRequest 封装类
 *
 * @author richie696
 * @version 1.0
 * @since 2022-01-09 17:40:04
 */
@Slf4j
public class RequestWrapper extends HttpServletRequestWrapper {

    /**
     * 数据载荷
     */
    private final byte[] payload;

    /**
     * 使用原始请求构造，并将 body 读取为字节数组以便多次读取。
     *
     * @param request 原始 HTTP 请求
     */
    public RequestWrapper(HttpServletRequest request) {
        super(request);
        String bodyString = getBodyString(request);
        payload = bodyString.getBytes(Charset.defaultCharset());
    }

    /**
     * 从请求输入流读取 body 并转为字符串。
     *
     * @param request 请求
     * @return 请求体字符串
     */
    public String getBodyString(final ServletRequest request) {
        try {
            return convertInputStreamToString(request.getInputStream());
        } catch (IOException e) {
            log.error("", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 返回已缓存的请求体字符串。
     *
     * @return 请求体字符串
     */
    public String getBodyString() {
        return convertInputStreamToString(new ByteArrayInputStream(payload));
    }

    /**
     * 将输入流内容读取为字符串（使用默认字符集）。
     *
     * @param inputStream 输入流
     * @return 字符串内容
     */
    private String convertInputStreamToString(InputStream inputStream) {
        var content = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(inputStream, Charset.defaultCharset()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
        } catch (IOException e) {
            log.error("", e);
            throw new RuntimeException(e);
        }
        return content.toString();
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }

    @Override
    public ServletInputStream getInputStream() {

        final var inputStream = new ByteArrayInputStream(payload);

        return new ServletInputStream() {
            @Override
            public int read() {
                return inputStream.read();
            }

            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean isReady() {
                return false;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }
        };
    }

}
