package cn.richie696.component.vector.knowledge;

import java.util.Set;

/**
 * 已由可信认证与授权系统解析完成的检索主体范围。
 *
 * <p>本类型是知识库检索 ACL 预过滤的唯一权威输入：{@link DocumentVisibility} 四档可见性
 * 的解算、{@link AccessScope} 各集合与 {@code tenantAdmin} 标志共同决定一次
 * {@link KnowledgeSearchRequest} 能命中哪些 {@code VectorRecord}。它不应由业务层自行拼装，
 * 而应在网关/拦截器阶段从登录态、Token 声明、RBAC 服务中合并而来。</p>
 *
 * <p>本类型不可变，集合字段在紧凑构造器中通过 {@link Set#copyOf} 防御性拷贝，
 * 避免上游后续改动影响下游检索时的可见性结果。所有字段在对象创建时就完成规范化，
 * 后续检索路径可以直接读取而无需再做空值判断。</p>
 *
 * <p>调用关系：
 * <ul>
 *   <li>由网关/认证拦截器创建并透传给业务层</li>
 *   <li>由 {@link DefaultKnowledgeBaseVectorService#search} 读取，用于构造
 *       {@link cn.richie696.component.vector.model.VectorFilter} 的 ACL 段</li>
 *   <li>由 {@link ActiveProjectionVersionResolver#activeVersionIds} 接收 tenantId，
 *       用于按租户 + 知识库维度解析投影版本</li>
 * </ul>
 *
 * @param tenantId      租户 ID，非空、不可包含空白字符；缺失时构造器直接抛出
 *                      {@link IllegalArgumentException}，以确保下游 filter 中
 *                      {@code tenantId} 字段始终存在有效值
 * @param departmentIds 主体所属部门 ID 集合（可空，内部规范化为不可变空集）；
 *                      与 {@code visibility=DEPARTMENT|CUSTOM} 下的
 *                      {@code allowedDepartmentIds} 做 {@code containsAny} 匹配
 * @param principalIds  主体直接 ID 集合（用户/角色/账号，可空，内部规范化为不可变空集）；
 *                      与 {@code visibility=CUSTOM|PRIVATE} 下的
 *                      {@code allowedPrincipalIds} 做 {@code containsAny} 匹配
 * @param tenantAdmin   是否为租户级管理员；为 {@code true} 时
 *                      {@link DefaultKnowledgeBaseVectorService} 将跳过 visibility 过滤，
 *                      仅保留 {@code tenantId/knowledgeBaseId/status} 三段基础断言，
 *                      实现"租户内全可见"语义
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public record AccessScope(String tenantId, Set<String> departmentIds, Set<String> principalIds, boolean tenantAdmin) {
    /**
     * 紧凑构造器：对不可变集合字段执行防御性规范化，并对 {@code tenantId} 做必填校验。
     *
     * <p>所有规范化都在创建时一次性完成，避免在检索热路径上反复做空值或拷贝判断。
     * 一旦 {@code AccessScope} 构造完成，所有下游路径都可以假定其字段是非空的不可变集合。</p>
     *
     * @throws IllegalArgumentException 当 {@code tenantId} 为 {@code null} 或全空白字符时抛出
     */
    public AccessScope {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        departmentIds = departmentIds == null ? Set.of() : Set.copyOf(departmentIds);
        principalIds = principalIds == null ? Set.of() : Set.copyOf(principalIds);
    }
}
