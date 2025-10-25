package com.group_finity.mascot.log;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import com.group_finity.mascot.log.config.EventLogConfig;

/**
 * EventLog
 *
 * 非同期ログ集約システムのエントリポイント。
 * Aggregator と Sink を初期化し、record() 経由でログ記録を行う。
 */
public final class EventLog {

    private static volatile EventLogAggregator AGG;

    private EventLog() {
        // インスタンス化防止
    }

    /**
     * デフォルト構成で EventLog を初期化する。
     *
     * @param fileDir ログファイル出力ディレクトリ
     */
    public static synchronized void initDefault(Path fileDir) {
        if (AGG != null) {
            return;
        }
        try {
            var sink = new com.group_finity.mascot.log.sink.RotatingFileEventLogSink(fileDir, 10 * 1024 * 1024);
            var cfg = EventLogConfig.defaultConfig();
            AGG = new EventLogAggregator(cfg, sink);
            AGG.start();
        } catch (Exception e) {
            throw new IllegalStateException("EventLog init failed", e);
        }
    }

    /**
     * ログシステムを停止する。
     * 全ての残件をフラッシュして終了する。
     */
    public static void shutdown() {
        var a = AGG;
        if (a != null) {
            a.stop(Duration.ofSeconds(2));
        }
    }

    /**
     * ログを記録する。
     *
     * @param source ログ発生元（例: EventDispatcher）
     * @param trigger トリガ識別子
     * @param success 成否
     * @param elapsedNanos 経過時間（ナノ秒）
     * @param level ログレベル
     * @param ctx 追加情報（コンテキストマップ）
     * @return 成功した場合 true（Aggregator が停止中なら false）
     */
    public static boolean record(
            String source,
            String trigger,
            boolean success,
            long elapsedNanos,
            EventLogRecord.Level level,
            Map<String, Object> ctx) {

        var a = AGG;
        if (a == null) {
            return false;
        }
        return a.offer(new EventLogRecord(source, trigger, success, elapsedNanos, level, ctx));
    }
}
