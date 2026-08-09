package com.jpb.bundler.Engine.Project;

import org.json.JSONObject;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Mutable state of a builder project ({@code .jpb}).
 *
 * <p>The persisted fields (SDD.md §5.2) are plain values serialized to JSON. The
 * project folder itself is <em>not</em> serialized — it is implied by the
 * location of the {@code .jpb} file, which always lives at
 * {@code <project folder>/<project name>.jpb}. Output and icon paths are derived
 * from that folder (see {@link #GetOutputDirectory()} and {@link #GetIconPath()}).</p>
 */
public final class JpbProject implements ISerializable
{

	private String ProjectName = "";
	private String ProjectSourcePath = "";
	private String JvmSourcePath = "";
	private String RustcPath = "";
	private String CustomJvmFlags = "";
	private String HeapSize = "";
	private String MaxHeapSize = "";
	private String StackSize = "";
	private String MaxMetaspaceSize = "";
	private String MainJarName = "";
	private String BuildConfig = "";
	private int MinJavaVersion = 17;

	/**
	 * Whether the build deletes the existing output folder first. Kept out of the
	 * persisted {@code .jpb} file on purpose: it is a per-build safety option, so
	 * it always starts checked (true) instead of remembering a previous choice.
	 */
	private boolean DeleteOutputOnBuild = true;

	/**
	 * Folder that holds this project's {@code .jpb} file. Not serialized.
	 */
	private Path ProjectFolder;

	public String GetProjectName()
	{
		return ProjectName;
	}

	public void SetProjectName(String _ProjectName)
	{
		this.ProjectName = _ProjectName;
	}

	public String GetProjectSourcePath()
	{
		return ProjectSourcePath;
	}

	public void SetProjectSourcePath(String _ProjectSourcePath)
	{
		this.ProjectSourcePath = _ProjectSourcePath;
	}

	public String GetJvmSourcePath()
	{
		return JvmSourcePath;
	}

	public void SetJvmSourcePath(String _JvmSourcePath)
	{
		this.JvmSourcePath = _JvmSourcePath;
	}

	public String GetRustcPath()
	{
		return RustcPath;
	}

	public void SetRustcPath(String _RustcPath)
	{
		this.RustcPath = _RustcPath;
	}

	public String GetCustomJvmFlags()
	{
		return CustomJvmFlags;
	}

	public void SetCustomJvmFlags(String _CustomJvmFlags)
	{
		this.CustomJvmFlags = _CustomJvmFlags;
	}

	public String GetHeapSize()
	{
		return HeapSize;
	}

	public void SetHeapSize(String _HeapSize)
	{
		this.HeapSize = _HeapSize;
	}

	public String GetMaxHeapSize()
	{
		return MaxHeapSize;
	}

	public void SetMaxHeapSize(String _MaxHeapSize)
	{
		this.MaxHeapSize = _MaxHeapSize;
	}

	public String GetStackSize()
	{
		return StackSize;
	}

	public void SetStackSize(String _StackSize)
	{
		this.StackSize = _StackSize;
	}

	public String GetMaxMetaspaceSize()
	{
		return MaxMetaspaceSize;
	}

	public void SetMaxMetaspaceSize(String _MaxMetaspaceSize)
	{
		this.MaxMetaspaceSize = _MaxMetaspaceSize;
	}

	public String GetMainJarName()
	{
		return MainJarName;
	}

	public void SetMainJarName(String _MainJarName)
	{
		this.MainJarName = _MainJarName;
	}

	public String GetBuildConfig()
	{
		return BuildConfig;
	}

	public void SetBuildConfig(String _BuildConfig)
	{
		this.BuildConfig = _BuildConfig;
	}

	public int GetMinJavaVersion()
	{
		return MinJavaVersion;
	}

	public void SetMinJavaVersion(int _MinJavaVersion)
	{
		this.MinJavaVersion = _MinJavaVersion;
	}

	public Path GetProjectFolder()
	{
		return ProjectFolder;
	}

	public void SetProjectFolder(Path _ProjectFolder)
	{
		this.ProjectFolder = _ProjectFolder;
	}

	/**
	 * Serializes the persisted fields (SDD.md §5.2); derived/transient state is skipped.
	 */
	@Override
	public JSONObject OnSerialize()
	{
		JSONObject __Json = new JSONObject();
		__Json.put("projectName", ProjectName);
		__Json.put("projectSourcePath", ProjectSourcePath);
		__Json.put("jvmSourcePath", JvmSourcePath);
		__Json.put("rustcPath", RustcPath);
		__Json.put("customJvmFlags", CustomJvmFlags);
		__Json.put("heapSize", HeapSize);
		__Json.put("maxHeapSize", MaxHeapSize);
		__Json.put("stackSize", StackSize);
		__Json.put("maxMetaspaceSize", MaxMetaspaceSize);
		__Json.put("mainJarName", MainJarName);
		__Json.put("buildConfig", BuildConfig);
		__Json.put("minJavaVersion", MinJavaVersion);
		return __Json;
	}

	/**
	 * Restores the persisted fields. Unknown keys are ignored and missing keys
	 * fall back to the field defaults, so older or newer {@code .jpb} files stay
	 * loadable (forward/backward compatible).
	 */
	@Override
	public void OnDeserialize(JSONObject _Json)
	{
		ProjectName = _Json.optString("projectName", "");
		ProjectSourcePath = _Json.optString("projectSourcePath", "");
		JvmSourcePath = _Json.optString("jvmSourcePath", "");
		RustcPath = _Json.optString("rustcPath", "");
		CustomJvmFlags = _Json.optString("customJvmFlags", "");
		HeapSize = _Json.optString("heapSize", "");
		MaxHeapSize = _Json.optString("maxHeapSize", "");
		StackSize = _Json.optString("stackSize", "");
		MaxMetaspaceSize = _Json.optString("maxMetaspaceSize", "");
		MainJarName = _Json.optString("mainJarName", "");
		BuildConfig = _Json.optString("buildConfig", "");
		MinJavaVersion = _Json.optInt("minJavaVersion", 17);
	}

	public boolean IsDeleteOutputOnBuild()
	{
		return DeleteOutputOnBuild;
	}

	public void SetDeleteOutputOnBuild(boolean _DeleteOutputOnBuild)
	{
		this.DeleteOutputOnBuild = _DeleteOutputOnBuild;
	}

	/**
	 * The {@code .jpb} file living inside the project folder: {@code <folder>/<name>.jpb}.
	 */
	public Path GetJpbFile()
	{
		return Folder().resolve(ProjectName + ".jpb");
	}

	/**
	 * Output bundle directory: {@code <project folder>/output}.
	 */
	public Path GetOutputDirectory()
	{
		return Folder().resolve("output");
	}

	/**
	 * Icon picked up from the project folder: {@code <project folder>/icon.ico}.
	 */
	public Path GetIconPath()
	{
		return Folder().resolve("icon.ico");
	}

	private Path Folder()
	{
		return Objects.requireNonNull(ProjectFolder, "ProjectFolder is not bound — load or create the project first");
	}
}
