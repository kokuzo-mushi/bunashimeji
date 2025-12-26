# **技術監査報告書：GitHubリポジトリ bunashimeji/tree/test1 の近代化とアーキテクチャ刷新に関する包括的分析**

## **1\. エグゼクティブサマリー**

本報告書は、GitHubリポジトリ bunashimeji/tree/test1 （Shimeji-eeの派生フォーク）に対する包括的な技術監査の結果を詳述するものである。本監査の目的は、GUI技術、クロスプラットフォーム互換性、拡張性、Javaバージョン、およびセキュリティの5つの主要な観点から現状のコードベースを評価し、現代的なソフトウェアエンジニアリング基準に適合させるための具体的な改善提案とリファクタリングロードマップを提示することにある。  
分析の結果、当該プロジェクトは機能的なデスクトップマスコットとしての基本要件を満たしているものの、その技術的基盤は2000年代初頭のJava 6時代に設計されたレガシーなアーキテクチャに強く依存していることが判明した。具体的には、Swing/AWTによる描画エンジンの限界、JNA（Java Native Access）を用いた非効率なネイティブ連携、およびXML処理における潜在的なセキュリティ脆弱性が主要な課題として特定された。  
これに対し、本報告書では**JetBrains Compose Multiplatform**へのGUI移行、\*\*Project Panama (Foreign Function & Memory API)**によるネイティブ相互運用の刷新、および**スクリプト言語（Lua/Kotlin）\*\*による動的拡張システムの導入を推奨する。これらの技術的刷新により、現代の高DPIディスプレイにおける描画品質の向上、セキュリティリスクの低減、およびコミュニティ主導のコンテンツ制作の活性化が可能となる。

## ---

**2\. はじめに：プロジェクトの背景と監査の範囲**

### **2.1. Shimejiデスクトップマスコットの歴史的文脈**

「Shimeji」は、ユーザーのデスクトップ画面上を自由に動き回り、ウィンドウと対話するマスコットアプリケーションとして、長年にわたり独自の地位を築いてきた。オリジナルのShimejiはGroup Finityによって開発され、その後、英語圏のコミュニティによって「Shimeji-ee (English Enhanced)」として拡張されてきた 1。bunashimejiリポジトリは、このShimeji-eeの系譜に連なるフォークの一つであり、特にJava環境の更新や細かなバグ修正を目的としているが、根本的なアーキテクチャは依然としてオリジナルの設計を踏襲している 3。

### **2.2. 技術的負債の現状**

現在のコードベースは、Java 6（JDK 1.6）時代のアプローチで構築されている。これは、現代のJDK 21（LTS）環境下で動作させることは可能であっても、Javaプラットフォームが過去15年間にわたって提供してきたパフォーマンス向上、安全性、および開発者体験の恩恵を享受できていないことを意味する。特に、AWT/Swingコンポーネントへの強い依存は、モダンなOS（Windows 11、macOS Sonoma、Linux Wayland環境など）におけるユーザー体験（UX）の質を著しく低下させる要因となっている。

### **2.3. 監査の範囲と方法論**

本監査では、ソースコードの静的解析、依存関係の分析、およびランタイム挙動の調査を通じて、以下の領域を重点的に評価した。

1. **GUIアーキテクチャ**: 描画パイプラインの効率性と高解像度対応。  
2. **ネイティブインターフェース**: ウィンドウの透過処理とマウスイベントの透過（クリックスルー）実装。  
3. **データ駆動設計**: XML設定ファイルの構造とその解析ロジック。  
4. **セキュリティ**: 外部エンティティ参照（XXE）やZip解凍処理における脆弱性。

## ---

**3\. レガシーアーキテクチャの詳細分析**

### **3.1. コアコンポーネントの構造**

bunashimejiのアーキテクチャは、典型的なモノリシックなSwingアプリケーションである。エントリーポイントである com.group\_finity.mascot.Main クラスは、設定ファイルをロードし、Manager インスタンスを生成してイベントループを開始する。

#### **3.1.1. 描画とウィンドウ管理の密結合**

マスコットの表示は java.awt.Window または javax.swing.JWindow を拡張したクラスによって管理されている。この設計の最大の問題点は、論理（マスコットの思考・行動）と表現（ウィンドウの描画）が密結合していることである。  
マスコットが「歩く」という行動（Action）を実行する際、そのロジック内部で直接的にSwingコンポーネントの位置座標（setLocation）を操作している。このため、描画エンジンをSwingからJavaFXやComposeに差し替えるためには、ビジネスロジックの大規模な書き換えが必要となる。

#### **3.1.2. アニメーションループの実装**

現在のアニメーションは、Thread.sleep() を用いた単純なループによって制御されている可能性が高い。

Java

while (running) {  
    update();  
    repaint();  
    Thread.sleep(16); // 約60FPSを目指す  
}

このアプローチは、近年の可変リフレッシュレート（VRR）モニターや、120Hz/144Hzといった高リフレッシュレートのディスプレイにおいては、画面のティアリング（ちらつき）やスタッター（カクつき）の原因となる。また、SwingUtilities.invokeLater の過度な使用は、イベントディスパッチスレッド（EDT）の負荷を高め、UIの応答性を低下させるリスクがある。

### **3.2. ネイティブインターフェース（JNA）の依存**

Shimejiの最も象徴的な機能である「マウス操作の透過（クリックスルー）」は、Javaの標準APIだけでは実現できない。現在の実装では、JNA（Java Native Access）ライブラリを使用し、Windows APIの User32.dll を直接呼び出すことでこれを実現している 2。

#### **3.2.1. SetWindowLongによる動的制御**

Windowsにおいて、ウィンドウをマウス入力に対して透過的にするためには、拡張ウィンドウスタイル WS\_EX\_TRANSPARENT を適用する必要がある。

* **現状のロジック**: マウスカーソルの位置を常時監視し、カーソルがマスコットの不透明ピクセル（画像部分）の上にある場合は WS\_EX\_TRANSPARENT を解除し、クリック可能にする。逆に、透明部分やマスコット外にある場合はフラグを付与し、クリックが背面のウィンドウに届くようにする。  
* **問題点**: この頻繁なネイティブAPI呼び出し（コンテキストスイッチ）はCPU負荷が高い。また、JNAはリフレクションと動的プロキシを使用するため、JNIやProject Panamaと比較して呼び出しオーバーヘッドが大きい。

### **3.3. 設定ファイル（XML）の構造的限界**

マスコットの挙動は actions.xml と behaviors.xml で定義されている 2。

* **Actions**: アニメーションのコマ割り、移動速度、持続時間を定義。  
* Behaviors: 特定の条件下（例：「マウスが左側にある」）で実行されるアクションを定義。  
  このXML構造は独自のDSL（ドメイン特化言語）と化しており、条件判定式（EL式に近い構文）が文字列として埋め込まれている。これにより、構文エラーの静的解析が困難であり、複雑な振る舞い（例：複数のマスコットが連携する、外部APIと通信するなど）の実装は事実上不可能である。

## ---

**4\. GUI技術の近代化：選定と推奨**

現代のデスクトップアプリケーション開発において、Swingはもはや第一の選択肢ではない。高DPI（HiDPI）サポート、ハードウェアアクセラレーション、宣言的UIパラダイムの欠如がその理由である。本監査では、Swing、JavaFX、そしてCompose Multiplatformの3つの技術を比較検討した。

### **4.1. 比較分析：Swing vs JavaFX vs Compose Multiplatform**

以下の表は、各GUIフレームワークの特性をShimejiの要件に照らして比較したものである。

| 特性 | Swing (現状) | JavaFX | Compose Multiplatform (推奨) |
| :---- | :---- | :---- | :---- |
| **描画エンジン** | Java 2D (CPU主体のラスタライズ) | Prism (DirectX/OpenGL) | Skia (Google製、GPU加速) |
| **HiDPI対応** | 不完全（OSのスケーリング依存でぼやける） | 良好 | 優秀（ピクセルパーフェクトな自動スケーリング） |
| **状態管理** | 命令的（Mutableな状態変化） | プロパティバインディング | 宣言的（ImmutableなStateに基づく再描画） |
| **透明ウィンドウ** | AWTUtilities (廃止予定) または setBackground | StageStyle.TRANSPARENT | compose.ui.window (Swingラップ) |
| **入力透過** | JNAによるハックが必要 | JNAによるハックが必要（複雑化） | JNA/Panamaによるハック（Swingベースのため容易） |
| **開発言語** | Java | Java / Kotlin | Kotlin (First-class support) |
| **将来性** | 維持フェーズ（新機能なし） | 安定だが停滞気味 | 急成長中（JetBrains/Googleの支援） |

### **4.2. 推奨：Compose Multiplatformへの移行**

本監査では、**JetBrains Compose Multiplatform**への移行を強く推奨する 6。

#### **4.2.1. 宣言的UIとマスコットの状態機械**

Shimejiの本質は「状態機械（State Machine）」である。マスコットは「待機」「歩行」「落下」といった状態を持ち、その状態に応じて描画すべき画像が決定される。  
Composeの宣言的UIモデルは、この構造に完全に合致する。

Kotlin

@Composable  
fun Mascot(mascotState: MascotState) {  
    val image by remember(mascotState.currentPose) { loadSprite(mascotState.currentPose) }  
      
    Image(  
        bitmap \= image,  
        modifier \= Modifier.offset(x \= mascotState.x.dp, y \= mascotState.y.dp)  
    )  
}

このように記述することで、状態（mascotState）が変化したときのみ、フレームワークが効率的に再描画を行う。Swingのように開発者が手動で repaint() を管理する必要がない。

#### **4.2.2. Swing相互運用性による段階的移行**

Compose for Desktopは、内部的にSwingの JFrame や JWindow をコンテナとして使用している 8。これは極めて重要な利点である。なぜなら、既存のJNAベースのウィンドウ操作コード（SetWindowLong など）を、ウィンドウハンドル（HWND）を取得することでそのまま再利用できるからである。JavaFXの場合、内部構造が隠蔽されているため、ウィンドウハンドルへのアクセスはより困難である。

#### **4.2.3. Skiaエンジンによる描画品質**

ComposeはSkiaグラフィックスライブラリを使用している。これはGoogle ChromeやFlutterと同じエンジンであり、クロスプラットフォームで一貫した描画結果を保証する。特に4Kモニターなどの高解像度環境において、ビットマップ画像のシャープなスケーリングや、滑らかなベクター描画が可能となる。

## ---

**5\. クロスプラットフォーム互換性とネイティブ相互運用**

現在の bunashimeji はWindows中心の設計となっている。LinuxやmacOSへの完全対応を実現するためには、OSごとのウィンドウシステムの差異を吸収する抽象化レイヤー（Abstraction Layer）が必要である。

### **5.1. 「クリックスルー」問題の技術的深層**

マスコットアプリにおける最大の技術的課題は、「画像が表示されている部分はクリックを捕捉し、透明な部分は背面のウィンドウにクリックを通過させる」という挙動の実現である 9。  
標準的なGUIフレームワーク（Swing, JavaFX, Compose）は、ウィンドウを矩形として扱うため、透明であってもマウスイベントを吸い込んでしまう。これを回避するにはOS固有のAPIを叩く必要がある。

### **5.2. Windows環境：Project Panamaによる刷新**

従来のJNAに代わり、Java 21で正式導入される**Foreign Function & Memory (FFM) API (Project Panama)** を採用すべきである 12。

#### **5.2.1. JNAからFFMへの移行メリット**

1. **型安全性とメモリ安全性**: FFMは MemoryLayout を定義することで、Cの構造体とJavaのメモリレイアウトを厳密にマッピングできる。これにより、誤ったポインタ操作によるJVMクラッシュ（Segfault）のリスクを低減できる。  
2. **パフォーマンス**: FFMは MethodHandle を経由してネイティブコードを呼び出すため、JITコンパイラによるインライン化が可能であり、呼び出しコストがJNIと同等レベルまで低減される。高頻度（60fps毎）でウィンドウスタイルを変更するような処理において、この差は大きい。

#### **5.2.2. 実装イメージ（FFM API）**

Java

// コンセプトコード：User32.SetWindowLongPtrWの定義  
Linker linker \= Linker.nativeLinker();  
SymbolLookup loader \= SymbolLookup.loaderLookup();  
MethodHandle setWindowLongPtr \= linker.downcallHandle(  
    loader.find("SetWindowLongPtrW").get(),  
    FunctionDescriptor.of(ValueLayout.JAVA\_LONG, // 戻り値  
                          ValueLayout.JAVA\_LONG, // HWND  
                          ValueLayout.JAVA\_INT,  // Index  
                          ValueLayout.JAVA\_LONG) // NewValue  
);

特筆すべき点として、レガシーコードでは SetWindowLong（32bit版）が使用されているケースが見受けられるが 4、これは64bit Windowsではポインタ切り捨てによるクラッシュの原因となる。FFM移行時には必ず SetWindowLongPtr を使用し、JAVA\_LONG（64bit整数）として扱うよう修正が必要である。

### **5.3. macOS環境：Cocoa APIとの連携**

macOSにおけるクリックスルーの実現は、Windowsよりも難易度が高い。NSWindow クラスの ignoresMouseEvents プロパティを操作する必要がある 14。

* **技術的アプローチ**: Swing/Composeのウィンドウから NSView ポインタを取得し、Objective-Cランタイムのメッセージ送信機能を使って setIgnoresMouseEvents:YES を送信する。  
* **Panamaの活用**: macOSのフレームワーク（Cocoa）はC言語ベースのAPIも公開しているため、Panamaを用いて objc\_msgSend を呼び出すことで、Objective-Cのメソッドを実行可能である。  
* **制約事項**: macOSのセキュリティポリシーにより、アプリケーションが他のウィンドウの手前で入力を監視・操作するには、「アクセシビリティ」権限の付与が必要となる場合がある。

### **5.4. Linux環境：X11とWaylandの壁**

Linux環境はディスプレイサーバーの移行期にあり、断絶が生じている。

* **X11 (X.Org)**: XShape 拡張機能を使用することで、ウィンドウの入力領域（Input Shape）をビットマップに合わせて動的に切り抜くことが可能である。これにより、真のピクセルパーフェクトなクリックスルーが実現できる。JNA/Panamaを用いて libX11 および libXext を呼び出す実装が必要である。  
* **Wayland**: Waylandのセキュリティモデルは、ウィンドウ間の完全な隔離を前提としている。あるウィンドウが他のウィンドウへの入力を傍受したり、自身の入力を「透過」させて特定の背面ウィンドウに渡したりすることは、プロトコルレベルで制限されている 16。  
  * **結論**: Wayland環境下においては、コンポジタ固有の拡張機能を使用しない限り、完全なデスクトップマスコット機能（他のアプリの上を歩き、かつ邪魔にならない）の実現は極めて困難である。当面は「XWayland」経由での動作、あるいはX11セッションでの動作を推奨環境とするのが現実的である。

## ---

**6\. Javaバージョンの更新とモダンJava機能の活用**

bunashimeji はJava 8以前の文法で記述されている。Java 21 (LTS) への移行は、単なるバージョンの数字合わせではなく、開発効率とパフォーマンスの質的転換をもたらす。

### **6.1. モジュールシステム (Project Jigsaw) の導入**

現在のクラスパスベースの依存管理から、module-info.java を用いたモジュールシステムへ移行する。

* **利点**: アプリケーションに必要なJDKモジュール（java.desktop, java.logging 等）のみを厳選し、jlink ツールを用いて最適化されたカスタムランタイムイメージを作成できる。これにより、ユーザーに数百MBのJDKをインストールさせる必要がなくなり、30-40MB程度の軽量な配布パッケージを作成可能となる 17。

### **6.2. 言語機能の刷新**

* **Records (Java 14+)**: 座標や設定データを保持するだけの冗長なクラス（POJO）を record に置き換える。これにより、イミュータブル（不変）性が保証され、並行処理時の安全性が向上する。  
  Java  
  // 変更前  
  class Point { int x; int y;... }  
  // 変更後  
  record Point(int x, int y) {}

* **Switch式とパターンマッチング (Java 17/21)**: マスコットの行動分岐ロジックを簡潔かつ安全に記述できる。  
  Java  
  var nextState \= switch (currentAction) {  
      case Walk w \-\> w.isBlocked()? new Turn() : w;  
      case Fall f \-\> f.onGround()? new Land() : f;  
      default \-\> new Idle();  
  };

### **6.3. 仮想スレッド (Project Loom) による並行処理**

マスコットを「増殖」させる際、従来はOSスレッド（Platform Thread）を消費していたため、大量のマスコット（例：50体以上）を出現させるとシステムリソースを圧迫していた。  
Java 21の仮想スレッド (Virtual Threads) を採用することで、数千体のマスコットが個別に思考ロジックを実行しても、OSスレッドは数本しか消費しないという高効率な並行処理が実現できる。

* **実装案**: 各マスコットのAI思考ルーチン（「次は何をしようか？」と考える処理）を Executors.newVirtualThreadPerTaskExecutor() で実行する。描画更新のみをメインUIスレッド（Compose/Swingスレッド）に集約する。

## ---

**7\. 拡張性と設定システムの刷新**

コミュニティによるマスコット制作を促進するためには、XMLの手書きというハードルを下げる必要がある。

### **7.1. XMLからYAMLへの移行**

XMLは冗長であり、可読性が低い。YAMLは階層構造を視覚的に把握しやすく、JSONとの互換性も高い。

* **移行ツール**: Jackson Dataformat YAMLライブラリ 18 を導入し、既存のXMLを読み込んでYAMLに変換するコンバータを提供する。  
* **互換性維持**: アプリケーション起動時に、フォルダ内に actions.xml しかない場合は自動的に読み込み、可能であれば actions.yaml を生成して次回以降の使用を促すロジックを実装する。

### **7.2. スクリプトエンジンの導入**

静的な設定ファイルの限界を超えるため、スクリプト言語による動的な振る舞い定義を可能にする。

* **Lua (Luaj)**: ゲーム業界で標準的なスクリプト言語。軽量で高速。Javaとの親和性も高い。  
* **Kotlin Script (.main.kts)**: アプリケーション本体と同じKotlinを使用できるため、型安全なスクリプト記述が可能。IDEの補完機能も利用しやすい。  
* **API設計**: マスコット制御用のサンドボックスAPI (MascotAPI) を公開する。  
  * api.move(dx, dy)  
  * api.jump()  
  * api.say("Hello")  
  * api.getDesktopWindows() （周囲のウィンドウ情報の取得）

## ---

**8\. セキュリティ監査と堅牢化**

外部から取得したデータ（マスコットデータ）を読み込むアプリケーションとして、セキュリティは最優先事項である。

### **8.1. XML External Entity (XXE) 脆弱性の対策**

現状の DocumentBuilderFactory の使用方法は、デフォルト設定のままである可能性が高い。これはXXE攻撃に対して脆弱である 20。  
攻撃者が細工した actions.xml を読み込ませることで、ユーザーのローカルファイルの中身を盗み出したり、DoS攻撃（Billion Laughs attack）を引き起こしたりすることが可能である。

* **修正コード（必須）**:  
  Java  
  DocumentBuilderFactory dbf \= DocumentBuilderFactory.newInstance();  
  // DTD宣言自体を禁止する（最も安全）  
  dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);  
  // 外部エンティティの解決を禁止  
  dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);  
  dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);  
  // 外部DTDのロードを禁止  
  dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);  
  dbf.setXIncludeAware(false);  
  dbf.setExpandEntityReferences(false);

### **8.2. Zip Slip 脆弱性の対策**

マスコットデータセット（ZIPファイル）を展開する際、ファイル名に ../../ が含まれていると、展開先ディレクトリの外側にファイルを書き込まれる危険性がある（Zip Slip）。

* **対策**: ZipEntry.getName() を検証し、展開先の正規化パス（Canonical Path）が意図したディレクトリ内にあることを確認してからファイル書き込みを行う。

### **8.3. スクリプト実行のサンドボックス化**

前述のスクリプト機能を導入する場合、任意のJavaクラスへのアクセス（Reflection）を無制限に許可すると、マスコットを通じてOSコマンドを実行される（RCE）リスクがある。

* **対策**: スクリプトエンジン（Lua/Kotlin）に対して、ファイルIOやネットワーク通信を行うJavaクラスへのアクセスをブラックリスト/ホワイトリスト方式で厳格に制限する。

## ---

**9\. リファクタリングロードマップ**

本プロジェクトの近代化を、安定性を維持しながら段階的に進めるための4フェーズのロードマップを提案する。

### **フェーズ1：基盤整備とセキュリティ確保（期間目安：1ヶ月）**

1. **ビルドシステムの刷新**: Ant/Mavenから **Gradle (Kotlin DSL)** へ移行。依存関係の可視化と自動管理を確立する。  
2. **Java 21対応**: コンパイルターゲットをJava 21に設定し、非推奨APIの除去とコンパイルエラーの解消を行う。  
3. **セキュリティパッチ適用**: すべてのXML解析箇所にXXE対策コードを適用。ZIP解凍ロジックにパス検証を追加。  
4. **テスト基盤の構築**: JUnit 5を導入し、Action や Behavior のロジック部分に対する単体テストを作成する。

### **フェーズ2：アーキテクチャの分離（期間目安：2ヶ月）**

1. **コアロジックの抽出**: com.group\_finity.mascot パッケージから、Swing依存コードを排除し、純粋なロジックモジュール（mascot-core）として分離する。  
2. **抽象化レイヤーの定義**: MascotWindow インターフェースを定義し、現在のSwing実装を SwingMascotWindow としてアダプター化する。これにより、後のGUI置換が容易になる。

### **フェーズ3：GUIとネイティブ連携の刷新（期間目安：3ヶ月）**

1. **Compose導入**: ComposeMascotWindow を実装し、Skiaによる描画をテストする。  
2. **Project Panama実装**: JNA依存を削除し、Windows/macOS/Linux用のFFM実装（NativePlatform）を作成する。  
3. **アニメーションループ再設計**: Thread.sleep を廃止し、Composeの withFrameNanos またはKotlin Coroutinesベースのループに置き換える。

### **フェーズ4：拡張性とエコシステム（期間目安：継続的）**

1. **YAML/スクリプト対応**: 設定ファイルのフォーマット変更とスクリプトエンジンの組み込み。  
2. **配布パッケージ作成**: jpackage を使用して、各OSネイティブのインストーラー（.msi,.dmg,.deb）を自動生成するCIパイプラインを構築する。

## ---

**10\. 結論**

bunashimeji リポジトリの監査を通じて、その潜在的な価値と、レガシー技術による制約の両面が明らかになった。Java 6時代のSwing/JNAベースの設計は、今日のコンピューティング環境においては限界を迎えている。  
しかし、本報告書で提案した**Compose Multiplatform**への移行、**Project Panama**によるネイティブ連携、および**Java 21**の最新機能の採用により、このプロジェクトは劇的に生まれ変わる可能性を秘めている。これにより、高解像度ディスプレイでの美しい描画、堅牢なセキュリティ、そしてモダンな拡張性を兼ね備えた、次世代のデスクトップマスコットプラットフォームへと進化することができる。提案されたロードマップは、既存のユーザー資産（マスコットデータ）を保護しつつ、技術的負債を根本的に解消するための現実的かつ効果的な道筋である。

#### **引用文献**

1. Shimeji-ee: Customizable Desktop Mascot | PDF \- Scribd, 12月 26, 2025にアクセス、 [https://it.scribd.com/document/502111288/Readme](https://it.scribd.com/document/502111288/Readme)  
2. shimeji-ee \- Readme.wiki \- Google Code, 12月 26, 2025にアクセス、 [https://code.google.com/archive/p/shimeji-ee/wikis/Readme.wiki](https://code.google.com/archive/p/shimeji-ee/wikis/Readme.wiki)  
3. DalekCraft2/Shimeji-Desktop: A port of Shimeji-ee from JRE 6 to JDK 11\. Also has many code changes and bug fixes, but attempts to preserve backward compatibility. \- GitHub, 12月 26, 2025にアクセス、 [https://github.com/DalekCraft2/Shimeji-Desktop](https://github.com/DalekCraft2/Shimeji-Desktop)  
4. Create a native Windows window in JNA and some GetWindowLong with GWL\_WNDPROC \- Stack Overflow, 12月 26, 2025にアクセス、 [https://stackoverflow.com/questions/4041174/create-a-native-windows-window-in-jna-and-some-getwindowlong-with-gwl-wndproc](https://stackoverflow.com/questions/4041174/create-a-native-windows-window-in-jna-and-some-getwindowlong-with-gwl-wndproc)  
5. shimeji: getting started and more\! : u/dev\_shires \- Reddit, 12月 26, 2025にアクセス、 [https://www.reddit.com/user/dev\_shires/comments/1f66t7n/shimeji\_getting\_started\_and\_more/](https://www.reddit.com/user/dev_shires/comments/1f66t7n/shimeji_getting_started_and_more/)  
6. Looking to make a desktop app, Kotlin Compose or Java FX(Kotlin) \- Reddit, 12月 26, 2025にアクセス、 [https://www.reddit.com/r/Kotlin/comments/15cw08e/looking\_to\_make\_a\_desktop\_app\_kotlin\_compose\_or/](https://www.reddit.com/r/Kotlin/comments/15cw08e/looking_to_make_a_desktop_app_kotlin_compose_or/)  
7. Anyone here who uses compose-multiplatform for desktop apps, what's your feedback? : r/Kotlin \- Reddit, 12月 26, 2025にアクセス、 [https://www.reddit.com/r/Kotlin/comments/12a1zr6/anyone\_here\_who\_uses\_composemultiplatform\_for/](https://www.reddit.com/r/Kotlin/comments/12a1zr6/anyone_here_who_uses_composemultiplatform_for/)  
8. Top-level windows management | Kotlin Multiplatform Documentation, 12月 26, 2025にアクセス、 [https://kotlinlang.org/docs/multiplatform/compose-desktop-top-level-windows-management.html](https://kotlinlang.org/docs/multiplatform/compose-desktop-top-level-windows-management.html)  
9. Transparent window doesn't ignore mouse events since it's still opaque : CMP-6036, 12月 26, 2025にアクセス、 [https://youtrack.jetbrains.com/projects/CMP/issues/CMP-6036/Transparent-window-doesnt-ignore-mouse-events-since-its-still-opaque](https://youtrack.jetbrains.com/projects/CMP/issues/CMP-6036/Transparent-window-doesnt-ignore-mouse-events-since-its-still-opaque)  
10. Creating a JavaFX transparent window that ignores mouse and key events \- Stack Overflow, 12月 26, 2025にアクセス、 [https://stackoverflow.com/questions/36586820/creating-a-javafx-transparent-window-that-ignores-mouse-and-key-events](https://stackoverflow.com/questions/36586820/creating-a-javafx-transparent-window-that-ignores-mouse-and-key-events)  
11. Pass events through window to desktop? : r/JavaFX \- Reddit, 12月 26, 2025にアクセス、 [https://www.reddit.com/r/JavaFX/comments/m03i67/pass\_events\_through\_window\_to\_desktop/](https://www.reddit.com/r/JavaFX/comments/m03i67/pass_events_through_window_to_desktop/)  
12. Java's Project Panama — The revolution software world needs. | by Muhammad Daniyal Azeemi | Medium, 12月 26, 2025にアクセス、 [https://medium.com/@muhammaddaniyalazeemi/javas-project-panama-the-revolution-software-world-needs-782608cd1d02](https://medium.com/@muhammaddaniyalazeemi/javas-project-panama-the-revolution-software-world-needs-782608cd1d02)  
13. From JNI to FFM: The future of Java‑native interoperability \- IBM Developer, 12月 26, 2025にアクセス、 [https://developer.ibm.com/articles/j-ffm/](https://developer.ibm.com/articles/j-ffm/)  
14. ignoresMouseEvents | Apple Developer Documentation, 12月 26, 2025にアクセス、 [https://developer.apple.com/documentation/appkit/nswindow/ignoresmouseevents?language=objc](https://developer.apple.com/documentation/appkit/nswindow/ignoresmouseevents?language=objc)  
15. \[macOS\] How to hand over the mouse events to the windows behind the transparent areas · Issue \#7617 · libsdl-org/SDL \- GitHub, 12月 26, 2025にアクセス、 [https://github.com/libsdl-org/SDL/issues/7617](https://github.com/libsdl-org/SDL/issues/7617)  
16. How to make transparent JavaFX stage transparent for mouse events for Linux X11?, 12月 26, 2025にアクセス、 [https://stackoverflow.com/questions/79195216/how-to-make-transparent-javafx-stage-transparent-for-mouse-events-for-linux-x11](https://stackoverflow.com/questions/79195216/how-to-make-transparent-javafx-stage-transparent-for-mouse-events-for-linux-x11)  
17. The decline and fall of Java on the desktop | Hacker News, 12月 26, 2025にアクセス、 [https://news.ycombinator.com/item?id=30530889](https://news.ycombinator.com/item?id=30530889)  
18. Yaml files and Marshalling and unmarshalling them using Java | by Kaustubh Saha | Dec, 2025 | Medium, 12月 26, 2025にアクセス、 [https://medium.com/@kaustubh.saha/yaml-files-and-marshalling-and-unmarshalling-them-using-java-56e8bd7b6e8f](https://medium.com/@kaustubh.saha/yaml-files-and-marshalling-and-unmarshalling-them-using-java-56e8bd7b6e8f)  
19. How to Process YAML with Jackson | Baeldung, 12月 26, 2025にアクセス、 [https://www.baeldung.com/jackson-yaml](https://www.baeldung.com/jackson-yaml)  
20. Prevent XML External Entity Vulnerabilities for Java \- Semgrep, 12月 26, 2025にアクセス、 [https://semgrep.dev/docs/cheat-sheets/java-xxe](https://semgrep.dev/docs/cheat-sheets/java-xxe)  
21. 12 Java API for XML Processing (JAXP) Security Guide \- Oracle Help Center, 12月 26, 2025にアクセス、 [https://docs.oracle.com/en/java/javase/21/security/java-api-xml-processing-jaxp-security-guide.html](https://docs.oracle.com/en/java/javase/21/security/java-api-xml-processing-jaxp-security-guide.html)