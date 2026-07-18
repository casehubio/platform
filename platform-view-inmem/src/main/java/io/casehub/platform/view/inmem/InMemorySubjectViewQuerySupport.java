package io.casehub.platform.view.inmem;

import io.casehub.platform.api.view.LabelPatternMatcher;
import io.casehub.platform.api.view.SubjectViewQuery;
import io.casehub.platform.api.view.SubjectViewSpec;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public abstract class InMemorySubjectViewQuerySupport<S>
        implements SubjectViewQuery<S> {

    private final Supplier<Collection<S>> subjectSource;
    private final Function<S, Set<String>> labelExtractor;
    private final Function<S, String> tenancyExtractor;
    private final Function<String, Comparator<S>> sortFieldResolver;

    protected InMemorySubjectViewQuerySupport(
            Supplier<Collection<S>> subjectSource,
            Function<S, Set<String>> labelExtractor,
            Function<S, String> tenancyExtractor,
            Function<String, Comparator<S>> sortFieldResolver) {
        this.subjectSource = subjectSource;
        this.labelExtractor = labelExtractor;
        this.tenancyExtractor = tenancyExtractor;
        this.sortFieldResolver = sortFieldResolver;
    }

    @Override
    public List<S> findByView(SubjectViewSpec view) {
        var stream = subjectSource.get().stream()
            .filter(s -> tenancyExtractor.apply(s).equals(view.tenancyId()))
            .filter(s -> labelExtractor.apply(s).stream()
                .anyMatch(p -> LabelPatternMatcher.matches(
                    view.labelPattern(), p)));
        return sorted(stream, view).toList();
    }

    @Override
    public List<S> findByView(SubjectViewSpec view, int offset, int limit) {
        return findByView(view).stream()
            .skip(offset).limit(limit).toList();
    }

    @Override
    public long countByView(SubjectViewSpec view) {
        return findByView(view).size();
    }

    private Stream<S> sorted(Stream<S> stream, SubjectViewSpec view) {
        if (view.sortField() == null || sortFieldResolver == null) {
            return stream;
        }
        Comparator<S> cmp = sortFieldResolver.apply(view.sortField());
        if (cmp == null) return stream;
        if ("DESC".equalsIgnoreCase(view.sortDirection())) {
            cmp = cmp.reversed();
        }
        return stream.sorted(cmp);
    }
}
