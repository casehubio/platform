package io.casehub.platform.view.jpa;

import io.casehub.platform.api.view.SubjectViewSpec;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.ListAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

import java.util.List;

public abstract class JpaLabelPatternQuerySupport<E, L> {

    private final Class<E> entityClass;
    private final ListAttribute<E, L> labelsAttr;
    private final SingularAttribute<L, String> pathAttr;
    private final SingularAttribute<E, String> tenancyAttr;

    protected JpaLabelPatternQuerySupport(
            Class<E> entityClass,
            ListAttribute<E, L> labelsAttr,
            SingularAttribute<L, String> pathAttr,
            SingularAttribute<E, String> tenancyAttr) {
        this.entityClass = entityClass;
        this.labelsAttr = labelsAttr;
        this.pathAttr = pathAttr;
        this.tenancyAttr = tenancyAttr;
    }

    protected List<E> findByView(EntityManager em, SubjectViewSpec view) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<E> cq = cb.createQuery(entityClass);
        Root<E> root = cq.from(entityClass);
        Join<E, L> labelJoin = root.join(labelsAttr);

        cq.where(cb.and(
            LabelPatternPredicates.toPredicate(cb, labelJoin.get(pathAttr),
                view.labelPattern()),
            cb.equal(root.get(tenancyAttr), view.tenancyId())
        )).distinct(true);

        if (view.sortField() != null) {
            cq.orderBy("DESC".equalsIgnoreCase(view.sortDirection())
                ? cb.desc(root.get(view.sortField()))
                : cb.asc(root.get(view.sortField())));
        }

        return em.createQuery(cq).getResultList();
    }

    protected List<E> findByView(EntityManager em, SubjectViewSpec view,
            int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<E> cq = cb.createQuery(entityClass);
        Root<E> root = cq.from(entityClass);
        Join<E, L> labelJoin = root.join(labelsAttr);

        cq.where(cb.and(
            LabelPatternPredicates.toPredicate(cb, labelJoin.get(pathAttr),
                view.labelPattern()),
            cb.equal(root.get(tenancyAttr), view.tenancyId())
        )).distinct(true);

        if (view.sortField() != null) {
            cq.orderBy("DESC".equalsIgnoreCase(view.sortDirection())
                ? cb.desc(root.get(view.sortField()))
                : cb.asc(root.get(view.sortField())));
        } else {
            cq.orderBy(cb.asc(root.get("id")));
        }

        TypedQuery<E> query = em.createQuery(cq);
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    protected long countByView(EntityManager em, SubjectViewSpec view) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<E> root = cq.from(entityClass);
        Join<E, L> labelJoin = root.join(labelsAttr);

        cq.select(cb.countDistinct(root));
        cq.where(cb.and(
            LabelPatternPredicates.toPredicate(cb, labelJoin.get(pathAttr),
                view.labelPattern()),
            cb.equal(root.get(tenancyAttr), view.tenancyId())
        ));

        return em.createQuery(cq).getSingleResult();
    }
}
