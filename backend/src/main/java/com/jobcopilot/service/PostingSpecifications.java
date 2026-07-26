package com.jobcopilot.service;

import com.jobcopilot.entity.Posting;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable JPA specifications for querying {@link Posting} entities in the job list.
 */
public final class PostingSpecifications {

    private PostingSpecifications() {
    }

    public static Specification<Posting> search(String query) {
        return (root, cq, cb) -> {
            if (query == null || query.isBlank()) {
                return cb.conjunction();
            }
            String like = "%" + query.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("company")), like),
                    cb.like(cb.lower(root.get("location")), like)
            );
        };
    }

    public static Specification<Posting> minScore(Integer minScore) {
        return (root, cq, cb) -> minScore == null
                ? cb.conjunction()
                : cb.greaterThanOrEqualTo(root.get("score"), minScore);
    }

    public static Specification<Posting> remote(Boolean remote) {
        return (root, cq, cb) -> remote == null
                ? cb.conjunction()
                : cb.equal(root.get("remote"), remote);
    }

    public static Specification<Posting> source(String source) {
        return (root, cq, cb) -> (source == null || source.isBlank())
                ? cb.conjunction()
                : cb.equal(root.get("source"), source);
    }

    public static Specification<Posting> company(String company) {
        return (root, cq, cb) -> (company == null || company.isBlank())
                ? cb.conjunction()
                : cb.like(cb.lower(root.get("company")), "%" + company.toLowerCase() + "%");
    }

    /** Excludes postings whose ids are in the given (dismissed) list. */
    public static Specification<Posting> notIn(List<Long> excludedIds) {
        return (root, cq, cb) -> {
            if (excludedIds == null || excludedIds.isEmpty()) {
                return cb.conjunction();
            }
            return cb.not(root.get("id").in(excludedIds));
        };
    }

    public static Specification<Posting> combine(List<Specification<Posting>> specs) {
        Specification<Posting> result = Specification.where(null);
        for (Specification<Posting> spec : specs) {
            if (spec != null) {
                result = result.and(spec);
            }
        }
        return result;
    }

    /** Exclude archived (soft-deleted) postings. */
    public static Specification<Posting> notArchived() {
        return (root, cq, cb) -> cb.isFalse(root.get("archived"));
    }

    /** Exclude manually created and Gmail-synced postings from the jobs page. */
    public static Specification<Posting> notManual() {
        return (root, cq, cb) -> cb.and(
                cb.notEqual(root.get("source"), "MANUAL"),
                cb.notEqual(root.get("source"), "GMAIL_SYNC")
        );
    }
}
