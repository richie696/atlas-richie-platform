package com.richie.component.mfa.management.manager;

import com.richie.contract.exception.BusinessException;
import com.richie.component.cache.GlobalCache;
import com.richie.component.mfa.core.config.MfaProperties;
import com.richie.component.mfa.core.entity.MfaTrustedDevice;
import com.richie.component.mfa.core.support.MfaTenantSupport;
import com.richie.component.mfa.core.util.MfaKeyUtils;
import com.richie.component.mfa.management.mapper.MfaTrustedDeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;

/**
 * 可信设备管理器
 * <p>
 * 职责：管理用户的可信设备（注册、查询、撤销等）
 *
 * @author richie696
 * @since 5.0.0
 */
@SuppressWarnings("preview")
@Slf4j
@Component
@RequiredArgsConstructor
public class TrustedDeviceManager {

    /**
     * 可信设备Mapper（数据库操作）
     */
    private final MfaTrustedDeviceMapper trustedDeviceMapper;

    /**
     * MFA统一配置属性
     */
    private final MfaProperties properties;

    /**
     * 租户支持类（用于判断是否启用租户功能）
     */
    private final MfaTenantSupport tenantSupport;

    /**
     * 注册可信设备
     * <p>
     * 使用结构化并发优化：并行查询设备数量限制和设备是否存在，减少总耗时。
     * <p>
     * 性能优化说明：
     * <ul>
     *   <li>原实现：串行执行两个数据库查询，总耗时 = 查询1耗时 + 查询2耗时</li>
     *   <li>优化后：并行执行两个独立的数据库查询，总耗时 ≈ max(查询1耗时, 查询2耗时)</li>
     *   <li>预期性能提升：约50%（当两个查询耗时相同时）</li>
     * </ul>
     *
     * @param tenantId 租户ID（可为null）
     * @param userId 用户ID
     * @param deviceId 设备ID（设备指纹）
     * @param deviceName 设备名称（用于显示）
     * @param deviceFingerprint 设备指纹（原始指纹的哈希，可选，用于审计）
     * @return 注册的可信设备
     */
    @Transactional
    public MfaTrustedDevice registerTrustedDevice(String tenantId, String userId, String deviceId,
                                                    String deviceName, String deviceFingerprint) {
        // 1. 检查是否启用可信设备功能
        if (!properties.getSecurity().getTrustedDevice().isEnabled()) {
            log.warn("可信设备功能未启用，tenantId: {}, userId: {}", tenantId, userId);
            throw new BusinessException("可信设备功能未启用");
        }

        String actualTenantId = tenantSupport.isTenantEnabled() ? tenantId : null;

        try (var scope = StructuredTaskScope.open()) {
            // 并行查询：设备数量限制和设备是否存在（两个独立的数据库查询，无数据依赖）
            var deviceCountTask = scope.fork(() ->
                trustedDeviceMapper.countByTenantAndUser(actualTenantId, userId));
            var existingDeviceTask = scope.fork(() ->
                trustedDeviceMapper.selectByDeviceId(actualTenantId, userId, deviceId));

            scope.join(); // JDK 25: 全部成功则返回，任一失败则抛出 FailedException

            Long deviceCount = deviceCountTask.get();
            MfaTrustedDevice existing = existingDeviceTask.get();

            // 2. 首个设备设为主管理设备
            boolean isFirstDevice = deviceCount == null || deviceCount == 0;

            // 3. 检查设备数量限制
            int maxDevices = properties.getSecurity().getTrustedDevice().getMaxDevices();
            if (deviceCount != null && deviceCount >= maxDevices) {
                log.warn("用户可信设备数量已达上限，tenantId: {}, userId: {}, count: {}, max: {}",
                    tenantId, userId, deviceCount, maxDevices);
                throw new BusinessException("可信设备数量已达上限，最多支持%d个设备".formatted(maxDevices));
            }

            // 4. 检查设备是否已存在
            if (existing != null) {
                // 设备已存在，更新信任时间
                log.info("设备已存在，更新信任时间，tenantId: {}, userId: {}, deviceId: {}",
                    tenantId, userId, deviceId);

                existing.setTrustedUntil(calculateTrustUntil());
                existing.setLastUsedTime(LocalDateTime.now());
                if (StringUtils.isNotBlank(deviceName)) {
                    existing.setDeviceName(deviceName);
                }
                if (StringUtils.isNotBlank(deviceFingerprint)) {
                    existing.setDeviceFingerprint(deviceFingerprint);
                }

                trustedDeviceMapper.updateById(existing);

                // 同步到缓存
                syncToCache(existing);

                return existing;
            }

            // 5. 创建新设备（首个设备设为主管理设备）
            MfaTrustedDevice device = new MfaTrustedDevice();
            device.setTenantId(actualTenantId);
            device.setUserId(userId);
            device.setDeviceId(deviceId);
            device.setDeviceName(deviceName);
            device.setDeviceFingerprint(deviceFingerprint);
            device.setTrustedUntil(calculateTrustUntil());
            device.setLastUsedTime(LocalDateTime.now());
            device.setIsPrimary(isFirstDevice ? 1 : 0);

            trustedDeviceMapper.insert(device);

            log.info("可信设备注册成功，tenantId: {}, userId: {}, deviceId: {}, deviceName: {}",
                tenantId, userId, deviceId, deviceName);

            // 6. 同步到缓存
            syncToCache(device);

            return device;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("并行查询可信设备被中断，tenantId: {}, userId: {}, deviceId: {}",
                tenantId, userId, deviceId, e);
            throw new RuntimeException("注册可信设备失败", e);
        } catch (StructuredTaskScope.FailedException e) {
            log.error("并行查询可信设备执行失败，tenantId: {}, userId: {}, deviceId: {}",
                tenantId, userId, deviceId, e);
            throw new RuntimeException("注册可信设备失败", e.getCause() != null ? e.getCause() : e);
        }
    }

    /**
     * 检查设备是否可信
     * <p>
     * 检查逻辑：
     * <ol>
     *   <li>检查是否启用可信设备功能</li>
     *   <li>先从 GlobalCache 查询（缓存优先）</li>
     *   <li>如果缓存未命中，从数据库查询并同步到缓存</li>
     *   <li>检查设备信任是否有效（未过期）</li>
     * </ol>
     *
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param userId   用户ID（必填，业务系统User表的主键ID）
     * @param deviceId 设备ID（必填，设备指纹）
     * @return 设备是否可信且未过期
     * <ul>
     *   <li>{@code true}：设备可信且未过期，可以跳过 MFA 验证</li>
     *   <li>{@code false}：设备不可信、已过期或可信设备功能未启用</li>
     * </ul>
     */
    public boolean isTrustedDevice(String tenantId, String userId, String deviceId) {
        // 1. 检查是否启用可信设备功能
        if (!properties.getSecurity().getTrustedDevice().isEnabled()) {
            return false;
        }

        // 2. 先从缓存查询
        String actualTenantId = tenantSupport.isTenantEnabled() ? tenantId : null;
        String cacheKey = MfaKeyUtils.getTrustedDeviceCacheKey(
            actualTenantId, userId, deviceId, tenantSupport.isTenantEnabled());

        MfaTrustedDevice cached = GlobalCache.getObjectFromHash(cacheKey, MfaTrustedDevice.class);
        if (cached != null) {
            // 缓存命中，检查是否有效
            return isTrustValid(cached);
        }

        // 3. 缓存未命中，从数据库查询
        MfaTrustedDevice device = trustedDeviceMapper.selectByDeviceId(actualTenantId, userId, deviceId);
        if (device == null) {
            return false;
        }

        // 4. 同步到缓存
        syncToCache(device);

        // 5. 检查是否有效
        return isTrustValid(device);
    }

    /**
     * 更新设备最后使用时间
     * <p>
     * 当设备用于跳过 MFA 验证时，更新设备的最后使用时间
     * <p>
     * 执行流程：
     * <ol>
     *   <li>从数据库查询设备信息</li>
     *   <li>更新 lastUsedTime 为当前时间</li>
     *   <li>同步到 GlobalCache</li>
     * </ol>
     *
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param userId   用户ID（必填，业务系统User表的主键ID）
     * @param deviceId 设备ID（必填，设备指纹）
     */
    public void updateLastUsedTime(String tenantId, String userId, String deviceId) {
        String actualTenantId = tenantSupport.isTenantEnabled() ? tenantId : null;
        MfaTrustedDevice device = trustedDeviceMapper.selectByDeviceId(actualTenantId, userId, deviceId);

        if (device != null) {
            device.setLastUsedTime(LocalDateTime.now());
            trustedDeviceMapper.updateById(device);

            // 同步到缓存
            syncToCache(device);

            log.debug("更新设备最后使用时间，tenantId: {}, userId: {}, deviceId: {}",
                tenantId, userId, deviceId);
        }
    }

    /**
     * 查询用户的所有可信设备
     * <p>
     * 从数据库查询指定用户的所有可信设备列表（包括已过期的设备）
     *
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param userId   用户ID（必填，业务系统User表的主键ID）
     * @return 可信设备列表（包含设备ID、设备名称、信任过期时间、最后使用时间等信息）
     */
    public List<MfaTrustedDevice> listTrustedDevices(String tenantId, String userId) {
        String actualTenantId = tenantSupport.isTenantEnabled() ? tenantId : null;
        return trustedDeviceMapper.selectByTenantAndUser(actualTenantId, userId);
    }

    /**
     * 统计用户的可信设备数量
     * <p>
     * 从数据库统计指定用户的可信设备数量（包括已过期的设备）
     *
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param userId   用户ID（必填，业务系统User表的主键ID）
     * @return 可信设备数量（如果查询失败或用户不存在，返回 0）
     */
    public int countTrustedDevices(String tenantId, String userId) {
        String actualTenantId = tenantSupport.isTenantEnabled() ? tenantId : null;
        Long count = trustedDeviceMapper.countByTenantAndUser(actualTenantId, userId);
        return count != null ? count.intValue() : 0;
    }

    /**
     * 撤销可信设备（仅主管理设备可执行）
     * <p>
     * 删除指定用户的指定可信设备（逻辑删除），并清除缓存。
     * 仅当 currentDeviceId 为该用户的主管理设备时允许撤销；若撤销的是主设备，则将剩余第一台设为主设备。
     *
     * @param tenantId        租户ID（可选）
     * @param userId          用户ID
     * @param deviceId        要撤销的设备ID
     * @param currentDeviceId 当前请求设备ID（须为主管理设备）
     * @throws BusinessException 设备不存在或当前设备非主管理设备
     */
    @Transactional
    public void revokeTrustedDevice(String tenantId, String userId, String deviceId, String currentDeviceId) {
        String actualTenantId = tenantSupport.isTenantEnabled() ? tenantId : null;
        requirePrimaryDevice(actualTenantId, userId, currentDeviceId);

        MfaTrustedDevice device = trustedDeviceMapper.selectByDeviceId(actualTenantId, userId, deviceId);
        if (device == null) {
            log.warn("可信设备不存在，无法撤销，tenantId: {}, userId: {}, deviceId: {}",
                tenantId, userId, deviceId);
            throw new BusinessException("可信设备不存在");
        }

        boolean revokingPrimary = Integer.valueOf(1).equals(device.getIsPrimary());

        trustedDeviceMapper.deleteById(device.getId());
        removeFromCache(actualTenantId, userId, deviceId);

        if (revokingPrimary) {
            List<MfaTrustedDevice> remaining = trustedDeviceMapper.selectByTenantAndUser(actualTenantId, userId);
            if (!remaining.isEmpty()) {
                MfaTrustedDevice next = remaining.get(0);
                trustedDeviceMapper.clearPrimaryByTenantAndUser(actualTenantId, userId);
                trustedDeviceMapper.setPrimaryByTenantAndUserAndDevice(actualTenantId, userId, next.getDeviceId());
                next.setIsPrimary(1);
                syncToCache(next);
            }
        }

        log.info("可信设备撤销成功，tenantId: {}, userId: {}, deviceId: {}",
            tenantId, userId, deviceId);
    }

    /**
     * 撤销用户的所有可信设备（仅主管理设备可执行）
     *
     * @param tenantId        租户ID（可选）
     * @param userId          用户ID
     * @param currentDeviceId 当前请求设备ID（须为主管理设备）
     * @return 撤销的设备数量
     */
    @Transactional
    public int revokeAllTrustedDevices(String tenantId, String userId, String currentDeviceId) {
        String actualTenantId = tenantSupport.isTenantEnabled() ? tenantId : null;
        requirePrimaryDevice(actualTenantId, userId, currentDeviceId);

        List<MfaTrustedDevice> devices = trustedDeviceMapper.selectByTenantAndUser(actualTenantId, userId);
        if (devices.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (MfaTrustedDevice device : devices) {
            trustedDeviceMapper.deleteById(device.getId());
            removeFromCache(actualTenantId, userId, device.getDeviceId());
            count++;
        }

        log.info("撤销所有可信设备成功，tenantId: {}, userId: {}, count: {}",
            tenantId, userId, count);
        return count;
    }

    /**
     * 设置主管理设备
     * <p>
     * 允许条件：当前设备为主管理设备；或当前无主设备时，当前设备为任意一台可信设备（便于主设备被撤销后由其余设备重新指定主设备）。
     *
     * @param tenantId          租户ID（可选）
     * @param userId            用户ID
     * @param newPrimaryDeviceId 要设为主设备的设备ID
     * @param currentDeviceId   当前请求设备ID（主设备或当无主设备时为任意可信设备）
     */
    @Transactional
    public void setPrimaryDevice(String tenantId, String userId, String newPrimaryDeviceId, String currentDeviceId) {
        String actualTenantId = tenantSupport.isTenantEnabled() ? tenantId : null;
        requireCanSetPrimary(actualTenantId, userId, currentDeviceId);

        MfaTrustedDevice target = trustedDeviceMapper.selectByDeviceId(actualTenantId, userId, newPrimaryDeviceId);
        if (target == null) {
            throw new BusinessException("目标可信设备不存在");
        }

        trustedDeviceMapper.clearPrimaryByTenantAndUser(actualTenantId, userId);
        trustedDeviceMapper.setPrimaryByTenantAndUserAndDevice(actualTenantId, userId, newPrimaryDeviceId);
        target.setIsPrimary(1);
        syncToCache(target);

        log.info("已设置主管理设备，tenantId: {}, userId: {}, deviceId: {}", tenantId, userId, newPrimaryDeviceId);
    }

    /**
     * 获取用户的主管理设备（用于判断当前请求设备是否为主设备）
     *
     * @param tenantId 租户ID（可选）
     * @param userId   用户ID
     * @return 主设备，若无则 null
     */
    public MfaTrustedDevice getPrimaryDevice(String tenantId, String userId) {
        String actualTenantId = tenantSupport.isTenantEnabled() ? tenantId : null;
        return trustedDeviceMapper.selectPrimaryByTenantAndUser(actualTenantId, userId);
    }

    private void requirePrimaryDevice(String tenantId, String userId, String currentDeviceId) {
        if (StringUtils.isBlank(currentDeviceId)) {
            throw new BusinessException("当前设备ID不能为空，仅主管理设备可执行此操作");
        }
        MfaTrustedDevice primary = trustedDeviceMapper.selectPrimaryByTenantAndUser(tenantId, userId);
        if (primary == null || !currentDeviceId.equals(primary.getDeviceId())) {
            throw new BusinessException("仅主管理设备可执行此操作");
        }
    }

    /**
     * 校验当前设备是否有权执行「设为主设备」：当前为主设备，或当前无主设备且当前设备为可信设备之一。
     */
    private void requireCanSetPrimary(String tenantId, String userId, String currentDeviceId) {
        if (StringUtils.isBlank(currentDeviceId)) {
            throw new BusinessException("当前设备ID不能为空");
        }
        MfaTrustedDevice primary = trustedDeviceMapper.selectPrimaryByTenantAndUser(tenantId, userId);
        if (primary != null && currentDeviceId.equals(primary.getDeviceId())) {
            return;
        }
        if (primary == null) {
            MfaTrustedDevice current = trustedDeviceMapper.selectByDeviceId(tenantId, userId, currentDeviceId);
            if (current != null) {
                return;
            }
        }
        throw new BusinessException("仅主管理设备可执行此操作；若无主设备，请从可信设备列表中选择一台设为主设备");
    }

    /**
     * 计算信任过期时间
     * <p>
     * 根据配置的默认信任天数，计算新注册的可信设备的信任过期时间
     *
     * @return 信任过期时间（当前时间 + 默认信任天数）
     */
    private LocalDateTime calculateTrustUntil() {
        int days = properties.getSecurity().getTrustedDevice().getDefaultTrustDays();
        return LocalDateTime.now().plusDays(days);
    }

    /**
     * 检查设备信任是否有效
     * <p>
     * 检查设备的信任过期时间是否在当前时间之后
     *
     * @param device 可信设备（必填）
     * @return 设备信任是否有效
     * <ul>
     *   <li>{@code true}：设备信任有效（未过期）</li>
     *   <li>{@code false}：设备信任已过期或设备为null</li>
     * </ul>
     */
    private boolean isTrustValid(MfaTrustedDevice device) {
        if (device == null || device.getTrustedUntil() == null) {
            return false;
        }
        return device.getTrustedUntil().isAfter(LocalDateTime.now());
    }

    /**
     * 同步设备信息到缓存
     * <p>
     * 将可信设备信息同步到 GlobalCache，并维护设备ID列表
     * <p>
     * 执行流程：
     * <ol>
     *   <li>计算TTL（信任过期时间 - 当前时间）</li>
     *   <li>如果TTL > 0，同步设备信息到缓存（Hash结构）</li>
     *   <li>同步设备ID到列表（Set结构，供 validation 模块查询设备数量）</li>
     *   <li>如果TTL <= 0，从缓存删除设备信息</li>
     * </ol>
     *
     * @param device 可信设备（必填）
     */
    private void syncToCache(MfaTrustedDevice device) {
        String cacheKey = MfaKeyUtils.getTrustedDeviceCacheKey(
            device.getTenantId(),
            device.getUserId(),
            device.getDeviceId(),
            tenantSupport.isTenantEnabled()
        );

        // 计算TTL（信任过期时间 - 当前时间）
        long ttl = Duration.between(LocalDateTime.now(), device.getTrustedUntil()).toMillis();
        if (ttl > 0) {
            GlobalCache.addObjectToHash(cacheKey, device, ttl);
            log.debug("可信设备信息同步到缓存成功，tenantId: {}, userId: {}, deviceId: {}",
                device.getTenantId(), device.getUserId(), device.getDeviceId());

            // 同步维护设备ID列表（供 validation 模块查询设备数量）
            syncDeviceIdToList(device);
        } else {
            // 已过期，从缓存删除
            removeFromCache(device.getTenantId(), device.getUserId(), device.getDeviceId());
        }
    }

    /**
     * 从缓存删除设备信息
     * <p>
     * 从 GlobalCache 删除设备信息和设备ID列表
     * <p>
     * 执行流程：
     * <ol>
     *   <li>删除设备信息（Hash结构）</li>
     *   <li>从设备ID列表中移除设备ID（Set结构）</li>
     * </ol>
     *
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param userId   用户ID（必填，业务系统User表的主键ID）
     * @param deviceId 设备ID（必填，设备指纹）
     */
    private void removeFromCache(String tenantId, String userId, String deviceId) {
        String cacheKey = MfaKeyUtils.getTrustedDeviceCacheKey(
            tenantId, userId, deviceId, tenantSupport.isTenantEnabled());
        GlobalCache.removeCache(cacheKey);
        log.debug("可信设备信息从缓存删除成功，tenantId: {}, userId: {}, deviceId: {}",
            tenantId, userId, deviceId);

        // 从设备ID列表中移除
        removeDeviceIdFromList(tenantId, userId, deviceId);
    }

    /**
     * 同步设备ID到列表（供 validation 模块查询设备数量）
     * <p>
     * 将设备ID添加到用户的设备ID列表（Set结构），供 validation 模块快速统计设备数量
     * <p>
     * 注意：此方法不会刷新 Set 的 TTL，Set 的 TTL 由首次创建时设置。
     * 设备ID的有效性由单个设备缓存的 TTL 和 trustedUntil 字段控制。
     *
     * @param device 可信设备（必填）
     */
    private void syncDeviceIdToList(MfaTrustedDevice device) {
        String listKey = MfaKeyUtils.getTrustedDeviceListKey(
            device.getTenantId(), device.getUserId(), tenantSupport.isTenantEnabled());

        // 计算TTL（信任过期时间 - 当前时间）
        long ttl = Duration.between(LocalDateTime.now(), device.getTrustedUntil()).toMillis();
        if (ttl > 0) {
            GlobalCache.addSetItem(listKey, device.getDeviceId());
            log.debug("设备ID已添加到列表，tenantId: {}, userId: {}, deviceId: {}",
                device.getTenantId(), device.getUserId(), device.getDeviceId());
        }
    }

    /**
     * 从设备ID列表中移除
     * <p>
     * 从用户的设备ID列表（Set结构）中移除指定设备ID
     *
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param userId   用户ID（必填，业务系统User表的主键ID）
     * @param deviceId 设备ID（必填，设备指纹）
     */
    private void removeDeviceIdFromList(String tenantId, String userId, String deviceId) {
        String listKey = MfaKeyUtils.getTrustedDeviceListKey(
            tenantId, userId, tenantSupport.isTenantEnabled());
        GlobalCache.removeSetItem(listKey, deviceId);
        log.debug("设备ID已从列表移除，tenantId: {}, userId: {}, deviceId: {}",
            tenantId, userId, deviceId);
    }
}
