package com.richie.component.messaging.service.impl;

import com.richie.component.messaging.domain.UserInfo;
import com.richie.component.messaging.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 消息队列测试服务接口实现类
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-02 11:31:07
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Override
    public String sayHello(String name) {
        log.info("执行了1行");
        log.info("执行了2行");
        log.info("执行了3行");
        log.info("执行了4行");
        log.info("执行了5行");
        log.info("执行了6行");
        try {
            int sleepTime = ThreadLocalRandom.current().nextInt(1, 10);
            TimeUnit.SECONDS.sleep(sleepTime);
            if (sleepTime > 5) {
                throw new RuntimeException("测试异常, ThreadID = %d".formatted(Thread.currentThread().threadId()));
            }
        } catch (InterruptedException ignore) {
        }
        return "Hello, %s".formatted(name);
    }

    @Override
    public String sayHello(UserInfo userInfo) {
        return "Hello, %s".formatted(userInfo.getName());
    }
}
