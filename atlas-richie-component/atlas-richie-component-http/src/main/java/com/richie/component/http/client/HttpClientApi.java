package com.richie.component.http.client;

import com.richie.component.http.bean.AsyncCallback;
import com.richie.component.http.bean.HttpMethod;
import com.richie.component.http.bean.HttpResponse;
import tools.jackson.core.type.TypeReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;


/**
 * HTTP客户端接口
 *
 * @author richie696
 * @version 1.0
 * @since 2023-08-23 22:21:48
 */
public interface HttpClientApi {

    /**
     * 执行请求的方法
     *
     * @param method       请求方法
     * @param url          请求URL地址
     * @param params       请求参数集合
     * @param headers      请求头字段集合
     * @param returnErrors 请求出现错误信息（入参请给一个空的 list，为空表示请求正常，没有任何问题）
     * @return 返回结果报文字符串
     */
    HttpResponse doRequest(HttpMethod method, String url, Map<String, String> params, Map<String, String> headers, List<Throwable> returnErrors);

    /**
     * 无参数GET请求方法
     *
     * @param url 请求URL地址
     * @return 返回结果报文字符串
     */
    @Nullable
    String doGet(String url);

    /**
     * 异步无参数GET请求方法
     *
     * @param url           请求URL地址
     * @param asyncCallback 异步回调接口
     */
    void doAsyncGet(String url, AsyncCallback<String> asyncCallback);

    /**
     * 无参数GET请求方法
     *
     * @param cls 报文类型
     * @param url 请求URL地址
     * @param <T> 报文的具体类型
     * @return 返回结果报文对象
     */
    @Nullable
    <T> T doGet(Class<T> cls, String url);

    /**
     * 无参数GET请求方法
     *
     * @param cls          报文类型
     * @param url          请求URL地址
     * @param returnErrors 请求出现错误信息（入参请给一个空的 list）
     * @param <T>          报文的具体类型
     * @return 返回结果报文对象
     */
    @Nullable
    <T> T doGet(Class<T> cls, String url, List<Throwable> returnErrors);

    /**
     * 异步无参数GET请求方法
     *
     * @param cls           报文类型
     * @param url           请求URL地址
     * @param asyncCallback 异步回调接口
     * @param <T>           报文的具体类型
     */
    <T> void doAsyncGet(Class<T> cls, String url, AsyncCallback<T> asyncCallback);

    /**
     * 带参数GET请求方法
     *
     * @param url    请求URL地址
     * @param params 请求参数集合
     * @return 返回结果报文字符串
     */
    @Nullable
    String doGet(String url, Map<String, String> params);

    /**
     * 带参数GET请求方法
     *
     * @param cls    报文类型
     * @param url    请求URL地址
     * @param params 请求参数集合
     * @param <T>    报文的具体类型
     * @return 返回结果报文对象
     */
    @Nullable
    <T> T doGet(Class<T> cls, String url, Map<String, String> params);

    /**
     * 异步带参数GET请求方法
     *
     * @param cls           报文类型
     * @param url           请求URL地址
     * @param params        请求参数集合
     * @param asyncCallback 异步回调
     * @param <T>           报文的具体类型
     */
    <T> void doAsyncGet(Class<T> cls, String url, Map<String, String> params, AsyncCallback<T> asyncCallback);

    /**
     * 带头信息和请求参数的GET请求方法
     *
     * @param url       请求URL地址
     * @param params    请求参数集合
     * @param headerMap 请求头字段集合
     * @return 返回结果报文字符串
     */
    @Nullable
    String doGet(String url, Map<String, String> params, Map<String, String> headerMap);

    /**
     * 带头信息和请求参数的GET请求方法
     *
     * @param url       请求URL地址
     * @param params    请求参数集合
     * @param headerMap 请求头字段集合
     * @param returnErrors 请求出现错误信息（入参请给一个空的 list）
     * @return 返回结果报文字符串
     */
    @Nullable
    String doGet(String url, Map<String, String> params, Map<String, String> headerMap, List<Throwable> returnErrors);

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
    @Nullable
    <T> T doGet(Class<T> cls, String url, Map<String, String> params, Map<String, String> headerMap);

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
    <T> void doAsyncGet(Class<T> cls, String url, Map<String, String> params, Map<String, String> headerMap, AsyncCallback<T> asyncCallback);

    /**
     * 执行删除操作的方法
     *
     * @param url       请求URL地址
     * @param json      执行删除的报文参数JSON体
     * @param headerMap 请求头字段集合
     * @return 返回结果报文字符串
     */
    @Nullable
    String doDelete(String url, String json, Map<String, String> headerMap);

    /**
     * 执行删除操作的方法
     *
     * @param url          请求URL地址
     * @param json         执行删除的报文参数JSON体
     * @param headerMap    请求头字段集合
     * @param returnErrors 请求出现错误信息（入参请给一个空的 list）
     * @return 返回结果报文字符串
     */
    @Nullable
    String doDelete(String url, String json, Map<String, String> headerMap, List<Throwable> returnErrors);

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
    @Nullable
    <T> T doDelete(Class<T> cls, String url, String json, Map<String, String> headerMap);

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
    <T> void doAsyncDelete(Class<T> cls, String url, String json, Map<String, String> headerMap, AsyncCallback<T> asyncCallback);

    /**
     * 执行 POST 请求的方法
     *
     * @param url    请求URL地址
     * @param params 请求参数集合
     * @return 返回结果报文字符串
     */
    @Nullable
    String doPost(String url, Map<String, String> params);

    /**
     * 执行 POST 请求的方法
     *
     * @param url    请求URL地址
     * @param params 请求参数集合
     * @param returnErrors 请求出现错误信息（入参请给一个空的 list）
     * @return 返回结果报文字符串
     */
    @Nullable
    String doPost(String url, Map<String, String> params, List<Throwable> returnErrors);

    /**
     * 执行 POST 请求的方法
     *
     * @param url          请求URL地址
     * @param params       请求参数集合
     * @param headerMap    请求头字段集合
     * @param returnErrors 请求出现错误信息（入参请给一个空的 list）
     * @return 返回结果报文字符串
     */
    @Nullable
    String doPost(String url, Map<String, String> params, Map<String, String> headerMap, List<Throwable> returnErrors);

    /**
     * 执行 POST 请求的方法
     *
     * @param url           请求URL地址
     * @param params        请求参数集合
     * @param typeReference 报文类型
     * @param <T>           报文的具体类型
     * @return 返回结果报文字符串
     */
    @Nullable
    <T> T doPost(String url, Object params, TypeReference<T> typeReference);
    /**
     * 执行 POST 请求的方法
     *
     * @param url           请求URL地址
     * @param params        请求参数集合
     * @param typeReference 报文类型
     * @param <T>           报文的具体类型
     * @return 返回结果报文字符串
     */
    @Nullable
    <T> T doPost(String url, Object params, Map<String, String> headerMap, Long timeout, List<Throwable> returnErrors, TypeReference<T> typeReference);

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
    @Nullable
    <T> T doPost(String url, Object params, Map<String, String> headerMap, TypeReference<T> typeReference);

    /**
     * 执行 POST 请求的方法
     *
     * @param url           请求URL地址
     * @param params        请求参数集合
     * @param headerMap     请求头字段集合
     * @param timeout       请求超时时间（单位：毫秒）
     * @param typeReference 报文类型
     * @param <T>           报文的具体类型
     * @return 返回结果报文字符串
     */
    @Nullable
    <T> T doPost(String url, Object params, Map<String, String> headerMap, Long timeout, TypeReference<T> typeReference);

    /**
     * 执行异步 POST 请求的方法
     *
     * @param cls           报文类型
     * @param url           请求URL地址
     * @param params        请求参数集合
     * @param asyncCallback 异步回调
     * @param <T>           报文的具体类型
     */
    <T> void doAsyncPost(Class<T> cls, String url, Object params, AsyncCallback<T> asyncCallback);

    /**
     * 执行异步 POST 请求的方法
     *
     * @param cls           报文类型
     * @param url           请求URL地址
     * @param params        请求参数集合
     * @param timeout       请求超时时间（单位：毫秒）
     * @param asyncCallback 异步回调
     * @param <T>           报文的具体类型
     */
    <T> void doAsyncPost(Class<T> cls, String url, Object params, Long timeout, AsyncCallback<T> asyncCallback);

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
    <T> void doAsyncPost(Class<T> cls, String url, Object params, Map<String, String> headerMap, AsyncCallback<T> asyncCallback);

    /**
     * 执行异步 POST 请求的方法
     *
     * @param cls           报文类型
     * @param url           请求URL地址
     * @param params        请求参数集合
     * @param headerMap     请求头字段集合
     * @param timeout       请求超时时间（单位：毫秒）
     * @param asyncCallback 异步回调
     * @param <T>           报文的具体类型
     */
    <T> void doAsyncPost(Class<T> cls, String url, Object params, Map<String, String> headerMap, Long timeout, AsyncCallback<T> asyncCallback);

    /**
     * 执行 POST 请求的方法
     *
     * @param cls           回调报文类型
     * @param url           请求URL
     * @param params        请求参数对象
     * @param headerMap     请求头
     * @param contentType   内容类型
     * @param timeout       超时时长（单位：毫秒）
     * @param asyncCallback 异步回调函数
     * @param <T>           报文的具体类型
     */
    <T> void doAsyncPost(Class<T> cls, String url, Object params, Map<String, String> headerMap, String contentType, Long timeout, AsyncCallback<T> asyncCallback);

    /**
     * 执行 POST 请求的方法（请求参数为参数对象）
     *
     * @param url      请求URL地址
     * @param argument 请求参数对象
     * @return 返回结果报文字符串
     */
    @Nullable
    String doPost(String url, Object argument);

    /**
     * 执行 POST 请求的方法（请求参数为参数对象）
     *
     * @param url          请求URL地址
     * @param argument     请求参数对象
     * @param returnErrors 请求出现错误信息（入参请给一个空的 list）
     * @return 返回结果报文字符串
     */
    @Nullable
    String doPost(String url, Object argument, List<Throwable> returnErrors);

    /**
     * 执行 POST 请求的方法（请求参数为参数对象）
     *
     * @param url      请求URL地址
     * @param argument 请求参数对象
     * @param timeout  请求超时时间（单位：毫秒）
     * @return 返回结果报文字符串
     */
    @Nullable
    String doPost(String url, Object argument, Long timeout);

    /**
     * 执行 POST 请求的方法（请求参数为参数对象）
     *
     * @param url          请求URL地址
     * @param argument     请求参数对象
     * @param returnErrors 请求出现错误信息（入参请给一个空的 list）
     * @param timeout  请求超时时间（单位：毫秒）
     * @return 返回结果报文字符串
     */
    @Nullable
    String doPost(String url, Object argument, List<Throwable> returnErrors, Long timeout);

    /**
     * 执行 POST 请求的方法（请求参数为参数对象）
     *
     * @param url       请求URL地址
     * @param argument  请求参数对象
     * @param headerMap 请求头字段集合
     * @return 返回请求结果
     */
    @Nullable
    String doPost(String url, Object argument, Map<String, String> headerMap);

    /**
     * 执行 POST 请求的方法（请求参数为参数对象）
     *
     * @param url          请求URL地址
     * @param argument     请求参数对象
     * @param headerMap    请求头字段集合
     * @param returnErrors 请求出现错误信息（入参请给一个空的 list）
     * @return 返回请求结果
     */
    @Nullable
    String doPost(String url, Object argument, Map<String, String> headerMap, List<Throwable> returnErrors);

    /**
     * 执行 POST 请求的方法（请求参数为参数对象）
     *
     * @param cls      报文类型
     * @param url      请求URL地址
     * @param argument 请求参数对象
     * @param <T>      报文的具体类型
     * @return 返回结果报文字符串
     */
    @Nullable
    <T> T doPost(Class<T> cls, String url, Object argument);

    /**
     * 执行 POST 请求的方法（请求参数为参数对象）
     *
     * @param cls          报文类型
     * @param url          请求URL地址
     * @param argument     请求参数对象
     * @param returnErrors 请求出现错误信息（入参请给一个空的 list）
     * @param <T>          报文的具体类型
     * @return 返回结果报文字符串
     */
    @Nullable
    <T> T doPost(Class<T> cls, String url, Object argument, List<Throwable> returnErrors);

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
    @Nullable
    <T> T doPost(Class<T> cls, String url, Object argument, String contentType);

    /**
     * 执行 POST 请求的方法（请求参数为参数对象）
     *
     * @param cls          报文类型
     * @param url          请求URL地址
     * @param argument     请求参数对象
     * @param contentType  请求内容类型
     * @param returnErrors 请求出现错误信息（入参请给一个空的 list）
     * @param <T>          报文的具体类型
     * @return 返回结果报文字符串
     */
    @Nullable
    <T> T doPost(Class<T> cls, String url, Object argument, String contentType, List<Throwable> returnErrors);

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
    @Nullable
    <T> T doPost(Class<T> cls, String url, Object argument, Map<String, String> headerMap);

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
    @Nullable
    <T> T doPost(Class<T> cls, String url, Object argument, Map<String, String> headerMap, String contentType);

    /**
     * 执行 POST 请求的方法（请求参数为参数对象）
     *
     * @param cls          报文类型
     * @param url          请求URL地址
     * @param argument     请求参数对象
     * @param headerMap    请求头字段集合
     * @param returnErrors 请求出现错误信息（入参请给一个空的 list）
     * @param <T>          报文的具体类型
     * @return 返回请求结果
     */
    @Nullable
    <T> T doPost(Class<T> cls, String url, Object argument, Map<String, String> headerMap, List<Throwable> returnErrors);

    /**
     * 执行 POST 请求的方法（允许自定义请求内容类型）
     *
     * @param url         请求URL地址
     * @param argument    请求参数对象
     * @param headerMap   请求头
     * @param contentType 请求头MIME
     * @return 返回请求结果
     */
    @Nullable
    String doPost(String url, Object argument, Map<String, String> headerMap, String contentType);

    /**
     * 执行 POST 请求的方法（请求参数为JSON字符串）
     *
     * @param url  请求URL地址
     * @param json 请求参数对象 JSON 字符串
     * @return 返回请求结果
     */
    @Nullable
    String doPost(String url, String json);

    /**
     * 执行 POST 请求的方法（允许自定义请求内容类型）
     *
     * @param url          请求URL地址
     * @param argument     请求参数对象
     * @param headerMap    请求头
     * @param contentType  请求头MIME
     * @param returnErrors 请求出现错误信息（入参请给一个空的 list）
     * @return 返回请求结果
     */
    @Nullable
    String doPost(String url, Object argument, Map<String, String> headerMap, String contentType, List<Throwable> returnErrors);

    /**
     * 执行 SOAP1.2 协议请求的方法（请求参数为 XML 格式）
     *
     * @param url 请求URL地址
     * @param xml 请求数据 xml 字符串
     * @return 返回请求结果
     */
    @Nullable
    String doPostSOAP1_2(String url, String xml);

    /**
     * 执行 SOAP1.2 协议请求的方法（请求参数为 XML 格式）
     *
     * @param url          请求URL地址
     * @param xml          请求数据 xml 字符串
     * @param returnErrors 请求出现错误信息（入参请给一个空的 list）
     * @return 返回请求结果
     */
    @Nullable
    String doPostSOAP1_2(String url, String xml, List<Throwable> returnErrors);

    /**
     * 执行 SOAP1.2 协议请求的方法（请求参数为 XML 格式）
     *
     * @param cls 应答报文类型
     * @param url 请求URL地址
     * @param xml 请求数据 xml 字符串
     * @param <T> 报文的具体类型
     * @return 返回请求结果
     */
    @Nullable
    <T> T doPostSOAP1_2(Class<T> cls, String url, String xml);

    /**
     * 执行 SOAP1.2 协议请求的方法（请求参数为 XML 格式）
     *
     * @param cls          应答报文类型
     * @param url          请求URL地址
     * @param xml          请求数据 xml 字符串
     * @param returnErrors 请求出现错误信息（入参请给一个空的 list）
     * @param <T>          报文的具体类型
     * @return 返回请求结果
     */
    <T> T doPostSOAP1_2(Class<T> cls, String url, String xml, List<Throwable> returnErrors);

    /**
     * 执行 SOAP1.2 协议异步请求的方法（请求参数为 XML 格式）
     *
     * @param cls           应答报文类型
     * @param url           请求URL地址
     * @param xml           请求数据 xml 字符串
     * @param asyncCallback 异步回调
     * @param <T>           应答报文的具体类型
     */
    <T> void doAsyncPostSOAP1_2(Class<T> cls, String url, String xml, AsyncCallback<T> asyncCallback);

    /**
     * 执行 SOAP1.2 协议异步请求的方法（请求参数为 XML 格式）
     *
     * @param cls           应答报文类型
     * @param url           请求URL地址
     * @param xml           请求数据 xml 字符串
     * @param headerMap     请求头字段集合
     * @param asyncCallback 异步回调
     * @param <T>           应答报文的具体类型
     */
    <T> void doAsyncPostSOAP1_2(Class<T> cls, String url, String xml, Map<String, String> headerMap, AsyncCallback<T> asyncCallback);

    /**
     * 执行 POST 请求的方法（请求参数为 XML 格式）
     *
     * @param url 请求URL地址
     * @param xml 请求数据 xml 字符串
     * @return 返回请求结果
     */
    @Nullable
    String doPostXml(String url, String xml);

    /**
     * 执行 POST 请求的方法（请求参数为 XML 格式）
     *
     * @param url        请求URL地址
     * @param xml        请求数据 xml 字符串
     * @param exceptions 请求出现错误信息（入参请给一个空的 list）
     * @return 返回请求结果
     */
    @Nullable
    String doPostXml(String url, String xml, List<Throwable> exceptions);

    /**
     * 执行 POST 请求的方法（请求参数为 XML 格式）
     *
     * @param cls 报文类型
     * @param url 请求URL地址
     * @param xml 请求数据 xml 字符串
     * @param <T> 报文的具体类型
     * @return 返回请求结果
     */
    @Nullable
    <T> T doPostXml(Class<T> cls, String url, String xml);

    /**
     * 执行 POST 请求的方法（请求参数为 XML 格式）
     *
     * @param cls        报文类型
     * @param url        请求URL地址
     * @param xml        请求数据 xml 字符串
     * @param <T>        报文的具体类型
     * @param exceptions 请求出现错误信息（入参请给一个空的 list）
     * @return 返回请求结果
     */
    @Nullable
    <T> T doPostXml(Class<T> cls, String url, String xml, List<Throwable> exceptions);

    /**
     * 执行 POST 请求的方法（请求参数为 XML 格式）
     *
     * @param cls           报文类型
     * @param url           请求URL地址
     * @param xml           请求数据 xml 字符串
     * @param asyncCallback 异步回调
     * @param <T>           报文的具体类型
     */
    <T> void doAsyncPostXml(Class<T> cls, String url, String xml, AsyncCallback<T> asyncCallback);

    /**
     * 执行 POST 请求的方法（请求参数为 XML 格式）
     *
     * @param cls           报文类型
     * @param url           请求URL地址
     * @param xml           请求数据 xml 字符串
     * @param headerMap     请求头
     * @param asyncCallback 异步回调
     * @param <T>           报文的具体类型
     */
    <T> void doAsyncPostXml(Class<T> cls, String url, String xml, Map<String, String> headerMap, AsyncCallback<T> asyncCallback);

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
    <T> T doUploadInputStream(@Nonnull String url, @Nonnull UploadFileMetadata metadata,
                              Map<String, String> params, Map<String, String> headerMap, TypeReference<T> typeReference);

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
    String doUploadInputStream(@Nonnull String url, @Nonnull UploadFileMetadata metadata,
                               Map<String, String> params, Map<String, String> headerMap);

    /**
     * 批量上传文件
     *
     * @param url          文件URL
     * @param metadataList 文件元数据信息
     * @return 返回上传结果
     */
    @Nullable
    String doUploadInputStream(@Nonnull String url, @Nonnull List<UploadFileMetadata> metadataList);

    /**
     * 批量上传文件
     *
     * @param url          文件URL
     * @param metadataList 文件元数据信息
     * @param params       请求参数
     * @return 返回上传结果
     */
    @Nullable
    String doUploadInputStream(@Nonnull String url, @Nonnull List<UploadFileMetadata> metadataList,
                               Map<String, String> params);

    /**
     * 批量上传文件
     *
     * @param url          文件URL
     * @param metadataList 文件元数据信息
     * @param params       请求参数
     * @param headerMap    请求头参数
     * @return 返回上传结果
     */
    @Nullable
    String doUploadInputStream(@Nonnull String url, @Nonnull List<UploadFileMetadata> metadataList,
                               Map<String, String> params, Map<String, String> headerMap);

    /**
     * 上传文件
     *
     * @param url       文件URL
     * @param fileMap   文件集合
     * @param params    请求参数
     * @param headerMap 请求头参数
     * @return 返回上传结果
     */
    String doUploadFile(String url, Map<String, File> fileMap,
                        Map<String, String> params, Map<String, String> headerMap);

    /**
     * 上传文件（返回错误信息）
     *
     * @param url          文件URL
     * @param fileMap      文件集合
     * @param params       请求参数
     * @param headerMap    请求头参数
     * @param returnErrors 请求出现错误信息（入参请给一个空的 list）
     * @return 返回上传结果
     */
    String doUploadFile(String url, Map<String, File> fileMap,
                        Map<String, String> params, Map<String, String> headerMap, List<Throwable> returnErrors);

    /**
     * 异步上传文件
     *
     * @param url           文件URL
     * @param fileMap       文件集合
     * @param params        请求参数
     * @param headerMap     请求头参数
     * @param asyncCallback 异步回调
     */
    void doAsyncUploadFile(String url, Map<String, File> fileMap,
                           Map<String, String> params, Map<String, String> headerMap, AsyncCallback<String> asyncCallback);

    /**
     * 异步上传文件
     *
     * @param url           文件URL
     * @param fileMap       文件集合
     * @param params        请求参数
     * @param headerMap     请求头参数
     * @param timeout       请求超时时间（单位：毫秒）
     * @param asyncCallback 异步回调
     */
    void doAsyncUploadFile(String url, Map<String, File> fileMap,
                           Map<String, String> params, Map<String, String> headerMap, Long timeout, AsyncCallback<String> asyncCallback);

    /**
     * 下载文件
     *
     * @param url          文件URL
     * @param destFilePath 存储文件路径
     * @return 返回文件
     * @throws Exception 异常
     */
    @Nullable
    File doDownloadFile(String url, String destFilePath) throws Exception;

    /**
     * 下载文件
     *
     * @param url 文件URL
     * @return 返回文件
     * @throws IOException 异常
     */
    @Nullable
    InputStream doDownloadFile(@Nonnull String url) throws IOException;

    /**
     * 异步下载文件
     *
     * @param url           文件URL
     * @param asyncCallback 异步回调
     * @throws IOException 异常
     */
    void doAsyncDownloadFile(@Nonnull String url, AsyncCallback<InputStream> asyncCallback) throws IOException;

    /**
     * 异步下载文件
     *
     * @param url           文件URL
     * @param headers       请求头
     * @param asyncCallback 异步回调
     * @throws IOException 异常
     */
    void doAsyncDownloadFile(@Nonnull String url, Map<String, String> headers, AsyncCallback<InputStream> asyncCallback) throws IOException;

    /**
     * 下载文件并返回文件流的方法
     *
     * @param url    文件URL
     * @param params 请求参数
     * @return 返回文件流（如果下载失败则返回null）
     * @throws IOException 异常
     */
    @Nullable
    InputStream doDownloadFileByPost(String url, Object params) throws IOException;

    /**
     * 下载文件并返回文件流的方法
     *
     * @param url       文件URL
     * @param params    请求头
     * @param headerMap 请求头
     * @return 返回文件流（如果下载失败则返回null）
     * @throws IOException 异常
     */
    @Nullable
    InputStream doDownloadFileByPost(String url, Object params, Map<String, String> headerMap) throws IOException;

    /**
     * 异步下载文件
     *
     * @param url           文件URL
     * @param params        请求参数
     * @param asyncCallback 异步回调
     * @throws IOException 异常
     */
    void doAsyncDownloadFileByPost(String url, Object params, AsyncCallback<InputStream> asyncCallback) throws IOException;

    /**
     * 异步下载文件
     *
     * @param url           文件URL
     * @param params        请求参数
     * @param headers       请求头
     * @param asyncCallback 异步回调
     * @throws IOException 异常
     */
    void doAsyncDownloadFileByPost(String url, Object params, Map<String, String> headers, AsyncCallback<InputStream> asyncCallback) throws IOException;

    /**
     * 上传文件的元数据信息
     *
     * @param paramName   参数名
     * @param fileName    文件名
     * @param inputStream 文件流
     */
    record UploadFileMetadata(
            String paramName,
            String fileName,
            InputStream inputStream
    ) {
    }

    /**
     * 以 form-body 形式执行 POST 请求
     *
     * @param cls    响应体类型
     * @param url    请求 URL
     * @param params 表单参数（key-value）
     * @param <T>    响应体泛型类型
     * @return 解析后的响应对象
     */
    <T> T doPostFormBody(Class<T> cls, String url, Map<String, String> params);

}
