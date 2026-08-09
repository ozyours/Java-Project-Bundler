package com.jpb.bundler;

import javafx.application.Application;

/**
 * IDE-friendly entry point for running the app directly off the classpath.
 *
 * <p>The JVM refuses to launch a class that extends {@link Application} unless
 * JavaFX is on the module path, so IDEs like IntelliJ IDEA fail on {@link MainApp}
 * with "JavaFX runtime components are missing". This plain class sidesteps that
 * check and hands off to the JavaFX launcher, making the green Run button work
 * with no extra VM options.</p>
 */
public final class Main
{

	private Main()
	{
	}

	public static void main(String[] _Args)
	{
		Application.launch(MainApp.class, _Args);
	}
}
