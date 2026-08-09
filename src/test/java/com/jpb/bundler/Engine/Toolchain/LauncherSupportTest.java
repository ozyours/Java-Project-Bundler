package com.jpb.bundler.Engine.Toolchain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherSupportTest {

    @Test
    void ExeNameAppendsExeOnWindows() {
        assertEquals("MyCoolApp.exe", LauncherSupport.ExeName("MyCoolApp", true));
        assertEquals("MyCoolApp", LauncherSupport.ExeName("MyCoolApp", false));
    }

    @Test
    void ExeNameFallsBackWhenProjectNameBlank() {
        assertEquals("app.exe", LauncherSupport.ExeName("   ", true));
        assertEquals("app", LauncherSupport.ExeName(null, false));
    }

    @Test
    void ConfCarriesAllSettings() {
        String __Conf = LauncherSupport.LauncherConf("app.jar", "-Xmx2g", true, "com.example.Main", null, true);

        assertTrue(__Conf.contains("main.jar = app.jar"));
        assertTrue(__Conf.contains("jvm.flags = -Xmx2g"));
        assertTrue(__Conf.contains("use.classpath = true"));
        assertTrue(__Conf.contains("main.class = com.example.Main"));
        assertTrue(__Conf.contains("jvm.module.path = "));
        assertTrue(__Conf.contains("debug.mode = true"));
    }

    @Test
    void ConfDefaultsMainJarAndDisablesClasspathWhenNoMainClass() {
        String __Conf = LauncherSupport.LauncherConf(null, "", true, null, null, false);

        assertTrue(__Conf.contains("main.jar = app-main.jar"));
        assertTrue(__Conf.contains("use.classpath = false"));
        assertTrue(__Conf.contains("main.class = "));
    }

    @Test
    void ConfAllowsClasspathWithoutFlags() {
        String __Conf = LauncherSupport.LauncherConf("app.jar", "", true, "com.example.Main", null, false);

        assertTrue(__Conf.contains("jvm.flags = "));
        assertTrue(__Conf.contains("use.classpath = true"));
    }

    @Test
    void ConfCarriesJavaFxModulePathWhenProvided() {
        String __Conf = LauncherSupport.LauncherConf("app.jar", "", true, "com.example.Main",
                "JARs/lib/javafx-graphics-26.0.2-win.jar", false);

        assertTrue(__Conf.contains("jvm.module.path = JARs/lib/javafx-graphics-26.0.2-win.jar"));
    }

    @Test
    void ConfDefaultsDebugModeOff() {
        String __Conf = LauncherSupport.LauncherConf("app.jar", "", true, "com.example.Main", null, false);

        assertTrue(__Conf.contains("debug.mode = false"));
    }

    @Test
    void JvmFlagsBuildsFromStructuredSizes() {
        assertEquals("-Xms512m -Xmx2g -Xss1m -XX:MaxMetaspaceSize=512m",
                LauncherSupport.JvmFlags("", "512m", "2g", "1m", "512m"));
    }

    @Test
    void JvmFlagsSkipsEmptySizesAndAppendsCustomFlags() {
        assertEquals("-Xmx2g -Dfile.encoding=UTF-8",
                LauncherSupport.JvmFlags("-Dfile.encoding=UTF-8", "", "2g", "  ", null));
    }

    @Test
    void JvmFlagsEmptyWhenNothingConfigured() {
        assertEquals("", LauncherSupport.JvmFlags(null, null, null, null, null));
    }

    @Test
    void LauncherRustSourceShipsAsClasspathResource() throws IOException {
        try (InputStream __In = LauncherSupport.class.getResourceAsStream("/com/jpb/bundler/launcher.rs")) {
            assertNotNull(__In, "launcher.rs must ship as a classpath resource");
            String __Source = new String(__In.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(__Source.contains("find_java"), "launcher must implement JVM discovery");
            assertTrue(__Source.contains("windows_subsystem"), "launcher must hide the console on Windows");
            assertTrue(__Source.contains("main.jar"), "launcher must read the main JAR from config");
            assertTrue(__Source.contains("launcher-error.log"),
                    "launcher must write startup errors to a log file next to the executable");
            assertTrue(__Source.contains("capture_output"),
                    "launcher must capture the failed JVM's startup output");
            assertTrue(__Source.contains("jvm.module.path"),
                    "launcher must read the JavaFX module path from config");
            assertTrue(__Source.contains("--add-modules ALL-MODULE-PATH"),
                    "launcher must resolve JavaFX modules from the module path");
            assertTrue(__Source.contains("JPB_EXTRA_CLASSPATH"),
                    "launcher must extend the classpath from an environment variable");
            assertTrue(__Source.contains("debug.mode"),
                    "launcher must read the debug-mode flag from config");
        }
    }
}
