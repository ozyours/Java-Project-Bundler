package com.jpb.bundler.Engine.Toolchain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Finds an installed JDK on the local machine so the build can use it as the
 * default when no explicit JDK path is configured (a typed value on the
 * dashboard always wins once saved to the {@code .jpb}).
 *
 * <p>Search order: the JDK the bundler itself runs on ({@code java.home}),
 * then {@code JAVA_HOME}, then the usual per-OS install folders, then the
 * {@code java} executable on {@code PATH}. Pure local machine lookup — no
 * network or telemetry.</p>
 */
public final class JdkLocator
{
	private JdkLocator()
	{
	}

	/**
	 * @return the first usable JDK found, or {@code null} if none could be located
	 */
	public static String FindInstalledJdk()
	{
		String __Home = System.getProperty("java.home");
		if (IsJdk(__Home))
		{
			return __Home;
		}

		String __JavaHome = System.getenv("JAVA_HOME");
		if (IsJdk(__JavaHome))
		{
			return __JavaHome;
		}

		Path __Best = PickBest(FindCandidates(CommonRoots()));
		if (__Best != null)
		{
			return __Best.toString();
		}

		return FromPathLookup();
	}

	// --- helpers (package-private for unit testing) ---

	/**
	 * Best (highest-versioned) valid JDK among the candidates, or {@code null}.
	 */
	static Path PickBest(List<Path> _Candidates)
	{
		return _Candidates.stream().map(JdkLocator::JdkHome).filter(JdkLocator::IsJdkDir).sorted(Comparator.comparing(JdkLocator::VersionNumbers, JdkLocator::CompareVersions).reversed()).findFirst().orElse(null);
	}

	/**
	 * On macOS a JDK lives at {@code <name>.jdk/Contents/Home}; elsewhere the
	 * candidate directory is the home directly.
	 */
	static Path JdkHome(Path _Candidate)
	{
		Path __MacHome = _Candidate.resolve("Contents").resolve("Home");
		return Files.isDirectory(__MacHome) ? __MacHome : _Candidate;
	}

	/**
	 * A directory is a JDK if it has both {@code java} and {@code javac} (the
	 * build needs javac, jlink/jdeps need a real JDK). A JRE-only path is left
	 * for the user to type in manually.
	 */
	static boolean IsJdkDir(Path _Dir)
	{
		Path __Bin = _Dir.resolve("bin");
		boolean __Java = Files.isRegularFile(__Bin.resolve("java")) || Files.isRegularFile(__Bin.resolve("java.exe"));
		boolean __Javac = Files.isRegularFile(__Bin.resolve("javac")) || Files.isRegularFile(__Bin.resolve("javac.exe"));
		return __Java && __Javac;
	}

	/**
	 * Compares two parsed version lists element-wise; a prefix sorts as older.
	 */
	static int CompareVersions(List<Integer> _A, List<Integer> _B)
	{
		int __Size = Math.min(_A.size(), _B.size());
		for (int __I = 0; __I < __Size; __I++)
		{
			int __Cmp = Integer.compare(_A.get(__I), _B.get(__I));
			if (__Cmp != 0)
			{
				return __Cmp;
			}
		}
		return Integer.compare(_A.size(), _B.size());
	}

	/**
	 * Parses version numbers out of a JDK folder name, e.g. {@code jdk-17.0.10+7 → [17, 0, 10, 7]}.
	 */
	static List<Integer> VersionNumbers(Path _Dir)
	{
		List<Integer> __Numbers = new ArrayList<>();
		StringBuilder __Current = new StringBuilder();
		for (char __C : _Dir.getFileName().toString().toCharArray())
		{
			if (Character.isDigit(__C))
			{
				__Current.append(__C);
			}
			else if (!__Current.isEmpty())
			{
				__Numbers.add(Integer.parseInt(__Current.toString()));
				__Current.setLength(0);
			}
		}
		if (!__Current.isEmpty())
		{
			__Numbers.add(Integer.parseInt(__Current.toString()));
		}
		return __Numbers;
	}

	private static boolean IsJdk(String _Path)
	{
		if (_Path == null || _Path.isBlank())
		{
			return false;
		}
		try
		{
			return IsJdkDir(Path.of(_Path.trim()));
		}
		catch (RuntimeException _E)
		{
			return false; // malformed path
		}
	}

	private static List<Path> FindCandidates(List<Path> _Roots)
	{
		List<Path> __Candidates = new ArrayList<>();
		for (Path __Root : _Roots)
		{
			if (!Files.isDirectory(__Root))
			{
				continue;
			}
			try (Stream<Path> __Children = Files.list(__Root))
			{
				__Children.forEach(__Candidates::add);
			}
			catch (IOException _Ignored)
			{
				// skip unreadable root
			}
		}
		return __Candidates;
	}

	private static List<Path> CommonRoots()
	{
		String __Os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		List<Path> __Roots = new ArrayList<>();
		if (__Os.contains("win"))
		{
			for (String __ProgramFiles : new String[]{System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)"), "C:\\Program Files", "C:\\Program Files (x86)"})
			{
				if (__ProgramFiles == null || __ProgramFiles.isBlank())
				{
					continue;
				}
				Path __Pf = Path.of(__ProgramFiles);
				__Roots.addAll(List.of(__Pf.resolve("Java"), __Pf.resolve("Eclipse Adoptium"), __Pf.resolve("Microsoft"), __Pf.resolve("Amazon Corretto"), __Pf.resolve("Zulu"), __Pf.resolve("BellSoft")));
			}
		}
		else if (__Os.contains("mac"))
		{
			__Roots.add(Path.of("/Library/Java/JavaVirtualMachines"));
			__Roots.add(Path.of(System.getProperty("user.home"), "Library", "Java", "JavaVirtualMachines"));
		}
		else
		{
			__Roots.addAll(List.of(Path.of("/usr/lib/jvm"), Path.of("/opt/java"), Path.of("/usr/java")));
		}
		return __Roots;
	}

	/**
	 * Last resort: resolve the {@code java} on PATH up to its JDK home.
	 */
	private static String FromPathLookup()
	{
		boolean __Windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
		try
		{
			Process __Process = new ProcessBuilder(__Windows ? "where" : "which", "java").redirectErrorStream(true).start();
			List<String> __Lines;
			try (BufferedReader __Reader = new BufferedReader(new InputStreamReader(__Process.getInputStream(), StandardCharsets.UTF_8)))
			{
				__Lines = __Reader.lines().toList(); // drain fully before waitFor
			}
			__Process.waitFor();
			if (__Lines.isEmpty() || __Lines.get(0).isBlank())
			{
				return null;
			}
			// Guard against garbage output (e.g. Windows "INFO: ..." error text)
			// which Path.of would reject with InvalidPathException.
			Path __Real;
			try
			{
				__Real = Path.of(__Lines.get(0).trim()).toRealPath(); // resolves symlinks (Unix)
			}
			catch (RuntimeException _E)
			{
				return null;
			}
			Path __Home = __Real.getParent() != null ? __Real.getParent().getParent() : null;
			return __Home != null && IsJdkDir(__Home) ? __Home.toString() : null;
		}
		catch (InterruptedException _E)
		{
			Thread.currentThread().interrupt();
			return null;
		}
		catch (IOException _E)
		{
			return null;
		}
	}
}
