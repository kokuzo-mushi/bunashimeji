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
                <Action Name="Step1" Type="Stay" Duration="10" />
                <Action Name="Step2" Type="Stay" Duration="20" />
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
                    <Condition>mascot.state == 'tired'</Condition>
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
        Map<String, Object> mascotTrue = new HashMap<>();
        mascotTrue.put("isGrounded", true);
        Map<String, Object> varsTrue = new HashMap<>();
        varsTrue.put("mascot", mascotTrue);
        assertTrue(b1.evaluate(null, new EvaluationContext(varsTrue)), "XMLから読み込んだ条件 'mascot.isGrounded' が true と評価されるべき");

        // Behavior 2: SitBehavior (mascot.state == 'tired')
        Behavior b2 = behaviors.stream().filter(b -> b.getName().equals("SitBehavior")).findFirst().orElseThrow();
        
        // 条件式の評価テスト: state = 'tired' の場合
        Map<String, Object> mascotTired = new HashMap<>();
        mascotTired.put("state", "tired");
        Map<String, Object> varsTired = new HashMap<>();
        varsTired.put("mascot", mascotTired);
        assertTrue(b2.evaluate(null, new EvaluationContext(varsTired)), "XMLから読み込んだ条件 'mascot.state == tired' が true と評価されるべき");

        // 6. 検証: StayActionが正しく読み込まれ、Durationが機能しているか
        Action sitAction = b2.getAction();
        assertNotNull(sitAction, "SitBehaviorのアクションはnullであってはならない");
        assertEquals("com.group_finity.mascot.action.StayAction", sitAction.getClass().getName(), "アクションクラスはStayActionであるべき");

        // 実行テスト (Duration=50ms)
        Mascot mascot = new Mascot();
        sitAction.execute(mascot);
        assertTrue(sitAction.hasNext(), "開始直後は継続しているはず");

        Thread.sleep(100); // Duration(50ms)以上待機
        assertFalse(sitAction.hasNext(), "指定時間経過後は終了しているはず");

        // 7. 検証: SequenceActionが正しく構成されているか
        Behavior bSeq = behaviors.stream().filter(b -> b.getName().equals("SequenceBehavior")).findFirst().orElseThrow();
        Action seqAction = bSeq.getAction();
        assertEquals("com.group_finity.mascot.action.SequenceAction", seqAction.getClass().getName());

        // リフレクションを使用して内部のリストサイズを確認（SequenceActionにgetterがないため）
        java.lang.reflect.Field sequenceField = seqAction.getClass().getDeclaredField("sequence");
        sequenceField.setAccessible(true);
        List<?> sequenceList = (List<?>) sequenceField.get(seqAction);
        assertEquals(2, sequenceList.size(), "SequenceActionは2つの子アクションを持つべき");

        // 8. 検証: SequenceActionの実行順序と遷移
        // Step1 (10ms) -> Step2 (20ms)
        
        // リフレクションで currentIndex フィールドを取得
        java.lang.reflect.Field indexField = seqAction.getClass().getDeclaredField("currentIndex");
        indexField.setAccessible(true);

        // 初期状態
        assertEquals(0, indexField.getInt(seqAction), "最初はインデックス0 (Step1) であるべき");
        assertTrue(seqAction.hasNext());

        // 実行開始 (Step1開始)
        seqAction.execute(mascot);
        
        // 15ms経過 (Step1: 10ms は終了し、Step2に遷移しているはず)
        Thread.sleep(15); 
        seqAction.execute(mascot); // Step1終了判定 -> Step2開始

        assertEquals(1, indexField.getInt(seqAction), "Step1が終了し、インデックス1 (Step2) に遷移しているべき");
        assertTrue(seqAction.hasNext());

        // さらに 25ms経過 (Step2: 20ms も終了しているはず)
        Thread.sleep(25);
        seqAction.execute(mascot); // Step2終了判定 -> シーケンス終了

        // 9. 検証: ループ動作 (Loop="2")
        // ここまでの実行で1周目が終了した直後。
        // SequenceActionのロジックでは、最後の要素が終わった瞬間にリセットされて2周目に入るため、
        // hasNext() は true のまま、currentIndex は 0 に戻っているはず。
        
        assertTrue(seqAction.hasNext(), "ループ設定があるため、1周目終了後も継続しているべき");
        assertEquals(0, indexField.getInt(seqAction), "2周目の開始 (Step1) に戻っているべき");

        // 2周目の実行
        // Step1 (10ms)
        Thread.sleep(15);
        seqAction.execute(mascot); // Step1終了 -> Step2へ
        assertEquals(1, indexField.getInt(seqAction), "2周目: Step1終了 -> Step2");

        // Step2 (20ms)
        Thread.sleep(25);
        seqAction.execute(mascot); // Step2終了 -> 2周完了 -> 終了

        assertFalse(seqAction.hasNext(), "指定ループ回数(2回)終了後は false を返すべき");

        // 10. 検証: 無限ループ (Loop="-1")
        // 新しいSequenceActionを手動で構築してテスト（XML定義を追加するより手軽なため）
        SequenceAction infiniteSeq = new SequenceAction();
        infiniteSeq.setLoopCount(-1);
        // 短いStayActionを1つ追加
        infiniteSeq.setSequence(List.of(new StayAction(null, 10))); 
        
        infiniteSeq.execute(mascot); // 1回目開始
        assertTrue(infiniteSeq.hasNext());
        
        // 何回繰り返しても終わらないことを確認
        for(int i=0; i<5; i++) {
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