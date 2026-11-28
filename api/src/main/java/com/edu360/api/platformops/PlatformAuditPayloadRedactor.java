package com.edu360.api.platformops;

import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/** Redacts sensitive audit payload values at every nesting level before they leave the API. */
public final class PlatformAuditPayloadRedactor {
    private static final Set<String> SENSITIVE = Set.of("password", "secret", "token", "nationalid", "salary");

    private PlatformAuditPayloadRedactor() { }

    public static JsonNode redact(JsonNode source) {
        if (source == null || source.isNull() || source.isValueNode()) {
            return source == null ? null : source.deepCopy();
        }
        if (source.isObject()) {
            ObjectNode safe = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            Iterator<java.util.Map.Entry<String, JsonNode>> fields = source.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                safe.set(field.getKey(), isSensitive(field.getKey())
                        ? TextNode.valueOf("***") : redact(field.getValue()));
            }
            return safe;
        }
        if (source.isArray()) {
            ArrayNode safe = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            source.forEach(item -> safe.add(redact(item)));
            return safe;
        }
        return source.deepCopy();
    }

    private static boolean isSensitive(String field) {
        if (field == null) return false;
        String normalized = field.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
        return SENSITIVE.stream().anyMatch(normalized::contains);
    }
}
