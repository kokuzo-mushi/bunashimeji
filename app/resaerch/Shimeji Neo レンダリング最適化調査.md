# **Shimeji Neo 近代化計画 \- フェーズ3：描画エンジンおよびパフォーマンス最適化に関する包括的技術調査報告書**

## **1\. エグゼクティブサマリー**

本報告書は、デスクトップマスコットアプリケーション「Shimeji Neo」の近代化プロジェクト（フェーズ3）において、特に描画性能とリソース効率の劇的な向上を達成するための技術的戦略を詳述するものである。Java 21環境下において、50体以上のマスコット（独立した透明ウィンドウ）を60 FPSで滑らかに動作させるという要件は、従来のAWT/Swingアーキテクチャにとって極めて高いハードルである。特にWindows 11のDesktop Window Manager (DWM) とJava 2Dパイプラインの相互作用において、透過ウィンドウ（Per-pixel Alpha）の扱いは歴史的にパフォーマンスのボトルネックとなってきた 1。  
本調査では、標準的なJava 2Dの最適化（VolatileImageの活用、レンダリングループの刷新）から、Project Panama (Foreign Function & Memory API) を用いたWindowsネイティブAPI（DirectComposition/Direct2D）への直接介入まで、段階的な最適化戦略を提示する。検証の結果、標準のSwingコンポーネントレンダリング（Passive Rendering）では、50体以上のオブジェクトが重なり合うシナリオにおいて「Dirty Region（再描画領域）」の計算とCPUベースの合成処理が飽和し、目標とするフレームレートの維持が困難であることが示唆された 3。  
本報告書が提案する戦略の核心は、Javaの管理下にあるヒープメモリと、OS/GPUが管理するビデオメモリ（VRAM）の間のデータ転送（バス帯域幅）を最小化することにある。具体的には、以下の3つの主要なアプローチを詳細に検討する。

1. **AWT/Swingの限界突破（Active Rendering）**: 従来のイベント駆動型描画を廃止し、BufferStrategyを用いた能動的なレンダリングループを構築することで、Swingのオーバーヘッドを回避する 5。  
2. **ハードウェアパイプラインの適正化**: Windows環境におけるDirect3Dパイプラインの挙動を解析し、JVMフラグによるチューニングとVolatileImageの適切なライフサイクル管理によって、GPUアクセラレーションを最大限に引き出す 7。  
3. **ネイティブ合成への移行（Project Panama）**: 最終的な解決策として、WindowsのDirectComposition APIをJavaから直接操作し、ウィンドウ合成処理をOSのコンポジタ（DWM）に委譲するアーキテクチャを設計する 9。

さらに、Java 21で導入されたGenerational ZGCの特性を活かしたメモリ管理戦略と、テクスチャアトラスを用いたVRAM効率化についても論じる。

## ---

**2\. 序論：デスクトップマスコットにおける技術的課題**

### **2.1 プロジェクトの背景と技術的制約**

「Shimeji Neo」は、デスクトップ画面上を自由に動き回るマスコットを表示するアプリケーションである。技術的な観点から見ると、これは「不定形な透明領域を持つトップレベルウィンドウを、多数、高フレームレートで同期させて動かす」という極めて特殊なワークロードである。

#### **2.1.1 Java AWT/Swing の歴史的背景**

JavaのGUIツールキットであるAWT（Abstract Window Toolkit）およびSwingは、本来、矩形のビジネスアプリケーションフォームを描画するために設計された。初期の設計思想では、ウィンドウは不透明な矩形であり、OSのウィンドウマネージャが装飾（タイトルバーなど）を管理することを前提としていた 1。  
Java 7以降、SetWindowOpacityやsetShapeといったAPIによって異形ウィンドウや半透明ウィンドウがサポートされたが、これらはOSのコンポジタ（WindowsであればGDI+やDWM）との重厚な相互運用層の上に成り立っている 12。

#### **2.1.2 Windows グラフィックスサブシステムの進化**

Windows Vista以降、デスクトップ描画はDesktop Window Manager (DWM) によってGPU合成されるようになった。これにより、アプリケーションが描画した内容は一度オフスクリーンサーフェスに書き込まれ、DWMがそれらを合成して最終的なデスクトップ画面を生成する。  
しかし、Javaの透過ウィンドウ（Per-pixel Alpha）は、依然としてWin32 APIの UpdateLayeredWindow 関数に依存しているケースが多い。この関数は、システムメモリ上のビットマップへのポインタを要求するため、GPUで描画した結果を一度メインメモリに読み戻す（Readback）必要が生じ、これが深刻なパフォーマンスボトルネックとなる 14。

### **2.2 目標とするパフォーマンス指標**

* **ターゲット**: 50体以上の独立したマスコット  
* **フレームレート**: 安定した60 FPS（フレームタイム 16.6ms以内）  
* **CPU負荷**: アイドル時（マスコット静止時）はほぼゼロ、動作時も1コア未満  
* **メモリフットプリント**: VRAMおよびメインメモリの効率的な利用

これらの目標を達成するためには、従来の「描画してOSに投げる」アプローチから、「GPU上のリソースを操作する」アプローチへの転換が必要となる。

## ---

**3\. AWT/Swing 描画パイプラインの深層分析と最適化**

本章では、既存のJWindowベースの実装が抱える構造的な欠陥を分析し、AWTの低レベルAPIを用いた最適化手法を提案する。

### **3.1 透過ウィンドウ（Per-pixel Alpha）のレンダリングコスト構造**

Javaにおいて setBackground(new Color(0,0,0,0)) を設定したウィンドウの描画プロセスは、不透明なウィンドウとは根本的に異なる経路を辿る。

#### **3.1.1 ソフトウェアフォールバックのメカニズム**

Windows環境において、Java 2Dパイプライン（SunGraphics2D）は通常、Direct3Dを使用して描画コマンドをGPUに発行する。しかし、描画対象が「透過ウィンドウのバックバッファ」である場合、以下の理由からハードウェアアクセラレーションが無効化され、ソフトウェアレンダリングにフォールバックする可能性が高い 2。

1. **GDIの制約**: 古典的なGDIサーフェスはアルファチャンネルをネイティブにサポートしていない。  
2. **Readbackのコスト**: GPU上でレンダリングされた結果を UpdateLayeredWindow に渡すためには、VRAMからシステムメモリへの転送（glReadPixels や GetRenderTargetData 相当の処理）が必要となる。PCI Expressバスを介したこの転送は非常に遅く、50個のウィンドウで毎フレーム実行すれば帯域は容易に飽和する。  
3. **パイプラインの分断**: Java 2Dは、頻繁なRead/Writeが発生するサーフェス（Read-Modify-Writeサイクル）に対しては、安全のためにソフトウェア・ループ（CPU処理）を選択する傾向がある 16。

#### **3.1.2 RepaintManager と Dirty Region の爆発的増加**

Swingの描画アーキテクチャは「受動的（Passive）」である。OSからの露出イベントやアプリケーションからの repaint() 要求を受け取り、RepaintManager がそれらをマージして再描画領域（Dirty Region）を決定する 3。  
50体のマスコットが画面上を動き回り、互いに重なり合う状況を想定する。

* **シナリオ**: マスコットAがマスコットBの上を通過する。  
* **Swingの挙動**: マスコットAの移動により、その背面に位置するマスコットBの一部が「汚れた」と判定される。SwingはZオーダーに従って、まずBを描画し、その上にAを描画し直す必要がある。  
* **計算量**: 重なり判定とクリッピング領域の計算は、ウィンドウ数 $N$ に対して $O(N^2)$ の複雑度になり得る。不定形ウィンドウの場合、矩形クリッピングよりも計算コストが高い 18。  
* **結果**: イベントディスパッチスレッド（EDT）が「どの領域を再描画すべきか」の計算だけで時間を浪費し、実際の描画コマンド発行が遅延する。これがフレームレート低下の主要因の一つである。

### **3.2 最適化戦略1: Active Rendering への移行**

SwingのpaintComponent機構に依存する限り、EDTのボトルネックとRepaintManagerのオーバーヘッドからは逃れられない。解決策は、アプリケーション主導で描画タイミングを制御する **Active Rendering（能動的描画）** への移行である 5。

#### **3.2.1 BufferStrategy の導入**

java.awt.Window または java.awt.Canvas は createBufferStrategy メソッドを提供しており、これを利用することでOSの描画イベントを無視し、直接ビデオメモリ（またはシステムメモリ上のバックバッファ）に描画することが可能になる 6。

| 機能 | Passive Rendering (Swing) | Active Rendering (BufferStrategy) |
| :---- | :---- | :---- |
| **主導権** | OS / EDT | アプリケーションのメインループ |
| **タイミング** | 非決定的（イベント依存） | 決定的（タイマー/ループ依存） |
| **バッファリング** | Swingのダブルバッファリング | ハードウェアページフリップ（可能な場合） |
| **スレッド** | EDT (UI操作と競合) | 専用レンダリングスレッド |
| **適正** | 静的なGUI | ゲーム、アニメーション |

#### **3.2.2 実装アプローチ**

JWindow ではなく java.awt.Window を使用し、Swingのオーバーヘッドを最小化する。

Java

// Active Rendering の基本構造  
public class MascotWindow extends Window {  
    public MascotWindow(Frame owner) {  
        super(owner);  
        // OSからの再描画要求を無視する（ちらつき防止と効率化）  
        setIgnoreRepaint(true);  
        setBackground(new Color(0, 0, 0, 0));  
    }

    public void init() {  
        // ダブルバッファリング戦略の作成  
        createBufferStrategy(2);  
    }

    public void render() {  
        BufferStrategy bs \= getBufferStrategy();  
        if (bs \== null) return;

        // VRAMロスト対策のループ  
        do {  
            do {  
                Graphics2D g \= (Graphics2D) bs.getDrawGraphics();  
                try {  
                    // 1\. 前フレームの消去（透明色で塗りつぶし）  
                    g.setBackground(new Color(0, 0, 0, 0));  
                    g.clearRect(0, 0, getWidth(), getHeight());

                    // 2\. マスコットの描画  
                    drawMascot(g);  
                } finally {  
                    g.dispose();  
                }  
            } while (bs.contentsRestored()); // バッファが復元された場合は再描画

            // 3\. バッファの切り替え（Flip / Blit）  
            bs.show();  
        } while (bs.contentsLost()); // バッファがロストした場合は再描画  
    }  
}

このアプローチにより、EDTの負荷とは無関係に、60 FPSの安定したループを回すことが可能になる。また、RepaintManagerによる複雑な領域計算をバイパスし、「全画面（ウィンドウ全体）を書き換える」単純かつ高速なパスを採用できる 5。

### **3.3 最適化戦略2: VolatileImage の適正利用**

Active Renderingを採用しても、描画先がシステムメモリであっては効果が薄い。VolatileImage を活用し、VRAM上でピクセル合成を行う必要がある 22。

#### **3.3.1 VolatileImage vs BufferedImage**

* **BufferedImage**: Javaヒープ（またはDirectBuffer）上のピクセル配列。CPUによるアクセスは高速だが、GPUによる描画先としては不向き（ソフトウェアレンダリングになる）。  
* **VolatileImage**: デバイス（GPU）固有のメモリ領域（VRAM）。GPUによる高速な描画が可能だが、OSの都合（スクリーンセーバー起動、解像度変更など）で内容が失われる可能性がある 22。

#### **3.3.2 透過ウィンドウでの活用法**

透過ウィンドウの場合、最終的な表示には UpdateLayeredWindow が必要となるため、完全なハードウェアパイプラインの維持は難しい。しかし、**マスコットの合成処理（パーツの組み合わせなど）** を VolatileImage 上で行うことで、その計算コストをGPUにオフロードすることは可能である。  
**推奨フロー:**

1. 各マスコットのパーツ（目、口、体など）を BufferedImage としてロードする（Managed ImageとしてVRAMにキャッシュされる） 24。  
2. フレームごとの合成が必要な場合、オフスクリーンの VolatileImage を作成し、そこにパーツを描画する（GPUアクセラレーション）。  
3. 合成済みの VolatileImage を BufferStrategy のグラフィックスコンテキスト（ウィンドウのバックバッファ）に描画する。

これにより、CPUは「画像の転送命令」を出すだけで済み、実際のピクセル合成はGPUが行うことになる。

## ---

**4\. ハードウェアアクセラレーション（Java 2D Pipeline）の最適化**

Java 2Dは、OSのグラフィックスAPI（GDI, Direct3D, OpenGL, X11）の上に構築された抽象層である。Windows 11において最適なパフォーマンスを得るための設定と検証方法を詳述する。

### **4.1 パイプラインの選択：Direct3D vs OpenGL**

Java on Windows のデフォルトは Direct3D (D3D) パイプラインであるが、特定の条件下（特に透過ウィンドウ）では OpenGL パイプラインの方が高性能な場合がある。

#### **4.1.1 Direct3D パイプライン (-Dsun.java2d.d3d=true)**

* **特徴**: Windowsの標準。OSとの親和性が高く、ドライバサポートも安定している。  
* **課題**: 透過ウィンドウに対する描画において、Java 2Dの実装が保守的であり、ハードウェアアクセラレーションを無効化（ソフトウェアレンダリングへのフォールバック）するケースが多い 8。特に AlphaComposite を多用する場合、Read-Modify-Writeのオーバーヘッドを避けるためにCPU処理に切り替わることがある。  
* **設定**: sun.java2d.d3d プロパティで制御。

#### **4.1.2 OpenGL パイプライン (-Dsun.java2d.opengl=true)**

* **特徴**: クロスプラットフォームなAPI。NVIDIAなどのGPUドライバによっては、D3Dよりも効率的なコンテキスト切り替えやFBO（Frame Buffer Object）の利用が期待できる 2。  
* **課題**: Windows上ではデフォルトで無効化されており、ドライバのバグを踏むリスクが高い（画面のちらつき、クラッシュなど） 26。また、DWMとの連携において、D3Dほど最適化されていない場合がある。  
* **メリット**: 透過画像の扱いやFBOによるオフスクリーンレンダリングにおいて、D3Dパイプラインよりも柔軟にハードウェア機能を利用できる可能性がある。

**推奨**: 基本的には **Direct3D** を維持しつつ、後述する検証手段でアクセラレーションが効いていないことが判明した場合にのみ OpenGL を試行する。

### **4.2 ハードウェアアクセラレーションの検証手法**

「設定したから速くなるはず」という推測は危険である。Java 2Dが実際にハードウェアパイプラインを使用しているかを検証するために、以下のJVMフラグを用いてトレースログを取得・分析する 28。  
**起動引数:**

Bash

\-Dsun.java2d.trace=log,timestamp,count,out:java2d.log

ログの分析ポイント:  
生成された java2d.log を分析し、以下のパターンを確認する。

| ログ出力例 | 意味 | パフォーマンス評価 |
| :---- | :---- | :---- |
| D3DBlitLoops::Blit(Texture-\>Surface) | テクスチャ（VRAM）から画面（VRAM）への転送 | **優良** (HW加速) |
| D3DMaskBlit(Texture-\>Surface) | アルファマスク付き転送 | **良** (HW加速) |
| MaskBlit(IntArgb, SrcOver, IntArgb) | CPUメモリ間での合成 | **不良** (ソフトウェア) |
| AnyBlit(...) | 専用ループが見つからず汎用ループを使用 | **最悪** (非常に遅い) |
| Lock(...) / Unlock(...) | サーフェスのロック | 頻発する場合、CPUがVRAMにアクセスしている証拠（ボトルネック） |

特に、マスコットの描画ループ内で MaskBlit(IntArgb,...) が多発している場合、透過処理がCPUで行われていることを意味する。この場合、VolatileImage の構成を見直すか、パイプライン設定を変更する必要がある 7。

### **4.3 Windows 11 向け最適化フラグセット**

Shimeji Neo において推奨される JVM オプションの組み合わせは以下の通りである。

Bash

\# 基本的なD3D有効化  
\-Dsun.java2d.d3d=true  
\# ソフトウェアレンダリング（No Direct Draw）を無効化し、強制的にHWを使用試行  
\-Dsun.java2d.noddraw=false   
\# 高Dピクセル対応（Windowsのスケーリングによるぼやけ防止）  
\-Dsun.java2d.uiScale.enabled=true  
\# 透過アクセラレーションの強制（非公式フラグだが効果がある場合あり）  
\-Dsun.java2d.transaccel=true  
\# VRAMへのイメージキャッシュを積極的に行う  
\-Dsun.java2d.accthreshold=0

もしD3Dで十分な性能が出ない場合、OpenGLへの切り替えをテストする：

Bash

\-Dsun.java2d.opengl=true  
\# FBOの使用を強制（透過処理の高速化に寄与）  
\-Dsun.java2d.opengl.fbobject=true

## ---

**5\. 先進的ネイティブレンダリング：Project Panama と DirectComposition**

AWT/Swingの最適化（Active Rendering, VolatileImage）を行っても、最終的に UpdateLayeredWindow というWin32 APIの壁にぶつかる可能性がある。50体以上のマスコットを60 FPSで動かすという極限の性能を追求する場合、Javaの標準ライブラリをバイパスし、Windowsの合成エンジンである **DirectComposition** を直接叩くアーキテクチャへの移行が最終解となる。

### **5.1 DirectComposition のアーキテクチャと優位性**

DirectComposition は、Windows 8から導入された高性能なビットマップ合成APIであり、DWM (Desktop Window Manager) と密接に統合されている 9。

#### **5.1.1 従来の Layered Window との違い**

* **Layered Window (UpdateLayeredWindow)**: アプリケーションがビットマップを用意し、APIを呼んでOSに渡す。OSはそれをコピーして合成する。CPUとメモリバスの帯域を消費する。  
* **DirectComposition**: アプリケーションは「Visual Tree（視覚ツリー）」を構築し、各ノードにテクスチャ（DXGI Surface）を紐付ける。実際の合成・移動・変形・透明度計算は、**DWMプロセス内のGPUスレッド** で非同期に実行される 15。

#### **5.1.2 マスコットアプリへの応用**

50個のウィンドウ（HWND）を作成する代わりに、以下の構成をとることで劇的な軽量化が可能である。

1. **単一の透明なトップレベルウィンドウ**: デスクトップ全体、あるいはマスコットの活動範囲を覆う透明なウィンドウを1つだけ作成する（入力イベント受け取り用）。  
2. **Visual Tree**: 各マスコットを DirectComposition の IDCompositionVisual オブジェクトとして表現する。  
3. **GPU合成**: マスコットの移動は、Visual の SetOffsetX / SetOffsetY プロパティを変更して Commit するだけである。ピクセルデータの転送は発生しない。

### **5.2 Project Panama (FFM API) による実装**

Java 21 (Preview) / 22 (Final) で導入された **Foreign Function & Memory (FFM) API**（Project Panama）を利用することで、JNI (Java Native Interface) のようなC++グルーコードを書くことなく、Javaから直接ネイティブライブラリ（dcomp.dll, d3d11.dll）を呼び出すことが可能になった 31。

#### **5.2.1 相互運用性の複雑さとパフォーマンス**

Project Panama を用いた DirectComposition の実装は、AWTに比べて実装難易度が桁違いに高いが、パフォーマンスゲインもまた桁違いである。

| 項目 | AWT/Swing | Project Panama \+ DirectComposition |
| :---- | :---- | :---- |
| **実装難易度** | 低（標準API） | 極めて高（COM, 手動メモリ管理） |
| **CPU負荷** | 高（描画・転送） | 極小（コマンド発行のみ） |
| **GPU活用** | 部分的 | 完全（DWMによる合成） |
| **ウィンドウ数限界** | \~20体で限界 | 100体以上も容易 |

#### **5.2.2 実装ステップの詳細**

Step 1: jextract によるバインディング生成  
Windows SDKに含まれるヘッダーファイル（dcomp.h, d3d11.h, dxgi.h）から、Javaのクラスを自動生成する。jextract ツールを使用する 34。

Bash

jextract \--output src \-t com.microsoft.win32 \\  
  \-I "C:\\Program Files (x86)\\Windows Kits\\10\\Include\\10.0.xxxxx.0\\um" \\  
  \-l dcomp \\  
  dcomp.h

Step 2: デバイスとターゲットの作成  
Javaコード内で IDCompositionDevice を作成し、ターゲットウィンドウ（HWND）にバインドする。

Java

// 概念実装コード  
try (Arena arena \= Arena.ofConfined()) {  
    // D3D11デバイスの作成（省略）  
    //...  
      
    // DirectCompositionデバイスの作成  
    MemorySegment dcompDevicePtr \= arena.allocate(C\_POINTER);  
    DCompLib.DCompositionCreateDevice(  
        dxgiDevicePtr,   
        IID\_IDCompositionDevice,   
        dcompDevicePtr  
    );  
      
    // ターゲットの作成  
    MemorySegment targetPtr \= arena.allocate(C\_POINTER);  
    IDCompositionDevice.CreateTargetForHwnd(  
        dcompDevicePtr.get(C\_POINTER, 0),  
        hwnd, // Javaのウィンドウハンドルまたはネイティブウィンドウ  
        true,  
        targetPtr  
    );  
}

Step 3: Visual の構築とスプライト共有  
ここが最大の難関である。Javaの BufferedImage のデータを、DirectComposition が理解できる IDCompositionSurface (または ID3D11Texture2D) に転送する必要がある。

* **共有の可否**: Javaのヒープ上のデータを直接GPUが読むことはできない。MemorySegment を用いてオフヒープメモリにピクセルデータを配置し、それを ID3D11DeviceContext::UpdateSubresource などを使ってGPUテクスチャにアップロードする。

Step 4: アニメーションループ  
Java側のゲームループは、ピクセルを描画する代わりに、各マスコットに対応する Visual の座標プロパティを更新し、Commit を呼ぶだけになる。

Java

// ループ内処理  
visual.SetOffsetX(mascot.x);  
visual.SetOffsetY(mascot.y);  
device.Commit(); // DWMに反映指示

このアプローチにより、Javaスレッドは単なる「指令塔」となり、重い画像処理から解放される。

## ---

**6\. メモリ管理とGC最適化 (ZGC & Object Pooling)**

描画パイプラインが最適化されても、頻繁なガベージコレクション（GC）による停止（Stop-the-World）が発生すれば、ユーザーは「カクつき（Jank）」を感じる。50体のマスコットが毎フレーム（60FPS）でオブジェクトを生成・破棄する環境におけるメモリ戦略を定義する。

### **6.1 Generational ZGC の採用と効果**

Java 21では、**Generational ZGC** が利用可能となっている（-XX:+UseZGC \-XX:+ZGenerational）。これは従来のZGC（単世代）の弱点を克服し、Shimejiのようなアプリケーションに革命的な恩恵をもたらす 36。

#### **6.1.1 従来型GCとの比較**

* **G1GC**: 汎用的だが、停止時間が数百ミリ秒に達することがあり、フレーム落ちの原因となる。  
* **Legacy ZGC**: 停止時間は短いが、全てのオブジェクトを単一の世代で管理するため、短命オブジェクト（Point, Eventなど）の回収効率が悪く、スループットが低下する場合があった。  
* **Generational ZGC**: 「多くのオブジェクトは若くして死ぬ」という仮説に基づき、Young GenerationとOld Generationを分離。毎フレーム生成される大量の短命オブジェクトを、Young Gen GCで極めて低コストかつ並列に回収する 38。

#### **6.1.2 割り当てストール（Allocation Stall）の回避**

ZGCにおける唯一の敵は「割り当てストール」である。これは、GCスレッドによるメモリ回収が、アプリスレッドによるメモリ確保に追いつかない場合に発生し、スレッドがブロックされる 40。  
これを防ぐためには、十分なヒープサイズ（余裕を持って確保する）とCPUリソースが必要である。  
**推奨設定:**

Bash

\-XX:+UseZGC   
\-XX:+ZGenerational   
\-Xmx4g  \# メモリが許す限り大きく確保し、GC頻度を下げる  
\-XX:SoftMaxHeapSize=2g \# 必要に応じてOSにメモリを返すヒント

### **6.2 Object Pooling の是非と現代的な解釈**

かつてのJavaゲーム開発では、Point や Rectangle などの小規模オブジェクトをプールして再利用する「Object Pooling」が常識であった。しかし、Generational ZGCの時代において、この常識は覆されている 36。

#### **6.2.1 プーリングの弊害**

* **Old Genへの昇格**: プールされたオブジェクトは長生きするため、Old Generationに昇格される。Old GenのGCはYoung Genよりコストが高いため、かえって全体のパフォーマンスを悪化させるリスクがある。  
* **実装コスト**: スレッドセーフなプールの実装は複雑であり、バグの温床となる。  
* **アロケーションの高速化**: 現代のJVMにおけるTLAB（Thread Local Allocation Buffer）からのメモリ確保は、ポインタをインクリメントするだけであり、数ナノ秒で完了する。

#### **6.2.2 結論：何をプールすべきか？**

* **プール不要**: Point, Rectangle, Dimension, 短命な Event オブジェクト。これらは使い捨て（Allocation is cheap）で構わない。ZGCがバックグラウンドで瞬時に回収する。  
* **プール推奨**: byte / int の巨大なバッファ、BufferedImage（再生成コストが高い）、VolatileImage（ネイティブ資源）、DirectByteBuffer。これらは生成・破棄のコストが高いため、再利用が必須である。

### **6.3 Sprite Management: テクスチャアトラスの導入**

50体のマスコットがそれぞれ別々の画像ファイルを読み込み、別々のテクスチャとしてVRAMに転送するのは非効率極まりない。**テクスチャアトラス（Texture Atlas）** の導入を強く推奨する 42。

#### **6.3.1 アトラス化のメリット**

1. **ステート変更の削減**: GPUはテクスチャの切り替え（Bind Texture）を嫌う。全てのマスコットの画像が1枚の巨大なテクスチャ（例えば 4096 x 4096）に含まれていれば、描画時にテクスチャを切り替える必要がなくなり、UV座標を変更するだけで済む。  
2. **VRAM断片化の防止**: 多数の小さなテクスチャを確保するよりも、少数の大きなテクスチャを確保する方がメモリ効率が良い。

#### **6.3.2 実装戦略：動的アトラス生成**

Shimejiはユーザーが任意のマスコットを追加できるため、静的なアトラス（ビルド時に作成）は使えない。実行時に動的にアトラスを構築する。

1. **Bin Packing アルゴリズム**: MaxRects などのアルゴリズムを用いて、ロードされたマスコット画像を効率的に矩形領域に詰め込む 44。  
2. **キャッシュ管理**: 生成されたアトラス画像は Managed Image（BufferedImage）として保持し、必要に応じて VolatileImage に転送する。  
3. **UVマッピング**: 各マスコットのアニメーションフレームがアトラス上のどの矩形（u, v, width, height）に対応するかのルックアップテーブルを作成する。

## ---

**7\. 結論と実装ロードマップ**

本調査により、Shimeji Neoの目標である「50体 @ 60 FPS」の達成には、AWTの受動的な描画モデルからの脱却が不可欠であることが明らかとなった。以下に、リスクと効果に基づいた3段階の実装ロードマップを提示する。

### **Phase 3.1: Active Rendering と ZGC の導入（即時対応）**

既存のSwingコンポーネント構造を維持しつつ、描画ループのみを刷新する。

* **アクション**: JWindow を Window に変更し、BufferStrategy を用いたレンダリングループを実装する。  
* **アクション**: JVMフラグを更新し、Generational ZGC を有効化する。  
* **期待効果**: RepaintManager のボトルネック解消によるCPU負荷低減。フレームレートの安定化（ただし、UpdateLayeredWindow の限界により50体での60FPS完全維持は厳しい可能性がある）。

### **Phase 3.2: リソース管理の最適化（短期的対応）**

* **アクション**: テクスチャアトラスを実装し、スプライト描画の効率化を図る。  
* **アクション**: VolatileImage による合成キャッシュを導入する。  
* **期待効果**: VRAM使用量の削減と、描画スループットの向上。

### **Phase 3.3: Project Panama / DirectComposition への移行（中長期的対応）**

Phase 3.1/3.2 で目標未達の場合、または将来的な拡張性（100体以上など）を見据える場合の最終手段。

* **アクション**: jextract を用いて DirectComposition バインディングを作成。  
* **アクション**: マスコットを Visual として扱う新しいレンダリングバックエンドを実装。  
* **期待効果**: OSの合成機能をフル活用した、圧倒的なパフォーマンスと省電力性。

Shimeji Neo の近代化において、Java 21 の新機能（FFM API, ZGC）は強力な武器となる。これらを適切に組み合わせることで、過去の制約を打ち破り、次世代のデスクトップマスコット体験を提供することが可能である。

## ---

**8\. 補足資料：Active Rendering 実装コード例**

以下は、Swingの影響を受けずに高速描画を行うための、Window と BufferStrategy を用いたレンダリングループの基本実装である。

Java

import java.awt.\*;  
import java.awt.image.BufferStrategy;

public class MascotWindow extends Window {

    private boolean running \= true;  
    private final int TARGET\_FPS \= 60;  
    private final long OPTIMAL\_TIME \= 1000000000 / TARGET\_FPS;

    public MascotWindow(Frame owner) {  
        super(owner);  
        // 透過設定：重要  
        setBackground(new Color(0, 0, 0, 0));  
        setSize(300, 300);  
          
        // OS/Swingからの再描画イベントを無視する  
        setIgnoreRepaint(true);  
        setVisible(true);  
    }

    // レンダリングループの開始  
    public void startLoop() {  
        // ダブルバッファリングの作成  
        createBufferStrategy(2);  
          
        Thread loopThread \= new Thread(this::renderLoop, "RenderThread");  
        loopThread.setPriority(Thread.MAX\_PRIORITY);  
        loopThread.start();  
    }

    private void renderLoop() {  
        BufferStrategy bs \= getBufferStrategy();  
        long lastLoopTime \= System.nanoTime();

        while (running) {  
            long now \= System.nanoTime();  
            long updateLength \= now \- lastLoopTime;  
            lastLoopTime \= now;  
              
            // ゲームロジックの更新（デルタタイムに基づく）  
            updateState(updateLength);

            // 描画処理  
            do {  
                do {  
                    Graphics2D g \= (Graphics2D) bs.getDrawGraphics();  
                      
                    // レンダリング品質の設定（速度優先）  
                    g.setRenderingHint(RenderingHints.KEY\_RENDERING, RenderingHints.VALUE\_RENDER\_SPEED);  
                    g.setRenderingHint(RenderingHints.KEY\_ALPHA\_INTERPOLATION, RenderingHints.VALUE\_ALPHA\_INTERPOLATION\_SPEED);

                    try {  
                        // 1\. バッファのクリア（重要：AlphaComposite.Clearで透明に戻す）  
                        g.setComposite(AlphaComposite.Clear);  
                        g.fillRect(0, 0, getWidth(), getHeight());

                        // 2\. マスコットの描画  
                        g.setComposite(AlphaComposite.SrcOver);  
                        drawMascot(g);  
                          
                    } finally {  
                        g.dispose();  
                    }  
                } while (bs.contentsRestored()); // VolatileImageがリストアされたら再描画

                // 3\. 画面への反映  
                bs.show();  
                // Linux/WindowsによってはToolkit.sync()が必要な場合がある  
                Toolkit.getDefaultToolkit().sync();  
                  
            } while (bs.contentsLost()); // VRAMロスト時は再試行

            // FPS制御のためのスリープ  
            long sleepTime \= (lastLoopTime \- System.nanoTime() \+ OPTIMAL\_TIME) / 1000000;  
            if (sleepTime \> 0\) {  
                try { Thread.sleep(sleepTime); } catch (InterruptedException e) {}  
            }  
        }  
    }

    private void updateState(long delta) {  
        // マスコットの位置計算やアニメーションフレーム更新  
    }

    private void drawMascot(Graphics2D g) {  
        // 現在のフレームを描画  
        // g.drawImage(currentFrame, 0, 0, null);  
    }  
}

#### **引用文献**

1. JWindow (Java SE 11 & JDK 11 ) \- Oracle Help Center, 12月 27, 2025にアクセス、 [https://docs.oracle.com/en/java/javase/11/docs/api/java.desktop/javax/swing/JWindow.html](https://docs.oracle.com/en/java/javase/11/docs/api/java.desktop/javax/swing/JWindow.html)  
2. Java 2D Drawing Optimal Performance \- Stack Overflow, 12月 27, 2025にアクセス、 [https://stackoverflow.com/questions/148478/java-2d-drawing-optimal-performance](https://stackoverflow.com/questions/148478/java-2d-drawing-optimal-performance)  
3. Low-latency painting in AWT and Swing \- Pavel Fatin, 12月 27, 2025にアクセス、 [https://pavelfatin.com/low-latency-painting-in-awt-and-swing/](https://pavelfatin.com/low-latency-painting-in-awt-and-swing/)  
4. How to repaint only dirty region in Swing? \- Stack Overflow, 12月 27, 2025にアクセス、 [https://stackoverflow.com/questions/46331181/how-to-repaint-only-dirty-region-in-swing](https://stackoverflow.com/questions/46331181/how-to-repaint-only-dirty-region-in-swing)  
5. Passive vs. Active Rendering \- The Java Tutorials, 12月 27, 2025にアクセス、 [https://docs.oracle.com/javase/tutorial/extra/fullscreen/rendering.html](https://docs.oracle.com/javase/tutorial/extra/fullscreen/rendering.html)  
6. Double Buffering and Active Rendering in Java with Swing Integration, 12月 27, 2025にアクセス、 [https://www.jamesgames.org/resources/double\_buffer/double\_buffering\_and\_active\_rendering.html](https://www.jamesgames.org/resources/double_buffer/double_buffering_and_active_rendering.html)  
7. Java 2D \- Oracle Help Center, 12月 27, 2025にアクセス、 [https://docs.oracle.com/en/java/javase/21/troubleshoot/java-2d.html](https://docs.oracle.com/en/java/javase/21/troubleshoot/java-2d.html)  
8. JDK-8165212 VolatileImage should not be compatible with GraphicsConfiguration which transform is changed \- Java Bug Database, 12月 27, 2025にアクセス、 [https://bugs.java.com/bugdatabase/view\_bug?bug\_id=8165212](https://bugs.java.com/bugdatabase/view_bug?bug_id=8165212)  
9. Windows with C++ \- High-Performance Window Layering Using the Windows Composition Engine | Microsoft Learn, 12月 27, 2025にアクセス、 [https://learn.microsoft.com/en-us/archive/msdn-magazine/2014/june/windows-with-c-high-performance-window-layering-using-the-windows-composition-engine](https://learn.microsoft.com/en-us/archive/msdn-magazine/2014/june/windows-with-c-high-performance-window-layering-using-the-windows-composition-engine)  
10. High-Performance Window Layering Using the Windows Composition Engine | Kenny Kerr, 12月 27, 2025にアクセス、 [https://kennykerrca.wordpress.com/2014/06/02/high-performance-window-layering-using-the-windows-composition-engine/](https://kennykerrca.wordpress.com/2014/06/02/high-performance-window-layering-using-the-windows-composition-engine/)  
11. Painting in AWT and Swing \- Oracle, 12月 27, 2025にアクセス、 [https://www.oracle.com/java/technologies/painting.html](https://www.oracle.com/java/technologies/painting.html)  
12. swing \- Java 6 \- how to make JWindow transparent? \- Stack Overflow, 12月 27, 2025にアクセス、 [https://stackoverflow.com/questions/18880384/java-6-how-to-make-jwindow-transparent](https://stackoverflow.com/questions/18880384/java-6-how-to-make-jwindow-transparent)  
13. linux \- Java transparent window \- Stack Overflow, 12月 27, 2025にアクセス、 [https://stackoverflow.com/questions/11844927/java-transparent-window](https://stackoverflow.com/questions/11844927/java-transparent-window)  
14. Window Features \- Win32 apps \- Microsoft Learn, 12月 27, 2025にアクセス、 [https://learn.microsoft.com/en-us/windows/win32/winmsg/window-features](https://learn.microsoft.com/en-us/windows/win32/winmsg/window-features)  
15. Window regions vs layered windows \- winapi \- Stack Overflow, 12月 27, 2025にアクセス、 [https://stackoverflow.com/questions/48448739/window-regions-vs-layered-windows](https://stackoverflow.com/questions/48448739/window-regions-vs-layered-windows)  
16. OpenGL VM flag degrade performance on Java2D??? \- JVM Gaming, 12月 27, 2025にアクセス、 [https://jvm-gaming.org/t/opengl-vm-flag-degrade-performance-on-java2d/40649](https://jvm-gaming.org/t/opengl-vm-flag-degrade-performance-on-java2d/40649)  
17. 12.1 Generic Performance Issues \- Oracle Help Center, 12月 27, 2025にアクセス、 [https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/java2d001.html](https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/java2d001.html)  
18. Algorithms for rendering overlapping (floating) windows in 2D, 12月 27, 2025にアクセス、 [https://computergraphics.stackexchange.com/questions/13083/algorithms-for-rendering-overlapping-floating-windows-in-2d](https://computergraphics.stackexchange.com/questions/13083/algorithms-for-rendering-overlapping-floating-windows-in-2d)  
19. Problem repainting overlapping JPanels (Swing / AWT / SWT forum at Coderanch), 12月 27, 2025にアクセス、 [https://coderanch.com/t/338654/java/repainting-overlapping-JPanels](https://coderanch.com/t/338654/java/repainting-overlapping-JPanels)  
20. java \- Active Rendering using Only Swing \- Stack Overflow, 12月 27, 2025にアクセス、 [https://stackoverflow.com/questions/28986963/active-rendering-using-only-swing](https://stackoverflow.com/questions/28986963/active-rendering-using-only-swing)  
21. Java Examples for java.awt.image.BufferStrategy \- Javatips.net, 12月 27, 2025にアクセス、 [https://www.javatips.net/api/java.awt.image.bufferstrategy](https://www.javatips.net/api/java.awt.image.bufferstrategy)  
22. VolatileImage (Java SE 21 & JDK 21\) \- Oracle Help Center, 12月 27, 2025にアクセス、 [https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/java/awt/image/VolatileImage.html](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/java/awt/image/VolatileImage.html)  
23. VolatileImage (Java Platform SE 8 ) \- Oracle Help Center, 12月 27, 2025にアクセス、 [https://docs.oracle.com/javase/8/docs/api/java/awt/image/VolatileImage.html](https://docs.oracle.com/javase/8/docs/api/java/awt/image/VolatileImage.html)  
24. Awesome Speeds With VolatileImage \- Java 2D \- JVM Gaming, 12月 27, 2025にアクセス、 [https://jvm-gaming.org/t/awesome-speeds-with-volatileimage/39754?page=2](https://jvm-gaming.org/t/awesome-speeds-with-volatileimage/39754?page=2)  
25. Hardware accelerate bitmap drawing in java \- Stack Overflow, 12月 27, 2025にアクセス、 [https://stackoverflow.com/questions/4178907/hardware-accelerate-bitmap-drawing-in-java](https://stackoverflow.com/questions/4178907/hardware-accelerate-bitmap-drawing-in-java)  
26. Exception with java2d.opengl=true on Windows \- jogamp, 12月 27, 2025にアクセス、 [https://forum.jogamp.org/Exception-with-java2d-opengl-true-on-Windows-td4029893.html](https://forum.jogamp.org/Exception-with-java2d-opengl-true-on-Windows-td4029893.html)  
27. \[FlatLaf 3.5.1\] Repaint issue on Windows 11 \#887 \- GitHub, 12月 27, 2025にアクセス、 [https://github.com/JFormDesigner/FlatLaf/issues/887](https://github.com/JFormDesigner/FlatLaf/issues/887)  
28. Java Hardware Acceleration \- Stack Overflow, 12月 27, 2025にアクセス、 [https://stackoverflow.com/questions/4627320/java-hardware-acceleration](https://stackoverflow.com/questions/4627320/java-hardware-acceleration)  
29. System Properties for Java 2D Technology \- Oracle Help Center, 12月 27, 2025にアクセス、 [https://docs.oracle.com/javase/8/docs/technotes/guides/2d/flags.html](https://docs.oracle.com/javase/8/docs/technotes/guides/2d/flags.html)  
30. How to build a simple visual tree \- Win32 apps | Microsoft Learn, 12月 27, 2025にアクセス、 [https://learn.microsoft.com/en-us/windows/win32/directcomp/how-to--build-a-visual-tree](https://learn.microsoft.com/en-us/windows/win32/directcomp/how-to--build-a-visual-tree)  
31. Project Panama: Interconnecting JVM and native code \- OpenJDK, 12月 27, 2025にアクセス、 [https://openjdk.org/projects/panama/](https://openjdk.org/projects/panama/)  
32. From C to Java Code using Panama \- Mostly nerdless, 12月 27, 2025にアクセス、 [https://mostlynerdless.de/blog/2023/12/11/from-c-to-java-code-using-panama/](https://mostlynerdless.de/blog/2023/12/11/from-c-to-java-code-using-panama/)  
33. Foreign Function & Memory API \- A (Quick) Peek Under the Hood \- YouTube, 12月 27, 2025にアクセス、 [https://www.youtube.com/watch?v=iwmVbeiA42E](https://www.youtube.com/watch?v=iwmVbeiA42E)  
34. jextract/doc/GUIDE.md at master \- GitHub, 12月 27, 2025にアクセス、 [https://github.com/openjdk/jextract/blob/master/doc/GUIDE.md](https://github.com/openjdk/jextract/blob/master/doc/GUIDE.md)  
35. Calling Native Functions with jextract \- Java \- Oracle Help Center, 12月 27, 2025にアクセス、 [https://docs.oracle.com/en/java/javase/24/core/call-native-functions-jextract.html](https://docs.oracle.com/en/java/javase/24/core/call-native-functions-jextract.html)  
36. Leveraging Generational ZGC for Optimal Temporary Object Management \- Baeldung, 12月 27, 2025にアクセス、 [https://www.baeldung.com/java-z-garbage-collector](https://www.baeldung.com/java-z-garbage-collector)  
37. 9 The Z Garbage Collector \- Oracle Help Center, 12月 27, 2025にアクセス、 [https://docs.oracle.com/en/java/javase/21/gctuning/z-garbage-collector.html](https://docs.oracle.com/en/java/javase/21/gctuning/z-garbage-collector.html)  
38. Bending pause times to your will with Generational ZGC | by Netflix Technology Blog, 12月 27, 2025にアクセス、 [https://netflixtechblog.com/bending-pause-times-to-your-will-with-generational-zgc-256629c9386b](https://netflixtechblog.com/bending-pause-times-to-your-will-with-generational-zgc-256629c9386b)  
39. ZGC vs G1GC for Scala \- Lunatech's engineer blog, 12月 27, 2025にアクセス、 [https://blog.lunatech.com/posts/2025-02-07-zgc-vs-g1gc-for-scala](https://blog.lunatech.com/posts/2025-02-07-zgc-vs-g1gc-for-scala)  
40. Let's Take a Look at... Lower Java Tail Latencies With ZGC \- Gunnar Morling, 12月 27, 2025にアクセス、 [https://www.morling.dev/blog/lower-java-tail-latencies-with-zgc/](https://www.morling.dev/blog/lower-java-tail-latencies-with-zgc/)  
41. Is Pooling for Small Objects More Efficient than Android's Java Garbage Collector?, 12月 27, 2025にアクセス、 [https://stackoverflow.com/questions/17098476/is-pooling-for-small-objects-more-efficient-than-androids-java-garbage-collecto](https://stackoverflow.com/questions/17098476/is-pooling-for-small-objects-more-efficient-than-androids-java-garbage-collecto)  
42. Why Atlas Textures increase the 2D game performance? \- Stack Overflow, 12月 27, 2025にアクセス、 [https://stackoverflow.com/questions/28006061/why-atlas-textures-increase-the-2d-game-performance](https://stackoverflow.com/questions/28006061/why-atlas-textures-increase-the-2d-game-performance)  
43. Here's the performance gain in using textures atlases in my game\! : r/Unity3D \- Reddit, 12月 27, 2025にアクセス、 [https://www.reddit.com/r/Unity3D/comments/figs1u/heres\_the\_performance\_gain\_in\_using\_textures/](https://www.reddit.com/r/Unity3D/comments/figs1u/heres_the_performance_gain_in_using_textures/)  
44. 2D Rendering with SDF's and Atlases | Randy Gaul's Game Programming Blog, 12月 27, 2025にアクセス、 [https://randygaul.github.io/graphics/2025/03/04/2D-Rendering-SDF-and-Atlases.html](https://randygaul.github.io/graphics/2025/03/04/2D-Rendering-SDF-and-Atlases.html)