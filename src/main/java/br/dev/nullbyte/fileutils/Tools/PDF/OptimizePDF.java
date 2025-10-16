package br.dev.nullbyte.fileutils.Tools.PDF;

import br.dev.nullbyte.fileutils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class OptimizePDF {
	public static void run(String... args) {
		File file;
		float downsampleFactor = CompressPDF.DEFAULT_DOWNSAMPLE_FACTOR;
		float jpegQuality = CompressPDF.DEFAULT_JPEG_QUALITY;
		int imageDpi = CompressPDF.DEFAULT_IMAGE_DPI;
		long chunkSizeBytes = SplitPDF.DEFAULT_CHUNK_SIZE_BYTES;

		if (args.length >= 1) {
			file = new File(args[0]);
			if (!file.exists() || !file.isFile() || !file.getName().endsWith(".pdf"))
				throw new RuntimeException("Optimize PDF requires the path to a PDF file.");
		} else throw new RuntimeException("Optimize PDF command requires at least one argument.");

		if (args.length >= 2)
			downsampleFactor = Float.parseFloat(args[1]);

		if (args.length >= 3)
			jpegQuality = Float.parseFloat(args[2]);

		if (args.length >= 4)
			imageDpi = Integer.parseInt(args[3]);

		if (args.length >= 5)
			chunkSizeBytes = Long.parseLong(args[4]);

		try {
			optimizePdf(file, downsampleFactor, jpegQuality, imageDpi, chunkSizeBytes);
		} catch (IOException e) {
			throw new RuntimeException("Error optimizing PDF: " + e.getMessage(), e);
		}
	}

	private static void optimizePdf(File file, float downsampleFactor, float jpegQuality, int imageDpi, long chunkSizeBytes) throws IOException {
		File appFolder = new File(FileUtils.APP_FOLDER);
		if (!appFolder.exists())
			appFolder.mkdirs();

		File copiedFile = new File(appFolder, file.getName());
		Files.copy(file.toPath(), copiedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

		FileUtils.LOGGER.info("Copied PDF to: " + copiedFile.getAbsolutePath());

		String baseFileName = copiedFile.getPath();
		if (baseFileName.toLowerCase().endsWith(".pdf"))
			baseFileName = baseFileName.substring(0, baseFileName.length() - ".pdf".length());

		FileUtils.LOGGER.info("Starting PDF optimization");

		CompressPDF.run(copiedFile.getAbsolutePath(), String.valueOf(downsampleFactor), String.valueOf(jpegQuality), String.valueOf(imageDpi));

		FileUtils.LOGGER.info("Compression complete, starting split");

		File compressedFile = new File(baseFileName + "Compressed.pdf");
		SplitPDF.run(compressedFile.getAbsolutePath(), String.valueOf(chunkSizeBytes));

		FileUtils.LOGGER.info("Moving optimized chunks to original folder");

		File originalFolder = file.getParentFile();
		String originalBaseName = file.getPath();
		if (originalBaseName.toLowerCase().endsWith(".pdf"))
			originalBaseName = originalBaseName.substring(0, originalBaseName.length() - ".pdf".length());

		int chunkNumber = 1;
		File chunkFile = new File(baseFileName + "CompressedChunk" + chunkNumber + ".pdf");
		while (chunkFile.exists()) {
			String newName = originalBaseName + "Optimized" + chunkNumber + ".pdf";
			File newFile = new File(originalFolder, new File(newName).getName());
			Files.move(chunkFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			FileUtils.LOGGER.info("Moved to: " + newFile.getAbsolutePath());
			chunkNumber++;
			chunkFile = new File(baseFileName + "Chunk" + chunkNumber + ".pdf");
		}

		FileUtils.LOGGER.info("Cleaning up temporary files");
		Files.deleteIfExists(copiedFile.toPath());
		Files.deleteIfExists(compressedFile.toPath());

		FileUtils.LOGGER.info("Optimization complete");
	}
}