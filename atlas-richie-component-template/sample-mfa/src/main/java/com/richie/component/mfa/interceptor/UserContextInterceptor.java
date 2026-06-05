package com.richie.component.mfa.interceptor;

import com.richie.context.common.api.LoginUserContextHolder;
import com.richie.contract.model.ApiResult;
import com.richie.contract.constant.GlobalConstants;
import com.richie.contract.gateway.config.GatewayContract;
import com.richie.context.utils.data.JsonUtils;
import com.richie.context.utils.spring.JwtUtils;
import com.richie.component.cache.GlobalCache;
import com.richie.component.i18n.config.I18nProperties;
import com.richie.component.mfa.dto.LoginUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

/**
 * 用户信息拦截器
 *
 * @author richie696
 * @version 1.0
 * @since 2023-11-16 10:31:14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserContextInterceptor implements AsyncHandlerInterceptor {

    private final GatewayContract gatewayContract;
    private final I18nProperties i18nProperties;

    @Override
    public boolean preHandle(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull Object handler) throws Exception {
        // 忽略健康检查相关接口
        String requestURI = request.getRequestURI();
        if (requestURI.startsWith("/actuator/health") || requestURI.startsWith("/swagger-ui")) {
            return true;
        }
        log.info("requestURI，{}", requestURI);
        // 设置默认的地区
        setDefaultLocale(request);
        if (gatewayContract == null) {
            return true;
        }
        String token = request.getHeader(JwtUtils.X_ACCESS_TOKEN);
        if (gatewayContract.getToken().getIgnoreUriList().stream().anyMatch(requestURI::matches) && StringUtils.isBlank(token)) {
            return true;
        }
        if (gatewayContract.getToken().getLoginUriList().stream().anyMatch(requestURI::matches)) {
            return true;
        }

        if (StringUtils.isBlank(token)) {
            return createUnauthorizedError(response);
        }
        var userKey = JwtUtils.getUserKey(token);
        var userInfo = GlobalCache.struct().get(userKey, LoginUserPrincipal.class);
        if (Objects.isNull(userInfo)) {
            return createUnauthorizedError(response);
        }
        LoginUserContextHolder.setUserInfo(userInfo);
        LoginUserContextHolder.setToken(token);
        return true;
    }

    private static boolean createUnauthorizedError(@Nonnull HttpServletResponse response) throws IOException {
        ApiResult<Object> error = ApiResult.error(String.valueOf(HttpStatus.UNAUTHORIZED.value()), HttpStatus.UNAUTHORIZED.getReasonPhrase());
        response.setStatus(HttpStatus.OK.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(Objects.requireNonNull(JsonUtils.getInstance().serialize(error)));
        return false;
    }

    private void setDefaultLocale(HttpServletRequest request) {
        Locale locale;
        String language = request.getHeader(GlobalConstants.X_RD_REQUEST_LANGUAGE);
        if (StringUtils.isBlank(language)) {
            locale = i18nProperties.getDefaultLocale();
        } else {
            locale = Locale.forLanguageTag(language);
        }
        LocaleContextHolder.setLocale(locale);
    }

    @Override
    public void afterCompletion(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response,
                                @Nonnull Object handler, Exception ex) {
        LoginUserContextHolder.clear();
    }
}
