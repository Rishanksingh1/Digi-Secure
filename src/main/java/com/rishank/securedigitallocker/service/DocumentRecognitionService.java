package com.rishank.securedigitallocker.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentRecognitionService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentRecognitionService.class);
    private static final int MAX_PDF_PAGES = 5;
    private static final Duration OCR_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern NAME_PATTERN = Pattern.compile(
            "(?im)^\\s*(?:student\\s+name|candidate\\s+name|name)\\s*[:\\-]\\s*(.{2,80})\\s*$");
    private static final Pattern INSTITUTION_PATTERN = Pattern.compile(
            "(?im)^\\s*(?:school|college|university|institution)\\s*[:\\-]\\s*(.{2,100})\\s*$");
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(?im)^\\s*(?:date\\s+of\\s+birth|dob|issue\\s+date|date)\\s*[:\\-]\\s*"
                    + "([\\d./-]{4,20}|[\\p{L} ]{3,20}\\s+\\d{1,2},?\\s+\\d{4})\\s*$");

    private final String tesseractCommand;

    public DocumentRecognitionService(
            @Value("${app.ocr.tesseract-command:tesseract}") String tesseractCommand) {
        this.tesseractCommand = tesseractCommand;
    }

    public DocumentRecognitionResult recognize(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String contentType = file.getContentType() == null ? "" : file.getContentType();
        return recognize(fileName, contentType, file.getBytes());
    }

    public DocumentRecognitionResult recognize(String fileName, String contentType, byte[] content) {
        if (!supports(fileName, contentType)) {
            return failed("OCR is available for image and PDF documents. The file was still uploaded.");
        }

        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        boolean isPdf = normalizedContentType.equals("application/pdf")
                || fileName.toLowerCase(Locale.ROOT).endsWith(".pdf");
        Path workspace;
        try {
            workspace = Files.createTempDirectory("document-ocr-");
        } catch (IOException exception) {
            logger.warn("Unable to create temporary workspace for OCR", exception);
            return failed("Local OCR could not start. The file was still uploaded.");
        }

        try {
            String text = isPdf
                    ? recognizePdf(content, workspace)
                    : recognizeImage(content, workspace);
            if (text.isBlank()) {
                return failed("No readable text was found. The file was still uploaded.");
            }
            return classify(text);
        } catch (TesseractUnavailableException exception) {
            logger.warn("Local OCR is unavailable. Configure app.ocr.tesseract-command to a Tesseract executable.",
                    exception.getCause());
            return failed("Local OCR is unavailable. Install Tesseract to enable document recognition.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.warn("Document OCR was interrupted", exception);
            return failed("Document recognition was interrupted. The file was still uploaded.");
        } catch (IOException exception) {
            logger.warn("Document OCR could not process the uploaded file", exception);
            return failed("Document recognition could not read this file. The file was still uploaded.");
        } finally {
            deleteWorkspace(workspace);
        }
    }

    public static boolean supports(String fileName, String contentType) {
        String normalizedName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return normalizedContentType.equals("application/pdf")
                || normalizedName.endsWith(".pdf")
                || normalizedContentType.startsWith("image/");
    }

    DocumentRecognitionResult classify(String text) {
        String normalizedText = text.toLowerCase(Locale.ROOT);
        String documentType;
        String category;
        if (containsAny(normalizedText, "aadhaar", "uidai", "passport", "driving licence",
                "driving license", "pan card", "voter id")) {
            documentType = "Identity document";
            category = "Identity";
        } else if (containsAny(normalizedText, "marksheet", "mark sheet", "statement of marks",
                "transcript", "grade sheet")) {
            documentType = "Marksheet";
            category = "Education";
        } else if (containsAny(normalizedText, "certificate", "certify that", "hereby awarded")) {
            documentType = "Certificate";
            category = "Certificates";
        } else {
            documentType = "Unclassified";
            category = "General";
        }
        return new DocumentRecognitionResult(documentType, category, extractMetadata(text),
                "Document type and category were suggested from locally processed OCR text.", true);
    }

    private String recognizePdf(byte[] content, Path workspace) throws IOException, InterruptedException {
        try (var pdf = Loader.loadPDF(content)) {
            String embeddedText = new PDFTextStripper().getText(pdf);
            if (!embeddedText.isBlank()) {
                return embeddedText;
            }

            StringBuilder text = new StringBuilder();
            PDFRenderer renderer = new PDFRenderer(pdf);
            int pagesToRead = Math.min(pdf.getNumberOfPages(), MAX_PDF_PAGES);
            for (int page = 0; page < pagesToRead; page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, 180, ImageType.GRAY);
                Path imagePath = workspace.resolve("page-" + page + ".png");
                if (!ImageIO.write(image, "png", imagePath.toFile())) {
                    throw new IOException("Unable to prepare PDF page for OCR");
                }
                text.append(runTesseract(imagePath, workspace, page)).append('\n');
            }
            return text.toString();
        }
    }

    private String recognizeImage(byte[] content, Path workspace) throws IOException, InterruptedException {
        Path imagePath = workspace.resolve("upload");
        Files.write(imagePath, content);
        return runTesseract(imagePath, workspace, 0);
    }

    private String runTesseract(Path imagePath, Path workspace, int page)
            throws IOException, InterruptedException {
        Path outputPath = workspace.resolve("page-" + page + ".txt");
        Path errorPath = workspace.resolve("page-" + page + ".err");
        Process process;
        try {
            process = new ProcessBuilder(tesseractCommand, imagePath.toString(), "stdout", "-l", "eng")
                    .redirectOutput(outputPath.toFile())
                    .redirectError(errorPath.toFile())
                    .start();
        } catch (IOException exception) {
            throw new TesseractUnavailableException(exception);
        }

        if (!process.waitFor(OCR_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor();
            throw new IOException("Tesseract OCR timed out");
        }
        if (process.exitValue() != 0) {
            throw new IOException("Tesseract OCR failed with exit code " + process.exitValue());
        }
        return Files.readString(outputPath, StandardCharsets.UTF_8);
    }

    private Map<String, String> extractMetadata(String text) {
        Map<String, String> metadata = new LinkedHashMap<>();
        addMatch(metadata, "Name", NAME_PATTERN, text);
        addMatch(metadata, "Institution", INSTITUTION_PATTERN, text);
        addMatch(metadata, "Date", DATE_PATTERN, text);
        return metadata;
    }

    private void addMatch(Map<String, String> metadata, String label, Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            metadata.put(label, matcher.group(1).trim());
        }
    }

    private boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private DocumentRecognitionResult failed(String notice) {
        return new DocumentRecognitionResult("Unclassified", "General", Map.of(), notice, false);
    }

    private void deleteWorkspace(Path workspace) {
        try (Stream<Path> paths = Files.walk(workspace)) {
            for (Path path : paths.sorted((left, right) -> right.compareTo(left)).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            logger.warn("Unable to remove temporary OCR files", exception);
        }
    }

    private static final class TesseractUnavailableException extends IOException {
        private TesseractUnavailableException(IOException cause) {
            super(cause);
        }
    }
}
