package com.group_finity.mascot.log;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import com.group_finity.mascot.log.config.EventLogConfig;
import com.group_finity.mascot.log.config.EventLogConfig.DropPolicy;

/**
 * EventLogAggregator の基本動作テスト。
 */
public class EventLogAggregatorTest {

    /**
     * メモリ上に記録を集約する簡易Sink。
     */
    static class InMemorySink implements EventLogSink {
        final List<List<EventLogRecord>> batches = new CopyOnWriteArrayList<>();
        @Override
        public void writeBatch(List<EventLogRecord> batch) {
            // defensive copy
            batches.add(new ArrayList<>(batch));
        }
    }

    @Test
    void testBatchingAndOrder() throws Exception {
        var cfg = new EventLogConfig(32, 3, Duration.ofSeconds(5), DropPolicy.DROP_OLDEST);
        var sink = new InMemorySink();
        var agg = new EventLogAggregator(cfg, sink);
        agg.start();

        // 7件送信 → batch=3,3,1 のはず
        for (int i = 0; i < 7; i++) {
            agg.offer(new EventLogRecord("src", "trg", true, 0L,
                    EventLogRecord.Level.INFO, Map.of("i", i)));
        }

        // flush: batchSize=3で即時出力されるよう待機
        Thread.sleep(200);
        agg.stop(Duration.ofSeconds(1));

        // 出力件数確認
        long total = sink.batches.stream().mapToLong(List::size).sum();
        assertEquals(7, total, "総件数が一致すること");

        // バッチ構成確認（3,3,1）
        var sizes = sink.batches.stream().map(List::size).toList();
        assertEquals(List.of(3, 3, 1), sizes, "バッチサイズが期待通り");

        // 順序性確認（seq昇順）
        var allSeq = sink.batches.stream().flatMap(List::stream).mapToLong(r -> r.seq).toArray();
        for (int i = 1; i < allSeq.length; i++) {
            assertTrue(allSeq[i - 1] < allSeq[i], "seqが昇順であること");
        }
    }

    @Test
    void testDropOldestPolicy() throws Exception {
        var cfg = new EventLogConfig(2, 10, Duration.ofMillis(50), DropPolicy.DROP_OLDEST);
        var sink = new InMemorySink();
        var agg = new EventLogAggregator(cfg, sink);
        agg.start();

        // 容量2のキューに5件送る（古い3件はドロップされる）
        for (int i = 0; i < 5; i++) {
            agg.offer(new EventLogRecord("src", "drop", true, 0L,
                    EventLogRecord.Level.INFO, Map.of("i", i)));
        }

        Thread.sleep(200);
        agg.stop(Duration.ofSeconds(1));

        // 最後の2件だけが保持されているはず
        var remaining = sink.batches.stream().flatMap(List::stream)
                .map(r -> (Integer) r.context.get("i"))
                .toList();

        assertTrue(remaining.containsAll(List.of(3, 4)), "末尾2件が保持されること");
    }

    @Test
    void testGracefulShutdownFlushesRemaining() throws Exception {
        var cfg = new EventLogConfig(8, 100, Duration.ofSeconds(2), DropPolicy.DROP_OLDEST);
        var sink = new InMemorySink();
        var agg = new EventLogAggregator(cfg, sink);
        agg.start();

        for (int i = 0; i < 5; i++) {
            agg.offer(new EventLogRecord("src", "grace", true, 0L,
                    EventLogRecord.Level.INFO, Map.of("i", i)));
        }

        // 即時 stop → flushInterval より前に強制終了
        agg.stop(Duration.ofSeconds(1));

        long total = sink.batches.stream().mapToLong(List::size).sum();
        assertEquals(5, total, "停止時に残件がすべてフラッシュされること");
    }
}
