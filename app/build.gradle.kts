plugins {
    id("java")
    id("application")
}

application {
    // Shimeji Neo のエントリポイント（必要に応じて修正）
    mainClass.set("com.group_finity.buna-shimeji.Main")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("org.graalvm.js:js:25.0.0")

    // --- JUnit 5 (Jupiter) ---
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")

    // ✅ テストランナー（JUnit Platform Launcher）
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.3")
}

// Java実行時のJVM引数（Java 25 のプレビュー機能許可）
tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-preview")
}

// ✅ JUnit Platformを利用し、標準出力を常に表示
tasks.test {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true    // ← System.out/System.err を常時表示
        events("passed", "skipped", "failed")  // テスト結果を詳細表示
    }
}
