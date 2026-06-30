package com.dailyexpense.expense;

import com.dailyexpense.expense.port.CategoryLookupPort;
import com.dailyexpense.expense.port.CategoryValidationResponse;
import com.dailyexpense.expense.port.StoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T060/T061 gate — ReceiptIT.
 * SECURITY MUSTs:
 * - magic-byte type validation (not Content-Type)
 * - EXIF stripped: stored bytes have 0 EXIF APP1 segments
 * - ≤ 5 MB size limit
 * - ≤ 25 MP pixel-flood guard
 * - foreign expense → 403
 * - GET: Content-Disposition: inline + X-Content-Type-Options: nosniff
 * - DELETE removes object+row; Expense retained
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReceiptIT extends AbstractExpenseServiceIT {

    @MockBean
    CategoryLookupPort categoryLookupPort;

    @MockBean
    StoragePort storagePort;

    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();

    @BeforeEach
    void setupMocks() {
        when(categoryLookupPort.validate(any(), any(), any()))
            .thenReturn(new CategoryValidationResponse(CATEGORY_ID, "Food", "EXPENSE", "NONE"));
        doNothing().when(storagePort).store(anyString(), any(), anyString());
        when(storagePort.retrieve(anyString()))
            .thenReturn(new ByteArrayInputStream(minimalJpeg()));
    }

    @Test
    void upload_validJpeg_returns201AndExifStripped() throws Exception {
        UUID expenseId = createExpenseGetId(USER_A);
        byte[] jpegWithExif = createJpegWithFakeExif();

        mockMvc.perform(multipart("/api/v1/expenses/{id}/receipt", expenseId)
                .file(new MockMultipartFile("file", "r.jpg", "image/jpeg", jpegWithExif))
                .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_A)))
            .andExpect(status().isCreated());

        // Capture stored bytes — assert 0 EXIF APP1 segments
        ArgumentCaptor<byte[]> cap = ArgumentCaptor.forClass(byte[].class);
        verify(storagePort).store(anyString(), cap.capture(), eq("image/jpeg"));
        byte[] stored = cap.getValue();
        assertThat(countExifApp1Segments(stored)).isZero();
    }

    @Test
    void upload_pdfMagicBytes_returns400() throws Exception {
        UUID expenseId = createExpenseGetId(USER_A);
        byte[] pdf = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};

        mockMvc.perform(multipart("/api/v1/expenses/{id}/receipt", expenseId)
                .file(new MockMultipartFile("file", "r.pdf", "image/jpeg", pdf))
                .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_A)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void upload_exceedsMaxSize_returns400() throws Exception {
        UUID expenseId = createExpenseGetId(USER_A);
        // 5 MB + 1 byte of JPEG magic then zeros — magic bytes valid but size > 5 MB
        byte[] tooBig = new byte[5 * 1024 * 1024 + 1];
        tooBig[0] = (byte) 0xFF; tooBig[1] = (byte) 0xD8; tooBig[2] = (byte) 0xFF;

        mockMvc.perform(multipart("/api/v1/expenses/{id}/receipt", expenseId)
                .file(new MockMultipartFile("file", "big.jpg", "image/jpeg", tooBig))
                .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_A)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void upload_foreignExpense_returns403() throws Exception {
        UUID expenseId = createExpenseGetId(USER_A);

        mockMvc.perform(multipart("/api/v1/expenses/{id}/receipt", expenseId)
                .file(new MockMultipartFile("file", "r.jpg", "image/jpeg", minimalJpeg()))
                .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_B)))
            .andExpect(status().isForbidden());
    }

    @Test
    void upload_nonExistentExpense_returns403() throws Exception {
        // 403-never-404 (INV-1)
        mockMvc.perform(multipart("/api/v1/expenses/{id}/receipt", UUID.randomUUID())
                .file(new MockMultipartFile("file", "r.jpg", "image/jpeg", minimalJpeg()))
                .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_A)))
            .andExpect(status().isForbidden());
    }

    @Test
    void get_receipt_returnsSecureHeaders() throws Exception {
        UUID expenseId = createExpenseGetId(USER_A);
        uploadReceipt(expenseId, USER_A);

        mockMvc.perform(get("/api/v1/expenses/{id}/receipt", expenseId)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_A)))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"receipt\""))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void get_foreignReceipt_returns403() throws Exception {
        UUID expenseId = createExpenseGetId(USER_A);
        uploadReceipt(expenseId, USER_A);

        mockMvc.perform(get("/api/v1/expenses/{id}/receipt", expenseId)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_B)))
            .andExpect(status().isForbidden());
    }

    @Test
    void delete_removesReceiptExpenseRetained() throws Exception {
        UUID expenseId = createExpenseGetId(USER_A);
        uploadReceipt(expenseId, USER_A);

        doNothing().when(storagePort).delete(anyString());

        mockMvc.perform(delete("/api/v1/expenses/{id}/receipt", expenseId)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(USER_A)))
            .andExpect(status().isNoContent());

        // Expense still accessible
        mockMvc.perform(get("/api/v1/expenses/{id}", expenseId)
                .headers(authHeaders(USER_A)))
            .andExpect(status().isOk());

        // Storage delete was called
        verify(storagePort, atLeastOnce()).delete(anyString());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private UUID createExpenseGetId(UUID userId) throws Exception {
        var result = mockMvc.perform(post("/api/v1/expenses")
                .headers(authHeaders(userId))
                .content(expenseJson("200.00", "2026-06-28", CATEGORY_ID.toString(), "UPI")))
            .andExpect(status().isCreated())
            .andReturn();
        String loc = result.getResponse().getHeader("Location");
        return UUID.fromString(loc.substring(loc.lastIndexOf('/') + 1));
    }

    private void uploadReceipt(UUID expenseId, UUID userId) throws Exception {
        mockMvc.perform(multipart("/api/v1/expenses/{id}/receipt", expenseId)
                .file(new MockMultipartFile("file", "r.jpg", "image/jpeg", minimalJpeg()))
                .header(HttpHeaders.AUTHORIZATION, bearerToken(userId)))
            .andExpect(status().isCreated());
    }

    /** Minimal valid JPEG (10×10 pixels, no EXIF). */
    private byte[] minimalJpeg() {
        try {
            BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "jpeg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a valid JPEG with a fake APP1 (EXIF) segment injected after SOI.
     * EXIF magic: FF E1 [length] "Exif\0\0" [minimal TIFF header]
     */
    private byte[] createJpegWithFakeExif() {
        byte[] plain = minimalJpeg();
        byte[] exifPayload = new byte[]{
            'E', 'x', 'i', 'f', 0, 0,                         // Exif identifier
            0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00,   // Little-endian TIFF header
            0x00, 0x00                                          // 0 IFD entries
        };
        int len = 2 + exifPayload.length;                       // APP1 length = size of length field + payload
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(plain, 0, 2);                                 // SOI: FF D8
        out.write(0xFF); out.write(0xE1);                       // APP1 marker
        out.write((len >> 8) & 0xFF); out.write(len & 0xFF);    // length big-endian
        try { out.write(exifPayload); } catch (Exception e) { throw new RuntimeException(e); }
        out.write(plain, 2, plain.length - 2);                  // rest of JPEG
        return out.toByteArray();
    }

    /**
     * Counts JPEG APP1 segments that carry "Exif\0\0" identifier.
     * Used to assert 0 EXIF segments in stored bytes.
     */
    private int countExifApp1Segments(byte[] jpeg) {
        int count = 0;
        try (InputStream in = new ByteArrayInputStream(jpeg)) {
            if (in.read() != 0xFF || in.read() != 0xD8) return 0; // not JPEG SOI
            while (in.available() >= 4) {
                int m0 = in.read() & 0xFF;
                if (m0 != 0xFF) break;
                int m1 = in.read() & 0xFF;
                if (m1 == 0xD9) break; // EOI
                if (m1 == 0xDA) break; // SOS
                int lenHi = in.read() & 0xFF, lenLo = in.read() & 0xFF;
                int segLen = (lenHi << 8) | lenLo;
                int payloadLen = segLen - 2;
                if (m1 == 0xE1 && payloadLen >= 6) {
                    byte[] peek = new byte[6];
                    int read = in.read(peek);
                    if (read == 6
                        && peek[0] == 'E' && peek[1] == 'x' && peek[2] == 'i'
                        && peek[3] == 'f' && peek[4] == 0 && peek[5] == 0) {
                        count++;
                    }
                    in.skip(payloadLen - (read < 0 ? 0 : read));
                } else {
                    in.skip(payloadLen);
                }
            }
        } catch (Exception ignored) {}
        return count;
    }
}
