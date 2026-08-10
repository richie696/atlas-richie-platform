package cn.richie696.component.web.core.exception;

import cn.richie696.contract.exception.BaseException;
import cn.richie696.contract.exception.BusinessException;
import cn.richie696.contract.exception.I18nMessageKeyException;
import cn.richie696.contract.exception.PlatformDataAccessException;
import cn.richie696.contract.exception.PlatformRuntimeException;
import cn.richie696.contract.model.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionControllerAdviceTest {

    private final GlobalExceptionControllerAdvice advice = new GlobalExceptionControllerAdvice();

    @Test
    void coversBaseExceptionAndPreservesBusinessCode() {
        var response = advice.base(new BaseException("418", "业务状态不允许"), request("req-base"));

        assertThat(response.getStatusCode().value()).isEqualTo(418);
        assertThat(response.getBody()).extracting(ApiResult::isSuccess).isEqualTo(false);
        assertThat(response.getBody()).extracting(ApiResult::getCode).isEqualTo("418");
        assertThat(response.getBody()).extracting(ApiResult::getMsg).isEqualTo("业务状态不允许");
        assertThat(response.getBody()).extracting(ApiResult::getRequestId).isEqualTo("req-base");
    }

    @Test
    void coversI18nExceptionWithItsHttpStatusAndErrorCode() {
        var exception = new I18nMessageKeyException("oauth.invalid_client", 401, "OAUTH_INVALID_CLIENT");
        var response = advice.i18nMessageKey(exception, request(null));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).extracting(ApiResult::getCode).isEqualTo("OAUTH_INVALID_CLIENT");
        assertThat(response.getBody()).extracting(ApiResult::getMsg).isEqualTo("oauth.invalid_client");
    }

    @Test
    void doesNotExposeDataAccessDetailsOrUnhandledExceptionDetails() {
        var dataAccess = advice.platformDataAccess(
                new PlatformDataAccessException("password=secret"), request("req-db"));
        var fallback = advice.fallback(new IllegalStateException("sql=secret"), request("req-fallback"));

        assertThat(dataAccess.getBody()).extracting(ApiResult::getMsg)
                .isEqualTo("数据访问失败，请稍后重试");
        assertThat(fallback.getBody()).extracting(ApiResult::getMsg)
                .isEqualTo("服务器内部错误，请稍后重试");
        assertThat(fallback.getBody().getMsg()).doesNotContain("secret");
    }

    @Test
    void coversOtherPlatformExceptionFamilies() {
        var business = advice.business(new BusinessException("BUSINESS_ERROR", "业务失败"), request(null));
        var runtime = advice.platformRuntime(new PlatformRuntimeException("请求无法完成"), request(null));

        assertThat(business.getStatusCode().value()).isEqualTo(400);
        assertThat(business.getBody()).extracting(ApiResult::getCode).isEqualTo("BUSINESS_ERROR");
        assertThat(runtime.getStatusCode().value()).isEqualTo(400);
        assertThat(runtime.getBody()).extracting(ApiResult::getCode).isEqualTo("REQUEST_ERROR");
    }

    @Test
    void mapsArgumentErrorsToBadRequest() {
        var response = advice.invalidRequest(new IllegalArgumentException("bad argument"), request(null));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).extracting(ApiResult::getCode)
                .isEqualTo(EnumErrorMassage.REQUEST_PARAMS_INVALID.getI18nCode());
    }

    @Test
    void unknownControllerExceptionProducesApiJsonInsteadOfContainerErrorPage() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(advice)
                .build();

        mockMvc.perform(get("/boom").header(GlobalExceptionControllerAdvice.REQUEST_ID_HEADER, "req-http"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("500"))
                .andExpect(jsonPath("$.msg").value("服务器内部错误，请稍后重试"))
                .andExpect(jsonPath("$.requestId").value("req-http"));
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("secret implementation detail");
        }
    }

    private static HttpServletRequest request(String requestId) {
        var request = new MockHttpServletRequest("GET", "/api/test");
        if (requestId != null) {
            request.addHeader(GlobalExceptionControllerAdvice.REQUEST_ID_HEADER, requestId);
        }
        return request;
    }
}
