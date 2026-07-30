package cn.richie696.antivirus.service;

/** Redis Stream 不可用时拒绝受理，避免向调用方返回无法被消费的任务。 */
public class ScanTaskSubmissionException extends RuntimeException {
    public ScanTaskSubmissionException(String message, Throwable cause) {
        super(message, cause);
    }
}
