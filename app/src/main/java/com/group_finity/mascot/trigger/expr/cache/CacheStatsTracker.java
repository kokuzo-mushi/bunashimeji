package com.group_finity.mascot.trigger.expr.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Records cache hit/miss statistics globally and per trigger.
 */
public final class CacheStatsTracker {

    public static final CacheStatsTracker INSTANCE = new CacheStatsTracker();
    private CacheStatsTracker() {}

    private final AtomicLong hitCount  = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();

    private final ConcurrentHashMap<String, AtomicLong> perTriggerHits  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> perTriggerMisses = new ConcurrentHashMap<>();

    public void recordHit(String triggerId) {
        hitCount.incrementAndGet();
        perTriggerHits.computeIfAbsent(triggerId, id -> new AtomicLong()).incrementAndGet();
    }

    public void recordMiss(String triggerId) {
        missCount.incrementAndGet();
        perTriggerMisses.computeIfAbsent(triggerId, id -> new AtomicLong()).incrementAndGet();
    }

    public long getHitCount()  { return hitCount.get(); }
    public long getMissCount() { return missCount.get(); }

    public double getGlobalHitRate() {
        long total = hitCount.get() + missCount.get();
        return total == 0 ? 0.0 : (double) hitCount.get() / total;
    }

    public Map<String, AtomicLong> getPerTriggerHits()  { return perTriggerHits; }
    public Map<String, AtomicLong> getPerTriggerMisses() { return perTriggerMisses; }

    public void reset() {
        hitCount.set(0);
        missCount.set(0);
        perTriggerHits.clear();
        perTriggerMisses.clear();
    }
}
