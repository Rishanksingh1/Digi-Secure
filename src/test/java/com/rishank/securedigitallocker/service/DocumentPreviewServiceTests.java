package com.rishank.securedigitallocker.service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class DocumentPreviewServiceTests {

    private final DocumentPreviewService previewService = new DocumentPreviewService();

    @Test
    void convertsImagePreviewToWatermarkedPng() throws Exception {
        BufferedImage source = new BufferedImage(100, 60, BufferedImage.TYPE_INT_RGB);
        source.setRGB(0, 0, Color.WHITE.getRGB());
        ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", sourceBytes);

        byte[] preview = previewService.renderPreview(sourceBytes.toByteArray());
        BufferedImage rendered = ImageIO.read(new ByteArrayInputStream(preview));

        assertTrue(previewService.isImage(sourceBytes.toByteArray()));
        assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G'},
                new byte[] {preview[0], preview[1], preview[2], preview[3]});
        assertTrue(rendered.getWidth() > 0);
        assertTrue(hasWatermarkPixels(rendered), "The watermark should be visible in the generated preview");
    }

    @Test
    void rendersOwnerImagePreviewWithoutSharedPreviewWatermark() throws Exception {
        BufferedImage source = new BufferedImage(100, 60, BufferedImage.TYPE_INT_RGB);
        source.setRGB(50, 30, Color.WHITE.getRGB());
        ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", sourceBytes);

        byte[] preview = previewService.renderImagePreview(sourceBytes.toByteArray());
        BufferedImage rendered = ImageIO.read(new ByteArrayInputStream(preview));

        assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G'},
                new byte[] {preview[0], preview[1], preview[2], preview[3]});
        assertEquals(100, rendered.getWidth());
        assertEquals(60, rendered.getHeight());
        assertEquals(Color.WHITE.getRGB(), rendered.getRGB(50, 30));
    }

    @Test
    void convertsPdfPreviewToPngRatherThanReturningOriginalPdf() throws Exception {
        ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.addPage(new PDPage());
            document.save(sourceBytes);
        }

        byte[] pdf = sourceBytes.toByteArray();
        byte[] preview = previewService.renderPdfPagePreview(pdf, 1);

        assertTrue(previewService.isPdf(pdf));
        assertEquals(2, previewService.getPdfPageCount(pdf));
        assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G'},
                new byte[] {preview[0], preview[1], preview[2], preview[3]});
        assertTrue(hasWatermarkPixels(ImageIO.read(new ByteArrayInputStream(preview))),
                "The PDF preview should also include the watermark");
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> previewService.renderPdfPagePreview(pdf, 2));
        assertEquals("The requested PDF page does not exist", exception.getMessage());
    }

    private boolean hasWatermarkPixels(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        for (int y = Math.max(0, height - height / 3); y < height; y += Math.max(1, height / 30)) {
            for (int x = Math.max(0, width / 20); x < width; x += Math.max(1, width / 40)) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                if (red + green + blue < 700) {
                    return true;
                }
            }
        }
        return false;
    }
}
