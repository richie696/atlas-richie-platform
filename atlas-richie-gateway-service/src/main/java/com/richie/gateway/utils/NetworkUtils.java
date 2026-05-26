package com.richie.gateway.utils;

import com.richie.contract.model.ApiResult;
import com.richie.context.utils.data.JsonUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.util.SubnetUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * 网络访问工具类
 *
 * @author richie696
 * @version 1.0
 * @since 2021/07/01
 */
public final class NetworkUtils {

    private static final Set<String> LOOPBACK_IPS = Set.of(
            "127.0.0.1", "localhost", "::1", "0:0:0:0:0:0:0:1"
    );
    private static final Map<String, String> INTRANET_IP_MAP = Map.of(
            "10.0.0.0/8", "10.255.255.255",
            "172.16.0.0/16", "172.31.255.255",
            "192.168.0.0/16", "192.168.255.255"
    );
    private static final List<SubnetUtils.SubnetInfo> SUBNETS = new ArrayList<>(255 * 3);

    static {
        for (Map.Entry<String, String> segment : INTRANET_IP_MAP.entrySet()) {
            String[] startIps = segment.getKey().split("\\.");
            String[] endIps = segment.getValue().split("\\.");
            if (segment.getKey().startsWith("172")) {
                for (int i = Integer.parseInt(startIps[1]); i <= Integer.parseInt(endIps[1]); i++) {
                    SUBNETS.add(new SubnetUtils(String.format("%s.%s.0.0/16", startIps[0], i)).getInfo());
                }
            } else {
                SUBNETS.add(new SubnetUtils(segment.getKey()).getInfo());
            }
        }
    }

    private NetworkUtils() {
    }

    /**
     * 根据HTTP请求信息获取访问客户端IP的方法
     *
     * @param request HTTP请求
     * @return 返回服务器IP
     */
    public static String getClientHost(ServerHttpRequest request) {
        return Objects.requireNonNull(request.getRemoteAddress()).getHostString();
    }

    /**
     * 返回错误信息的方法
     *
     * @param response 应答对象
     * @param httpStatus HTTP状态码
     * @param message 错误信息
     * @return 返回错误信息
     */
    public static Mono<Void> returnError(ServerHttpResponse response, HttpStatus httpStatus, String message) {
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        ApiResult<Void> result = ApiResult.error(message);
        result.setCode(String.valueOf(httpStatus.value()));
        DataBuffer wrap = response.bufferFactory()
                .wrap(Objects.requireNonNull(JsonUtils.getInstance().serializeBytes(result)));
        return response.writeWith(Mono.just(wrap));
    }


    /**
     * 检查当前访问的IP是否是内网IP的方法
     *
     * @param ip 待检测的IP地址
     * @return 返回检测结果（true：是内网IP，false：不是内网IP）
     */
    public static boolean isIntranetAddress(String ip) {
        for (SubnetUtils.SubnetInfo subnet : SUBNETS) {
            if (isLoopBackAddress(ip) || subnet.isInRange(ip)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查当前访问的IP是否是本地回环地址的方法
     *
     * @param ip 待检测的IP地址
     * @return 返回检测结果（true：是回环地址，false：不是回环地址）
     */
    public static boolean isLoopBackAddress(String ip) {
        return LOOPBACK_IPS.contains(ip);
    }

    /**
     * 获取客户端真实IP的方法
     * <p style="color:red">注意：此方法前端的反向代理服务器需要增加IP传递的相关配置，以nginx为例，增加如下配置后方可获取真实IP。
     * <pre>
     *     proxy_set_header     Host $host;
     *     proxy_set_header     X-Real-IP $remote_addr;
     *     proxy_set_header     REMOTE-HOST $remote_addr;
     *     proxy_set_header     X-Forwarded-For $proxy_add_x_forwarded_for;
     * </pre>
     *
     * @param request 请求对象
     * @return 返回客户端真实IP
     */
    public static String getIP(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("x-forwarded-for");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeaders().getFirst("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeaders().getFirst("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeaders().getFirst("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeaders().getFirst("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = Objects.requireNonNull(request.getRemoteAddress()).getHostString();
        }
        // 获取到多个ip时取第一个作为客户端真实ip
        if (StringUtils.isNotEmpty(ip) && ip.contains(",")) {
            String[] ipArray = ip.split(",");
            if (ArrayUtils.isNotEmpty(ipArray)) {
                ip = ipArray[0];
            }
        }
        return ip;
    }

    /**
     * 获取客户端 User-Agent
     *
     * @param request 请求对象
     * @return 返回 User-Agent，如果不存在则返回 null
     */
    public static String getUserAgent(ServerHttpRequest request) {
        return request.getHeaders().getFirst("User-Agent");
    }

}
