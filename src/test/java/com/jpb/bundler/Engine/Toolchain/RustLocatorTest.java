package com.jpb.bundler.Engine.Toolchain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RustLocatorTest {

    @TempDir
    Path TempDir;

    @Test
    void ResolveFromPathFindsRustc() throws IOException {
        Path __Bin = TempDir.resolve("bin");
        Files.createDirectories(__Bin);
        String __Exe = RustLocator.ExecutableName("rustc");
        Files.writeString(__Bin.resolve(__Exe), "");

        String __Resolved = RustLocator.ResolveFromPath(
                List.of(TempDir.resolve("nope").toString(), __Bin.toString()), "rustc");

        assertEquals(__Bin.resolve(__Exe).toString(), __Resolved);
    }

    @Test
    void ResolveFromPathSkipsMissingEntriesAndReturnsNull() {
        assertNull(RustLocator.ResolveFromPath(
                List.of(TempDir.resolve("nope1").toString(), TempDir.resolve("nope2").toString()), "rustc"));
        assertNull(RustLocator.ResolveFromPath(List.of(), "rustc"));
    }

    @Test
    void ResolveFromPathIgnoresDirectoriesNamedRustc() throws IOException {
        // A directory is not an executable — only regular files count.
        Files.createDirectories(TempDir.resolve(RustLocator.ExecutableName("rustc")));

        assertNull(RustLocator.ResolveFromPath(List.of(TempDir.toString()), "rustc"));
    }

    @Test
    void IsRustcFileAcceptsOnlyExistingRegularFiles() throws IOException {
        Path __Rustc = TempDir.resolve(RustLocator.ExecutableName("rustc"));
        Files.writeString(__Rustc, "");

        assertTrue(RustLocator.IsRustcFile(__Rustc.toString()));
        assertFalse(RustLocator.IsRustcFile(TempDir.toString())); // directory
        assertFalse(RustLocator.IsRustcFile(TempDir.resolve("missing").toString()));
        assertFalse(RustLocator.IsRustcFile("   "));
        assertFalse(RustLocator.IsRustcFile(null));
    }

    @Test
    void CargoBinDirAlwaysResolvesToABinFolder() {
        Path __Dir = RustLocator.CargoBinDir();

        assertNotNull(__Dir);
        assertEquals("bin", __Dir.getFileName().toString());
    }

    @Test
    void SplitPathKeepsNonBlankEntries() {
        List<String> __Dirs = RustLocator.SplitPath(" /a ; ; /b ");

        assertEquals(List.of("/a", "/b"), __Dirs);
    }
}
