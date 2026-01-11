# **Shimeji Neo 近代化プロジェクト フェーズ4：配布およびパッケージング技術に関する包括的調査報告書**

## **1\. 序論およびプロジェクト概要**

### **1.1 プロジェクトの背景と目的**

デスクトップマスコットアプリケーション「Shimeji Neo」の近代化プロジェクトにおいて、現在直面している最大の課題は、エンドユーザーに対する導入障壁の高さである。現在のビルドおよび実行環境は、Java 21の導入に加え、コマンドラインからの \--enable-preview オプションの指定をユーザーに求めている。これは、開発者層以外の一般ユーザーにとって極めて高いハードルであり、アプリケーションの普及を阻害する要因となっている。  
本フェーズ（Phase 4）の主たる目的は、Gradleビルドシステムと連携した自動化パイプラインを構築し、Javaランタイム環境（JRE）を内包した自己完結型（Self-Contained）のWindowsインストーラー（.exeまたは.msi）を生成することである。これにより、ユーザーは事前の環境構築を意識することなく、ワンクリックでアプリケーションを利用可能となる。

### **1.2 技術的制約と課題**

本プロジェクトには、最新のJava技術とレガシーな依存関係が混在することに起因する、いくつかの技術的特異点が存在する。  
第一に、**Project Panama (Foreign Function & Memory API)** の採用である。Java 21時点において、FFM APIはプレビュー機能（Preview Feature）として提供されているため、実行時にはJVMに対して明示的なフラグ（--enable-preview）を渡す必要がある 1。加えて、ネイティブメモリへのアクセスを許可するためのセキュリティフラグ（--enable-native-access）も必須となり、これをインストーラー生成プロセスにおいて適切に埋め込む手法が求められる 3。  
第二に、**非モジュールライブラリへの依存**である。JNA (Java Native Access) や JAXB といったライブラリは、Java Platform Module System (JPMS) に完全には準拠していない「自動モジュール（Automatic Modules）」として扱われる。これにより、標準的な jlink コマンドによるランタイム生成が失敗するため、ハイブリッドなランタイム構築戦略が必要となる 5。  
第三に、**Windowsインストーラー生成ツールの互換性**である。JDK 21に同梱される jpackage ツールは、バックエンドとして **WiX Toolset v3** を利用する設計となっているが、現在主流となりつつある WiX v4/v5 との間にはコマンドラインインターフェースおよびアーキテクチャ上の非互換性が存在する 7。  
本報告書では、これらの課題に対する詳細な技術調査に基づき、最適なパッケージング戦略、ランタイム最適化手法、セキュリティ対策、およびCI/CDパイプラインの設計を包括的に提案する。

## ---

**2\. Java 21 プレビュー機能とネイティブアクセスの制御戦略**

### **2.1 Project Panama 採用におけるランチャー構成の重要性**

Project Panama (FFM API) は、従来のJNI (Java Native Interface) に代わる安全かつ高効率なネイティブ相互運用APIであるが、Java 21においてはプレビュー段階にある。プレビュー機能は、将来的な仕様変更の可能性を示唆すると同時に、不用意な利用を防ぐためにデフォルトでは無効化されている。したがって、アプリケーションの起動時には必ず \--enable-preview フラグが必要となる 9。  
通常のJARファイル配布であれば、ユーザーに起動スクリプトを提供する形での対応が可能だが、ネイティブインストーラー（.exe）による配布を目指す本プロジェクトにおいては、このフラグを**実行ファイル内部に恒久的に埋め込む**必要がある。jpackage はこのための機構として \--java-options 引数を提供しており、ここにJVM起動オプションを渡すことで、生成されるランチャーが内部的にJVMを初期化する際に指定されたオプションを適用する仕組みとなっている 11。

### **2.2 ネイティブアクセス制御とセキュリティモデル**

Java 9以降のモジュールシステム導入に伴い、カプセル化された内部APIやネイティブコードへのアクセスは厳格に管理されるようになった。特にProject Panamaのようなメモリ操作を伴うAPIは、誤用すればJVMのクラッシュやメモリ破壊を引き起こすリスクがあるため、Java 21では「制限されたメソッド（Restricted Methods）」として扱われる 3。  
これらを利用する際、JVMはデフォルトで警告を出力するが、将来的には実行をブロックする可能性がある。この警告を抑制し、正当な動作として許可するためには \--enable-native-access オプションが必要となる。本プロジェクトで利用するJNAなどのライブラリは、明示的なモジュール記述子（module-info.java）を持たないため、これらは「無名モジュール（Unnamed Module）」として扱われる。したがって、対象を特定のモジュールに限定できず、--enable-native-access=ALL-UNNAMED という広範な許可を与える設定が不可避となる 12。  
この設定はセキュリティの観点からは権限の拡大を意味するが、既存の非モジュールライブラリ資産を活用しつつProject Panamaの恩恵を受けるためには、現状における唯一の解である。jpackage の設定においては、このオプションも \--java-options を通じてランチャーに埋め込むことになる。

| 設定項目 | 必要なフラグ | 理由と背景 |
| :---- | :---- | :---- |
| **プレビュー機能** | \--enable-preview | FFM APIがJava 21でプレビュー段階であるため、JVM起動時に必須。 |
| **ネイティブアクセス** | \--enable-native-access=ALL-UNNAMED | JNA等の非モジュールライブラリがFFM APIまたはUnsafeを利用する際の警告抑制および実行許可。 |
| **ガベージコレクション** | \-XX:+UseZGC (推奨) | デスクトップマスコットとしての応答性を重視する場合、低遅延GCの指定も検討に値する。 |

## ---

**3\. ランタイム最適化とモジュール依存関係の解決**

### **3.1 完全モジュール化の障壁と jlink の制約**

jlink は、アプリケーションが必要とするモジュールのみを抽出してカスタムJREを作成する強力なツールである。しかし、その動作原理は「依存関係グラフの解決」に基づいており、すべての依存関係が明示的なモジュール（Explicit Module）であることを前提としている。  
「Shimeji Neo」が依存する JNA や JAXB は、JARファイルのマニフェストに Automatic-Module-Name が記載されているか、あるいはそれすらない「自動モジュール」である。jlink は仕様上、自動モジュールをリンク対象に含めることができないため、これらを jlink コマンドの \--add-modules に指定するとエラーが発生する 5。これが、非モジュール依存を持つアプリケーションの近代化における最大の障壁となっている。

### **3.2 推奨される解決策：ハイブリッドランタイム・アプローチ**

この問題を解決するために、**ハイブリッドランタイム**と呼ばれるアプローチを採用する。これは、アプリケーションの実行環境を「最小限のJDKモジュール群」と「クラスパス上のアプリケーション/ライブラリ群」に分離する手法である。  
具体的には、以下の手順で構成される。

1. **JDKモジュールの特定**: jdeps ツールを用いて、アプリケーションおよび依存ライブラリが利用している**JDK標準モジュール**（java.base, java.desktop, java.logging など）を特定する 13。  
2. **カスタムJREの生成**: 特定されたJDKモジュールのみを含むランタイムイメージを jlink で生成する。この時点では、アプリケーションJARやJNAなどのサードパーティライブラリは含まれない。  
3. **アプリケーションの配置**: 生成されたパッケージ構造内の所定のディレクトリ（例: app や lib）に、アプリケーションJARと依存ライブラリをそのまま配置する。  
4. **クラスパス起動**: 生成されるランチャー（.exe）に対し、カスタムJREを使用して起動しつつ、アプリケーションとライブラリをクラスパス（-cp）として読み込むよう設定する。

このアプローチにより、jlink の制約を回避しつつ、ユーザー環境に依存しない自己完結型の配布が可能となる。配布サイズに関しても、全JDKをバンドルする場合（数百MB）と比較して、不要なJDKモジュール（コンパイラやサーバー系APIなど）を削除できるため、大幅なサイズ削減（数十MB程度）が見込める 15。

### **3.3 Gradleプラグインの選定：Badass Runtime Plugin**

このハイブリッド構成をGradle上で自動化するために最適なツールが、**Badass Runtime Plugin (org.beryx.runtime)** である 17。  
同作者による badass-jlink-plugin は、すべての依存関係をマージしてモジュール化しようとするアプローチを取るが、これは署名情報の消失やリソース競合のリスクが高く、JNAのような複雑なライブラリを含む場合にはトラブルの原因となりやすい 5。対して badass-runtime-plugin は、前述のハイブリッドアプローチ（非モジュールアプリケーションとしてのパッケージング）を前提に設計されており、jpackage タスクとの統合もスムーズであるため、本プロジェクトの要件に合致する。

## ---

**4\. パッケージング基盤技術：jpackage と WiX Toolset**

### **4.1 jpackage のアーキテクチャと WiX への依存**

Java 14で正式導入された jpackage は、各OSのネイティブインストーラー作成ツールをラップするフロントエンドツールである。Windows環境において .exe または .msi インストーラーを生成する場合、jpackage はバックエンドとして **WiX (Windows Installer XML) Toolset** を利用する 18。  
具体的には、jpackage は内部的にWiXのコンパイラである candle.exe と、リンカである light.exe を呼び出し、XML定義ファイル（.wxs）からMSIデータベースを構築するプロセスを経る。この際、jpackage はこれらのツールが環境パス（PATH）に含まれていることを期待する。

### **4.2 WiX v3 と v4/v5 の非互換性問題**

現在、WiX Toolsetには大きなバージョン断絶が存在する。長らく標準であった **WiX v3** 系に対し、最新の **WiX v4/v5** 系は.NET Core ツールとして再設計され、コマンド体系が根本的に変更された 8。

* **WiX v3**: candle.exe（コンパイル）、light.exe（リンク）という個別の実行ファイルを使用。  
* **WiX v4/v5**: wix build という単一のサブコマンド体系に統合。拡張機能（UIやUtilなど）はNuGetパッケージとして管理される。

極めて重要な点として、Java 21 (LTS) に含まれる jpackage は、WiX v3 のコマンド体系（candle/light）にハードコード依存している 7。  
調査によれば、WiX v4/v5 への対応は JDK 24 で予定されており、JDK 21 ではバックポートされていない 20。したがって、WiX v4/v5 がインストールされた環境で Java 21 の jpackage を実行すると、「candle.exe が見つからない」あるいは「WIX0144 エラー（拡張機能が見つからない）」といった致命的なエラーが発生し、ビルドが失敗する 21。

### **4.3 解決策：WiX v3.11 の継続利用**

GitHub Actionsの windows-latest ランナーや開発環境においては、最新のWiX v4/v5が導入されつつあるが、本プロジェクトでは **WiX v3.11** を明示的に利用する必要がある。  
CI/CDパイプラインにおいては、.NET tool install で WiX v4 を入れるのではなく、従来のインストーラー形式またはバイナリ配布された WiX v3.11 を取得し、その bin ディレクトリにPATHを通す処理が不可欠である 23。これにより、jpackage は正しく candle.exe と light.exe を認識し、インストーラー生成プロセスを完遂できる。

| WiX バージョン | Java 21 jpackage 対応 | 特徴 | 判定 |
| :---- | :---- | :---- | :---- |
| **WiX v3.11** | **対応** | candle.exe, light.exe を使用。枯れた技術で安定。 | **採用** |
| **WiX v4.0+** | 非対応 | wix build コマンド体系。JDK 24以降で対応予定。 | 不採用 |
| **WiX v5.0+** | 非対応 | さらに新しいアーキテクチャ。現状では jpackage と併用不可。 | 不採用 |

## ---

**5\. Windowsセキュリティモデルとコード署名戦略**

### **5.1 SmartScreenと「Unknown Publisher」の壁**

Windows 10/11 のセキュリティ機能である **Microsoft Defender SmartScreen** は、ダウンロードされた実行ファイルの「評価（Reputation）」をチェックする。署名されていない、あるいは新規に発行されたばかりの証明書で署名されたアプリケーションは、評価が確立されていないため、「WindowsによってPCが保護されました（Unknown Publisher）」という警告画面が表示され、実行がブロックされる 25。  
この警告はユーザーに「このアプリは危険である」という強い印象を与えるため、配布において致命的な障害となる。これを回避するには、信頼された認証局（CA）から発行されたコード署名証明書を用いて実行ファイルに署名を行う必要がある。

### **5.2 証明書の種類とコストの課題**

コード署名証明書には主に2つの種類がある 27。

1. **OV (Organization Validation) 証明書**: 組織の実在性を確認して発行される。安価（年間数万円）だが、SmartScreenの評価を確立するまでに一定のダウンロード数と期間を要する（即座に警告は消えない）。  
2. **EV (Extended Validation) 証明書**: 厳格な審査を経て発行される。**SmartScreenの評価が即座に確立され、警告が出ない**。しかし、年間十数万円と高額であり、ハードウェアトークン（USBキー）での秘密鍵管理が義務付けられるため、クラウドCI/CDでの自動化が困難である。

オープンソースプロジェクトにとって、これらのコストと管理の手間は大きな負担である。

### **5.3 オープンソース向け解決策：SignPath.io**

本プロジェクトにおける最適な解は、**SignPath.io** の活用である。SignPath.io は、承認されたオープンソースプロジェクトに対し、**無料でコード署名サービスを提供**している 25。  
SignPath.io の仕組みは、従来の「秘密鍵を開発者が管理する」モデルとは一線を画す。

* **クラウドHSM**: 秘密鍵はSignPathの安全なハードウェアセキュリティモジュール（HSM）で管理され、開発者は直接触れることができない。  
* **ビルドの真正性検証**: SignPathは、GitHub Actionsなどの信頼されたCIシステムからのビルド成果物（Artifact）のみを受け入れ、署名を行う。これにより、「ソースコードからビルドされたもの」であることの証明と署名が紐付けられる 29。  
* **GitHub Actions統合**: 専用のアクション (signpath/github-action-submit-signing-request) が提供されており、ビルドパイプラインにシームレスに組み込むことが可能である 29。

このサービスを利用することで、Shimeji Neoはコストをかけることなく、信頼されたパブリッシャー（SignPath Foundation名義、またはプロジェクト名義）としての署名を得ることができ、SmartScreenの警告を回避（または早期に評価を確立）することが可能となる。

## ---

**6\. 実装詳細仕様書**

### **6.1 Gradle ビルド構成 (build.gradle.kts)**

以下に、推奨される org.beryx.runtime プラグインを用いた build.gradle.kts の構成例を示す。この設定は、Java 21のプレビュー機能有効化、ネイティブアクセス許可、およびインストーラー生成のすべてを網羅している。

Kotlin

plugins {  
    id("application")  
    id("org.beryx.runtime") version "1.13.1" // 最新バージョンを確認して適用すること  
}

repositories {  
    mavenCentral()  
}

dependencies {  
    implementation("net.java.dev.jna:jna:5.14.0")  
    implementation("net.java.dev.jna:jna-platform:5.14.0")  
    // その他の依存関係 (JAXBなど)  
}

application {  
    mainClass.set("com.kokuzomushi.bunashimeji.Main") // アプリケーションのエントリーポイント  
    applicationName \= "ShimejiNeo"  
}

java {  
    toolchain {  
        languageVersion.set(JavaLanguageVersion.of(21))  
    }  
}

// コンパイル時のプレビュー機能有効化  
tasks.withType\<JavaCompile\> {  
    options.compilerArgs.add("--enable-preview")  
    options.release.set(21)  
}

// テスト実行時のプレビュー機能有効化  
tasks.withType\<Test\> {  
    useJUnitPlatform()  
    jvmArgs("--enable-preview")  
}

// Badass Runtime Plugin 設定  
runtime {  
    // 1\. カスタムJRE生成オプション (jlink)  
    // 最小限のランタイムを構成するためのオプション  
    options.set(listOf(  
        "--strip-debug",  
        "--compress", "2",  
        "--no-header-files",  
        "--no-man-pages"  
    ))

    // 2\. 必要なJDKモジュールの指定  
    // アプリケーションと依存ライブラリが必要とするJDKモジュールを列挙する  
    // jdepsコマンドで確認した結果をここに反映する  
    modules.set(listOf(  
        "java.base",  
        "java.desktop",  
        "java.logging",  
        "java.xml",  
        "java.scripting",  
        "jdk.unsupported" // JNA等がUnsafeを使用する場合に必要  
    ))  
      
    // 3\. jpackage (インストーラー生成) 設定  
    jpackage {  
        // 生成されるインストーラーのファイル名形式  
        installerName \= "ShimejiNeo-Installer"  
          
        // ランチャー(.exe)に埋め込むJVM引数  
        // ユーザーが意識することなくプレビュー機能とネイティブアクセスを有効化する  
        // \--java-options はスペース区切りではなく、個別の引数として渡す必要がある場合があるが、  
        // jpackageプラグインの仕様に合わせてリストで渡す  
        javaOptions.set(listOf(  
            "--enable-preview",  
            "--enable-native-access=ALL-UNNAMED",  
            "-Dfile.encoding=UTF-8",  
            "-XX:+UseZGC" // オプション: ガベージコレクションの最適化  
        ))

        // インストーラー作成時のオプション (WiXに渡されるパラメータ等)  
        installerOptions.set(listOf(  
            "--win-per-user-install", // ユーザーごとのインストール  
            "--win-dir-chooser",      // インストール先選択画面を表示  
            "--win-menu",             // スタートメニューに追加  
            "--win-shortcut",         // デスクトップショートカット作成  
            "--vendor", "Shimeji Neo Project",  
            "--copyright", "Copyright (c) 2025 Shimeji Neo Project"  
        ))  
          
        // インストーラーの種類 (msi または exe)  
        installerType \= "msi"   
          
        // アプリアイコンの設定 (icoファイルが必要)  
        // imageOptions.set(listOf("--icon", "src/main/resources/icon.ico"))  
    }  
}

この設定により、./gradlew jpackage コマンドを実行するだけで、最適化されたJREを含むMSIインストーラーが生成される。アプリケーションJARと依存ライブラリは、生成されたイメージ内の app ディレクトリに配置され、ランチャーによってクラスパスとしてロードされる。

## ---

**7\. CI/CD パイプライン設計 (GitHub Actions)**

### **7.1 ワークフローの全体像**

構築するGitHub Actionsワークフローは、以下のステップで構成される。

1. **環境セットアップ**: Java 21 および WiX Toolset v3.11 の準備。  
2. **ビルド & パッケージング**: Gradleによるビルドと jpackage によるインストーラー生成。  
3. **コード署名**: SignPath.io と連携した署名プロセス。  
4. **リリース**: 署名済みインストーラーを GitHub Releases にアップロード。

### **7.2 ワークフロー定義ファイル (.github/workflows/release.yml)**

YAML

name: Build, Sign, and Release Installer

on:  
  push:  
    tags:  
      \- 'v\*' \# v1.0.0 などのタグプッシュで発火

jobs:  
  build-package-sign:  
    runs-on: windows-2019 \# windows-latestでも良いが、環境の安定性のため2019を指定することも有効  
    permissions:  
      contents: write \# リリース作成のために必要

    steps:  
      \# 1\. ソースコードのチェックアウト  
      \- name: Checkout code  
        uses: actions/checkout@v4

      \# 2\. Java 21 のセットアップ  
      \- name: Setup Java 21  
        uses: actions/setup-java@v4  
        with:  
          distribution: 'temurin'  
          java-version: '21'

      \# 3\. WiX Toolset v3.11 のセットアップ  
      \# jpackage (Java 21\) は WiX v3 の candle.exe/light.exe を必要とする。  
      \# Chocolatey を使用して明示的に v3.11.2 をインストールし、PATHを通す。  
      \- name: Setup WiX Toolset v3.11  
        run: |  
           choco install wixtoolset \--version 3.11.2 \-y  
           echo "C:\\Program Files (x86)\\WiX Toolset v3.11\\bin" \>\> $env:GITHUB\_PATH

      \# 4\. Gradle のセットアップ  
      \- name: Setup Gradle  
        uses: gradle/actions/setup-gradle@v3

      \# 5\. プロジェクトのビルドとインストーラー生成  
      \# badass-runtime-plugin の jpackage タスクを実行  
      \- name: Build and Package  
        run:./gradlew jpackage  
        env:  
          org.gradle.caching: true

      \# 6\. 未署名インストーラーのアップロード (SignPath用)  
      \# 生成されたMSIファイルをアーティファクトとして保存し、次のステップでIDを取得する  
      \- name: Upload Unsigned Installer  
        id: upload-unsigned  
        uses: actions/upload-artifact@v4  
        with:  
          name: unsigned-installer  
          path: build/jpackage/\*.msi \# 生成パスはプロジェクト構成に依存するため確認が必要  
          if-no-files-found: error

      \# 7\. SignPath.io による署名プロセス  
      \# アーティファクトをSignPathに送信し、署名完了を待機してダウンロードする  
      \- name: Sign Installer with SignPath  
        id: sign-installer  
        uses: signpath/github-action-submit-signing-request@v2  
        with:  
          api-token: ${{ secrets.SIGNPATH\_API\_TOKEN }}  
          organization-id: ${{ vars.SIGNPATH\_ORG\_ID }} \# GitHub Variablesで管理推奨  
          project-slug: 'ShimejiNeo'  
          signing-policy-slug: 'release-signing'  
          github-artifact-id: ${{ steps.upload-unsigned.outputs.artifact-id }}  
          wait-for-completion: true  
          output-artifact-directory: 'signed-artifacts'

      \# 8\. GitHub Release の作成と署名済みファイルの公開  
      \- name: Create Release  
        uses: softprops/action-gh-release@v1  
        if: startsWith(github.ref, 'refs/tags/')  
        with:  
          files: signed-artifacts/\*.msi  
          draft: true       \# 確認のためドラフトとして作成  
          prerelease: false  
        env:  
          GITHUB\_TOKEN: ${{ secrets.GITHUB\_TOKEN }}

### **7.3 パイプライン解説と注意点**

* **WiX Toolsetのインストール**: ステップ3において choco install wixtoolset \--version 3.11.2 を実行している点が重要である。GitHub Actionsのランナー環境は頻繁に更新されるため、プリインストールのWiXに依存せず、バージョンを固定してインストールすることで jpackage との互換性を確実に担保している 24。  
* **SignPath連携**: 秘密鍵をリポジトリ内に保持せず、アーティファクトのIDを渡して署名依頼を行う方式（Artifact-based Signing）を採用している。これには事前に actions/upload-artifact でファイルをアップロードし、その artifact-id をSignPathアクションに渡す必要がある 29。  
* **パスの確認**: build/jpackage/\*.msi のパスは、Gradleプラグインの installerOutputDir 設定やプロジェクト名によって変化するため、実際のビルドログを確認して正確なパスを指定する必要がある。

## ---

**8\. 結論および今後の展望**

本調査により、Shimeji Neoの配布形態を近代化し、ユーザーフレンドリーなインストーラーを提供するための具体的な道筋が確立された。

1. **Project Panamaの統合**: jpackage の \--java-options を活用することで、プレビュー機能とネイティブアクセス許可をランチャーに不可視化し、ユーザー体験を損なうことなく最新技術を利用可能にする。  
2. **ハイブリッドランタイム**: badass-runtime-plugin を採用し、非モジュールライブラリ（JNA等）とカスタムJREを共存させることで、モジュールシステムの制約を回避しつつ配布サイズを最適化する。  
3. **インフラの互換性**: Java 21 LTSを選択する以上、WiX Toolset v3.11 をCI環境に固定導入することで、jpackage の安定動作を保証する。  
4. **セキュリティ**: SignPath.io を導入することで、OSSプロジェクトとしてのコスト制約を守りながら、信頼性のあるコード署名を実現し、SmartScreen警告の問題を解決する。

このアーキテクチャは、単なるインストーラー生成にとどまらず、将来的にJavaのバージョンが上がり、Project Panamaが正式機能となった際（Java 22以降）にも、Gradle設定のフラグを削除するだけでスムーズに移行可能な、持続可能性の高い設計となっている。  
プロジェクトチームは、本報告書の実装ガイドに基づき、直ちに test1 ブランチにおけるビルドスクリプトの改修およびSignPath.ioへの申請手続きに着手することを推奨する。

#### **引用文献**

1. Gradle Goodness: Enabling Preview Features For Java \- DZone, 12月 27, 2025にアクセス、 [https://dzone.com/articles/gradle-goodness-enabling-preview-features-for-java](https://dzone.com/articles/gradle-goodness-enabling-preview-features-for-java)  
2. How to enable Java 12 preview features with Gradle? \- Stack Overflow, 12月 28, 2025にアクセス、 [https://stackoverflow.com/questions/55433883/how-to-enable-java-12-preview-features-with-gradle](https://stackoverflow.com/questions/55433883/how-to-enable-java-12-preview-features-with-gradle)  
3. Restricted Methods \- Java \- Oracle Help Center, 12月 27, 2025にアクセス、 [https://docs.oracle.com/en/java/javase/25/core/restricted-methods.html](https://docs.oracle.com/en/java/javase/25/core/restricted-methods.html)  
4. Restricted Methods \- Java \- Oracle Help Center, 12月 27, 2025にアクセス、 [https://docs.oracle.com/en/java/javase/22/core/restricted-methods.html](https://docs.oracle.com/en/java/javase/22/core/restricted-methods.html)  
5. The Badass JLink Plugin, 12月 28, 2025にアクセス、 [https://badass-jlink-plugin.beryx.org/releases/2.10.1/](https://badass-jlink-plugin.beryx.org/releases/2.10.1/)  
6. The Badass JLink Plugin, 12月 28, 2025にアクセス、 [https://badass-jlink-plugin.beryx.org/](https://badass-jlink-plugin.beryx.org/)  
7. \[JDK-8319457\] Update jpackage to support WiX v4 and v5 on Windows \- Java Bug System, 12月 28, 2025にアクセス、 [https://bugs.openjdk.org/browse/JDK-8319457?focusedId=14678735\&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel](https://bugs.openjdk.org/browse/JDK-8319457?focusedId=14678735&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel)  
8. Use jpackage with WiX 4 \- java \- Stack Overflow, 12月 28, 2025にアクセス、 [https://stackoverflow.com/questions/74498307/use-jpackage-with-wix-4](https://stackoverflow.com/questions/74498307/use-jpackage-with-wix-4)  
9. Java's Project Panama — The revolution software world needs. | by Muhammad Daniyal Azeemi | Medium, 12月 28, 2025にアクセス、 [https://medium.com/@muhammaddaniyalazeemi/javas-project-panama-the-revolution-software-world-needs-782608cd1d02](https://medium.com/@muhammaddaniyalazeemi/javas-project-panama-the-revolution-software-world-needs-782608cd1d02)  
10. From C to Java Code using Panama \- Mostly nerdless, 12月 28, 2025にアクセス、 [https://mostlynerdless.de/blog/2023/12/11/from-c-to-java-code-using-panama/](https://mostlynerdless.de/blog/2023/12/11/from-c-to-java-code-using-panama/)  
11. How to use jpackage with preview features in Java \- Stack Overflow, 12月 28, 2025にアクセス、 [https://stackoverflow.com/questions/76108915/how-to-use-jpackage-with-preview-features-in-java](https://stackoverflow.com/questions/76108915/how-to-use-jpackage-with-preview-features-in-java)  
12. Why introduce a mandatory \--enable-native-access? Panama simplifies native access while this makes it harder. I don't get it. : r/java \- Reddit, 12月 28, 2025にアクセス、 [https://www.reddit.com/r/java/comments/17cjajl/why\_introduce\_a\_mandatory\_enablenativeaccess/](https://www.reddit.com/r/java/comments/17cjajl/why_introduce_a_mandatory_enablenativeaccess/)  
13. Chapter 2\. Creating a custom Java runtime environment for non-modular applications, 12月 27, 2025にアクセス、 [https://docs.redhat.com/en/documentation/red\_hat\_build\_of\_openjdk/21/html/using\_jlink\_to\_customize\_java\_runtime\_environment/creating-custom-jre](https://docs.redhat.com/en/documentation/red_hat_build_of_openjdk/21/html/using_jlink_to_customize_java_runtime_environment/creating-custom-jre)  
14. Using jlink to Build Java Runtimes for non-Modular Applications | by Simon Ritter \- Medium, 12月 27, 2025にアクセス、 [https://medium.com/azulsystems/using-jlink-to-build-java-runtimes-for-non-modular-applications-9568c5e70ef4](https://medium.com/azulsystems/using-jlink-to-build-java-runtimes-for-non-modular-applications-9568c5e70ef4)  
15. TIL: 'The Badass Runtime Plugin', jpackage & jlink \- create a 'native' installable executable from your JVM-app that isn't huge \- Londogard Blog, 12月 28, 2025にアクセス、 [https://blog.londogard.com/posts/2020-09-03-til-badass-runtime.html](https://blog.londogard.com/posts/2020-09-03-til-badass-runtime.html)  
16. Creating Runtime and Application Images with JLink \- Dev.java, 12月 27, 2025にアクセス、 [https://dev.java/learn/creating-runtime-and-application-images-with-jlink/](https://dev.java/learn/creating-runtime-and-application-images-with-jlink/)  
17. The Badass Runtime Plugin, 12月 28, 2025にアクセス、 [https://badass-runtime-plugin.beryx.org/](https://badass-runtime-plugin.beryx.org/)  
18. The jpackage Command \- Oracle Help Center, 12月 27, 2025にアクセス、 [https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html)  
19. WiX Toolset \- GitHub, 12月 28, 2025にアクセス、 [https://github.com/wixtoolset](https://github.com/wixtoolset)  
20. \[JDK-8319457\] Update jpackage to support WiX v4 and v5 on Windows \- Java Bug System, 12月 28, 2025にアクセス、 [https://bugs.openjdk.org/browse/JDK-8319457](https://bugs.openjdk.org/browse/JDK-8319457)  
21. jpackage from JDK24 does not work with Wix 5 but it should... \#1262 \- GitHub, 12月 28, 2025にアクセス、 [https://github.com/adoptium/adoptium-support/issues/1262](https://github.com/adoptium/adoptium-support/issues/1262)  
22. Build error with \`WixToolset.UI.wixext\` · Issue \#1473 · oleg-shilo/wixsharp \- GitHub, 12月 28, 2025にアクセス、 [https://github.com/oleg-shilo/wixsharp/issues/1473](https://github.com/oleg-shilo/wixsharp/issues/1473)  
23. wix-examples/SETUP.md at main \- GitHub, 12月 28, 2025にアクセス、 [https://github.com/michelou/wix-examples/blob/main/SETUP.md](https://github.com/michelou/wix-examples/blob/main/SETUP.md)  
24. How To Install WiX Toolset 3.11.2 on Windows 11 x64 \- YouTube, 12月 28, 2025にアクセス、 [https://www.youtube.com/watch?v=IXeBEV50Xas](https://www.youtube.com/watch?v=IXeBEV50Xas)  
25. The free Code Signing & Software Integrity solution for Open Source Projects \- SignPath, 12月 28, 2025にアクセス、 [https://signpath.io/solutions/open-source-community](https://signpath.io/solutions/open-source-community)  
26. SignPath Knowledge Base on Code Signing \- Introduction, 12月 28, 2025にアクセス、 [https://signpath.io/knowledge-base/introduction](https://signpath.io/knowledge-base/introduction)  
27. A guide to code signing certificates for the Microsoft app store and a question for the experts, 12月 28, 2025にアクセス、 [https://www.reddit.com/r/electronjs/comments/17sizjf/a\_guide\_to\_code\_signing\_certificates\_for\_the/](https://www.reddit.com/r/electronjs/comments/17sizjf/a_guide_to_code_signing_certificates_for_the/)  
28. SignPath Foundation, 12月 28, 2025にアクセス、 [https://signpath.org/](https://signpath.org/)  
29. GitHub \- SignPath, 12月 28, 2025にアクセス、 [https://docs.signpath.io/trusted-build-systems/github](https://docs.signpath.io/trusted-build-systems/github)  
30. SignPath/github-action-submit-signing-request, 12月 28, 2025にアクセス、 [https://github.com/SignPath/github-action-submit-signing-request](https://github.com/SignPath/github-action-submit-signing-request)