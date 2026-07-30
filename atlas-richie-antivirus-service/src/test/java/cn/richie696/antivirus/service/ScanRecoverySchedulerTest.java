package cn.richie696.antivirus.service;

import cn.richie696.antivirus.config.AntivirusProperties;
import cn.richie696.antivirus.model.ScanStatus;
import cn.richie696.antivirus.model.ScanTask;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ScanRecoverySchedulerTest {

    @Test
    void republishesNonTerminalTaskThenConditionallyRemovesOldMarker() {
        ScanTask task = task("task-1", ScanStatus.SCANNING);
        FakeRepository repository = new FakeRepository(task);
        FakeLeaseManager leaseManager = new FakeLeaseManager("task-1");
        FakePublisher publisher = new FakePublisher(false);
        ScanRecoveryScheduler scheduler = new ScanRecoveryScheduler(repository, leaseManager, publisher);

        scheduler.recoverExpiredTasks();

        assertThat(publisher.publishedTaskId).isEqualTo("task-1");
        assertThat(leaseManager.conditionallyRemovedTaskId).isEqualTo("task-1");
        assertThat(leaseManager.discardedTaskId).isNull();
    }

    @Test
    void keepsRecoveryMarkerWhenRepublishFails() {
        ScanTask task = task("task-2", ScanStatus.SCANNING);
        FakeLeaseManager leaseManager = new FakeLeaseManager("task-2");
        ScanRecoveryScheduler scheduler = new ScanRecoveryScheduler(
                new FakeRepository(task),
                leaseManager,
                new FakePublisher(true));

        scheduler.recoverExpiredTasks();

        assertThat(leaseManager.conditionallyRemovedTaskId).isNull();
        assertThat(leaseManager.discardedTaskId).isNull();
    }

    @Test
    void discardsRecoveryMarkerForTerminalTask() {
        ScanTask task = task("task-3", ScanStatus.CLEAN);
        FakeLeaseManager leaseManager = new FakeLeaseManager("task-3");
        FakePublisher publisher = new FakePublisher(false);
        ScanRecoveryScheduler scheduler = new ScanRecoveryScheduler(
                new FakeRepository(task),
                leaseManager,
                publisher);

        scheduler.recoverExpiredTasks();

        assertThat(leaseManager.discardedTaskId).isEqualTo("task-3");
        assertThat(publisher.publishedTaskId).isNull();
    }

    private static ScanTask task(String taskId, ScanStatus status) {
        ScanTask task = new ScanTask();
        task.setTaskId(taskId);
        task.setStatus(status);
        return task;
    }

    private static final class FakeRepository extends ScanTaskRepository {
        private final ScanTask task;

        private FakeRepository(ScanTask task) {
            super(new AntivirusProperties());
            this.task = task;
        }

        @Override
        public Optional<ScanTask> find(String taskId) {
            return Optional.ofNullable(task);
        }
    }

    private static final class FakeLeaseManager extends ScanLeaseManager {
        private final String dueTaskId;
        private String conditionallyRemovedTaskId;
        private String discardedTaskId;

        private FakeLeaseManager(String dueTaskId) {
            super(new AntivirusProperties());
            this.dueTaskId = dueTaskId;
        }

        @Override
        public Set<String> findDueTaskIds() {
            return Set.of(dueTaskId);
        }

        @Override
        public void removeExpiredMarkerIfNoLease(String taskId) {
            conditionallyRemovedTaskId = taskId;
        }

        @Override
        public void discardRecovery(String taskId) {
            discardedTaskId = taskId;
        }
    }

    private static final class FakePublisher extends ScanEventPublisher {
        private final boolean fail;
        private String publishedTaskId;

        private FakePublisher(boolean fail) {
            super(new AntivirusProperties());
            this.fail = fail;
        }

        @Override
        public void publish(String taskId) {
            if (fail) {
                throw new IllegalStateException("redis unavailable");
            }
            publishedTaskId = taskId;
        }
    }
}
