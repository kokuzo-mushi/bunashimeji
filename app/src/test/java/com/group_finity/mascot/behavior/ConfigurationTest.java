package com.group_finity.mascot.behavior;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.action.SequenceAction;
import com.group_finity.mascot.action.StayAction;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurationTest {

    @Test
    void load_shouldParseXmlAndCreateBehaviors(@TempDir Path tempDir) throws Exception {
        // 1. actions.xml の作成 (ダミーのアクション定義)
        Path actionsPath = tempDir.resolve("actions.xml");
        String actionsXml = """
                <Actions>
                    <!-- ActionBuilderの検証要件を満たすため、パラメータ不要な Turn タイプを使用します -->
                    <!-- 本来 Move には <Point> が、Walk には <Animation> が必要です -->
                    <Action Name="Walk" Type="Turn" />
                    <Action Name="Sit" Type="Stay" Duration="50" />

                    <!-- SequenceActionのテスト用定義 -->
                    <Action Name="Step1" Type="Stay" Duration="40" />
                    <Action Name="Step2" Type="Stay" Duration="40" />
                    <!-- Loop="2" で2回繰り返す設定 -->
                    <Action Name="MySequence" Type="Sequence" Loop="2">
                        <ActionReference Name="Step1" />
                        <ActionReference Name="Step2" />
                    </Action>

                    <!-- DraggedActionのテスト用定義 -->
                    <Action Name="MyDragged" Type="Dragged">
                        <Animation>
                            <Pose Image="drag.png" Duration="100" />
                        </Animation>
                    </Action>
                </Actions>
                """;
        Files.writeString(actionsPath, actionsXml);

        // 2. behaviors.xml の作成 (条件式を含むビヘイビア定義)
        Path behaviorsPath = tempDir.resolve("behaviors.xml");
        String behaviorsXml = """
                <Behaviors>
                    <Behavior Name="WalkBehavior" Hidden="false">
                        <Condition>mascot.isGrounded</Condition>
                        <ActionReference Name="Walk" />
                    </Behavior>
                    <Behavior Name="SitBehavior">
                        <Condition>mascot.isHittingCeiling</Condition>
                        <ActionReference Name="Sit" />
                    </Behavior>
                    <Behavior Name="SequenceBehavior">
                        <Condition>mascot.state == 'combo'</Condition>
                        <ActionReference Name="MySequence" />
                    </Behavior>
                </Behaviors>
                """;
        Files.writeString(behaviorsPath, behaviorsXml);

        // 3. Configurationによる読み込み
        Configuration config = new Configuration(actionsPath, behaviorsPath);

        // 4. 検証: アクションが読み込まれているか
        Map<String, Action> actions = config.getActions();
        assertEquals(6, actions.size()); // Walk, Sit, Step1, Step2, MySequence, MyDragged
        assertTrue(actions.containsKey("Walk"));
        assertTrue(actions.containsKey("Sit"));
        assertTrue(actions.containsKey("MySequence"));

        // 5. 検証: ビヘイビアが読み込まれ、条件式が機能しているか
        List<Behavior> behaviors = config.getBehaviors();
        assertEquals(3, behaviors.size());

        // Behavior 1: WalkBehavior (mascot.isGrounded)
        Behavior b1 = behaviors.stream().filter(b -> b.getName().equals("WalkBehavior")).findFirst().orElseThrow();
        assertNotNull(b1.getAction());

        // 条件式の評価テスト: isGrounded = true の場合
        // Use Real Mascot and Real Context to avoid extensive mocking of GraalJS
        Mascot mascotTrueVal = new Mascot();
        mascotTrueVal.setGrounded(true);
        try (org.graalvm.polyglot.Context jsCtx = org.graalvm.polyglot.Context.newBuilder("js").build()) {
            mascotTrueVal.setJsContext(jsCtx);

            // Behavior check() requires mascot to be in context variables
            Map<String, Object> varsTrue = new HashMap<>();
            varsTrue.put("mascot", mascotTrueVal);

            assertTrue(b1.evaluate(null, new EvaluationContext(varsTrue)),
                    "XMLから読み込んだ条件 'mascot.isGrounded' が true と評価されるべき");
        }

        // Behavior 2: SitBehavior (mascot.isHittingCeiling)
        Behavior b2 = behaviors.stream().filter(b -> b.getName().equals("SitBehavior")).findFirst().orElseThrow();

        // 条件式の評価テスト: isHittingCeiling = true の場合
        Mascot mascotCeilingVal = new Mascot();
        mascotCeilingVal.setHittingCeiling(true);
        try (org.graalvm.polyglot.Context jsCtx2 = org.graalvm.polyglot.Context.newBuilder("js").build()) {
            mascotCeilingVal.setJsContext(jsCtx2);

            Map<String, Object> varsCeiling = new HashMap<>();
            varsCeiling.put("mascot", mascotCeilingVal);
            assertTrue(b2.evaluate(null, new EvaluationContext(varsCeiling)),
                    "XMLから読み込んだ条件 'mascot.isHittingCeiling' が true と評価されるべき");
        }
        // 6. 検証: StayActionが正しく読み込まれ、Durationが機能しているか
        Action sitAction = b2.getAction();
        assertNotNull(sitAction, "SitBehaviorのアクションはnullであってはならない");
        assertEquals("com.group_finity.mascot.action.StayAction", sitAction.getClass().getName(),
                "アクションクラスはStayActionであるべき");

        // 実行テスト (Duration=50ms)
        Mascot mascot = new Mascot();
        sitAction.execute(mascot);
        assertTrue(sitAction.hasNext(), "開始直後は継続しているはず");

        // Simulate frames (StayAction is frame-based)
        for (int i = 0; i < 5; i++)
            sitAction.execute(mascot);
        assertFalse(sitAction.hasNext(), "指定時間経過後は終了しているはず");

        // 7. 検証: SequenceActionが正しく構成されているか
        Behavior bSeq = behaviors.stream().filter(b -> b.getName().equals("SequenceBehavior")).findFirst()
                .orElseThrow();
        Action seqAction = bSeq.getAction();
        assertEquals("com.group_finity.mascot.action.SequenceAction", seqAction.getClass().getName());

        // リフレクションを使用して内部のリストサイズを確認（SequenceActionにgetterがないため）
        java.lang.reflect.Field sequenceField = seqAction.getClass().getDeclaredField("sequence");
        sequenceField.setAccessible(true);
        List<?> sequenceList = (List<?>) sequenceField.get(seqAction);
        assertEquals(2, sequenceList.size(), "SequenceActionは2つの子アクションを持つべき");

        // 8. 検証: SequenceActionの実行順序と遷移
        // Step1 (10ms) -> Step2 (20ms)
        // Step1 (40ms) -> Step2 (40ms)

        // リフレクションで currentIndex フィールドを取得
        java.lang.reflect.Field indexField = seqAction.getClass().getDeclaredField("currentIndex");
        indexField.setAccessible(true);

        // 初期状態
        assertEquals(0, indexField.getInt(seqAction), "最初はインデックス0 (Step1) であるべき");
        assertTrue(seqAction.hasNext());

        // 9. 検証: ループ動作 (Loop="2")
        // SequenceAction: Step1(40ms) -> Step2(40ms) (Total 80ms)
        // 16ms/frame. Step1 needs 3 frames (48ms). Step2 needs 3 frames (48ms).
        // Total sequence: 6 frames per loop.

        // Loop 1 Step 1
        for (int i = 0; i < 3; i++) {
            seqAction.execute(mascot);
        }
        // Should be at Step 2 (Index 1)
        assertEquals(1, indexField.getInt(seqAction), "Step1 finished, Step2 started");

        // Loop 1 Step 2
        for (int i = 0; i < 3; i++) {
            seqAction.execute(mascot);
        }
        // Should be reset to Loop 2 Step 1 (Index 0)
        assertEquals(0, indexField.getInt(seqAction), "Loop 1 finished, Loop 2 Step 1 started");
        assertTrue(seqAction.hasNext(), "Should have Next (Loop 2)");

        // Loop 2 Step 1 + Step 2
        for (int i = 0; i < 6; i++) {
            seqAction.execute(mascot);
        }

        // Should be finished
        assertFalse(seqAction.hasNext(), "Sequence should be finished after 2 loops");

        // 10. 検証: 無限ループ (Loop="-1")
        // 新しいSequenceActionを手動で構築してテスト（XML定義を追加するより手軽なため）
        SequenceAction infiniteSeq = new SequenceAction();
        infiniteSeq.setLoopCount(-1);
        // 短いStayActionを1つ追加 (Duration=50ms > 16ms so it doesn't loop infinitely in one
        // frame)
        infiniteSeq.setSequence(List.of(new StayAction(null, 50)));

        infiniteSeq.execute(mascot); // 1回目開始
        assertTrue(infiniteSeq.hasNext());

        // 何回繰り返しても終わらないことを確認
        for (int i = 0; i < 5; i++) {
            Thread.sleep(15); // Action(10ms)より長く待つ
            infiniteSeq.execute(mascot); // アクション終了 -> リセット -> 次のループへ
            assertTrue(infiniteSeq.hasNext(), "無限ループ設定なので常に true を返すべき (loop: " + i + ")");
        }

        // 11. 検証: DraggedActionの読み込み
        Action draggedAction = actions.get("MyDragged");
        assertNotNull(draggedAction, "DraggedActionが読み込まれているべき");
        assertEquals("com.group_finity.mascot.action.DraggedAction", draggedAction.getClass().getName(), "クラス型が正しいこと");
    }
}