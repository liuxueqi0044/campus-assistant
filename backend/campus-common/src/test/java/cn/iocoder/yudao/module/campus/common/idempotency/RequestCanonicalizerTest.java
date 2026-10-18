package cn.iocoder.yudao.module.campus.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestCanonicalizerTest {

    private final RequestCanonicalizer canonicalizer = new RequestCanonicalizer(new ObjectMapper());

    @Test
    void sortsObjectFieldsButKeepsArrayOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("z", 1);
        first.put("a", Map.of("y", true, "b", 2));
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", Map.of("b", 2, "y", true));
        second.put("z", 1);

        RequestCanonicalizer.CanonicalRequest left = canonicalizer.canonicalize(first);
        RequestCanonicalizer.CanonicalRequest right = canonicalizer.canonicalize(second);

        assertThat(left.json()).isEqualTo("{\"a\":{\"b\":2,\"y\":true},\"z\":1}");
        assertThat(left.hash()).isEqualTo(right.hash());
    }

    @Test
    void commandRejectsOversizedActionAndRawKey() {
        assertThatThrownBy(() -> new IdempotentCommand(1, 1, "x".repeat(129), "key", Map.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotentCommand(1, 1, "action", "x".repeat(257), Map.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void excludesTraceIdMetadataRecursivelyButKeepsOtherDifferences() {
        Map<String, Object> first = Map.of(
                "amount", 10,
                "traceId", "trace-a",
                "nested", Map.of("traceId", "nested-a", "items", new Object[]{
                        Map.of("traceId", "array-a", "sku", "A")
                }));
        Map<String, Object> differentTrace = Map.of(
                "amount", 10,
                "traceId", "trace-b",
                "nested", Map.of("traceId", "nested-b", "items", new Object[]{
                        Map.of("traceId", "array-b", "sku", "A")
                }));
        Map<String, Object> differentSemanticField = Map.of(
                "amount", 11,
                "traceId", "trace-c",
                "nested", Map.of("traceId", "nested-c", "items", new Object[]{
                        Map.of("traceId", "array-c", "sku", "A")
                }));

        RequestCanonicalizer.CanonicalRequest left = canonicalizer.canonicalize(first);
        RequestCanonicalizer.CanonicalRequest traceOnly = canonicalizer.canonicalize(differentTrace);
        RequestCanonicalizer.CanonicalRequest semantic = canonicalizer.canonicalize(differentSemanticField);

        assertThat(left.json()).doesNotContain("traceId");
        assertThat(left.json()).isEqualTo(traceOnly.json());
        assertThat(left.hash()).isEqualTo(traceOnly.hash());
        assertThat(left.hash()).isNotEqualTo(semantic.hash());
    }
}
