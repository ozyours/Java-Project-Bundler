package com.jpb.bundler;

import atlantafx.base.theme.PrimerLight;
import com.jpb.bundler.Engine.Build.Bundler;
import com.jpb.bundler.Engine.Project.JpbProject;
import com.jpb.bundler.Engine.Project.JpbProjectStore;
import com.jpb.bundler.Engine.Session.Session;
import com.jpb.bundler.Engine.Session.SessionStore;
import com.jpb.bundler.UI.DashboardView;
import com.jpb.bundler.UI.ProjectChooserView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Entry point of the Java Project Bundler desktop application.
 *
 * <p>Applies the AtlantaFX {@code PrimerLight} theme and drives the project-first
 * workflow: the app starts at a project chooser (create or open a {@code .jpb}
 * project) and switches to the dashboard once a project is open.</p>
 */
public final class MainApp extends Application {

    private Stage Stage;

    /** Window state of the last session (position, size, maximized). */
    private Session Session = new Session();

    @Override
    public void start(Stage _Stage) {
        this.Stage = _Stage;
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        _Stage.setTitle("Java Project Bundler");
        _Stage.setMinWidth(640);
        _Stage.setMinHeight(440);
        RestoreSession();
        BindSessionListeners();
        _Stage.setOnCloseRequest(_E -> SaveSession());
        ShowChooser();
    }

    /** Reapplies the saved window position, size and maximized state. */
    private void RestoreSession() {
        try {
            Session = SessionStore.Load();
        } catch (IOException _E) {
            Session = new Session(); // corrupt or unreadable session — start fresh
        }
        if (Session.HasBounds()) {
            Stage.setX(Session.GetX());
            Stage.setY(Session.GetY());
            Stage.setWidth(Session.GetWidth());
            Stage.setHeight(Session.GetHeight());
        }
        if (Session.IsMaximized()) {
            Stage.setMaximized(true);
        }
    }

    /**
     * Feeds every window change into the session. Bounds are only recorded while
     * the window is not maximized — {@link Session#Update} applies that rule, so
     * maximized (full-screen) coordinates never overwrite the restored bounds.
     */
    private void BindSessionListeners() {
        Stage.xProperty().addListener((_Obs, _OldV, _NewV) -> CaptureWindowState());
        Stage.yProperty().addListener((_Obs, _OldV, _NewV) -> CaptureWindowState());
        Stage.widthProperty().addListener((_Obs, _OldV, _NewV) -> CaptureWindowState());
        Stage.heightProperty().addListener((_Obs, _OldV, _NewV) -> CaptureWindowState());
        Stage.maximizedProperty().addListener((_Obs, _OldV, _NewV) -> CaptureWindowState());
    }

    private void CaptureWindowState() {
        Session.Update(Stage.getX(), Stage.getY(), Stage.getWidth(), Stage.getHeight(),
                Stage.isMaximized());
    }

    /** Persists the window state to the local user path; failures are non-fatal. */
    private void SaveSession() {
        try {
            SessionStore.Save(Session);
        } catch (IOException _E) {
            System.err.println("Could not save window session: " + _E.getMessage());
        }
    }

    private void ShowChooser() {
        ProjectChooserView __Chooser = new ProjectChooserView();
        ProjectChooserView.Handler __Handler = new ProjectChooserView.Handler() {
            @Override
            public void OnCreate(String _ProjectName, Path _ParentFolder) {
                CreateProject(_ProjectName, _ParentFolder);
            }

            @Override
            public void OnOpen(Path _JpbFile) {
                OpenProject(_JpbFile);
            }
        };
        Parent __Root = __Chooser.Build(__Handler, Session.GetRecentProjects());

        Stage.setMinWidth(640);
        Stage.setMinHeight(440);
        Stage.setScene(Scene(__Root, 760, 460));
        Stage.show();
    }

    private void CreateProject(String _ProjectName, Path _ParentFolder) {
        try {
            ShowDashboard(JpbProjectStore.Create(_ProjectName, _ParentFolder));
        } catch (IOException _E) {
            ShowError("Could not create project", _E);
        }
    }

    private void OpenProject(Path _JpbFile) {
        try {
            ShowDashboard(JpbProjectStore.Load(_JpbFile));
        } catch (IOException _E) {
            ShowError("Could not open project", _E);
        }
    }

    private void ShowDashboard(JpbProject _Project) {
        // Track the project in the recent list and persist right away, so it
        // survives even if the app is killed before a clean close.
        Session.AddRecentProject(_Project.GetJpbFile());
        SaveSession();

        DashboardView __View = new DashboardView(_Project);
        __View.GetSaveButton().setOnAction(_E -> SaveProject(__View, _Project));
        __View.GetCloseButton().setOnAction(_E -> ShowChooser());
        __View.GetBuildButton().setOnAction(_E -> BuildProject(__View, _Project));

        Stage.setMinWidth(720);
        Stage.setMinHeight(720);
        Stage.setScene(Scene(__View.Build(), 880, 900));
        Stage.show();
    }

    /** Runs the packaging pipeline (SDD §7) off the FX thread, streaming to the log. */
    private void BuildProject(DashboardView _View, JpbProject _Project) {
        // Sync the form into the model before the worker thread reads it (SDD §7 step 1).
        _View.WriteToProject();
        _View.GetBuildButton().setDisable(true);
        _View.Log("Build started.");

        Task<Void> __Task = new Task<>() {
            @Override
            protected Void call() {
                // Batch log lines so a chatty build cannot flood the FX thread.
                BatchedLog __Sink = new BatchedLog(_Chunk -> Platform.runLater(() -> _View.Log(_Chunk)));
                try {
                    Bundler.Build(_Project, __Sink);
                    return null;
                } catch (Exception _E) {
                    throw new RuntimeException(_E.getMessage(), _E);
                } finally {
                    __Sink.Flush();
                }
            }
        };
        __Task.setOnSucceeded(_E -> {
            _View.GetBuildButton().setDisable(false);
            _View.Log("Build finished.");
        });
        __Task.setOnFailed(_E -> {
            _View.GetBuildButton().setDisable(false);
            Throwable __Error = __Task.getException();
            String __Message = __Error != null && __Error.getMessage() != null
                    ? __Error.getMessage() : "Unknown error";
            _View.Log("Build failed: " + __Message);
            ShowError("Build failed", __Error instanceof Exception __Ex ? __Ex : new RuntimeException(__Message));
        });

        Thread __Thread = new Thread(__Task, "jpb-build");
        __Thread.setDaemon(true);
        __Thread.start();
    }

    /**
     * Coalesces engine output into chunks so the FX thread only handles a handful
     * of updates per second instead of one per log line.
     */
    private static final class BatchedLog implements Consumer<String> {
        private static final int MaxLines = 50;
        private static final long MaxAgeNanos = 200_000_000L; // 200 ms

        private final Consumer<String> Sink;
        private final StringBuilder Buffer = new StringBuilder();
        private int Lines;
        private long LastFlush = System.nanoTime();

        BatchedLog(Consumer<String> _Sink) {
            this.Sink = _Sink;
        }

        @Override
        public void accept(String _Line) {
            Buffer.append(_Line).append(System.lineSeparator());
            Lines++;
            if (Lines >= MaxLines || System.nanoTime() - LastFlush >= MaxAgeNanos) {
                Flush();
            }
        }

        /** Delivers any buffered lines; call from the worker thread when the build ends. */
        void Flush() {
            if (Buffer.isEmpty()) {
                return;
            }
            String __Chunk = Buffer.toString();
            Buffer.setLength(0);
            Lines = 0;
            LastFlush = System.nanoTime();
            Platform.runLater(() -> Sink.accept(__Chunk));
        }
    }

    private void SaveProject(DashboardView _View, JpbProject _Project) {
        try {
            _View.WriteToProject();
            JpbProjectStore.Save(_Project);
            _View.Log("Project saved to " + _Project.GetJpbFile());
        } catch (IOException _E) {
            ShowError("Could not save project", _E);
        }
    }

    private void ShowError(String _Title, Exception _E) {
        Alert __Alert = new Alert(Alert.AlertType.ERROR, _E.getMessage(), ButtonType.OK);
        __Alert.setTitle(_Title);
        __Alert.setHeaderText(_Title);
        __Alert.initOwner(Stage);
        __Alert.showAndWait();
    }

    private Scene Scene(Parent _Root, double _Width, double _Height) {
        Scene __Scene = new Scene(_Root, _Width, _Height);
        __Scene.getStylesheets().add(
                // Note: resource lives under UI/ (matching the package). Resource
                // lookups inside a JAR are case-sensitive, so the case here must
                // match the directory exactly — a mismatch is masked on Windows/macOS
                // (case-insensitive filesystems) but breaks packaged bundles.
                Objects.requireNonNull(MainApp.class.getResource("UI/dashboard.css"),
                        "dashboard.css resource is missing").toExternalForm());
        return __Scene;
    }

    public static void main(String[] _Args) {
        launch(_Args);
    }
}
