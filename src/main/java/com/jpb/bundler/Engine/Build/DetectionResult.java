package com.jpb.bundler.Engine.Build;

/**
 * Result of probing a source directory for a build engine (SDD.md §5.1).
 */
public record DetectionResult(BuildTool Tool, boolean WrapperPresent)
{
	/**
	 * User-facing hint shown next to the detection tag.
	 */
	public String Hint()
	{
		return switch (Tool)
		{
			case Maven ->
					WrapperPresent ? "Maven project found (pom.xml + wrapper). Build uses ./mvnw." : "Maven project found (pom.xml). No ./mvnw wrapper — uses system Maven.";
			case Gradle ->
					WrapperPresent ? "Gradle project found (build.gradle + wrapper). Build uses ./gradlew." : "Gradle project found (build.gradle). No ./gradlew wrapper — uses system Gradle.";
			case Unrecognized -> "No pom.xml or build.gradle(.kts) found in the source folder.";
		};
	}
}
