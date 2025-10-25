@echo off
:: ====================================================
:: Shimeji Neo - .gitignore 自動生成スクリプト
:: 対応環境: Windows + Eclipse + Gradle + Java
:: ====================================================

echo Generating .gitignore for Shimeji Neo project...
echo -------------------------------------------------

set FILE=.gitignore

:: 既存ファイルがある場合はバックアップ
if exist %FILE% (
    echo Existing .gitignore found, creating backup...
    copy %FILE% %FILE%.bak >nul
)

(
echo # ===============================
echo # Shimeji Neo Project .gitignore
echo # Generated automatically
echo # ===============================
echo.
echo # Build artifacts
echo /build/
echo /app/build/
echo /out/
echo /bin/
echo /target/
echo /dist/
echo.
echo # Gradle
echo /.gradle/
echo /app/.gradle/
echo gradle-app.setting
echo gradle.properties
echo !gradle-wrapper.jar
echo.
echo # Eclipse files
echo /.settings/
echo /.classpath
echo /.project
echo /app/.classpath
echo /app/.project
echo.
echo # IntelliJ IDEA (もし使う場合)
echo /.idea/
echo *.iml
echo.
echo # Logs / Temporary files
echo *.log
echo *.tmp
echo *.bak
echo *.swp
echo /temp/
echo /tmp/
echo.
echo # OS files
echo Thumbs.db
echo Desktop.ini
echo .DS_Store
echo.
echo # Java / Misc
echo *.class
echo *.jar
echo *.war
echo *.ear
echo /META-INF/
echo /MANIFEST.MF
echo.
echo # Environment or private files
echo .env
echo local.properties
echo /app/local.properties
echo.
echo # Ignore all credential or API keys
echo token.txt
echo api_keys.json
echo.
echo # ===============================
echo # End of generated .gitignore
echo # ===============================
) > %FILE%

echo ✅ .gitignore has been generated successfully!
pause
