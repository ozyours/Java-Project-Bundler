# Java Project Bundler — Roadmap

This file tracks the project plan. Committed Plan = discussed and committed (done).
Pending Plan = missing, not yet implemented.

The project started from `SDD.md` (the software design document) and is now
scaffolded as a Maven/JavaFX application with the packaging engine and tests in
place. Every pending item derives from `SDD.md`.

## Committed Plan

- [x] **Maven project scaffold** — Java 17 Maven project (`pom.xml`) with JavaFX 21.0.12, AtlantaFX 2.0.1, org.json, and JUnit wired; `mvnw` wrapper included; builds with `./mvnw verify` (`3b01ee9`).
- [x] **Runnable main window** — `com.jpb.bundler.MainApp` JavaFX entry point applying the AtlantaFX `PrimerLight` theme; launches with `./mvnw javafx:run` (`3b01ee9`).
- [x] **Main window layout** — SDD §4 dashboard: header with Load/Save actions, project form (Name, Source, JDK/JVM, Icon, Output) with Browse buttons, build-engine detection tag, Build Progress log area, and accent Build button; `DashboardView` + `dashboard.css` (`3b01ee9`).
- [x] **Repo housekeeping** — `.gitignore`, `README.md`, `AGENTS.md`, `ROADMAP.md`, `SDD.md`, and a freshly initialized git repository (`3b01ee9`).
- [x] **JRE assembler** — `Bundler.AssembleJvm` runs `jlink` (`--add-modules`, `--strip-debug`, `--compress=2`) into `jvm/`, or copies the full JDK when no `jlink` is present; module list comes from `jdeps` with the JavaFX platform closure merged in, so bundled JavaFX apps start (`4bb0af6`).

## Pending Plan

### Foundation & utilities

- [ ] **Execution & assembly utilities** — ProcessBuilder-based helpers for invoking Maven/Gradle and `jlink`, per `SDD.md` §3.1 (org.json persistence dependency already wired).

### JavaFX UI (`SDD.md` §4)

- [ ] **AtlantaFX styling** — apply `PrimerLight`/`PrimerDark` themes, rounded `TextField`s, `SUCCESS`/`ACCENT` detection `Tag`, outlined Save/Load buttons, and accent large Build button per §4.1.
- [ ] **Browse dialogs** — native directory/file choosers for source project, JDK/JVM, icon, and output paths.
- [ ] **Build log view** — streaming build progress output into the progress area during packaging.

### Project auto-detection (`SDD.md` §5.1)

- [ ] **Maven detection** — detect `pom.xml`, detect `./mvnw` / `mvnw.cmd` wrappers.
- [ ] **Gradle detection** — detect `build.gradle` / `build.gradle.kts`, detect `./gradlew` / `gradlew.bat`.
- [ ] **Unrecognized fallback** — warning tag plus manual target-JAR configuration when neither engine is found.

### Builder project persistence (`SDD.md` §5.2)

- [ ] **`.jpb` JSON model** — serialize `projectName`, `projectSourcePath`, `jvmSourcePath`, `iconPath`, `outputDirectory`, `customJvmFlags`, `heapSize`, `maxHeapSize`, `stackSize`, `maxMetaspaceSize`, `mainJarName`, `buildConfig`.
- [ ] **Save/Load actions** — wire Save and Load Project buttons to read/write `.jpb` files.

### Packaging engine (`SDD.md` §7)

- [ ] **Build invoker** — run `mvn clean package` / `gradle build` via `ProcessBuilder` from the detected engine.
- [ ] **Output directory creation** — create `<Output>/<Project Name>/` with `JARs/`, `jvm/`, `config/`, `assets/` subdirectories.
- [ ] **JAR assembly** — copy built JARs from `target/` or `build/libs/` into `JARs/` (with `lib/` for dependency JARs).
- [ ] **Config & assets** — write default `config/app-config.json`; copy selected icon into `assets/`.

### Native launcher (`SDD.md` §6)

- [ ] **Rust launcher** — single std-only `launcher.rs` compiled with `rustc` into `<Name>.exe` / `<Name>`; GUI-subsystem on Windows (no console), `setsid`-detached on Unix, native error dialogs (`MessageBoxW` / `osascript` / `zenity` / `kdialog`).
- [ ] **Launcher config** — bundler writes `config/launcher.conf` (`main.jar`, `jvm.flags`, classpath + main class); `--check` mode for automated tests.
- [ ] **JVM discovery** — launcher searches local `jvm/` first, then `JAVA_HOME`, then surfaces a "JVM Not Found" dialog per §6.1.
- [ ] **Dashboard rustc path** — Path to Rust Compiler field; empty (grayed `rustc`) means the default rustc from `RUSTC` / `PATH` / `~/.cargo/bin` is resolved at build time.

### Engineering hygiene

- [ ] **Automated tests** — unit tests for detection, `.jpb` serialization, and JVM discovery logic; integration test for the packaging pipeline.
- [ ] **Sample project** — a minimal Maven and Gradle fixture project for verifying builds end-to-end.
- [ ] **README & AGENTS.md** — usage documentation, screenshots, and architecture notes for contributors.
