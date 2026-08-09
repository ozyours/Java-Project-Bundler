package com.jpb.bundler.Engine.Toolchain;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Finds the Rust compiler on the local machine so the build can use it as the
 * default when no explicit rustc path is configured (a typed value on the
 * dashboard always wins once saved to the {@code .jpb}). Pure local machine
 * lookup — no network or telemetry.
 *
 * <p>Search order: the {@code RUSTC} environment variable, the rustup install
 * folder {@code ~/.cargo/bin} (honouring {@code CARGO_HOME}), then the
 * {@code rustc} executable found on {@code PATH}.</p>
 */
public final class RustLocator
{
	private RustLocator()
	{
	}

	/**
	 * @return the first usable rustc found, or {@code null} if none could be located
	 */
	public static String FindInstalledRustc()
	{
		String __FromEnv = System.getenv("RUSTC");
		if (IsRustcFile(__FromEnv))
		{
			return __FromEnv.trim();
		}

		Path __CargoBin = CargoBinDir();
		if (__CargoBin != null && Files.isRegularFile(__CargoBin.resolve(ExecutableName("rustc"))))
		{
			return __CargoBin.resolve(ExecutableName("rustc")).toString();
		}

		String __PathVar = System.getenv("PATH");
		if (__PathVar != null)
		{
			return ResolveFromPath(SplitPath(__PathVar), "rustc");
		}
		return null;
	}

	// --- helpers (package-private for unit testing) ---

	/**
	 * {@code true} when the path points at an existing regular file.
	 */
	static boolean IsRustcFile(String _Path)
	{
		if (_Path == null || _Path.isBlank())
		{
			return false;
		}
		try
		{
			return Files.isRegularFile(Path.of(_Path.trim()));
		}
		catch (RuntimeException _E)
		{
			return false; // malformed path
		}
	}

	/**
	 * {@code CARGO_HOME/bin} or {@code ~/.cargo/bin}; {@code null} when no home is known.
	 */
	static Path CargoBinDir()
	{
		String __CargoHome = System.getenv("CARGO_HOME");
		Path __Base;
		if (__CargoHome != null && !__CargoHome.isBlank())
		{
			__Base = Path.of(__CargoHome.trim());
		}
		else
		{
			String __Home = System.getProperty("user.home");
			if (__Home == null || __Home.isBlank())
			{
				return null;
			}
			__Base = Path.of(__Home).resolve(".cargo");
		}
		return __Base.resolve("bin");
	}

	/**
	 * First PATH directory containing the executable, or {@code null}.
	 *
	 * @param _PathDirs    split PATH entries (see {@link #SplitPath})
	 * @param _Executable  base executable name without the platform extension
	 */
	static String ResolveFromPath(List<String> _PathDirs, String _Executable)
	{
		String __Exe = ExecutableName(_Executable);
		for (String __Dir : _PathDirs)
		{
			if (__Dir == null || __Dir.isBlank())
			{
				continue;
			}
			try
			{
				Path __Candidate = Path.of(__Dir.trim()).resolve(__Exe);
				if (Files.isRegularFile(__Candidate))
				{
					return __Candidate.toString();
				}
			}
			catch (RuntimeException _Ignored)
			{
				// malformed entry — keep scanning
			}
		}
		return null;
	}

	/**
	 * Splits a PATH-style variable on the OS-specific path separator.
	 */
	static List<String> SplitPath(String _Path)
	{
		List<String> __Dirs = new ArrayList<>();
		for (String __Dir : _Path.split(Pattern.quote(File.pathSeparator)))
		{
			if (!__Dir.isBlank())
			{
				__Dirs.add(__Dir.trim());
			}
		}
		return __Dirs;
	}

	/**
	 * Appends {@code .exe} on Windows, e.g. {@code rustc} → {@code rustc.exe}.
	 */
	static String ExecutableName(String _Base)
	{
		return IsWindows() ? _Base + ".exe" : _Base;
	}

	private static boolean IsWindows()
	{
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}
}
