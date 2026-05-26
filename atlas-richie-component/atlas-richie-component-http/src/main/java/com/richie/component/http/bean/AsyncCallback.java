package com.richie.component.http.bean;


import jakarta.annotation.Nullable;

import java.io.IOException;

/**
 * 异步请求回调接口
 *
 * @param <T> 响应数据类型
 * @author richie696
 * @version 1.0
 * @since 2025-01-17 09:04:23
 */
public interface AsyncCallback<T> {
    /**
     * 当请求由于取消、连接问题或超时而无法执行时调用。由于网络在交换期间可能
     * 会失败，因此远程服务器可能在故障之前接受了请求。
     *
     * @param exception 失败原因
     */
    void onFailure(IOException exception);

    /**
     * 当远程服务器成功返回 HTTP 响应时调用。回调可以继续使用 [Response.body]
     * 读取响应正文。响应仍处于活动状态，直到其响应正文 [关闭][响应正文]。回调
     * 的接收方可能会使用另一个线程上的响应正文。请注意，传输层成功（接收 HTTP
     * 响应代码、标头和正文）并不一定表示应用层成功：“响应”可能仍表示不满意的
     * HTTP 响应代码，如 404 或 500。
     *
     * @param response HTTP响应数据
     * @param data     从响应中解析的数据，如果解析失败则为null
     */
    void onResponse(HttpResponse response, @Nullable T data);

}
