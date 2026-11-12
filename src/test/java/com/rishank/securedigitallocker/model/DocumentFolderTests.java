package com.rishank.securedigitallocker.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DocumentFolderTests {

    @Test
    void groupsPdfFilesByContentTypeOrExtension() {
        assertEquals(DocumentFolder.PDFS, DocumentFolder.classify("report.pdf", "application/octet-stream"));
        assertEquals(DocumentFolder.PDFS, DocumentFolder.classify("report", "application/pdf"));
    }

    @Test
    void groupsImagesByMimeTypeOrExtensionIgnoringCase() {
        assertEquals(DocumentFolder.IMAGES, DocumentFolder.classify("photo.JPG", "application/octet-stream"));
        assertEquals(DocumentFolder.IMAGES, DocumentFolder.classify("photo", "image/png"));
    }

    @Test
    void putsOtherFormatsInOtherAndKeepsLegacyRowsClassifiable() {
        assertEquals(DocumentFolder.OTHER, DocumentFolder.classify("notes.txt", "text/plain"));

        Document legacyDocument = new Document();
        legacyDocument.setFileName("scan.PDF");
        legacyDocument.setFileType("application/octet-stream");
        assertEquals(DocumentFolder.PDFS.getKey(), legacyDocument.getFolderKey());
        assertEquals(DocumentFolder.PDFS.getLabel(), legacyDocument.getFolderLabel());
    }
}
