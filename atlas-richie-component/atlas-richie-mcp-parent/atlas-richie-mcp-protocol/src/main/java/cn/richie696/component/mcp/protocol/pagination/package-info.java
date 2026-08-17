/**
 * MCP 协议分页包：实现"完整性受保护的服务端分页游标"编解码。
 *
 * <p>为什么需要这个游标：传统 {@code page=1&size=20} 形式的分页 URL 会暴露业务分页
 * 状态、且无任何防篡改保护。本包用 HMAC-SHA256 对分页偏移量进行签名，使游标对外
 * 表现为不透明字符串，攻击者无法猜测或篡改偏移量；服务端解码时通过常量时间签名
 * 比较（{@link java.security.MessageDigest#isEqual}）避免时序攻击。
 *
 * <p>为什么仅承载 {@code offset}：本组件的分页策略固定为"按 offset 偏移"，
 * 不暴露游标的内部结构（排序键、过滤条件等）可以避免被反推业务实现；如有更复杂
 * 的需求，应使用 MRTR 包中的 {@link cn.richie696.component.mcp.protocol.mrtr.McpRequestState}。
 *
 * 核心类职责：
 * <ul>
 *   <li>{@link cn.richie696.component.mcp.protocol.pagination.McpCursorCodec}：
 *       提供 {@code encode(offset)} / {@code decode(cursor)} 两个对称方法，
 *       内部使用 HMAC-SHA256 签名、Base64-URL 编码、严格 round-trip 校验。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
 */
package cn.richie696.component.mcp.protocol.pagination;
