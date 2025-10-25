# =======================================
# Shimeji Neo GitHub Safe .gitignore Generator (PowerShell)
# =======================================

@'
# ==============================
# Shimeji Neo Safe .gitignore
# ==============================

# Gradle build output
.gradle/
build/
app/build/
app/bin/
out/
target/
!gradle/wrapper/gradle-wrapper.jar

# IDE configs
.project
.classpath
.settings/
app/.project
app/.classpath
app/.settings/
.idea/
.vscode/

# OS junk
.DS_Store
Thumbs.db

# Logs & temp files
*.log
*.tmp
*.bak
*.swp

# Java compiled
*.class
hs_err_pid*
replay_pid*

# Environment files (DO NOT COMMIT)
.env
local.properties
secret.key
api.key
'@ | Out-File -Encoding utf8 .gitignore

Write-Host "✅ .gitignore generated successfully!"
