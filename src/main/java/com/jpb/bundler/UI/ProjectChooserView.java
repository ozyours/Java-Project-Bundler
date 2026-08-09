package com.jpb.bundler.UI;

import atlantafx.base.theme.Styles;
import com.jpb.bundler.Engine.Project.JpbProjectStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Start screen shown before any project is open (SDD.md §4): the app begins with
 * creating a new {@code .jpb} project, opening an existing one, or reopening one
 * of the recently opened projects from the saved session.
 */
public final class ProjectChooserView
{
	/**
	 * Callbacks invoked when the user creates or opens a project.
	 */
	public interface Handler
	{
		void OnCreate(String _ProjectName, Path _ParentFolder);

		void OnOpen(Path _JpbFile);
	}

	public Parent Build(Handler _Handler, List<String> _RecentProjects)
	{
		Label __Title = new Label("Java Project Bundler");
		__Title.getStyleClass().addAll(Styles.TITLE_1, Styles.TEXT_BOLD);

		Label __Subtitle = new Label("Create a new builder project or open an existing .jpb project to start packaging.");
		__Subtitle.setWrapText(true);
		__Subtitle.getStyleClass().addAll(Styles.TEXT_CAPTION, Styles.TEXT_MUTED);

		Button __CreateButton = new Button("Create New Project");
		__CreateButton.setMaxWidth(Double.MAX_VALUE);
		__CreateButton.setPrefHeight(40);
		__CreateButton.getStyleClass().add(Styles.ACCENT);
		__CreateButton.setTooltip(new Tooltip("Create a new folder containing <project name>.jpb"));

		Button __OpenButton = new Button("Open Project\u2026");
		__OpenButton.setMaxWidth(Double.MAX_VALUE);
		__OpenButton.setPrefHeight(40);
		__OpenButton.getStyleClass().add(Styles.BUTTON_OUTLINED);
		__OpenButton.setTooltip(new Tooltip("Open an existing builder project (.jpb) file"));

		__CreateButton.setOnAction(_E -> CreateProject(_Handler));
		__OpenButton.setOnAction(_E ->
		{
			Path __File = OpenJpb(__OpenButton.getScene().getWindow());
			if (__File != null)
			{
				_Handler.OnOpen(__File);
			}
		});

		VBox __Buttons = new VBox(12, __CreateButton, __OpenButton);
		__Buttons.setMaxWidth(320);

		VBox __Card = new VBox(10, __Title, __Subtitle, new Separator(), __Buttons);
		__Card.setPadding(new Insets(28));
		__Card.setMaxWidth(480);
		__Card.getStyleClass().add("chooser-card");

		// Recently opened projects (from the saved session), most recent first;
		// entries whose .jpb file no longer exists are skipped.
		VBox __RecentBox = RecentProjectsBox(_Handler, _RecentProjects);
		if (__RecentBox != null)
		{
			__Card.getChildren().addAll(new Separator(), __RecentBox);
		}

		VBox __Root = new VBox(__Card);
		__Root.setAlignment(Pos.CENTER);
		__Root.setPadding(new Insets(40));
		__Root.getStyleClass().add("root-panel");
		return __Root;
	}

	/**
	 * Builds the recent-projects section, or {@code null} when there is nothing
	 * to show (no recents, or none of the saved files still exist).
	 */
	private VBox RecentProjectsBox(Handler _Handler, List<String> _RecentProjects)
	{
		if (_RecentProjects == null || _RecentProjects.isEmpty())
		{
			return null;
		}
		VBox __Box = new VBox(6);
		Label __Title = new Label("Recent Projects");
		__Title.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_CAPTION);
		__Box.getChildren().add(__Title);

		boolean __Added = false;
		for (String __Path : _RecentProjects)
		{
			if (!IsExistingJpb(__Path))
			{
				continue;
			}
			Button __Item = new Button(__Path);
			__Item.setMaxWidth(Double.MAX_VALUE);
			__Item.setAlignment(Pos.CENTER_LEFT);
			__Item.getStyleClass().add("recent-project");
			__Item.setTooltip(new Tooltip("Open " + __Path));
			__Item.setOnAction(_E -> _Handler.OnOpen(Path.of(__Path)));
			__Box.getChildren().add(__Item);
			__Added = true;
		}
		return __Added ? __Box : null;
	}

	private static boolean IsExistingJpb(String _Path)
	{
		try
		{
			return Files.isRegularFile(Path.of(_Path));
		}
		catch (RuntimeException _E)
		{
			return false; // malformed path — treat as gone
		}
	}

	private void CreateProject(Handler _Handler)
	{
		Dialog<ButtonType> __Dialog = new Dialog<>();
		__Dialog.setTitle("Create New Project");
		__Dialog.setHeaderText("Name the project. A folder and <name>.jpb file will be created.");

		TextField __NameField = new TextField();
		__NameField.setPromptText("e.g. MyCoolApp");

		Label __FolderLabel = new Label(DefaultParentFolder());
		__FolderLabel.setWrapText(true);
		Button __BrowseButton = new Button("Choose\u2026");
		__BrowseButton.getStyleClass().add(Styles.BUTTON_OUTLINED);
		__BrowseButton.setOnAction(_E ->
		{
			DirectoryChooser __Chooser = new DirectoryChooser();
			__Chooser.setTitle("Choose where to create the project folder");
			File __Dir = __Chooser.showDialog(__BrowseButton.getScene().getWindow());
			if (__Dir != null)
			{
				__FolderLabel.setText(__Dir.toPath().toAbsolutePath().normalize().toString());
			}
		});

		HBox __FolderRow = new HBox(10, __FolderLabel, __BrowseButton);
		__FolderRow.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(__FolderLabel, Priority.ALWAYS);

		GridPane __Grid = new GridPane();
		__Grid.setHgap(12);
		__Grid.setVgap(12);
		__Grid.add(new Label("Project name:"), 0, 0);
		__Grid.add(__NameField, 1, 0);
		__Grid.add(new Label("Location:"), 0, 1);
		__Grid.add(__FolderRow, 1, 1);
		GridPane.setHgrow(__NameField, Priority.ALWAYS);

		__Dialog.getDialogPane().setContent(__Grid);
		__Dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		Button __OkButton = (Button) __Dialog.getDialogPane().lookupButton(ButtonType.OK);
		__OkButton.setDisable(true);
		__NameField.textProperty().addListener((_Obs, _OldText, _NewText) -> __OkButton.setDisable(!JpbProjectStore.IsValidProjectName(_NewText)));
		__NameField.requestFocus();

		Optional<ButtonType> __Result = __Dialog.showAndWait();
		if (__Result.isPresent() && __Result.get() == ButtonType.OK)
		{
			_Handler.OnCreate(__NameField.getText().trim(), Path.of(__FolderLabel.getText()));
		}
	}

	/**
	 * Shows the {@code .jpb} open dialog.
	 *
	 * @return the selected file, or {@code null} if the dialog was cancelled
	 */
	public static Path OpenJpb(javafx.stage.Window _Owner)
	{
		FileChooser __Chooser = new FileChooser();
		__Chooser.setTitle("Open Builder Project");
		__Chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Builder Project (*.jpb)", "*.jpb"));
		File __File = __Chooser.showOpenDialog(_Owner);
		return __File != null ? __File.toPath() : null;
	}

	private static String DefaultParentFolder()
	{
		return Path.of(System.getProperty("user.home")).toAbsolutePath().normalize().toString();
	}
}
