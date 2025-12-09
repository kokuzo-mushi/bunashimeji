package com.group_finity.mascot;

import com.group_finity.mascot.behavior.Behavior;
import com.group_finity.mascot.behavior.Configuration;
import com.group_finity.mascot.trigger.EventDispatcher;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.event.EventEnvelope;
import com.group_finity.mascot.trigger.event.EventType;
import com.group_finity.mascot.image.ImageCache;
import com.group_finity.mascot.view.MascotView;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * アプリケーションのメインエントリーポイント。
 * 設定を読み込み、マスコットを生成し、メインループを開始します。
 */
public class Main {

    public static void main(String[] args) {
        try {
            new Main().run();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("An unexpected error occurred. Exiting.");
        }
    }

    public void run() throws InterruptedException {
        System.out.println("=== Shimeji Neo Start ===");

        // --- 1️⃣ 設定の読み込み ---
        // actions.xml と behaviors.xml からアクションとビヘイビアの定義を読み込みます。
        Configuration config = new Configuration(Path.of("conf/actions.xml"), Path.of("conf/behaviors.xml"));
        List<Behavior> behaviors = config.getBehaviors();

        if (behaviors == null || behaviors.isEmpty()) {
            System.err.println("No behaviors found in configuration. The mascot will not do anything.");
            return;
        }

        // --- 2️⃣ マスコットとイベントシステムの初期化 ---
        Mascot mascot = new Mascot();

        // --- 2.5. 描画システムの初期化 ---
        ImageCache imageCache = new ImageCache(Path.of("img"));
        MascotView mascotView = new MascotView(mascot, imageCache);

        Map<String, Object> contextVariables = new HashMap<>();
        // NOTE: ここで定義する変数が、behaviors.xml の <condition> で使用できます。
        contextVariables.put("mascot.isGrounded", true); // 例: 地面にいるか
        contextVariables.put("mascot.x", mascot.getX());
        contextVariables.put("mascot.y", mascot.getY());
        contextVariables.put("time", 0L);

        EvaluationContext context = new EvaluationContext(contextVariables);
        EventDispatcher dispatcher = new EventDispatcher(context, mascot);

        // 読み込んだビヘイビアをディスパッチャに登録します。
        for (Behavior behavior : behaviors) {
            dispatcher.registerTrigger(behavior);
        }
        System.out.printf("[Main] Loaded and registered %d behaviors.%n", dispatcher.getRegisteredCount());

        // ウィンドウを可視化
        mascotView.setVisible(true);

        // --- 3️⃣ メインループ ---
        System.out.println("[Main] Starting main loop... (Press Ctrl+C to exit)");
        long tickCount = 0;

        while (!Thread.currentThread().isInterrupted()) {
            // コンテキスト変数を更新します。
            // これにより、ビヘイビアの条件が動的に変化します。
            context.getVariables().put("time", ++tickCount);
            context.getVariables().put("mascot.x", mascot.getX());
            context.getVariables().put("mascot.y", mascot.getY());

            // 1. イベントをディスパッチして、条件に合うビヘイビアを探します。
            // SYSTEM_TICKは、毎フレーム発生する基本的なイベントです。
            dispatcher.evaluateTriggers(new EventEnvelope<>(EventType.SYSTEM_TICK, tickCount, this));

            // 2. マスコットのtick()を呼び出し、現在のアクションを実行させます。
            mascot.tick();

            // 3. 描画処理（将来的に実装）
            mascotView.update();

            // 4. 少し待機して、CPU使用率を抑えます。
            Thread.sleep(30); // 約33 FPS
        }

        System.out.println("=== Shimeji Neo Shutdown ===");
    }
}
