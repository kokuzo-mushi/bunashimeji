# Shimeji Neo (Bunashimeji)

![Java](https://img.shields.io/badge/Java-21%2B-orange)
![Gradle](https://img.shields.io/badge/Build-Gradle-02303A)

A modernized desktop mascot application built with Java 21 and Project Panama.
This project is a **Gradle** project using Kotlin DSL.

## Project Structure

This repository follows a standard Gradle project structure.

- **Build System**: Gradle 8.x+ (Kotlin DSL)
- **JDK Version**: Java 21 (LTS) with `--enable-preview`
- **Main Module**: `app/`

## How to Build

Please use the included Gradle Wrapper (`gradlew`).

### Windows
```powershell
.\gradlew.bat build
```

### Linux / macOS
```bash
./gradlew build
```

## Key Configuration Files
- `settings.gradle.kts`: Project inclusion settings.
- `app/build.gradle.kts`: Main build script.