//! Native launcher for distribution bundles produced by Java Project Bundler.
//!
//! The bundler compiles this single file with `rustc` (std only — no crates, no
//! network) into `<Project Name>.exe` on Windows / `<Project Name>` on Unix.
//! At runtime the executable:
//!
//!   1. reads `config/launcher.conf` next to itself (written by the bundler),
//!   2. resolves a JVM: bundled `jvm/` first, then `JAVA_HOME` (SDD.md §6.1),
//!   3. launches the application with `-jar`, or `-cp` + main class when the
//!      bundle carries dependency JARs in `JARs/lib/` (plus `--module-path`
//!      for JavaFX jars, without which an `Application` main class refuses
//!      to start; an optional `JPB_EXTRA_CLASSPATH` environment variable
//!      appends extra entries to `-cp`, e.g. a `JARs/lib/plugins/*` folder
//!      added after packaging without rebuilding the bundle),
//!   4. when the application fails to start, captures the JVM's actual startup
//!      output and shows it in a native error dialog; debug builds additionally
//!      write the full report to `launcher-error.log` next to the executable
//!      (a message box truncates long text, so the file is the reliable channel
//!      — `javaw` has no console to read errors from — but release bundles
//!      never write to disk).
//!
//! On Windows the executable is a GUI-subsystem app (no console flash); on Unix
//! it detaches from the launching terminal. Run with `--check` to print the
//! resolved JVM path without launching anything (used by automated tests).

#![cfg_attr(windows, windows_subsystem = "windows")]

use std::collections::HashMap;
use std::env;
use std::io::Read;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

#[cfg(windows)]
use std::os::windows::process::CommandExt;

#[cfg(unix)]
use std::os::unix::process::CommandExt;

/// Keys understood from `config/launcher.conf`; must match the constants in
/// the Java `LauncherSupport` class.
const CONF_MAIN_JAR: &str = "main.jar";
const CONF_JVM_FLAGS: &str = "jvm.flags";
const CONF_USE_CLASSPATH: &str = "use.classpath";
const CONF_MAIN_CLASS: &str = "main.class";
// When set, the JVM gets `--module-path <value> --add-modules ALL-MODULE-PATH`
// before -cp/-jar. JavaFX bundles need this: JavaFX refuses to run an
// `Application` main class from the plain classpath ("JavaFX runtime
// components are missing") and only starts once the JavaFX modules are
// resolved from the module path.
const CONF_MODULE_PATH: &str = "jvm.module.path";
// Set by the bundler to `true` for debug builds: only then does the launcher
// write `launcher-error.log` next to the executable on startup failures. The
// native error dialog is always shown, regardless of mode.
const CONF_DEBUG_MODE: &str = "debug.mode";
// Optional runtime extension point: when set, its value is appended to the
// `-cp` value in classpath mode (after the native path separator). Lets an
// operator add JARs to the application without rebuilding the bundle — e.g.
// `JPB_EXTRA_CLASSPATH=JARs/lib/plugins/*` for a plugin folder dropped next
// to the executable, or an absolute path to an external lib. Entries resolve
// against the bundle directory (the JVM's working directory) and support
// Java's `dir/*` wildcard. No effect in `-jar` mode: the JVM ignores `-cp`
// (and the `CLASSPATH` environment variable) there.
const ENV_EXTRA_CLASSPATH: &str = "JPB_EXTRA_CLASSPATH";

fn main() {
    std::process::exit(run());
}

fn run() -> i32 {
    let dir = match env::current_exe() {
        Ok(exe) => exe.parent().map(Path::to_path_buf).unwrap_or_else(|| PathBuf::from(".")),
        Err(_) => {
            show_error("Launcher Error", "Could not determine the launcher location.");
            return 1;
        }
    };

    let config = load_config(&dir.join("config/launcher.conf"));
    let debug = is_debug_mode(&config);
    let java = find_java(&dir);

    // Test-only echo of the resolved JVM (no dialog, no launch).
    if env::args().nth(1).as_deref() == Some("--check") {
        match java {
            Some(path) => {
                let _ = writeln!(std::io::stdout(), "{}", path.display());
                0
            }
            None => {
                let _ = writeln!(std::io::stdout(), "JVM_NOT_FOUND");
                1
            }
        }
    } else {
        match java {
            Some(path) => launch(&dir, &path, &config, debug),
            None => {
                report_error(
                    &dir,
                    debug,
                    "JVM Missing",
                    &format!(
                        "No valid Java Runtime Environment found in\n{}\nor JAVA_HOME.",
                        dir.join("jvm").display()
                    ),
                );
                1
            }
        }
    }
}

/// Launches the application with the settings from `launcher.conf`.
fn launch(dir: &Path, java: &Path, config: &HashMap<String, String>, debug: bool) -> i32 {
    let main_jar = config
        .get(CONF_MAIN_JAR)
        .map(String::as_str)
        .filter(|s| !s.is_empty())
        .unwrap_or("app-main.jar");
    let flags = config.get(CONF_JVM_FLAGS).cloned().unwrap_or_default();
    let use_classpath = config
        .get(CONF_USE_CLASSPATH)
        .map(|s| s == "true")
        .unwrap_or(false);
    let main_class = config.get(CONF_MAIN_CLASS).cloned().unwrap_or_default();

    // Surface a broken bundle before spawning anything: a missing main JAR is
    // the most common reason a launcher "does nothing" when a JVM exists.
    let jars_dir = dir.join("JARs");
    let launch_target;
    if use_classpath && !main_class.is_empty() {
        if !has_jar(&jars_dir) {
            report_error(
                &dir,
                debug,
                "Missing Application JAR",
                &format!("No application JARs found in\n{}", jars_dir.display()),
            );
            return 1;
        }
        let sep = if cfg!(windows) { ';' } else { ':' };
        launch_target = match env::var(ENV_EXTRA_CLASSPATH) {
            Ok(extra) if !extra.trim().is_empty() => {
                format!("JARs/*{sep}JARs/lib/*{sep}{extra}")
            }
            _ => format!("JARs/*{sep}JARs/lib/*"),
        };
    } else {
        if !jars_dir.join(main_jar).is_file() {
            report_error(
                &dir,
                debug,
                "Missing Application JAR",
                &format!(
                    "Main JAR not found:\n{main_jar}\n\nExpected at:\n{}",
                    jars_dir.join(main_jar).display()
                ),
            );
            return 1;
        }
        launch_target = format!("JARs/{main_jar}");
    }

    // Build the argument list once so the error dialog can show the exact
    // command (with -cp/-jar and the main class) and the failed command can be
    // re-run with captured output.
    let module_path = config.get(CONF_MODULE_PATH).cloned().unwrap_or_default();
    let has_module_path = !module_path.trim().is_empty();

    let mut args: Vec<String> = Vec::new();
    if !flags.trim().is_empty() {
        args.extend(flags.split_whitespace().map(str::to_string));
    }
    let mut command_line = String::from("java");
    if !flags.trim().is_empty() {
        command_line.push(' ');
        command_line.push_str(&flags);
    }
    if has_module_path {
        args.push("--module-path".to_string());
        args.push(module_path.clone());
        args.push("--add-modules".to_string());
        args.push("ALL-MODULE-PATH".to_string());
        command_line.push_str(" --module-path ");
        command_line.push_str(&module_path);
        command_line.push_str(" --add-modules ALL-MODULE-PATH");
    }
    if use_classpath && !main_class.is_empty() {
        args.push("-cp".to_string());
        args.push(launch_target.clone());
        args.push(main_class.clone());
        command_line.push_str(&format!(" -cp {launch_target} {main_class}"));
    } else {
        args.push("-jar".to_string());
        args.push(launch_target.clone());
        command_line.push_str(&format!(" -jar {launch_target}"));
    }

    let mut cmd = Command::new(java);
    cmd.current_dir(dir);
    cmd.args(&args);

    #[cfg(windows)]
    {
        // Keep a console out of the child (javaw is windowless anyway; this
        // covers the JAVA_HOME fallback resolving to java.exe).
        cmd.creation_flags(0x0800_0000); // CREATE_NO_WINDOW
    }

    #[cfg(unix)]
    {
        // Detach from the launching terminal, like the old `nohup ... &` script.
        cmd.stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::null());
        unsafe {
            cmd.pre_exec(|| {
                extern "C" {
                    fn setsid() -> i32;
                }
                setsid();
                Ok(())
            });
        }
    }

    match cmd.spawn() {
        Ok(mut child) => {
            // Give the JVM a short grace period to prove it can start. A quick
            // non-zero exit means startup failed (bad flags, missing modules,
            // corrupt JAR) — javaw/java.exe show no console, so surface the
            // JVM's own error message instead of a bare exit code.
            for _ in 0..40 {
                match child.try_wait() {
                    Ok(Some(status)) => {
                        if status.success() {
                            return 0;
                        }
                        let exit_code = status.code().unwrap_or(-1);
                        // Re-run the same command once with output piped, so the
                        // real startup error (missing class, missing module,
                        // invalid flag, ...) can be shown and logged.
                        let startup = capture_output(java, &args, dir);
                        let mut message = format!(
                            "The application exited with an error (exit code {exit_code}).\n\n\
                             JVM: {}\n\
                             Command: {command_line}\n\n",
                            java.display()
                        );
                        match &startup {
                            Some(text) => {
                                message.push_str("Startup output:\n");
                                message.push_str(text);
                            }
                            None => message.push_str(
                                "No startup output could be captured.\n\n\
                                 If this repeats, check the application's startup logs.",
                            ),
                        }
                        report_error(&dir, debug, "Application Error", &message);
                        return 1;
                    }
                    Ok(None) => {}
                    Err(_) => return 0, // handle lost — assume it launched
                }
                std::thread::sleep(std::time::Duration::from_millis(100));
            }
            0
        }
        Err(e) => {
            report_error(&dir, debug, "Launch Failed", &format!("Failed to launch the application:\n{e}"));
            1
        }
    }
}

/// Re-runs the command once with stdout/stderr captured, so a quick startup
/// failure can be reported with the JVM's actual error message. Returns the
/// captured output (trimmed, capped), or `None` when nothing was captured.
fn capture_output(java: &Path, args: &[String], dir: &Path) -> Option<String> {
    let mut cmd = Command::new(java);
    cmd.current_dir(dir);
    cmd.args(args);
    cmd.stdout(Stdio::piped());
    cmd.stderr(Stdio::piped());
    #[cfg(windows)]
    {
        cmd.creation_flags(0x0800_0000); // CREATE_NO_WINDOW
    }

    let mut child = cmd.spawn().ok()?;
    // Wait up to 15s; a failing JVM normally exits in about a second, this is
    // only a safety cap for the rare case the re-run does not reproduce.
    let mut exited = false;
    for _ in 0..150 {
        match child.try_wait() {
            Ok(Some(_)) => {
                exited = true;
                break;
            }
            Ok(None) => {}
            Err(_) => return None,
        }
        std::thread::sleep(std::time::Duration::from_millis(100));
    }
    if !exited {
        let _ = child.kill();
        let _ = child.wait();
        return None;
    }

    let mut out = Vec::new();
    let mut budget = 8192usize; // cap the dialog/log to 8 KB of startup output
    if let Some(mut stdout) = child.stdout.take() {
        budget = drain(&mut stdout, &mut out, budget);
    }
    if let Some(mut stderr) = child.stderr.take() {
        let _ = drain(&mut stderr, &mut out, budget);
    }

    let text = String::from_utf8_lossy(&out).trim().to_string();
    if text.is_empty() {
        None
    } else {
        Some(text)
    }
}

/// Reads up to `budget` bytes from `reader` into `out`; returns the remaining
/// budget. Both child pipes are drained after the child exited, so the reads
/// hit EOF instead of blocking.
fn drain(reader: &mut impl Read, out: &mut Vec<u8>, mut budget: usize) -> usize {
    let mut buf = [0u8; 1024];
    while budget > 0 {
        match reader.read(&mut buf) {
            Ok(0) => break,
            Ok(n) => {
                let take = n.min(budget);
                out.extend_from_slice(&buf[..take]);
                budget -= take;
            }
            Err(_) => break,
        }
    }
    budget
}

/// True when the directory contains at least one `.jar` file.
fn has_jar(dir: &Path) -> bool {
    match std::fs::read_dir(dir) {
        Ok(entries) => entries
            .filter_map(Result::ok)
            .any(|e| e.file_name().to_str().map(|n| n.ends_with(".jar")).unwrap_or(false)),
        Err(_) => false,
    }
}

/// Parses the simple `key=value` config file written by the bundler. Unknown or
/// missing keys fall back to defaults; blank lines and `#` comments are skipped.
fn load_config(path: &Path) -> HashMap<String, String> {
    let mut map = HashMap::new();
    let content = match std::fs::read_to_string(path) {
        Ok(text) => text,
        Err(_) => return map,
    };
    for line in content.lines() {
        let line = line.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        if let Some((key, value)) = line.split_once('=') {
            map.insert(key.trim().to_string(), value.trim().to_string());
        }
    }
    map
}

/// True when the bundle was built in debug mode (`debug.mode = true` in
/// launcher.conf) — the only mode that writes `launcher-error.log`.
fn is_debug_mode(config: &HashMap<String, String>) -> bool {
    config.get(CONF_DEBUG_MODE).map(|s| s == "true").unwrap_or(false)
}

/// JVM discovery: bundled `jvm/` first, then `JAVA_HOME` (SDD.md §6.1).
fn find_java(dir: &Path) -> Option<PathBuf> {
    let mut candidates = Vec::new();
    if cfg!(windows) {
        candidates.push(dir.join("jvm/bin/javaw.exe"));
        candidates.push(dir.join("jvm/bin/java.exe"));
    } else {
        candidates.push(dir.join("jvm/bin/java"));
    }
    if let Ok(home) = env::var("JAVA_HOME") {
        if home.trim().is_empty() {
            return candidates.into_iter().find(|c| usable(c));
        }
        let home = PathBuf::from(home);
        if cfg!(windows) {
            candidates.push(home.join("bin/javaw.exe"));
            candidates.push(home.join("bin/java.exe"));
        } else {
            candidates.push(home.join("bin/java"));
        }
    }
    candidates.into_iter().find(|c| usable(c))
}

/// A candidate JVM is usable when it exists and — on Unix — is executable
/// (mirrors the old launcher's `[ -x ]` check).
fn usable(path: &Path) -> bool {
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        path.is_file()
            && path
                .metadata()
                .map(|m| m.permissions().mode() & 0o111 != 0)
                .unwrap_or(false)
    }
    #[cfg(not(unix))]
    {
        path.is_file()
    }
}

/// Shows the native error dialog (always) and, for debug builds, writes the
/// report to `launcher-error.log` next to the executable. The file is the
/// reliable channel for long startup output (a message box truncates; `javaw`
/// has no console) — release bundles never write to disk.
fn report_error(dir: &Path, debug: bool, title: &str, message: &str) {
    if debug {
        let _ = write_log(dir, &format!("{title}\n{message}\n"));
    }
    show_error(title, message);
}

fn write_log(dir: &Path, contents: &str) -> std::io::Result<()> {
    let path = dir.join("launcher-error.log");
    let mut file = std::fs::File::create(&path)?;
    file.write_all(contents.as_bytes())
}

/// Shows a native error dialog; falls back to stderr when no dialog tool exists.
fn show_error(title: &str, message: &str) {
    #[cfg(target_os = "windows")]
    {
        use std::ffi::OsStr;
        use std::os::windows::ffi::OsStrExt;
        use std::ptr::null_mut;

        #[link(name = "user32")]
        extern "system" {
            fn MessageBoxW(
                hwnd: *mut std::ffi::c_void,
                text: *const u16,
                caption: *const u16,
                kind: u32,
            ) -> i32;
        }

        let wide = |s: &str| -> Vec<u16> {
            OsStr::new(s).encode_wide().chain(std::iter::once(0)).collect()
        };
        let text = wide(message);
        let title = wide(title);
        unsafe {
            MessageBoxW(null_mut(), text.as_ptr(), title.as_ptr(), 0x10 /* MB_ICONERROR */);
        }
    }

    #[cfg(target_os = "macos")]
    {
        let script = format!(
            "display dialog \"{}\" with title \"{}\" buttons {{\"OK\"}} default button 1 with icon stop",
            message.replace('"', "\\\""),
            title.replace('"', "\\\"")
        );
        let _ = Command::new("osascript").arg("-e").arg(&script).status();
    }

    #[cfg(all(unix, not(target_os = "macos")))]
    {
        let attempts: [(&str, &[&str]); 4] = [
            ("zenity", &["--error", "--title", title, "--text", message]),
            ("kdialog", &["--error", message]),
            ("notify-send", &["--urgency=critical", title, message]),
            ("xmessage", &["-center", message]),
        ];
        for (tool, args) in attempts {
            if Command::new(tool)
                .args(args)
                .status()
                .map(|s| s.success())
                .unwrap_or(false)
            {
                return;
            }
        }
        let _ = writeln!(std::io::stderr(), "{title}: {message}");
    }

    #[cfg(not(any(target_os = "windows", target_os = "macos", unix)))]
    {
        let _ = writeln!(std::io::stderr(), "{title}: {message}");
    }
}
