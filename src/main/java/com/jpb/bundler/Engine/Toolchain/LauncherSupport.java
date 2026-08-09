package com.jpb.bundler.Engine.Toolchain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Support for the native (Rust) launcher that replaced the {@code run.vbs} /
 * {@code run.cmd} / {@code run.sh} scripts (SDD.md §6). Provides the bundle-side
 * launcher configuration file, the executable file name, and the rustc
 * resolution shared by the dashboard and the packaging engine.
 */
public final class LauncherSupport
{
	// Keys must match the constants in the bundled launcher.rs resource.
	private static final String ConfMainJar = "main.jar";
	private static final String ConfJvmFlags = "jvm.flags";
	private static final String ConfUseClasspath = "use.classpath";
	private static final String ConfMainClass = "main.class";
	private static final String ConfModulePath = "jvm.module.path";
	private static final String ConfDebugMode = "debug.mode";

	private LauncherSupport()
	{
	}

	/**
	 * Bundle entry name of the launcher executable: {@code <Name>.exe} on
	 * Windows, plain {@code <Name>} elsewhere.
	 */
	public static String ExeName(String _ProjectName)
	{
		return ExeName(_ProjectName, IsWindows());
	}

	/**
	 * Testable variant with the platform decided by the caller.
	 */
	static String ExeName(String _ProjectName, boolean _Windows)
	{
		String __Safe = _ProjectName == null ? "" : _ProjectName.trim();
		if (__Safe.isEmpty())
		{
			__Safe = "app";
		}
		return _Windows ? __Safe + ".exe" : __Safe;
	}

	/**
	 * Renders {@code config/launcher.conf}, the runtime settings the native
	 * launcher reads at startup. Rewritten on every build.
	 *
	 * @param _MainJarName file name of the main JAR inside {@code JARs/}
	 * @param _Flags       custom JVM flags, e.g. {@code -Xmx2g} (may be empty)
	 * @param _Classpath   {@code true} to launch with {@code -cp} + main class
	 * @param _MainClass   main class used with {@code -cp}; ignored when blank
	 * @param _ModulePath  {@code --module-path} value (JavaFX jars), or {@code null}
	 * @param _Debug       {@code true} to write launcher-error.log on startup failures
	 */
	public static String LauncherConf(String _MainJarName, String _Flags, boolean _Classpath, String _MainClass, String _ModulePath, boolean _Debug)
	{
		String __Jar = _MainJarName == null || _MainJarName.isBlank() ? "app-main.jar" : _MainJarName.trim();
		String __ExtraFlags = _Flags == null ? "" : _Flags.trim();
		String __Clazz = _MainClass == null ? "" : _MainClass.trim();
		String __Mp = _ModulePath == null ? "" : _ModulePath.trim();
		boolean __UseClasspath = _Classpath && !__Clazz.isEmpty();

		return "# Launcher configuration consumed by the native launcher executable.\n" + "# Rewritten on every build; do not edit by hand.\n" + ConfMainJar + " = " + __Jar + "\n" + ConfJvmFlags + " = " + __ExtraFlags + "\n" + ConfUseClasspath + " = " + __UseClasspath + "\n" + ConfMainClass + " = " + __Clazz + "\n" + ConfModulePath + " = " + __Mp + "\n" + ConfDebugMode + " = " + _Debug + "\n";
	}

	/**
	 * Combines the structured heap/stack/metaspace sizes with the free-form
	 * custom flags into the single {@code jvm.flags} line. Empty values are
	 * skipped; custom flags are appended last so they can override the sizes.
	 */
	public static String JvmFlags(String _CustomFlags, String _HeapSize, String _MaxHeapSize, String _StackSize, String _MaxMetaspaceSize)
	{
		StringBuilder __Sb = new StringBuilder();
		AppendFlag(__Sb, "-Xms", _HeapSize);
		AppendFlag(__Sb, "-Xmx", _MaxHeapSize);
		AppendFlag(__Sb, "-Xss", _StackSize);
		AppendFlag(__Sb, "-XX:MaxMetaspaceSize=", _MaxMetaspaceSize);
		String __Custom = _CustomFlags == null ? "" : _CustomFlags.trim();
		if (!__Custom.isEmpty())
		{
			if (__Sb.length() > 0)
			{
				__Sb.append(' ');
			}
			__Sb.append(__Custom);
		}
		return __Sb.toString();
	}

	private static void AppendFlag(StringBuilder _Sb, String _Flag, String _Value)
	{
		String __V = _Value == null ? "" : _Value.trim();
		if (!__V.isEmpty())
		{
			if (_Sb.length() > 0)
			{
				_Sb.append(' ');
			}
			_Sb.append(_Flag).append(__V);
		}
	}

	/**
	 * Resolves the rustc executable to compile the launcher with: the value
	 * configured on the dashboard when usable, otherwise a locally discovered
	 * rustc ({@code RUSTC}, {@code ~/.cargo/bin}, {@code PATH}).
	 *
	 * @return a path to an existing rustc executable, or {@code null}
	 */
	public static Path ResolveRustc(String _Configured)
	{
		if (_Configured != null && !_Configured.isBlank())
		{
			try
			{
				Path __Path = Path.of(_Configured.trim());
				return Files.isRegularFile(__Path) ? __Path : null;
			}
			catch (RuntimeException _E)
			{
				return null; // malformed path — fall through to discovery
			}
		}
		String __Found = RustLocator.FindInstalledRustc();
		return __Found != null ? Path.of(__Found) : null;
	}

	private static boolean IsWindows()
	{
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}
}
