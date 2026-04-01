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

    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        try {
            mapSprite = ImageIO.read(new File("assets/map.png"));
            
            BufferedImage droneSheet = ImageIO.read(new File("assets/drone.png"));
            droneSprites = slice(droneSheet, 8, 4);
            
            BufferedImage fireSheet = ImageIO.read(new File("assets/fire.png"));
            fireSprites = slice(fireSheet, 6, 3);
            
            BufferedImage truckSheet = ImageIO.read(new File("assets/truck.png"));
            truckSprites = slice(truckSheet, 2, 2);
            
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
}
