package com.richie.component.threadpool.controller;

import com.richie.contract.model.ApiResult;
import com.richie.contract.constant.GlobalConstants;
import com.richie.context.utils.data.Collections;
import com.richie.component.cache.local.manage.CacheName;
import com.richie.component.cache.local.manage.LocalCache;
import com.richie.component.dao.snowflake.IdBuilder;
import com.richie.component.i18n.config.I18nProperties;
import com.richie.component.i18n.resolver.I18n;
import com.richie.component.logging.annotations.AccessLog;
import com.richie.component.threadpool.domain.ApiInfo;
import com.richie.component.threadpool.domain.TimeInfo;
import com.richie.component.threadpool.service.ApiService;
import com.richie.component.threadpool.service.TimeService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dromara.dynamictp.core.DtpRegistry;
import org.dromara.dynamictp.core.executor.DtpExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * API DOC 测试控制器
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-01 20:27:39
 */
@Slf4j
@Tag(name = "ApiDoc测试接口")
@RequestMapping("/api-doc")
@RestController
@RequiredArgsConstructor
public class ThreadPoolController {

    @Qualifier("demoExecutor")
    private final DtpExecutor demoExecutor;
    private final ApiService apiService;
    private final IdBuilder idBuilder;
    private final TimeService timeService;

    @AccessLog(value = "获取API信息的接口", persistent = true)
    @Operation(summary = "打招呼的接口", method = "GET", description = "这是打招呼的方法，您输入一个名字，我们将会和您打招呼。")
    @GetMapping("/hello")
    public ApiResult<String> hello(@Parameter(name = "你的名字", required = true) String name) throws InterruptedException, ExecutionException {
        demoExecutor.execute(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
            }
            System.err.println("你好，" + name + "！这里是动态线程池测试，");
        });
        // 通过代码获取
        Future<String> stringFuture = DtpRegistry.getDtpExecutor("demoExecutor").submit(() -> "DtpRegistry.getDtpExecutor() 获取的线程(" + name + ")。");
        return ApiResult.success(stringFuture.get(), null);
    }

    @GetMapping("/i18n")
    public ApiResult<ApiInfo> getApiI118n() {
        ApiInfo apiInfo = new ApiInfo();
        apiInfo.setId(1L).setName("MSG_KEY_1").setRole(new ApiInfo.RoleInfo().setId(1L).setName("MSG_KEY_2"));
        return ApiResult.success(apiInfo);
    }

    @AccessLog(value = "获取API信息的接口", persistent = true)
    @Operation(summary = "获取指定API信息的接口", method = "GET", description = "通过REST接口请求获取指定API信息。")
    @GetMapping("/list/{id}")
    public ApiResult<ApiInfo> getApi(@Parameter(name = "API信息ID", required = true) @PathVariable Long id) {
        ApiInfo uniqueApi = apiService.getUniqueApi(id);
        if (Objects.isNull(uniqueApi)) {
            return ApiResult.error("找不到对应的API信息");
        }
        return ApiResult.success(uniqueApi);
    }


    @GetMapping("/login/{username}")
    public ApiResult<Map<String, Object>> login(@PathVariable String username) {
        Map<String, Object> map = Collections.mapOf("token", "token", "content", "Hello, " + username);
        return ApiResult.success(map);
    }

    @PostMapping("/list")
    public ApiResult<Page<ApiInfo>> list(@RequestBody ApiInfo info) {
        return apiService.getApiList(info);
    }

    @AccessLog(value = "获取雪花ID", persistent = true)
    @GetMapping("/test2")
    public ApiResult<String> test2(HttpServletRequest request) {
        Collections.streamOf(request.getParameterNames()).forEach(name -> {

        });
        return ApiResult.success(idBuilder.nextId() + " (Release)");
    }

    public record CacheBean(String cache, String key, Object value) implements CacheName {
        @Override
        public String getCache() {
            return cache;
        }
    }

    @AccessLog(value = "写入本地缓存数据", persistent = true)
    @PostMapping("/local/cache/put")
    public ApiResult<?> putLocalCache(@RequestBody CacheBean cacheBean) {
        LocalCache.put(cacheBean, cacheBean.key(), cacheBean.value());
        return ApiResult.success();
    }


    @AccessLog(value = "获取本地缓存数据", persistent = true)
    @PostMapping("/local/cache/get")
    public ApiResult<?> getLocalCache(@RequestBody CacheBean cacheBean) {
        Map<String, Object> map = LocalCache.get(cacheBean, cacheBean.key());
        return ApiResult.success(map);
    }

    @PostMapping("/time")
    public ApiResult<?> testTime(@RequestBody TimeInfo timeBean) {
        return ApiResult.success(timeService.save(timeBean));
    }

    private final I18nProperties i18nProperties;

    @GetMapping("/i18n/thanks")
    public ApiResult<?> sayThanks(HttpServletRequest request) {
        Locale locale;
        String language = request.getHeader(GlobalConstants.X_RD_REQUEST_LANGUAGE);
        if (StringUtils.isBlank(language)) {
            locale = i18nProperties.getDefaultLocale();
        } else {
            locale = Locale.of(language);
        }
        return ApiResult.success(I18n.get(locale, "MSG_THANKS"));
    }
}
