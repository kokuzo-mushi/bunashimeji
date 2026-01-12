# Phase 5 Step 3: Launcher Crash Fix 作業レポート

## 1. 概要
ランチャー画面でのキャラクター選択後に発生していたクラッシュ（NullPointerException）を修正しました。
原因は、`EventDispatcher` がスクリプトベースの `Behavior` からアクションを取得する際に、適切なインスタンス化メソッド (`instantiateAction`) ではなく、生のフィールド (`getAction`) を参照していたためでした。

## 2. 修正内容

### 2.1 EventDispatcher.java の修正
アクションの取得方法を変更し、スクリプト（Generator）の場合は常に新しい実行可能オブジェクトを生成するようにしました。

#### [MODIFY] app/src/main/java/com/group_finity/mascot/trigger/EventDispatcher.java
```diff
-            Action action = selectedBehavior.getAction();
+            Action action = selectedBehavior.instantiateAction(this.mascot);
```

### 2.2 Behavior.java の副作用除去
`evaluate` メソッド内で行われていた `execute()`（アクションの即時実行）を削除し、実行の責務を `EventDispatcher` に一本化しました。これにより、トリガー評価とアクション実行のタイミングが整理され、競合や二重実行のリスクが解消されました。

#### [MODIFY] app/src/main/java/com/group_finity/mascot/behavior/Behavior.java
```diff
     public boolean evaluate(EventEnvelope<?> event, EvaluationContext context) {
         if (check(event, context)) {
             Mascot mascot = (Mascot) context.getVariables().get("mascot");
             if (mascot != null) {
-                execute(event, mascot);
                 return true;
             }
         }
```

## 3. 検証結果
`./gradlew run` にてアプリケーションが正常に動作し、スクリプト読み込み後もクラッシュが発生しないことを確認しました。
