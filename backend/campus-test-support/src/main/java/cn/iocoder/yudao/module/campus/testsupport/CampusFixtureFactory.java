package cn.iocoder.yudao.module.campus.testsupport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Loads the shared, desensitized fixed fixture from the packaged v1 JSON resource. */
public final class CampusFixtureFactory {

    public static final String RESOURCE = "fixtures/common/campus-fixtures-v1.json";
    private static final Set<String> REQUIRED_IDS = Set.of(
            "S1", "S2", "C11", "C12", "C21", "U1", "U2", "U3", "R1", "R2", "O1", "O2", "F1");
    private final Map<String, Fixture> fixtures;

    public CampusFixtureFactory() {
        this(load());
    }

    CampusFixtureFactory(Collection<Fixture> fixtures) {
        Map<String, Fixture> indexed = new LinkedHashMap<>();
        for (Fixture fixture : fixtures) {
            Fixture previous = indexed.putIfAbsent(fixture.id(), fixture);
            if (previous != null) {
                throw new IllegalStateException("Duplicate fixture id: " + fixture.id());
            }
        }
        if (!indexed.keySet().equals(REQUIRED_IDS)) {
            throw new IllegalStateException("Fixture IDs must equal the M00.06 fixed sample set");
        }
        this.fixtures = Map.copyOf(indexed);
    }

    public Fixture fixture(String id) {
        Fixture fixture = fixtures.get(Objects.requireNonNull(id, "id"));
        if (fixture == null) {
            throw new IllegalArgumentException("Unknown campus fixture: " + id);
        }
        return fixture;
    }

    public Fixture school(String id) {
        return ofKind(id, Kind.SCHOOL);
    }

    public Fixture campus(String id) {
        return ofKind(id, Kind.CAMPUS);
    }

    public Fixture student(String id) {
        return ofKind(id, Kind.STUDENT);
    }

    public Fixture runner(String id) {
        return ofKind(id, Kind.RUNNER);
    }

    public Fixture operator(String id) {
        return ofKind(id, Kind.OPERATOR);
    }

    public Fixture finance() {
        return ofKind("F1", Kind.FINANCE);
    }

    public List<Fixture> all() {
        return fixtures.values().stream().sorted(java.util.Comparator.comparing(Fixture::id)).toList();
    }

    private Fixture ofKind(String id, Kind expected) {
        Fixture fixture = fixture(id);
        if (fixture.kind() != expected) {
            throw new IllegalArgumentException(id + " is not a " + expected);
        }
        return fixture;
    }

    private static Collection<Fixture> load() {
        try (InputStream input = CampusFixtureFactory.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing packaged fixture resource: " + RESOURCE);
            }
            ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
            FixtureDocument document = mapper.readValue(input, new TypeReference<>() { });
            if (document.version != 1 || document.samples == null) {
                throw new IllegalStateException("Unsupported or invalid fixture document");
            }
            return document.samples.stream().map(FixtureDocument.Sample::toFixture).toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load fixed campus fixtures", exception);
        }
    }

    public enum Kind { SCHOOL, CAMPUS, STUDENT, RUNNER, OPERATOR, FINANCE }

    public record Fixture(String id, Kind kind, String displayName, String schoolId, String campusId,
                          boolean enabled, List<String> roles) {
        public Fixture {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(displayName, "displayName");
            roles = List.copyOf(roles == null ? List.of() : roles);
        }
    }

    private static final class FixtureDocument {
        public int version;
        public List<Sample> samples;

        private static final class Sample {
            public String id;
            public Kind kind;
            public String displayName;
            public String schoolId;
            public String campusId;
            public boolean enabled;
            public List<String> roles;

            private Fixture toFixture() {
                return new Fixture(id, kind, displayName, schoolId, campusId, enabled, roles);
            }
        }
    }
}
