package cn.richie696.antivirus.controller;

import cn.richie696.antivirus.api.ApiResponse;
import cn.richie696.antivirus.api.ScanTaskResponse;
import cn.richie696.antivirus.api.SubmitLocalScanRequest;
import cn.richie696.antivirus.api.SubmitScanRequest;
import cn.richie696.antivirus.service.ScanTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 仅供经过 Gateway 鉴权的内部业务服务调用。 */
@RestController
@RequestMapping("/internal/v1/scans")
@RequiredArgsConstructor
public class ScanTaskController {
    private final ScanTaskService taskService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<ScanTaskResponse> submit(@Valid @RequestBody SubmitScanRequest request,
                                                @RequestHeader("X-Tenant-Id") String tenantId) {
        return ApiResponse.success(ScanTaskResponse.from(taskService.submit(request, tenantId)));
    }

    @PostMapping("/local")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<ScanTaskResponse> submitLocal(@Valid @RequestBody SubmitLocalScanRequest request,
                                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        return ApiResponse.success(ScanTaskResponse.from(taskService.submitLocal(request, tenantId)));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<ScanTaskResponse> get(@PathVariable String taskId,
                                             @RequestHeader("X-Tenant-Id") String tenantId) {
        return taskService.get(taskId, tenantId)
                .map(ScanTaskResponse::from)
                .<ApiResponse<ScanTaskResponse>>map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.failure("无此任务"));
    }
}
