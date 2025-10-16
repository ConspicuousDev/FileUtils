package br.dev.nullbyte.fileutils.Tools.PDF;

import br.dev.nullbyte.fileutils.FileUtils;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CompressPDF {
	public static final float DEFAULT_DOWNSAMPLE_FACTOR = 0.75f;
	public static final float DEFAULT_JPEG_QUALITY = 0.5f;
	public static final int DEFAULT_IMAGE_DPI = 96;

	public static void run(String... args) {
		File file;
		float downsampleFactor = DEFAULT_DOWNSAMPLE_FACTOR;
		float jpegQuality = DEFAULT_JPEG_QUALITY;
		int imageDpi = DEFAULT_IMAGE_DPI;

		if (args.length >= 1) {
			file = new File(args[0]);
			if (!file.exists() || !file.isFile() || !file.getName().endsWith(".pdf"))
				throw new RuntimeException("Compress PDF requires the path to a PDF file.");
		} else throw new RuntimeException("Compress PDF command requires at least one argument.");

		if (args.length >= 2)
			downsampleFactor = Float.parseFloat(args[1]);

		if (args.length >= 3)
			jpegQuality = Float.parseFloat(args[2]);

		if (args.length >= 4)
			imageDpi = Integer.parseInt(args[3]);

		try {
			compressPdf(file, downsampleFactor, jpegQuality, imageDpi);
		} catch (IOException e) {
			throw new RuntimeException("Error compressing PDF: " + e.getMessage(), e);
		}
	}

	public static void compressPdf(File pdfFile, float downsampleFactor, float jpegQuality, int targetDpi) throws IOException {
		String baseFileName = pdfFile.getPath();
		if (baseFileName.toLowerCase().endsWith(".pdf"))
			baseFileName = baseFileName.substring(0, baseFileName.length() - ".pdf".length());

		FileUtils.LOGGER.info("Downsample factor: " + downsampleFactor);
		FileUtils.LOGGER.info("JPEG quality: " + jpegQuality);
		FileUtils.LOGGER.info("Target DPI: " + targetDpi);

		try (PDDocument document = PDDocument.load(pdfFile)) {
			int totalPages = document.getNumberOfPages();
			FileUtils.LOGGER.info("Processing PDF with " + totalPages + " pages");

			Map<Object, PDImageXObject> compressedCache = new HashMap<>();

			for (PDPage page : document.getPages())
				compressPage(page, document, downsampleFactor, jpegQuality, targetDpi, compressedCache);

			String outputPath = baseFileName + "Compressed.pdf";
			document.save(outputPath);
			FileUtils.LOGGER.info("Compression complete: " + outputPath);
		}
	}

	private static void compressPage(PDPage page, PDDocument document, float downsampleFactor, float jpegQuality, int targetDpi, Map<Object, PDImageXObject> compressedCache) throws IOException {
		PDResources resources = page.getResources();
		if (resources == null) return;

		Iterable<COSName> xObjectNames = resources.getXObjectNames();
		if (xObjectNames == null) return;

		for (COSName name : xObjectNames) {
			PDXObject xObject = resources.getXObject(name);

			if (xObject instanceof PDImageXObject) {
				PDImageXObject image = (PDImageXObject) xObject;
				Object cacheKey = image.getCOSObject();

				PDImageXObject compressedImage;
				if (compressedCache.containsKey(cacheKey)) {
					compressedImage = compressedCache.get(cacheKey);
				} else {
					compressedImage = compressImage(document, image, downsampleFactor, jpegQuality, targetDpi);
					if (compressedImage != null) {
						compressedCache.put(cacheKey, compressedImage);
					} else {
						compressedCache.put(cacheKey, image);
					}
				}

				if (compressedImage != null && compressedImage != image)
					resources.put(name, compressedImage);
			}
		}
	}

	private static PDImageXObject compressImage(PDDocument document, PDImageXObject image, float downsampleFactor, float jpegQuality, int targetDpi) throws IOException {
		BufferedImage bufferedImage = image.getImage();

		if (bufferedImage == null) return null;

		int currentWidth = bufferedImage.getWidth();
		int currentHeight = bufferedImage.getHeight();

		int newWidth = Math.max(1, (int) (currentWidth * downsampleFactor));
		int newHeight = Math.max(1, (int) (currentHeight * downsampleFactor));

		int imageType = bufferedImage.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
		BufferedImage processedImage = new BufferedImage(newWidth, newHeight, imageType);

		Graphics2D g2d = processedImage.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g2d.drawImage(bufferedImage, 0, 0, newWidth, newHeight, null);
		g2d.dispose();

		FileUtils.LOGGER.info("Downsampled image from " + currentWidth + "x" + currentHeight + " to " + newWidth + "x" + newHeight);

		try {
			PDImageXObject compressedImage = JPEGFactory.createFromImage(document, processedImage, jpegQuality, targetDpi);
			FileUtils.LOGGER.info("Compressed image with JPEG quality: " + jpegQuality);
			return compressedImage;
		} catch (Exception e) {
			FileUtils.LOGGER.warning("Failed to compress image: " + e.getMessage());
			return null;
		}
	}
}