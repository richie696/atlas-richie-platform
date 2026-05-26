package com.richie.component.http.bean;

import com.richie.context.utils.data.JsonUtils;
import tools.jackson.core.type.TypeReference;
import lombok.Builder;
import lombok.Data;

import java.io.InputStream;

/**
 * Http响应体
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-17 08:47:49
 */
@Data
@Builder
public class HttpResponse {

    private int code;

    private String message;

    private HttpHeader headers;

    private boolean redirect;

    private boolean successful;

    private HttpProtocol protocol;

    private byte[] body;

    private String contentType;

    private Long contentLength;

    private InputStream byteStream;

    /**
     * 获取消息体
     *
     * @param typeReference 类型引用
     * @return 消息体
     * @param <T> 泛型
     */
    public <T> T getBody(TypeReference<T> typeReference) {
        if (this.body == null || this.body.length == 0) {
            return null;
        }
        return JsonUtils.getInstance().deserializePayload(this.body, typeReference);
    }

    /**
     * 获取消息体
     *
     * @param clazz 消息体类型
     * @return 消息体
     * @param <T> 泛型
     */
    public <T> T getBody(Class<T> clazz) {
        if (this.body == null || this.body.length == 0) {
            return null;
        }
        return JsonUtils.getInstance().deserializePayload(this.body, clazz);
    }
}
