/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.redis.streammq.monitor;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * 指标错误分类记录器
 *
 * <p>用于将异常按类别（超时/连接/序列化）归类并计入 RedisStreamMetrics，对外提供静态记录方法。
 *
 * <p>主要功能：
 * <ul>
 *   <li>识别常见的超时类异常并计数</li>
 *   <li>识别连接错误（Jedis/Lettuce/网络）并计数</li>
 *   <li>识别序列化/反序列化相关错误并计数</li>
 *   <li>避免对具体客户端形成强依赖，通过类名与消息内容进行兼容判断</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-09-16
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MetricsErrorRecorder {

    /**
     * 记录超时错误
     *
     * @param metrics 指标对象
     * @param streamKey 流名称
     * @param t 异常对象
     */
    public static void recordTimeoutIfAny(RedisStreamMetrics metrics, String streamKey, Throwable t) {
        if (isTimeout(t)) {
            metrics.recordTimeoutError(streamKey);
        }
    }

    /**
     * 记录连接错误
     *
     * @param metrics 指标对象
     * @param streamKey 流名称
     * @param t 异常对象
     */
    public static void recordConnectionIfAny(RedisStreamMetrics metrics, String streamKey, Throwable t) {
        if (isConnection(t)) {
            metrics.recordConnectionError(streamKey);
        }
    }

    /**
     * 识别序列化错误并计数
     *
     * @param metrics 指标对象
     * @param streamKey 流名称
     * @param t 异常对象
     */
    public static void recordSerializationIfAny(RedisStreamMetrics metrics, String streamKey, Throwable t) {
        if (isSerialization(t)) {
            metrics.recordSerializationError(streamKey);
        }
    }

    private static boolean isTimeout(Throwable t) {
        Throwable root = rootCause(t);
        if (root instanceof QueryTimeoutException) return true;
        // Lettuce
        if (classNameEquals(root, "io.lettuce.core.RedisCommandTimeoutException")) return true;
        // Jedis 常见超时：一般为 JedisConnectionException 的具体消息，但不稳定；尽量兼容
        if (classNameEquals(root, "redis.clients.jedis.exceptions.JedisConnectionException") && containsTimeout(root)) return true;
        // Netty 低层超时
        if (classNameEquals(root, "java.net.SocketTimeoutException")) return true;
        return false;
    }

    private static boolean isConnection(Throwable t) {
        Throwable root = rootCause(t);
        if (root instanceof RedisConnectionFailureException) return true;
        if (root instanceof DataAccessResourceFailureException) return true;
        if (classNameEquals(root, "redis.clients.jedis.exceptions.JedisConnectionException")) return true;
        if (classNameEquals(root, "io.lettuce.core.RedisConnectionException")) return true;
        if (classNameEquals(root, "java.net.ConnectException")) return true;
        if (classNameEquals(root, "java.net.SocketException")) return true;
        return false;
    }

    private static boolean isSerialization(Throwable t) {
        Throwable root = rootCause(t);
        if (root instanceof SerializationException) return true;
        // 常见序列化/反序列化异常
        if (classNameEquals(root, "com.fasterxml.jackson.core.JsonProcessingException")) return true;
        if (classNameEquals(root, "com.fasterxml.jackson.databind.JsonMappingException")) return true;
        if (classNameEquals(root, "java.io.InvalidClassException")) return true;
        if (classNameEquals(root, "java.io.StreamCorruptedException")) return true;
        if (classNameEquals(root, "java.lang.ClassCastException")) return true;
        return false;
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }

    private static boolean classNameEquals(Throwable t, String fqcn) {
        return t != null && t.getClass().getName().equals(fqcn);
    }

    private static boolean containsTimeout(Throwable t) {
        String msg = t.getMessage();
        if (msg == null) return false;
        String m = msg.toLowerCase();
        return m.contains("timeout") || m.contains("timed out") || m.contains("read timed out") || m.contains("operation timed out");
    }
}


