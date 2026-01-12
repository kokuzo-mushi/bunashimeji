# Antigravity Prompt Engineering Guide for Shimeji Neo

このドキュメントは、Google Antigravity (またはその他のエージェント型AI IDE) に開発タスクを依頼する際に使用する「コンテキスト定義」と「プロンプトテンプレート」をまとめたものです。

AIエージェントに作業を依頼する際は、以下の **[System Context]** ブロックを最初に読み込ませるか、プロンプトの冒頭に貼り付けることで、プロジェクトのルール（Iron Rules）を厳守させることができます。

---

## 1. System Context (Copy & Paste Block)

以下のブロックをコピーして、Antigravityのチャットまたはコンテキストウィンドウに入力してください。

```markdown
# PROJECT CONTEXT: Shimeji Neo

You are an expert Java Architect working on "Shimeji Neo", a modern desktop mascot application.
Your goal is to implement features while strictly adhering to the following architectural constraints.

## 1. Core Technology Stack
- **Language**: Java 21 (LTS) & Kotlin 1.9.
- **Native Access**: Project Panama (FFM API) ONLY. No JNA for new logic.
- **GUI**: JetBrains Compose Multiplatform (Desktop).
- **Scripting**: GraalJS (JavaScript) for behavior logic.
- **Build**: Gradle (Kotlin DSL).

## 2. Iron Rules (MUST FOLLOW)
1.  **No Legacy Swing Timers**: Use Kotlin Coroutines (`LaunchedEffect`, `delay`) for the main loop. Do not use `javax.swing.Timer`.
2.  **State-Driven Rendering**: Do not manually trigger repaints. Use Compose `State<T>` to drive UI updates automatically.
3.  **Panama over JNA**: Use `java.lang.foreign.*` for all OS interactions (Window movement, transparency).
4.  **Immutable Data**: Use Java `record` or Kotlin `data class` for data carriers.
5.  **Agent-Oriented**: The mascot logic is separated into `Action` (Atomic movement) and `Behavior` (Decision making).

## 3. Directory Structure
- `app/src/main/java/com/group_finity/mascot/`
    - `nativeaccess/`: FFM API implementations (User32, etc.)
    - `action/`: Atomic actions (Walk, Fall, Thrown)
    - `behavior/`: Complex behaviors and decision trees
- `app/src/main/kotlin/com/group_finity/mascot/`
    - `ui/`: JetBrains Compose UI components (`ShimejiApp.kt`, `MascotWindow.kt`)
    - `config/`: Configuration loading (XML/YAML) (`ConfigurationLoader.kt`)

## 4. Current Task Context
We are currently in **Phase 5 (Next-Gen Architecture)**.
Focus on migrating legacy Java logic to Kotlin/Compose and implementing YAML configuration support.
```

---

## 2. Task Prompt Templates

具体的な作業を依頼する際は、上記のコンテキストに続けて、以下のテンプレートを使用してください。

### A. 新機能の実装 (New Feature)

```markdown
## TASK: Implement New Feature

**Feature Name**: [機能名, 例: Wall Bounce Physics]

**Objective**:
[目的を1行で記述。例: マスコットを投げた際、壁に当たったら即停止せず、物理法則に従って跳ね返るようにしたい。]

**Requirements**:
1. [要件1: 物理演算クラスに反発係数(restitution)を追加すること]
2. [要件2: 画面端の判定は `NativeWindowUtil` を使用すること]
3. [要件3: 跳ね返り後は徐々に減速し、停止したら `FallAction` へ遷移すること]

**Verification**:
- Create a unit test `PhysicsTest.java` to verify the velocity inversion logic.
- Ensure no memory leaks occur during the bounce calculation.
```

### B. リファクタリング (Refactoring)

```markdown
## TASK: Refactoring Code

**Target**: [対象ファイルまたはクラス, 例: `Mascot.java`]

**Goal**:
[リファクタリングのゴール。例: 巨大な `Mascot` クラスから、ウィンドウ操作ロジックを `MascotWindow` インターフェースとして分離する。]

**Constraints**:
- Do NOT break existing behavior defined in `actions.xml`.
- Maintain binary compatibility for `Action` classes if possible.
- Use Java 21 `sealed interfaces` if applicable for state management.

**Steps**:
1. Analyze the dependencies of the target class.
2. Propose a plan to extract the logic.
3. Execute the refactoring step-by-step.
```

### C. バグ修正 (Bug Fix)

```markdown
## TASK: Fix Bug

**Symptom**:
[現象の説明。例: マルチモニタ環境で、DPIの異なるモニタへ移動するとマスコットのサイズがおかしくなる。]

**Logs/Errors**:
```text
[エラーログがあればここに貼り付け]
```

**Suspected Cause**:
[推測される原因。例: `NativeWindowUtil` の座標変換ロジックで、モニタごとのDPIスケールを考慮していない可能性がある。]

**Request**:
- Investigate `app/src/main/java/.../NativeWindowUtil.java`.
- Fix the coordinate calculation logic to support Per-Monitor V2 DPI.
- Verify the fix by simulating a monitor switch event.
```

---

## 3. Antigravity Tips

Antigravity (Agent IDE) を最大限に活用するためのコツです。

1.  **Plan First**:
    いきなりコードを書かせず、「まずは実装計画（Plan）を立てて、ファイル変更リストを提示してください」と指示すると、手戻りが減ります。

2.  **Reference Files**:
    プロンプト内で「`ARCHITECTURE.md` の "Native Interface" セクションを参照してください」のように、具体的なファイルとセクションを指定すると精度が上がります。

3.  **Iterative Fix**:
    エラーが出た場合、自分で直すのではなく、エラーログをそのまま貼り付けて「Fix this based on the error log」と指示するのが最も効率的です。

4.  **Definition of Done**:
    「テストが通ること」「ビルドが成功すること」を完了条件として明示してください。

---

## 4. Quick Copy (One-Liner)

チャットの最初に一発でコンテキストを注入するためのワンライナーです。

> I am working on Shimeji Neo (Java 21, Project Panama, Active Rendering). Please read `app/ad/ARCHITECTURE.md` and `app/ad/DIRECTORY_STRUCTURE.md` to understand the project constraints. My current goal is: [ここにゴールを記述]
```

### 解説

*   **System Context**: これをAntigravityに最初に読ませることで、「Java 21を使う」「JNAは使わない」「Active Renderingを守る」といった**Iron Rules**を徹底させます。
*   **Task Templates**: 「何を作るか」「どう検証するか」を明確にするためのフォーマットです。Markdownのコードブロックになっているため、ここからコピーしてAntigravityに貼り付けやすくなっています。
*   **Quick Copy**: ファイルを読み込ませる機能がある場合、このワンライナーで既存のドキュメントを参照させるのが最も手軽です。