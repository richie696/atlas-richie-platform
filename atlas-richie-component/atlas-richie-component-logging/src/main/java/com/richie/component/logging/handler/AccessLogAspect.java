package com.richie.component.logging.handler;

import com.richie.context.common.api.HeaderContextHolder;
import com.richie.contract.model.LoginUserPrincipal;
import com.richie.contract.model.ApiResult;
import com.richie.contract.constant.GlobalConstants;
import com.richie.context.utils.data.JsonUtils;
import com.richie.context.utils.web.ServletUtils;
import com.richie.context.utils.spring.JwtUtils;
import com.richie.context.utils.spring.SpringBeanUtils;
import com.richie.component.cache.GlobalCache;
import com.richie.component.concurrency.measurement.Stopwatch;
import com.richie.component.dao.snowflake.IdBuilder;
import com.richie.component.logging.annotations.AccessLog;
import com.richie.component.logging.callback.LogLifecycleCallback;
import com.richie.component.logging.config.OperateLogProperties;
import com.richie.component.logging.domain.AccessLogInfo;
import com.richie.component.logging.service.AccessLogService;
import org.apache.commons.lang3.exception.ExceptionUtils;
import tools.jackson.databind.JsonNode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import jakarta.annotation.Nonnull;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.richie.component.cache.enums.L2CachingRegion.ACCESS_LOG;

/**
 * 访问日志切面：对 Controller 方法进行环绕增强，记录请求/响应、耗时等并支持 FILE/REDIS/MQ/DB 多种持久化方式。
 *
 * @author richie696
 * @version 1.0
 * @since 2022-05-16 11:20:48
 */
@Slf4j
@Aspect
@Order(2)
@Component
@RequiredArgsConstructor
public class AccessLogAspect {

    /**
     * 日志记录配置文件
     */
    private final OperateLogProperties properties;

    /**
     * 消息队列服务
     */
    private final QueueHandler queueHandler;

    /**
     * 日志记录数据库接口
     */
    private final AccessLogService accessLogService;

    /**
     * 日志ID生成器
     */
    private final IdBuilder idBuilder;

    /**
     * 切点：带 @AccessLog 的方法或所有 Controller 的 public 方法（当启用全局切点时）。
     */
    @Pointcut("@annotation(com.richie.component.logging.annotations.AccessLog) || execution(public * com.richie..*.controller..*Controller.*(..))")
    private void useMethod() {
    }

    /**
     * 环绕增强：记录请求参数、执行耗时、响应结果，并按配置进行 FILE/REDIS/MQ/DB 持久化。
     *
     * @param joinPoint 切点
     * @return 原方法返回值
     * @throws Throwable 原方法或切面逻辑抛出的异常
     */
    @Around("useMethod()")
    public Object recordLog(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!properties.isEnable()) {
            return joinPoint.proceed();
        }
        // 获取 Request 对象
        var requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        assert requestAttributes != null;
        var request = requestAttributes.getRequest();
        Stopwatch stopwatch = Stopwatch.createStarted();
        // 获取切点方法
        var signature = (MethodSignature) joinPoint.getSignature();
        // 获取方法
        var method = signature.getMethod();
        // 获取切点函数的请求参数
        var args = joinPoint.getArgs();
        // 获取方法上的注解
        var accessLog = method.getAnnotation(AccessLog.class);
        // 如果全局日志开关关闭并且当前方法上没有注解则直接执行方法
        if (!properties.isEnableGlobalAdvice() && Objects.isNull(accessLog)) {
            return joinPoint.proceed();
        }
        // 否则执行切面日志记录
        var requestData = getRequestData(signature, args);
        var zoneId = LocaleContextHolder.getTimeZone().toZoneId();
        var operateTime = OffsetDateTime.now(zoneId);
        Object originResult;
        Throwable ex = null;
        try {
            originResult = joinPoint.proceed();
        } catch (Throwable throwable) {
            if (properties.isPrintException()) {
                log.error("AOP 日志记录异常：%s".formatted(throwable.getMessage()), throwable);
            }
            // 调用异常处理回调
            var errorCallback = SpringBeanUtils.getBean(LogLifecycleCallback.OnErrorCallback.class);
            if (errorCallback != null) {
                var customResult = errorCallback.apply(requestData, throwable);
                if (customResult != null) {
                    originResult = customResult;
                } else {
                    originResult = ApiResult.error("500", ExceptionUtils.getRootCause(throwable));
                }
            } else {
                originResult = ApiResult.error("500", ExceptionUtils.getRootCause(throwable));
            }
            ex = throwable;
        }
        long elapsedMillis = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        if (properties.isEnable()) {
            generatePointCutLog(request, requestData, originResult, operateTime,
                    elapsedMillis, accessLog, ex, joinPoint);
        }
        return originResult;
    }

    /**
     * 将切点方法参数名与参数值组装为 Map（文件/Request/Response 等以类型名占位）。
     *
     * @param targetMethod 方法签名
     * @param args         参数值
     * @return 参数名 -> 参数值
     */
    @Nonnull
    private Map<String, Object> getRequestData(MethodSignature targetMethod, Object[] args) {
        var parameterNames = targetMethod.getParameterNames();
        Map<String, Object> requestData = new LinkedHashMap<>(parameterNames.length);
        // 遍历参数列表
        for (int i = 0; i < args.length; i++) {
            var obj = args[i];
            // 如果当前对象是文件对象则直接将参数跳过，不进行请求体记录
            if ((obj instanceof MultipartFile)
                    || (obj instanceof HttpServletRequest)
                    || (obj instanceof HttpServletResponse)) {
                requestData.put(parameterNames[i], obj.getClass().getName());
                continue;
            }
            requestData.put(parameterNames[i], obj);
        }
        return requestData;
    }

    /**
     * 生成并持久化切点日志（调用回调、按 recordType 落盘、可选 DB 持久化）。
     *
     * @param request      请求
     * @param requestData  请求参数 Map
     * @param responseData 响应数据
     * @param operateTime  操作时间
     * @param elapsedTime  耗时（毫秒）
     * @param accessLog    方法上的 AccessLog 注解（可为 null）
     * @param throwable    若执行异常则非 null
     * @param joinPoint    切点
     * @throws Throwable 若 throwable 非 null 则重新抛出
     */
    private void generatePointCutLog(HttpServletRequest request, Map<String, Object> requestData,
                                     Object responseData, OffsetDateTime operateTime, long elapsedTime,
                                     AccessLog accessLog, Throwable throwable, ProceedingJoinPoint joinPoint) throws Throwable {
        // 调用日志记录前回调
        var beforeLogCallback = SpringBeanUtils.getBean(LogLifecycleCallback.BeforeLogCallback.class);
        AccessLogInfo logInfo = null;
        if (beforeLogCallback != null) {
            // 对于非 ResultVO 类型，回调可能无法处理，需要特殊处理
            if (responseData instanceof ApiResult<?> apiResult) {
                logInfo = beforeLogCallback.apply(requestData, apiResult);
            } else {
                logInfo = beforeLogCallback.apply(requestData, null);
            }
        }
        // 如果回调返回null，则使用默认方式生成日志信息
        if (logInfo == null) {
            logInfo = createDefaultLog(request, requestData, responseData, operateTime, elapsedTime, accessLog);
        }

        // 调用持久化前回调
        var beforePersistCallback = SpringBeanUtils.getBean(LogLifecycleCallback.BeforePersistCallback.class);
        if (beforePersistCallback != null) {
            var modifiedLogInfo = beforePersistCallback.apply(logInfo, joinPoint);
            if (modifiedLogInfo != null) {
                logInfo = modifiedLogInfo;
            }
        }

        // 应答消息体
        switch (properties.getRecordType()) {
            case FILE -> recordLogFile(logInfo);
            case REDIS -> recordRedis(logInfo);
            case MQ -> sendMQ(logInfo);
        }
        // 如果启用数据库持久化则执行
        if (accessLog == null || (properties.isDbPersistent() && accessLog.persistent())) {
            doPersistent(logInfo);
        }

        // 调用日志记录后回调
        var afterLogCallback = SpringBeanUtils.getBean(LogLifecycleCallback.AfterLogCallback.class);
        if (afterLogCallback != null) {
            var shouldContinue = afterLogCallback.apply(logInfo, throwable);
            if (!shouldContinue) {
                // 如果回调返回false，终止执行
                return;
            }
        }

        if (throwable != null) {
            throw throwable;
        }
    }


    /**
     * 使用请求、响应、耗时等创建默认的 AccessLogInfo。
     *
     * @param request      请求
     * @param requestData  请求参数
     * @param responseData 响应数据
     * @param operateTime  操作时间
     * @param elapsedTime  耗时（毫秒）
     * @param accessLog    方法上的 AccessLog（可为 null）
     * @return 填充好的 AccessLogInfo
     * @throws Exception 解析/序列化异常
     */
    @Nonnull
    private AccessLogInfo createDefaultLog(HttpServletRequest request, Map<String, Object> requestData, Object responseData, OffsetDateTime operateTime, long elapsedTime, AccessLog accessLog) throws Exception {
        var logInfo = new AccessLogInfo();
        var url = request.getRequestURI();
        var token = request.getHeader(JwtUtils.X_ACCESS_TOKEN);

        // 设置操作人和租户信息
        setOperatorAndTenant(logInfo, token, url, responseData);

        // 设置请求体和响应体
        var requestBody = buildRequestBody(requestData);
        var responseBody = buildResponseBody(responseData);

        // 获取操作人详细信息
        enrichOperatorInfo(logInfo, token);

        // 填充日志基本信息
        fillLogInfo(logInfo, accessLog, operateTime, url, request, requestBody, responseBody, elapsedTime);

        return logInfo;
    }

    /**
     * 设置操作人和租户信息（从 token 或登录响应中解析）。
     *
     * @param logInfo       待填充的日志信息
     * @param token         JWT 或会话 token
     * @param url           请求 URL（用于判断是否登录接口）
     * @param responseData  响应数据（登录接口时用于解析用户信息）
     * @throws Exception 解析异常
     */
    private void setOperatorAndTenant(AccessLogInfo logInfo, String token, String url, Object responseData) throws Exception {
        // 如果令牌存在，直接使用令牌信息
        if (Objects.nonNull(token)) {
            logInfo.setOperator(JwtUtils.getUsername(token))
                    .setTenantId(JwtUtils.getArgument(token, "tenantId"));
            return;
        }

        // 令牌不存在，检查是否为登录请求
        if (!url.contains("login")) {
            logInfo.setOperator("未知用户");
            return;
        }

        // 登录请求，尝试从响应数据中获取令牌
        Object result;
        if (responseData instanceof ApiResult<?> apiResult) {
            result = apiResult.getData();
        } else {
            // 非 ResultVO 类型，直接使用响应数据
            result = responseData;
        }

        if (Objects.isNull(result)) {
            logInfo.setOperator("未知用户");
            return;
        }

        // 从响应数据中提取用户信息
        if (result instanceof JsonNode jsonNode) {
            var loginToken = jsonNode.asString("token");
            if (StringUtils.isNotBlank(loginToken)) {
                logInfo.setOperator(JwtUtils.getUsername(loginToken))
                        .setTenantId(JwtUtils.getArgument(loginToken, "tenantId"));
                return;
            }
        }

        if (result instanceof LoginUserPrincipal user) {
            String tenantId = user.getSignParams() != null ? user.getSignParams().get("tenantId") : null;
            logInfo.setOperator(user.getUsername())
                    .setTenantId(tenantId);
            return;
        }

        // 对于非 ResultVO 类型且无法提取用户信息的情况，设置为未知用户
        logInfo.setOperator("未知用户");
    }

    /**
     * 将请求参数 Map 序列化为字符串，并按配置截断长度。
     *
     * @param requestData 请求参数
     * @return 请求体字符串
     */
    private String buildRequestBody(Map<String, Object> requestData) {
        if (!properties.isRequestBodyPersistent()) {
            return "";
        }

        var requestBody = JsonUtils.getInstance().serialize(requestData);
        if (Objects.isNull(requestBody)) {
            requestBody = "";
        }

        if (properties.isRequestBodySizeLimit() && requestBody.length() > properties.getRequestBodyMaxLength()) {
            requestBody = requestBody.substring(0, properties.getRequestBodyMaxLength());
        }

        return requestBody;
    }

    /**
     * 将响应对象序列化为字符串，并按配置截断长度。
     *
     * @param responseData 响应数据
     * @return 响应体字符串
     */
    private String buildResponseBody(Object responseData) {
        if (!properties.isResponseBodyPersistent()) {
            return "";
        }

        // 对于 void 类型，返回空字符串或特殊标记
        if (responseData == null) {
            return "[void]";
        }

        // 对于非 ResultVO 类型，尝试序列化
        String responseBody;
        if (responseData instanceof ApiResult<?> apiResult) {
            responseBody = JsonUtils.getInstance().serialize(apiResult);
        } else {
            // 非 ResultVO 类型，构建一个包含类型信息的响应体
            var responseType = responseData.getClass().getSimpleName();
            try {
                // 尝试序列化响应数据
                var serialized = JsonUtils.getInstance().serialize(responseData);
                if (StringUtils.isNotBlank(serialized)) {
                    responseBody = serialized;
                } else {
                    // 如果序列化失败，记录类型信息
                    responseBody = "[%s]".formatted(responseType);
                }
            } catch (Exception e) {
                // 序列化失败，记录类型信息
                responseBody = "[%s]".formatted(responseType);
            }
        }

        if (Objects.isNull(responseBody)) {
            responseBody = "";
        }

        if (properties.isResponseBodySizeLimit() && responseBody.length() > properties.getResponseBodyMaxLength()) {
            responseBody = responseBody.substring(0, properties.getResponseBodyMaxLength());
        }

        return responseBody;
    }

    /**
     * 从 OperatorContextHolder 获取操作人 ID/姓名并写入 logInfo。
     *
     * @param logInfo 待填充的日志信息
     * @param token   会话 token
     */
    private void enrichOperatorInfo(AccessLogInfo logInfo, String token) {
        if (Objects.isNull(token) || !OperatorContextHolder.hasOperator(token)) {
            return;
        }

        var operator = OperatorContextHolder.getOperator(token);
        logInfo.setOperatorId(operator.getId())
                .setOperator(operator.getName());
    }

    /**
     * 填充日志基本信息（标题、URL、方法、请求/响应体、耗时、IP 等）。
     *
     * @param logInfo      待填充的日志信息
     * @param accessLog    方法上的 AccessLog（可为 null）
     * @param operateTime  操作时间
     * @param url          请求 URL
     * @param request      请求
     * @param requestBody  请求体字符串
     * @param responseBody 响应体字符串
     * @param elapsedTime  耗时（毫秒）
     */
    private void fillLogInfo(AccessLogInfo logInfo, AccessLog accessLog, OffsetDateTime operateTime,
                            String url, HttpServletRequest request, String requestBody,
                            String responseBody, long elapsedTime) {
        logInfo.setId(idBuilder.nextId())
                .setTitle(accessLog == null ? "" : accessLog.value())
                .setOperateTime(operateTime)
                .setUrl(url)
                .setMethod(request.getMethod())
                .setRequestBody(requestBody)
                .setResponseBody(responseBody)
                .setElapsedTime(elapsedTime)
                .setIp(ServletUtils.getClientIP(request));

        var extra = HeaderContextHolder.getHeader(GlobalConstants.X_RD_REQUEST_EXTRA);
        if (StringUtils.isNotBlank(extra)) {
            logInfo.setExtra(extra);
        }
    }

    /**
     * 记录到日志文件的方法
     *
     * @param logInfo 日志信息
     */
    private void recordLogFile(AccessLogInfo logInfo) {
        log.info("[AOP LOG] >>> {}", logInfo);
    }

    /**
     * 记录到 Redis 的方法
     *
     * @param logInfo 日志信息
     */
    private void recordRedis(AccessLogInfo logInfo) {
        var date = logInfo.getOperateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        GlobalCache.struct().set(
                "%s:%s:%s".formatted(properties.getCacheAccessLogKey(), date, logInfo.getOperator()),
                logInfo,
                TimeUnit.DAYS.toMillis(3)
        );
    }

    /**
     * 记录到 Kafka 的方法
     *
     * @param logInfo 日志信息
     */
    private void sendMQ(AccessLogInfo logInfo) {
        queueHandler.sendMessage(properties.getMqTopicName(), logInfo);
    }

    /**
     * 持久化到数据库的方法
     *
     * @param logInfo 日志信息
     */
    private void doPersistent(AccessLogInfo logInfo) {
        accessLogService.doRecordLog(ACCESS_LOG, logInfo);
    }

}


