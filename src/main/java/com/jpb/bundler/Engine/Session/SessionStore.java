package com.jpb.bundler.Engine.Session;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistence for the window session ({@link Session}). The file lives in the
 * local user path — {@code ~/.jpb/session.json} — because window state belongs
 * to the application, not to any single project.
 */
public final class SessionStore
{
	private static final String DirName = ".jpb";
	private static final String FileName = "session.json";

	private SessionStore()
	{
	}

	/**
	 * The session file in the local user path: {@code <user.home>/.jpb/session.json}.
	 */
	public static Path File()
	{
		return Path.of(System.getProperty("user.home"), DirName, FileName);
	}

	/**
	 * Loads the session from the local user path.
	 *
	 * @return the saved session, or an empty default session when no session file
	 * exists yet
	 * @throws IOException if the file exists but cannot be read or parsed
	 */
	public static Session Load() throws IOException
	{
		return Load(File());
	}

	/**
	 * Testable variant reading from an explicit path.
	 */
	public static Session Load(Path _File) throws IOException
	{
		Session __Session = new Session();
		if (!Files.isRegularFile(_File))
		{
			return __Session;
		}
		String __Content = Files.readString(_File, StandardCharsets.UTF_8);
		try
		{
			__Session.Deserialize(new JSONObject(__Content));
		}
		catch (JSONException _E)
		{
			throw new IOException("Invalid session file: " + _File, _E);
		}
		return __Session;
	}

	/**
	 * Writes the session to the local user path.
	 */
	public static void Save(Session _Session) throws IOException
	{
		Save(_Session, File());
	}

	/**
	 * Testable variant writing to an explicit path.
	 */
	public static void Save(Session _Session, Path _File) throws IOException
	{
		Files.createDirectories(_File.getParent());
		Files.writeString(_File, _Session.Serialize().toString(2), StandardCharsets.UTF_8);
	}
}
