package com.jpb.bundler.Engine.Build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectDetectorTest {

    @TempDir
    Path TempDir;

    @Test
    void DetectsMavenFromPomXml() throws IOException {
        Files.createFile(TempDir.resolve("pom.xml"));

        DetectionResult __Result = ProjectDetector.Detect(TempDir.toString());

        assertEquals(BuildTool.Maven, __Result.Tool());
        assertFalse(__Result.WrapperPresent());
        assertTrue(__Result.Hint().contains("Maven"));
    }

    @Test
    void DetectsMavenWithWrapper() throws IOException {
        Files.createFile(TempDir.resolve("pom.xml"));
        Files.createFile(TempDir.resolve("mvnw"));

        DetectionResult __Result = ProjectDetector.Detect(TempDir.toString());

        assertEquals(BuildTool.Maven, __Result.Tool());
        assertTrue(__Result.WrapperPresent());
    }

    @Test
    void DetectsGradleFromKts() throws IOException {
        Files.createFile(TempDir.resolve("build.gradle.kts"));

        DetectionResult __Result = ProjectDetector.Detect(TempDir.toString());

        assertEquals(BuildTool.Gradle, __Result.Tool());
        assertFalse(__Result.WrapperPresent());
    }

    @Test
    void DetectsGradleWithWrapper() throws IOException {
        Files.createFile(TempDir.resolve("build.gradle"));
        Files.createFile(TempDir.resolve("gradlew.bat"));

        DetectionResult __Result = ProjectDetector.Detect(TempDir.toString());

        assertEquals(BuildTool.Gradle, __Result.Tool());
        assertTrue(__Result.WrapperPresent());
    }

    @Test
    void UnrecognizedWhenDirectoryIsEmpty() throws IOException {
        Path __Empty = Files.createDirectory(TempDir.resolve("empty"));

        assertEquals(BuildTool.Unrecognized, ProjectDetector.Detect(__Empty.toString()).Tool());
    }

    @Test
    void UnrecognizedWhenPathDoesNotExist() {
        assertEquals(BuildTool.Unrecognized,
                ProjectDetector.Detect(TempDir.resolve("nope").toString()).Tool());
    }

    @Test
    void UnrecognizedWhenInputIsBlankOrNull() {
        assertEquals(BuildTool.Unrecognized, ProjectDetector.Detect("").Tool());
        assertEquals(BuildTool.Unrecognized, ProjectDetector.Detect("  ").Tool());
        assertEquals(BuildTool.Unrecognized, ProjectDetector.Detect(null).Tool());
    }
}
