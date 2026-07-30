package cn.richie696.antivirus.consumer;

import cn.richie696.antivirus.model.ScanRequestedEvent;
import cn.richie696.antivirus.model.ScanStatus;
import cn.richie696.antivirus.model.ScanTask;
import cn.richie696.antivirus.scanner.ScanExecutor;
import cn.richie696.antivirus.scanner.ScanOutcome;
import cn.richie696.antivirus.service.ScanLeaseManager;
import cn.richie696.antivirus.service.ScanTaskRepository;
import cn.richie696.component.redis.streammq.stream.AbstractStreamConsumer;
import cn.richie696.component.redis.streammq.stream.EventContext;
import cn.richie696.component.redis.streammq.stream.RedisStreamConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 多实例通过 Redis Stream consumer group 竞争消费同一个扫描任务。 */
@Slf4j
@RedisStreamConsumer("antivirus-scan-worker")
@RequiredArgsConstructor
public class ScanRequestConsumer extends AbstractStreamConsumer<ScanRequestedEvent> {
    private final ScanTaskRepository repository;
    private final ScanExecutor executor;
    private final ScanLeaseManager leaseManager;

    @Override
    protected void handle(ScanRequestedEvent event, EventContext context) {
        ScanTask task = repository.find(event.getTaskId()).orElse(null);
        if (task == null || isTerminal(task.getStatus())) {
            leaseManager.discardRecovery(event.getTaskId());
            context.ack();
            return;
        }

        String ownerToken = UUID.randomUUID().toString();
        if (!leaseManager.tryAcquire(event.getTaskId(), ownerToken)) {
            // 另一实例正在执行；其租约到期记录已经负责宕机恢复，本条重复消息可直接确认。
            context.ack();
            return;
        }

        // 从此刻起，恢复 ZSet 是任务的可靠重试来源，Stream 消息不再占用 Pending List。
        context.ack();

        // 领取租约后再次读取，避免排队期间任务已经进入终态。
        task = repository.find(event.getTaskId()).orElse(null);
        if (task == null || isTerminal(task.getStatus())) {
            leaseManager.complete(event.getTaskId(), ownerToken);
            return;
        }

        task.setStatus(ScanStatus.SCANNING);
        if (task.getStartedAt() == null) {
            task.setStartedAt(OffsetDateTime.now());
        }
        repository.save(task);

        ScanOutcome outcome;
        try {
            outcome = executor.scan(task);
        } catch (RuntimeException exception) {
            outcome = ScanOutcome.failed("扫描执行异常");
        }

        // 租约超时后可能已有新实例接管，旧实例不得覆盖新执行结果。
        if (!leaseManager.isOwner(task.getTaskId(), ownerToken)) {
            log.warn("扫描完成时租约已失效，丢弃旧执行结果: taskId={}", task.getTaskId());
            return;
        }

        applyOutcome(task, outcome);
        repository.save(task);
        leaseManager.complete(task.getTaskId(), ownerToken);
    }

    @Override
    protected void onError(Throwable error, ScanRequestedEvent event, EventContext context) {
        log.error("扫描消息处理异常，将由恢复调度器重投: taskId={}", event.getTaskId(), error);
        try {
            leaseManager.scheduleAfterFailure(event.getTaskId());
            context.ack();
        } catch (RuntimeException recoveryError) {
            log.error("登记扫描任务恢复失败: taskId={}", event.getTaskId(), recoveryError);
        }
    }

    @Override
    protected String buildIdempotencyKey(ScanRequestedEvent event, String recordId) {
        // 任务幂等由可过期租约负责；避免 StreamMQ 的长 TTL 去重阻断恢复消息。
        return recordId + ':' + UUID.randomUUID();
    }

    private boolean isTerminal(ScanStatus status) {
        return status == ScanStatus.CLEAN
                || status == ScanStatus.INFECTED
                || status == ScanStatus.FAILED;
    }

    private void applyOutcome(ScanTask task, ScanOutcome outcome) {
        // 执行器仅允许产出终态；防止错误实现将 PENDING/SCANNING 写回缓存。
        task.setStatus(outcome.status() == ScanStatus.CLEAN || outcome.status() == ScanStatus.INFECTED
                ? outcome.status() : ScanStatus.FAILED);
        task.setActualSize(outcome.actualSize());
        task.setSha256(outcome.sha256());
        task.setDetectedMimeType(outcome.detectedMimeType());
        task.setThreatName(outcome.threatName());
        task.setEngineVersion(outcome.engineVersion());
        task.setSignatureVersion(outcome.signatureVersion());
        task.setErrorMessage(outcome.errorMessage());
        task.setCompletedAt(OffsetDateTime.now());
    }
}
