/**
 * MCP 协议 MRTR（Multi-Request Token Relay）状态包：实现跨多步交互请求的不可伪造、不透明状态令牌。
 *
 * <p>为什么需要 MRTR：在 MCP 的"多步工具调用 / 跨域恢复"等场景中，业务需要把第一阶段
 * 的入参、主体指纹、目标方法、过期时间等绑定在一起传递给第二阶段，但又不能允许客户端
 * 篡改。本包用 HMAC-SHA256 提供一个签名版的"半不透明"令牌：服务器可以验证签名和过期，
 * 但不解析 payload（payload 是任意字符串）。</p>
 *
 * 关键安全设计：
 * <ul>
 *   <li>签名使用常量时间比较（{@link java.security.MessageDigest#isEqual}）避免时序攻击。</li>
 *   <li>secret 至少 32 字节，构造期强校验以防误用弱密钥。</li>
 *   <li>主体指纹（principalFingerprint）参与校验，防止令牌被其他会话劫持复用。</li>
 *   <li>目标方法（method）参与校验，防止令牌被同主体的其他方法滥用。</li>
 *   <li>nonce 防止重放：每次 {@code protect} 调用都生成新 UUID。</li>
 * </ul>
 *
 * 核心类职责：
 * <ul>
 *   <li>{@link cn.richie696.component.mcp.protocol.mrtr.McpRequestState}：
 *       不可变状态记录（payload/principal/method/expiresAt/nonce）。</li>
 *   <li>{@link cn.richie696.component.mcp.protocol.mrtr.McpRequestStateCodec}：
 *       签发与验证令牌；payload 本身不解析，只承担不透明载体职责。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
 */
package cn.richie696.component.mcp.protocol.mrtr;
