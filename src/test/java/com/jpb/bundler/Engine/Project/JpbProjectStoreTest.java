package com.jpb.bundler.Engine.Project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JpbProjectStoreTest {

    @TempDir
    Path TempDir;

    @Test
    void CreateMakesProjectFolderAndJpbFile() throws Exception {
        JpbProject __Project = JpbProjectStore.Create("MyCoolApp", TempDir);

        Path __Folder = TempDir.resolve("MyCoolApp");
        assertTrue(Files.isDirectory(__Folder));
        assertTrue(Files.isRegularFile(__Folder.resolve("MyCoolApp.jpb")));

        assertEquals(__Folder, __Project.GetProjectFolder());
        assertEquals(__Folder.resolve("MyCoolApp.jpb"), __Project.GetJpbFile());
        assertEquals(__Folder.resolve("output"), __Project.GetOutputDirectory());
        assertEquals(__Folder.resolve("icon.ico"), __Project.GetIconPath());
        assertEquals(17, __Project.GetMinJavaVersion());
    }

    @Test
    void SaveAndLoadRoundTrip() throws Exception {
        JpbProject __Project = JpbProjectStore.Create("App", TempDir);
        __Project.SetProjectSourcePath("C:/src/app");
        __Project.SetJvmSourcePath("C:/jdk/jdk-21");
        __Project.SetRustcPath("C:/Users/me/.cargo/bin/rustc.exe");
        __Project.SetCustomJvmFlags("-Xmx2g");
        __Project.SetHeapSize("512m");
        __Project.SetMaxHeapSize("2g");
        __Project.SetStackSize("1m");
        __Project.SetMaxMetaspaceSize("512m");
        __Project.SetMainJarName("app.jar");
        __Project.SetBuildConfig("release");
        __Project.SetMinJavaVersion(21);
        JpbProjectStore.Save(__Project);

        JpbProject __Loaded = JpbProjectStore.Load(__Project.GetJpbFile());
        assertEquals("App", __Loaded.GetProjectName());
        assertEquals("C:/src/app", __Loaded.GetProjectSourcePath());
        assertEquals("C:/jdk/jdk-21", __Loaded.GetJvmSourcePath());
        assertEquals("C:/Users/me/.cargo/bin/rustc.exe", __Loaded.GetRustcPath());
        assertEquals("-Xmx2g", __Loaded.GetCustomJvmFlags());
        assertEquals("512m", __Loaded.GetHeapSize());
        assertEquals("2g", __Loaded.GetMaxHeapSize());
        assertEquals("1m", __Loaded.GetStackSize());
        assertEquals("512m", __Loaded.GetMaxMetaspaceSize());
        assertEquals("app.jar", __Loaded.GetMainJarName());
        assertEquals("release", __Loaded.GetBuildConfig());
        assertEquals(21, __Loaded.GetMinJavaVersion());
        assertEquals(FolderOf(__Project), __Loaded.GetProjectFolder());
    }

    @Test
    void DeleteOutputOnBuildDefaultsTrueAndIsNotPersisted() throws Exception {
        JpbProject __Project = JpbProjectStore.Create("App", TempDir);
        assertTrue(__Project.IsDeleteOutputOnBuild(), "the per-build toggle must default to checked");

        // The choice is per-build only: it must not leak into the .jpb file, so
        // reopening the project always starts checked again.
        __Project.SetDeleteOutputOnBuild(false);
        JpbProjectStore.Save(__Project);
        JpbProject __Loaded = JpbProjectStore.Load(__Project.GetJpbFile());
        assertTrue(__Loaded.IsDeleteOutputOnBuild(), "flag must not be persisted");
    }

    @Test
    void LoadBindsFolderFromJpbLocation() throws Exception {
        JpbProject __Project = JpbProjectStore.Create("App", TempDir);
        Path __Moved = TempDir.resolve("elsewhere").resolve("App.jpb");
        Files.createDirectories(__Moved.getParent());
        Files.move(__Project.GetJpbFile(), __Moved);

        JpbProject __Loaded = JpbProjectStore.Load(__Moved);
        assertEquals(__Moved.getParent(), __Loaded.GetProjectFolder());
        assertEquals(__Moved.getParent().resolve("output"), __Loaded.GetOutputDirectory());
    }

    @Test
    void CreateRefusesExistingFolder() throws Exception {
        JpbProjectStore.Create("App", TempDir);
        assertThrows(IOException.class, () -> JpbProjectStore.Create("App", TempDir));
    }

    @Test
    void CreateRefusesInvalidName() {
        assertThrows(IOException.class, () -> JpbProjectStore.Create("bad/name", TempDir));
        assertThrows(IOException.class, () -> JpbProjectStore.Create("bad:name", TempDir));
    }

    @Test
    void IsValidProjectNameRules() {
        assertTrue(JpbProjectStore.IsValidProjectName("MyCoolApp"));
        assertTrue(JpbProjectStore.IsValidProjectName("my-app_2"));
        assertFalse(JpbProjectStore.IsValidProjectName(""));
        assertFalse(JpbProjectStore.IsValidProjectName("   "));
        assertFalse(JpbProjectStore.IsValidProjectName("a/b"));
        assertFalse(JpbProjectStore.IsValidProjectName("a\\b"));
        assertFalse(JpbProjectStore.IsValidProjectName("a:b"));
        assertFalse(JpbProjectStore.IsValidProjectName("."));
        assertFalse(JpbProjectStore.IsValidProjectName(".."));
    }

    private static Path FolderOf(JpbProject _Project) {
        return _Project.GetJpbFile().getParent();
    }
}
