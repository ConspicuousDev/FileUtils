package br.dev.nullbyte.fileutils;

import java.io.File;

public class JavaUtils {
	private static String getJavaHome() {
		String javaHome = System.getProperty("java.home");
		File javaHomeFile = new File(javaHome);
		if (javaHomeFile.exists())
			return javaHomeFile.getAbsolutePath();
		return null;
	}

	public static String getJavaWPath() {
		String javaHome = getJavaHome();
		if (javaHome == null)
			return null;
		File javaHomeFile = new File(javaHome);
		File javaWFile = new File(javaHomeFile, "bin" + File.separator + "javaw.exe");
		if (javaWFile.exists())
			return javaWFile.getAbsolutePath();
		return null;
	}
}
