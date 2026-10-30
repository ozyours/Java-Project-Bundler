package com.jpb.bundler.Engine.Build;

import com.jpb.bundler.Engine.Project.JpbProject;
import com.jpb.bundler.Engine.Project.JpbProjectStore;
import com.jpb.bundler.Engine.Toolchain.JdkLocator;
import com.jpb.bundler.Engine.Toolchain.LauncherSupport;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Packaging engine (SDD.md §7): runs the Maven/Gradle build and assembles the
 * self-contained distribution bundle under {@code <project folder>/output}:
 *
 * <pre>{@code
 * output/
 * ├── JARs/          application JAR (plus lib/ for dependency JARs)
 * ├── jvm/           jlink-generated runtime (or copied JRE/JDK)
 * ├── config/        app-config.json + launcher.conf
 * ├── assets/        icon.ico (when present in the project folder)
 * └── <Name>.exe     native launcher compiled from launcher.rs (Rust; <Name> on Unix)
 * }</pre>
 *
 * <p>Long-running; callers run it off the JavaFX application thread and feed the
 * log consumer on the FX thread (see {@code MainApp}).</p>
 */
public final class Bundler
{
	/**
	 * Fallback jlink modules when jdeps cannot analyze the main JAR.
	 * Cover desktop, scripting, XML, and crypto needs so a typical
	 * app still starts instead of failing with missing classes.
	 */
	private static final String FallbackModules = "java.base,java.logging,java.desktop,java.naming,java.scripting,java.xml,java.datatransfer,java.prefs,jdk.unsupported,jdk.crypto.ec";

	/**
	 * Extra platform modules required transitively by modular JavaFX jars.
	 * Add them explicitly because the jdeps classpath scan misses these
	 * transitive needs, leaving a runtime that fails at JavaFX startup.
	 * Cover the full JavaFX closure (javafx.graphics needs java.desktop and
	 * jdk.unsupported, fxml needs java.scripting and java.xml) so a minimal
	 * jdeps result still yields a runnable image.
	 */
	private static final String JavaFxExtraModules = "java.base,java.logging,java.desktop,java.naming,java.scripting,java.xml,java.datatransfer,java.prefs,jdk.unsupported,jdk.crypto.ec";

	private Bundler()
	{
	}

	/**
	 * Builds the source project and assembles the distribution bundle.
	 *
	 * @param _Project project to package (saved to disk first, SDD §7 step 1)
	 * @param _Log     receives progress and engine output lines (worker thread)
	 * @throws IOException          on any build/assembly failure
	 * @throws InterruptedException if the build is interrupted
	 */
	public static void Build(JpbProject _Project, Consumer<String> _Log) throws IOException, InterruptedException
	{
		JpbProjectStore.Save(_Project);

		String __SourcePath = _Project.GetProjectSourcePath() == null ? "" : _Project.GetProjectSourcePath().trim();
		if (__SourcePath.isEmpty())
		{
			throw new IOException("Source project path is empty — set it on the dashboard before building.");
		}
		Path __Source = Path.of(__SourcePath).toAbsolutePath().normalize();
		if (!Files.isDirectory(__Source))
		{
			throw new IOException("Source project path is not a directory: " + __Source);
		}
		DetectionResult __Detection = ProjectDetector.Detect(__Source.toString());
		if (__Detection.Tool() == BuildTool.Unrecognized)
		{
			throw new IOException("No Maven or Gradle project found in: " + __Source);
		}

		_Log.accept("Detected: " + __Detection.Tool());
		RunEngineBuild(_Project, __Source, __Detection, _Log);

		Path __Bundle = _Project.GetOutputDirectory();
		if (_Project.IsDeleteOutputOnBuild())
		{
			DeleteTree(__Bundle);
			_Log.accept("Deleted existing output folder: " + __Bundle);
		}
		else
		{
			_Log.accept("Keeping existing output folder: " + __Bundle + " (delete-on-build unchecked)");
			// jlink refuses to write into an existing non-empty directory, so the
			// bundled runtime is regenerated even when the rest of the folder is
			// kept; everything else is overwritten in place during assembly.
			DeleteTree(__Bundle.resolve("jvm"));
		}
		Files.createDirectories(__Bundle);
		_Log.accept("Bundle directory: " + __Bundle);

		Path __JarsDir = __Bundle.resolve("JARs");
		Path __LibDir = __JarsDir.resolve("lib");
		Files.createDirectories(__LibDir);

		Path __MainJar = CopyJars(_Project, __Source, __Detection, __JarsDir, __LibDir, _Log);
		CollectDependencies(_Project, __Source, __Detection, __LibDir, _Log);

		// JavaFX refuses to run an Application main class from the plain
		// classpath ("JavaFX runtime components are missing"); resolving the
		// JavaFX modules from the module path is what makes it start. Null when
		// the bundle carries no JavaFX jars.
		String __JavaFxModulePath = JavaFxModulePath(__LibDir);
		if (__JavaFxModulePath != null)
		{
			_Log.accept("JavaFX detected — launching with module path for the JavaFX jars.");
		}

		Path __JvmDir = __Bundle.resolve("jvm");
		AssembleJvm(_Project, __JvmDir, __MainJar, __LibDir, _Log);

		WriteConfig(_Project, __Bundle, __MainJar, _Log);
		CopyIcon(_Project, __Bundle, _Log);
		WriteLauncher(_Project, __Bundle, __MainJar, __LibDir, __JavaFxModulePath, _Log);

		_Log.accept("Distribution bundle ready: " + __Bundle);
	}

	/**
	 * Runs {@code mvn clean package} / {@code gradle build}, streaming output.
	 * The build configuration (empty → {@code release}) selects a Maven profile
	 * or Gradle project property, e.g. {@code -Prelease}, and controls compiler
	 * debug info: release builds ship with no debug information at all
	 * ({@code -g:none}), while a {@code debug} build compiles with full debug
	 * info (line numbers, source, local variables).
	 */
	private static void RunEngineBuild(JpbProject _Project, Path _Source, DetectionResult _Detection, Consumer<String> _Log) throws IOException, InterruptedException
	{
		String __Config = _Project.GetBuildConfig() == null ? "" : _Project.GetBuildConfig().trim();
		if (__Config.isEmpty())
		{
			__Config = "release"; // empty field means the default configuration
		}
		boolean __Debug = "debug".equals(__Config);
		_Log.accept("Build configuration: " + __Config + (__Debug ? " (debug info on)" : " (no debug info)"));

		List<String> __Command = new ArrayList<>();
		if (_Detection.Tool() == BuildTool.Maven)
		{
			Path __Wrapper = FirstRegular(_Source, "mvnw.cmd", "mvnw");
			__Command.add(__Wrapper != null ? __Wrapper.toString() : (IsWindows() ? "mvn.cmd" : "mvn"));
			__Command.addAll(List.of("clean", "package", "-B"));
			// maven-compiler-plugin honors -Dmaven.compiler.debug / .debuglevel
			// out of the box, so no pom changes are needed in the bundled project.
			__Command.addAll(MavenCompilerDebugFlags(__Debug));
			if (HasMavenProfile(_Source, __Config))
			{
				__Command.add("-P" + __Config);
			}
			else
			{
				_Log.accept("No '" + __Config + "' profile in pom.xml — building with the default configuration.");
			}
		}
		else
		{
			Path __Wrapper = FirstRegular(_Source, "gradlew.bat", "gradlew");
			__Command.add(__Wrapper != null ? __Wrapper.toString() : (IsWindows() ? "gradle.bat" : "gradle"));
			__Command.addAll(List.of("build", "--no-daemon"));
			__Command.add("-P" + __Config); // Gradle project property, e.g. -Prelease
			// Gradle has no standard CLI property for options.debug, so expose
			// the mode as a project property (-Pjpb.debug) a build script can
			// opt into, e.g. compileJava.options.debug = findProperty("jpb.debug") != "false".
			__Command.add(__Debug ? "-Pjpb.debug=true" : "-Pjpb.debug=false");
		}
		_Log.accept("Running: " + String.join(" ", __Command));
		int __Code = RunProcess(__Command, _Source, _Log);
		if (__Code != 0)
		{
			throw new IOException("Build failed with exit code " + __Code + " (see log above).");
		}
	}

	/**
	 * Compiler debug flags passed to the bundled project's build. Maven's
	 * compiler plugin maps these to javac {@code -g} switches: the release
	 * configuration compiles with {@code -g:none} (no line numbers, source or
	 * local-variable info at all); the debug configuration with full
	 * {@code -g} (lines, vars, source). Any configuration other than the
	 * literal {@code debug} is treated as a release-style build.
	 */
	static List<String> MavenCompilerDebugFlags(boolean _Debug)
	{
		if (_Debug)
		{
			return List.of("-Dmaven.compiler.debug=true", "-Dmaven.compiler.debuglevel=lines,vars,source");
		}
		return List.of("-Dmaven.compiler.debug=true", "-Dmaven.compiler.debuglevel=none");
	}

	/**
	 * True when the project's pom.xml declares a profile with the given id.
	 */
	private static boolean HasMavenProfile(Path _Source, String _Profile)
	{
		Path __Pom = _Source.resolve("pom.xml");
		if (!Files.isRegularFile(__Pom))
		{
			return false;
		}
		try
		{
			String __Xml = Files.readString(__Pom, StandardCharsets.UTF_8);
			int __Profiles = __Xml.indexOf("<profiles>");
			return __Profiles >= 0 && __Xml.indexOf("<id>" + _Profile + "</id>", __Profiles) >= 0;
		}
		catch (IOException _E)
		{
			return false;
		}
	}

	/**
	 * Copies the produced JARs: the main JAR into {@code JARs/}, the rest into {@code JARs/lib/}.
	 */
	private static Path CopyJars(JpbProject _Project, Path _Source, DetectionResult _Detection, Path _JarsDir, Path _LibDir, Consumer<String> _Log) throws IOException
	{
		Path __BuildOutput = _Detection.Tool() == BuildTool.Maven ? _Source.resolve("target") : _Source.resolve("build/libs");
		if (!Files.isDirectory(__BuildOutput))
		{
			throw new IOException("No build output found in " + __BuildOutput + " — the build may have skipped packaging.");
		}

		List<Path> __Jars;
		try (Stream<Path> __Stream = Files.list(__BuildOutput))
		{
			__Jars = __Stream.filter(_P -> _P.getFileName().toString().endsWith(".jar")).filter(_P -> !IsClassifierJar(_P.getFileName().toString())).sorted(Comparator.comparingLong(Bundler::SizeOf).reversed()).collect(Collectors.toList());
		}
		if (__Jars.isEmpty())
		{
			throw new IOException("No application JARs produced in " + __BuildOutput + ".");
		}

		Path __MainJar = ResolveMainJar(_Project, __Jars, _Log);
		String __MainName = __MainJar.getFileName().toString();
		_Log.accept("Main JAR: " + __MainName);
		Files.copy(__MainJar, _JarsDir.resolve(__MainName), StandardCopyOption.REPLACE_EXISTING);

		for (Path __Jar : __Jars)
		{
			if (__Jar.equals(__MainJar))
			{
				continue;
			}
			Files.copy(__Jar, _LibDir.resolve(__Jar.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
			_Log.accept("Dependency JAR: " + __Jar.getFileName());
		}
		return _JarsDir.resolve(__MainName);
	}

	/**
	 * Gathers the project's runtime dependency JARs into {@code JARs/lib/} so the
	 * bundled app can run: the build output folder only holds the application
	 * JAR, while Maven/Gradle keep dependencies in their local caches. Without
	 * this step the launcher would fall back to {@code -jar}, which cannot load
	 * classes from the missing dependency JARs.
	 */
	private static void CollectDependencies(JpbProject _Project, Path _Source, DetectionResult _Detection, Path _LibDir, Consumer<String> _Log) throws IOException, InterruptedException
	{
		if (_Detection.Tool() == BuildTool.Maven)
		{
			Path __Wrapper = FirstRegular(_Source, "mvnw.cmd", "mvnw");
			List<String> __Command = new ArrayList<>(List.of(__Wrapper != null ? __Wrapper.toString() : (IsWindows() ? "mvn.cmd" : "mvn"), "dependency:copy-dependencies", "-DincludeScope=runtime", "-DoutputDirectory=" + _LibDir, "-B"));
			_Log.accept("Collecting dependency JARs (mvn dependency:copy-dependencies) ...");
			int __Code = RunProcess(__Command, _Source, _Log);
			if (__Code != 0)
			{
				// A dependency-free app still bundles fine; only warn so the user
				// knows a thin-jar app with dependencies would not start.
				_Log.accept("Warning: dependency collection failed (exit code " + __Code + ") — the bundle may not start if the app has runtime dependencies.");
			}
		}
		else
		{
			// Gradle has no standard CLI to export dependencies; the bundler relies
			// on the build producing a fat jar (shadow/application plugin).
			_Log.accept("Gradle project — no standard dependency export; the bundle relies on " + "a fat jar in build/libs for runtime dependencies.");
		}
	}

	/**
	 * Bundles the runtime: jlink when the JDK provides it, otherwise a full copy.
	 */
	private static void AssembleJvm(JpbProject _Project, Path _JvmDir, Path _MainJar, Path _LibDir, Consumer<String> _Log) throws IOException, InterruptedException
	{
		String __JvmSource = _Project.GetJvmSourcePath() == null ? "" : _Project.GetJvmSourcePath().trim();
		if (__JvmSource.isEmpty())
		{
			// Empty field means "use the default JDK from the environment".
			String __Found = JdkLocator.FindInstalledJdk();
			if (__Found == null)
			{
				throw new IOException("Path to JDK is empty and no JDK could be found in the environment — " + "set it on the dashboard before building.");
			}
			__JvmSource = __Found;
			_Log.accept("Using default JDK from the environment: " + __JvmSource);
		}
		Path __Jdk = Path.of(__JvmSource).toAbsolutePath().normalize();
		if (!Files.isDirectory(__Jdk))
		{
			throw new IOException("JDK path is not a directory: " + __Jdk);
		}

		Path __Bin = __Jdk.resolve("bin");
		Path __Jlink = __Bin.resolve(IsWindows() ? "jlink.exe" : "jlink");
		if (Files.isRegularFile(__Jlink))
		{
			String __Modules = ComputeModules(__Jdk, _MainJar, _LibDir, _Log);
			_Log.accept("Bundling runtime with jlink (modules: " + __Modules + ") ...");
			int __Code = RunProcess(List.of(__Jlink.toString(), "--add-modules", __Modules, "--output", _JvmDir.toString(), "--strip-debug", "--no-header-files", "--no-man-pages", "--compress=2"), __Jdk, _Log);
			if (__Code != 0)
			{
				throw new IOException("jlink failed with exit code " + __Code + " (see log above).");
			}
		}
		else
		{
			_Log.accept("No jlink in " + __Jdk + " — copying the full runtime instead (larger bundle).");
			CopyTree(__Jdk, _JvmDir);
		}
	}

	/**
	 * Asks jdeps for the platform modules the app needs and feeds them to jlink.
	 *
	 * <p>The main JAR <em>and</em> the dependency JARs are analyzed together: the
	 * runtime must contain every module the classpath libraries rely on (e.g.
	 * JavaFX needs {@code java.desktop} and {@code jdk.unsupported}), otherwise
	 * the packaged app dies at startup with a missing-class error. Non-platform
	 * modules (javafx.*, org.json, ...) live in {@code JARs/lib/} and are ignored
	 * by jdeps, so only JDK modules end up in the list jlink can add. Augment
	 * the result with JavaFX extras when the bundle carries JavaFX.</p>
	 */
	private static String ComputeModules(Path _Jdk, Path _MainJar, Path _LibDir, Consumer<String> _Log) throws IOException, InterruptedException
	{
		Path __Bin = _Jdk.resolve("bin");
		Path __Jdeps = __Bin.resolve(IsWindows() ? "jdeps.exe" : "jdeps");
		if (!Files.isRegularFile(__Jdeps) || !Files.isRegularFile(_MainJar))
		{
			return AugmentJavaFxModules(FallbackModules, _LibDir);
		}

		List<String> __Command = new ArrayList<>(List.of(__Jdeps.toString(), "--ignore-missing-deps", "--print-module-deps", "--multi-release", JavaFeatureVersion(_Jdk), _MainJar.toString()));
		if (Files.isDirectory(_LibDir))
		{
			try (Stream<Path> __Stream = Files.list(_LibDir))
			{
				__Stream.filter(_P -> _P.getFileName().toString().endsWith(".jar")).filter(_P -> !HasModuleInfo(_P)).sorted().forEach(_P -> __Command.add(_P.toString()));
			}
		}

		Process __Process = new ProcessBuilder(__Command).redirectErrorStream(true).start();
		String __Output;
		try (BufferedReader __Reader = new BufferedReader(new InputStreamReader(__Process.getInputStream(), StandardCharsets.UTF_8)))
		{
			__Output = __Reader.lines().collect(Collectors.joining());
		}
		int __Code = __Process.waitFor();
		if (__Code == 0 && !__Output.isBlank())
		{
			String __Modules = JdkModulesOnly(__Output.trim());
			String __Augmented = AugmentJavaFxModules(__Modules, _LibDir);
			_Log.accept("jdeps module analysis: " + __Augmented);
			return __Augmented;
		}
		_Log.accept("Warning: jdeps analysis failed (exit code " + __Code + ") — using fallback modules with JavaFX requirements.");
		return AugmentJavaFxModules(FallbackModules, _LibDir);
	}

	/**
	 * Keeps only JDK modules ({@code java.*}/{@code jdk.*}) in the jdeps result.
	 * jlink can only add modules that ship with the bundling JDK; modular
	 * dependency JARs (JavaFX, ...) would otherwise leak their own module names
	 * into the list and make jlink fail with "module not found".
	 */
	private static String JdkModulesOnly(String _Modules)
	{
		String __Filtered = Arrays.stream(_Modules.split(",")).map(String::trim).filter(_M -> _M.startsWith("java.") || _M.startsWith("jdk.")).distinct().collect(Collectors.joining(","));
		return __Filtered.isEmpty() ? FallbackModules : __Filtered;
	}

	/**
	 * Add JavaFX transitive platform modules when the bundle carries JavaFX.
	 * Leave non-JavaFX apps untouched to keep the jlink runtime minimal.
	 *
	 * @param _Modules jdeps-derived or fallback module list
	 * @param _LibDir dependency jars scanned for JavaFX
	 * @return augmented module list, or the input unchanged without JavaFX
	 */
	private static String AugmentJavaFxModules(String _Modules, Path _LibDir)
	{
		if (JavaFxModulePath(_LibDir) == null)
		{
			return _Modules;
		}
		return MergeModules(_Modules, JavaFxExtraModules);
	}

	/**
	 * Merge two comma-separated module lists into one de-duplicated list.
	 * Trim and deduplicate because jdeps output and the JavaFX extras overlap,
	 * and jlink needs a clean {@code --add-modules} value.
	 *
	 * @param _Base base module list
	 * @param _Extra modules to add
	 * @return merged, trimmed, distinct module list
	 */
	private static String MergeModules(String _Base, String _Extra)
	{
		List<String> __Merged = new ArrayList<>(Arrays.asList(_Base.split(",")));
		__Merged.addAll(Arrays.asList(_Extra.split(",")));
		return __Merged.stream().map(String::trim).filter(_M -> !_M.isEmpty()).distinct().collect(Collectors.joining(","));
	}

	/**
	 * Builds the {@code --module-path} value for the launcher when the bundle
	 * carries JavaFX: the relative paths ({@code JARs/lib/...}) of every
	 * {@code javafx-*.jar} that contains a {@code module-info.class}, joined
	 * with the platform path separator. JavaFX refuses to start an
	 * {@code Application} main class from the plain classpath, but accepts it
	 * once the JavaFX modules are resolved from the module path.
	 *
	 * <p>Only jars with a module descriptor go on the module path: JavaFX ships
	 * empty stub jars (no classes, no {@code module-info}) next to the platform
	 * jars that carry both, and a descriptor-less jar on the module path would
	 * become an automatic module whose derived name collides with the real one.</p>
	 *
	 * @return the module-path value, or {@code null} when there are no JavaFX jars
	 */
	static String JavaFxModulePath(Path _LibDir)
	{
		if (!Files.isDirectory(_LibDir))
		{
			return null;
		}
		List<String> __Modules = new ArrayList<>();
		try (Stream<Path> __Stream = Files.list(_LibDir))
		{
			__Stream.filter(_P -> _P.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("javafx-")).filter(_P -> _P.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")).filter(Bundler::HasModuleInfo).sorted().forEach(_P -> __Modules.add("JARs/lib/" + _P.getFileName()));
		}
		catch (IOException _E)
		{
			return null;
		}
		return __Modules.isEmpty() ? null : String.join(File.pathSeparator, __Modules);
	}

	/**
	 * True when the JAR contains a {@code module-info.class} (a modular JAR).
	 */
	private static boolean HasModuleInfo(Path _Jar)
	{
		try (ZipFile __Zip = new ZipFile(_Jar.toFile()))
		{
			return __Zip.getEntry("module-info.class") != null;
		}
		catch (IOException _E)
		{
			return false;
		}
	}

	/**
	 * Feature version of the bundling JDK (e.g. {@code 26} for {@code 26.0.2}),
	 * read from its {@code release} file. Used as the {@code --multi-release}
	 * target so jdeps can analyze multi-release dependency JARs; falls back to
	 * {@code 17} when the version cannot be determined.
	 */
	private static String JavaFeatureVersion(Path _Jdk)
	{
		try
		{
			Path __Release = _Jdk.resolve("release");
			if (Files.isRegularFile(__Release))
			{
				for (String __Line : Files.readAllLines(__Release, StandardCharsets.UTF_8))
				{
					if (__Line.startsWith("JAVA_VERSION="))
					{
						String __Version = __Line.substring("JAVA_VERSION=".length()).replace("\"", "").trim();
						int __Dot = __Version.indexOf('.');
						String __Feature = __Dot > 0 ? __Version.substring(0, __Dot) : __Version;
						if (!__Feature.isEmpty() && __Feature.chars().allMatch(Character::isDigit))
						{
							return __Feature;
						}
					}
				}
			}
		}
		catch (IOException _Ignored)
		{
			// fall through to the default
		}
		return "17";
	}

	/**
	 * Writes {@code config/app-config.json} with the runtime settings.
	 */
	private static void WriteConfig(JpbProject _Project, Path _Bundle, Path _MainJar, Consumer<String> _Log) throws IOException
	{
		JSONObject __Node = new JSONObject();
		__Node.put("projectName", _Project.GetProjectName());
		__Node.put("mainJar", _MainJar.getFileName().toString());
		__Node.put("minJavaVersion", _Project.GetMinJavaVersion());
		__Node.put("customJvmFlags", _Project.GetCustomJvmFlags());
		__Node.put("jvmBundled", Files.isDirectory(_Bundle.resolve("jvm")));

		Path __ConfigDir = _Bundle.resolve("config");
		Files.createDirectories(__ConfigDir);
		Files.writeString(__ConfigDir.resolve("app-config.json"), __Node.toString(2), StandardCharsets.UTF_8);
		_Log.accept("Wrote config/app-config.json");
	}

	/**
	 * Copies {@code icon.ico} from the project folder into {@code assets/}, when present.
	 */
	private static void CopyIcon(JpbProject _Project, Path _Bundle, Consumer<String> _Log) throws IOException
	{
		Path __Icon = _Project.GetIconPath();
		if (!Files.isRegularFile(__Icon))
		{
			_Log.accept("No icon.ico in the project folder — skipping assets.");
			return;
		}
		Path __Assets = _Bundle.resolve("assets");
		Files.createDirectories(__Assets);
		Files.copy(__Icon, __Assets.resolve("icon.ico"), StandardCopyOption.REPLACE_EXISTING);
		_Log.accept("Copied icon to assets/icon.ico");
	}

	/**
	 * Compiles the native (Rust) launcher into the bundle root and writes the
	 * {@code config/launcher.conf} it reads at startup (SDD §6).
	 *
	 * @param _JavaFxModulePath {@code --module-path} value for JavaFX jars, or
	 *                          {@code null} when the bundle has no JavaFX
	 */
	private static void WriteLauncher(JpbProject _Project, Path _Bundle, Path _MainJar, Path _LibDir, String _JavaFxModulePath, Consumer<String> _Log) throws IOException, InterruptedException
	{
		String __Flags = LauncherSupport.JvmFlags(_Project.GetCustomJvmFlags(), _Project.GetHeapSize(), _Project.GetMaxHeapSize(), _Project.GetStackSize(), _Project.GetMaxMetaspaceSize());
		// Debug builds get launcher-error.log on startup failures; release
		// bundles only show the native error dialog and never write to disk.
		String __BuildConfig = _Project.GetBuildConfig() == null ? "" : _Project.GetBuildConfig().trim();
		boolean __Debug = "debug".equals(__BuildConfig);
		boolean __HasLibs = HasJars(_LibDir);
		String __MainClass = ReadMainClass(_MainJar);
		if (__MainClass == null)
		{
			// A bundle without an entry point can never start: -jar mode needs the
			// manifest Main-Class and classpath mode needs a main class to invoke.
			// Fail at build time instead of shipping a launcher that exits with
			// code 1 on first run ("no main manifest attribute").
			throw new IOException("Cannot package " + _MainJar.getFileName() + ": its manifest has no Main-Class attribute. Configure the main " + "class in the build (e.g. the maven-jar-plugin <mainClass> or the " + "Gradle jar manifest) and rebuild.");
		}
		_Log.accept(__HasLibs ? "Launch entry point: " + __MainClass + " (classpath mode)" : "Launch entry point: " + __MainClass + " (-jar mode)");
		String __MainJarName = _MainJar.getFileName().toString();

		String __ConfiguredRustc = _Project.GetRustcPath() == null ? "" : _Project.GetRustcPath().trim();
		Path __Rustc = LauncherSupport.ResolveRustc(__ConfiguredRustc);
		if (__Rustc == null)
		{
			String __Message = __ConfiguredRustc.isEmpty() ? "Rust compiler (rustc) not found — set the Path to Rust Compiler on the dashboard. " + "Looked in RUSTC, ~/.cargo/bin and PATH." : "The configured Rust compiler path is not a rustc executable: " + __ConfiguredRustc + " — fix the Path to Rust Compiler on the dashboard.";
			throw new IOException(__Message);
		}
		if (__ConfiguredRustc.isEmpty())
		{
			_Log.accept("Using default rustc from the environment: " + __Rustc);
		}
		String __ExeName = LauncherSupport.ExeName(_Project.GetProjectName());
		Path __Exe = _Bundle.resolve(__ExeName);

		// Runtime settings consumed by the launcher on startup.
		Path __ConfigDir = _Bundle.resolve("config");
		Files.createDirectories(__ConfigDir);
		Files.writeString(__ConfigDir.resolve("launcher.conf"), LauncherSupport.LauncherConf(__MainJarName, __Flags, __HasLibs, __MainClass, _JavaFxModulePath, __Debug), StandardCharsets.UTF_8);

		// Extract the launcher source and compile it next to the bundle contents.
		Path __Staging = _Bundle.resolve(".launcher-src");
		Files.createDirectories(__Staging);
		Path __Source = __Staging.resolve("launcher.rs");
		try (InputStream __In = Bundler.class.getResourceAsStream("/com/jpb/bundler/launcher.rs"))
		{
			if (__In == null)
			{
				throw new IOException("launcher.rs resource is missing from the application JAR.");
			}
			Files.copy(__In, __Source, StandardCopyOption.REPLACE_EXISTING);
		}

		_Log.accept("Compiling native launcher with " + __Rustc + " ...");
		int __Code = RunProcess(List.of(__Rustc.toString(), "--edition", "2021", "-O", "-C", "strip=symbols", __Source.toString(), "-o", __Exe.toString()), _Bundle, _Log);
		DeleteTree(__Staging);
		if (__Code != 0)
		{
			throw new IOException("rustc failed with exit code " + __Code + " (see log above).");
		}
		if (!IsWindows())
		{
			MakeExecutable(__Exe);
		}
		_Log.accept("Wrote native launcher: " + __Exe.getFileName());
	}

	/**
	 * Picks the main JAR: the user-configured name (with {@code .jar} appended
	 * when omitted, e.g. {@code MyProject} → {@code MyProject.jar}), else the
	 * largest produced JAR.
	 */
	private static Path ResolveMainJar(JpbProject _Project, List<Path> _Jars, Consumer<String> _Log)
	{
		String __Requested = _Project.GetMainJarName() == null ? "" : _Project.GetMainJarName().trim();
		if (!__Requested.isEmpty())
		{
			String __Name = __Requested.toLowerCase(Locale.ROOT).endsWith(".jar") ? __Requested : __Requested + ".jar";
			for (Path __Jar : _Jars)
			{
				if (__Jar.getFileName().toString().equalsIgnoreCase(__Name))
				{
					return __Jar;
				}
			}
			_Log.accept("Main JAR name not found in the build output: " + __Name + " — using the largest produced JAR instead.");
		}
		return _Jars.get(0);
	}

	/**
	 * Reads the manifest {@code Main-Class} of a JAR, or {@code null}.
	 */
	private static String ReadMainClass(Path _Jar)
	{
		try (JarFile __JarFile = new JarFile(_Jar.toFile()))
		{
			Attributes __Attrs = __JarFile.getManifest() == null ? null : __JarFile.getManifest().getMainAttributes();
			if (__Attrs != null)
			{
				String __Main = __Attrs.getValue(Attributes.Name.MAIN_CLASS);
				if (__Main != null && !__Main.isBlank())
				{
					return __Main.trim();
				}
			}
		}
		catch (IOException _Ignored)
		{
			// fall through to null
		}
		return null;
	}

	private static boolean HasJars(Path _Dir)
	{
		if (!Files.isDirectory(_Dir))
		{
			return false;
		}
		try (Stream<Path> __Stream = Files.list(_Dir))
		{
			return __Stream.anyMatch(_P -> _P.getFileName().toString().endsWith(".jar"));
		}
		catch (IOException _E)
		{
			return false;
		}
	}

	private static boolean IsClassifierJar(String _Name)
	{
		String __Lower = _Name.toLowerCase(Locale.ROOT);
		return __Lower.endsWith("-sources.jar") || __Lower.endsWith("-javadoc.jar") || __Lower.endsWith("-tests.jar") || __Lower.startsWith("original-");
	}

	private static long SizeOf(Path _Path)
	{
		try
		{
			return Files.size(_Path);
		}
		catch (IOException _E)
		{
			return 0L;
		}
	}

	private static Path FirstRegular(Path _Dir, String... _Names)
	{
		for (String __Name : _Names)
		{
			Path __Candidate = _Dir.resolve(__Name);
			if (Files.isRegularFile(__Candidate))
			{
				return __Candidate;
			}
		}
		return null;
	}

	private static int RunProcess(List<String> _Command, Path _WorkDir, Consumer<String> _Log) throws IOException, InterruptedException
	{
		ProcessBuilder __Pb = new ProcessBuilder(_Command);
		__Pb.directory(_WorkDir.toFile());
		__Pb.redirectErrorStream(true);
		Process __Process = __Pb.start();
		try (BufferedReader __Reader = new BufferedReader(new InputStreamReader(__Process.getInputStream(), StandardCharsets.UTF_8)))
		{
			String __Line;
			while ((__Line = __Reader.readLine()) != null)
			{
				_Log.accept(__Line);
			}
		}
		return __Process.waitFor();
	}

	private static void DeleteTree(Path _Root) throws IOException
	{
		if (!Files.exists(_Root))
		{
			return;
		}
		try (Stream<Path> __Paths = Files.walk(_Root))
		{
			List<Path> __List = __Paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
			for (Path __P : __List)
			{
				Files.deleteIfExists(__P);
			}
		}
	}

	private static void CopyTree(Path _Source, Path _Target) throws IOException
	{
		try (Stream<Path> __Paths = Files.walk(_Source))
		{
			for (Path __P : (Iterable<Path>) __Paths::iterator)
			{
				Path __Destination = _Target.resolve(_Source.relativize(__P).toString());
				if (Files.isSymbolicLink(__P))
				{
					// Preserve the link itself (macOS JDKs contain symlinks).
					Files.createDirectories(__Destination.getParent());
					Files.copy(__P, __Destination, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
				}
				else if (Files.isDirectory(__P))
				{
					Files.createDirectories(__Destination);
				}
				else
				{
					Files.createDirectories(__Destination.getParent());
					Files.copy(__P, __Destination, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	private static void MakeExecutable(Path _File)
	{
		try
		{
			Files.setPosixFilePermissions(_File, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE));
		}
		catch (UnsupportedOperationException | IOException _E)
		{
			// Not a POSIX file system (e.g. Windows) — nothing to do.
		}
	}

	private static boolean IsWindows()
	{
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}
}
