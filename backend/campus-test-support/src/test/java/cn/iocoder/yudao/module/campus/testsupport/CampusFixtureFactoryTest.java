package cn.iocoder.yudao.module.campus.testsupport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CampusFixtureFactoryTest {

    @Test
    void packagesTheCompleteDesensitizedM0006SampleSet() {
        CampusFixtureFactory factory = new CampusFixtureFactory();

        assertThat(factory.all()).hasSize(13);
        assertThat(factory.all()).extracting(CampusFixtureFactory.Fixture::id)
                .containsExactlyInAnyOrder("S1", "S2", "C11", "C12", "C21", "U1", "U2", "U3", "R1", "R2", "O1", "O2", "F1");
        assertThat(factory.campus("C11").schoolId()).isEqualTo("S1");
        assertThat(factory.student("U3").schoolId()).isEqualTo("S2");
        assertThat(factory.runner("R1").enabled()).isTrue();
        assertThat(factory.runner("R2").enabled()).isFalse();
        assertThat(factory.finance().roles()).containsExactly("FINANCE");
    }
}
