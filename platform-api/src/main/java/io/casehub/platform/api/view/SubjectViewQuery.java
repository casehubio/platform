package io.casehub.platform.api.view;

import java.util.List;

public interface SubjectViewQuery<S> {
    List<S> findByView(SubjectViewSpec view);
    List<S> findByView(SubjectViewSpec view, int offset, int limit);
    long countByView(SubjectViewSpec view);
}
