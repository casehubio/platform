package io.casehub.platform.view.inmem;

import io.casehub.platform.api.view.SubjectViewSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySubjectViewQuerySupportTest {

    private List<TestSubject> subjects;
    private TestQuery query;

    record TestSubject(UUID id, String tenancyId, Set<String> labels, String name) {}

    static class TestQuery extends InMemorySubjectViewQuerySupport<TestSubject> {
        TestQuery(List<TestSubject> subjects) {
            super(() -> subjects,
                  TestSubject::labels,
                  TestSubject::tenancyId,
                  field -> "name".equals(field)
                      ? Comparator.comparing(TestSubject::name) : null);
        }
    }

    @BeforeEach
    void setUp() {
        subjects = new ArrayList<>();
        query = new TestQuery(subjects);
    }

    private SubjectViewSpec view(String pattern) {
        return new SubjectViewSpec(UUID.randomUUID(), "v", "t1",
            pattern, null, null, null, null, null);
    }

    private SubjectViewSpec sortedView(String pattern, String sortField, String dir) {
        return new SubjectViewSpec(UUID.randomUUID(), "v", "t1",
            pattern, null, sortField, dir, null, null);
    }

    @Test
    void findByView_matchesByLabelPattern() {
        subjects.add(new TestSubject(UUID.randomUUID(), "t1",
            Set.of("iot/triage/hvac"), "s1"));
        subjects.add(new TestSubject(UUID.randomUUID(), "t1",
            Set.of("legal/compliance"), "s2"));

        var result = query.findByView(view("iot/**"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("s1");
    }

    @Test
    void findByView_filtersByTenancy() {
        subjects.add(new TestSubject(UUID.randomUUID(), "t1",
            Set.of("iot/x"), "s1"));
        subjects.add(new TestSubject(UUID.randomUUID(), "t2",
            Set.of("iot/x"), "s2"));

        var result = query.findByView(view("iot/**"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tenancyId()).isEqualTo("t1");
    }

    @Test
    void findByView_pagination() {
        for (int i = 0; i < 5; i++) {
            subjects.add(new TestSubject(UUID.randomUUID(), "t1",
                Set.of("iot/x"), "s" + i));
        }

        var page = query.findByView(view("iot/**"), 1, 2);

        assertThat(page).hasSize(2);
    }

    @Test
    void countByView() {
        subjects.add(new TestSubject(UUID.randomUUID(), "t1",
            Set.of("iot/x"), "s1"));
        subjects.add(new TestSubject(UUID.randomUUID(), "t1",
            Set.of("iot/y"), "s2"));
        subjects.add(new TestSubject(UUID.randomUUID(), "t1",
            Set.of("legal/x"), "s3"));

        assertThat(query.countByView(view("iot/**"))).isEqualTo(2);
    }

    @Test
    void findByView_sortAscending() {
        subjects.add(new TestSubject(UUID.randomUUID(), "t1",
            Set.of("iot/x"), "charlie"));
        subjects.add(new TestSubject(UUID.randomUUID(), "t1",
            Set.of("iot/y"), "alpha"));
        subjects.add(new TestSubject(UUID.randomUUID(), "t1",
            Set.of("iot/z"), "bravo"));

        var result = query.findByView(sortedView("iot/**", "name", "ASC"));

        assertThat(result).extracting(TestSubject::name)
            .containsExactly("alpha", "bravo", "charlie");
    }

    @Test
    void findByView_sortDescending() {
        subjects.add(new TestSubject(UUID.randomUUID(), "t1",
            Set.of("iot/x"), "alpha"));
        subjects.add(new TestSubject(UUID.randomUUID(), "t1",
            Set.of("iot/y"), "bravo"));

        var result = query.findByView(sortedView("iot/**", "name", "DESC"));

        assertThat(result).extracting(TestSubject::name)
            .containsExactly("bravo", "alpha");
    }

    @Test
    void findByView_noSortFieldNoOrdering() {
        subjects.add(new TestSubject(UUID.randomUUID(), "t1",
            Set.of("iot/x"), "s1"));

        var result = query.findByView(view("iot/**"));

        assertThat(result).hasSize(1);
    }

    @Test
    void findByView_emptyResultsWhenNoMatch() {
        subjects.add(new TestSubject(UUID.randomUUID(), "t1",
            Set.of("legal/x"), "s1"));

        assertThat(query.findByView(view("iot/**"))).isEmpty();
    }
}
