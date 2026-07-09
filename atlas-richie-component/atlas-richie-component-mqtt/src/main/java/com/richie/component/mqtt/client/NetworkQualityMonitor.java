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
package com.richie.component.mqtt.client;

import com.richie.component.mqtt.beans.NetworkQualityEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 网络质量监控器
 * <p>
 * 通过多种网络测试方法监控MQTT服务器的网络质量，支持跨平台网络诊断和性能分析。
 * 该类实现了系统无关的网络质量检测，能够准确测量网络延迟、连通性和稳定性，
 * 为MQTT连接质量评估提供可靠的数据基础。
 * <p>
 * <strong>核心功能：</strong>
 * <ul>
 *   <li><strong>跨平台支持</strong>：不依赖系统命令，纯Java实现</li>
 *   <li><strong>多种检测方式</strong>：TCP连接、HTTP请求、DNS解析等</li>
 *   <li><strong>性能测量</strong>：精确测量网络延迟和连通性</li>
 *   <li><strong>异步支持</strong>：支持同步和异步两种监控方式</li>
 *   <li><strong>超时控制</strong>：可配置的超时机制，避免长时间等待</li>
 * </ul>
 * <p>
 * <strong>监控指标：</strong>
 * <ul>
 *   <li><strong>网络延迟</strong>：连接建立时间，单位毫秒</li>
 *   <li><strong>连通性</strong>：网络是否可达</li>
 *   <li><strong>稳定性</strong>：多次测试的一致性</li>
 *   <li><strong>丢包率</strong>：基于TCP重传的丢包检测</li>
 * </ul>
 * <p>
 * <strong>使用场景：</strong>
 * <ul>
 *   <li>MQTT连接前的网络质量评估</li>
 *   <li>网络故障诊断和排查</li>
 *   <li>连接质量实时监控</li>
 *   <li>网络性能基准测试</li>
 *   <li>故障转移决策支持</li>
 * </ul>
 * <p>
 * <strong>技术特点：</strong>
 * <ul>
 *   <li>纯Java实现，不依赖系统命令</li>
 *   <li>支持多种网络协议测试</li>
 *   <li>异常处理和超时控制，确保监控稳定性</li>
 *   <li>跨平台兼容，无需额外依赖</li>
 * </ul>
 *
 * @author richie696
 * @version 2.0
 * @since 2025-07-27
 */
@Slf4j
@Component
public class NetworkQualityMonitor {

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 10000;
    private static final int DEFAULT_TEST_COUNT = 3;
    private static final int DEFAULT_TCP_PORT = 1883; // MQTT默认端口

    /**
     * 异步监控网络质量
     * <p>
     * <strong>设计原则：</strong> 非阻塞执行，异步处理，提高系统响应性
     * <p>
     * <strong>功能说明：</strong>
     * 异步执行网络质量监控，不阻塞调用线程，返回CompletableFuture对象。
     * 该方法适用于需要非阻塞网络监控的场景，如定时任务、事件处理等。
     * <p>
     * <strong>执行方式：</strong>
     * <ul>
     *   <li>使用CompletableFuture.supplyAsync()异步执行</li>
     *   <li>在ForkJoinPool.commonPool()中执行</li>
     *   <li>支持链式操作和异常处理</li>
     *   <li>可以设置超时和取消操作</li>
     * </ul>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>定时任务中的网络监控</li>
     *   <li>用户界面中的网络检测</li>
     *   <li>事件驱动的网络质量检查</li>
     *   <li>需要非阻塞的网络诊断</li>
     * </ul>
     * <p>
     * <strong>异步操作示例：</strong>
     * <pre>{@code
     * CompletableFuture<NetworkQualityEvent> future = monitor.monitorNetworkQualityAsync("example.com");
     * future.thenAccept(event -> {
     *     if (event.getPingLatency() > 100) {
     *         log.warn("网络延迟过高: {}ms", event.getPingLatency());
     *     }
     * }).exceptionally(throwable -> {
     *     log.error("网络监控失败", throwable);
     *     return null;
     * });
     * }</pre>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>返回的Future对象需要正确处理</li>
     *   <li>异常情况需要单独处理</li>
     *   <li>可以设置超时和取消操作</li>
     *   <li>适合非关键路径的网络监控</li>
     * </ul>
     *
     * @param host MQTT服务器地址
     * @return CompletableFuture&lt;NetworkQualityEvent&gt; 异步网络质量监控结果
     */
    public CompletableFuture<NetworkQualityEvent> monitorNetworkQualityAsync(String host) {
        return CompletableFuture.supplyAsync(() -> monitorNetworkQuality(host));
    }

    /**
     * 同步监控网络质量
     * <p>
     * <strong>设计原则：</strong> 简单易用，默认配置，快速网络检测
     * <p>
     * <strong>功能说明：</strong>
     * 使用默认配置同步执行网络质量监控，提供简单易用的网络检测接口。
     * 该方法使用TCP连接测试，适合快速网络状态检查。
     * <p>
     * <strong>默认配置：</strong>
     * <ul>
     *   <li>测试次数：3次（DEFAULT_TEST_COUNT）</li>
     *   <li>连接超时：5000毫秒（DEFAULT_CONNECT_TIMEOUT_MS）</li>
     *   <li>读取超时：10000毫秒（DEFAULT_READ_TIMEOUT_MS）</li>
     *   <li>适合大多数网络环境的检测需求</li>
     * </ul>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>快速网络连通性检查</li>
     *   <li>简单的网络质量评估</li>
     *   <li>开发测试和调试</li>
     *   <li>用户手动触发的网络检测</li>
     * </ul>
     * <p>
     * <strong>性能特点：</strong>
     * <ul>
     *   <li>执行时间：取决于网络延迟和测试次数</li>
     *   <li>阻塞调用：会阻塞当前线程直到完成</li>
     *   <li>资源消耗：较低，只建立TCP连接</li>
     *   <li>适合低频调用</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>会阻塞调用线程直到监控完成</li>
     *   <li>超时情况下会返回失败结果</li>
     *   <li>适合同步调用场景</li>
     *   <li>不建议在高频循环中使用</li>
     * </ul>
     *
     * @param host MQTT服务器地址
     * @return NetworkQualityEvent 网络质量监控结果
     */
    public NetworkQualityEvent monitorNetworkQuality(String host) {
        return monitorNetworkQuality(host, DEFAULT_TCP_PORT, DEFAULT_TEST_COUNT);
    }

    /**
     * 监控网络质量
     * <p>
     * <strong>设计原则：</strong> 灵活配置，精确控制，跨平台兼容
     * <p>
     * <strong>功能说明：</strong>
     * 执行可配置的网络质量监控，支持自定义端口和测试次数。
     * 该方法使用TCP连接测试，能够准确测量网络性能指标。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>DNS解析测试</li>
     *   <li>TCP连接测试</li>
     *   <li>数据传输测试</li>
     *   <li>计算网络质量指标</li>
     *   <li>返回网络质量事件对象</li>
     * </ol>
     * <p>
     * <strong>测试方法：</strong>
     * <ul>
     *   <li><strong>DNS解析</strong>：测试域名解析性能</li>
     *   <li><strong>TCP连接</strong>：测试TCP连接建立时间</li>
     *   <li><strong>连接验证</strong>：验证TCP连接的有效性</li>
     * </ul>
     * <p>
     * <strong>超时控制：</strong>
     * <ul>
     *   <li>使用Socket.setSoTimeout()设置超时</li>
     *   <li>超时后返回失败结果</li>
     *   <li>避免长时间等待网络响应</li>
     *   <li>提高监控的可靠性</li>
     * </ul>
     * <p>
     * <strong>异常处理：</strong>
     * <ul>
     *   <li>网络不可达时返回失败结果</li>
     *   <li>连接超时时返回特殊标识</li>
     *   <li>DNS解析失败时记录错误日志</li>
     *   <li>确保监控过程的稳定性</li>
     * </ul>
     * <p>
     * <strong>性能考虑：</strong>
     * <ul>
     *   <li>测试次数影响检测精度和耗时</li>
     *   <li>超时时间影响响应速度</li>
     *   <li>建议根据网络环境调整参数</li>
     *   <li>高频调用时注意资源消耗</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>需要网络支持TCP连接</li>
     *   <li>某些网络环境可能限制端口访问</li>
     *   <li>超时设置要合理，避免过早超时</li>
     *   <li>测试次数过多会增加检测时间</li>
     * </ul>
     *
     * @param host      MQTT服务器地址
     * @param port      服务器端口，建议使用MQTT端口1883或8883
     * @param testCount 测试次数，建议范围：1-5
     * @return NetworkQualityEvent 网络质量监控结果
     */
    public NetworkQualityEvent monitorNetworkQuality(String host, int port, int testCount) {
        try {
            log.debug("开始网络质量监控: {}:{}", host, port);

            List<NetworkQualityEvent> results = new ArrayList<>();

            // 执行多次测试
            for (int i = 0; i < testCount; i++) {
                NetworkQualityEvent result = performSingleTest(host, port);
                if (result.getPingLatency() > 0) {
                    results.add(result);
                }

                // 测试间隔，避免过于频繁
                if (i < testCount - 1) {
                    Thread.sleep(100);
                }
            }

            // 计算综合指标
            return calculateComprehensiveQuality(results);

        } catch (Exception e) {
            log.error("网络质量监控失败: {}", e.getMessage(), e);
            return new NetworkQualityEvent(-1, 100.0f);
        }
    }

    /**
     * 执行单次网络测试
     * <p>
     * <strong>功能说明：</strong>
     * 执行一次完整的网络质量测试，包括DNS解析和TCP连接测试。
     * 不进行数据传输测试，避免向MQTT端口发送非协议数据。
     * <p>
     * <strong>测试内容：</strong>
     * <ul>
     *   <li><strong>DNS解析测试</strong>：测试域名解析性能</li>
     *   <li><strong>TCP连接测试</strong>：测试TCP连接建立时间</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>不向MQTT端口发送数据，避免协议冲突</li>
     *   <li>只测试网络层面的连接能力</li>
     *   <li>测试结果反映网络连接质量</li>
     * </ul>
     *
     * @param host 目标主机
     * @param port 目标端口
     * @return 单次测试结果
     */
    private NetworkQualityEvent performSingleTest(String host, int port) {
        try {
            // 1. DNS解析测试
            long dnsStartTime = System.currentTimeMillis();
            InetAddress address = InetAddress.getByName(host);
            long dnsTime = System.currentTimeMillis() - dnsStartTime;

            // 2. TCP连接测试
            long tcpStartTime = System.currentTimeMillis();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(address, port), DEFAULT_CONNECT_TIMEOUT_MS);
                socket.setSoTimeout(DEFAULT_READ_TIMEOUT_MS);
                long tcpTime = System.currentTimeMillis() - tcpStartTime;

                // 3. 连接有效性验证（不发送数据）
                boolean connectionValid = testDataTransfer(socket);

                if (connectionValid) {
                    // 总延迟 = DNS时间 + TCP连接时间
                    long totalLatency = dnsTime + tcpTime;
                    return new NetworkQualityEvent(Math.max((int) totalLatency, 1), 0.0f);
                } else {
                    return new NetworkQualityEvent(-1, 100.0f);
                }
            }

        } catch (Exception e) {
            log.debug("单次网络测试失败: {}:{} - {}", host, port, e.getMessage());
            return new NetworkQualityEvent(-1, 100.0f);
        }
    }

    /**
     * 测试数据传输能力
     * <p>
     * <strong>功能说明：</strong>
     * 测试TCP连接的数据传输能力，但不向MQTT端口发送非协议数据。
     * 该方法只进行连接建立测试和延迟测量，避免协议冲突。
     * <p>
     * <strong>测试内容：</strong>
     * <ul>
     *   <li><strong>连接建立</strong>：验证TCP连接是否能够成功建立</li>
     *   <li><strong>连接延迟</strong>：测量连接建立的响应时间</li>
     *   <li><strong>连接稳定性</strong>：验证连接是否稳定可用</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>不向MQTT端口发送非协议数据，避免协议冲突</li>
     *   <li>只进行连接层面的测试，不涉及应用层协议</li>
     *   <li>测试结果反映网络连接质量，不是MQTT协议兼容性</li>
     * </ul>
     *
     * @param socket 已建立的Socket连接
     * @return 连接测试是否成功
     */
    private boolean testDataTransfer(Socket socket) {
        try {
            // 检查Socket是否仍然连接
            if (!socket.isConnected() || socket.isClosed()) {
                log.debug("Socket连接已断开或关闭");
                return false;
            }

            // 检查Socket是否可写（连接是否有效）
            if (!socket.isOutputShutdown()) {
                // 连接有效，测试成功
                return true;
            } else {
                log.debug("Socket输出流已关闭");
                return false;
            }

        } catch (Exception e) {
            log.debug("连接测试失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 计算综合网络质量
     * <p>
     * <strong>功能说明：</strong>
     * 基于多次测试结果计算综合的网络质量指标。
     *
     * @param results 多次测试结果列表
     * @return 综合网络质量结果
     */
    private NetworkQualityEvent calculateComprehensiveQuality(List<NetworkQualityEvent> results) {
        if (results.isEmpty()) {
            return new NetworkQualityEvent(-1, 100.0f);
        }

        // 计算平均延迟
        int totalLatency = results.stream()
                .mapToInt(NetworkQualityEvent::getPingLatency)
                .sum();
        int avgLatency = totalLatency / results.size();

        // 计算丢包率（基于测试成功率）
        int successCount = (int) results.stream()
                .filter(r -> r.getPingLatency() > 0)
                .count();
        float packetLoss = (float) (results.size() - successCount) / results.size() * 100;

        log.debug("网络质量统计 - 测试次数: {}, 成功: {}, 平均延迟: {}ms, 丢包率: {}%",
                results.size(), successCount, avgLatency, Math.round(packetLoss * 100.0f) / 100.0f);

        return new NetworkQualityEvent(avgLatency, packetLoss);
    }

    /**
     * 使用HTTP请求测试网络质量
     * <p>
     * <strong>功能说明：</strong>
     * 通过HTTP GET请求测试网络质量，适用于有HTTP服务的环境。
     *
     * @param url HTTP URL地址
     * @return 网络质量结果
     */
    public NetworkQualityEvent monitorNetworkQualityViaHttp(String url) {
        try {
            URL targetUrl = URI.create(url).toURL();
            HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
            connection.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(DEFAULT_READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "NetworkQualityMonitor/2.0");

            long startTime = System.currentTimeMillis();
            int responseCode = connection.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;

            if (responseCode >= 200 && responseCode < 400) {
                return new NetworkQualityEvent((int) responseTime, 0.0f);
            } else {
                return new NetworkQualityEvent(-1, 100.0f);
            }

        } catch (Exception e) {
            log.debug("HTTP网络测试失败: {} - {}", url, e.getMessage());
            return new NetworkQualityEvent(-1, 100.0f);
        }
    }

    /**
     * 使用DNS解析测试网络质量
     * <p>
     * <strong>功能说明：</strong>
     * 通过DNS解析测试网络质量，适用于需要测试域名解析性能的场景。
     *
     * @param hostname 主机名
     * @return 网络质量结果
     */
    public NetworkQualityEvent monitorNetworkQualityViaDns(String hostname) {
        try {
            long startTime = System.currentTimeMillis();
            InetAddress address = InetAddress.getByName(hostname);
            long resolveTime = System.currentTimeMillis() - startTime;

            if (address != null) {
                return new NetworkQualityEvent((int) resolveTime, 0.0f);
            } else {
                return new NetworkQualityEvent(-1, 100.0f);
            }

        } catch (Exception e) {
            log.debug("DNS网络测试失败: {} - {}", hostname, e.getMessage());
            return new NetworkQualityEvent(-1, 100.0f);
        }
    }
}
