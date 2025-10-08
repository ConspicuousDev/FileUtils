package br.dev.nullbyte.fileutils;

import br.dev.nullbyte.fileutils.SplitPDF.SplitPDF;

import javax.swing.*;
import java.io.*;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.logging.*;

public class FileUtils {
	public static final boolean DEBUG = false;

	public static final Logger LOGGER = Logger.getLogger(FileUtils.class.getName());
	public static final String APP_NAME = "FileUtils";
	public static final String APP_FOLDER = System.getProperty("user.home") + File.separator + ".fileutils";
	public static final String JAR_NAME = "fileutils.jar";

	static {
		LOGGER.setLevel(Level.ALL);
		LOGGER.setUseParentHandlers(false);

		Formatter customFormatter = new Formatter() {
			private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

			@Override
			public String format(LogRecord record) {
				String baseMessage = String.format("[%s] [%s] [%s] %8s %s%n",
						dateFormat.format(new Date(record.getMillis())),
						Thread.currentThread().getName(),
						record.getLoggerName(),
						record.getLevel().getName(),
						record.getMessage());
				if (record.getThrown() != null) {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					PrintStream ps = new PrintStream(baos);
					record.getThrown().printStackTrace(ps);
					ps.close();
					String stackTrace = baos.toString();
					baseMessage = baseMessage + stackTrace;
				}
				return baseMessage;
			}
		};

		ConsoleHandler handler = new ConsoleHandler() {
			@Override
			protected synchronized void setOutputStream(java.io.OutputStream out) throws SecurityException {
				super.setOutputStream(System.out);
			}
		};
		handler.setFormatter(customFormatter);
		handler.setLevel(Level.ALL);
		LOGGER.addHandler(handler);

		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Failed to set system look and feel", e);
		}
	}

	public static void main(String[] args) {
		if (args.length == 0) {
			LOGGER.info("Initializing application...");
			LOGGER.fine(String.format("APP_NAME='%s'", APP_NAME));
			LOGGER.fine(String.format("APP_FOLDER='%s'", APP_FOLDER));
			LOGGER.fine(String.format("JAR_NAME='%s'", JAR_NAME));

			SwingUtilities.invokeLater(() -> {
				InstallerUI installerUI = new InstallerUI();
				installerUI.setVisible(true);
			});

			LOGGER.info("Application initialized.");
		} else if (args.length == 1 && args[0].equals("--uninstall")) {
			try {
				uninstall(APP_FOLDER);
			} catch (Exception e) {
				LOGGER.log(Level.SEVERE, "Error uninstalling application", e);

				SwingUtilities.invokeLater(() -> {
					JOptionPane.showMessageDialog(
							null,
							"Failed to uninstall: " + e.getMessage(),
							APP_NAME + " Uninstallation Error",
							JOptionPane.ERROR_MESSAGE
					);
					System.exit(1);
				});
			}
		} else {
			String command = args[0];
			if (args[0].startsWith("--"))
				command = command.substring(2);
			final String finalCommand = command;
			final String[] commandArgs = Arrays.stream(args).skip(1).toArray(String[]::new);
			try {
				runModule(finalCommand, commandArgs);
			} catch (Exception e) {
				SwingUtilities.invokeLater(() -> {
					JOptionPane.showMessageDialog(
							null,
							"Error running command '" + finalCommand + "': " + e.getMessage(),
							APP_NAME + " Command Error",
							JOptionPane.ERROR_MESSAGE
					);
					System.exit(1);
				});
				LOGGER.log(Level.SEVERE, "Error running command " + command, e);
			}
		}
	}

	public static boolean checkInstallation(String appFolder, String jarName) {
		File appFolderFile = new File(appFolder);
		if (!appFolderFile.exists()) return false;
		File jarFile = new File(appFolderFile, jarName);
		if (!jarFile.exists()) return false;
		return true;
	}

	public static void install(String appFolder, String jarName) throws Exception {
		LOGGER.info("Starting installation process...");

		File appFolderFile = new File(appFolder);
		if (!appFolderFile.exists()) {
			LOGGER.fine(String.format("Creating app folder: '%s'", appFolderFile.getAbsolutePath()));
			if (!appFolderFile.mkdirs()) {
				LOGGER.severe("App folder failed to be created.");
				throw new RuntimeException("Unable to create folder " + appFolder);
			}
		}

		File jarFile = new File(appFolderFile, jarName);
		if (!jarFile.exists()) {
			LOGGER.fine(String.format("Creating JAR: '%s'", jarFile.getAbsolutePath()));
			String currentJarPath = FileUtils.class
					.getProtectionDomain()
					.getCodeSource()
					.getLocation()
					.getPath();
			currentJarPath = URLDecoder.decode(currentJarPath, "UTF-8");
			File sourceJarFile = new File(currentJarPath);
			LOGGER.fine(String.format("Source JAR: '%s'", sourceJarFile.getAbsolutePath()));

			if (!sourceJarFile.exists()) {
				LOGGER.severe("Source JAR does not exist.");
				throw new RuntimeException("Source JAR " + sourceJarFile.getAbsolutePath() + " does not exist");
			} else if (!sourceJarFile.isFile() || !sourceJarFile.getName().endsWith(".jar")) {
				LOGGER.severe("Source JAR is not a JAR file.");
				throw new RuntimeException("Source JAR " + sourceJarFile.getAbsolutePath() + " is not a JAR file");
			}

			Files.copy(sourceJarFile.toPath(), jarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}

		ContextMenuUtils.setupContextMenuOptions();

		LOGGER.info("Installation finished.");
	}

	public static void uninstall(String appFolder) throws IOException {
		LOGGER.info("Starting uninstallation process...");

		try {
			ContextMenuUtils.removeContextMenuOptions();
		} catch (Exception | ExceptionInInitializerError e) {
			LOGGER.log(Level.SEVERE, "Failed to remove context menu options.", e);
		}

		File appFolderFile = new File(appFolder);
		File batchFile = new File(System.getProperty("java.io.tmpdir"), "uninstall_fileutils.bat");

		try (PrintWriter writer = new PrintWriter(new FileWriter(batchFile))) {
			writer.println("@echo off");
			writer.println("echo Waiting for Java process to exit...");

			writer.println("timeout /t 2 > nul");

			writer.println(":WAIT_LOOP");
			writer.println("echo Attempting to delete FileUtils folder...");
			writer.println("rmdir /S /Q \"" + appFolderFile.getAbsolutePath() + "\" 2>nul");
			writer.println("if exist \"" + appFolderFile.getAbsolutePath() + "\" (");
			writer.println("  echo Files still in use, waiting 1 second...");
			writer.println("  timeout /t 1 > nul");
			writer.println("  goto WAIT_LOOP");
			writer.println(") else (");
			writer.println("  echo FileUtils successfully uninstalled!");
			writer.println(")");

			if (DEBUG) {
				writer.println("echo.");
				writer.println("echo Uninstallation complete. Press any key to close this window...");
				writer.println("pause > nul");
				writer.println("del \"%~f0\"");
			} else {
				writer.println("del \"%~f0\" & exit");
			}
		}

		batchFile.setExecutable(true);

		ProcessBuilder processBuilder;
		if (DEBUG)
			processBuilder = new ProcessBuilder("cmd.exe", "/c", "start", batchFile.getAbsolutePath());
		else
			processBuilder = new ProcessBuilder("cmd.exe", "/c", batchFile.getAbsolutePath());
		processBuilder.redirectOutput(new File("NUL"));
		processBuilder.redirectError(new File("NUL"));
		processBuilder.start();

		LOGGER.info("Uninstallation scheduled. Application will now exit.");
		System.exit(0);
	}

	public static void runModule(String command, String... args) {
		if (command.equals("split-pdf")) {
			SplitPDF.run(args);
		}
	}
}
