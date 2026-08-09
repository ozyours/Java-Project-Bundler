package com.jpb.bundler.Engine.Build;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Auto-detects the build engine of a source project (SDD.md §5.1): a Maven
 * project has {@code pom.xml} (optionally an {@code mvnw} wrapper), a Gradle
 * project has {@code build.gradle} or {@code build.gradle.kts} (optionally a
 * {@code gradlew} wrapper). Anything else is {@link BuildTool#Unrecognized}.
 */
public final class ProjectDetector
{

	private ProjectDetector()
	{
	}

	/**
	 * Probes the directory at {@code _SourcePath} and returns the detection result.
	 */
	public static DetectionResult Detect(String _SourcePath)
	{
		if (_SourcePath == null || _SourcePath.isBlank())
		{
			return new DetectionResult(BuildTool.Unrecognized, false);
		}
		Path __Dir = Path.of(_SourcePath.trim());
		if (!Files.isDirectory(__Dir))
		{
			return new DetectionResult(BuildTool.Unrecognized, false);
		}

		if (Files.isRegularFile(__Dir.resolve("pom.xml")))
		{
			boolean __Wrapper = Files.isRegularFile(__Dir.resolve("mvnw")) || Files.isRegularFile(__Dir.resolve("mvnw.cmd"));
			return new DetectionResult(BuildTool.Maven, __Wrapper);
		}

		if (Files.isRegularFile(__Dir.resolve("build.gradle")) || Files.isRegularFile(__Dir.resolve("build.gradle.kts")))
		{
			boolean __Wrapper = Files.isRegularFile(__Dir.resolve("gradlew")) || Files.isRegularFile(__Dir.resolve("gradlew.bat"));
			return new DetectionResult(BuildTool.Gradle, __Wrapper);
		}

		return new DetectionResult(BuildTool.Unrecognized, false);
	}
}
