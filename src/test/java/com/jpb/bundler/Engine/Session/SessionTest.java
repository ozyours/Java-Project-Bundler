package com.jpb.bundler.Engine.Session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTest {

    @TempDir
    Path TempDir;

    @Test
    void UpdateWhileMaximizedKeepsPreviousBounds() {
        Session __Session = new Session();
        __Session.Update(10, 20, 800, 600, false);

        // Maximizing reports full-screen coordinates — they must not overwrite
        // the restored bounds.
        __Session.Update(0, 0, 1920, 1080, true);

        assertEquals(10, __Session.GetX());
        assertEquals(20, __Session.GetY());
        assertEquals(800, __Session.GetWidth());
        assertEquals(600, __Session.GetHeight());
        assertTrue(__Session.IsMaximized());
    }

    @Test
    void UpdateWhileWindowedStoresBoundsAndClearsMaximized() {
        Session __Session = new Session();
        __Session.Update(0, 0, 1920, 1080, true);
        __Session.Update(50, 60, 900, 700, false);

        assertEquals(50, __Session.GetX());
        assertEquals(60, __Session.GetY());
        assertEquals(900, __Session.GetWidth());
        assertEquals(700, __Session.GetHeight());
        assertFalse(__Session.IsMaximized());
    }

    @Test
    void FreshSessionHasNoBounds() {
        Session __Session = new Session();
        assertFalse(__Session.HasBounds());
        assertFalse(__Session.IsMaximized());
    }

    @Test
    void HasBoundsAfterNonZeroSizeCaptured() {
        Session __Session = new Session();
        __Session.Update(1, 1, 640, 480, false);
        assertTrue(__Session.HasBounds());
    }

    @Test
    void SerializeContainsWindowStateAndRecentsKeys() {
        Session __Session = new Session();
        __Session.Update(1, 2, 800, 600, true);

        var __Keys = __Session.OnSerialize().keySet();
        assertEquals(java.util.Set.of("x", "y", "width", "height", "maximized", "recentProjects"),
                __Keys);
    }

    @Test
    void AddRecentProjectKeepsMostRecentFirst() {
        Session __Session = new Session();
        __Session.AddRecentProject(Path.of("C:/p/a.jpb"));
        __Session.AddRecentProject(Path.of("C:/p/b.jpb"));

        assertEquals(java.util.List.of(Path("C:/p/b.jpb"), Path("C:/p/a.jpb")),
                __Session.GetRecentProjects());
    }

    @Test
    void AddRecentProjectDeduplicatesAndMovesToFront() {
        Session __Session = new Session();
        __Session.AddRecentProject(Path.of("C:/p/a.jpb"));
        __Session.AddRecentProject(Path.of("C:/p/b.jpb"));
        __Session.AddRecentProject(Path.of("C:/p/a.jpb")); // reopen A

        assertEquals(java.util.List.of(Path("C:/p/a.jpb"), Path("C:/p/b.jpb")),
                __Session.GetRecentProjects());
    }

    @Test
    void AddRecentProjectCapsAtFiveDroppingOldest() {
        Session __Session = new Session();
        for (int __I = 1; __I <= 6; __I++) {
            __Session.AddRecentProject(Path.of("C:/p/proj" + __I + ".jpb"));
        }

        assertEquals(Session.MaxRecentProjects, __Session.GetRecentProjects().size());
        assertFalse(__Session.GetRecentProjects().contains(Path("C:/p/proj1.jpb")),
                "the oldest project must be dropped");
        assertEquals(Path("C:/p/proj6.jpb"), __Session.GetRecentProjects().get(0));
    }

    @Test
    void RecentProjectsSurviveRoundTrip() throws IOException {
        Path __File = TempDir.resolve("session.json");

        Session __Session = new Session();
        __Session.AddRecentProject(Path.of("C:/p/a.jpb"));
        __Session.AddRecentProject(Path.of("C:/p/b.jpb"));
        __Session.AddRecentProject(Path.of("C:/p/c.jpb"));
        SessionStore.Save(__Session, __File);

        Session __Loaded = SessionStore.Load(__File);
        assertEquals(java.util.List.of(Path("C:/p/c.jpb"), Path("C:/p/b.jpb"), Path("C:/p/a.jpb")),
                __Loaded.GetRecentProjects());
    }

    /** Normalizes a path the same way {@link Session#AddRecentProject} does. */
    private static String Path(String _P) {
        return Path.of(_P).toAbsolutePath().normalize().toString();
    }

    @Test
    void LoadWithoutRecentsKeyReturnsEmptyList() throws IOException {
        Path __File = TempDir.resolve("session.json");
        Files.writeString(__File, "{\"x\": 1, \"y\": 2, \"width\": 3, \"height\": 4, "
                + "\"maximized\": false}");

        Session __Loaded = SessionStore.Load(__File);
        assertTrue(__Loaded.GetRecentProjects().isEmpty());
    }

    @Test
    void SaveAndLoadRoundTrip() throws IOException {
        Path __File = TempDir.resolve("session.json");

        Session __Session = new Session();
        __Session.Update(42, 24, 1024, 768, false);
        SessionStore.Save(__Session, __File);

        Session __Loaded = SessionStore.Load(__File);
        assertEquals(42, __Loaded.GetX());
        assertEquals(24, __Loaded.GetY());
        assertEquals(1024, __Loaded.GetWidth());
        assertEquals(768, __Loaded.GetHeight());
        assertFalse(__Loaded.IsMaximized());
    }

    @Test
    void MaximizedFlagSurvivesRoundTrip() throws IOException {
        Path __File = TempDir.resolve("session.json");

        Session __Session = new Session();
        __Session.Update(1, 1, 800, 600, false);
        __Session.Update(0, 0, 1920, 1080, true);
        SessionStore.Save(__Session, __File);

        Session __Loaded = SessionStore.Load(__File);
        assertTrue(__Loaded.IsMaximized());
        assertEquals(800, __Loaded.GetWidth()); // restored bounds, not the maximized ones
        assertEquals(600, __Loaded.GetHeight());
    }

    @Test
    void LoadMissingFileReturnsDefaults() throws IOException {
        Session __Session = SessionStore.Load(TempDir.resolve("nope.json"));
        assertFalse(__Session.HasBounds());
        assertFalse(__Session.IsMaximized());
    }

    @Test
    void LoadMalformedFileThrows() throws IOException {
        Path __File = TempDir.resolve("session.json");
        Files.writeString(__File, "not json {");

        assertThrows(IOException.class, () -> SessionStore.Load(__File));
    }
}
