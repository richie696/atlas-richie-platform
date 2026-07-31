package cn.richie696.antivirus.grpc;

import cn.richie696.antivirus.api.SubmitLocalScanRequest;
import cn.richie696.antivirus.api.SubmitScanRequest;
import cn.richie696.antivirus.grpc.v1.AntivirusServiceGrpc;
import cn.richie696.antivirus.grpc.v1.GetScanStatusRequest;
import cn.richie696.antivirus.grpc.v1.LocalScanRequest;
import cn.richie696.antivirus.grpc.v1.ScanRequest;
import cn.richie696.antivirus.model.ScanStatus;
import cn.richie696.antivirus.service.ScanTaskService;
import cn.richie696.context.common.api.HeaderContextHolder;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.OffsetDateTime;

/**
 * gRPC AntivirusService 实现，方法签名与 REST 控制器一一对应。
 *
 * <p>Spring Boot gRPC starter 会扫描 {@code @GrpcService} 注解并注册到 gRPC Server，
 * 拦截器（鉴权/日志/指标/异常映射）由 {@code atlas-richie-component-grpc} 提供。
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class AntivirusGrpcService extends AntivirusServiceGrpc.AntivirusServiceImplBase {

    private final ScanTaskService taskService;

    @Override
    public void submitScan(ScanRequest request, StreamObserver<cn.richie696.antivirus.grpc.v1.ScanTask> responseObserver) {
        SubmitScanRequest restRequest = new SubmitScanRequest();
        restRequest.setFileId(request.getFileId());
        restRequest.setDownloadUrl(request.getDownloadUrl());
        restRequest.setFileName(request.getFileName());
        if (request.hasExpectedSize()) {
            restRequest.setExpectedSize(request.getExpectedSize());
        }
        restRequest.setExpectedEtag(request.getExpectedEtag());

        cn.richie696.antivirus.model.ScanTask task = taskService.submit(restRequest, resolveTenantId());
        responseObserver.onNext(toProto(task));
        responseObserver.onCompleted();
    }

    @Override
    public void submitLocalScan(LocalScanRequest request, StreamObserver<cn.richie696.antivirus.grpc.v1.ScanTask> responseObserver) {
        SubmitLocalScanRequest restRequest = new SubmitLocalScanRequest();
        restRequest.setLocalPath(request.getLocalPath());
        restRequest.setFileName(request.getFileName());
        if (request.hasExpectedSize()) {
            restRequest.setExpectedSize(request.getExpectedSize());
        }

        cn.richie696.antivirus.model.ScanTask task = taskService.submitLocal(restRequest, resolveTenantId());
        responseObserver.onNext(toProto(task));
        responseObserver.onCompleted();
    }

    @Override
    public void getScanStatus(GetScanStatusRequest request, StreamObserver<cn.richie696.antivirus.grpc.v1.ScanTask> responseObserver) {
        String tenantId = resolveTenantId();
        taskService.get(request.getTaskId(), tenantId)
                .ifPresentOrElse(
                        task -> {
                            responseObserver.onNext(toProto(task));
                            responseObserver.onCompleted();
                        },
                        () -> responseObserver.onError(
                                io.grpc.Status.NOT_FOUND
                                        .withDescription("scan task not found: " + request.getTaskId())
                                        .asRuntimeException()));
    }

    private cn.richie696.antivirus.grpc.v1.ScanTask toProto(cn.richie696.antivirus.model.ScanTask task) {
        return cn.richie696.antivirus.grpc.v1.ScanTask.newBuilder()
                .setTaskId(task.getTaskId() == null ? "" : task.getTaskId())
                .setFileId(task.getFileId() == null ? "" : task.getFileId())
                .setTenantId(task.getTenantId() == null ? "" : task.getTenantId())
                .setDownloadUrl(task.getDownloadUrl() == null ? "" : task.getDownloadUrl())
                .setLocalPath(task.getLocalPath() == null ? "" : task.getLocalPath())
                .setFileName(task.getFileName() == null ? "" : task.getFileName())
                .setExpectedSize(task.getExpectedSize() == null ? 0 : task.getExpectedSize())
                .setExpectedEtag(task.getExpectedEtag() == null ? "" : task.getExpectedEtag())
                .setActualSize(task.getActualSize() == null ? 0 : task.getActualSize())
                .setStatus(toProto(task.getStatus()))
                .setSha256(task.getSha256() == null ? "" : task.getSha256())
                .setDetectedMimeType(task.getDetectedMimeType() == null ? "" : task.getDetectedMimeType())
                .setThreatName(task.getThreatName() == null ? "" : task.getThreatName())
                .setEngineVersion(task.getEngineVersion() == null ? "" : task.getEngineVersion())
                .setSignatureVersion(task.getSignatureVersion() == null ? "" : task.getSignatureVersion())
                .setErrorMessage(task.getErrorMessage() == null ? "" : task.getErrorMessage())
                .setSubmittedAtEpochMs(toEpoch(task.getSubmittedAt()))
                .setStartedAtEpochMs(toEpoch(task.getStartedAt()))
                .setCompletedAtEpochMs(toEpoch(task.getCompletedAt()))
                .build();
    }

    private static long toEpoch(OffsetDateTime time) {
        return time == null ? 0 : time.toInstant().toEpochMilli();
    }

    private String resolveTenantId() {
        String tenantId = HeaderContextHolder.getHeader("X-Tenant-Id");
        if (tenantId == null) {
            tenantId = HeaderContextHolder.getHeader("x-tenant-id");
        }
        return tenantId;
    }

    private static cn.richie696.antivirus.grpc.v1.ScanStatus toProto(ScanStatus status) {
        if (status == null) {
            return cn.richie696.antivirus.grpc.v1.ScanStatus.SCAN_STATUS_UNSPECIFIED;
        }
        return switch (status) {
            case PENDING -> cn.richie696.antivirus.grpc.v1.ScanStatus.PENDING;
            case SCANNING -> cn.richie696.antivirus.grpc.v1.ScanStatus.SCANNING;
            case CLEAN -> cn.richie696.antivirus.grpc.v1.ScanStatus.CLEAN;
            case INFECTED -> cn.richie696.antivirus.grpc.v1.ScanStatus.INFECTED;
            case FAILED -> cn.richie696.antivirus.grpc.v1.ScanStatus.FAILED;
        };
    }
}