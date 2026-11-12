package com.rishank.securedigitallocker.service;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

@Service
public class DocumentPreviewService {

    private static final int MAX_IMAGE_DIMENSION = 1200;
    private static final long MAX_IMAGE_PIXELS = 8_000_000L;

    public boolean isPdf(byte[] content) {
        int limit = Math.min(content.length, 1024);
        for (int i = 0; i + 5 <= limit; i++) {
            if (content[i] == '%' && content[i + 1] == 'P' && content[i + 2] == 'D'
                    && content[i + 3] == 'F' && content[i + 4] == '-') {
                return true;
            }
        }
        return false;
    }

    public byte[] renderPreview(byte[] content) throws IOException {
        if (isPdf(content)) {
            return renderPdfPreview(content, 0);
        }
        BufferedImage image = readSafeImage(content);
        BufferedImage preview = resize(image);
        watermark(preview);
        return encodePng(preview);
    }

    public byte[] renderImagePreview(byte[] content) throws IOException {
        BufferedImage image = readSafeImage(content);
        return encodePng(resize(image));
    }

    public byte[] renderPdfPagePreview(byte[] content, int pageIndex) throws IOException {
        if (!isPdf(content)) {
            throw new IOException("This file is not a PDF");
        }
        return renderPdfPreview(content, pageIndex);
    }

    public int getPdfPageCount(byte[] content) throws IOException {
        if (!isPdf(content)) {
            throw new IOException("This file is not a PDF");
        }
        try (PDDocument document = Loader.loadPDF(content)) {
            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                throw new IOException("This PDF has no pages to preview");
            }
            return pageCount;
        }
    }

    public boolean isImage(byte[] content) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (input == null) {
                return false;
            }
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return false;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                return width > 0 && height > 0 && (long) width * height <= MAX_IMAGE_PIXELS;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            return false;
        }
    }

    private BufferedImage readSafeImage(byte[] content) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (input == null) {
                throw new IOException("This file type cannot be safely previewed");
            }
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("This file type cannot be safely previewed");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new IOException("This image is too large to preview safely");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IOException("This file type cannot be safely previewed");
                }
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    private byte[] renderPdfPreview(byte[] content, int pageIndex) throws IOException {
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.getNumberOfPages() == 0) {
                throw new IOException("This PDF has no pages to preview");
            }
            if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
                throw new IllegalArgumentException("The requested PDF page does not exist");
            }
            var box = document.getPage(pageIndex).getMediaBox();
            if (box.getWidth() <= 0 || box.getHeight() <= 0
                    || (double) box.getWidth() * box.getHeight() > MAX_IMAGE_PIXELS) {
                throw new IOException("This PDF page is too large to preview safely");
            }
            BufferedImage image = new PDFRenderer(document)
                    .renderImage(pageIndex, 1.0f, ImageType.RGB);
            BufferedImage preview = resize(image);
            watermark(preview);
            return encodePng(preview);
        }
    }

    private BufferedImage resize(BufferedImage source) {
        double scale = Math.min(1.0,
                Math.min((double) MAX_IMAGE_DIMENSION / source.getWidth(),
                        (double) MAX_IMAGE_DIMENSION / source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private void watermark(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
            graphics.setColor(Color.DARK_GRAY);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD,
                    Math.max(14, image.getWidth() / 24)));
            String watermark = "DIGI SECURE - SHARED PREVIEW";
            int x = Math.max(8, image.getWidth() / 20);
            int y = Math.max(24, image.getHeight() - image.getHeight() / 16);
            graphics.drawString(watermark, x, y);
        } finally {
            graphics.dispose();
        }
    }

    private byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IOException("PNG preview encoding is unavailable");
        }
        return output.toByteArray();
    }
}
