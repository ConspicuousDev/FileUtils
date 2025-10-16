package br.dev.nullbyte.fileutils.Tools.PDF;

import br.dev.nullbyte.fileutils.FileUtils;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SplitPDF {
	public final static long DEFAULT_CHUNK_SIZE_BYTES = 10_000_000;

	public static void run(String... args) {
		File file;
		long chunkSizeBytes = DEFAULT_CHUNK_SIZE_BYTES;

		if (args.length >= 1) {
			file = new File(args[0]);
			if (!file.exists() || !file.isFile() || !file.getName().endsWith(".pdf"))
				throw new RuntimeException("Split PDF requires the path to a PDF file.");
		} else throw new RuntimeException("Split PDF command requires at least one argument.");

		if (args.length >= 2)
			chunkSizeBytes = Long.parseLong(args[1]);

		try {
			splitPdf(file, chunkSizeBytes);
		} catch (IOException e) {
			throw new RuntimeException("Error splitting PDF: " + e.getMessage(), e);
		}
	}

	public static void splitPdf(File pdfFile, long maxChunkSizeBytes) throws IOException {
		String baseFileName = pdfFile.getPath();
		if (baseFileName.toLowerCase().endsWith(".pdf"))
			baseFileName = baseFileName.substring(0, baseFileName.length() - ".pdf".length());

		try (PDDocument document = PDDocument.load(pdfFile)) {
			int totalPages = document.getNumberOfPages();
			FileUtils.LOGGER.info("Processing PDF with " + totalPages + " pages");

			List<PDDocument> chunks = determineChunks(document, maxChunkSizeBytes);
			FileUtils.LOGGER.info("Splitting into " + chunks.size() + " chunks");

			for (int i = 0; i < chunks.size(); i++) {
				PDDocument chunkDocument = chunks.get(i);
				createChunk(chunkDocument, baseFileName + "Chunk" + (i + 1) + ".pdf");
				FileUtils.LOGGER.info("Created chunk " + (i + 1) + "/" + chunks.size() + " with " + chunkDocument.getNumberOfPages() + " page(s)");
			}
		}
	}

	private static List<PDDocument> determineChunks(PDDocument document, long maxChunkSizeBytes) throws IOException {
		List<PDDocument> chunks = new ArrayList<>();
		PDDocument currentChunk = new PDDocument();

		int i = 0;
		for (PDPage page : document.getPages()) {
			pruneUnusedXObjects(page);

			currentChunk.addPage(page);

			long chunkSize = measureDocumentSize(currentChunk);

			if (chunkSize >= maxChunkSizeBytes) {
				currentChunk.removePage(page);
				int finalPageCount = currentChunk.getNumberOfPages();
				chunks.add(currentChunk);

				currentChunk = new PDDocument();
				currentChunk.addPage(page);

				FileUtils.LOGGER.info("Chunk " + (i + 1) + " finalized with " + finalPageCount + "page(s)");
			}

			i++;
		}

		if (currentChunk.getNumberOfPages() > 0) {
			chunks.add(currentChunk);

			FileUtils.LOGGER.info("Chunk " + (i + 1) + " finalized with " + currentChunk.getNumberOfPages() + "page(s)");
		}

		return chunks;
	}

	private static void pruneUnusedXObjects(PDPage page) throws IOException {
		Set<COSName> usedXObjects = new HashSet<>();

		PDFStreamParser parser = new PDFStreamParser(page);
		parser.parse();
		List<Object> tokens = parser.getTokens();
		for (int i = 0; i < tokens.size(); i++) {
			Object tok = tokens.get(i);
			if (tok instanceof Operator) {
				Operator op = (Operator) tok;
				if ("Do".equals(op.getName()) && i > 0 && tokens.get(i - 1) instanceof COSName)
					usedXObjects.add((COSName) tokens.get(i - 1));
			}
		}

		PDResources res = page.getResources();
		if (res == null) return;

		PDResources newRes = new PDResources();

		for (COSName name : res.getXObjectNames()) {
			if (usedXObjects.contains(name)) {
				PDXObject xobj = res.getXObject(name);
				if (xobj != null) newRes.put(name, xobj);
			}
		}

		for (COSName n : res.getFontNames()) newRes.put(n, res.getFont(n));
		for (COSName n : res.getColorSpaceNames()) newRes.put(n, res.getColorSpace(n));
		for (COSName n : res.getShadingNames()) newRes.put(n, res.getShading(n));
		for (COSName n : res.getPatternNames()) newRes.put(n, res.getPattern(n));
		for (COSName n : res.getExtGStateNames()) newRes.put(n, res.getExtGState(n));
		for (COSName n : res.getPropertiesNames()) newRes.put(n, res.getProperties(n));

		page.setResources(newRes);
	}

	private static long measureDocumentSize(PDDocument document) throws IOException {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		document.save(outputStream);

		long size = outputStream.size();

		outputStream.close();

		return size;
	}

	private static void createChunk(PDDocument chunkDocument, String outputPath) throws IOException {
		chunkDocument.save(outputPath);
		chunkDocument.close();
	}
}