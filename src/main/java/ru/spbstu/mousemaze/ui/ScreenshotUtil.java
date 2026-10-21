package ru.spbstu.mousemaze.ui;

import javax.imageio.ImageIO;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * Утилита сохранения Swing-компонента в PNG.
 */
public final class ScreenshotUtil {
    private ScreenshotUtil() {
    }

    /**
     * Сохраняет компонент в PNG-файл.
     */
    public static void save(Component component, File file) throws IOException {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(file, "file");
        int width = component.getWidth();
        int height = component.getHeight();
        if (width <= 0 || height <= 0) {
            throw new IllegalStateException("Компонент еще не имеет размеров для создания снимка");
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        component.printAll(g);
        g.dispose();
        ImageIO.write(image, "png", file);
    }
}
