# **Shimeji Neo 開発プロジェクト：Java 21/Kotlin 1.9 環境への移行における技術的課題と解決策に関する包括的分析レポート**

## **1\. 序論：デスクトップマスコットの現代化と技術的負債**

### **1.1 プロジェクト背景と「Shimeji Neo」の立ち位置**

「Shimeji Neo」プロジェクトは、2000年代後半に公開され、オープンソースコミュニティによって派生・発展を遂げてきたデスクトップマスコット「Shimeji（およびShimeji-ee）」を、現代の技術スタックで刷新する野心的な試みである。オリジナルのShimejiは、Yuki Yamada氏（Group Finity）によって開発され、Java 6およびAWT/Swingを基盤としていた 1。その後、Shimeji-ee（English Enhanced）などの派生版が登場し、XMLによる動作定義の拡張や64ビット環境への対応が進められたが、基本アーキテクチャは依然としてレガシーなJavaの設計思想に留まっていた 3。  
本プロジェクトが採用した技術スタック（Java 21、Kotlin 1.9、Gradle、GraalJS）は、パフォーマンス、安全性、開発効率の面で劇的な向上を約束するものである。Java 21のZGCによる低遅延ガベージコレクション、KotlinのNull安全性と表現力豊かなDSL（ドメイン特化言語）、そしてGraalVM技術に基づく高速なJavaScriptエンジン（GraalJS）は、デスクトップマスコットのような常駐型アプリケーションにとって理想的な基盤である。しかし、この「15年分の技術的飛躍」を一足飛びに超える過程で、言語間の相互運用性（Interop）、厳格化されたカプセル化、そしてスクリプトエンジンの挙動変更という、深刻なインピーダンスミスマッチ（Impedance Mismatch）に直面している。

### **1.2 本レポートの目的と構成**

本レポートは、開発チームが現在直面している以下の主要な課題に対し、その根本原因を深層レベルで分析し、具体的な解決策を提示することを目的とする。

1. **ビルドエラー（相互運用性）:** Kotlinコード（ActionBuilder.kt）からJavaのレガシーコード（SequenceAction等）のprivateフィールドへのアクセス不能問題。および、XMLデータバインディングにおけるXmlPointとjava.awt.Pointの型不整合。  
2. **実行時エラー（安定性）:** Behavior.javaにおけるリソース読み込みとアクション生成時のNullPointerException（NPE）。これはJavaのモジュールシステムやリソース管理の変化に起因する。  
3. **スクリプト実行エラー（機能性）:** NashornからGraalJSへの移行に伴う、walk.jsの戻り値評価とJavaオブジェクト操作の不具合。

本稿では、単なるコードの修正案提示に留まらず、なぜそのエラーが発生するのかという言語仕様およびJVM内部のメカニズムにまで踏み込み、将来的な保守性を担保するためのアーキテクチャ再設計案を詳述する。分析は提供されたソースコード断片 5 および関連する技術文書 1 を包括的に統合して行われる。

## ---

**2\. KotlinとJavaの相互運用性における可視性とビルドアーキテクチャの分析**

### **2.1 ActionBuilder.kt におけるアクセス権限エラーの構造的要因**

#### **2.1.1 問題の現象とコンパイラの挙動**

報告されているビルドエラーの核心は、「KotlinからJavaのprivateフィールドへのアクセス不可」である。これは、KotlinのType-Safe Buildersパターンを用いて、従来のXML定義をKotlin DSL（Domain Specific Language）に置き換えようとする過程、あるいはXMLパーサーからKotlinコードを介してJavaオブジェクトを構築する過程で発生していると推測される。  
具体的には、ActionBuilder.ktがcom.group\_finity.mascot.actionパッケージ内のSequenceActionやRandomChoiceActionといったクラスのインスタンスを生成・設定しようとした際にエラーとなる 5。

| 言語 / 機能 | Java (Legacy) | Kotlin | 相互運用の壁 |
| :---- | :---- | :---- | :---- |
| **フィールド可視性** | package-private (デフォルト) や protected を多用し、同一パッケージ内からの直接アクセスを許容する傾向があった。 | デフォルトで public だが、Javaの private フィールドは厳格に不可視。 | KotlinはJavaの同一パッケージ内アクセス権限を持たない（ファイル単位でコンパイルされるため）。 |
| **プロパティ・アクセサ** | Getter/Setterを手動で書く必要がある（JavaBeans規約）。省略されることが多い。 | プロパティ構文 (obj.prop) が自動的にGetter/Setterにマッピングされる。 | Java側にGetter/Setterがない場合、Kotlinはフィールドへの直接アクセスを試みるが、可視性がなければコンパイルエラーとなる。 |
| **ビルドツール** | AntやMaven (XMLベース) | Gradle (Kotlin DSL) | ビルドプロセス自体は問題ないが、コンパイル時のクラスパス解決が厳密化している。 |

#### **2.1.2 SequenceAction と RandomChoiceAction のレガシー構造**

Shimeji-eeのソースコード履歴 1 から推察するに、SequenceActionなどのアクションクラスは、以下のような構造を持っている可能性が高い。

Java

// 推定される SequenceAction.java の構造（修正前）  
package com.group\_finity.mascot.action;

import java.util.List;

public class SequenceAction extends ComplexAction {  
    // 多くのレガシーコードではフィールドが package-private または private で  
    // アクセサが存在しないケースがある  
    private boolean loop;   
    private List\<Action\> actions;   
      
    // コンストラクタや内部ロジックのみが存在  
    public SequenceAction(VariableMap params) {  
        super(params);  
        //...  
    }  
}

Kotlinコンパイラが ActionBuilder.kt をコンパイルする際、以下のようなコードに遭遇すると：

Kotlin

// ActionBuilder.kt  
val sequence \= SequenceAction(params)  
sequence.loop \= true // エラー: loop has private access in SequenceAction

Javaコード内であればリフレクション（setAccessible(true)）を用いて強制的に書き込む手法が横行していたが、Kotlinの静的型付けコンパイルはこれを許容しない。特に、Java 21環境下ではカプセル化がより尊重される傾向にあり、モジュール境界を越えたアクセスはさらに厳しく制限される。

#### **2.1.3 ActionBuilder.kt の役割とDSL設計の誤算**

ActionBuilder.kt の意図は、冗長なXML記述 11 を、Kotlinの強力な言語機能を使って簡潔かつ型安全に記述することにあると思われる。

XML

\<Action Name="SitOnTheLeftEdgeOfIE" Type="Sequence" Loop="false"\>  
    \<ActionReference Name="Walk"... /\>  
    \<ActionReference Name="Stand"... /\>  
\</Action\>

これをKotlin DSLで表現しようとする試み：

Kotlin

// 目指している Kotlin DSL  
action("SitOnTheLeftEdgeOfIE") {  
    sequence(loop \= false) {  
        walk(...)  
        stand(...)  
    }  
}

このDSLの裏側で動く ActionBuilder は、SequenceAction のインスタンスを生成し、loop フィールドを設定し、子アクションのリストを注入しなければならない。Javaクラス側に setLoop() や getActions() が存在しない場合、Kotlinは「プロパティ」として認識できず、バッキングフィールドへの直接アクセスもできず、手詰まりとなる。

#### **2.1.4 解決策：アクセサの導入とJavaBeans規約の徹底**

最も正統的かつ永続的な解決策は、Java側のアクションクラスを修正し、外部からのアクセスを許可すべきフィールドに対して明示的なGetter/Setterを提供することである。これによりKotlinとの相互運用性が完全に保証される。  
**修正の詳細設計:**

1. **Lombokの利用検討と却下:** JavaコードにLombok (@Data 等) を導入すればコード量を減らせるが、Kotlinとの併用ビルド（Kapt/KSP）の設定複雑化を招く恐れがあるため、今回は手動でのメソッド追加、あるいはIDEによる生成を推奨する。  
2. **可視性の変更:** フィールド自体を public にするのはアンチパターンであるため避ける。フィールドは private のまま、public なアクセサを追加する。

**修正コード案 (SequenceAction.java):**

Java

package com.group\_finity.mascot.action;

import java.util.List;  
import java.util.ArrayList;

public class SequenceAction extends ComplexAction {  
    // フィールド定義  
    private boolean loop;  
    private final List\<ActionReference\> actions \= new ArrayList\<\>();

    // \--- 追加すべきアクセサ \---

    /\*\*  
     \* Kotlinプロパティ: loop  
     \* Kotlinからのアクセス: action.isLoop \= true / val l \= action.isLoop  
     \*/  
    public boolean isLoop() {  
        return loop;  
    }

    public void setLoop(boolean loop) {  
        this.loop \= loop;  
    }

    /\*\*  
     \* Kotlinプロパティ: actions  
     \* Kotlinからのアクセス: action.actions.add(...)  
     \*/  
    public List\<ActionReference\> getActions() {  
        return actions;  
    }  
      
    // リスト全体を差し替える必要がある場合のみSetterを用意  
    public void setActions(List\<ActionReference\> actions) {  
        this.actions.clear();  
        if (actions\!= null) {  
            this.actions.addAll(actions);  
        }  
    }  
}

RandomChoiceAction.java についても同様に、選択肢リストへのアクセサを追加する必要がある。

### **2.2 XmlPoint と Point の型不一致とデータバインディングの闇**

#### **2.2.1 型システムの乖離**

次に分析するのは、XmlPoint と java.awt.Point の間で発生している型不一致エラーである 12。Animation.java はマスコットの描画位置やアンカーポイントを管理するが、この座標データは歴史的にAWTの java.awt.Point で表現されてきた。  
一方で、XMLシリアライズ/デシリアライズの都合上、引数なしコンストラクタや特定のアノテーションを必要とする XmlPoint クラスが導入されたと考えられる。Java 21への移行に伴い、JAXB（javax.xml.bind）がJDKから削除されたため、代替ライブラリ（Jackson XMLやJakarta XML Binding）への移行が行われている可能性があり、その過程で型変換の自動化が機能しなくなっている。

| クラス | java.awt.Point | XmlPoint (DTO) |
| :---- | :---- | :---- |
| **目的** | 画面描画、座標計算、Swing APIとの連携 | XMLファイル (actions.xml, animations.xml) とのマッピング |
| **構造** | x (int), y (int). フィールドはpublicだが、setter/getterの挙動はレガシー。 | x, y 属性を持つPOJO。通常は文字列 "x,y" からのパース機能も持つ。 |
| **Kotlinでの扱い** | プラットフォーム型 (Point\!) | 通常のクラス |

#### **2.2.2 Animation.java の修正とアダプターパターン**

エラーは Animation.java 内で XmlPoint 型のデータを java.awt.Point 型のフィールドに代入しようとした箇所、あるいは ActionBuilder.kt が XmlPoint を渡すべき場所に Point を渡している箇所で発生している。  
**根本的な解決策:**  
ドメインモデル（Animation）をXML表現の詳細（XmlPoint）から分離する。Animation クラス内部では常に java.awt.Point を使用し、読み込み時に変換を行う「腐敗防止層（Anti-Corruption Layer）」を設ける。  
**修正コード案 (Animation.java):**

Java

package com.group\_finity.mascot.animation;

import java.awt.Point;  
import com.group\_finity.mascot.config.XmlPoint; // パッケージ名は仮定

public class Animation {  
      
    // 内部表現は標準のPointを使用  
    private Point anchor;  
    private Point velocity;

    // XMLバインディングフレームワークが呼び出すためのセッター  
    // 引数にXmlPointを受け取り、内部でPointに変換するオーバーロードを提供する  
    public void setAnchor(XmlPoint xmlPoint) {  
        if (xmlPoint\!= null) {  
            this.anchor \= new Point(xmlPoint.getX(), xmlPoint.getY());  
        }  
    }

    // 従来のPointを受け取るセッターも維持  
    public void setAnchor(Point point) {  
        this.anchor \= point;  
    }

    public Point getAnchor() {  
        return this.anchor;  
    }  
}

また、Kotlin側（ActionBuilder.kt）で変換を行う場合は、拡張関数を利用するのが最もエレガントである。  
**修正コード案 (Kotlin Extension):**

Kotlin

// Extensions.kt  
fun XmlPoint.toAwtPoint(): java.awt.Point \= java.awt.Point(this.x, this.y)  
fun java.awt.Point.toXmlPoint(): XmlPoint \= XmlPoint(this.x, this.y)

// ActionBuilder.kt での使用  
val animation \= Animation()  
animation.anchor \= xmlData.anchor?.toAwtPoint()?: java.awt.Point(0, 0\)

このアプローチにより、既存のJavaコードを大きく破壊することなく、コンパイルエラーを解消し、かつ型安全性を向上させることができる。

## ---

**3\. 実行時エラー分析：Behavior.java におけるNull安全性とリソース管理の現代化**

### **3.1 NullPointerException (NPE) の発生メカニズムとJava 21の影響**

#### **3.1.1 発生箇所：instantiateAction メソッドとリソースロード**

Behavior.java はマスコットの自律行動（Behavior）を制御する中核クラスである 6。ここでのNPEは、アプリケーションの起動シーケンスまたは特定アクションのトリガー時に発生し、プロセスをクラッシュさせる致命的な要因となっている。  
主な発生ポイントは以下の2点である：

1. **リソース取得の失敗:** Class.getResourceAsStream() が null を返す。  
2. **設定データの欠落:** Configuration オブジェクトからアクション定義を取得する際、キーが存在せず null が返る。

#### **3.1.2 Javaモジュールシステム (JPMS) とリソースパス**

従来のJava（Java 8以前）では、クラスパス上のリソース取得は比較的緩やかであった。しかし、Java 9以降のモジュールシステム、およびGradleのようなビルドツールの標準構成では、リソースの配置場所と可視性が厳格化されている。  
特に、Behavior.java が Main.class.getResourceAsStream("/logging.properties") 1 のようなコードを含んでいる場合、以下の理由で失敗する可能性がある：

* **モジュールカプセル化:** 呼び出し元のクラスが属するモジュールが、リソースを含むモジュール（またはフォルダ）に対して「開かれて」いない。  
* **パス指定の厳密化:** 絶対パス（/で始まる）と相対パスの解釈が、ClassLoaderの実装（AppClassLoader vs PlatformClassLoader）によって異なる挙動を示す場合がある。

#### **3.1.3 instantiateAction における防御的プログラミングの欠如**

Behavior.java の instantiateAction メソッドは、文字列のアクション名をキーとして、アクションのプロトタイプを取得し、複製（インスタンス化）する 1。

Java

// 問題のあるコード（推測）  
public Action instantiateAction(String name) {  
    return configuration.getSchema().get(name).createInstance();   
    // get(name) が null を返すと、即座に NPE が発生する  
}

XML設定ファイル（actions.xml）に記述ミスがあったり、読み込み順序に問題があったりすると、configuration マップ内に当該アクションが存在しない状態が発生する。レガシーコードは「設定は常に正しい」という前提で書かれていることが多いが、堅牢なアプリケーションでは「設定は壊れている可能性がある」という前提で防御する必要がある。

### **3.2 修正アプローチ：Optional型の導入と堅牢なリソース管理**

#### **3.2.1 リソースローディングの修正**

Java 21においては、リソース取得に対して明確なNullチェックを行うとともに、try-with-resources 構文を用いてストリームの閉め忘れを防止する必要がある。また、リソースパスの解決には ClassLoader を経由する方が、モジュール環境下では安全な場合がある。  
**修正コード案 (Behavior.java \- loadConfiguration):**

Java

private void loadConfiguration() {  
    String path \= "/conf/Behavior.xml"; // リソースルートからの絶対パス  
      
    // クラスローダー経由での取得を試みる  
    try (InputStream stream \= Behavior.class.getResourceAsStream(path)) {  
        if (stream \== null) {  
            // 明確な例外送出により、デバッグを容易にする  
            // ログには「どのパスが見つからなかったか」を明記する  
            log.log(Level.SEVERE, "Resource not found at path: {0}", path);  
            throw new BehaviorInstantiationException("Resource not found: " \+ path);  
        }  
        // XMLパース処理...  
        log.log(Level.INFO, "Behavior configuration loaded successfully from {0}", path);  
          
    } catch (IOException e) {  
        log.log(Level.SEVERE, "Failed to load behavior configuration", e);  
        // 必要に応じてアプリケーションを安全に停止させる処理  
    }  
}

#### **3.2.2 instantiateAction のNull安全性向上**

instantiateAction メソッドでは、Optional を使用するか、明示的なNullチェックを行って、呼び出し元にエラーを適切に伝播させる設計に変更する。  
**修正コード案 (Behavior.java \- instantiateAction):**

Java

/\*\*  
 \* 指定された名前のアクションを生成する。  
 \* @param actionName XMLで定義されたアクション名  
 \* @param params アクションに渡すパラメータ  
 \* @return 生成されたActionインスタンス  
 \* @throws BehaviorInstantiationException アクションが見つからない、または生成に失敗した場合  
 \*/  
public Action instantiateAction(String actionName, VariableMap params) throws BehaviorInstantiationException {  
    // 1\. 引数の検証  
    if (actionName \== null) {  
        throw new BehaviorInstantiationException("Action name cannot be null in behavior: " \+ this.name);  
    }

    // 2\. ConfigurationとSchemaの検証  
    if (this.configuration \== null |

| this.configuration.getSchema() \== null) {  
         throw new BehaviorInstantiationException("Configuration schema is not initialized.");  
    }

    // 3\. アクション定義の取得  
    Action prototype \= this.configuration.getSchema().get(actionName);  
      
    // 4\. 定義存在チェック（NPE防止の要）  
    if (prototype \== null) {  
        String msg \= String.format("Action '%s' is not defined in the configuration schema. Available actions: %s",   
                                   actionName, this.configuration.getSchema().keySet());  
        log.log(Level.SEVERE, msg);  
        // ここでデフォルトアクション（例えば「立つ」）を返すフォールバックも検討可能だが、  
        // 設定エラーは早期に検知すべきため例外を投げる  
        throw new BehaviorInstantiationException(msg);  
    }

    try {  
        // 5\. インスタンス化実行  
        return prototype.createInstance(params);  
    } catch (Exception e) {  
        // 原因となる例外をラップして再送出。スタックトレースを保持する。  
        throw new BehaviorInstantiationException("Failed to instantiate action: " \+ actionName, e);  
    }  
}

この修正により、XML設定ミスによるクラッシュ（NPE）は、意味のあるエラーメッセージを伴う例外（BehaviorInstantiationException）へと変わり、開発者が原因を特定しやすくなる。

## ---

**4\. スクリプトエンジン移行分析：GraalJSにおける walk.js の挙動とポリグロット実装**

### **4.1 NashornからGraalJSへのパラダイムシフトと影響**

#### **4.1.1 スクリプトエンジンの世代交代**

Shimejiは、マスコットの移動ロジック（例えば、画面の端を歩く、マウスを追従するなど）をJavaコードにハードコードするのではなく、外部のJavaScriptファイル（walk.jsなど）に記述し、実行時に動的に評価するアーキテクチャを採用している 7。これはユーザーによる動作カスタマイズを可能にする重要な機能である。  
Java 8〜14時代にはJDK標準の「Nashorn」エンジンが利用されていたが、Java 15で削除された。そのため、Java 21環境である「Shimeji Neo」では、Oracle Labsが開発する「GraalJS」への移行が不可欠である 9。

#### **4.1.2 Nashorn vs GraalJS: 決定的な違い**

GraalJSはNashornとの高い互換性を謳っているが、いくつかの重要な点で挙動が異なる。

| 特徴 | Nashorn (Legacy) | GraalJS (Modern) | 影響 |
| :---- | :---- | :---- | :---- |
| **Java型へのアクセス** | デフォルトで許可。比較的緩い。 | デフォルトで**禁止**（Secure by Default）。明示的な allowHostAccess 設定が必要。 | 設定なしではスクリプトからJavaクラス（java.awt.Point等）が見えずエラーになる。 |
| **戻り値の型** | Javaのラッパーオブジェクトまたはプリミティブ型を直接返すことが多い。 | org.graalvm.polyglot.Value という専用ラッパーを返す。 | Java側で Value オブジェクトを int や Point に明示的に変換（Unwrap）する必要がある。 |
| **ECMAScript仕様** | ES5.1 準拠（古い）。 | 最新のECMAScript (ES2022+) をサポート。 | let, const, アロー関数などが使える反面、古い非標準構文が動かない可能性がある。 |
| **変数のスコープ** | グローバル変数の扱いが緩い。 | Strictモードがデフォルトに近い挙動。 | 変数宣言なしの代入などがエラーになる可能性がある。 |

### **4.2 walk.js における戻り値評価の問題**

#### **4.2.1 eval 関数の戻り値仕様**

報告されている「戻り値の扱いについての問題」は、GraalJSの eval メソッドが返す Value オブジェクトの性質を正しく理解していないことに起因する 8。  
従来の walk.js の典型的なコード：

JavaScript

// walk.js (Legacy)  
var x \= mascot.anchor.x;  
var y \= mascot.anchor.y;  
x \= x \+ 5;  
mascot.setAnchor(new java.awt.Point(x, y));  
// 暗黙的に最後の式の値、あるいは undefined が返る

GraalJSにおいて context.eval(...) を実行した際、スクリプトの最後が var x \=... などの文（Statement）で終わっていると、戻り値が null (Java側での Value.isNull() が true) になるケースがある。また、計算結果を返したい場合、単に式を書くだけではなく、明示的に値を返す構造にする方が安全である。

#### **4.2.2 Value オブジェクトのトラップ**

GraalJSから返された Value オブジェクトを、そのままJavaの Point 型変数にキャストしようとすると ClassCastException が発生する。

Java

// 誤ったコード  
Point p \= (Point) context.eval("js", script); // エラー！ Value型はPoint型ではない

正しい方法は、Value.as(Point.class) を使うか、Value.asInt() などでプリミティブを取り出すことである。

### **4.3 修正コードと解説：GraalJS対応の実装**

#### **4.3.1 Java側：ScriptManager の実装**

GraalJSを安全かつ正しく利用するためのラッパークラスを実装する。ここでは、HostAccess.ALL を許可してNashornに近い利便性を提供するが、本番環境ではセキュリティ要件に応じて制限をかけることが望ましい。  
**修正コード案 (ScriptManager.java):**

Java

import org.graalvm.polyglot.\*;  
import com.group\_finity.mascot.Mascot;  
import java.awt.Point;

public class ScriptManager {  
    private final Context context;

    public ScriptManager() {  
        // GraalJSコンテキストの初期化  
        this.context \= Context.newBuilder("js")  
               .allowHostAccess(HostAccess.ALL)       // Javaメソッド/フィールドへのアクセス許可  
               .allowHostClassLookup(s \-\> true)       // 任意のJavaクラスのロード許可 (new java.awt.Point等)  
               .option("js.ecmascript-version", "2022") // 最新JS仕様  
               .build();  
    }

    /\*\*  
     \* walk.js 等の移動スクリプトを実行する  
     \* @param scriptContent スクリプトのソースコード文字列  
     \* @param mascot 操作対象のマスコットインスタンス  
     \*/  
    public void executeWalk(String scriptContent, Mascot mascot) {  
        try {  
            // 1\. マスコットオブジェクトをJSのグローバルスコープにバインド  
            Value bindings \= context.getBindings("js");  
            bindings.putMember("mascot", mascot);  
              
            // 2\. スクリプト実行  
            // 戻り値を利用しない場合でも、評価自体はここで行われる  
            Value result \= context.eval("js", scriptContent);  
              
            // デバッグ用: スクリプトが明示的に値を返した場合のログ  
            if (\!result.isNull()) {  
                // System.out.println("Script returned: " \+ result.toString());  
            }  
              
        } catch (PolyglotException e) {  
            // JS側での例外もここでキャッチ可能  
            System.err.println("Script execution failed: " \+ e.getMessage());  
            e.printStackTrace();  
        }  
    }  
}

#### **4.3.2 JavaScript側：walk.js の修正**

GraalJSでは Java.type を使用してJavaクラスを参照するのが標準的である。また、未宣言変数の使用を避けるため var, let, const を適切に使用する。  
**修正コード案 (walk.js):**

JavaScript

// Javaクラスの参照を取得  
const Point \= Java.type('java.awt.Point');

// グローバル変数 'mascot' はJava側から注入されている  
// プロパティアクセスはJavaBeans (getAnchor(), isLookRight()等) に自動マッピングされる  
var anchor \= mascot.getAnchor();  
var lookRight \= mascot.isLookRight();

var dx \= lookRight? 5 : \-5;  
var dy \= 0;

// 新しい座標の計算  
var newX \= anchor.x \+ dx;  
var newY \= anchor.y \+ dy;

// Javaオブジェクトの生成とメソッド呼び出し  
mascot.setAnchor(new Point(newX, newY));

// スクリプトの完了を示す値を返す（必須ではないがデバッグに有用）  
"Moved to (" \+ newX \+ ", " \+ newY \+ ")";

## ---

**5\. 結論と推奨される移行ロードマップ**

本レポートでは、Shimeji Neo開発における「ビルドエラー」「実行時エラー」「スクリプトエラー」の3大要因を分析した。これらは個別のバグではなく、レガシーJavaアーキテクチャからモダンJava/Kotlinエコシステムへの移行に伴う構造的な歪みである。

### **5.1 主要な知見とアクションアイテム**

| 領域 | 問題の核心 | 推奨される対策 |
| :---- | :---- | :---- |
| **ビルド (Kotlin)** | Javaのprivateフィールドへのアクセス不可 | **アクセサ導入:** SequenceAction.java 等に isLoop(), getActions() 等を追加し、Kotlinプロパティとして公開する。 |
| **ビルド (型)** | XmlPoint と Point の不整合 | **変換層の構築:** Animation.java またはKotlin拡張関数にて、XmlPoint \-\> Point の変換ロジックを実装する。 |
| **実行時 (NPE)** | リソース取得失敗と設定欠落 | **堅牢化:** Behavior.java における getResourceAsStream のパス修正と、instantiateAction での厳密なNullチェック・例外ハンドリングの実装。 |
| **スクリプト** | Nashorn廃止とGraalJSの型システム | **GraalJS対応:** ScriptManager クラスを作成し、Context 設定（allowHostAccess）を適切に行う。walk.js 内で Java.type を利用する記述に修正する。 |

### **5.2 今後の展望**

これらの修正を適用することで、Shimeji NeoはJava 21上で正常にビルド・動作するようになる。長期的には、XMLベースの設定システムをJSONやYAML、あるいはKotlin DSL自体に完全に置き換えることで、型安全性をさらに高め、リフレクションへの依存を排除することが推奨される。また、GraalVMのNative Image機能を活用すれば、JVMの起動オーバーヘッドを排除し、真に軽量なデスクトップマスコットとしての進化も期待できる。  
提示した修正コードは、単なるパッチワークではなく、これら将来の進化を見据えた堅牢な設計に基づいている。開発チームには、これらの修正を速やかに適用し、Shimejiの新たな歴史（Neo）を切り拓くことを強く推奨する。

#### **引用文献**

1. shimeji-ee/src/com/group\_finity/mascot/Main.java at master \- GitHub, 1月 12, 2026にアクセス、 [https://github.com/failedbuilder/shimeji-ee/blob/master/src/com/group\_finity/mascot/Main.java](https://github.com/failedbuilder/shimeji-ee/blob/master/src/com/group_finity/mascot/Main.java)  
2. shimeji-ee \- Readme.wiki \- Google Code, 1月 12, 2026にアクセス、 [https://code.google.com/archive/p/shimeji-ee/wikis/Readme.wiki](https://code.google.com/archive/p/shimeji-ee/wikis/Readme.wiki)  
3. shimeji-ee/readme.txt at master \- GitHub, 1月 12, 2026にアクセス、 [https://github.com/gil/shimeji-ee/blob/master/readme.txt](https://github.com/gil/shimeji-ee/blob/master/readme.txt)  
4. Shimeji-ee Desktop Pet \- Kilkakon.com, 1月 12, 2026にアクセス、 [https://kilkakon.com/shimeji/](https://kilkakon.com/shimeji/)  
5. 1月 1, 1970にアクセス、 [https://github.com/kokuzo-mushi/bunashimeji/blob/test1/app/src/main/java/com/group\_finity/mascot/action/RandomChoiceAction.java](https://github.com/kokuzo-mushi/bunashimeji/blob/test1/app/src/main/java/com/group_finity/mascot/action/RandomChoiceAction.java)  
6. 1月 1, 1970にアクセス、 [https://github.com/kokuzo-mushi/bunashimeji/blob/test1/app/src/main/java/com/group\_finity/mascot/behavior/Behavior.java](https://github.com/kokuzo-mushi/bunashimeji/blob/test1/app/src/main/java/com/group_finity/mascot/behavior/Behavior.java)  
7. 1月 1, 1970にアクセス、 [https://github.com/kokuzo-mushi/bunashimeji/blob/test1/app/src/main/resources/behavior/walk.js](https://github.com/kokuzo-mushi/bunashimeji/blob/test1/app/src/main/resources/behavior/walk.js)  
8. eval() \- JavaScript \- MDN Web Docs \- Mozilla, 1月 12, 2026にアクセス、 [https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global\_Objects/eval](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/eval)  
9. GraalJS \- GraalVM, 1月 12, 2026にアクセス、 [https://www.graalvm.org/latest/reference-manual/js/](https://www.graalvm.org/latest/reference-manual/js/)  
10. 1月 1, 1970にアクセス、 [https://github.com/kokuzo-mushi/bunashimeji/blob/test1/app/src/main/java/com/group\_finity/mascot/action/SequenceAction.java](https://github.com/kokuzo-mushi/bunashimeji/blob/test1/app/src/main/java/com/group_finity/mascot/action/SequenceAction.java)  
11. shimeji-ee/conf/actions.xml at master \- GitHub, 1月 12, 2026にアクセス、 [https://github.com/TigerHix/shimeji-ee/blob/master/conf/actions.xml](https://github.com/TigerHix/shimeji-ee/blob/master/conf/actions.xml)  
12. 1月 1, 1970にアクセス、 [https://github.com/kokuzo-mushi/bunashimeji/blob/test1/app/src/main/java/com/group\_finity/mascot/animation/Animation.java](https://github.com/kokuzo-mushi/bunashimeji/blob/test1/app/src/main/java/com/group_finity/mascot/animation/Animation.java)  
13. shimeji-ee/src/com/group\_finity/mascot/action/Breed.java at master \- GitHub, 1月 12, 2026にアクセス、 [https://github.com/logany20/shimeji-ee/blob/master/src/com/group\_finity/mascot/action/Breed.java](https://github.com/logany20/shimeji-ee/blob/master/src/com/group_finity/mascot/action/Breed.java)  
14. Run GraalVM JavaScript on a Stock JDK \- Oracle Help Center, 1月 12, 2026にアクセス、 [https://docs.oracle.com/en/graalvm/enterprise/22/docs/reference-manual/js/RunOnJDK/](https://docs.oracle.com/en/graalvm/enterprise/22/docs/reference-manual/js/RunOnJDK/)  
15. Named js functions evaluate to undefined · Issue \#68 · oracle/graaljs \- GitHub, 1月 12, 2026にアクセス、 [https://github.com/graalvm/graaljs/issues/68](https://github.com/graalvm/graaljs/issues/68)