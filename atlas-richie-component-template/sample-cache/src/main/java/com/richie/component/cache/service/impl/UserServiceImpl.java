package com.richie.component.cache.service.impl;

import com.richie.component.cache.domain.UserInfo;
import com.richie.component.cache.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserServiceImpl implements UserService {


    /**
     * 处理用户信息的业务逻辑
     *
     * @param userInfo 用户信息
     * @throws Exception 处理异常
     */
    @Override
    public void processUserInfo(UserInfo userInfo) throws Exception {
        // 模拟业务处理逻辑
        log.debug("开始处理用户信息业务逻辑: {}", userInfo.getName());

        // 1. 数据验证
        validateUserInfo(userInfo);

        // 2. 业务处理
        performBusinessLogic(userInfo);

        // 3. 后续操作
        performPostProcessing(userInfo);

        log.debug("用户信息业务逻辑处理完成: {}", userInfo.getName());
    }

    @Override
    public void markUserAsError(UserInfo userInfo, String message) {
        log.error("标记用户为错误状态: {}, 原因: {}", userInfo.getName(), message);
        // 这里可以添加实际的错误标记逻辑，比如更新数据库状态等
    }

    /**
     * 验证用户信息
     *
     * @param userInfo 用户信息
     * @throws IllegalArgumentException 验证失败
     */
    private void validateUserInfo(UserInfo userInfo) throws IllegalArgumentException {
        if (userInfo.getName() == null || userInfo.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("用户姓名不能为空");
        }
        if (userInfo.getAge() == null || userInfo.getAge() < 0 || userInfo.getAge() > 150) {
            throw new IllegalArgumentException("用户年龄必须在 0-150 之间");
        }
        log.debug("用户信息验证通过: {}", userInfo.getName());
    }

    /**
     * 执行业务逻辑
     *
     * @param userInfo 用户信息
     * @throws Exception 业务处理异常
     */
    private void performBusinessLogic(UserInfo userInfo) throws Exception {
        // 模拟业务处理（比如：保存到数据库、发送通知等）
        log.debug("执行业务逻辑: 处理用户 {}", userInfo.getName());

        // 模拟处理时间
        Thread.sleep(100);

        // 模拟偶尔的业务异常（用于测试错误处理）
        if ("error".equals(userInfo.getName())) {
            throw new RuntimeException("模拟业务处理异常");
        }

        log.debug("业务逻辑执行完成: {}", userInfo.getName());
    }

    /**
     * 执行后续处理
     *
     * @param userInfo 用户信息
     * @throws Exception 后续处理异常
     */
    private void performPostProcessing(UserInfo userInfo) throws Exception {
        // 模拟后续处理（比如：发送邮件、更新缓存等）
        log.debug("执行后续处理: 用户 {}", userInfo.getName());

        // 模拟处理时间
        Thread.sleep(50);

        log.debug("后续处理完成: {}", userInfo.getName());
    }
}
