package com.richie.component.web.core.protection;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * AnomalyDetection 子规则：Bot UA 匹配器（README.md §4.8.2 / §4.8.3）。
 * <p>
 * <strong>glob 语法</strong>：
 * <ul>
 *   <li>{@code *} → 匹配任意字符（含空）</li>
 *   <li>{@code ?} → 匹配单字符</li>
 *   <li>其余字符按字面匹配（大小写不敏感），用 {@code \Q...\E} 包裹避免 regex 元字符问题</li>
 * </ul>
 * 编译时把 glob 转 regex，运行时一次构造 + O(1) 匹配。
 *
 * @author richie696
 * @since 2026-07
 */
public final class BotUserAgentMatcher {

    private final String pattern;
    private final Pattern regex;

    public BotUserAgentMatcher(String globPattern) {
        this.pattern = Objects.requireNonNull(globPattern, "globPattern must not be null");
        this.regex = Pattern.compile(
                globToRegex(globPattern),
                Pattern.CASE_INSENSITIVE);
    }

    public String pattern() {
        return pattern;
    }

    public boolean matches(String userAgent) {
        if (userAgent == null) {
            return false;
        }
        return regex.matcher(userAgent).matches();
    }

    static String globToRegex(String glob) {
        Objects.requireNonNull(glob, "glob must not be null");
        if (glob.isEmpty()) {
            return ".*";
        }
        StringBuilder sb = new StringBuilder(glob.length() + 16);
        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);
            if (c == '*' || c == '?') {
                sb.append(c == '*' ? ".*" : ".");
                i++;
                continue;
            }
            int start = i;
            while (i < glob.length() && glob.charAt(i) != '*' && glob.charAt(i) != '?') {
                i++;
            }
            String literal = glob.substring(start, i);
            // glob 惯例：`/path/*` 表示"目录下任意"——`*` 前 trailing `/` 不算字面
            // （`*` 是 0+ 通配，`/*` 等价于 `/` + 任意）；但 `?` 前 trailing `/` 算字面
            if (i < glob.length() && glob.charAt(i) == '*' && literal.endsWith("/")) {
                literal = literal.substring(0, literal.length() - 1);
            }
            if (!literal.isEmpty()) {
                sb.append("\\Q").append(literal).append("\\E");
            }
        }
        return sb.toString();
    }
}