package com.richie.component.threadpool.domain;

import com.richie.component.i18n.annotation.I18nDict;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * API信息表域对象
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-09 12:56:41
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(title = "api_info对象", description = "API信息表")
@TableName(value = "api_info", autoResultMap = true)
public class ApiInfo extends BaseInfo {

    /**
     * API信息ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(title = "API信息ID")
    private Long id;

    /**
     * API名称
     */
    @I18nDict
    @TableField("name")
    @Schema(title = "API名称")
    private String name;

    /**
     * 角色信息
     */
    @TableField(value = "role", typeHandler = JacksonTypeHandler.class)
    @Schema(title = "角色信息")
    private RoleInfo role;

    /**
     * 权限信息列表
     */
    @TableField(value = "permissions", typeHandler = JacksonTypeHandler.class)
    @Schema(title = "权限信息列表")
    private List<PermissionInfo> permissions;

    /**
     * 角色信息
     *
     * @author richie696
     * @version 1.0
     * @since 2022-10-09 13:01:47
     */
    @Data
    @Schema(title = "角色信息", description = "角色信息")
    public static class RoleInfo {

        /**
         * 角色ID
         */
        @Schema(title = "角色ID", type = "java.lang.Long", requiredMode = Schema.RequiredMode.REQUIRED, accessMode = Schema.AccessMode.READ_ONLY)
        private Long id;

        /**
         * 角色名称
         */
        @I18nDict
        @Schema(title = "角色名称", type = "java.lang.String", requiredMode = Schema.RequiredMode.REQUIRED, accessMode = Schema.AccessMode.READ_ONLY)
        private String name;

    }

    /**
     * 权限信息
     *
     * @author richie696
     * @version 1.0
     * @since 2022-10-09 13:02:01
     */
    @Data
    @Schema(title = "权限信息", description = "权限信息描述")
    public static class PermissionInfo {

        /**
         * 权限ID
         */
        @Schema(title = "权限ID", type = "java.lang.Long", requiredMode = Schema.RequiredMode.REQUIRED, accessMode = Schema.AccessMode.READ_ONLY)
        private Long id;

        /**
         * 权限名称
         */
        @Schema(title = "权限名称", type = "java.lang.String", requiredMode = Schema.RequiredMode.REQUIRED, accessMode = Schema.AccessMode.READ_ONLY)
        private String name;

    }
}
