package cn.richie696.antivirus.service;

import cn.richie696.antivirus.model.ScanStatus;
import cn.richie696.antivirus.model.ScanTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 重新投递租约已到期的扫描任务，支持 Pod 宕机后的自动恢复。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScanRecoveryScheduler {
    private final ScanTaskRepository repository;
    private final ScanLeaseManager leaseManager;
    private final ScanEventPublisher publisher;

    @Scheduled(
            initialDelayString = "${platform.antivirus.recovery.poll-interval-ms:10000}",
            fixedDelayString = "${platform.antivirus.recovery.poll-interval-ms:10000}")
    public void recoverExpiredTasks() {
        Set<String> dueTaskIds = leaseManager.findDueTaskIds();
        if (dueTaskIds.isEmpty()) {
            return;
        }

        dueTaskIds.forEach(this::recover);
    }

    private void recover(String taskId) {
        try {
            ScanTask task = repository.find(taskId).orElse(null);
            if (task == null || isTerminal(task.getStatus())) {
                leaseManager.discardRecovery(taskId);
                return;
            }

            // 先发布、后条件删除：发布失败时保留标记；新消费者已领取时则保留其新恢复时间。
            publisher.publish(taskId);
            leaseManager.removeExpiredMarkerIfNoLease(taskId);
            log.info("已重新投递超时扫描任务: taskId={}, status={}", taskId, task.getStatus());
        } catch (RuntimeException exception) {
            // 保留 ZSet 标记，下一轮继续尝试。
            log.warn("重新投递超时扫描任务失败: taskId={}, error={}", taskId, exception.getMessage());
        }
    }

    private boolean isTerminal(ScanStatus status) {
        return status == ScanStatus.CLEAN
                || status == ScanStatus.INFECTED
                || status == ScanStatus.FAILED;
    }
}
