package cn.iocoder.yudao.module.campus.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

/** Produces deterministic JSON and a SHA-256 request digest, excluding trace metadata. */
public class RequestCanonicalizer {

    private final ObjectMapper objectMapper;

    public RequestCanonicalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();
    }

    public CanonicalRequest canonicalize(Object request) {
        try {
            JsonNode normalized = normalize(objectMapper.valueToTree(request));
            String json = objectMapper.writeValueAsString(normalized);
            return new CanonicalRequest(json, sha256(json));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("request cannot be canonicalized", exception);
        }
    }

    private JsonNode normalize(JsonNode node) {
        if (node == null || node.isNull() || node.isValueNode()) {
            return node;
        }
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            node.forEach(child -> result.add(normalize(child)));
            return result;
        }
        ObjectNode result = objectMapper.createObjectNode();
        ArrayList<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
        node.fields().forEachRemaining(field -> {
            // Trace identifiers belong to transport metadata, not command semantics. Exclude the
            // exact field name recursively, including objects nested in arrays.
            if (!"traceId".equals(field.getKey())) {
                fields.add(field);
            }
        });
        fields.sort(Comparator.comparing(Map.Entry::getKey));
        for (Map.Entry<String, JsonNode> field : fields) {
            result.set(field.getKey(), normalize(field.getValue()));
        }
        return result;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }

    public record CanonicalRequest(String json, String hash) {
    }
}
