package com.richie.component.threadpool.aysnc;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.task.TaskDecorator;

/**
 * 子线程复制父线程的数据
 * 1. 复制父线程的数据源
 * 2. ...todo
 *
 * @author yuy
 * @version 1.0
 * @since 2023-10-24 17:08:42
 */
@Slf4j
@RequiredArgsConstructor
public class MdcTaskDecorator implements TaskDecorator {

    /** 异步线程池配置（用于判断是否传播数据源上下文） */
    private final CustomAsyncThreadPoolProperties customAsyncThreadPoolProperties;

    /**
     * 包装任务：在执行前恢复数据源上下文（若启用），执行后清理。
     *
     * @param runnable 原始任务
     * @return 包装后的任务
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        String peek = null;
        if (customAsyncThreadPoolProperties.isEnableDynamicDataSourceContextHolder()) {
            try {
                peek = DynamicDataSourceContextHolder.peek();
            } catch (Exception e) {
                log.error("Error Saving information for async thread");
            }
        }
        var finalPeek = peek;

        return () -> {
            // 是否需要清除
            var finalClearFlag = false;
            try {
                try {
                    if (customAsyncThreadPoolProperties.isEnableDynamicDataSourceContextHolder() && StringUtils.isNotBlank(finalPeek)) {
                        DynamicDataSourceContextHolder.push(finalPeek);
                        finalClearFlag = true;
                    }
                } catch (Exception e) {
                    log.error("Error Restore information for async thread");
                }
                runnable.run();
            } catch (Throwable e) {
                log.error("Error in async task", e);
            } finally {
                if (finalClearFlag) {
                    DynamicDataSourceContextHolder.poll();
                }
            }
        };
    }

}
