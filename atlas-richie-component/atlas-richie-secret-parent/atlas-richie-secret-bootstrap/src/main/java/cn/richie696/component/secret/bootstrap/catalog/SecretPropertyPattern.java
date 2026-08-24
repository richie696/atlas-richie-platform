/* Copyright (c) 2026 Richie (https://www.github.com/richie696) */
package cn.richie696.component.secret.bootstrap.catalog;

import org.springframework.boot.context.properties.source.ConfigurationPropertyName;

import java.util.regex.Pattern;

/** Binding Catalog 中受约束的动态 Map/集合属性模式。 */
final class SecretPropertyPattern {
    private static final String NAME = "{name}";
    private static final String INDEX = "{index}";

    private final String source;
    private final Pattern regex;

    private SecretPropertyPattern(String source, Pattern regex) {
        this.source = source;
        this.regex = regex;
    }

    static SecretPropertyPattern compile(String source) {
        if (source == null || source.isBlank()) return null;
        boolean dynamic = source.contains("{");
        if (!dynamic) {
            return ConfigurationPropertyName.isValid(source)
                    ? new SecretPropertyPattern(source, Pattern.compile(Pattern.quote(source))) : null;
        }
        String sample = source.replace(NAME, "sample").replace(INDEX, "0");
        if (sample.contains("{") || sample.contains("}") || !ConfigurationPropertyName.isValid(sample)) return null;

        StringBuilder expression = new StringBuilder("^");
        int cursor = 0;
        while (cursor < source.length()) {
            int name = source.indexOf(NAME, cursor);
            int index = source.indexOf(INDEX, cursor);
            int next;
            String token;
            if (name >= 0 && (index < 0 || name < index)) {
                next = name;
                token = NAME;
            } else if (index >= 0) {
                next = index;
                token = INDEX;
            } else {
                expression.append(Pattern.quote(source.substring(cursor)));
                break;
            }
            expression.append(Pattern.quote(source.substring(cursor, next)));
            expression.append(NAME.equals(token) ? "[a-z0-9](?:[a-z0-9-]*[a-z0-9])?" : "[0-9]+");
            cursor = next + token.length();
        }
        expression.append('$');
        return new SecretPropertyPattern(source, Pattern.compile(expression.toString()));
    }

    boolean dynamic() { return source.contains("{"); }
    boolean matches(String property) {
        return property != null && ConfigurationPropertyName.isValid(property) && regex.matcher(property).matches();
    }
}
