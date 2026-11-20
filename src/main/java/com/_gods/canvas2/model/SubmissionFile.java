package com._gods.canvas2.model;

import java.util.Arrays;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Lob;

/** A bounded document attachment, stored as a relational BLOB rather than in JSON responses. */
@Embeddable
public class SubmissionFile {
  public static final int MAX_BYTES = 10 * 1024 * 1024;

  @Column(name = "attachment_filename")
  private String filename;

  @Column(name = "attachment_content_type")
  private String contentType;

  @Lob
  @Column(name = "attachment_data")
  private byte[] data;

  protected SubmissionFile() {}

  public SubmissionFile(String originalFilename, byte[] data) {
    if (data == null || data.length == 0 || data.length > MAX_BYTES) {
      throw new IllegalArgumentException("Upload a non-empty file no larger than 10 MB");
    }
    String name = originalFilename == null ? "" : originalFilename.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\";]", "_").trim();
    if (name.isBlank() || name.length() > 200) {
      throw new IllegalArgumentException("Use a filename between 1 and 200 characters");
    }
    String lower = name.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".pdf") && startsWith(data, new byte[] {37, 80, 68, 70, 45})) {
      contentType = "application/pdf";
    } else if (lower.endsWith(".doc") && startsWith(data,
        new byte[] {(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1})) {
      contentType = "application/msword";
    } else if (lower.endsWith(".docx") && startsWith(data, new byte[] {80, 75, 3, 4})) {
      contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    } else {
      throw new IllegalArgumentException("Upload a PDF, DOC or DOCX file with a matching document signature");
    }
    filename = name;
    this.data = data.clone();
  }

  private static boolean startsWith(byte[] data, byte[] signature) {
    return data.length >= signature.length
        && Arrays.equals(Arrays.copyOf(data, signature.length), signature);
  }

  public String getFilename() { return filename; }
  public String getContentType() { return contentType; }
  public long getSize() { return data.length; }
  public byte[] getData() { return data.clone(); }
}
