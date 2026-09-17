package io.casehub.platform.view.jpa;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

public final class LabelPatternPredicates {

    private LabelPatternPredicates() {}

    static String escapeLikePrefix(String prefix) {
        return prefix.replace("\\", "\\\\")
                     .replace("%", "\\%")
                     .replace("_", "\\_");
    }

    public static Predicate toPredicate(
            CriteriaBuilder cb, Path<String> pathExpr, String pattern) {
        if (pattern.endsWith("/**")) {
            String prefix = escapeLikePrefix(
                pattern.substring(0, pattern.length() - 3));
            return cb.like(pathExpr, prefix + "/%", '\\');
        }
        if (pattern.endsWith("/*")) {
            String prefix = escapeLikePrefix(
                pattern.substring(0, pattern.length() - 2));
            return cb.and(
                cb.like(pathExpr, prefix + "/%", '\\'),
                cb.notLike(pathExpr, prefix + "/%/%", '\\')
            );
        }
        return cb.equal(pathExpr, pattern);
    }
}
