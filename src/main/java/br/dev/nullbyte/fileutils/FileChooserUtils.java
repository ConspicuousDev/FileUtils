package br.dev.nullbyte.fileutils;

import jnafilechooser.api.JnaFileChooser;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FileChooserUtils {
	public static final Filter PDF_FILTER = new Filter("PDF files", "pdf");

	public static File chooseFile(String title, Filter... filters) {
		List<File> files = open(title, false, filters);
		return files != null && !files.isEmpty() ? files.get(0) : null;
	}

	public static File chooseFile(Filter... filters) {
		return chooseFile(null, filters);
	}

	public static File choosePdf() {
		return chooseFile(PDF_FILTER);
	}

	public static List<File> chooseFiles(String title, Filter... filters) {
		List<File> files = open(title, true, filters);
		return files != null ? files : Collections.emptyList();
	}

	public static List<File> chooseFiles(Filter... filters) {
		return chooseFiles(null, filters);
	}

	public static List<File> choosePdfs() {
		return chooseFiles(PDF_FILTER);
	}

	private static List<File> open(String title, boolean multiple, Filter... filters) {
		if (title == null || title.isEmpty())
			title = "Select " + (multiple ? "files" : "file");

		JnaFileChooser fileChooser = new JnaFileChooser();
		fileChooser.setTitle(title);
		fileChooser.setMultiSelectionEnabled(multiple);

		for (Filter filter : filters)
			fileChooser.addFilter(filter.getName(), filter.getExtensions());

		if (fileChooser.showOpenDialog(null)) {
			if (multiple) {
				if (fileChooser.getSelectedFiles() == null)
					return null;
				else
					return Arrays.asList(fileChooser.getSelectedFiles());
			} else {
				if (fileChooser.getSelectedFile() == null)
					return null;
				else
					return Collections.singletonList(fileChooser.getSelectedFile());
			}
		}
		return null;
	}

	public static class Filter {
		private final String name;
		private final String[] extensions;

		public Filter(String name, String... extensions) {
			this.name = name;
			this.extensions = extensions;
		}

		public String getName() {
			return name;
		}

		public String[] getExtensions() {
			return extensions;
		}
	}
}
