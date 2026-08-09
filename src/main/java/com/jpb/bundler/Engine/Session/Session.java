package com.jpb.bundler.Engine.Session;

import com.jpb.bundler.Engine.Project.ISerializable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * State of the last application session: window position, size and the
 * maximized/windowed flag, plus the recently opened projects (most recent first,
 * capped at {@value #MaxRecentProjects}). Persisted via {@link SessionStore} to
 * a JSON file in the local user path ({@code ~/.jpb/session.json}).
 *
 * <p>The bounds are only meaningful when the window is <em>not</em> maximized —
 * a maximized window reports full-screen coordinates that are useless for
 * restoring. {@link #Update(double, double, double, double, boolean)} therefore
 * keeps the last restored bounds while the window is maximized and only records
 * new ones when it is windowed again.</p>
 */
public final class Session implements ISerializable
{
	/**
	 * How many recently opened projects are remembered.
	 */
	public static final int MaxRecentProjects = 5;

	private double X;
	private double Y;
	private double Width;
	private double Height;
	private boolean Maximized;

	/**
	 * Absolute {@code .jpb} file paths, most recent first.
	 */
	private final List<String> RecentProjects = new ArrayList<>();

	/**
	 * True once a real (non-zero) window size has been captured.
	 */
	public boolean HasBounds()
	{
		return Width > 0 && Height > 0;
	}

	/**
	 * Captures the current window state. While {@code maximized} is {@code true}
	 * only the flag is stored — the previous restored bounds are kept so they can
	 * be reapplied when the window returns to windowed mode.
	 */
	public void Update(double _X, double _Y, double _Width, double _Height, boolean _Maximized)
	{
		if (_Maximized)
		{
			this.Maximized = true;
		}
		else
		{
			this.X = _X;
			this.Y = _Y;
			this.Width = _Width;
			this.Height = _Height;
			this.Maximized = false;
		}
	}

	public double GetX()
	{
		return X;
	}

	public void SetX(double _X)
	{
		this.X = _X;
	}

	public double GetY()
	{
		return Y;
	}

	public void SetY(double _Y)
	{
		this.Y = _Y;
	}

	public double GetWidth()
	{
		return Width;
	}

	public void SetWidth(double _Width)
	{
		this.Width = _Width;
	}

	public double GetHeight()
	{
		return Height;
	}

	public void SetHeight(double _Height)
	{
		this.Height = _Height;
	}

	public boolean IsMaximized()
	{
		return Maximized;
	}

	public void SetMaximized(boolean _Maximized)
	{
		this.Maximized = _Maximized;
	}

	/**
	 * Records an opened project at the front of the recent list (deduplicating),
	 * dropping the oldest entry when the list exceeds {@link #MaxRecentProjects}.
	 */
	public void AddRecentProject(Path _JpbFile)
	{
		String __Path = _JpbFile.toAbsolutePath().normalize().toString();
		RecentProjects.remove(__Path);
		RecentProjects.add(0, __Path);
		while (RecentProjects.size() > MaxRecentProjects)
		{
			RecentProjects.remove(RecentProjects.size() - 1);
		}
	}

	/**
	 * Recently opened projects, most recent first (unmodifiable).
	 */
	public List<String> GetRecentProjects()
	{
		return List.copyOf(RecentProjects);
	}

	@Override
	public JSONObject OnSerialize()
	{
		JSONObject __Json = new JSONObject();
		__Json.put("x", X);
		__Json.put("y", Y);
		__Json.put("width", Width);
		__Json.put("height", Height);
		__Json.put("maximized", Maximized);
		__Json.put("recentProjects", new JSONArray(RecentProjects));
		return __Json;
	}

	@Override
	public void OnDeserialize(JSONObject _Json)
	{
		X = _Json.optDouble("x", 0);
		Y = _Json.optDouble("y", 0);
		Width = _Json.optDouble("width", 0);
		Height = _Json.optDouble("height", 0);
		Maximized = _Json.optBoolean("maximized", false);

		RecentProjects.clear();
		JSONArray __Arr = _Json.optJSONArray("recentProjects");
		if (__Arr != null)
		{
			for (int __I = 0; __I < __Arr.length() && RecentProjects.size() < MaxRecentProjects; __I++)
			{
				String __Path = __Arr.optString(__I, "").trim();
				if (!__Path.isEmpty())
				{
					RecentProjects.add(__Path);
				}
			}
		}
	}
}
