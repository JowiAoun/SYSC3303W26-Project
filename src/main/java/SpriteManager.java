import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Utility for loading and slicing user-provided sprite sheets.
 * Caches BufferedImage arrays statically for fast rendering.
 */
public class SpriteManager {
    public static BufferedImage mapSprite;
    public static BufferedImage[] droneSprites;
    public static BufferedImage[] fireSprites;
    public static BufferedImage[] truckSprites;
    public static BufferedImage[] portraitSprites;

    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        try {
            mapSprite = ImageIO.read(new File("assets/map.png"));
            
            BufferedImage droneSheet = ImageIO.read(new File("assets/drone.png"));
            droneSprites = sliceAndCrop(droneSheet, 4, 2);
            
            BufferedImage fireSheet = ImageIO.read(new File("assets/fire.png"));
            fireSprites = slice(fireSheet, 6, 3);
            
            BufferedImage truckSheet = ImageIO.read(new File("assets/truck.png"));
            truckSprites = slice(truckSheet, 2, 2);

            BufferedImage portraitSheet = ImageIO.read(new File("assets/character1.png"));
            portraitSprites = slice(portraitSheet, 3, 2);
            
            initialized = true;
        } catch (IOException e) {
            System.err.println("SpriteManager: Could not load sprite assets. Tactical Map will gracefully degrade.");
        }
    }

    private static BufferedImage[] slice(BufferedImage sheet, int cols, int rows) {
        int w = sheet.getWidth() / cols;
        int h = sheet.getHeight() / rows;
        BufferedImage[] sprites = new BufferedImage[cols * rows];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                sprites[y * cols + x] = sheet.getSubimage(x * w, y * h, w, h);
            }
        }
        return sprites;
    }

    /**
     * Slice a sprite sheet then auto-crop each frame to its visible (alpha > 0)
     * bounding box. This eliminates positional drift between frames that causes
     * "teleporting" when rendering at a fixed location.
     */
    private static BufferedImage[] sliceAndCrop(BufferedImage sheet, int cols, int rows) {
        int fw = sheet.getWidth() / cols;
        int fh = sheet.getHeight() / rows;
        BufferedImage[] sprites = new BufferedImage[cols * rows];

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                BufferedImage frame = sheet.getSubimage(col * fw, row * fh, fw, fh);

                // Find alpha bounding box
                int minX = fw, maxX = 0, minY = fh, maxY = 0;
                for (int py = 0; py < fh; py++) {
                    for (int px = 0; px < fw; px++) {
                        int alpha = (frame.getRGB(px, py) >> 24) & 0xFF;
                        if (alpha > 10) {
                            if (px < minX) minX = px;
                            if (px > maxX) maxX = px;
                            if (py < minY) minY = py;
                            if (py > maxY) maxY = py;
                        }
                    }
                }

                if (minX <= maxX && minY <= maxY) {
                    long sumX = 0;
                    int count = 0;
                    for (int py = minY; py <= maxY; py++) {
                        for (int px = minX; px <= maxX; px++) {
                            int alpha = (frame.getRGB(px, py) >> 24) & 0xFF;
                            if (alpha > 20) {
                                sumX += px;
                                count++;
                            }
                        }
                    }
                    int anchorX = count > 0 ? (int)(sumX / count) : (minX + maxX) / 2;
                    int leftExtent = anchorX - minX;
                    int rightExtent = maxX - anchorX;
                    int halfWidth = Math.max(leftExtent, rightExtent);
                    int paddedWidth = halfWidth * 2 + 1;
                    int croppedHeight = maxY - minY + 1;

                    BufferedImage padded = new BufferedImage(paddedWidth, croppedHeight, BufferedImage.TYPE_INT_ARGB);
                    padded.getGraphics().drawImage(frame.getSubimage(minX, minY, maxX - minX + 1, croppedHeight), halfWidth - leftExtent, 0, null);
                    sprites[row * cols + col] = padded;
                } else {
                    // Empty frame fallback
                    sprites[row * cols + col] = frame;
                }
            }
        }
        return sprites;
    }
}
