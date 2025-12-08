package br.dev.nullbyte.fileutils.Tools.PDF;

import br.dev.nullbyte.fileutils.*;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;

public class RemovePDFPassword {
	public static void run(String... args) {
		File file;
		if (args.length >= 1) {
			file = new File(args[0]);
			if (!file.exists() || !file.isFile())
				throw new RuntimeException("Remove PDF password requires a valid PDF file path.");
		} else {
			file = FileChooserUtils.choosePdf();
			if (file == null)
				throw new AbortException();
		}

		if (!PDFPasswordUtils.requiresPassword(file))
			throw new MessageException("The selected file does not require a password.");

		String password;
		if (args.length >= 2)
			password = args[1];
		else {
			password = PDFPasswordUtils.requestPassword(file);
		}

		try {
			removePdfPassword(file, password);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void removePdfPassword(File file, String password) {
		if (file == null || !file.exists() || !file.isFile())
			throw new RuntimeException("Invalid file: " + (file == null ? "null" : file.getAbsolutePath()));

		FileUtils.LOGGER.info("Removing password from file: " + file.getAbsolutePath());

		File oldFile = new File(file.getAbsolutePath());
		File newFile = new File(file.getParentFile(), "PasswordProtected-" + file.getName());

		if (!file.renameTo(newFile))
			throw new RuntimeException("Failed to rename file: " + file.getAbsolutePath());
		else FileUtils.LOGGER.info("Renamed file to: " + newFile.getAbsolutePath());

		try (PDDocument document = PDDocument.load(newFile, password)) {
			document.setAllSecurityToBeRemoved(true);
			document.save(oldFile);
			FileUtils.LOGGER.severe("Successfully removed pdf file password: " + oldFile.getAbsolutePath());
		} catch (Exception e) {
			FileUtils.LOGGER.severe("Failed to load document with provided password: " + newFile.getAbsolutePath());
			FileUtils.LOGGER.warning("Attempting to revert file name change...");
			if (!newFile.renameTo(oldFile))
				FileUtils.LOGGER.severe("Failed to revert name change.");
			throw new RuntimeException(e);
		}
	}
}
