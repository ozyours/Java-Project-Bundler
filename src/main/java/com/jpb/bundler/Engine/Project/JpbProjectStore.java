package com.jpb.bundler.Engine.Project;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistence for builder projects ({@code .jpb} JSON files, SDD.md §5.2).
 *
 * <p>Creating a project makes a new folder named after the project
 * ({@code <parent>/<name>/}) and writes {@code <name>.jpb} inside it. Output and
 * icon paths are not stored — they are derived from the project folder.</p>
 */
public final class JpbProjectStore
{

	private JpbProjectStore()
	{
	}

	/**
	 * Creates the project folder and its initial {@code .jpb} file.
	 *
	 * @param _ProjectName   folder and file name of the new project
	 * @param _ParentFolder  directory in which the project folder is created
	 * @return the new project, saved to disk
	 * @throws IOException if the folder already exists or writing fails
	 */
	public static JpbProject Create(String _ProjectName, Path _ParentFolder) throws IOException
	{
		if (!IsValidProjectName(_ProjectName))
		{
			throw new IOException("Invalid project name: \"" + _ProjectName + "\"");
		}
		Path __Folder = _ParentFolder.resolve(_ProjectName).toAbsolutePath().normalize();
		if (Files.exists(__Folder))
		{
			throw new IOException("A project folder already exists at: " + __Folder);
		}
		Files.createDirectories(__Folder);

		JpbProject __Project = new JpbProject();
		__Project.SetProjectName(_ProjectName);
		__Project.SetProjectFolder(__Folder);
		Save(__Project);
		return __Project;
	}

	/**
	 * Writes the project to {@code <project folder>/<project name>.jpb}.
	 */
	public static void Save(JpbProject _Project) throws IOException
	{
		Path __File = _Project.GetJpbFile();
		Files.createDirectories(__File.getParent());
		Files.writeString(__File, _Project.Serialize().toString(2), StandardCharsets.UTF_8);
	}

	/**
	 * Loads a project from a {@code .jpb} file and binds its folder to the file's
	 * parent directory, so derived paths (output, icon) resolve correctly.
	 */
	public static JpbProject Load(Path _JpbFile) throws IOException
	{
		String __Content = Files.readString(_JpbFile, StandardCharsets.UTF_8);
		JpbProject __Project = new JpbProject();
		try
		{
			__Project.Deserialize(new JSONObject(__Content));
		}
		catch (JSONException _E)
		{
			throw new IOException("Invalid project file: " + _JpbFile, _E);
		}
		__Project.SetProjectFolder(_JpbFile.toAbsolutePath().normalize().getParent());
		return __Project;
	}

	/**
	 * Validates a project name for use as a folder and file name.
	 *
	 * @return {@code true} if the name is non-blank, not {@code .}/{@code ..}, and
	 * free of path separators and characters illegal on Windows
	 */
	public static boolean IsValidProjectName(String _Name)
	{
		if (_Name == null || _Name.isBlank())
		{
			return false;
		}
		String __Trimmed = _Name.trim();
		if (__Trimmed.equals(".") || __Trimmed.equals(".."))
		{
			return false;
		}
		return __Trimmed.chars().noneMatch(_C -> _C == '/' || _C == '\\' || _C == ':' || _C == '*' || _C == '?' || _C == '"' || _C == '<' || _C == '>' || _C == '|');
	}
}
