package com.rishank.securedigitallocker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocumentRecognitionServiceTests {

    private final DocumentRecognitionService recognitionService =
            new DocumentRecognitionService("tesseract");

    @Test
    void classifiesIdentityDocumentsAndExtractsLabeledMetadata() {
        DocumentRecognitionResult result = recognitionService.classify("""
                UIDAI Aadhaar
                Name: Alex Example
                Date of Birth: 01/02/2000
                """);

        assertEquals("Identity document", result.documentType());
        assertEquals("Identity", result.category());
        assertEquals("Alex Example", result.metadata().get("Name"));
        assertEquals("01/02/2000", result.metadata().get("Date"));
        assertTrue(result.recognized());
    }

    @Test
    void classifiesMarksheetsAsEducation() {
        DocumentRecognitionResult result = recognitionService.classify(
                "UNIVERSITY TRANSCRIPT - STATEMENT OF MARKS");

        assertEquals("Marksheet", result.documentType());
        assertEquals("Education", result.category());
    }

    @Test
    void leavesUnrecognizedTextInGeneralCategory() {
        DocumentRecognitionResult result = recognitionService.classify("A document with no known labels");

        assertEquals("Unclassified", result.documentType());
        assertEquals("General", result.category());
        assertFalse(result.metadata().containsKey("Name"));
    }

    @Test
    void recognizesEmbeddedPdfTextWithoutLaunchingOcr() throws Exception {
        ByteArrayOutputStream pdfBytes = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                content.newLineAtOffset(50, 700);
                content.showText("UIDAI Aadhaar");
                content.endText();
            }
            document.save(pdfBytes);
        }
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "identity.pdf", "application/pdf", pdfBytes.toByteArray());
        DocumentRecognitionService noOcr = new DocumentRecognitionService("missing-tesseract-command");

        DocumentRecognitionResult result = noOcr.recognize(pdf);

        assertTrue(result.recognized(), result.notice());
        assertEquals("Identity document", result.documentType());
        assertEquals("Identity", result.category());
    }

    @Test
    void reportsUnavailableLocalOcrWithoutBlockingTheUpload() throws IOException {
        ByteArrayOutputStream imageBytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", imageBytes);
        MockMultipartFile image = new MockMultipartFile("file", "document.png", "image/png",
                imageBytes.toByteArray());
        DocumentRecognitionService unavailableOcr =
                new DocumentRecognitionService("missing-tesseract-command");

        DocumentRecognitionResult result = unavailableOcr.recognize(image);

        assertFalse(result.recognized());
        assertTrue(result.notice().contains("Install Tesseract"));
        assertEquals("General", result.category());
    }

    @Test
    void recognizesTextFromAnImageWhenTesseractIsInstalled() throws Exception {
        String executable = tesseractExecutable();
        assumeTrue(isAvailable(executable), "Tesseract is not installed on this machine");

        BufferedImage image = new BufferedImage(1400, 260, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 52));
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.drawString("UIDAI AADHAAR", 50, 90);
        graphics.drawString("Name: ALEX EXAMPLE", 50, 180);
        graphics.dispose();

        ByteArrayOutputStream imageBytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", imageBytes);
        MockMultipartFile upload = new MockMultipartFile("file", "identity.png", "image/png",
                imageBytes.toByteArray());

        DocumentRecognitionResult result = new DocumentRecognitionService(executable).recognize(upload);

        assertTrue(result.recognized(), result.notice());
        assertEquals("Identity document", result.documentType());
        assertEquals("Identity", result.category());
        assertTrue(result.metadata().get("Name").toLowerCase().contains("alex"),
                () -> "Expected OCR to extract the name, got: " + result.metadata());
    }

    private String tesseractExecutable() {
        String configuredCommand = System.getenv("TESSERACT_COMMAND");
        if (configuredCommand != null && !configuredCommand.isBlank()) {
            return configuredCommand;
        }
        Path windowsInstall = Path.of("C:/Program Files/Tesseract-OCR/tesseract.exe");
        return Files.isRegularFile(windowsInstall) ? windowsInstall.toString() : "tesseract";
    }

    private boolean isAvailable(String executable) throws Exception {
        try {
            Process process = new ProcessBuilder(executable, "--version")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException exception) {
            return false;
        }
    }
}
