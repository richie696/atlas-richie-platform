package com.richie.component.web.core.adapter;

import com.richie.contract.model.LoginUserPrincipal;
import com.richie.contract.model.ApiResult;
import com.richie.contract.constant.GlobalConstants;
import com.richie.context.utils.spring.JwtUtils;
import com.richie.component.web.core.config.login.LoginConfig;
import com.richie.component.web.core.config.mvc.CorsProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
 * <h2>作用域</h2>
 * <p>仅在<strong>不启用 Gateway</strong>的场景下生效。
 * 当部署了 atlas-richie-gateway-service 时，本 Advice 整个被旁路 —— 令牌签发归 gateway 负责。
 *
 * <h2>依赖</h2>
 * <p>同时注入 {@link LoginConfig}（登录地址 / token 密钥 / 有效期）与 {@link CorsProperties}（CORS 开关）。
 * 两个子域配置平级独立，从 {@link com.richie.component.web.core.config.WebProperties} 的子域引用。
 *
 * @author richie696
 * @since 2022-10-09
 */
@Slf4j
@RestControllerAdvice
public class IssueTokenAdvice implements ResponseBodyAdvice<Object> {

    private final LoginConfig login;
    private final CorsProperties cors;

    public IssueTokenAdvice(LoginConfig login, CorsProperties cors) {
        this.login = login;
        this.cors = cors;
    }

    @Override
    public boolean supports(@Nonnull MethodParameter returnType, @Nonnull Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, @Nonnull MethodParameter returnType, @Nonnull MediaType selectedContentType,
                                  @Nonnull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @Nonnull ServerHttpRequest request, @Nonnull ServerHttpResponse response) {
        if (!cors.isEnabled()) {
            return body;
        }
        if (CollectionUtils.isEmpty(login.getLoginUrls())) {
            log.error("未配置登录页信息，无法签发令牌。");
            return body;
        }
        if (login.getLoginUrls().stream().noneMatch(request.getURI().getPath()::equals)) {
            return body;
        }
        String secret = login.getTokenSecret();
        if (StringUtils.isBlank(secret)) {
            log.error("未配置令牌密钥，无法签发令牌。");
            return body;
        }
        long tokenExpirationDate = login.getTokenExpirationDate();
        if (tokenExpirationDate <= 0) {
            log.error("未配置令牌过期时间，无法签发令牌。");
            return body;
        }
        if (body instanceof ApiResult<?> apiResult && apiResult.getData() instanceof LoginUserPrincipal userVO
                && apiResult.isSuccess()) {
            long expiredTime = System.currentTimeMillis() + tokenExpirationDate;
            String token = JwtUtils.generateJwtToken(userVO, secret, expiredTime);
            userVO.getSignParams().clear();
            response.getHeaders().set(GlobalConstants.X_ACCESS_TOKEN, token);
        }
        return body;
    }
}