package io.casehub.platform.simulation;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InvocationJournalTest {

    @Test
    void recordAndRetrieveEntries() {
        var journal = new InvocationJournal();
        var entry = new JournalEntry("spi.method", "t1", "input", "output", Instant.now(), DataRealism.DOMAIN_PLAUSIBLE);
        journal.record(entry);

        assertThat(journal.entries()).hasSize(1);
        assertThat(journal.entries().get(0).qualifiedName()).isEqualTo("spi.method");
        assertThat(journal.entries().get(0).dataRealism()).isEqualTo(DataRealism.DOMAIN_PLAUSIBLE);
    }

    @Test
    void entriesForFiltersByQualifiedName() {
        var journal = new InvocationJournal();
        journal.record(new JournalEntry("spi.a", "t1", "in1", "out1", Instant.now(), DataRealism.DOMAIN_PLAUSIBLE));
        journal.record(new JournalEntry("spi.b", "t1", "in2", "out2", Instant.now(), null));
        journal.record(new JournalEntry("spi.a", "t1", "in3", "out3", Instant.now(), DataRealism.DOMAIN_PLAUSIBLE));

        assertThat(journal.entriesFor("spi.a")).hasSize(2);
        assertThat(journal.entriesFor("spi.b")).hasSize(1);
        assertThat(journal.entriesFor("spi.c")).isEmpty();
    }

    @Test
    void countForReturnsCorrectCount() {
        var journal = new InvocationJournal();
        journal.record(new JournalEntry("spi.a", "t1", "in1", "out1", Instant.now(), DataRealism.DOMAIN_PLAUSIBLE));
        journal.record(new JournalEntry("spi.a", "t1", "in2", "out2", Instant.now(), null));

        assertThat(journal.countFor("spi.a")).isEqualTo(2);
        assertThat(journal.countFor("spi.b")).isZero();
    }

    @Test
    void entriesReturnsDefensiveCopy() {
        var journal = new InvocationJournal();
        journal.record(new JournalEntry("spi.a", "t1", "in", "out", Instant.now(), DataRealism.DOMAIN_PLAUSIBLE));

        var entries = journal.entries();
        journal.record(new JournalEntry("spi.b", "t1", "in2", "out2", Instant.now(), null));

        assertThat(entries).hasSize(1);
        assertThat(journal.entries()).hasSize(2);
    }
}
