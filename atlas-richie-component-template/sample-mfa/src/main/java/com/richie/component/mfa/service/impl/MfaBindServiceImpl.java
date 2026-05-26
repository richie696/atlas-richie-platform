package com.richie.component.mfa.service.impl;

import com.richie.component.mfa.management.dto.MfaActivateRequest;
import com.richie.component.mfa.management.dto.MfaBindRequest;
import com.richie.component.mfa.management.dto.MfaBindResponse;
import com.richie.component.mfa.management.dto.MfaBindResult;
import com.richie.component.mfa.management.dto.MfaStatusResponse;
import com.richie.component.mfa.management.dto.MfaUnbindRequest;
import com.richie.component.mfa.management.manager.MfaBindManager;
import com.richie.component.mfa.management.manager.MfaStatusManager;
import com.richie.component.mfa.core.support.MfaTenantSupport;
import com.richie.component.mfa.service.MfaBindService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * MFA 绑定服务实现，委托 richie-component-mfa-management 的 MfaBindManager / MfaStatusManager
 */
@Service
@RequiredArgsConstructor
public class MfaBindServiceImpl implements MfaBindService {

    private final MfaBindManager bindManager;
    private final MfaStatusManager statusManager;
    private final MfaTenantSupport tenantSupport;

    @Override
    public MfaBindResponse bind(MfaBindRequest request) {
        String tenantId = tenantSupport.isTenantEnabled() ? request.getTenantId() : null;
        String deviceType = StringUtils.isNotBlank(request.getDeviceType()) ? request.getDeviceType() : "TOTP";

        MfaBindResult result = bindManager.bindDevice(
                tenantId,
                request.getUserId(),
                deviceType
        );

        MfaBindResponse response = new MfaBindResponse();
        response.setQrCodeUrl(result.getQrCodeUrl());
        response.setSecretKey(result.getSecretKey());
        response.setBackupCodes(result.getBackupCodes());
        response.setExpiresIn(result.getExpiresIn());
        return response;
    }


    @Override
    public boolean activate(MfaActivateRequest request) {
        String tenantId = tenantSupport.isTenantEnabled() ? request.getTenantId() : null;

        return bindManager.activateDevice(
                tenantId,
                request.getUserId(),
                request.getCode(),
                request.getDeviceId(),
                request.getDeviceName(),
                request.getDeviceFingerprint(),
                Boolean.TRUE.equals(request.getTrustDevice())
        );
    }

    @Override
    public MfaStatusResponse getStatus(String userId, String tenantId) {
        String actualTenantId = tenantSupport.isTenantEnabled() ? tenantId : null;
        return statusManager.getStatus(actualTenantId, userId);
    }

    @Override
    public boolean unbind(MfaUnbindRequest request) {
        String tenantId = tenantSupport.isTenantEnabled() ? request.getTenantId() : null;
        return bindManager.unbindDevice(tenantId, request.getUserId());
    }
}
