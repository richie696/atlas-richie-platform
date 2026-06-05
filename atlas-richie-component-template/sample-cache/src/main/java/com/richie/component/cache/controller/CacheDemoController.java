package com.richie.component.cache.controller;

import com.richie.contract.model.ApiResult;
import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.domain.UserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 缓存测试控制器
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-01 19:51:32
 */
@Tag(name = "缓存测试接口")
@RestController
@RequestMapping("/redis/demo")
public class CacheDemoController {


    @Operation(summary = "添加缓存接口", method = "GET", description = "添加缓存")
    @PostMapping("/addObject")
    public ApiResult<String> doAdd(@RequestBody UserInfo user) {
        GlobalCache.struct().set("user", user, 20000L);
        return ApiResult.success();
    }

    @Operation(summary = "添加缓存接口", method = "GET", description = "添加缓存")
    @GetMapping("/add")
    public ApiResult<String> doAdd(
            @Parameter(name = "缓存KEY", required = true) @RequestParam("key") String key,
            @Parameter(name = "缓存值", required = true) @RequestParam("value") String value,
            @Parameter(name = "超时时长", required = true) @RequestParam("time") Integer time) {
        try (var lock = GlobalCache.lock().pessimistic(key, time)) {
            if (!lock.isSuccess()) {
                return ApiResult.error("获取分布式锁失败。");
            }
            if (GlobalCache.key().hasKey(key)) {
                return ApiResult.error("当前数据已被其他线程写入，缓存写入失败。");
            }
            if ("obj".equals(value)) {
                List<UserInfo> users = new ArrayList<>(10);
                for (int i = 1; i <= 10; i++) {
                    users.add(new UserInfo(i, "测试%d".formatted(i), 18, ""));
                }
                GlobalCache.struct().set(key, users, 20000L);
            } else {
                GlobalCache.value().set(key, value, 20000L);
            }
        }
        return ApiResult.success(key);
    }

    @Operation(summary = "删除缓存接口", method = "GET", description = "删除缓存")
    @GetMapping("/delete")
    public ApiResult<String> doDelete(
            @Parameter(name = "缓存KEY", required = true) @RequestParam("key") String key) {
        GlobalCache.key().removeCache(key);
        return ApiResult.success();
    }

    @Operation(summary = "查询缓存接口", method = "GET", description = "查询缓存")
    @GetMapping("/search")
    public ApiResult<String> getString(
            @RequestParam("key") String key) {
        String value = GlobalCache.value().get(key, String.class);
        return ApiResult.success(value);
    }

    @Operation(summary = "查询缓存接口", method = "GET", description = "查询缓存")
    @GetMapping("/getObject")
    public ApiResult<UserInfo> getObject(
            @RequestParam("key") String key) {
        UserInfo user = GlobalCache.struct().get(key, UserInfo.class);
        return ApiResult.success(user);
    }

    public record AddUserDTO(String key, List<UserInfo> users) {}

    @Operation(summary = "添加缓存对象集合接口", method = "POST", description = "添加缓存对象集合接口")
    @GetMapping("/addList")
    public ApiResult<List<UserInfo>> addObjectList(@RequestBody AddUserDTO userDTO) {
//        GlobalCache.addListCache(userDTO.key, userDTO.users, 10000L);
        return ApiResult.success();
    }
    @Operation(summary = "查询缓存接口", method = "GET", description = "查询缓存")
    @GetMapping("/getList")
    public ApiResult<List<UserInfo>> getObjectList(
            @RequestParam("key") String key) {
        return ApiResult.success(null);
    }


}
