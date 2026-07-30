package cn.richie696.antivirus.service;

import cn.richie696.antivirus.config.AntivirusProperties;
import cn.richie696.antivirus.model.ScanTask;
import cn.richie696.component.cache.GlobalCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Redis 任务查询投影。过期即删除，不承担扫描审计或业务附件状态的持久化职责。 */
@Repository
@RequiredArgsConstructor
public class ScanTaskRepository {
    private final AntivirusProperties properties;

    public void save(ScanTask task) {
        GlobalCache.struct().set(key(task.getTaskId()), task, properties.getTaskTtl().toMillis());
    }

    public Optional<ScanTask> find(String taskId) {
        return Optional.ofNullable(GlobalCache.struct().get(key(taskId), ScanTask.class));
    }

    public void delete(String taskId) {
        GlobalCache.key().removeCache(key(taskId));
    }

    private String key(String taskId) {
        return properties.getTaskKeyPrefix() + taskId;
    }
}
