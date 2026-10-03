package io.casehub.platform.api.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelChainTest {

    @Test
    void of_varargs_createsNamedEntries() {
        var chain = ModelChain.of("opus", "sonnet", "haiku");
        assertThat(chain.entries()).hasSize(3);
        assertThat(chain.entries().get(0)).isInstanceOf(ModelChain.ModelChainEntry.Named.class);
        assertThat(((ModelChain.ModelChainEntry.Named) chain.entries().get(0)).modelRef()).isEqualTo("opus");
    }

    @Test
    void of_list_createsImmutableCopy() {
        var entries = new ArrayList<ModelChain.ModelChainEntry>();
        entries.add(new ModelChain.ModelChainEntry.Named("opus"));
        var chain = ModelChain.of(entries);
        entries.clear();
        assertThat(chain.entries()).hasSize(1);
    }

    @Test
    void isEmpty_emptyChain() {
        assertThat(ModelChain.of().isEmpty()).isTrue();
    }

    @Test
    void isEmpty_nonEmptyChain() {
        assertThat(ModelChain.of("opus").isEmpty()).isFalse();
    }

    @Test
    void namedEntry_holdsModelRef() {
        var entry = new ModelChain.ModelChainEntry.Named("claude-opus-4-6");
        assertThat(entry.modelRef()).isEqualTo("claude-opus-4-6");
    }

    @Test
    void queriedEntry_holdsModelQuery() {
        var query = ModelQuery.builder().tier(ModelTier.FLAGSHIP).build();
        var entry = new ModelChain.ModelChainEntry.Queried(query);
        assertThat(entry.query().tier()).isEqualTo(ModelTier.FLAGSHIP);
    }

    @Test
    void sealedInterface_switchCoversAllCases() {
        ModelChain.ModelChainEntry entry = new ModelChain.ModelChainEntry.Named("opus");
        String result = switch (entry) {
            case ModelChain.ModelChainEntry.Named n -> n.modelRef();
            case ModelChain.ModelChainEntry.Queried q -> q.query().toString();
        };
        assertThat(result).isEqualTo("opus");
    }

    @Test
    void chainResolutionResult_wasFallback_singleEntry() {
        var entry = new ModelChain.ModelChainEntry.Named("opus");
        var result = ChainResolutionResult.direct(null, entry);
        assertThat(result.wasFallback()).isFalse();
    }

    @Test
    void chainResolutionResult_wasFallback_multipleEntries() {
        var e1 = new ModelChain.ModelChainEntry.Named("opus");
        var e2 = new ModelChain.ModelChainEntry.Named("sonnet");
        var result = new ChainResolutionResult(null, e2, List.of(e1, e2));
        assertThat(result.wasFallback()).isTrue();
    }

    @Test
    void availabilityFilter_alwaysAvailable() {
        assertThat(ModelAvailabilityFilter.ALWAYS_AVAILABLE.isAvailable(null)).isTrue();
    }
}
