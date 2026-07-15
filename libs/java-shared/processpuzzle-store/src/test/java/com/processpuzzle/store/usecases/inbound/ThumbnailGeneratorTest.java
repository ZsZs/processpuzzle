package com.processpuzzle.store.usecases.inbound;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThumbnailGeneratorTest {

    private final ThumbnailGenerator generator = new ThumbnailGenerator();

    @Test
    void generate_shouldProduceScaledJpegThumbnail() throws IOException {
        byte[] source = pngImage(120, 90);

        byte[] thumbnail = generator.generate(source, 32, 0.8);

        assertNotNull(thumbnail);
        assertTrue(thumbnail.length > 0);
        // JPEG magic number
        assertEquals((byte) 0xFF, thumbnail[0]);
        assertEquals((byte) 0xD8, thumbnail[1]);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(thumbnail));
        assertNotNull(decoded);
        assertTrue(decoded.getWidth() <= 32);
        assertTrue(decoded.getHeight() <= 32);
    }

    @Test
    void generate_shouldThrowWhenSourceIsNotAnImage() {
        assertThrows(IOException.class, () -> generator.generate("not-an-image".getBytes(), 32, 0.8));
    }

    private static byte[] pngImage(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
