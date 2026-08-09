package com.jpb.bundler.UI;

import atlantafx.base.theme.Styles;
import com.jpb.bundler.Engine.Build.DetectionResult;
import com.jpb.bundler.Engine.Build.ProjectDetector;
import com.jpb.bundler.Engine.Project.JpbProject;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Main dashboard (SDD.md §4) shown for one open builder project.
 *
 * <p>The output directory and application icon are derived from the project
 * folder ({@code <folder>/output} and {@code <folder>/icon.ico}), so they are
 * shown read-only. The right-hand column holds the project configuration
 * (minimum Java version, JVM flags, main JAR). Browse buttons pick directories,
 * and the source path change triggers debounced build-engine auto-detection
 * (SDD.md §5.1). The Build action is wired by {@code MainApp} to the packaging
 * engine.</p>
 */
public final class DashboardView
{
	private final JpbProject Project;

	// Project fields (SDD.md §5.2) — icon and output are derived, not stored
	private final TextField ProjectNameField = new TextField();
	private final TextField SourcePathField = new TextField();
	private final TextField JvmPathField = new TextField();
	private final TextField RustcPathField = new TextField();
	private final ComboBox<String> MinJavaVersionBox = new ComboBox<>();
	private final TextField CustomJvmFlagsField = new TextField();
	private final TextField HeapSizeField = new TextField();
	private final TextField MaxHeapSizeField = new TextField();
	private final TextField StackSizeField = new TextField();
	private final TextField MaxMetaspaceSizeField = new TextField();
	private final TextField MainJarNameField = new TextField();
	private final ComboBox<String> BuildConfigBox = new ComboBox<>();

	// Per-build option: wipe the output folder before assembling the new bundle
	private final CheckBox DeleteOutputCheckBox = new CheckBox("Delete output folder on build");

	// Derived project info (read-only)
	private final Label ProjectFolderValue = new Label();
	private final Label IconValue = new Label();

	// Build engine detection line (SDD.md §5.1)
	private final Label DetectionTag = new Label("UNRECOGNIZED");
	private final Label DetectionHint = new Label("Set the source project path to auto-detect the build engine.");

	// Build log
	private final TextArea BuildLogArea = new TextArea();

	// Actions — opening a different project happens from the project chooser
	private final Button CloseButton = new Button("Close Project");
	private final Button SaveButton = new Button("Save");
	private final Button BuildButton = new Button("Build Distribution Package");

	// Browse buttons
	private final Button SourceBrowseButton = new Button("Browse\u2026");
	private final Button JvmBrowseButton = new Button("Browse\u2026");
	private final Button RustcBrowseButton = new Button("Browse\u2026");

	// Debounce so detection runs ~450 ms after the user stops typing (SDD §5.1)
	private final PauseTransition DetectionDelay = new PauseTransition(Duration.millis(450));

	public DashboardView(JpbProject _Project)
	{
		this.Project = _Project;
		SourceBrowseButton.getStyleClass().add(Styles.BUTTON_OUTLINED);
		SourceBrowseButton.setTooltip(new Tooltip("Choose the Maven / Gradle project directory"));
		JvmBrowseButton.getStyleClass().add(Styles.BUTTON_OUTLINED);
		JvmBrowseButton.setTooltip(new Tooltip("Choose the JDK"));
		RustcBrowseButton.getStyleClass().add(Styles.BUTTON_OUTLINED);
		RustcBrowseButton.setTooltip(new Tooltip("Choose the rustc executable"));
	}

	public Parent Build()
	{
		WireActions();
		Populate();
		RefreshDetection();

		VBox __Root = new VBox(18, Header(), new Separator(), Form(), LogSection());
		__Root.setPadding(new Insets(24, 28, 24, 28));
		__Root.getStyleClass().add("root-panel");

		BuildButton.setPrefHeight(44);
		BuildButton.getStyleClass().addAll(Styles.ACCENT, Styles.LARGE);
		BuildButton.setTooltip(new Tooltip("Run the build and assemble the distribution bundle."));

		// The dashboard scrolls vertically when the window is too small, and the
		// content fills the viewport height so the canvas background stays uniform.
		ScrollPane __ScrollPane = new ScrollPane(__Root);
		__ScrollPane.setFitToWidth(true);
		__ScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		__Root.setMinHeight(__ScrollPane.getViewportBounds().getHeight());
		__ScrollPane.viewportBoundsProperty().addListener((_Obs, _OldBounds, _Bounds) -> __Root.setMinHeight(_Bounds.getHeight()));
		return __ScrollPane;
	}

	private HBox Header()
	{
		Label __Title = new Label("Java Project Bundler");
		__Title.getStyleClass().addAll(Styles.TITLE_1, Styles.TEXT_BOLD);

		Label __Subtitle = new Label(Project.GetJpbFile().toString());
		__Subtitle.setWrapText(true);
		__Subtitle.getStyleClass().addAll(Styles.TEXT_CAPTION, Styles.TEXT_MUTED);

		CloseButton.getStyleClass().add(Styles.BUTTON_OUTLINED);
		CloseButton.setTooltip(new Tooltip("Close this project and return to the project chooser"));
		SaveButton.getStyleClass().add(Styles.BUTTON_OUTLINED);
		SaveButton.setTooltip(new Tooltip("Save the current setup to " + Project.GetJpbFile().getFileName()));

		Region __Spacer = new Region();
		HBox.setHgrow(__Spacer, Priority.ALWAYS);

		HBox __Header = new HBox(12, new VBox(2, __Title, __Subtitle), __Spacer, SaveButton, CloseButton, BuildButton);
		__Header.setAlignment(Pos.CENTER_LEFT);
		return __Header;
	}

	private GridPane Form()
	{
		GridPane __Grid = new GridPane();
		__Grid.getStyleClass().add("form-card");
		__Grid.setHgap(28);
		__Grid.setVgap(16);

		ColumnConstraints __Left = new ColumnConstraints();
		__Left.setPercentWidth(60);
		__Left.setHgrow(Priority.ALWAYS);
		ColumnConstraints __Right = new ColumnConstraints();
		__Right.setPercentWidth(40);
		__Grid.getColumnConstraints().addAll(__Left, __Right);

		VBox __LeftColumn = new VBox(16, FieldRow("Project Name", ProjectNameField, null, null, "Set when the project is created; the folder and .jpb file share this name."), InfoRow("Project Folder", ProjectFolderValue, "Location of this project's .jpb file."), SourceRow(), FieldRow("Path to JDK", JvmPathField, "java", JvmBrowseButton, "The JDK used to build the project; jlink produces the bundled runtime (jvm/) from it. " + "Leave empty (java) to use the default from the environment (JAVA_HOME)."), FieldRow("Path to Rust Compiler", RustcPathField, "rustc", RustcBrowseButton, "The rustc executable that compiles the native launcher into the bundle. " + "Leave empty (rustc) to use the default from the environment " + "(RUSTC / PATH / ~/.cargo/bin)."), InfoRow("Application Icon", IconValue, "icon.ico placed in the project folder is bundled automatically."), DeleteOutputRow());

		VBox __RightColumn = new VBox(16, SectionTitle("Project Configuration"), FieldRow("Main JAR Name", MainJarNameField, "(Optional)", null, "File name of the application JAR without the .jar extension, e.g. MyProject " + "=> MyProject.jar. Leave empty to auto-detect the main JAR produced by the build."), FieldRow("Build Configuration", BuildConfigBox, "release", null, "Maven profile / Gradle project property passed to the build. " + "release compiles with no debug info (-g:none); debug compiles with full debug info."), FieldRow("Minimum Java Version", MinJavaVersionBox, "17", null, "Lowest Java version the bundled app must run on; used by jlink."), SectionTitle("JVM Runtime"), FieldRow("Heap Size", HeapSizeField, "e.g. 512m", null, "Initial heap size (-Xms); leave empty to use the JVM default."), FieldRow("Max Heap Size", MaxHeapSizeField, "e.g. 2g", null, "Maximum heap size (-Xmx); leave empty to use the JVM default."), FieldRow("Stack Size", StackSizeField, "e.g. 1m", null, "Per-thread stack size (-Xss); leave empty to use the JVM default."), FieldRow("Max Metaspace Size", MaxMetaspaceSizeField, "e.g. 512m", null, "Metaspace cap (-XX:MaxMetaspaceSize); leave empty to use the JVM default."), FieldRow("Custom JVM Flags", CustomJvmFlagsField, null, null, "Additional free-form flags baked into the launcher config, e.g. " + "-Dfile.encoding=UTF-8. Leave empty for none."));

		__Grid.add(__LeftColumn, 0, 0);
		__Grid.add(__RightColumn, 1, 0);
		return __Grid;
	}

	private VBox FieldRow(String _Label, Control _Input, String _Prompt, Button _Trailing, String _Caption)
	{
		Label __LabelNode = new Label(_Label);
		__LabelNode.getStyleClass().add("form-label");

		if (_Input instanceof TextField __TextField)
		{
			__TextField.setPromptText(_Prompt);
			__TextField.getStyleClass().add(Styles.ROUNDED);
		}
		else if (_Input instanceof ComboBox<?> __Combo)
		{
			__Combo.setPromptText(_Prompt);
			__Combo.getStyleClass().add(Styles.ROUNDED);
		}

		HBox __Row = new HBox(10, _Input);
		HBox.setHgrow(_Input, Priority.ALWAYS);
		if (_Trailing != null)
		{
			__Row.getChildren().add(_Trailing);
		}

		VBox __Box = new VBox(6, __LabelNode, __Row);
		if (_Caption != null)
		{
			Label __CaptionNode = new Label(_Caption);
			__CaptionNode.setWrapText(true);
			__CaptionNode.getStyleClass().addAll(Styles.TEXT_CAPTION, Styles.TEXT_MUTED);
			__Box.getChildren().add(__CaptionNode);
		}
		return __Box;
	}

	private VBox InfoRow(String _Label, Label _Value, String _Caption)
	{
		Label __LabelNode = new Label(_Label);
		__LabelNode.getStyleClass().add("form-label");

		_Value.setWrapText(true);
		_Value.getStyleClass().addAll("info-value", Styles.TEXT_CAPTION);

		VBox __Box = new VBox(6, __LabelNode, _Value);
		if (_Caption != null)
		{
			Label __CaptionNode = new Label(_Caption);
			__CaptionNode.setWrapText(true);
			__CaptionNode.getStyleClass().addAll(Styles.TEXT_CAPTION, Styles.TEXT_MUTED);
			__Box.getChildren().add(__CaptionNode);
		}
		return __Box;
	}

	private Label SectionTitle(String _Text)
	{
		Label __Label = new Label(_Text);
		__Label.getStyleClass().add("column-title");
		return __Label;
	}

	private VBox DeleteOutputRow()
	{
		DeleteOutputCheckBox.setSelected(true); // default: wipe previous output
		DeleteOutputCheckBox.setTooltip(new Tooltip("Delete the existing output folder before assembling the new bundle."));

		Label __CaptionNode = new Label("Deletes the existing output folder before assembling the new bundle. " + "Uncheck to keep previous output files.");
		__CaptionNode.setWrapText(true);
		__CaptionNode.getStyleClass().addAll(Styles.TEXT_CAPTION, Styles.TEXT_MUTED);
		return new VBox(6, DeleteOutputCheckBox, __CaptionNode);
	}

	private VBox SourceRow()
	{
		Label __LabelNode = new Label("Source Project Path");
		__LabelNode.getStyleClass().add("form-label");

		SourcePathField.setPromptText("C:\\path\\to\\source-project");
		SourcePathField.getStyleClass().add(Styles.ROUNDED);

		HBox __Row = new HBox(10, SourcePathField, SourceBrowseButton);
		HBox.setHgrow(SourcePathField, Priority.ALWAYS);

		Label __CaptionNode = new Label("Maven or Gradle project directory.");
		__CaptionNode.getStyleClass().addAll(Styles.TEXT_CAPTION, Styles.TEXT_MUTED);

		Label __Prefix = new Label("Detected:");
		__Prefix.getStyleClass().addAll(Styles.TEXT_CAPTION, Styles.TEXT_MUTED);

		DetectionTag.getStyleClass().addAll("detection-tag", Styles.WARNING);

		DetectionHint.getStyleClass().addAll(Styles.TEXT_CAPTION, Styles.TEXT_MUTED, Styles.TEXT_ITALIC);

		HBox __Detection = new HBox(8, __Prefix, DetectionTag, DetectionHint);
		__Detection.setAlignment(Pos.CENTER_LEFT);

		return new VBox(6, __LabelNode, __Row, __CaptionNode, __Detection);
	}

	private TitledPane LogSection()
	{
		BuildLogArea.setEditable(false);
		BuildLogArea.setWrapText(false);
		BuildLogArea.setPrefRowCount(8);
		BuildLogArea.setPromptText("Ready — configure the project, then press Build Distribution Package.");
		BuildLogArea.getStyleClass().add("log-area");

		TitledPane __Pane = new TitledPane("Build Progress", BuildLogArea);
		__Pane.setExpanded(true);
		__Pane.setAnimated(false);
		return __Pane;
	}

	/**
	 * Wires control actions: browse dialogs and debounced engine detection.
	 */
	private void WireActions()
	{
		SourceBrowseButton.setOnAction(_E -> ChooseDirectory(SourceBrowseButton, "Choose the Maven / Gradle project directory", SourcePathField));
		JvmBrowseButton.setOnAction(_E -> ChooseDirectory(JvmBrowseButton, "Choose the JDK", JvmPathField));
		RustcBrowseButton.setOnAction(_E -> ChooseRustcFile(RustcBrowseButton, "Choose the Rust compiler (rustc)", RustcPathField));

		// Every keystroke cancels the pending check and restarts the timer, so
		// detection only runs once the user has stopped typing (reset on change).
		DetectionDelay.setOnFinished(_E -> RefreshDetection());
		SourcePathField.textProperty().addListener((_Obs, _OldText, _NewText) ->
		{
			DetectionDelay.stop();
			DetectionDelay.playFromStart();
		});
	}

	private void ChooseDirectory(Button _Anchor, String _Title, TextField _Target)
	{
		DirectoryChooser __Chooser = new DirectoryChooser();
		__Chooser.setTitle(_Title);
		String __Current = _Target.getText().trim();
		if (!__Current.isEmpty() && Files.isDirectory(Path.of(__Current)))
		{
			__Chooser.setInitialDirectory(Path.of(__Current).toFile());
		}
		File __Dir = __Chooser.showDialog(_Anchor.getScene().getWindow());
		if (__Dir != null)
		{
			_Target.setText(__Dir.toPath().toAbsolutePath().normalize().toString());
		}
	}

	/**
	 * File chooser for the rustc executable; starts at the current value's folder.
	 */
	private void ChooseRustcFile(Button _Anchor, String _Title, TextField _Target)
	{
		FileChooser __Chooser = new FileChooser();
		__Chooser.setTitle(_Title);
		String __Current = _Target.getText().trim();
		if (!__Current.isEmpty())
		{
			try
			{
				Path __Parent = Path.of(__Current).getParent();
				if (__Parent != null && Files.isDirectory(__Parent))
				{
					__Chooser.setInitialDirectory(__Parent.toFile());
				}
			}
			catch (RuntimeException _Ignored)
			{
				// malformed path — let the chooser start at its default
			}
		}
		File __File = __Chooser.showOpenDialog(_Anchor.getScene().getWindow());
		if (__File != null)
		{
			_Target.setText(__File.toPath().toAbsolutePath().normalize().toString());
		}
	}

	/**
	 * Re-runs build-engine detection against the current source path.
	 */
	private void RefreshDetection()
	{
		DetectionResult __Result = ProjectDetector.Detect(SourcePathField.getText());
		String __Variant = switch (__Result.Tool())
		{
			case Maven -> Styles.ACCENT;
			case Gradle -> Styles.SUCCESS;
			case Unrecognized -> Styles.WARNING;
		};
		SetDetection(__Result.Tool().name(), __Variant);
		DetectionHint.setText(__Result.Hint());
	}

	/**
	 * Copies the project model into the form controls.
	 */
	private void Populate()
	{
		// The name is fixed at creation: the project folder and .jpb file share it.
		ProjectNameField.setText(Project.GetProjectName());
		ProjectNameField.setEditable(false);
		SourcePathField.setText(Project.GetProjectSourcePath());
		// The JDK/rustc fields stay empty to signal "use the default from the
		// environment"; a saved explicit value wins when present. The grayed
		// prompt text ("java" / "rustc") marks that default mode, and the build
		// resolves the actual paths from JAVA_HOME / RUSTC / PATH etc.
		JvmPathField.setText(Project.GetJvmSourcePath());
		RustcPathField.setText(Project.GetRustcPath());
		CustomJvmFlagsField.setText(Project.GetCustomJvmFlags());
		HeapSizeField.setText(Project.GetHeapSize());
		MaxHeapSizeField.setText(Project.GetMaxHeapSize());
		StackSizeField.setText(Project.GetStackSize());
		MaxMetaspaceSizeField.setText(Project.GetMaxMetaspaceSize());
		MainJarNameField.setText(Project.GetMainJarName());

		BuildConfigBox.setEditable(true); // presets + any custom profile name
		BuildConfigBox.getItems().setAll("release", "debug");
		String __Config = Project.GetBuildConfig();
		if (__Config != null && !__Config.isBlank())
		{
			if (!BuildConfigBox.getItems().contains(__Config))
			{
				BuildConfigBox.getItems().add(__Config); // keep saved custom values selectable
			}
			BuildConfigBox.setValue(__Config);
		}
		else
		{
			BuildConfigBox.setValue("release"); // default configuration
		}

		MinJavaVersionBox.getItems().setAll("8", "11", "17", "21");
		String __Min = String.valueOf(Project.GetMinJavaVersion());
		if (!MinJavaVersionBox.getItems().contains(__Min))
		{
			MinJavaVersionBox.getItems().add(__Min);
		}
		MinJavaVersionBox.setValue(__Min);

		ProjectFolderValue.setText(Project.GetProjectFolder().toString());

		Path __Icon = Project.GetIconPath();
		boolean __Found = Files.isRegularFile(__Icon);
		IconValue.setText(__Icon.toString() + (__Found ? "  \u2713 found" : "  \u2014 not present"));
		IconValue.getStyleClass().add(__Found ? "found" : "missing");

		// Transient (never persisted): always starts checked, matching the default.
		DeleteOutputCheckBox.setSelected(Project.IsDeleteOutputOnBuild());
	}

	/**
	 * Copies the form controls back into the project model.
	 */
	public void WriteToProject()
	{
		Project.SetProjectName(ProjectNameField.getText().trim());
		Project.SetProjectSourcePath(SourcePathField.getText().trim());
		Project.SetJvmSourcePath(JvmPathField.getText().trim());
		Project.SetRustcPath(RustcPathField.getText().trim());
		Project.SetCustomJvmFlags(CustomJvmFlagsField.getText().trim());
		Project.SetHeapSize(HeapSizeField.getText().trim());
		Project.SetMaxHeapSize(MaxHeapSizeField.getText().trim());
		Project.SetStackSize(StackSizeField.getText().trim());
		Project.SetMaxMetaspaceSize(MaxMetaspaceSizeField.getText().trim());
		Project.SetMainJarName(MainJarNameField.getText().trim());
		String __Config = BuildConfigBox.getValue();
		Project.SetBuildConfig(__Config == null ? "" : __Config.trim());

		String __Min = MinJavaVersionBox.getValue();
		if (__Min != null)
		{
			try
			{
				Project.SetMinJavaVersion(Integer.parseInt(__Min));
			}
			catch (NumberFormatException _Ignored)
			{
				// keep the previously parsed version
			}
		}
		Project.SetDeleteOutputOnBuild(DeleteOutputCheckBox.isSelected());
	}

	/**
	 * Appends text to the build progress log; adds a trailing newline only when
	 * the text does not already end with one (batched chunks include theirs).
	 */
	public void Log(String _Text)
	{
		BuildLogArea.appendText(_Text);
		if (!_Text.endsWith("\n"))
		{
			BuildLogArea.appendText(System.lineSeparator());
		}
	}

	/**
	 * Swaps the detection tag text and its color variant.
	 *
	 * @param _Text    the tag label, e.g. {@code "MAVEN"}, {@code "GRADLE"} or {@code "UNRECOGNIZED"}
	 * @param _Variant one of {@link Styles#ACCENT}, {@link Styles#SUCCESS} or {@link Styles#WARNING}
	 */
	public void SetDetection(String _Text, String _Variant)
	{
		DetectionTag.setText(_Text);
		DetectionTag.getStyleClass().removeAll(Styles.ACCENT, Styles.SUCCESS, Styles.WARNING, Styles.DANGER);
		DetectionTag.getStyleClass().add(_Variant);
	}

	public JpbProject GetProject()
	{
		return Project;
	}

	public TextField GetProjectNameField()
	{
		return ProjectNameField;
	}

	public TextField GetSourcePathField()
	{
		return SourcePathField;
	}

	public TextField GetJvmPathField()
	{
		return JvmPathField;
	}

	public TextField GetRustcPathField()
	{
		return RustcPathField;
	}

	public ComboBox<String> GetMinJavaVersionBox()
	{
		return MinJavaVersionBox;
	}

	public TextField GetCustomJvmFlagsField()
	{
		return CustomJvmFlagsField;
	}

	public TextField GetHeapSizeField()
	{
		return HeapSizeField;
	}

	public TextField GetMaxHeapSizeField()
	{
		return MaxHeapSizeField;
	}

	public TextField GetStackSizeField()
	{
		return StackSizeField;
	}

	public TextField GetMaxMetaspaceSizeField()
	{
		return MaxMetaspaceSizeField;
	}

	public TextField GetMainJarNameField()
	{
		return MainJarNameField;
	}

	public ComboBox<String> GetBuildConfigBox()
	{
		return BuildConfigBox;
	}

	public CheckBox GetDeleteOutputCheckBox()
	{
		return DeleteOutputCheckBox;
	}

	public Label GetDetectionTag()
	{
		return DetectionTag;
	}

	public Label GetDetectionHint()
	{
		return DetectionHint;
	}

	public TextArea GetBuildLogArea()
	{
		return BuildLogArea;
	}

	public Button GetCloseButton()
	{
		return CloseButton;
	}

	public Button GetSaveButton()
	{
		return SaveButton;
	}

	public Button GetBuildButton()
	{
		return BuildButton;
	}
}
