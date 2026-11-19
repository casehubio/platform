package io.casehub.platform.mcp;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DomainFilterTest {

    @Test
    void discoverModeAllowsAllDomains() {
        var filter = new DomainFilter("discover", Optional.empty());
        assertThat(filter.isAllowed("connectors/chat")).isTrue();
        assertThat(filter.isAllowed("connectors/bank")).isTrue();
        assertThat(filter.isAllowed("anything")).isTrue();
    }

    @Test
    void explicitModeAllowsOnlyListedDomains() {
        var filter = new DomainFilter("explicit",
                Optional.of("chat,calendar,document"));
        assertThat(filter.isAllowed("connectors/chat")).isTrue();
        assertThat(filter.isAllowed("connectors/calendar")).isTrue();
        assertThat(filter.isAllowed("connectors/document")).isTrue();
        assertThat(filter.isAllowed("connectors/bank")).isFalse();
        assertThat(filter.isAllowed("connectors/health")).isFalse();
    }

    @Test
    void explicitModeMatchesLastSegment() {
        var filter = new DomainFilter("explicit", Optional.of("chat"));
        assertThat(filter.isAllowed("connectors/chat")).isTrue();
        assertThat(filter.isAllowed("neocortex/chat")).isTrue();
        assertThat(filter.isAllowed("chat")).isTrue();
    }

    @Test
    void explicitModeWithEmptyDomainsBlocksEverything() {
        var filter = new DomainFilter("explicit", Optional.empty());
        assertThat(filter.isAllowed("connectors/chat")).isFalse();
    }
}
