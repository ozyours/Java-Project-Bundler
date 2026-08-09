# Software Design Document: Java Project Bundler

## 1. Executive Summary & Objective

The **Java Project Bundler** is a desktop utility application designed to package Java projects (Maven or Gradle) into a self-contained, customizable distribution bundle. The application outputs a standardized directory structure containing the application JARs, a bundled JVM, runtime configurations, assets (such as application icons), and platform-specific launcher scripts.

The tool supports persistent builder project files (`.jpb`) to save and load build configurations, as well as silent launcher scripts configured to locate runtime environments without displaying console windows unless an error occurs.

---

## 2. Target File Structure Specification

The tool outputs a structured folder named after the user-specified **Project Name**:

```text
<Project Name>/
├── JARs/
│   ├── app-main.jar
│   └── lib/                      (Optional dependency JARs)
├── jvm/
│   ├── bin/                      (Contains java/javaw executables)
│   ├── conf/
│   └── lib/
├── config/
│   ├── app-config.json           (Runtime & application configuration)
│   └── launcher.conf             (Native launcher runtime settings)
├── assets/
│   └── app-icon.ico / .png       (Selected application icon)
├── <Project Name>.exe            (Windows native launcher — Rust)
└── <Project Name>                (Linux/macOS native launcher — Rust)

```

---

## 3. System Architecture & Tech Stack

### 3.1 Tech Stack

* **Language:** Java 17+
* **GUI Framework:** JavaFX 21+
* **UI Theme:** AtlantaFX (`PrimerLight` / `PrimerDark`)
* **Persistence:** org.json (JSON-java) via the `ISerializable` interface (JSON serialization for `.jpb` builder project files)
* **Execution & Assembly Engine:** ProcessBuilder (Maven/Gradle invocations, `jlink` execution, file IO operations)

### 3.2 Component Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    JavaFX UI (AtlantaFX)                    │
│  [Project Name] [Project Path] [JVM Path] [Icon] [Config]   │
└──────────────┬──────────────────────────────┬───────────────┘
               │                              │
               ▼                              ▼
┌──────────────────────────────┐┌─────────────────────────────┐
│ Builder Project State Manager││    Project Auto-Detector    │
│   (Save / Load .jpb Files)   ││    (Maven / Gradle Engine)  │
└──────────────┬───────────────┘└──────────────┬──────────────┘
               │                              │
               └──────────────┬───────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      Packaging Engine                       │
│  ┌──────────────────────┐ ┌───────────────┐ ┌─────────────┐ │
│  │ Maven/Gradle Invoker │ │ JRE Assembler │ │Launcher Gen │ │
│  └──────────────────────┘ └───────────────┘ └─────────────┘ │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                  Output Bundle Generator                    │
│          [ <Project Name> / JARs / jvm / run / ... ]        │
└─────────────────────────────────────────────────────────────┘

```

---

## 4. UI/UX Specification (AtlantaFX)

The layout features controls for path selection, builder project state management, and build execution logging.

```
+-----------------------------------------------------------------------+
|  Java Project Bundler                       [ Load Project ] [ Save ] |
+-----------------------------------------------------------------------+
|                                                                       |
|  Project Name                                                         |
|  [ MyCoolApp                                                        ] |
|                                                                       |
|  Source Project Path                                                  |
|  [ /path/to/source-project                          ] [ Browse... ]   |
|  (i) Detected: Gradle Project (build.gradle.kts found)                |
|                                                                       |
|  JDK / JVM Path (empty = default from environment)                    |
|  [ java                                            ] [ Browse... ]   |
|                                                                       |
|  Rust Compiler Path (empty = default from environment)                |
|  [ rustc                                           ] [ Browse... ]   |
|                                                                       |
|  Application Icon Path                                                |
|  [ /path/to/icon.ico                                ] [ Browse... ]   |
|                                                                       |
|  Output Parent Directory                                              |
|  [ /path/to/dist                                    ] [ Browse... ]   |
|                                                                       |
|  [--------------------------- Build Progress -----------------------] |
|                                                                       |
|  [ Build Distribution Package ]                                       |
|                                                                       |
+-----------------------------------------------------------------------+

```

### 4.1 UI Controls & Styling

| Control Element | AtlantaFX Style / Class | Function |
| --- | --- | --- |
| **Project Name Input** | `TextField` (`Styles.ROUNDED`) | Sets the root output directory name. |
| **Source Path Input** | `TextField` (`Styles.ROUNDED`) | Path to the Maven or Gradle source project. |
| **JVM Path Input** | `TextField` (`Styles.ROUNDED`) | Path to the JDK or JRE to bundle. |
| **Icon Path Input** | `TextField` (`Styles.ROUNDED`) | Path to `.ico`, `.png`, or `.icns` file. |
| **Detection Tag** | `Tag` (`Styles.SUCCESS` / `Styles.ACCENT`) | Displays `MAVEN`, `GRADLE`, or `UNRECOGNIZED`. |
| **Save/Load Buttons** | `Button` (`Styles.BUTTON_OUTLINED`) | Saves or loads the current setup as a `.jpb` file. |
| **Build Button** | `Button` (`Styles.ACCENT`, `Styles.LARGE`) | Triggers the build and packaging pipeline. |

---

## 5. System Logic & Data Persistence

### 5.1 Project Auto-Detection Engine

When the **Source Project Path** field updates, the tool checks the directory:

1. Search for `pom.xml` $\rightarrow$ Set to **Maven**. Check for `./mvnw` or `mvnw.cmd` wrappers.
2. Search for `build.gradle` or `build.gradle.kts` $\rightarrow$ Set to **Gradle**. Check for `./gradlew` or `gradlew.bat` wrappers.
3. Neither found $\rightarrow$ Display warning tag, require manually specifying target JAR build outputs.

### 5.2 Builder Project Persistence (`.jpb` File Format)

The tool supports saving and loading builder states. All configuration attributes are stored as file paths or plain values in a JSON structure:

```json
{
  "projectName": "MyCoolApp",
  "projectSourcePath": "/path/to/source-project",
  "jvmSourcePath": "/path/to/jdk-21",
  "rustcPath": "/path/to/rustc",
  "iconPath": "/path/to/icon.ico",
  "outputDirectory": "/path/to/dist",
  "customJvmFlags": "-Dfile.encoding=UTF-8",
  "heapSize": "512m",
  "maxHeapSize": "2g",
  "stackSize": "1m",
  "maxMetaspaceSize": "512m",
  "mainJarName": "MyProject",
  "buildConfig": "release"
}

```

---

## 6. Native Launcher Specification & JVM Discovery

Each bundle contains a single **native launcher executable** written in Rust and compiled with `rustc` during packaging (`<Project Name>.exe` on Windows, `<Project Name>` on Unix). It replaces the former `run.vbs` / `run.cmd` / `run.sh` scripts: there is no console window, no terminal flash, and no shell dependency at the end user's side.

The launcher source (`launcher.rs`) ships inside the application JAR and is compiled with `rustc --edition 2021 -O` into the bundle root. It reads its runtime settings from `config/launcher.conf` (written by the bundler on every build). The executable must execute silently without revealing a terminal or command prompt window during standard operation. A native error dialog is presented when JVM discovery fails, the main JAR is missing, or the application exits with an error shortly after launch — always, in every build. Debug builds additionally write the report to `launcher-error.log` next to the executable (the reliable channel for long startup output, since a message box truncates and `javaw` has no console); release builds never write to disk.

### 6.1 JVM Discovery Algorithm

```
                 [ User Launches Application ]
                              │
                              ▼
               Does `./jvm/bin/` exist locally?
                      ├── YES ──► Launch via local `./jvm` (Silent)
                      └── NO
                           │
                           ▼
             Is `JAVA_HOME` set & valid?
                      ├── YES ──► Launch via `$JAVA_HOME` (Silent)
                      └── NO  ──► Display "JVM Not Found" Dialog / Terminal Error

```

---

### 6.2 Platform Implementations

#### A. Windows Execution Strategy

The launcher is compiled as a GUI-subsystem executable (`#![cfg_attr(windows, windows_subsystem = "windows")]`), so no console window is created on double-click. It resolves `jvm/bin/javaw.exe` first, then `JAVA_HOME/bin/javaw.exe` (with `java.exe` fallbacks for copied JREs), and spawns the JVM with `CREATE_NO_WINDOW`. JVM errors surface through the native `MessageBoxW` dialog.

#### B. Linux & macOS Execution Strategy

On Unix the launcher starts the JVM detached from the launching terminal: stdin/stdout/stderr are redirected to `/dev/null` and `setsid()` puts the child in its own session, mirroring the former `nohup ... &` behaviour. JVM errors are shown via `osascript` (macOS) or `zenity` / `kdialog` / `notify-send` / `xmessage` (Linux), falling back to stderr.

#### C. Launcher Configuration (`config/launcher.conf`)

The bundler rewrites this file on every build; the launcher reads it once at startup. Unknown or missing keys fall back to defaults (`main.jar` defaults to `app-main.jar`).

```text
main.jar = app-main.jar      # entry JAR inside JARs/
jvm.flags = -Xmx2g           # extra JVM flags
use.classpath = false        # true → -cp JARs/*:JARs/lib/* + main.class
main.class = com.example.Main
debug.mode = false           # true → write launcher-error.log on startup failures (debug builds)
jvm.module.path = JARs/lib/javafx-base-26.0.2-win.jar;JARs/lib/javafx-graphics-26.0.2-win.jar  # when JavaFX is bundled: --module-path + --add-modules ALL-MODULE-PATH
```

The launcher launches with `-jar`, or with `-cp` + main class when the bundle carries dependency JARs. When the bundler detects JavaFX jars in `JARs/lib/` it writes `jvm.module.path` (the relative paths of the modular JavaFX jars); the launcher then adds `--module-path <value> --add-modules ALL-MODULE-PATH` before `-cp`, which is required for JavaFX apps whose main class extends `javafx.application.Application` — launched from the plain classpath those fail with *"JavaFX runtime components are missing"*.

The launcher also accepts a `--check` argument that prints the resolved JVM path (or `JVM_NOT_FOUND`) and exits without launching — used by automated tests.

At runtime the launcher additionally reads the optional `JPB_EXTRA_CLASSPATH` environment variable: in classpath mode its value is appended to the `-cp` list (after the platform path separator), so extra JARs can be added after packaging without rebuilding the bundle — e.g. `JPB_EXTRA_CLASSPATH=JARs/lib/plugins/*` for a plugin folder dropped next to the executable. Entries resolve relative to the bundle directory (the JVM's working directory) and support Java's `dir/*` wildcard. `-jar` mode ignores it: the JVM ignores `-cp` and the `CLASSPATH` environment variable there.

---

## 7. Packaging Workflow Steps

When the user clicks **Build Distribution Package**:

1. **Save Configuration:** Automatically update or create the project's `.jpb` file.
2. **Execute Build:** Run Maven (`mvn clean package`) or Gradle (`gradle build`) via `ProcessBuilder`.

The **Build Configuration** (default `release`) is passed as a Maven profile (`-Prelease`) or Gradle property (`-Prelease`) when the project declares one, and additionally controls compiler debug info: `release` builds with `-g:none` (no line numbers, source or local-variable info at all — `-Dmaven.compiler.debug=true -Dmaven.compiler.debuglevel=none` for Maven, `-Pjpb.debug=false` for Gradle), while `debug` builds compile with full debug info (`-g`, i.e. `-Dmaven.compiler.debuglevel=lines,vars,source` / `-Pjpb.debug=true`).
3. **Structure Output Directory:**
* Create `<Output Directory>/<Project Name>/`
* Create `JARs/`, `jvm/`, `config/`, and `assets/` subdirectories.


4. **Copy & Assemble Assets:**
* Move generated `.jar` file(s) from `target/` or `build/libs/` into `JARs/`.
* Collect the project's runtime dependency JARs into `JARs/lib/` (`mvn dependency:copy-dependencies` for Maven; Gradle bundles rely on a fat jar in `build/libs`).
* Run `jlink` using the configured JDK path (or the default JDK resolved from the environment when the field is empty) to generate a runtime inside `jvm/` (or copy the specified JRE folder directly). jdeps analyzes the main JAR *and* the dependency JARs so the runtime contains every platform module the classpath libraries need (e.g. `java.desktop`, `jdk.unsupported` for JavaFX); only JDK modules (`java.*`/`jdk.*`) from the analysis are passed to `jlink`. When the bundle carries JavaFX jars, the launcher is configured with a module path (`jvm.module.path`) so the app starts with `--module-path` + `--add-modules ALL-MODULE-PATH` — without it a JavaFX app whose main class extends `Application` dies at launch with "JavaFX runtime components are missing".
* Write default `config/app-config.json`.
* Copy the selected icon file into `assets/`.
* Compile the native Rust launcher (`<Project Name>.exe` / `<Project Name>`) and write `config/launcher.conf` configured for the current project layout.