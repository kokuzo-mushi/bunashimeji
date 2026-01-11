# **Shimeji Neo Modernization \- Phase 2 (Logic & Scripting) Technical Research Report**

## **1\. 序論：デスクトップマスコットの進化と近代化の必要性**

### **1.1 背景と現状の課題**

「Shimeji Neo」プロジェクトは、長年にわたり愛されてきたデスクトップマスコット「Shimeji」のアーキテクチャを、現代のJavaエコシステム（Java 21）に合わせて刷新する試みである。オリジナルのShimejiおよびその派生版（Shimeji-eeなど）は、マスコットの振る舞いを定義するためにXMLファイル（behaviors.xml）を使用している1。このXMLベースのアプローチは、プログラミングの知識がないユーザーでも編集可能であるという利点を持つ一方で、マスコットの「知能」を表現する上で深刻な制約となっている。  
現状のXMLによる定義は、本質的にステートレスな「条件（Condition）とアクション（Action）のペア」のリスト評価に過ぎない3。例えば、「マウスで掴まれた」というイベントが発生した際、マスコットは即座にその状態へ遷移するが、その前の文脈（何をしていたか、どのような感情であったか）を保持・参照することは困難である。また、複雑な条件分岐や、時間の経過に伴う段階的な行動変化（例：「お腹が空いたので冷蔵庫を探し、なければ寝る」といったシーケンス）を記述しようとすると、XMLは爆発的に肥大化し、可読性と保守性が著しく低下する。これは、いわゆる「スパゲッティコード」のXML版とも言える状況を生み出しており、マスコットの表現力を「ランダムに動くだけ」の存在に留めている主因である。

### **1.2 近代化の目的と技術的選定**

本フェーズ（Phase 2）の目的は、この静的なXML定義を、動的かつチューリング完全なスクリプティング環境へと置換、あるいは拡張することにある。これにより、Modder（ユーザー開発者）は条件分岐、ループ、変数による状態保持、外部環境との高度な相互作用を含む「AI（人工知能）」を記述できるようになる。  
ランタイムとしてJava 21を採用し、スクリプティングエンジンにはOracle Labsが開発する**GraalJS**（GraalVM JavaScript）を選定した。GraalJSはECMAScript 2024仕様に準拠しており5、従来のNashornやRhinoと比較して圧倒的なパフォーマンスと、Javaとのシームレスな相互運用性（Polyglot API）を提供する。特に、Java 21の仮想スレッド（Project Loom）やGraalVMのJITコンパイラ技術との親和性は、数百体のマスコットを同時に60FPSで描画・制御する「Shimeji」特有の過酷な要件を満たす上で決定的な要素となる。  
本レポートでは、シニアJavaアーキテクトの視点から、GraalJSの統合パターン、ゲームループにおけるパフォーマンス最適化、安全性（サンドボックス化）、そしてユーザーが直感的にAIを記述できるModding APIの設計について、詳細な調査結果と推奨アーキテクチャを提案する。

## ---

**2\. Scripting Engine Integration (GraalJS)**

デスクトップマスコットは、一般的なサーバーサイドアプリケーションとは異なり、「リアルタイム性（60FPSの維持）」と「多重性（数十〜数百のインスタンス）」という二つの相反する制約の中で動作する必要がある。GraalJSの統合においては、エンジンのライフサイクル管理と実行コンテキストの分離戦略が、メモリ効率と応答速度を決定づける。

### **2.1 Integration Pattern: Context Management Strategy**

GraalJSにおけるスクリプト実行環境は、主にEngineとContextという二つの概念で構成される。EngineはJITコンパイラやコードキャッシュ（Code Cache）を管理するスレッドセーフな共有リソースであり、Contextはグローバル変数や実行状態を保持するインスタンス固有の環境である6。

#### **2.1.1 Single Engine, Separate Contexts (推奨パターン)**

Shimejiのアーキテクチャにおいて最も推奨されるのは、アプリケーション全体で単一のEngineインスタンスを共有し、マスコット1体ごとに個別のContextを生成する「**Shared Engine / Separate Contexts**」パターンである。

* コードキャッシュの共有とWarmup効率:  
  GraalJSは、実行されたJavaScriptコードをAST（抽象構文木）に変換し、さらにTruffleフレームワークを通じて最適化されたマシンコードへとコンパイルする。このコンパイル結果はEngineレベルでキャッシュされる6。したがって、全てのマスコットが同じスクリプト（例：behavior.js）を実行する場合、Engineを共有することで、2体目以降のマスコットはコンパイル済みのコードを即座に利用でき、起動時間とCPU負荷が劇的に低減される7。  
* 状態の分離（Isolation）:  
  マスコットはそれぞれ個別の「記憶（状態）」を持つ必要がある。あるマスコットが変数state \= "hungry"を設定したとしても、他のマスコットに影響を与えてはならない。Contextを分離することで、グローバルスコープ（Global Scope）の汚染を完全に防ぎ、各マスコットが独立したJavaScript環境を持つことが保証される。これは、ユーザーが作成したMod同士の変数名の競合を防ぐ上でも不可欠である。

#### **2.1.2 実装設計とContext構成**

以下に、Java 21とGraalJS 25.0.0を用いた推奨セットアップコードを示す。ここでは、HostAccessによるJavaオブジェクトへのアクセス制御と、共有エンジンの設定を行っている。

Java

import org.graalvm.polyglot.\*;  
import java.io.ByteArrayOutputStream;

public class ScriptEngineManager {  
    // アプリケーション全体で共有する単一のEngine  
    // コードキャッシュとJITコンパイルの状態を保持する  
    private static final Engine SHARED\_ENGINE \= Engine.newBuilder("js")  
           .option("engine.WarnInterpreterOnly", "false") // 運用環境での警告抑制  
           .build();

    /\*\*  
     \* マスコット個別の実行コンテキストを作成するファクトリメソッド  
     \* @param mascotAPI 公開するマスコット操作API  
     \* @return 設定済みのContext  
     \*/  
    public Context createMascotContext(MascotAPI mascotAPI) {  
        // セキュリティ設定: 明示的に許可したメソッドのみアクセス可能にする  
        HostAccess hostAccess \= HostAccess.newBuilder(HostAccess.EXPLICIT)  
               .allowListAccess(true)  // JS Array \<-\> Java List  
               .allowMapAccess(true)   // JS Object \<-\> Java Map  
               .build();

        Context.Builder builder \= Context.newBuilder("js")  
               .engine(SHARED\_ENGINE) // エンジン共有による最適化  
               .allowHostAccess(hostAccess)  
               .allowPolyglotAccess(PolyglotAccess.NONE) // 他言語連携は不要  
               .allowCreateThread(false) // スクリプト内でのスレッド生成禁止  
               .allowIO(false)           // ファイルI/Oの完全禁止 (サンドボックス)  
               .out(new ByteArrayOutputStream()) // 標準出力を抑制またはキャプチャ  
               .err(new ByteArrayOutputStream());

        Context context \= builder.build();

        // APIオブジェクトの注入 (Globalスコープに 'mascot' として公開)  
        context.getBindings("js").putMember("mascot", mascotAPI);  
          
        // 環境情報の注入 (ウィンドウ情報など)  
        context.getBindings("js").putMember("env", new EnvironmentAPI());

        return context;  
    }  
}

### **2.2 Performance Optimization in the Game Loop**

Shimejiは60FPS（約16.6msごとの更新）で動作するゲームループを持つ9。Javaのメインループから、数百のContextに対して毎フレームJavaScript関数を呼び出すオーバーヘッドは無視できない問題となる。

#### **2.2.1 Boundary Crossing Overheadの最小化**

JavaからJavaScript（Guest Language）を呼び出す際、またはその逆の際、Truffleフレームワークは境界（Boundary）を超えるための処理を行う。頻繁なクロスオーバーはパフォーマンス劣化の原因となる11。

1. Sourceの事前パースとキャッシュ:  
   スクリプトファイル（behavior.js）の内容は、Sourceオブジェクトとして一度だけパースし、アプリケーション全体でキャッシュすべきである。context.eval("js", string)を毎フレーム呼び出すのは絶対に行ってはならない。毎回パース処理が走り、深刻なラグを生む。  
2. Function参照のキャッシュと直接実行:  
   初回実行時にスクリプトが返す「更新関数（update function）」への参照をValueオブジェクトとしてJava側に保持し、毎フレームそのValue.execute()を呼び出す方式を採用する。これにより、名前解決（Lookup）のコストを回避できる12。  
   Java  
   // 初回ロード時  
   Source source \= Source.newBuilder("js", scriptContent, "behavior.js").build();  
   Value exports \= context.eval(source);  
   Value updateFunc \= exports.getMember("tick"); // 関数参照を取得・保持

   // 毎フレームのループ内  
   if (updateFunc.canExecute()) {  
       updateFunc.executeVoid(deltaTime); // キャッシュした関数を直接実行  
   }

3. JavaのFunctional Interfaceへのキャスト:  
   さらに高速化を図る場合、GraalJSのValue.as(Class\<T\>)メソッドを使用して、JavaScript関数をJavaのインターフェース（例：Consumer\<Double\>）に変換（キャスト）して保持する手法が有効である。これにより、Truffleの最適化によりJavaのネイティブ呼び出しに近い速度まで最適化される可能性がある。

#### **2.2.2 メモリ管理とGC圧力**

各ContextはJavaヒープメモリを消費する。数百体を起動する場合、各Contextが保持するオブジェクト数がGC（ガベージコレクション）の圧力を高めるリスクがある。

* **対策:** スクリプト内で一時オブジェクト（{x: 1, y: 2}のようなオブジェクト）を毎フレーム生成することを避けるよう、Modder向けのガイドラインを整備する。また、Java側から渡すオブジェクトは再利用可能な状態（Mutable）で渡し、毎フレームnewすることを避ける設計が求められる。

### **2.3 Sandboxing & Security**

ユーザー作成のスクリプトを実行する以上、悪意のあるコード（システム破壊、無限ループによるCPU占有、メモリ枯渇攻撃）への対策は必須である。GraalJSは強力なサンドボックス機能を提供している。

#### **2.3.1 リソース制限 (Resource Limits)**

単にAPIを隠蔽するだけでなく、実行リソースそのものを制限することでDoS攻撃を防ぐ。Java 21環境下でのGraalJSは、以下の制限設定が可能である14。

* **CPU時間制限:** sandbox.MaxCPUTimeを設定し、1回のtick呼び出しが例えば10ミリ秒を超えた場合に強制的に例外をスローして停止させる。これにより、while(true){}のような無限ループが書かれても、アプリケーション全体がフリーズすることを防げる。  
* **メモリ制限:** sandbox.MaxHeapMemoryまたはsandbox.MaxIsolateMemoryを設定し、スクリプトが確保できる最大メモリ量を制限する。配列の無限拡張などによるOutOfMemoryErrorを防ぐ。  
* **ステートメント制限:** sandbox.MaxStatementsにより、実行可能な命令数を制限することも可能だが、時間制限の方が直感的で扱いやすい。

#### **2.3.2 禁止事項の徹底 (HostAccess Policy)**

HostAccess.EXPLICITを使用することは前述したが、具体的に何を禁止すべきかを列挙する。

* **java.lang.Systemへのアクセス禁止:** System.exit()やSystem.getProperty()へのアクセスは遮断する。  
* **リフレクションの禁止:** getClass()メソッドなどを通じて任意のJavaクラスへアクセスされることを防ぐ。HostAccess.Builderの設定でデフォルトで禁止されているが、明示的に確認が必要である。  
* **ファイル操作の禁止:** java.io.Fileやjava.nio関連クラスは一切渡さない。マスコットができるのは「画面内での移動」と「特定の許可されたデータの読み書き（専用API経由）」のみとする。

## ---

**3\. State Machine Architecture**

従来のbehaviors.xmlは、マスコットの状態を明示的に管理しておらず、条件式の羅列によって擬似的な振る舞いを決定していた。これを「**Hierarchical State Machine (HSM: 階層型ステートマシン)**」または「**Behavior Tree (BT: ビヘイビアツリー)**」に移行することで、複雑なAIの構築が可能となる。ここでは、Shimejiの特性（デスクトップ上での非同期インタラクション）に最適なパターンを分析する。

### **3.1 Design Pattern Comparison: HSM vs. BT**

ゲームAI開発において、HSMとBTは双璧をなすアーキテクチャであるが、その特性は大きく異なる。

#### **3.1.1 Behavior Tree (BT) の特性とShimejiへの適用**

Behavior Treeは、ノードの階層構造（Selector, Sequence, Decoratorなど）を用いて、毎フレーム（または定期的に）ルートから木を走査し、実行すべきアクションを決定する16。

* **メリット:** モジュール性が高い。アクション（例：「歩く」）を再利用しやすい。優先順位の管理（Priority Selector）が得意。  
* **デメリット:** 毎フレームのツリー走査（Traversal）は、数百体のマスコットが稼働する場合、CPU負荷が高くなる可能性がある。また、状態を持たない（Stateless）ことが基本であるため、「今何をしているか」という文脈を維持するためにBlackboard（共有メモリ）への依存が高まり、設計が複雑化しやすい19。

#### **3.1.2 Hierarchical State Machine (HSM) の特性とShimejiへの適用**

State Machineは、「状態（State）」と「遷移（Transition）」によって定義される。階層型（Hierarchical）にすることで、「地上にいる（Grounded）」状態の中に「歩く（Walk）」「立つ（Idle）」などのサブ状態を持つことができ、状態爆発を防げる20。

* **メリット:** 「今、マスコットは何をしているか？」が明確である。状態遷移がイベント駆動（Event-Driven）で発生するため、毎フレームの再評価コストが低い。マウスイベント（Drag, Drop）のような割り込み処理を「Global Transition」として定義しやすい。  
* **デメリット:** 遷移ロジック（Transition Logic）が複雑になりがちで、状態間の結合度が強くなる（スパゲッティ化のリスク）。

#### **3.1.3 推奨アーキテクチャ: Hybrid Event-Driven State Machine**

Shimejiのユースケース（デスクトップマスコット）においては、**HSMの方が適している**と結論付ける。理由は以下の通りである。

1. **インタラクティブ性の高さ:** ユーザーがマスコットをマウスで掴んだり、ウィンドウを動かしたりといった「非同期イベント」が頻発する。これらは「現在の状態に関わらず、強制的に特定の状態（例：Dragged）へ遷移させる」というステートマシンのパラダイムと非常に相性が良い。Behavior Treeでは、これらの割り込みを処理するためにツリー全体にガード条件を散りばめる必要があり、可読性が落ちる21。  
2. **直感的な記述:** Modderにとって、「今、歩いている」「今、寝ている」という「状態」ベースの思考は、ツリー構造の走査ロジックよりも直感的である。

ただし、各Stateの内部実装（アクションの実行）には、Behavior Treeの「Sequence」の概念（コルーチン）を取り入れることで、両者のいいとこ取りを行う「ハイブリッド」構成を提案する。

### **3.2 Conceptual Class Diagram & Architecture**

この設計では、Java側がステート管理の基盤を提供し、JavaScript側が各ステートの振る舞い（Behavior）を記述する。

コード スニペット

classDiagram  
    class AIController {  
        \-State currentState  
        \-Map\<String, State\> states  
        \+changeState(String stateName)  
        \+handleEvent(Event e)  
        \+tick(double deltaTime)  
    }  
      
    class State {  
        \<\<Interface\>\>  
        \+onEnter()  
        \+onExit()  
        \+onUpdate(double deltaTime)  
        \+onEvent(Event e)  
    }  
      
    class JSStateAdapter {  
        \-Value jsStateObject  
        \+onEnter()  
        \+onUpdate()  
    }  
      
    class ScriptEnvironment {  
        \+MascotAPI mascot  
        \+EnvironmentAPI env  
    }  
      
    AIController \--\> State : manages  
    State \<|-- JSStateAdapter : implements  
    JSStateAdapter \--\> ScriptEnvironment : uses

**Java側の実装スケルトン:**

Java

public abstract class State {  
    protected final Mascot mascot;  
      
    public State(Mascot mascot) { this.mascot \= mascot; }  
      
    public abstract void enter();  
    public abstract void exit();  
    public abstract void update(double deltaTime);  
      
    // イベントハンドラ（割り込み用）  
    public boolean handleEvent(MascotEvent event) { return false; }  
}

public class ScriptState extends State {  
    private final Value jsStateObject;  
      
    public ScriptState(Mascot mascot, Value jsStateObject) {  
        super(mascot);  
        this.jsStateObject \= jsStateObject;  
    }  
      
    @Override  
    public void enter() {  
        Value onEnter \= jsStateObject.getMember("enter");  
        if (onEnter\!= null) onEnter.executeVoid();  
    }  
      
    @Override  
    public void update(double deltaTime) {  
        // JS側のupdateを実行  
        // ここでGeneratorのtickを進める処理が入る（後述）  
        Value onUpdate \= jsStateObject.getMember("update");  
        if (onUpdate\!= null) onUpdate.executeVoid(deltaTime);  
    }  
      
    //... exit, handleEventも同様に委譲  
}

このアーキテクチャにより、Modderは以下のような構造でマスコットのAIを記述できる。

JavaScript

// behavior.js (ユーザー作成)  
const IdleState \= {  
    enter: function() {  
        mascot.setPose("stand");  
    },  
    update: function(dt) {  
        if (Math.random() \< 0.01) {  
            mascot.changeState("Walk");  
        }  
    }  
};

const WalkState \= {  
    enter: function() {  
        mascot.setPose("walk");  
        this.targetX \= mascot.getX() \+ 100;  
    },  
    update: function(dt) {  
        mascot.moveTowards(this.targetX);  
        if (mascot.getX() \>= this.targetX) {  
            mascot.changeState("Idle");  
        }  
    }  
};

## ---

**4\. Modding API Design & Asynchronous Handling**

ここが本レポートの最も重要な技術的提案事項である。Shimejiの行動定義において最も難しいのは、「指定した場所まで歩く」「3秒間待つ」といった\*\*時間のかかる処理（Asynchronous/Long-running actions）\*\*を、60FPSの同期ループ内でどのように記述させるか、という点である。

### **4.1 The Concurrency Problem**

従来の単純なAPI設計では、以下のような問題が発生する。

* ブロッキング呼び出しの問題:  
  mascot.walkTo(100) と書いたとき、Java側で移動が完了するまで処理をブロックしてしまうと、ゲームループ全体が停止し、画面がフリーズする（FPSが0になる）。  
* コールバック地獄:  
  非同期APIにして mascot.walkTo(100, callback) とすると、複雑な行動（歩いて、座って、ジャンプする）を記述する際にネストが深くなり、可読性が壊滅する。

### **4.2 Solution: JavaScript Generators as Coroutines**

この問題を解決するために、JavaScript (ES6+) の標準機能である **Generator Functions (function\*)** を活用し、UnityのCoroutineのような「中断可能な関数実行」を実現するアーキテクチャを提案する23。  
Generatorを使用すると、関数実行を任意の行（yield）で一時停止し、呼び出し元（Javaエンジン）に制御を戻すことができる。Java側は次のフレームで再びジェネレータを再開（resume）させる。これにより、**同期的なコードの見た目で、非同期的な処理を記述**できる。

#### **4.2.1 High-Level API Design**

Modderには、以下のようなAPIサーフェイスを提供する。

JavaScript

// ユーザー定義のAIスクリプト  
function\* mainBehavior() {  
    while (true) {  
        // 1\. 右へ100px移動 (完了するまでここで待機)  
        yield mascot.walkTo(mascot.getX() \+ 100);  
          
        // 2\. "sit"ポーズに変更  
        mascot.setPose("sit");  
          
        // 3\. 60フレーム(約1秒)待機  
        yield mascot.wait(60);  
          
        // 4\. マウスカーソルの方へジャンプ  
        yield mascot.jumpTo(env.getMouseX(), env.getMouseY());  
    }  
}

ここで重要なのは、mascot.walkTo(...) などのAPIメソッドが、実際の移動処理を行うのではなく、**「移動タスクを表すオブジェクト（Command/Task Object）」を返す**という点である。yieldキーワードにより、そのタスクオブジェクトがJava側に渡される。

#### **4.2.2 Java-Side Implementation of Coroutine Runner**

Java側では、Generatorから返されたタスクオブジェクトを受け取り、そのタスクが完了するまでGeneratorの再開（next()の呼び出し）を保留する「スケジューラ」を実装する。  
**実装詳細フロー:**

1. **開始:** JavaがJSの mainBehavior() を呼び出し、Iterator (GraalJS Value) を取得する。  
2. **実行サイクル (Tick):**  
   * 現在実行中のタスク（currentTask）があるかチェックする。  
   * **Yes:** currentTask.update() を呼び出す。タスクが完了（isFinished() \== true）していなければ、そこでリターン（次のフレームへ）。  
   * **No:** iterator.execute("next") を呼び出し、JSを実行再開する。  
3. **Yield処理:**  
   * JSが yield task を実行すると、Java側に IteratorResult { value: task, done: false } が返る。  
   * Java側はこの value を currentTask として登録し、そのフレームの処理を終える。  
4. **終了処理:** done: true が返ってきたら、ジェネレータは終了である（ループさせるか、終了とする）。

**コードスケルトン (Java):**

Java

public class ScriptCoroutineRunner {  
    private Value generatorIterator;  
    private MascotTask currentTask; // 現在実行中の長時間タスク

    // スクリプトから返されるタスクの基底クラス  
    public interface MascotTask {  
        void start();  
        void update(double deltaTime);  
        boolean isFinished();  
    }

    public void update(double deltaTime) {  
        // 1\. 実行中のタスクがあれば更新  
        if (currentTask\!= null) {  
            currentTask.update(deltaTime);  
            if (\!currentTask.isFinished()) {  
                return; // タスク完了待ち。JSは進めない。  
            }  
            currentTask \= null; // タスク完了。次へ。  
        }

        // 2\. ジェネレータを進める (JSのコードを次のyieldまで実行)  
        if (generatorIterator \== null) return;

        // generator.next() を実行  
        Value nextResult \= generatorIterator.getMember("next").execute();  
          
        if (nextResult.getMember("done").asBoolean()) {  
            // Generator終了  
            return;   
        }

        // 3\. yieldされた値を取得  
        Value yieldValue \= nextResult.getMember("value");  
          
        // yieldされた値がMascotTaskなら登録、そうでなければ無視(1フレーム待機)  
        if (yieldValue.isHostObject() && yieldValue.asHostObject() instanceof MascotTask) {  
            this.currentTask \= (MascotTask) yieldValue.asHostObject();  
            this.currentTask.start();  
        }  
    }  
}

### **4.3 Handling Interrupts (Interruption Logic)**

ステートマシン（HSM）の利点を活かし、非同期イベントによる「割り込み（Interrupt）」を実装する。  
例えば、「歩いている途中（yield walkToの待機中）」に「マウスでドラッグされた」場合、Generatorの待機を強制的にキャンセルし、Draggedステートへ遷移させる必要がある。  
**Java側での割り込み処理:**

Java

public void handleDragEvent() {  
    // 現在のタスクを強制終了  
    if (currentTask\!= null) {  
        currentTask.cancel();   
        currentTask \= null;  
    }  
      
    // ジェネレータも破棄（またはリセット）  
    this.generatorIterator \= null;  
      
    // Draggedステートへ遷移  
    stateMachine.changeState("Dragged");  
}

また、Generatorの return() メソッドを呼び出すことで、JS側の finally ブロックを実行させ、クリーンアップ（リソース解放など）を行わせることも可能である26。

JavaScript

function\* walkState() {  
    try {  
        yield mascot.walkTo(100);  
    } finally {  
        // 割り込まれた場合でも実行される  
        console.log("Walk state terminated");  
    }  
}

### **4.4 Data Exchange: Proxy Objects & API Ergonomics**

GraalJSでは、JavaオブジェクトをそのままJSに渡すと、フィールドアクセスなどがJavaのセマンティクス（getter/setter）に依存する。よりJSらしい直感的なAPI（プロパティアクセス）を提供するために、ProxyObject インターフェースを利用するか、専用のラッパーAPIクラスを用意することを推奨する。  
**API設計表 (推奨):**

| Java Method | JS API Usage | 備考 |
| :---- | :---- | :---- |
| mascot.setAnchorX(int x) | mascot.x \= 100; | プロパティ代入に見せる |
| environment.getActiveWindows() | env.windows | 配列としてアクセス可能に |
| mascot.lookAt(int x, int y) | mascot.lookAt(target) | オブジェクトも引数に取れるようにオーバーロード |
| Task walkTo(int x) | yield mascot.walkTo(100) | コルーチン用のタスクオブジェクトを返す |

## ---

**5\. Security & Stability (詳細設計)**

Modding APIの公開は、システムへの攻撃経路を開くことと同義である。特にShimejiは常駐アプリであるため、安定性と安全性は最重要課題である。

### **5.1 HostAccess の厳格な運用**

前述の通り、HostAccess.EXPLICIT を使用し、許可リスト方式（Allow-list）を採用する。@HostAccess.Export アノテーションが付与されたメソッドのみがJSから見える。  
**危険なパターンの排除:**

* **戻り値の型:** APIメソッドが Object を返す場合、その実体が java.io.File などであってはならない。常に int, String などのプリミティブか、安全なラッパーオブジェクトを返すようにする。  
* **コールバック:** ユーザーが登録したコールバック関数（イベントリスナーなど）は、メモリリークの原因になりやすい。WeakReference を使用するか、ステート遷移時にリスナーを自動解除する仕組みをJava側に実装する。

### **5.2 Resource Limits (DoS対策)**

GraalVMのResource Limits機能を用い、以下の制限を強制する。

| 制限項目 | 設定値 (目安) | 理由 |
| :---- | :---- | :---- |
| **MaxCPUTime** | 10ms / tick | 1フレームの計算時間は16ms未満である必要がある。無限ループ防止。 |
| **MaxHeapMemory** | 32MB / context | 1体のマスコットが消費するメモリを制限。メモリリーク防止。 |
| **MaxStackDepth** | 64 | 再帰呼び出しによるスタックオーバーフロー防止。 |

### **5.3 Mod検証システム**

技術的なサンドボックスに加え、読み込むスクリプトのハッシュ値を検証する署名システムや、安全なModのみを配布するリポジトリの構築も長期的には検討すべきである。

## ---

**6\. Conclusion & Roadmap**

本レポートでは、Shimeji Neoのロジック近代化に向けた包括的な技術調査を行った。

1. **ランタイム:** Java 21 \+ GraalJS 25.0.0 を採用し、**Shared Engine / Separate Contexts** パターンで効率的なリソース管理を実現する。  
2. **AIアーキテクチャ:** **Event-Driven Hierarchical State Machine (HSM)** を基本とし、各ステート内部の振る舞い記述には **JavaScript Generators (Coroutines)** を採用するハイブリッド構成を推奨する。  
3. **API設計:** yield キーワードを活用した同期的記述スタイルにより、非同期アクションの複雑さを隠蔽し、Modderに直感的な開発環境を提供する。

このアーキテクチャにより、Shimejiは単なるランダム動作のマスコットから、外部環境を認識し、記憶を持ち、複雑なシナリオを実行可能な「デスクトップパートナー」へと進化することが可能となる。  
**推奨される次のステップ:**

1. **プロトタイプ作成:** SharedEngine と GeneratorRunner を含む最小限のJava実装を作成し、yield mascot.walk() が動作することを確認する。  
2. **API定義:** MascotAPI のインターフェース詳細を策定する。  
3. **レガシー移行:** 既存の behaviors.xml を解析し、自動的に等価なJSコードへ変換するコンバータの開発を検討する。

---

References:

1

#### **引用文献**

1. Readme | PDF \- Scribd, 12月 27, 2025にアクセス、 [https://www.scribd.com/document/502111288/Readme](https://www.scribd.com/document/502111288/Readme)  
2. shimeji-ee \- Readme.wiki \- Google Code, 12月 27, 2025にアクセス、 [https://code.google.com/archive/p/shimeji-ee/wikis/Readme.wiki](https://code.google.com/archive/p/shimeji-ee/wikis/Readme.wiki)  
3. Shimeji-ee Affordances Tutorial \- Kilkakon.com, 12月 27, 2025にアクセス、 [https://kilkakon.com/shimeji/affordances.php](https://kilkakon.com/shimeji/affordances.php)  
4. shimeji-ee/conf/behaviors.xml at master \- GitHub, 12月 27, 2025にアクセス、 [https://github.com/TigerHix/shimeji-ee/blob/master/conf/behaviors.xml](https://github.com/TigerHix/shimeji-ee/blob/master/conf/behaviors.xml)  
5. GraalJS Compatibility \- GraalVM, 12月 27, 2025にアクセス、 [https://www.graalvm.org/latest/reference-manual/js/JavaScriptCompatibility/](https://www.graalvm.org/latest/reference-manual/js/JavaScriptCompatibility/)  
6. Frequently Asked Questions \- Oracle Help Center, 12月 27, 2025にアクセス、 [https://docs.oracle.com/en/graalvm/enterprise/22/docs/reference-manual/js/FAQ/](https://docs.oracle.com/en/graalvm/enterprise/22/docs/reference-manual/js/FAQ/)  
7. Provide alternative to warming up multiple identical Contexts · Issue \#67 · oracle/graaljs, 12月 27, 2025にアクセス、 [https://github.com/graalvm/graaljs/issues/67](https://github.com/graalvm/graaljs/issues/67)  
8. Performance optimisation strategies (Context vs Engine reuse) · Issue \#935 · oracle/graaljs, 12月 27, 2025にアクセス、 [https://github.com/oracle/graaljs/issues/935](https://github.com/oracle/graaljs/issues/935)  
9. How to make a game loop for your idle game \- GitHub Gist, 12月 27, 2025にアクセス、 [https://gist.github.com/HipHopHuman/3e9b4a94b30ac9387d9a99ef2d29eb1a?permalink\_comment\_id=5724319](https://gist.github.com/HipHopHuman/3e9b4a94b30ac9387d9a99ef2d29eb1a?permalink_comment_id=5724319)  
10. How to make your game run at 60fps | by Tyler Glaiel \- Medium, 12月 27, 2025にアクセス、 [https://medium.com/@tglaiel/how-to-make-your-game-run-at-60fps-24c61210fe75](https://medium.com/@tglaiel/how-to-make-your-game-run-at-60fps-24c61210fe75)  
11. GraalVM \- Performance Issue A lot of time for invoke js method \- Stack Overflow, 12月 27, 2025にアクセス、 [https://stackoverflow.com/questions/77003734/graalvm-performance-issue-a-lot-of-time-for-invoke-js-method](https://stackoverflow.com/questions/77003734/graalvm-performance-issue-a-lot-of-time-for-invoke-js-method)  
12. Embedding Languages \- GraalVM, 12月 27, 2025にアクセス、 [https://www.graalvm.org/latest/reference-manual/embed-languages/](https://www.graalvm.org/latest/reference-manual/embed-languages/)  
13. Frequently Asked Questions \- Oracle Help Center, 12月 27, 2025にアクセス、 [https://docs.oracle.com/en/graalvm/enterprise/21/docs/reference-manual/js/FAQ/](https://docs.oracle.com/en/graalvm/enterprise/21/docs/reference-manual/js/FAQ/)  
14. Sandboxing \- GraalVM, 12月 27, 2025にアクセス、 [https://www.graalvm.org/latest/security-guide/sandboxing/](https://www.graalvm.org/latest/security-guide/sandboxing/)  
15. Polyglot Sandboxing \- Oracle Help Center, 12月 27, 2025にアクセス、 [https://docs.oracle.com/en/graalvm/jdk/21/docs/security-guide/polyglot-sandbox/](https://docs.oracle.com/en/graalvm/jdk/21/docs/security-guide/polyglot-sandbox/)  
16. State Machines vs Behavior Trees: designing a decision-making architecture for robotics, 12月 27, 2025にアクセス、 [https://www.polymathrobotics.com/blog/state-machines-vs-behavior-trees](https://www.polymathrobotics.com/blog/state-machines-vs-behavior-trees)  
17. Behavior Trees or Finite State Machines \- Opsive, 12月 27, 2025にアクセス、 [https://opsive.com/support/documentation/behavior-designer/behavior-trees-or-finite-state-machines/](https://opsive.com/support/documentation/behavior-designer/behavior-trees-or-finite-state-machines/)  
18. Behavior trees for AI: How they work \- Game Developer, 12月 27, 2025にアクセス、 [https://www.gamedeveloper.com/programming/behavior-trees-for-ai-how-they-work](https://www.gamedeveloper.com/programming/behavior-trees-for-ai-how-they-work)  
19. Is there any benefit to using a Behavior Tree for AI design vs Unity's Visual Scripting State Machine? : r/gamedev \- Reddit, 12月 27, 2025にアクセス、 [https://www.reddit.com/r/gamedev/comments/13mzcug/is\_there\_any\_benefit\_to\_using\_a\_behavior\_tree\_for/](https://www.reddit.com/r/gamedev/comments/13mzcug/is_there_any_benefit_to_using_a_behavior_tree_for/)  
20. Behaviour Trees versus State Machines | Queen Of Squiggles's Blog, 12月 27, 2025にアクセス、 [https://queenofsquiggles.github.io/guides/fsm-vs-bt/](https://queenofsquiggles.github.io/guides/fsm-vs-bt/)  
21. How to implement interrupts in a behavior tree \- AI \- Epic Developer Community Forums, 12月 27, 2025にアクセス、 [https://forums.unrealengine.com/t/how-to-implement-interrupts-in-a-behavior-tree/302331](https://forums.unrealengine.com/t/how-to-implement-interrupts-in-a-behavior-tree/302331)  
22. Behavior Tree with interrupted sequence \- Game Development Stack Exchange, 12月 27, 2025にアクセス、 [https://gamedev.stackexchange.com/questions/114125/behavior-tree-with-interrupted-sequence](https://gamedev.stackexchange.com/questions/114125/behavior-tree-with-interrupted-sequence)  
23. Handling synchronous flow in async generators JS \- Stack Overflow, 12月 27, 2025にアクセス、 [https://stackoverflow.com/questions/62041213/handling-synchronous-flow-in-async-generators-js](https://stackoverflow.com/questions/62041213/handling-synchronous-flow-in-async-generators-js)  
24. Async iteration and generators \- The Modern JavaScript Tutorial, 12月 27, 2025にアクセス、 [https://javascript.info/async-iterators-generators](https://javascript.info/async-iterators-generators)  
25. Iterators and generators \- JavaScript \- MDN Web Docs, 12月 27, 2025にアクセス、 [https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Iterators\_and\_generators](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Iterators_and_generators)  
26. Generators \- The Modern JavaScript Tutorial, 12月 27, 2025にアクセス、 [https://javascript.info/generators](https://javascript.info/generators)