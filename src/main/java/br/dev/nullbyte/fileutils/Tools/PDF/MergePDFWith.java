package br.dev.nullbyte.fileutils.Tools.PDF;

import br.dev.nullbyte.fileutils.FileUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MergePDFWith {

    public static void run(String... args) {
        List<File> filesToMerge = new ArrayList<>();

        if (args.length < 1)
            throw new RuntimeException(
                    "Merge PDF command requires at least one argument (base PDF file).");

        File baseFile = new File(args[0]);
        if (!baseFile.exists() || !baseFile.isFile()
                || !baseFile.getName().endsWith(".pdf"))
            throw new RuntimeException("Merge PDF requires a valid PDF file path.");

        filesToMerge.add(baseFile);

        if (args.length >= 2) {
            for (int i = 1; i < args.length; i++) {
                File file = new File(args[i]);
                if (!file.exists() || !file.isFile()
                        || !file.getName().endsWith(".pdf"))
                    throw new RuntimeException("All arguments must be valid PDF file paths.");
                filesToMerge.add(file);
            }
        } else {
            List<File> selectedFiles = openFileChooser();
            if (selectedFiles == null || selectedFiles.isEmpty())
                throw new RuntimeException("No files selected for merging.");
            filesToMerge.addAll(selectedFiles);
        }

        try {
            mergePdfWith(filesToMerge);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<File> openFileChooser() {
        FileDialog fileChooser = new FileDialog((java.awt.Frame) null,
                "Select files to merge with", FileDialog.LOAD);
        fileChooser.setMultipleMode(true);

        fileChooser.setVisible(true);

        File[] selectedFiles = fileChooser.getFiles();

        if (selectedFiles != null && selectedFiles.length > 0)
            return Arrays.asList(selectedFiles);

        return null;
    }

    public static void mergePdfWith(List<File> pdfFiles) throws IOException {
        if (pdfFiles == null || pdfFiles.isEmpty())
            throw new RuntimeException("No files provided for merging.");

        List<PDDocument> loadedDocuments = new ArrayList<>();

        try (PDDocument mergedDocument = new PDDocument()) {
            int totalPages = 0;

            for (File file : pdfFiles) {
                FileUtils.LOGGER.info("Loading file: " + file.getName());

                if (file.getName().toLowerCase().endsWith(".pdf")) {
                    PDDocument document = PDDocument.load(file);
                    loadedDocuments.add(document);

                    int pageCount = document.getNumberOfPages();
                    totalPages += pageCount;

                    for (PDPage page : document.getPages()) {
                        mergedDocument.addPage(page);
                    }

                    FileUtils.LOGGER.info("Added " + pageCount + " page(s) from " + file.getName());
                } else if (isImageFile(file)) {
                    PDPage imagePage = createPdfPageFromImage(file, mergedDocument);
                    mergedDocument.addPage(imagePage);
                    totalPages++;
                    FileUtils.LOGGER.info("Added image page from " + file.getName());
                } else {
                    PDPage textPage = createPdfPageFromText(file, mergedDocument);
                    mergedDocument.addPage(textPage);
                    totalPages++;
                    FileUtils.LOGGER.info("Added text page from " + file.getName());
                }
            }

            String outputPath = generateOutputPath(pdfFiles.get(0));
            mergedDocument.save(outputPath);

            FileUtils.LOGGER.info("Successfully merged " + pdfFiles.size() + " file(s) with " + totalPages + " total page(s)");
            FileUtils.LOGGER.info("Output saved to: " + outputPath);

        } finally {
            for (PDDocument doc : loadedDocuments) {
                try {
                    doc.close();
                } catch (IOException e) {
                    FileUtils.LOGGER.warning("Failed to close document: " + e.getMessage());
                }
            }
        }
    }

    private static PDPage createPdfPageFromText(File file, PDDocument document) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()));

        PDPage page = new PDPage(PDRectangle.LETTER);
        PDPageContentStream contentStream = new PDPageContentStream(document, page);

        contentStream.setFont(PDType1Font.HELVETICA, 12);
        contentStream.beginText();
        contentStream.newLineAtOffset(50, 750);

        String[] lines = content.split("\n");
        for (String line : lines) {
            contentStream.showText(line);
            contentStream.newLine();
        }

        contentStream.endText();
        contentStream.close();

        return page;
    }

    private static PDPage createPdfPageFromImage(File imageFile, PDDocument document) throws IOException {
        BufferedImage image = ImageIO.read(imageFile);

        if (image == null)
            throw new RuntimeException("Failed to read image: " + imageFile.getName());

        PDPage page = new PDPage(new PDRectangle(image.getWidth(), image.getHeight()));
        PDPageContentStream contentStream = new PDPageContentStream(document, page);

        PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);

        contentStream.drawImage(pdImage, 0, 0, image.getWidth(), image.getHeight());
        contentStream.close();

        return page;
    }

    private static boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg")
                || name.endsWith(".jpeg") || name.endsWith(".bmp")
                || name.endsWith(".gif");
    }

    private static String generateOutputPath(File baseFile) {
        String basePath = baseFile.getAbsolutePath();
        if (basePath.toLowerCase().endsWith(".pdf")) {
            basePath = basePath.substring(0, basePath.length() - 4);
        } else {
            int lastDot = basePath.lastIndexOf(".");
            if (lastDot > 0)
                basePath = basePath.substring(0, lastDot);
        }
        return basePath + "Merged.pdf";
    }
}