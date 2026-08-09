package com.jpb.bundler.Engine.Toolchain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdkLocatorTest {

    @TempDir
    Path TempDir;

    private Path FakeJdk(String _Name, String _Version) throws IOException {
        Path __Home = TempDir.resolve(_Name);
        Files.createDirectories(__Home.resolve("bin"));
        Files.writeString(__Home.resolve("bin/java"), "");
        Files.writeString(__Home.resolve("bin/javac"), "");
        Files.writeString(__Home.resolve("release"), "JAVA_VERSION=\"" + _Version + "\"");
        return __Home;
    }

    @Test
    void PicksHighestVersionJdk() throws IOException {
        FakeJdk("jdk-17", "17");
        FakeJdk("jdk-21.0.2", "21.0.2");

        Path __Best = JdkLocator.PickBest(List.of(
                TempDir.resolve("jdk-17"), TempDir.resolve("jdk-21.0.2")));

        assertEquals(TempDir.resolve("jdk-21.0.2"), __Best);
    }

    @Test
    void IgnoresNonJdkDirectories() throws IOException {
        FakeJdk("jdk-17", "17");
        Files.createDirectories(TempDir.resolve("not-a-jdk"));

        Path __Best = JdkLocator.PickBest(List.of(
                TempDir.resolve("not-a-jdk"), TempDir.resolve("jdk-17")));

        assertEquals(TempDir.resolve("jdk-17"), __Best);
    }

    @Test
    void ReturnsNullWhenNoJdkPresent() {
        assertNull(JdkLocator.PickBest(List.of(TempDir.resolve("empty1"), TempDir.resolve("empty2"))));
    }

    @Test
    void DetectsMacLayoutWithContentsHome() throws IOException {
        Path __MacJdk = TempDir.resolve("zulu-21.jdk");
        Path __HomeBin = __MacJdk.resolve("Contents").resolve("Home").resolve("bin");
        Files.createDirectories(__HomeBin);
        Files.writeString(__HomeBin.resolve("java"), "");
        Files.writeString(__HomeBin.resolve("javac"), "");

        Path __Home = JdkLocator.JdkHome(__MacJdk);

        assertEquals(__MacJdk.resolve("Contents/Home"), __Home);
        assertTrue(JdkLocator.IsJdkDir(__Home));
        assertFalse(JdkLocator.IsJdkDir(__MacJdk));
    }

    @Test
    void IsJdkDirAcceptsExeOnWindowsStyleLayouts() throws IOException {
        Path __Home = TempDir.resolve("jdk-21");
        Files.createDirectories(__Home.resolve("bin"));
        Files.writeString(__Home.resolve("bin/java.exe"), "");
        Files.writeString(__Home.resolve("bin/javac.exe"), "");

        assertTrue(JdkLocator.IsJdkDir(__Home));
    }

    @Test
    void RejectsJreOnlyRuntime() throws IOException {
        Path __Jre = TempDir.resolve("jre-17");
        Files.createDirectories(__Jre.resolve("bin"));
        Files.writeString(__Jre.resolve("bin/java"), "");
        // no javac → not a JDK

        assertFalse(JdkLocator.IsJdkDir(__Jre));
    }

    @Test
    void VersionNumbersParseJdkNames() {
        assertEquals(List.of(17, 0, 10, 7), JdkLocator.VersionNumbers(Path.of("jdk-17.0.10+7")));
        assertEquals(List.of(21), JdkLocator.VersionNumbers(Path.of("zulu-21.jdk")));
        assertEquals(List.of(1, 8, 0, 421), JdkLocator.VersionNumbers(Path.of("1.8.0_421")));
    }
}
