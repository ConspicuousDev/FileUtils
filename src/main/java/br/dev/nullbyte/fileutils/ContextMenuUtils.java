package br.dev.nullbyte.fileutils;

import com.github.sarxos.winreg.HKey;
import com.github.sarxos.winreg.RegistryException;
import com.github.sarxos.winreg.WindowsRegistry;

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
					.label("Split PDF file in 10MB chunks")
					.command("\"" + JavaUtils.getJavaWPath() + "\" -jar \"%APP_PATH%\" --split-pdf \"%FILE_PATH%\"")
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

	private static String getProgIdForExtension(WindowsRegistry registry, String fileExtension) {
		try {
			String userChoicePath = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\" + fileExtension + "\\UserChoice";

			try {
				String progId = registry.readString(HKey.HKCU, userChoicePath, "ProgId");
				if (progId != null && !progId.isEmpty()) {
					FileUtils.LOGGER.fine("Found ProgID from UserChoice: " + progId + " for " + fileExtension);
					return progId;
				}
			} catch (Exception e) {
				FileUtils.LOGGER.fine("No UserChoice ProgID found for " + fileExtension + ", trying traditional method");
			}

			try {
				String progId = registry.readString(HKey.HKCU, "Software\\Classes\\" + fileExtension, "");
				if (progId != null && !progId.isEmpty()) {
					FileUtils.LOGGER.fine("Found ProgID from direct registration: " + progId + " for " + fileExtension);
					return progId;
				}
			} catch (Exception e) {
				FileUtils.LOGGER.fine("No direct registration ProgID found for " + fileExtension);
			}

			try {
				String progId = registry.readString(HKey.HKCR, fileExtension, "");
				if (progId != null && !progId.isEmpty()) {
					FileUtils.LOGGER.fine("Found ProgID from HKCR: " + progId + " for " + fileExtension);
					return progId;
				}
			} catch (Exception e) {
				FileUtils.LOGGER.fine("No HKCR ProgID found for " + fileExtension);
			}

			FileUtils.LOGGER.warning("Could not find ProgID for " + fileExtension);
			return null;

		} catch (Exception e) {
			FileUtils.LOGGER.log(Level.WARNING, "Error getting ProgID for " + fileExtension, e);
			return null;
		}
	}

	public static void setupContextMenuOptions() {
		for (int i = 0; i < contextMenuOptionsList.size(); i++)
			setupContextMenuOption(contextMenuOptionsList.get(i), i);
	}

	public static void removeContextMenuOptions() {
		FileUtils.LOGGER.info("Removing context menu options...");

		try {
			WindowsRegistry registry = WindowsRegistry.getInstance();

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
						String progId = getProgIdForExtension(registry, fileExtension);

						if (progId != null && !progId.isEmpty()) {
							String commandKeyPath = "Software\\Classes\\" + progId + "\\shell\\" + menuId + "\\command";
							registry.deleteKey(HKey.HKCU, commandKeyPath);
							FileUtils.LOGGER.fine("Deleted command key: " + commandKeyPath);

							String shellKeyPath = "Software\\Classes\\" + progId + "\\shell\\" + menuId;
							registry.deleteKey(HKey.HKCU, shellKeyPath);
							FileUtils.LOGGER.fine("Deleted shell key: " + shellKeyPath);
						}

						String commandKeyPath = "Software\\Classes\\" + fileExtension + "\\shell\\" + menuId + "\\command";
						registry.deleteKey(HKey.HKCU, commandKeyPath);

						String shellKeyPath = "Software\\Classes\\" + fileExtension + "\\shell\\" + menuId;
						registry.deleteKey(HKey.HKCU, shellKeyPath);
					} catch (RegistryException e) {
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
			WindowsRegistry registry = WindowsRegistry.getInstance();
			String finalCommand = contextMenuOption.command
					.replaceFirst("%APP_PATH%", Matcher.quoteReplacement(FileUtils.APP_FOLDER + File.separator + FileUtils.JAR_NAME))
					.replaceFirst("%FILE_PATH%", "%1");
			String menuId = FileUtils.APP_NAME + "_" + id;

			for (String fileExtension : contextMenuOption.fileExtensions) {
				FileUtils.LOGGER.info(String.format("Setting up context menu option '%s' for '%s' files", contextMenuOption.label, fileExtension));

				String progId = getProgIdForExtension(registry, fileExtension);
				if (progId == null || progId.isEmpty())
					progId = fileExtension;
				else FileUtils.LOGGER.info("Using ProgID: " + progId + " for " + fileExtension);

				String shellKeyPath = "Software\\Classes\\" + progId + "\\shell\\" + menuId;
				registry.createKey(HKey.HKCU, shellKeyPath);
				registry.writeStringValue(HKey.HKCU, shellKeyPath, "", contextMenuOption.label);
				//registry.writeStringValue(HKey.HKCU, shellKeyPath, "Position", "Top");

				String commandKeyPath = shellKeyPath + "\\command";
				registry.createKey(HKey.HKCU, commandKeyPath);
				registry.writeStringValue(HKey.HKCU, commandKeyPath, "", finalCommand);

				FileUtils.LOGGER.fine(String.format("Context menu option '%s' command for '%s' (ProgID: %s): '%s'", contextMenuOption.label, fileExtension, progId, finalCommand));
			}

			FileUtils.LOGGER.info(String.format("Context menu option '%s' setup completed successfully.", contextMenuOption.label));
		} catch (RegistryException e) {
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
