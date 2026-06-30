package com.richie.component.storage.local.cleanup;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.richie.component.cache.GlobalCache;
import com.richie.component.storage.bean.LocalConfig;
import com.richie.component.storage.local.repository.entity.FileMetadata;
import com.richie.component.storage.local.repository.mapper.FileMetadataMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 冷数据清理任务
 *
 * <p>基于本地文件系统的最后访问/修改时间执行近似清理，
 * 支持 dry-run 预览、批次上限、按配置的 cron 动态调度，
 * 并在删除文件后同步清理相关缓存，且可选软删数据库元数据。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-10-14
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class FileCleanupJob implements SchedulingConfigurer {

    private final LocalConfig localConfig;
    private final FileMetadataMapper fileMetadataMapper;

    /**
     * 执行清理任务（由调度器触发）
     */
    public void cleanup() {
        if (localConfig.getCleanup() == null || !Boolean.TRUE.equals(localConfig.getCleanup().getEnabled())) {
            return;
        }
        try {
            int retention = localConfig.getCleanup().getRetentionDays() == null ? 180 : localConfig.getCleanup().getRetentionDays();
            int maxDelete = localConfig.getCleanup().getMaxDeletePerRun() == null ? 1000 : localConfig.getCleanup().getMaxDeletePerRun();
            boolean dryRun = Boolean.TRUE.equals(localConfig.getCleanup().getDryRun());

            String basePath = localConfig.getPath();
            if (basePath == null || basePath.isBlank()) {
                log.warn("[storage-local] 清理任务跳过：未配置本地存储路径");
                return;
            }
            Path root = Paths.get(basePath);
            if (!Files.exists(root)) {
                log.warn("[storage-local] 清理任务跳过：根目录不存在 - {}", basePath);
                return;
            }

            LocalDateTime threshold = LocalDateTime.now().minusDays(retention);
            List<Path> candidates = new ArrayList<>();

            try (var stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .forEach(p -> {
                            try {
                                var attr = Files.readAttributes(p, BasicFileAttributes.class);
                                Instant last = attr.lastAccessTime() != null ? attr.lastAccessTime().toInstant() : attr.lastModifiedTime().toInstant();
                                LocalDateTime lastTime = LocalDateTime.ofInstant(last, ZoneId.systemDefault());
                                if (lastTime.isBefore(threshold)) {
                                    candidates.add(p);
                                }
                            } catch (Exception ignored) {
                            }
                        });
            }

            if (candidates.isEmpty()) {
                log.info("[storage-local] 清理任务：无过期文件");
                return;
            }

            int deleted = 0;
            for (Path p : candidates) {
                if (deleted >= maxDelete) break;
                if (dryRun) {
                    log.info("[storage-local] 清理预览(未执行)：{}", p);
                    deleted++;
                    continue;
                }
                try {
                    Files.deleteIfExists(p);
                    // 同步清理缓存（存在性/内容/元数据），尽力而为
                    String key = root.relativize(p).toString().replace(File.separatorChar, '/');
                    GlobalCache.key().removeCache("file:exists:" + key);
                    GlobalCache.key().removeCache("file:content:" + key);
                    GlobalCache.key().removeCache("file:metadata:" + key);
                    // 可选：同时清理数据库元数据（若开启）
                    if (Boolean.TRUE.equals(localConfig.getCleanup().getRemoveMetadata())) {
                        softDeleteMetadata(key, p.toString());
                    }
                    log.info("[storage-local] 已删除过期文件：{}", p);
                    deleted++;
                } catch (Exception e) {
                    log.warn("[storage-local] 删除失败：{} - {}", p, e.getMessage());
                }
            }
            log.info("[storage-local] 清理任务完成：处理 {} 个候选，实际{} {} 个", candidates.size(), dryRun ? "预览" : "删除", deleted);
        } catch (Exception e) {
            log.error("[storage-local] 清理任务异常", e);
        }
    }

    /**
     * 软删数据库中的元数据记录（更新 storage_state 与 deleted_time）
     *
     * @param key 存储键
     * @param physicalPath 物理路径
     */
    private void softDeleteMetadata(String key, String physicalPath) {
        try {
            FileMetadata update = new FileMetadata();
            update.setStorageState("DELETED");
            update.setDeletedTime(java.time.LocalDateTime.now());
            LambdaUpdateWrapper<FileMetadata> uw = new LambdaUpdateWrapper<>();
            uw.eq(FileMetadata::getKeyPath, key)
              .or()
              .eq(FileMetadata::getPhysicalPath, physicalPath);
            int affected = fileMetadataMapper.update(update, uw);
            if (affected == 0) {
                log.info("[storage-local] 未找到匹配的元数据记录，key={}, path={}", key, physicalPath);
            } else {
                log.info("[storage-local] 已软删元数据记录，影响行数={}，key={}, path={}", affected, key, physicalPath);
            }
        } catch (Exception e) {
            log.warn("[storage-local] 软删元数据失败：key={}, path={}, err={}", key, physicalPath, e.getMessage());
        }
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(
                this::cleanup,
                triggerContext -> {
                    String cron = "0 0 3 * * ?";
                    try {
                        if (localConfig.getCleanup() != null && localConfig.getCleanup().getCron() != null && !localConfig.getCleanup().getCron().isBlank()) {
                            cron = localConfig.getCleanup().getCron();
                        }
                    } catch (Exception ignored) {}
                    return new CronTrigger(cron).nextExecution(triggerContext);
                }
        );
    }
}


