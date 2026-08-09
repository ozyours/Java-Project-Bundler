package com.jpb.bundler.Engine.Build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundlerTest {

    @TempDir
    Path Temp;

    private Path Jar(String _Name, boolean _Modular) throws IOException {
        Path __File = Temp.resolve(_Name);
        try (ZipOutputStream __Zip = new ZipOutputStream(Files.newOutputStream(__File))) {
            if (_Modular) {
                __Zip.putNextEntry(new ZipEntry("module-info.class"));
                __Zip.write(new byte[]{0});
                __Zip.closeEntry();
            }
            __Zip.putNextEntry(new ZipEntry("com/example/Foo.class"));
            __Zip.write(new byte[]{0});
            __Zip.closeEntry();
        }
        return __File;
    }

    @Test
    void ModulePathListsOnlyJavafxJarsWithModuleInfo() throws IOException {
        Jar("javafx-graphics-26.0.2-win.jar", true);   // platform jar: classes + module-info
        Jar("javafx-graphics-26.0.2.jar", false);      // empty stub: no module-info
        Jar("javafx-base-26.0.2-win.jar", true);
        Jar("org.json-20260719.jar", true);            // not JavaFX -> stays off the module path
        Jar("commons-io-2.16.1.jar", false);           // not JavaFX, not modular

        String __ModulePath = Bundler.JavaFxModulePath(Temp);

        assertNotNull(__ModulePath);
        assertTrue(__ModulePath.contains("JARs/lib/javafx-base-26.0.2-win.jar"));
        assertTrue(__ModulePath.contains("JARs/lib/javafx-graphics-26.0.2-win.jar"));
        assertTrue(!__ModulePath.contains("javafx-graphics-26.0.2.jar"),
                "the empty stub jar must stay on the classpath, not the module path");
        assertTrue(!__ModulePath.contains("org.json"), "non-JavaFX jars must not be added");
        assertTrue(!__ModulePath.contains("commons-io"), "non-JavaFX jars must not be added");
    }

    @Test
    void ModulePathNullWithoutJavafx() throws IOException {
        Jar("org.json-20260719.jar", true);
        Jar("commons-io-2.16.1.jar", false);

        assertNull(Bundler.JavaFxModulePath(Temp));
    }

    @Test
    void ModulePathNullForMissingDirectory() {
        assertNull(Bundler.JavaFxModulePath(Temp.resolve("does-not-exist")));
    }

    @Test
    void ModulePathIsEmptyListWhenNoModularJavafxJars() throws IOException {
        Jar("javafx-graphics-26.0.2.jar", false); // JavaFX-named but a descriptor-less stub

        assertNull(Bundler.JavaFxModulePath(Temp));
    }

    @Test
    void ReleaseBuildStripsAllDebugInfo() {
        assertEquals(List.of("-Dmaven.compiler.debug=true", "-Dmaven.compiler.debuglevel=none"),
                Bundler.MavenCompilerDebugFlags(false));
    }

    @Test
    void DebugBuildCompilesFullDebugInfo() {
        assertEquals(List.of("-Dmaven.compiler.debug=true", "-Dmaven.compiler.debuglevel=lines,vars,source"),
                Bundler.MavenCompilerDebugFlags(true));
    }

    @Test
    void ModulePathJoinedWithPlatformSeparator() throws IOException {
        Jar("javafx-graphics-26.0.2-win.jar", true);
        Jar("javafx-controls-26.0.2-win.jar", true);

        String __ModulePath = Bundler.JavaFxModulePath(Temp);
        assertNotNull(__ModulePath);
        assertEquals(2, __ModulePath.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)).length);
    }
}
