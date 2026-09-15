package io.casehub.platform.simulation.config;

import java.util.Objects;

@FunctionalInterface
public interface FieldSimilarity {

    double score(Object queryValue, Object candidateValue);

    FieldSimilarity EXACT = (q, c) -> Objects.equals(q, c) ? 1.0 : 0.0;

    FieldSimilarity SUBSTRING = (q, c) -> {
        if (q == null || c == null) return Objects.equals(q, c) ? 1.0 : 0.0;
        String qs = q.toString(), cs = c.toString();
        if (qs.equals(cs)) return 1.0;
        if (qs.contains(cs) || cs.contains(qs)) return 0.8;
        return 0.0;
    };

    FieldSimilarity NUMERIC_RANGE = (q, c) -> {
        if (!(q instanceof Number qn) || !(c instanceof Number cn)) return 0.0;
        double diff = Math.abs(qn.doubleValue() - cn.doubleValue());
        double max = Math.max(Math.abs(qn.doubleValue()), Math.abs(cn.doubleValue()));
        return max == 0.0 ? 1.0 : Math.max(0.0, 1.0 - diff / max);
    };

    FieldSimilarity IGNORE = (q, c) -> 1.0;
}
