package com.richie.component.mfa.controller;

import com.richie.contract.model.ApiResult;
import com.richie.component.mfa.domain.UserInfo;
import com.richie.component.mfa.dto.ChangePasswordDTO;
import com.richie.component.mfa.dto.LoginDTO;
import com.richie.component.mfa.dto.LoginUserPrincipal;
import com.richie.component.mfa.management.dto.LoginMfaCheckResult;
import com.richie.component.mfa.management.manager.MfaBindManager;
import com.richie.component.mfa.service.UserInfoService;
import com.richie.component.mfa.validation.dto.MfaValidationResult;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 简单登录示例接口
 * <p>
 * 说明：
 * 本示例仅演示如何在接入 MFA 管理组件的同时，提供一个基础登录接口。
 * 实际业务中，请根据自身用户体系实现用户名 / 密码 / MFA 校验逻辑。
 *
 * @author richie696
 * @since 2026-01-29
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserInfoService userInfoService;
    private final MfaBindManager mfaBindManager;

    /**
     * 用户注册示例接口
     */
    @PostMapping("/register")
    public ApiResult<Void> register(@RequestBody LoginDTO request) {
        if (request == null
                || StringUtils.isBlank(request.getUsername())
                || StringUtils.isBlank(request.getPassword())) {
            return ApiResult.error("用户名或密码不能为空");
        }

        UserInfo existing = userInfoService.findByUsername(request.getUsername());
        if (existing != null) {
            return ApiResult.error("用户名已存在");
        }

        UserInfo user = new UserInfo();
        user.setUsername(request.getUsername());
        // 示例中直接存储明文摘要，实际项目请使用更安全的加密算法（如 BCrypt）
        user.setPasswordHash(request.getPassword());
        userInfoService.save(user);

        return ApiResult.success();
    }

    /**
     * 登录示例接口（基于 user_info 表）
     * <p>
     * 在判断 MFA 是否必填之前，先根据请求头中的设备 ID（及可选硬件指纹）校验是否为可信设备；
     * 若是可信设备且未过期，则直接放行，不要求 MFA 验证。
     *
     * @param request              登录请求参数
     * @param deviceId             设备 ID（请求头 X-Device-Id，可选）
     * @return 登录结果
     */
    @PostMapping("/login")
    public ApiResult<LoginUserPrincipal> login(
            @RequestBody LoginDTO request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        if (request == null
                || StringUtils.isBlank(request.getUsername())
                || StringUtils.isBlank(request.getPassword())) {
            return ApiResult.error("用户名或密码不能为空");
        }

        UserInfo user = userInfoService.findByUsername(request.getUsername());
        if (user == null || !Strings.CS.equals(user.getPasswordHash(), request.getPassword())) {
            return ApiResult.error("用户名或密码错误");
        }

        String userId = String.valueOf(user.getId());
        String tenantId = request.getTenantId();

        // 检查是否需要MFA验证
        LoginMfaCheckResult mfaCheck = mfaBindManager.checkLoginMfa(tenantId, userId, deviceId);

        // 如果需要MFA验证，但未提供MFA验证码，则返回错误信息
        if (mfaCheck.isMfaRequired() && StringUtils.isBlank(request.getMfaCode())) {
            LoginUserPrincipal response = new LoginUserPrincipal();
            response.setUserId(user.getId());
            response.setUsername(user.getUsername());
            response.setMfaBound(true);
            // 不设置 accessToken，表示需要MFA验证后才能获取token
            ApiResult<LoginUserPrincipal> result = ApiResult.error("请输入MFA验证码");
            result.setData(response);
            return result;
        }

        // 如果需要MFA验证，且提供了MFA验证码，则进行MFA验证
        if (mfaCheck.isMfaRequired() && mfaCheck.isMfaBound()) {
            MfaValidationResult validationResult = mfaBindManager.verifyMfaCode(userId, tenantId, request.getMfaCode());
            // 验证未通过
            if (!validationResult.isSuccess()) {
                // MFA验证失败，返回错误信息
                LoginUserPrincipal errorResponse = new LoginUserPrincipal();
                errorResponse.setUserId(user.getId());
                errorResponse.setUsername(user.getUsername());
                errorResponse.setMfaBound(true);
                ApiResult<LoginUserPrincipal> errorResult = ApiResult.error(validationResult.getErrorMessage());
                errorResult.setData(errorResponse);
                return errorResult;
            }
        }

        LoginUserPrincipal response = new LoginUserPrincipal();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setAccessToken("mock-token-for-demo");
        response.setMfaBound(mfaCheck.isMfaBound());

        return ApiResult.success(response);
    }

    /**
     * 修改密码示例接口
     */
    @PostMapping("/change-password")
    public ApiResult<Void> changePassword(@RequestBody ChangePasswordDTO request) {
        if (request == null
                || StringUtils.isBlank(request.getUsername())
                || StringUtils.isBlank(request.getOldPassword())
                || StringUtils.isBlank(request.getNewPassword())) {
            return ApiResult.error("用户名、旧密码和新密码不能为空");
        }

        UserInfo user = userInfoService.findByUsername(request.getUsername());
        if (user == null || !Strings.CS.equals(user.getPasswordHash(), request.getOldPassword())) {
            return ApiResult.error("用户名或旧密码不正确");
        }

        user.setPasswordHash(request.getNewPassword());
        userInfoService.updateById(user);

        return ApiResult.success();
    }
}

