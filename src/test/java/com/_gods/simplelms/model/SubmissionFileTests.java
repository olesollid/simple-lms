package com._gods.simplelms.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com._gods.simplelms.model.SubmissionFile;

class SubmissionFileTests {
  @Test
  void boundsAndDocumentSignaturesAreValidated() {
    assertThrows(IllegalArgumentException.class, () -> new SubmissionFile("a.pdf", new byte[0]));
    assertThrows(IllegalArgumentException.class,
        () -> new SubmissionFile("a.pdf", new byte[SubmissionFile.MAX_BYTES + 1]));
    assertThrows(IllegalArgumentException.class,
        () -> new SubmissionFile("a.pdf", "not a PDF".getBytes()));
    assertThrows(IllegalArgumentException.class,
        () -> new SubmissionFile("a.exe", "%PDF-1.4".getBytes()));
    assertEquals("report.pdf", new SubmissionFile("C:\\fakepath\\report.pdf", "%PDF-1.4".getBytes()).getFilename());
    assertEquals("application/msword", new SubmissionFile("a.doc",
        new byte[] {(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1}).getContentType());
    assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        new SubmissionFile("a.docx", new byte[] {80, 75, 3, 4}).getContentType());
  }
}
