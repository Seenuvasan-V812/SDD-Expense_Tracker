package com.dailyexpense.expense.service;

import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Arrays;

/**
 * T060 — Server-side image security: magic-byte detection, size + pixel-flood guard, EXIF strip.
 * SEC-1: type detection uses magic bytes, NEVER client-supplied Content-Type.
 * SEC-2: EXIF stripped from all stored bytes — 0 EXIF segments guaranteed.
 */
@Service
public class ImageSecurityService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;      // 5 MB
    private static final long MAX_PIXELS = 25_000_000L;           // 25 MP

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] WEBP_RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_MARKER = {'W', 'E', 'B', 'P'};

    public enum ImageMimeType {
        JPEG("image/jpeg"),
        PNG("image/png"),
        WEBP("image/webp");

        public final String mime;
        ImageMimeType(String mime) { this.mime = mime; }
    }

    /**
     * Detect format from magic bytes — never trusts client Content-Type header.
     * Returns null for unsupported or unrecognised formats.
     */
    public ImageMimeType detectFormat(byte[] bytes) {
        if (startsWith(bytes, JPEG_MAGIC)) return ImageMimeType.JPEG;
        if (startsWith(bytes, PNG_MAGIC)) return ImageMimeType.PNG;
        if (bytes.length >= 12
                && startsWith(bytes, WEBP_RIFF)
                && Arrays.equals(Arrays.copyOfRange(bytes, 8, 12), WEBP_MARKER)) {
            return ImageMimeType.WEBP;
        }
        return null;
    }

    /** Reject if file exceeds 5 MB. */
    public void validateSize(int sizeBytes) {
        if (sizeBytes > MAX_BYTES) {
            throw new IllegalArgumentException("File exceeds 5 MB limit (" + sizeBytes + " bytes)");
        }
    }

    /**
     * Pixel-flood guard: read image dimensions from header (no full decompression),
     * reject if decoded pixel count exceeds 25 MP.
     */
    public void validatePixels(byte[] bytes) {
        try {
            ImageInfo info = Imaging.getImageInfo(bytes);
            long pixels = (long) info.getWidth() * info.getHeight();
            if (pixels > MAX_PIXELS) {
                throw new IllegalArgumentException(
                    "Image exceeds 25 MP pixel limit (" + pixels + " pixels)");
            }
        } catch (ImagingException | IOException e) {
            throw new IllegalArgumentException("Cannot read image dimensions: " + e.getMessage(), e);
        }
    }

    /**
     * Strip all EXIF metadata from the image.
     * Returns new byte array — the original bytes are not modified.
     * Guaranteed: 0 EXIF segments in returned bytes.
     */
    public byte[] stripExif(byte[] bytes, ImageMimeType format) {
        try {
            return switch (format) {
                case JPEG -> stripJpegExif(bytes);
                case PNG -> stripPngMetadata(bytes);
                case WEBP -> stripWebpExif(bytes);
            };
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to process image metadata: " + e.getMessage(), e);
        }
    }

    private byte[] stripJpegExif(byte[] bytes) throws Exception {
        ExifRewriter rewriter = new ExifRewriter();
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length);
        rewriter.removeExifMetadata(bytes, out);
        return out.toByteArray();
    }

    private byte[] stripPngMetadata(byte[] bytes) throws Exception {
        // Re-encoding via ImageIO drops all metadata chunks (tEXt, zTXt, iTXt, eXIf)
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) throw new IllegalArgumentException("Cannot decode PNG image");
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length);
        ImageIO.write(img, "PNG", out);
        return out.toByteArray();
    }

    private byte[] stripWebpExif(byte[] bytes) throws IOException {
        if (bytes.length < 12) return bytes;
        // RIFF/WEBP chunk structure: RIFF(4) + fileSize(4 LE) + WEBP(4) + chunks...
        // Each chunk: chunkId(4) + chunkSize(4 LE) + data + optional pad byte
        ByteArrayInputStream in = new ByteArrayInputStream(bytes, 12, bytes.length - 12);
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        byte[] idBuf = new byte[4];

        while (in.available() >= 8) {
            if (in.read(idBuf) != 4) break;
            String chunkId = new String(idBuf);
            int size = readInt32LE(in);
            if (size < 0) break;
            int paddedSize = size + (size % 2); // pad to even
            byte[] data = new byte[paddedSize];
            int read = in.read(data);
            if (read < size) break;

            if (!"EXIF".equals(chunkId) && !"XMP ".equals(chunkId)) {
                payload.write(idBuf);
                writeInt32LE(payload, size);
                payload.write(data, 0, paddedSize);
            }
        }

        byte[] payloadBytes = payload.toByteArray();
        int riffContentSize = 4 + payloadBytes.length; // "WEBP" + chunks

        ByteArrayOutputStream result = new ByteArrayOutputStream(12 + payloadBytes.length);
        result.write(WEBP_RIFF);
        writeInt32LE(result, riffContentSize);
        result.write(WEBP_MARKER);
        result.write(payloadBytes);
        return result.toByteArray();
    }

    private int readInt32LE(InputStream in) throws IOException {
        int b0 = in.read(), b1 = in.read(), b2 = in.read(), b3 = in.read();
        if (b3 < 0) return -1;
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private void writeInt32LE(OutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) return false;
        }
        return true;
    }
}
