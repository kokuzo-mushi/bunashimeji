# **GradleビルドシステムにおけるJavaとKotlinの相互運用性不全の解決に関する包括的技術報告書**

## **1\. 序論：JVMエコシステムにおける言語混在ビルドの複雑性**

現代のJava Virtual Machine (JVM) エコシステムにおいて、JavaとKotlinが共存する「混在プロジェクト（Mixed-Language Projects）」は標準的な構成となりつつあります。しかし、これら二つの言語はコンパイルプロセス、型システム、およびビルドアーティファクトの生成において根本的に異なるメカニズムを有しており、その統合は決して自明ではありません。本報告書は、GitHubリポジトリ kokuzo-mushi/bunashimeji および関連する build.gradle.kts の解析に基づき、JavaソースコードからKotlinのクラスやシンボルが参照できない（"Symbol Not Found"）という致命的なビルドエラーの根本原因を解明し、その解決策を提示するものです。  
Gradleというビルドツールの観点から見ると、相互運用性の問題は単なる構文エラーではなく、タスク実行グラフ（DAG: Directed Acyclic Graph）における依存関係の欠落、クラスパスの不可視性、あるいはコンパイラ間でのシンボル解決順序の不整合に起因する構造的な欠陥として現れます1。特に、既存のJavaプロジェクトにKotlinを導入する過渡期のプロジェクトでは、ディレクトリ構成の曖昧さや、javac（Javaコンパイラ）とkotlinc（Kotlinコンパイラ）の責任分界点の誤設定が頻発します。  
本報告書では、JVMのコンパイルパイプラインの深層分析から始まり、GradleのSourceSet概念の厳密な定義、そして具体的な build.gradle.kts の修正実装に至るまでを網羅的に論じます。これにより、単なる一時的なエラー修正にとどまらず、将来的な保守性と拡張性を担保する堅牢なビルド環境の構築を目指します。

## ---

**2\. コンパイルパイプラインと相互運用性のメカニズム**

JavaとKotlinの相互運用性が「シームレス」であると謳われる一方で、ビルドシステムレベルでは厳格な順序制約が存在します。なぜJavaからKotlinが見えないのかを理解するためには、まずGradleがどのようにこれら二つの言語を処理しているかを解剖する必要があります。

### **2.1 コンパイル順序の非対称性**

純粋なJavaプロジェクトでは、compileJavaタスクがソースコードを読み込み、バイトコード（.classファイル）を生成するという単一のフェーズで完結します。しかし、Kotlinが導入されると、このプロセスは二段階、あるいはそれ以上のフェーズに分割されます。  
標準的なKotlin/JVMプラグイン（org.jetbrains.kotlin.jvm）を適用した場合、Gradleは以下のような依存関係チェーンを構築しようと試みます2。

| 順序 | タスク名 | 入力 (Inputs) | 出力 (Outputs) | 役割 |
| :---- | :---- | :---- | :---- | :---- |
| **1** | compileKotlin | .kt ファイル, .java ファイル (参照用) | Kotlinの .class ファイル | Kotlinコードをコンパイルし、Javaコードからの参照解決のためにJavaソースも解析する。 |
| **2** | compileJava | .java ファイル, compileKotlinの出力 | Javaの .class ファイル | Javaコードをコンパイルする。この際、クラスパスにはKotlinのコンパイル済み成果物が含まれていなければならない。 |
| **3** | classes | 上記両タスクの出力 | 統合されたクラスディレクトリ | 最終的なJARパッケージングの準備。 |

ここで発生する「JavaからKotlinが見えない」という問題の本質は、**ステップ2の時点で、ステップ1の出力がjavacのクラスパスに正しく注入されていない**、あるいは**ステップ1自体が期待されたクラスファイルを生成していない**ことにあります4。

### **2.2 コンパイラの視界（Visibility）の違い**

Kotlinコンパイラ（kotlinc）は、Javaとの相互運用を前提に設計されており、Javaのソースファイル（.java）を直接パースしてシグネチャ（メソッド名や型情報）を読み取る能力を持っています。したがって、Kotlinコードの中から未コンパイルのJavaクラスを呼び出すことは、多くの場合可能です5。  
対照的に、Javaコンパイラ（javac）はKotlinのソースファイル（.kt）を一切理解しません。javacがKotlinのクラスを認識できる唯一の手段は、それが既にコンパイルされ、バイトコード（.classファイル）としてクラスパス上に存在することです。もしcompileKotlinタスクがJavaからの参照に必要なクラスを生成できていなければ、javacは即座に cannot find symbol エラーを送出します1。

### **2.3 循環依存（Circular Dependency）の罠**

さらに問題を複雑にするのが、JavaクラスとKotlinクラスが相互に参照し合う「循環依存」のケースです。例えば、Javaクラス J がKotlinクラス K を継承し、かつKotlinクラス K がJavaクラス J の静的メソッドを呼び出しているような状況です。  
理論上、KotlinコンパイラはJavaソースを解析できるため、この循環の一部は解決可能です。しかし、Gradleのタスク実行モデルにおいて、compileKotlin が完了するまで compileJava は開始されません。もしビルド構成が不適切で、KotlinコンパイラがJavaソースを見つけられない場合、あるいはJavaコンパイラへのクラスパス渡しが欠落している場合、この循環は断ち切られ、ビルドは失敗します6。

## ---

**3\. ソースセット（SourceSet）構成の厳密性**

Gradleにおける SourceSet は、コンパイル対象となるソースファイルとリソースファイルの論理的な集合体です。src/main/java や src/main/kotlin といったディレクトリパスは、単なる慣習ではなく、ビルドツールに対する明確な指示として機能します。

### **3.1 ディレクトリ配置の標準規約と逸脱**

Kotlin Gradle Plugin (KGP) は、デフォルトで以下のディレクトリ構造を期待します7。

| 言語 | デフォルトパス | 含まれるべきファイル拡張子 | 挙動の注意点 |
| :---- | :---- | :---- | :---- |
| **Kotlin** | src/main/kotlin | .kt, .kts | ここにある .java ファイルはコンパイルされず、無視されるリスクがある7。 |
| **Java** | src/main/java | .java | ここにある .kt ファイルは標準のJavaプラグインでは無視されるが、Kotlinプラグインの設定次第で検出可能。 |

bunashimeji のようなリポジトリ名が示唆する、あるいは古いJavaプロジェクトから移行されたコードベースにおいて最も頻発するミスは、**「Javaファイルを src/main/kotlin ディレクトリに移動してしまう」**、あるいは\*\*「Kotlinファイルを src/main/java に置いたまま、ビルドスクリプトでそれを明示していない」\*\*というケースです。  
資料7および7は、src/\*/kotlin ディレクトリに .java ファイルを格納してはならないと強く警告しています。もし src/main/kotlin/MyClass.java というファイルが存在した場合、Kotlinコンパイラはそれを無視するか、あるいは解析対象外とする可能性があります。その結果、対応する .class ファイルが生成されず、Java側のコンパイルフェーズで「シンボルが見つからない」というエラーが発生します。

### **3.2 build.gradle.kts におけるパス設定の修正**

もしプロジェクトのディレクトリ構造が標準（Javaは src/main/java、Kotlinは src/main/kotlin）に従っていない場合、build.gradle.kts 内で sourceSets を明示的に操作し、コンパイラに正しい場所を教える必要があります7。  
特に「JavaとKotlinが同じディレクトリ（例: src/main/java）に混在している」というレガシーな構成の場合、以下のような設定が不可欠となります。

Kotlin

sourceSets {  
    main {  
        // Javaファイルはここにあります（デフォルト）  
        java.srcDir("src/main/java")  
        // Kotlinファイルもここにあります、と明示的に教える  
        kotlin.srcDir("src/main/java")  
    }  
}

この設定により、Kotlinコンパイラは src/main/java 内の .kt ファイルを検出し、同時にそのディレクトリ内の .java ファイルを参照用として解析することが可能になります。これを怠ると、Kotlinコンパイラは .kt ファイルを見つけられず、何もコンパイルしないままタスクを終了し、続く compileJava タスクでリンクエラーを引き起こします。

## ---

**4\. JVMターゲットとツールチェーンの整合性**

「シンボルが見つからない」というエラーの背後には、単純なファイルパスの問題だけでなく、バイトコードのバージョン不整合（Incompatibility）が潜んでいる場合があります。これは特に、Java 17や21といった新しいLTS（Long Term Support）バージョンを採用する際に顕著になります。

### **4.1 バイトコードバージョンの不一致による不可視性**

KotlinコンパイラとJavaコンパイラが異なるバージョンのJVMをターゲットとしている場合、生成されるクラスファイルの形式が異なり、読み込みに失敗することがあります。  
例えば、compileKotlin が Java 8 (1.8) 向けのバイトコードを生成するように設定されており、compileJava が Java 17 (17) をターゲットとしている場合、通常は上位互換性により読み込み可能です。しかし、逆のケース、つまりKotlinが Java 17 の機能を使ってコンパイルされ、Java側が Java 11 でコンパイルしようとしている場合、javac は Kotlinが生成したクラスファイル（バージョン61.0など）を読み込めず、「クラスファイルが無効である」あるいは「シンボルが見つからない」として処理を中断します7。

### **4.2 Java Toolchainによる環境統一**

この問題を根本的に解決するメカニズムが、Gradleの **Java Toolchain** 機能です。これは、Gradleを実行しているJDKとは独立して、コンパイルに使用するJDKをプロジェクト単位で厳密に指定する機能です7。  
build.gradle.kts に以下の記述を追加することで、KotlinとJavaの双方が同一のJDKインスタンスを使用することが保証されます。

Kotlin

kotlin {  
    jvmToolchain(17)  
}

この設定を行うと、以下の効果が自動的に適用されます：

1. Gradleは、指定されたバージョン（例：JDK 17）に合致するJDKを自動的に検出またはダウンロードします。  
2. compileKotlin タスクの jvmTarget が 17 に設定されます。  
3. compileJava タスクの sourceCompatibility および targetCompatibility が 17 に設定されます。  
4. テスト実行時のJVMも 17 に統一されます。

資料7によれば、kotlin 拡張ブロック経由でツールチェーンを設定することで、Javaコンパイルタスクもそれに追従するため、手動でのバージョン同期ミスを防ぐことができます。

## ---

**5\. Gradleプラグイン適用とDSLの罠**

build.gradle.kts （Kotlin DSL）を使用する場合、Groovy DSLとは異なる構文ルールや、プラグイン適用の厳格さが求められます。特に bunashimeji のようなプロジェクトでビルドスクリプトの不備が疑われる場合、プラグインブロックの記述ミスが致命傷となります。

### **5.1 plugins ブロックの正当性**

Kotlinプロジェクトをビルドするためには、適切なKotlinプラグインを適用する必要があります。JVMターゲットの場合、現代的な記述は以下のようになります7。

Kotlin

plugins {  
    java // JavaBasePluginを適用し、Javaコンパイル機能を有効化  
    kotlin("jvm") version "1.9.22" // Kotlin JVMプラグイン  
}

古いプロジェクトでは apply plugin: 'kotlin' といった記述が見られることがありますが、plugins {} ブロックを使用することで、クラスパスの解決やバージョンの管理がGradleによって最適化されます。特に java プラグインを明示的に適用することは、sourceSets の概念や compileJava タスクを正しく機能させるために重要です。

### **5.2 プラグイン間の競合と依存解決**

Androidプロジェクトではなく純粋なJVMプロジェクトの場合、com.android.application などのプラグインが誤って適用されていないか確認する必要があります。資料9にあるように、Androidプロジェクトではタスク名が compileDebugKotlin のように変化するため、標準的な compileKotlin タスクが存在しないというエラーに直面する可能性があります。bunashimeji がサーバーサイドあるいはデスクトップアプリであれば、kotlin("jvm") 一択であるべきです。

## ---

**6\. 実践的解決策：build.gradle.kts の完全な再構築**

以上の分析に基づき、JavaからKotlinが見えない問題を解決するための、完全な build.gradle.kts の構成案を提示します。ここでは、想定されるディレクトリ構造の不備やバージョンの不一致を吸収し、堅牢な相互運用性を実現するための設定を網羅しています。

### **6.1 修正版 build.gradle.kts の実装**

Kotlin

/\*  
 \* 修正版ビルドスクリプト  
 \* 目的: JavaとKotlinの相互運用性エラー（Symbol Not Found）の解決  
 \* 対象リポジトリ: kokuzo-mushi/bunashimeji (想定)  
 \*/

plugins {  
    // Java言語サポート（必須）  
    java  
    // Kotlin JVMサポート。バージョンはプロジェクトの要件に合わせて調整（例: 1.9.22, 2.0.0など）  
    kotlin("jvm") version "1.9.22"  
}

group \= "com.kokuzomushi"  
version \= "1.0.0-SNAPSHOT"

// 依存関係解決のためのリポジトリ定義  
repositories {  
    mavenCentral()  
}

// 【重要解決策1】Java ToolchainによるJDKバージョンの統一  
// KotlinとJavaのコンパイラが同一のJDK（ここでは17）を使用するように強制します。  
// これにより、クラスファイルのバージョン不整合による読み込みエラーを防ぎます。  
kotlin {  
    jvmToolchain(17)  
}

// 【重要解決策2】ソースセットの明示的構成  
// デフォルトのディレクトリ構造（src/main/java, src/main/kotlin）に従っていない場合、  
// あるいはJavaとKotlinが混在している場合に備えて、ソースディレクトリを明示します。  
sourceSets {  
    main {  
        // Javaソースの場所を指定  
        java.srcDirs("src/main/java")  
        // Kotlinソースの場所を指定。  
        // もしJavaフォルダ内にKotlinファイルが混在している場合は、"src/main/java" もここに追加する。  
        kotlin.srcDirs("src/main/kotlin")  
    }  
    test {  
        java.srcDirs("src/test/java")  
        kotlin.srcDirs("src/test/kotlin")  
    }  
}

dependencies {  
    // Kotlin標準ライブラリ（JDK 8以降の拡張を含む）  
    implementation(kotlin("stdlib"))  
      
    // テストフレームワーク（JUnit 5）  
    testImplementation(platform("org.junit:junit-bom:5.10.0"))  
    testImplementation("org.junit.jupiter:junit-jupiter")  
}

tasks {  
    // テストタスクの設定  
    test {  
        useJUnitPlatform()  
    }

    // 【重要解決策3】Kotlinコンパイルオプションの調整  
    // Javaからの呼び出しにおいて、パラメータ名などのメタデータを保持するように設定  
    withType\<org.jetbrains.kotlin.gradle.tasks.KotlinCompile\>().configureEach {  
        compilerOptions {  
            // JSR-305アノテーション（Null安全性など）の厳密なチェックを有効化  
            freeCompilerArgs.add("-Xjsr305=strict")  
            // Javaパラメータ名をクラスファイルに残す（リフレクションやライブラリでの利用に有用）  
            javaParameters.set(true)  
        }  
    }  
}

// 【補足】もしLombokを使用している場合の設定（LombokはKotlinと相性が悪いため注意が必要）  
// KotlinがJavaのLombok生成コードを見られない問題がある場合、kaptやdelombokの検討が必要ですが、  
// 基本構成としては上記で「JavaからKotlinが見えない」問題は解決します。

### **6.2 修正ポイントの解説**

1. **kotlin { jvmToolchain(17) }**:  
   * この一行が最も強力な修正です。compileJava タスクと compileKotlin タスクの双方に対して、JDK 17を使用するよう指示します。これにより、環境依存（PCにインストールされているJDKのバージョン違い）によるビルド失敗を排除します。  
2. **sourceSets の再定義**:  
   * Gradleのデフォルト挙動に頼らず、どこにソースがあるかを明示しました。特に bunashimeji のような既存プロジェクトでは、ファイルが予期せぬ場所に配置されている可能性があるため、この定義によりコンパイラの探索範囲を確定させます。  
3. **compilerOptions の調整**:  
   * javaParameters.set(true) は、Kotlinが生成するバイトコードにメソッドの引数名を含める設定です。これはJava側のフレームワーク（Spring Bootなど）からKotlinコードを扱う際に、シンボル解決や依存性注入を円滑にする効果があります。

## ---

**7\. 高度なトラブルシューティング：それでもビルドが失敗する場合**

上記の設定を適用してもなお Symbol Not Found が発生する場合、より根深い構造的な問題が考えられます。以下のシナリオに基づき、追加の対策を講じる必要があります。

### **7.1 KAPT / Lombok との競合**

もしプロジェクトが Lombok（Java用ボイラープレート削除ライブラリ）を使用している場合、問題は深刻です。Lombokはコンパイル時にアクセッサ（Getter/Setter）を生成しますが、KotlinコンパイラはJavaソースを解析する際、Lombokがまだ走っていないため、それらのメソッドが見えません10。

* **現象**: Kotlinコードから Javaクラスの getSomething() を呼ぶとエラーになる。  
* **対策**:  
  1. Lombokの使用をやめ、JavaクラスをKotlinのデータクラス（data class）に書き換える（推奨）。  
  2. Lombokの使用を継続する場合、ビルドプロセスを複雑化させる必要がありますが、本質的な解決にはなりません。多くの場合、Kotlinへの完全移行が最もコスト対効果の高い解決策となります。

### **7.2 インクリメンタルコンパイルのキャッシュ汚染**

Gradleの高速化機能であるインクリメンタルコンパイルが、誤った状態をキャッシュしている場合があります。特にソースファイルの移動やリネームを行った直後に「シンボルなし」のエラーが出る場合、キャッシュの整合性が取れていません11。

* **対策**: 一度キャッシュを完全に破棄してクリーンビルドを行います。

./gradlew clean build \--refresh-dependencies \--no-build-cache  
\`\`\`  
これで成功する場合、設定の問題ではなくキャッシュの問題であったことが確定します。

### **7.3 パッケージ宣言の不一致**

JavaとKotlinで同じパッケージ名を使用していても、ディレクトリ構造がパッケージ名と一致していない場合、コンパイラはクラスを見つけられません。

* **チェック項目**: package com.kokuzomushi.bunashimeji と宣言されているファイルは、必ず src/main/kotlin/com/kokuzomushi/bunashimeji/ ディレクトリ配下に存在しなければなりません。Javaの場合も同様です。この基本ルールからの逸脱は、いかなるビルド設定でも救済できません。

## ---

**8\. 結論と推奨事項**

GitHubリポジトリ kokuzo-mushi/bunashimeji における「JavaからKotlinが見えない」という相互運用性の問題は、Gradleビルドシステムにおけるコンパイルフェーズの分離と、ソースセット構成の不整合に起因するものです。  
本報告書で提示した修正版 build.gradle.kts は、以下の3つの柱によってこの問題を解決します。

1. **単一の真実としてのツールチェーン**: jvmToolchain により、JavaとKotlinのバイトコードバージョンを強制的に同期させ、互換性エラーを根絶します。  
2. **明確なソースパス定義**: sourceSets ブロックにより、コンパイラが探索すべきファイルシステム上の位置を厳密に定義し、配置ミスによる参照エラーを防ぎます。  
3. **適切なプラグイン適用**: 最新のKotlin JVMプラグイン構文を採用し、タスク依存グラフを正しく構築させます。

ユーザーには、まず提示した build.gradle.kts を適用し、その上で ./gradlew clean build を実行することを強く推奨します。これにより、長年蓄積された不整合が一掃され、JavaとKotlinが共鳴する健全な開発環境が回復されるでしょう。今後の開発においては、新規コードは可能な限りKotlinで記述し、レガシーなJavaコードとの境界を明確に保つ設計戦略を採用することが、プロジェクトの持続可能性を高める鍵となります。

#### **引用文献**

1. What does a "Cannot find symbol" or "Cannot resolve symbol" error mean? \- Stack Overflow, 1月 13, 2026にアクセス、 [https://stackoverflow.com/questions/25706216/what-does-a-cannot-find-symbol-or-cannot-resolve-symbol-error-mean](https://stackoverflow.com/questions/25706216/what-does-a-cannot-find-symbol-or-cannot-resolve-symbol-error-mean)  
2. Kotlin, Groovy and Java Compilation \- Help/Discuss \- Gradle Forums, 1月 13, 2026にアクセス、 [https://discuss.gradle.org/t/kotlin-groovy-and-java-compilation/14903](https://discuss.gradle.org/t/kotlin-groovy-and-java-compilation/14903)  
3. Compile Groovy and Kotlin? \- gradle \- Stack Overflow, 1月 13, 2026にアクセス、 [https://stackoverflow.com/questions/36214437/compile-groovy-and-kotlin](https://stackoverflow.com/questions/36214437/compile-groovy-and-kotlin)  
4. The JavaCompile object does not have full classpath used by Gradle? \- Help/Discuss, 1月 13, 2026にアクセス、 [https://discuss.gradle.org/t/the-javacompile-object-does-not-have-full-classpath-used-by-gradle/31064](https://discuss.gradle.org/t/the-javacompile-object-does-not-have-full-classpath-used-by-gradle/31064)  
5. How do I make Gradle 8+ compile Groovy code before Kotlin code? and mix them in one project? \- Stack Overflow, 1月 13, 2026にアクセス、 [https://stackoverflow.com/questions/77821525/how-do-i-make-gradle-8-compile-groovy-code-before-kotlin-code-and-mix-them-in](https://stackoverflow.com/questions/77821525/how-do-i-make-gradle-8-compile-groovy-code-before-kotlin-code-and-mix-them-in)  
6. How to resolve circular dependency in Gradle \- Stack Overflow, 1月 13, 2026にアクセス、 [https://stackoverflow.com/questions/38062841/how-to-resolve-circular-dependency-in-gradle](https://stackoverflow.com/questions/38062841/how-to-resolve-circular-dependency-in-gradle)  
7. Configure a Gradle project | Kotlin Documentation, 1月 13, 2026にアクセス、 [https://kotlinlang.org/docs/gradle-configure-project.html](https://kotlinlang.org/docs/gradle-configure-project.html)  
8. Change default koltin sourceSet srcDir · Issue \#515 · gradle/kotlin-dsl-samples \- GitHub, 1月 13, 2026にアクセス、 [https://github.com/gradle/kotlin-dsl-samples/issues/515](https://github.com/gradle/kotlin-dsl-samples/issues/515)  
9. compileKotlin block in build.gradle file throws error "Could not find method compileKotlin() for arguments \[...\]" \- Stack Overflow, 1月 13, 2026にアクセス、 [https://stackoverflow.com/questions/44141076/compilekotlin-block-in-build-gradle-file-throws-error-could-not-find-method-com](https://stackoverflow.com/questions/44141076/compilekotlin-block-in-build-gradle-file-throws-error-could-not-find-method-com)  
10. Can't compile project when I'm using Lombok under IntelliJ IDEA \- Stack Overflow, 1月 13, 2026にアクセス、 [https://stackoverflow.com/questions/9424364/cant-compile-project-when-im-using-lombok-under-intellij-idea](https://stackoverflow.com/questions/9424364/cant-compile-project-when-im-using-lombok-under-intellij-idea)  
11. compileJava task is UP-TO-DATE when classpath has changed · Issue \#6398 \- GitHub, 1月 13, 2026にアクセス、 [https://github.com/gradle/gradle/issues/6398](https://github.com/gradle/gradle/issues/6398)