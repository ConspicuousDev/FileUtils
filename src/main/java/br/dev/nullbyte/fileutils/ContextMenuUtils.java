package br.dev.nullbyte.fileutils;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;

public class ContextMenuUtils {
	public static final List<ContextMenuOption> contextMenuOptionsList = Arrays.asList(
			new ContextMenuOption()
					.fileExtension(".pdf")
					.label("Compress PDF file")
					.command("\"" + JavaUtils.getJavaWPath() + "\" -jar \"%APP_PATH%\" --compress-pdf \"%FILE_PATH%\""),
			new ContextMenuOption()
					.fileExtension(".pdf")
					.label("Split PDF file in 10MB chunks")
					.command("\"" + JavaUtils.getJavaWPath() + "\" -jar \"%APP_PATH%\" --split-pdf \"%FILE_PATH%\""),
			new ContextMenuOption()
					.fileExtension(".pdf")
					.label("Optimize PDF file")
					.command("\"" + JavaUtils.getJavaWPath() + "\" -jar \"%APP_PATH%\" --optimize-pdf \"%FILE_PATH%\""),
			new ContextMenuOption()
					.fileExtension(".pdf")
					.label("Merge PDF with...")
					.command("\"" + JavaUtils.getJavaWPath() + "\" -jar \"%APP_PATH%\" --merge-pdf-with \"%FILE_PATH%\"")
			,
			new ContextMenuOption()
					.fileExtension(".pdf")
					.label("Remove PDF password")
					.command("\"" + JavaUtils.getJavaWPath() + "\" -jar \"%APP_PATH%\" --remove-pdf-password \"%FILE_PATH%\"")
	);

	private static boolean validateContextMenuOptions(ContextMenuOption contextMenuOption) {
		if (contextMenuOption.fileExtensions.isEmpty())
			return false;
		if (contextMenuOption.label == null || contextMenuOption.label.isEmpty())
			return false;
		if (contextMenuOption.command == null || contextMenuOption.command.isEmpty())
			return false;
		return true;
	}

	private static String getProgIdForExtension(String fileExtension) {
		String userChoicePath = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\" + fileExtension + "\\UserChoice";
		if (Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, userChoicePath)) {
			try {
				String progId = Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, userChoicePath, "ProgId");
				if (progId != null && !progId.isEmpty()) {
					FileUtils.LOGGER.fine("Found ProgID from UserChoice: " + progId);
					return progId;
				}
			} catch (Exception e) {
				FileUtils.LOGGER.fine("Failed to read UserChoice for " + fileExtension);
			}
		}

		String userClassPath = "Software\\Classes\\" + fileExtension;
		if (Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, userClassPath)) {
			try {
				String progId = Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, userClassPath, "");
				if (progId != null && !progId.isEmpty()) {
					FileUtils.LOGGER.fine("Found ProgID from HKCU Classes: " + progId);
					return progId;
				}
			} catch (Exception e) {
				FileUtils.LOGGER.fine("Failed to read HKCU Classes for " + fileExtension);
			}
		}

		if (Advapi32Util.registryKeyExists(WinReg.HKEY_CLASSES_ROOT, fileExtension)) {
			try {
				String progId = Advapi32Util.registryGetStringValue(WinReg.HKEY_CLASSES_ROOT, fileExtension, "");
				if (progId != null && !progId.isEmpty()) {
					FileUtils.LOGGER.fine("Found ProgID from HKCR: " + progId);
					return progId;
				}
			} catch (Exception e) {
				FileUtils.LOGGER.fine("Failed to read HKCR for " + fileExtension);
			}
		}

		return null;
	}

	public static void setupContextMenuOptions() {
		for (int i = 0; i < contextMenuOptionsList.size(); i++)
			setupContextMenuOption(contextMenuOptionsList.get(i), i);
	}

	public static void removeContextMenuOptions() {
		FileUtils.LOGGER.info("Removing context menu options...");

		try {
			for (int i = 0; i < contextMenuOptionsList.size(); i++) {
				ContextMenuOption option = contextMenuOptionsList.get(i);

				if (!validateContextMenuOptions(option)) {
					FileUtils.LOGGER.warning("Skipping invalid context menu option during removal.");
					continue;
				}

				String menuId = FileUtils.APP_NAME + "_" + i;

				for (String fileExtension : option.fileExtensions) {
					FileUtils.LOGGER.fine("Removing context menu for " + fileExtension + " files");

					try {
						String progId = getProgIdForExtension(fileExtension);

						if (progId != null && !progId.isEmpty()) {
							String shellKeyPath = "Software\\Classes\\" + progId + "\\shell\\" + menuId;
							String commandKeyPath = shellKeyPath + "\\command";

							Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, commandKeyPath);
							FileUtils.LOGGER.fine("Deleted command key: " + commandKeyPath);
							Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, shellKeyPath);
							FileUtils.LOGGER.fine("Deleted shell key: " + shellKeyPath);
						}

						String shellKeyPath = "Software\\Classes\\" + fileExtension + "\\shell\\" + menuId;
						String commandKeyPath = shellKeyPath + "\\command";

						Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, commandKeyPath);
						FileUtils.LOGGER.fine("Deleted command key: " + commandKeyPath);
						Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, shellKeyPath);
						FileUtils.LOGGER.fine("Deleted shell key: " + shellKeyPath);
					} catch (Exception e) {
						FileUtils.LOGGER.warning("Failed to remove registry key for " + fileExtension + ": " + e.getMessage());
					}
				}
			}
			FileUtils.LOGGER.info("Context menu options removed successfully.");
		} catch (Exception e) {
			FileUtils.LOGGER.log(Level.SEVERE, "Error removing context menu options.", e);
		}
	}

	private static void setupContextMenuOption(ContextMenuOption contextMenuOption, int id) {
		if (!validateContextMenuOptions(contextMenuOption)) {
			FileUtils.LOGGER.warning(String.format("Invalid context menu option: '%s'", contextMenuOption.label));
			return;
		}

		try {
			String finalCommand = contextMenuOption.command
					.replaceFirst("%APP_PATH%", Matcher.quoteReplacement(FileUtils.APP_FOLDER + File.separator + FileUtils.JAR_NAME))
					.replaceFirst("%FILE_PATH%", "%1");
			String menuId = FileUtils.APP_NAME + "_" + id;

			for (String fileExtension : contextMenuOption.fileExtensions) {
				FileUtils.LOGGER.info(String.format("Setting up context menu option '%s' for '%s' files", contextMenuOption.label, fileExtension));

				String progId = getProgIdForExtension(fileExtension);
				if (progId == null || progId.isEmpty())
					progId = fileExtension;
				else FileUtils.LOGGER.info("Using ProgID: " + progId + " for " + fileExtension);

				String progIdPath = "Software\\Classes\\" + progId;
				String shellPath = progIdPath + "\\shell\\";
				String menuKeyPath = shellPath + "\\" + menuId;

				if (!Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, shellPath))
					Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, progIdPath, "shell");

				if (!Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, shellPath + "\\" + menuId))
					Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, shellPath, menuId);

				Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, shellPath + "\\" + menuId, "", contextMenuOption.label);

				if (!Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, menuKeyPath + "\\command"))
					Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, menuKeyPath, "command");

				Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, menuKeyPath + "\\command", "", finalCommand);

				FileUtils.LOGGER.fine(String.format("Context menu option '%s' command for '%s' (ProgID: %s): '%s'", contextMenuOption.label, fileExtension, progId, finalCommand));
			}

			FileUtils.LOGGER.info(String.format("Context menu option '%s' setup completed successfully.", contextMenuOption.label));
		} catch (Exception e) {
			FileUtils.LOGGER.log(Level.SEVERE, String.format("Failed to setup context menu option: '%s'", contextMenuOption.label), e);
		}
	}

	public static class ContextMenuOption {
		private final List<String> fileExtensions = new LinkedList<>();
		private String label = null;
		private String command = null;

		public ContextMenuOption fileExtension(String fileExtension) {
			if (fileExtension == null || fileExtension.isEmpty())
				throw new IllegalArgumentException("File extension cannot be null or empty");
			if (!fileExtension.startsWith("."))
				throw new IllegalArgumentException("File extension must start with '.'");
			this.fileExtensions.add(fileExtension);
			return this;
		}

		public ContextMenuOption label(String label) {
			this.label = label;
			return this;
		}

		public ContextMenuOption command(String command) {
			this.command = command;
			return this;
		}
	}
}
