# セキュリティ実装ガイド (Security Implementation Guide)

技術監査で指摘された脆弱性（XXE, Zip Slip）への対策コード例を以下に示す。
開発者はこれらのパターンを遵守して実装を行うこと。

## 1. XXE対策 (XML External Entity Prevention)

XMLパーサーを使用する際は、必ず以下の設定を適用して外部エンティティを無効化する。

```java
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class XmlSecurity {
    public static DocumentBuilderFactory createSecureFactory() throws ParserConfigurationException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        
        // DTD宣言自体を禁止する (最も安全な設定)
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        
        // 外部エンティティの解決を禁止
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        
        // 外部DTDのロードを禁止
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        
        return dbf;
    }
}
```

## 2. Zip Slip対策 (Path Traversal Prevention)

ZIPファイルを展開する際は、展開先パスが正規化されたディレクトリ内に収まっているか検証する。

```java
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;

public class ZipSecurity {
    public static void validateZipEntry(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());
        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        // 展開先ファイルのパスが、意図したディレクトリのパスで始まっているか確認
        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Zip Slip vulnerability detected: " + zipEntry.getName());
        }
    }
}
```

## 3. スクリプト実行のサンドボックス化 (Script Sandboxing)

将来的に導入予定のスクリプト機能（Lua/Kotlin）において、任意のJavaクラスへのアクセスを許可することはRCE（リモートコード実行）脆弱性に直結する。
以下の原則に従い、サンドボックス環境を構築すること。

### 3.1. クラスアクセスの制限 (Class Filtering)

スクリプトエンジンがJavaクラスをロードする際、許可されたパッケージ（例: `com.group_finity.mascot.api.*`）以外のアクセスを遮断するフィルタを実装する。

```java
// 概念実装例 (ScriptEngine使用時)
// 注: 具体的な実装は採用するスクリプトエンジン(Luaj, Kotlin Scripting)の仕様に依存する。

public class MascotScriptSandbox {
    private static final List<String> ALLOWED_PACKAGES = List.of(
        "com.group_finity.mascot.api",
        "java.lang.Math" // 安全な標準ライブラリのみ許可
    );

    // エンジンのクラスローダーフック等で ALLOWED_PACKAGES 以外をブロックするロジックを実装すること
}
```
```