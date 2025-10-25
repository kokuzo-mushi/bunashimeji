package com.group_finity.mascot.trigger.expr.cache;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * LRU cache for expression evaluation results.
 */
public final class ExprCacheManager {

    private static final int DEFAULT_CAPACITY = 1024;

    private final Map<ExprCacheKey, EvaluationResult> cache =
        Collections.synchronizedMap(new LinkedHashMap<>(DEFAULT_CAPACITY, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ExprCacheKey, EvaluationResult> eldest) {
                return size() > DEFAULT_CAPACITY;
            }
        });

    public Optional<EvaluationResult> get(ExprCacheKey key) {
        return Optional.ofNullable(cache.get(key));
    }

    public void put(ExprCacheKey key, EvaluationResult result) {
        cache.put(key, result);
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }
}
