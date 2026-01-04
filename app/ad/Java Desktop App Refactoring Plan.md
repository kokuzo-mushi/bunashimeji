# **Bunashimeji リファクタリングおよびアーキテクチャ近代化に関する包括的調査報告書**

## **1\. 序論：レガシーデスクトップマスコットの技術的負債と近代化の必要性**

### **1.1 プロジェクトの背景と歴史的文脈**

「デスクトップマスコット」というソフトウェアカテゴリにおいて、「Shimeji」は象徴的な存在である。Yuki Yamada氏（Group Finity）によって開発されたオリジナルのShimejiは、Windowsのデスクトップ上をキャラクターが自由に歩き回り、増殖し、ウィンドウを投げ飛ばすというユニークな挙動で世界的な人気を博した1。その後、オープンソースコミュニティによって「Shimeji-ee (English Enhanced)」などの派生版が開発され、XMLによる動作定義の拡張や画像のカスタマイズ性が強化されてきた2。  
しかしながら、これらのプロジェクトの多くはJava 6からJava 8時代の技術スタックに強く依存しており、現代のソフトウェアエンジニアリングの観点からは深刻な「技術的負債」を抱えているのが現状である3。具体的には、AWT/Swingによる描画ロジックとキャラクターの思考ルーチンが密結合した「God Class（神クラス）」の存在、JNA (Java Native Access) やJNI (Java Native Interface) を用いた非効率的かつ不安定なネイティブAPI呼び出し、そして可変（Mutable）な状態管理によるスレッドセーフ性の欠如などが挙げられる。  
本プロジェクト「Bunashimeji」は、この古典的なShimejiの設計思想を継承しつつ、Java 21 (LTS) という最新のプラットフォーム上で完全に再構築することを目的としている。Java 21で導入されたProject Panama (Foreign Function & Memory API)、Records、Pattern Matching、Sealed Classesといった革新的な機能を活用することで、堅牢性、保守性、そしてパフォーマンスを劇的に向上させることが可能となる。本報告書では、既存コードベースの構造的欠陥を詳細に分析し、それらを解消するための具体的なリファクタリング戦略とアーキテクチャ設計を提案する。

### **1.2 技術的課題の鳥瞰**

現状のコードベース（test1ブランチ）は、Shimeji-eeの構造を色濃く残していると推測される2。主な技術的課題は以下の4点に集約される。

1. **関心の分離の欠如**: マスコットの「位置計算（Model）」、「描画（View）」、「XML動作定義の解釈（Controller/Presenter）」が単一のクラス（MascotクラスやManagerクラス）に混在している5。これにより、物理演算の修正が描画バグを引き起こしたり、その逆が発生したりするリスクが高い。  
2. **脆弱な状態管理**: マスコットの挙動（歩く、座る、落下するなど）が、整数定数（int STATE\_WALK \= 1など）と巨大なswitch文、あるいは複雑なif-elseチェーンによって管理されている6。これは可読性を損なうだけでなく、不正な状態遷移を防ぐ手立てがないことを意味する。  
3. **レガシーなネイティブ連携**: ウィンドウを最前面に表示したり、マウス入力を透過させたりするためにWindows API（User32.dll）を使用しているが、これらがJNA経由で行われている7。JNAはリフレクションを多用するためオーバーヘッドが大きく、また型安全性が保証されない。  
4. **可変データの氾濫**: 座標データや設定値が可変なJavaBeanとして実装されており、マルチスレッド環境下での予期せぬ副作用（Side Effects）を招きやすい8。

本報告書では、これらの課題に対し、MVPパターン、Stateパターン（Sealed Interfaces活用）、FFM APIによるFacadeパターン、そしてRecordsによる不変データモデルの導入を提案する。

## ---

**2\. 現状のアーキテクチャ分析：God Classとスパゲッティコードの解剖**

### **2.1 God Classとしての Mascot.java**

Shimeji系のソースコードにおいて、com.group\_finity.mascot.Mascot クラス（あるいはそれに相当するクラス）は、典型的な「God Class（神クラス）」の様相を呈している5。このクラスは、単一責任の原則（SRP）を著しく違反しており、その責務は多岐にわたる。

| 責務のカテゴリ | 具体的な処理内容 | 依存する技術 |
| :---- | :---- | :---- |
| **データ保持** | 座標(x, y)、速度(vx, vy)、現在の画像フレーム、HPなどのパラメータ | Primitive types, Java Beans |
| **レンダリング** | paintComponent等のオーバーライド、画像の読み込み、アフィン変換 | Java Swing (AWT), Java 2D |
| **ウィンドウ管理** | ウィンドウハンドルの取得、透明化処理、最前面固定 (SetWindowPos) | JNA, User32.dll |
| **行動ロジック** | 次の行動の決定、重力計算、壁・床との衝突判定 | 独自の物理演算ロジック |
| **スクリプト解釈** | XML (actions.xml) のパースと実行時の命令変換 | DOM/SAX Parser |

このように、Mascotクラスは「脳（思考）」と「体（表示）」と「環境（物理法則・OS）」をすべて内包してしまっている。例えば、マスコットが画面の右端で折り返す挙動を修正したい場合、開発者は描画ループやウィンドウAPIの呼び出しコードが混在する数千行のファイルの中から該当ロジックを探し出す必要がある10。これは保守性を著しく低下させる要因である。

### **2.2 XML駆動アーキテクチャの功罪**

Shimejiの柔軟性は、actions.xml や behaviors.xml によってユーザーが自由に挙動を定義できる点にある6。しかし、この柔軟性を支える実装は、Javaコード側での複雑なインタプリタロジックを必要とする。  
既存の実装では、XMLのタグ（例: \<Move\>, \<Stay\>, \<Sequence\>）をJavaのクラスにマッピングし、実行時にリフレクションや条件分岐を用いて処理を切り替えている12。この「XMLインタプリタ」部分がビジネスロジックと密結合しているため、新しい種類のアクション（例：ユーザーのクリックに反応するAI機能など）を追加しようとすると、XMLスキーマの変更だけでなく、インタプリタの広範な修正が必要となる。また、XMLの記述ミスが実行時例外（Runtime Exception）として現れやすく、デバッグが困難であるという問題もある。

### **2.3 JNAによるネイティブアクセスの限界**

Shimejiは「デスクトップマスコット」という性質上、通常のウィンドウアプリケーションとは異なる特殊な制御を必要とする。

* タスクバーに表示しない。  
* 常に最前面に表示する（Topmost）。  
* マウスイベントを透過させる（マスコットの画像がない部分はクリックが下のウィンドウに届く）。

これらを実現するために User32.dll の SetWindowLong や SetWindowPos を呼び出しているが、既存コードではJNA (Java Native Access) が使用されている7。JNAは Native.loadLibrary を通じて動的にDLLをロードし、インターフェース定義に基づいて関数をマッピングする。  
しかし、JNAには以下の欠点がある。

1. **パフォーマンス**: リフレクションベースの呼び出しであるため、JNIやFFMに比べて低速である。マスコットが多数（数十体）画面上に存在する場合、毎フレームごとのウィンドウ位置更新におけるオーバーヘッドが無視できなくなる。  
2. **型安全性**: Javaの型とCの型（ポインタや構造体）のマッピングが自動で行われるため、メモリレイアウトの不一致によるクラッシュ（Access Violation）が発生した際の原因特定が困難である7。  
3. **依存関係**: 外部ライブラリ（jna.jar）への依存が必要となる。

Java 21で導入されたProject Panama (FFM API) は、これらの問題を解決し、標準ライブラリのみで高性能かつ安全なネイティブアクセスを提供する14。

## ---

**3\. リファクタリング戦略：アーキテクチャ設計要件**

本プロジェクトの目標は、Bunashimejiを「維持可能なモダンアプリケーション」へと昇華させることである。そのために、以下の4つの柱に基づくアーキテクチャ刷新を提案する。

### **3.1 Separation of Concerns: MVP (Model-View-Presenter) パターンの導入**

GUIアプリケーションのアーキテクチャパターンとして、MVC (Model-View-Controller) が有名であるが、SwingやJavaFXのようなステートフルなGUIフレームワークにおいては、**MVP (Model-View-Presenter)**、特に **Passive View** パターンの適用が推奨される10。

#### **3.1.1 各コンポーネントの責務定義**

| コンポーネント | 役割 | 責務の詳細 | 依存関係 |
| :---- | :---- | :---- | :---- |
| **Model** | データと状態 | マスコットの座標、現在の状態（State）、設定値、画像データの保持。ビジネスロジック（物理演算など）はここに属するか、独立したServiceとして切り出す。 | ViewやPresenterを知らない（Pure Java）。 |
| **View** | 表示と入出力 | ウィンドウの描画、画像の更新、マウス入力の検知。ロジックを持たず、Presenterからの指示（setImage, moveTo）に受動的に従う。 | Presenterを知らない（Interface経由で通知）。Modelを直接参照しない。 |
| **Presenter** | 制御と仲介 | Viewからのイベント（クリック等）やタイマーイベント（Tick）を受け取り、Modelの状態を更新する。更新されたModelに基づいてViewを操作する。 | ModelとViewの双方を知っている。 |

#### **3.1.2 Passive Viewのメリット**

Passive Viewを採用することで、View（MascotWindow）は「画像の表示」と「ウィンドウ移動」という物理的な操作のみに特化する。これにより、テストが困難なGUIコードを最小限に抑え、複雑な挙動ロジック（PresenterとModel）をJUnit等の単体テストで検証可能にすることができる10。これは、バグの温床となっていた「God Class」を解体する上で最も効果的なアプローチである。

### **3.2 State Management: Sealed InterfacesとPattern Matchingによるステートマシン**

マスコットの挙動（State）は有限オートマトン（Finite State Machine）としてモデル化できる。従来は int 定数で管理されていたが、Java 21では **Sealed Interfaces（封印されたインターフェース）** と **Records** を組み合わせることで、型安全かつ表現力豊かなステートマシンを構築できる17。

#### **3.2.1 Sealed Interfacesの採用理由**

sealed interface MascotState permits Idling, Walking, Falling... のように定義することで、システム内に存在する状態をコンパイルレベルで固定できる。これにより、switch 式を用いた状態遷移ロジックにおいて、default 句を使わずに網羅性（Exhaustiveness）を保証できる19。新しい状態を追加した際に、修正漏れがあればコンパイルエラーとして検知できるため、保守性が飛躍的に向上する。

#### **3.2.2 データ随伴型状態（Algebraic Data Types）**

Recordsを用いることで、各状態に関連するデータを不変オブジェクトとして保持できる。

* Walking 状態：destination（目的地）、speed（速度）を持つ。  
* Falling 状態：velocity（現在の落下速度）、acceleration（加速度）を持つ。  
* Idling 状態：duration（待機時間）を持つ。

これにより、従来のように Mascot クラス内に fallVelocity のような「特定の状態でしか使わない変数」が散乱することを防ぎ、状態オブジェクトの中にデータをカプセル化できる21。

### **3.3 Safe Native Access: Project PanamaによるFacadeパターン**

Windows APIへのアクセスは、アプリケーションの安定性を左右する重要な要素である。Java 21のFFM API (Foreign Function & Memory API) を採用し、これを隠蔽するFacade（またはAdapter）パターンを適用する23。

#### **3.3.1 FFM APIの優位性**

FFM APIは、MethodHandle を通じてネイティブ関数を呼び出す。JITコンパイラによるインライン化などの最適化が効くため、JNAよりも高速である。また、Arena という概念により、ネイティブメモリの確保と解放のライフサイクルをJavaのスコープ（try-with-resources）と一致させることができるため、メモリリークのリスクを大幅に低減できる15。

#### **3.3.2 実装方針**

NativeWindowService というインターフェースを定義し、その実装として WindowsUser32Service を作成する。この実装クラス内部でのみFFM APIを使用し、外部（Presenterなど）には setWindowPosition(int x, int y) のような抽象度の高いメソッドのみを公開する。これにより、将来的にLinux (X11/Wayland) や macOS に対応する際も、インターフェースの実装を差し替えるだけで済むようになる。

### **3.4 Modern Java Idioms: ImmutabilityとRecords**

マルチスレッド環境（SwingのEDTとロジック計算スレッド）において、可変共有状態はバグの温床である。Java 21の **Records** を全面的に採用し、座標(Point)、サイズ(Dimension)、設定(Config)などを不変（Immutable）なデータキャリアとして定義する26。不変オブジェクトはスレッドセーフであるため、同期化コストを削減し、コードの予測可能性を高める。

## ---

**4\. リファクタリング・ロードマップ**

本プロジェクトは大規模な変更を伴うため、段階的に実施する必要がある。

### **Phase 1: 基盤整備とデータモデルの近代化**

1. **ビルドシステムの刷新**: Gradle (Kotlin DSL) を導入し、Java 21ツールチェーンを設定する。依存関係（JNA等）の整理を行う。  
2. **Recordsの導入**: Coordinates, Velocity, MascotConfig などの基本データ構造を record に置き換える。  
3. **ユーティリティの整備**: FFM APIを利用するための基盤クラス（Linkerのラッパーなど）を準備する。

### **Phase 2: ネイティブレイヤーの置換 (Project Panama)**

1. **User32 Facadeの実装**: SetWindowPos, GetWindowLong などのWindows APIをFFM APIを用いて実装する。  
2. **既存JNAの排除**: 現在のコードベースからJNA依存を削除し、新しいFacade経由でウィンドウ操作を行うように書き換える。この段階ではまだGod Classは残存していても良い。

### **Phase 3: ステートマシンの再構築**

1. **Sealed Interfacesの定義**: MascotState インターフェースと、各状態を表すRecordsを定義する。  
2. **ロジックの移行**: 既存のXMLインタプリタや巨大なswitch文を解析し、Pattern Matchingを用いた新しいステートマシン（BehaviorEngine）にロジックを移植する。

### **Phase 4: MVPへの分離と統合**

1. **Viewの抽出**: Mascot クラスから描画処理とSwing依存コードを MascotWindowView に切り出す。  
2. **Presenterの結合**: MascotPresenter を作成し、Phase 3で作成したステートマシンとPhase 2のネイティブサービス、Phase 4のViewを統合する。  
3. **God Classの消去**: 最終的に Mascot クラスを削除し、アプリケーションのエントリーポイントを刷新する。

## ---

**5\. 詳細設計とコード実装例 (Java 21\)**

以下に、提案するアーキテクチャの具体的な実装例を示す。これらはJava 21の機能を最大限に活用している。

### **5.1 State Pattern: Sealed InterfacesとPattern Matching**

Before (Legacy Code):  
従来のコードは定数と状態変数が混在し、可読性が低い。

Java

// Legacy Java Implementation  
public class Mascot {  
    private static final int STATE\_STAND \= 0;  
    private static final int STATE\_WALK \= 1;  
    private static final int STATE\_FALL \= 2;  
      
    private int state \= STATE\_STAND;  
    private int velocityY \= 0; // FALL状態でのみ使用される  
    private int targetX \= 0;   // WALK状態でのみ使用される

    public void tick() {  
        if (state \== STATE\_WALK) {  
            // 歩行ロジック...  
        } else if (state \== STATE\_FALL) {  
            // 落下ロジック...  
            if (onGround()) state \= STATE\_STAND;  
        }  
    }  
}

After (Java 21 Modern Implementation):  
Sealed Interfaceにより状態を型として定義し、Recordsでデータを保持。switch 式でロジックを記述する。

Java

// Modern Java 21 Implementation

// 1\. 状態の定義 (Sealed Interface)  
public sealed interface MascotState permits Idling, Walking, Falling, Dragged {  
    // 全状態で共通のメソッド（例：状態に入った時刻）  
    long enterTime();  
}

// 2\. 各状態の実装 (Records)  
// アイドリング状態：待機開始時間を持つ  
public record Idling(long enterTime) implements MascotState {}

// 歩行状態：目的地と速度を持つ  
public record Walking(long enterTime, int targetX, double speed) implements MascotState {}

// 落下状態：現在の垂直速度を持つ  
public record Falling(long enterTime, double velocityY) implements MascotState {}

// ドラッグ状態：マウスで掴まれている  
public record Dragged(long enterTime, int grabOffsetX, int grabOffsetY) implements MascotState {}

// 3\. Presenter内での状態遷移ロジック (Pattern Matching for Switch)  
public class MascotPresenter {  
    private MascotState currentState \= new Idling(System.currentTimeMillis());

    public void tick() {  
        // 次の状態を計算（不変オブジェクトとして新しい状態を生成）  
        currentState \= switch (currentState) {  
              
            // アイドリング中のロジック (Guarded Pattern)  
            case Idling(long start) when (System.currentTimeMillis() \- start \> 5000\) \-\>   
                new Walking(System.currentTimeMillis(), randomX(), 2.0); // 5秒経過で歩き出す  
              
            case Idling \_ \-\> currentState; // 待機継続

            // 歩行中のロジック (Record Patternsによる分解)  
            case Walking(long start, int target, double speed) \-\> {  
                moveMascotTowards(target, speed);  
                if (isAtTarget(target)) yield new Idling(System.currentTimeMillis());  
                else yield currentState;  
            }

            // 落下中のロジック  
            case Falling(long start, double vY) \-\> {  
                double newVy \= vY \+ GRAVITY;  
                moveMascotVertically(newVy);  
                if (isOnGround()) yield new Idling(System.currentTimeMillis());  
                else yield new Falling(start, newVy);  
            }

            // ドラッグ中はロジックによる遷移なし（マウスイベントで制御）  
            case Dragged \_ \-\> currentState;  
        };  
    }  
}

考察:  
このリファクタリングにより、状態ごとの変数がそれぞれのRecordに閉じ込められる（カプセル化）。また、switch 式は値を返す必要があるため、状態更新のロジックが明確になり、副作用を排除しやすくなる。コンパイラが全てのケースを網羅しているかチェックしてくれるため、バグの混入も防げる20。

### **5.2 Safe Native Access: Project Panama (FFM API)**

Before (JNA):  
型安全性が低く、オーバーヘッドが大きい。

Java

// Legacy JNA  
public interface User32 extends Library {  
    User32 INSTANCE \= Native.load("user32", User32.class);  
    boolean SetWindowPos(Pointer hWnd, Pointer hWndInsertAfter, int x, int y, int cx, int cy, int uFlags);  
}

After (Java 21 FFM API):  
厳密なメモリレイアウト定義と高性能な呼び出し。

Java

// Modern Java 21 FFM API Implementation  
package com.bunashimeji.nativeos.windows;

import java.lang.foreign.\*;  
import java.lang.invoke.MethodHandle;  
import static java.lang.foreign.ValueLayout.\*;

public class WindowsWindowService {  
    // Linkerの取得  
    private static final Linker LINKER \= Linker.nativeLinker();  
    // ライブラリのルックアップ (user32.dll)  
    private static final SymbolLookup USER32 \= SymbolLookup.libraryLookup("user32.dll", Arena.global());

    // FunctionDescriptor: 関数のシグネチャ（引数と戻り値のメモリ型）を定義  
    // BOOL SetWindowPos(HWND hWnd, HWND hWndInsertAfter, int X, int Y, int cx, int cy, UINT uFlags);  
    private static final FunctionDescriptor SET\_WINDOW\_POS\_DESC \= FunctionDescriptor.of(  
        JAVA\_INT,   // 戻り値 (BOOL)  
        ADDRESS,    // hWnd (Pointer)  
        ADDRESS,    // hWndInsertAfter (Pointer)  
        JAVA\_INT,   // X  
        JAVA\_INT,   // Y  
        JAVA\_INT,   // cx  
        JAVA\_INT,   // cy  
        JAVA\_INT    // uFlags  
    );

    // MethodHandleの生成（初回のみ実行）  
    private static final MethodHandle setWindowPosHandle \= LINKER.downcallHandle(  
        USER32.find("SetWindowPos").orElseThrow(),  
        SET\_WINDOW\_POS\_DESC  
    );

    // 定数定義 (User32.h)  
    private static final MemorySegment HWND\_TOPMOST \= MemorySegment.ofAddress(-1);  
    private static final int SWP\_NOSIZE \= 0x0001;  
    private static final int SWP\_NOMOVE \= 0x0002;  
    private static final int SWP\_NOACTIVATE \= 0x0010; // フォーカスを奪わない

    /\*\*  
     \* ウィンドウ位置を更新するFacadeメソッド  
     \*/  
    public void setWindowPosition(long hwndValue, int x, int y, int width, int height) {  
        // Confined Arena: スレッドローカルなメモリ割り当て（高速）  
        try (Arena arena \= Arena.ofConfined()) {  
            // Javaのlong値をネイティブポインタ(MemorySegment)に変換  
            MemorySegment hWnd \= MemorySegment.ofAddress(hwndValue);  
              
            try {  
                // invokeExactによる厳密な呼び出し  
                int result \= (int) setWindowPosHandle.invokeExact(  
                    hWnd,  
                    HWND\_TOPMOST, // 常に最前面  
                    x, y,  
                    width, height,  
                    SWP\_NOACTIVATE // 重要なフラグ：マスコットが作業の邪魔をしないようにする  
                );  
                  
                if (result \== 0\) {  
                    // エラーハンドリング: GetLastError()などを呼ぶ余地あり  
                    System.err.println("SetWindowPos failed.");  
                }  
            } catch (Throwable e) {  
                throw new RuntimeException("Native call failed", e);  
            }  
        }  
    }  
}

**考察**:

* FunctionDescriptor により、OS側の期待するデータ型をJava側で厳密に定義できる24。  
* Arena.ofConfined() を使用することで、ネイティブ呼び出しに伴う一時的なメモリ割り当てを、メソッドのスコープ内で安全かつ高速に行える28。これは、毎秒数十回（60FPS）呼び出される描画ループにおいて極めて重要である。  
* SWP\_NOACTIVATE フラグの指定は、デスクトップマスコットにおいてユーザーの作業（タイピングなど）を阻害しないために必須である29。

### **5.3 Immutable Data Model: Records**

**Before:**

Java

public class Point {  
    public int x;  
    public int y;  
    // コンストラクタ、Getter/Setter...  
}

**After:**

Java

public record Coordinates(int x, int y) {  
    // ユーティリティメソッドを追加可能（状態は変更せず新しいインスタンスを返す）  
    public Coordinates translate(int dx, int dy) {  
        return new Coordinates(this.x \+ dx, this.y \+ dy);  
    }  
}

public record WindowBounds(Coordinates position, int width, int height) {}

## ---

**6\. アーキテクチャ上の決定とその根拠：リスクと対策**

### **6.1 FFM API vs JNA**

なぜ使い慣れたJNAを捨てるのか？その答えは「Javaの未来との整合性」にある。JNAは素晴らしいライブラリだが、外部依存であり、Javaプラットフォームの進化（特にGraalVM Native ImageやProject LeydenなどのAOTコンパイル技術）との相性において、標準APIであるFFMに劣る24。Bunashimejiを「今後10年生きるプロジェクト」にするためには、FFMへの移行が不可欠である。ただし、FFMは記述量が増えるため、前述のようなFacadeクラスによる隠蔽が必須となる。

### **6.2 God Class解体のリスク**

巨大なクラスを分割する際、既存の挙動（特にXMLで定義された微妙なニュアンス）を壊すリスクがある。これを軽減するために、「Strangler Fig Pattern（絞め殺しの木パターン）」 の変形適用を推奨する。  
いきなりMascotクラスを削除するのではなく、まずMascotクラスの内部で新しいWindowsWindowServiceやMascotStateを使用するように書き換える。内部の実装を徐々にモダンなものに置き換えていき、最終的にMascotクラスが単なる薄い委譲（Delegation）ラッパーになった時点で、完全に削除する。この漸進的なアプローチにより、常に「動く状態」を維持しながらリファクタリングを進めることができる31。

### **6.3 クロスプラットフォームへの展望**

現状のShimejiはWindowsに特化している（User32.dll依存）。しかし、今回提案した NativeWindowService インターフェースを設けることで、Linux用の実装（XLibやWaylandプロトコルをFFMで叩く実装）やmacOS用の実装（Cocoa API）を追加することがアーキテクチャ上容易になる3。これはオープンソースプロジェクトとしての魅力を大きく高める要素となる。

## **7\. 結論**

本報告書で提案したリファクタリング計画は、Bunashimejiを単なる「古いソフトの修正版」ではなく、「Java 21のショーケース」として再生させるものである。MVPパターンによる関心の分離、Sealed Interfacesによる堅牢なステートマシン、そしてFFM APIによる安全なネイティブ連携は、デスクトップマスコット開発における新しいスタンダードとなり得る。この変革により、開発者は技術的負債との戦いから解放され、より創造的な機能（AI連携や新しいインタラクション）の実装に注力できるようになるだろう。

#### **引用文献**

1. Shimeji-ee Desktop Pet \- Kilkakon.com, 12月 29, 2025にアクセス、 [https://kilkakon.com/shimeji/](https://kilkakon.com/shimeji/)  
2. Valkryst/VShimeji: A fork of Kilkakon's version of Shimeji-ee, with additional UI and performance improvements. \- GitHub, 12月 29, 2025にアクセス、 [https://github.com/Valkryst/VShimeji](https://github.com/Valkryst/VShimeji)  
3. DalekCraft2/Shimeji-Desktop: A port of Shimeji-ee from JRE 6 to JDK 11\. Also has many code changes and bug fixes, but attempts to preserve backward compatibility. \- GitHub, 12月 29, 2025にアクセス、 [https://github.com/DalekCraft2/Shimeji-Desktop](https://github.com/DalekCraft2/Shimeji-Desktop)  
4. 1月 1, 1970にアクセス、 [https://github.com/kokuzo-mushi/bunashimeji/tree/test1](https://github.com/kokuzo-mushi/bunashimeji/tree/test1)  
5. Trying to run this, but... · Issue \#16 · estenv/linux-shimeji \- GitHub, 12月 29, 2025にアクセス、 [https://github.com/asdfman/linux-shimeji/issues/16](https://github.com/asdfman/linux-shimeji/issues/16)  
6. shimeji-ee \- Readme.wiki \- Google Code, 12月 29, 2025にアクセス、 [https://code.google.com/archive/p/shimeji-ee/wikis/Readme.wiki](https://code.google.com/archive/p/shimeji-ee/wikis/Readme.wiki)  
7. Improper thread detaching causes deadlock in LDR on Windows 10+ · Issue \#1479 · java-native-access/jna \- GitHub, 12月 29, 2025にアクセス、 [https://github.com/java-native-access/jna/issues/1479](https://github.com/java-native-access/jna/issues/1479)  
8. How do you refactor a God class? \- Stack Overflow, 12月 29, 2025にアクセス、 [https://stackoverflow.com/questions/14870377/how-do-you-refactor-a-god-class](https://stackoverflow.com/questions/14870377/how-do-you-refactor-a-god-class)  
9. Anti-patterns \- Code Quality Docs, 12月 29, 2025にアクセス、 [https://docs.embold.io/anti-patterns/](https://docs.embold.io/anti-patterns/)  
10. Refactoring an Activity to use MVP | by David Rawson \- Medium, 12月 29, 2025にアクセス、 [https://drawson.medium.com/refactoring-an-activity-to-use-mvp-d9e4eccde919](https://drawson.medium.com/refactoring-an-activity-to-use-mvp-d9e4eccde919)  
11. Readme | PDF \- Scribd, 12月 29, 2025にアクセス、 [https://www.scribd.com/document/502111288/Readme](https://www.scribd.com/document/502111288/Readme)  
12. shimeji-ee/conf/actions.xml at master \- GitHub, 12月 29, 2025にアクセス、 [https://github.com/TigerHix/shimeji-ee/blob/master/conf/actions.xml](https://github.com/TigerHix/shimeji-ee/blob/master/conf/actions.xml)  
13. Can I change my Windows desktop wallpaper programmatically in Java/Groovy?, 12月 29, 2025にアクセス、 [https://stackoverflow.com/questions/4750372/can-i-change-my-windows-desktop-wallpaper-programmatically-in-java-groovy](https://stackoverflow.com/questions/4750372/can-i-change-my-windows-desktop-wallpaper-programmatically-in-java-groovy)  
14. Project Panama: Interconnecting JVM and native code \- OpenJDK, 12月 29, 2025にアクセス、 [https://openjdk.org/projects/panama/](https://openjdk.org/projects/panama/)  
15. JEP 442: Foreign Function & Memory API (Third Preview) \- OpenJDK, 12月 29, 2025にアクセス、 [https://openjdk.org/jeps/442](https://openjdk.org/jeps/442)  
16. Refactoring WinForm ClickNCode to MVP Passive View \- Stack Overflow, 12月 29, 2025にアクセス、 [https://stackoverflow.com/questions/760961/refactoring-winform-clickncode-to-mvp-passive-view](https://stackoverflow.com/questions/760961/refactoring-winform-clickncode-to-mvp-passive-view)  
17. 7 Sealed Classes \- Java \- Oracle Help Center, 12月 29, 2025にアクセス、 [https://docs.oracle.com/en/java/javase/25/language/sealed-classes-and-interfaces.html](https://docs.oracle.com/en/java/javase/25/language/sealed-classes-and-interfaces.html)  
18. Java 21 Pattern Matching Tutorial // nipafx, 12月 29, 2025にアクセス、 [https://nipafx.dev/java-21-pattern-matching/](https://nipafx.dev/java-21-pattern-matching/)  
19. How to use pattern matching in Java \- BellSoft, 12月 29, 2025にアクセス、 [https://bell-sw.com/blog/a-guide-to-pattern-matching-for-switch-in-java-21/](https://bell-sw.com/blog/a-guide-to-pattern-matching-for-switch-in-java-21/)  
20. JEP 441: Pattern Matching for switch \- OpenJDK, 12月 29, 2025にアクセス、 [https://openjdk.org/jeps/441](https://openjdk.org/jeps/441)  
21. Record Patterns \- Java \- Oracle Help Center, 12月 29, 2025にアクセス、 [https://docs.oracle.com/en/java/javase/22/language/record-patterns.html](https://docs.oracle.com/en/java/javase/22/language/record-patterns.html)  
22. A Leap Towards Expressive Coding With Record Patterns In Java 21 \- Payara, 12月 29, 2025にアクセス、 [https://payara.fish/blog/a-leap-towards-expressive-coding-with-record-patterns-in-java-21/](https://payara.fish/blog/a-leap-towards-expressive-coding-with-record-patterns-in-java-21/)  
23. From C to Java Code using Panama \- Mostly nerdless, 12月 29, 2025にアクセス、 [https://mostlynerdless.de/blog/2023/12/11/from-c-to-java-code-using-panama/](https://mostlynerdless.de/blog/2023/12/11/from-c-to-java-code-using-panama/)  
24. Foreign Function & Memory API in Native Image \- GraalVM, 12月 29, 2025にアクセス、 [https://www.graalvm.org/jdk21/reference-manual/native-image/dynamic-features/foreign-interface/](https://www.graalvm.org/jdk21/reference-manual/native-image/dynamic-features/foreign-interface/)  
25. Foreign Function & Memory API to Bridge the Gap between Java and Native Libraries, 12月 29, 2025にアクセス、 [https://www.infoq.com/news/2023/10/foreign-function-and-memory-api/](https://www.infoq.com/news/2023/10/foreign-function-and-memory-api/)  
26. Pattern Matching for Switch and Record Patterns in Java 21 | by Emilie Robichaud \- Medium, 12月 29, 2025にアクセス、 [https://emilie-robichaud.medium.com/pattern-matching-for-switch-and-record-patterns-in-java-21-979d034b3c5](https://emilie-robichaud.medium.com/pattern-matching-for-switch-and-record-patterns-in-java-21-979d034b3c5)  
27. Exploring Java's Foreign Function and Memory API (FFM) in Java 21: A Game-Changer for Native Interactions | by Mayuri Yadav | Medium, 12月 29, 2025にアクセス、 [https://medium.com/@mayuriy078/exploring-javas-foreign-function-and-memory-api-ffm-in-java-21-a-game-changer-for-native-342606a534b6](https://medium.com/@mayuriy078/exploring-javas-foreign-function-and-memory-api-ffm-in-java-21-a-game-changer-for-native-342606a534b6)  
28. Control taskbar in Windows from java using FFM and winapi \- Stack Overflow, 12月 29, 2025にアクセス、 [https://stackoverflow.com/questions/79371077/control-taskbar-in-windows-from-java-using-ffm-and-winapi](https://stackoverflow.com/questions/79371077/control-taskbar-in-windows-from-java-using-ffm-and-winapi)  
29. setwindowpos (user32) \- PInvoke.net, 12月 29, 2025にアクセス、 [https://www.pinvoke.net/default.aspx/user32.setwindowpos](https://www.pinvoke.net/default.aspx/user32.setwindowpos)  
30. SetWindowPos function (winuser.h) \- Win32 apps | Microsoft Learn, 12月 29, 2025にアクセス、 [https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setwindowpos](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setwindowpos)  
31. No trivial god-class refactoring \- Software Engineering Stack Exchange, 12月 29, 2025にアクセス、 [https://softwareengineering.stackexchange.com/questions/423392/no-trivial-god-class-refactoring](https://softwareengineering.stackexchange.com/questions/423392/no-trivial-god-class-refactoring)  
32. Refactoring a legacy codebase with a god Repository and incomplete Clean Architecture, 12月 29, 2025にアクセス、 [https://softwareengineering.stackexchange.com/questions/457116/refactoring-a-legacy-codebase-with-a-god-repository-and-incomplete-clean-archite](https://softwareengineering.stackexchange.com/questions/457116/refactoring-a-legacy-codebase-with-a-god-repository-and-incomplete-clean-archite)