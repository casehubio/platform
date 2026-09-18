package io.casehub.platform.simulation.testing;

import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.ResourceId;
import io.casehub.platform.simulation.CorpusSeed;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AclCorpusTest {

    @Test
    void canAccessReturnsPreConfiguredSeed() {
        CorpusSeed<Object[], Boolean> seed = AclCorpus.canAccess("tenant-1");
        assertThat(seed.qualifiedName()).isEqualTo("access-control-provider.canAccess");
        assertThat(seed.keyExtractor()).isNotNull();
    }

    @Test
    void checkConstructsObjectArray() {
        Object[] input = AclCorpus.check("admin", new ResourceId("case", "c-1"), AclAction.WRITE);
        assertThat(input).hasSize(3);
        assertThat(input[0]).isEqualTo("admin");
        assertThat(input[1]).isEqualTo(new ResourceId("case", "c-1"));
        assertThat(input[2]).isEqualTo(AclAction.WRITE);
    }

    @Test
    void resourceConstructsResourceId() {
        ResourceId id = AclCorpus.resource("case", "c-1");
        assertThat(id.type()).isEqualTo("case");
        assertThat(id.id()).isEqualTo("c-1");
    }

    @Test
    void keyExtractorDerivesConsistentKey() {
        Object[] input = AclCorpus.check("admin", AclCorpus.resource("case", "c-1"), AclAction.WRITE);
        String key = AclCorpus.canAccessExtractor().extract(input);
        assertThat(key).isEqualTo("admin:case:c-1:WRITE");
    }

    @Test
    void endToEndSeedAccumulation() {
        var seed = AclCorpus.canAccess("hospital-a");
        seed.add(AclCorpus.check("admin", AclCorpus.resource("case", "c-1"), AclAction.WRITE), true);
        seed.add(AclCorpus.check("nurse", AclCorpus.resource("case", "c-1"), AclAction.READ), true);

        assertThat(seed.build()).hasSize(2);
        assertThat(seed.build().get(0).output()).isTrue();
        assertThat(seed.build().get(0).key()).isNotNull();
    }
}
