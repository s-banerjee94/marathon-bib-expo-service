package com.timekeeper.bibexpo.participantaccess.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

@Component
public class QrImageGenerator {

    private static final int QR_SIZE = 300;

    /**
     * Renders the smallest possible QR as a 1-bit PNG (one pixel per module) and returns it as a
     * {@code data:image/png;base64,...} URI for inline embedding. Display it upscaled on the client
     * with {@code image-rendering: pixelated}.
     */
    public String toCompactDataUri(String content) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(encode(content, 1));
    }

    public byte[] toPng(String content) {
        return encode(content, QR_SIZE);
    }

    private byte[] encode(String content, int size) {
        try {
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M
            );
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);

            int width = matrix.getWidth();
            int height = matrix.getHeight();
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("QR image generation failed.", e);
        }
    }
}
