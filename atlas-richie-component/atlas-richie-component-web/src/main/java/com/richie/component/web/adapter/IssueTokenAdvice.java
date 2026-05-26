package com.richie.component.web.adapter;

import com.richie.contract.model.LoginUserPrincipal;
import com.richie.contract.model.ApiResult;
import com.richie.contract.constant.GlobalConstants;
import com.richie.contract.gateway.config.GatewayContract;
import com.richie.context.utils.spring.JwtUtils;
import com.richie.component.web.config.WebMvcProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import jakarta.annotation.Nonnull;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 响应体增强：在登录接口的 ResultVO(GeneralUserVO) 响应中签发 JWT 并写入 X-Access-Token 头。
 *
 * @author richie696
 * @since 2022-10-09
 */
@Slf4j
@RestControllerAdvice
public class IssueTokenAdvice implements ResponseBodyAdvice<Object> {

    /** 跨服务共享契约（可选），用于登录 URI 与令牌密钥 */
    private final GatewayContract gatewayContract;
    /** WebMVC 配置（CORS、登录 URL、令牌密钥等） */
    private final WebMvcProperties mvc;

    /**
     * 构造函数。
     *
     * @param gatewayContract 跨服务共享契约，可为 null
     * @param mvc             WebMVC 配置
     */
    public IssueTokenAdvice(@Autowired(required = false) GatewayContract gatewayContract, WebMvcProperties mvc) {
        this.gatewayContract = gatewayContract;
        this.mvc = mvc;
    }

    /**
     * 对所有控制器返回值生效。
     *
     * @param returnType    方法返回类型
     * @param converterType 选中的转换器类型
     * @return true
     */
    @Override
    public boolean supports(@Nonnull MethodParameter returnType, @Nonnull Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    /**
     * 若为登录接口且响应体为成功的 ResultVO(GeneralUserVO)，则签发 JWT 并写入响应头。
     *
     * @param body                   响应体
     * @param returnType             方法返回类型
     * @param selectedContentType    选中的内容类型
     * @param selectedConverterType  选中的转换器类型
     * @param request                请求
     * @param response               响应
     * @return 原 body 或未修改的 body
     */
    @Override
    public Object beforeBodyWrite(Object body, @Nonnull MethodParameter returnType, @Nonnull MediaType selectedContentType,
                                  @Nonnull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @Nonnull ServerHttpRequest request, @Nonnull ServerHttpResponse response) {
        // 如果没启用跨域则跳过颁发逻辑
        if (!mvc.getCors().isEnable()) {
            return body;
        }
        String secret;
        long tokenExpirationDate;
        if (gatewayContract == null) {
            if (CollectionUtils.isEmpty(mvc.getLoginUrls())) {
                log.error("未配置网关登陆页信息，无法签发令牌。");
                return body;
            }
            if (mvc.getLoginUrls().stream().noneMatch(request.getURI().getPath()::equals)) {
                return body;
            }
            secret = mvc.getTokenSecret();
            if (StringUtils.isBlank(secret)) {
                log.error("未配置网关令牌密钥，无法签发令牌。");
                return body;
            }
            tokenExpirationDate = mvc.getTokenExpirationDate();
            if (tokenExpirationDate <= 0) {
                log.error("未配置网关令牌过期时间，无法签发令牌。");
                return body;
            }
        } else {
            // 如果不是登陆页则跳过颁发逻辑
            if (gatewayContract.getToken().getLoginUriList().stream().noneMatch(request.getURI().getPath()::equals)) {
                var token = request.getHeaders().getFirst(GlobalConstants.X_ACCESS_TOKEN);
                response.getHeaders().set(GlobalConstants.X_ACCESS_TOKEN, token);
                return body;
            }
            secret = gatewayContract.getToken().getSecret();
            tokenExpirationDate = gatewayContract.getToken().getExpireTimeMillis();
        }
        if (body instanceof ApiResult<?> apiResult && apiResult.getData() instanceof LoginUserPrincipal userVO) {
            if (!apiResult.isSuccess()) {
                return body;
            }
            long expiredTime = System.currentTimeMillis() + tokenExpirationDate;
            String token = JwtUtils.generateJwtToken(userVO, secret, expiredTime);
            userVO.getSignParams().clear();
            response.getHeaders().set(GlobalConstants.X_ACCESS_TOKEN, token);
        }
        return body;
    }
}
