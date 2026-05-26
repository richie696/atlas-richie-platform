package com.richie.component.logging.service.impl;

import com.richie.component.cache.local.manage.CacheName;
import com.richie.component.cache.local.manage.LocalCache;
import com.richie.component.logging.domain.AccessLogInfo;
import com.richie.component.logging.job.PersistentJob;
import com.richie.component.logging.mapper.AccessLogMapper;
import com.richie.component.logging.service.AccessLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 访问日志服务实现：将日志写入本地缓存，由 {@link PersistentJob} 定时批量入库。
 *
 * @author richie696
 * @since 2022-10-09
 */
@Service
public class AccessLogServiceImpl
        extends ServiceImpl<AccessLogMapper, AccessLogInfo>
        implements AccessLogService {

    /**
     * 默认构造函数（供 Spring 使用）。
     */
    public AccessLogServiceImpl() {
    }

    /**
     * 异步将单条访问日志放入本地缓存，后续由定时任务批量持久化。
     *
     * @param cacheName      缓存区域（如 ACCESS_LOG）
     * @param accessLogInfo  访问日志信息
     */
    @Async
    @Override
    public void doRecordLog(CacheName cacheName, AccessLogInfo accessLogInfo) {
        try {
            LocalCache.put(cacheName, accessLogInfo.getId().toString(), accessLogInfo);
        } catch (Exception _) {
        }
    }


}
