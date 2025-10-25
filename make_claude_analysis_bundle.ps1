# ============================
# Shimeji Neo - Claude 3.5 Sonnet 用 解析バンドル作成スクリプト
# ============================

# 作業ディレクトリ（プロジェクトルートから実行を想定）
$projectRoot = "C:\IDE\eclipse\buna-shimeji\buna-shimeji\app\src\main\java\com\group_finity\mascot"
$testRoot = "C:\IDE\eclipse\buna-shimeji\buna-shimeji\app\src\test\java\com\group_finity\mascot"
$outputDir = "C:\analysis_bundle"

# 出力ディレクトリ作成
if (-Not (Test-Path $outputDir)) {
    New-Item -Path $outputDir -ItemType Directory | Out-Null
}

# 対象ファイル一覧（相対パス）
$files = @(
    "$projectRoot\trigger\expr\parser\ExpressionParser.java",
    "$projectRoot\trigger\expr\node\ExpressionNode.java",
    "$projectRoot\trigger\expr\node\UnaryExpressionNode.java",
    "$projectRoot\trigger\expr\node\BinaryExpressionNode.java",
    "$projectRoot\trigger\expr\node\LiteralNode.java",
    "$projectRoot\trigger\expr\node\VariableNode.java",
    "$projectRoot\trigger\expr\eval\EvaluationContext.java",
    "$projectRoot\trigger\expr\type\DefaultTypeCoercion.java",
    "$testRoot\trigger\expr\ExprTriggerAdvancedTest.java",
    "$testRoot\trigger\expr\type\DefaultTypeCoercionTest.java",
    "$testRoot\trigger\expr\ExpressionEngineTypeTest.java",
    "C:\analysis_logs\testlog.txt"  # ← 直近のGradle出力をここに保存しておく
)

# コピー処理
foreach ($file in $files) {
    if (Test-Path $file) {
        Copy-Item -Path $file -Destination $outputDir -Force
        Write-Host "✔ Copied: $file"
    } else {
        Write-Host "⚠ Missing: $file"
    }
}

# ZIP化
$zipPath = "$outputDir\ShimejiNeo_Claude_AnalysisBundle.zip"
if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
Compress-Archive -Path "$outputDir\*" -DestinationPath $zipPath -Force

Write-Host ""
Write-Host "🎉 完了: $zipPath を Claude 3.5 Sonnet に提出してください。"
Write-Host "推奨プロンプト: make_claude_analysis_bundle_prompt.txt を参照"
