package com.richie.gateway.service.impl;

import com.richie.component.cache.GlobalCache;
import com.richie.gateway.config.DuplicateSubmitConfig;
import com.richie.gateway.constants.GatewayRedisKey;
import com.richie.gateway.service.DuplicateSubmitService;
import com.richie.gateway.utils.NetworkUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.nio.charset.StandardCharsets;

/**
 * 防重复提交服务实现类
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DuplicateSubmitServiceImpl implements DuplicateSubmitService {

    private final DuplicateSubmitConfig config;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public boolean shouldCheckDuplicateSubmit(String path) {
        if (!config.isEnabled()) {
            return false;
        }

        // 检查排除路径
        for (String excludePath : config.getExcludePaths()) {
            if (pathMatcher.match(excludePath, path)) {
                return false;
            }
        }

        // 检查包含路径
        for (String includePath : config.getIncludePaths()) {
            if (pathMatcher.match(includePath, path)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isDuplicateSubmit(ServerHttpRequest request, String requestBody) {
        String requestId = generateRequestId(request, requestBody);
        String cacheKey = GatewayRedisKey.DUPLICATE_SUBMIT.getKey(requestId);

        // 检查缓存中是否已存在该请求
        return GlobalCache.hasKey(cacheKey);
    }

    @Override
    public void recordSubmit(ServerHttpRequest request, String requestBody) {
        String requestId = generateRequestId(request, requestBody);
        String cacheKey = GatewayRedisKey.DUPLICATE_SUBMIT.getKey(requestId);

        // 记录请求，设置过期时间
        GlobalCache.addStringCache(cacheKey, "1", config.getCacheExpire());
        log.debug("记录防重复提交请求: {}", requestId);
    }

    @Override
    public String generateRequestId(ServerHttpRequest request, String requestBody) {
        StringBuilder sb = new StringBuilder();

        // 1. 添加请求路径
        sb.append(request.getPath().value());

        // 2. 添加HTTP方法
        sb.append(":").append(request.getMethod().name());

        // 3. 添加IP地址（如果启用IP级别防重复提交）
        if (config.isEnableIpLevel()) {
            String ip = NetworkUtils.getIP(request);
            sb.append(":ip:").append(ip);
        }

        // 4. 添加用户标识（如果启用用户级别防重复提交）
        if (config.isEnableUserLevel()) {
            String userId = getUserId(request);
            if (StringUtils.hasText(userId)) {
                sb.append(":user:").append(userId);
            }
        }

        // 5. 添加请求体哈希（如果启用请求体哈希校验）
        if (config.isEnableBodyHash() && StringUtils.hasText(requestBody)) {
            String bodyHash = DigestUtils.md5DigestAsHex(requestBody.getBytes(StandardCharsets.UTF_8));
            sb.append(":body:").append(bodyHash);
        }

        // 6. 生成最终哈希
        return DigestUtils.md5DigestAsHex(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 从请求中获取用户ID
     * 可以从JWT Token、Header、Cookie等地方获取
     *
     * @param request 请求对象
     * @return 用户ID
     */
    private String getUserId(ServerHttpRequest request) {
        // 1. 尝试从Header中获取用户ID
        String userId = request.getHeaders().getFirst("X-User-Id");
        if (StringUtils.hasText(userId)) {
            return userId;
        }

        // 2. 尝试从JWT Token中获取用户ID
        String token = request.getHeaders().getFirst("X-Access-Token");
        if (StringUtils.hasText(token)) {
            try {
                // 这里可以调用JWT工具类解析用户ID
                // 暂时返回token的哈希值作为用户标识
                return DigestUtils.md5DigestAsHex(token.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                log.warn("解析JWT Token获取用户ID失败", e);
            }
        }

        // 3. 如果没有用户标识，返回null
        return null;
    }
}
