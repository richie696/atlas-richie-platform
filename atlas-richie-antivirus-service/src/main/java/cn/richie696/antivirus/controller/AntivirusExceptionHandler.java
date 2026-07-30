package cn.richie696.antivirus.controller;

import cn.richie696.antivirus.api.ApiResponse;
import cn.richie696.antivirus.service.ScanTaskSubmissionException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AntivirusExceptionHandler {
    @ExceptionHandler(ScanTaskSubmissionException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ApiResponse<Void> taskSubmissionFailed(ScanTaskSubmissionException exception) {
        return ApiResponse.failure(exception.getMessage());
    }
}
