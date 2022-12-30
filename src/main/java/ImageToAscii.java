import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.RescaleOp;
import java.util.List;

/**
 * Based on: https://github.com/IonicaBizau/pixel-class/blob/master/lib/index.js
 */
public class ImageToAscii {
    // A list of pixels to use, ordered by intensity from least to greatest
    private final List<String> glyphs;
    
    public String convertImageToAscii(BufferedImage image, int targetWidth, float contrastScaleFactor) {
        image = adjustImage(image, targetWidth, contrastScaleFactor);
        
        final Pixel[] pixels = getARGBPixels(image);
        
        StringBuilder resultBuilder = new StringBuilder();
        float invMaxIntensity = 1f / (3 * 255);
        for(Pixel p: pixels) {
            float relativeIntensity = p.intensity() * invMaxIntensity;
            String correspondingGlyph = glyphs.get(Math.round(relativeIntensity * (glyphs.size() - 1)));
            resultBuilder.append(correspondingGlyph);
        }
        
        return resultBuilder.toString();
    }
    
    public String convertImageToAscii(BufferedImage image) {
        return convertImageToAscii(image, image.getWidth(), 0.9f);
    }
    
    public BufferedImage adjustImage(BufferedImage image, int targetWidth, float contrastScaleFactor) {
        // Unify image type
        if(image.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage argbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            argbImage.getGraphics().drawImage(image, 0, 0, null);
            argbImage.getGraphics().dispose();
            image = argbImage;
        }
        
        float resizeRatio = (float) targetWidth / image.getWidth();
        image = resize(image, targetWidth, Math.round(image.getHeight() * resizeRatio / 2));
        RescaleOp rescaleOp = new RescaleOp(contrastScaleFactor, 0, null);
        rescaleOp.filter(image, image);
        
        return image;
    }
    
    public ImageToAscii(List<String> glyphs) {
        this.glyphs = glyphs;
    }
    
    public ImageToAscii() {
        this(List.of(" .,:;i1tfLCG08@".split("")));
    }
    
    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage img = new BufferedImage(width, height, source.getType());
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return img;
    }
    
    private static Pixel[] getARGBPixels(BufferedImage image) {
        final int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        final int width = image.getWidth();
        final int height = image.getHeight();
        
        Pixel[] result = new Pixel[height * width];
        
        for(int i = 0; i < result.length; i++) {
            final int a = (pixels[i] >> 24) & 0xff;
            final int r = (pixels[i] >> 16) & 0xff;
            final int g = (pixels[i] >> 8) & 0xff;
            final int b = pixels[i] & 0xff;
            result[i] = new Pixel(a, r, g, b);
        }
        return result;
    }
    
    public static class Pixel{
        public final int depth;
        public final int a;
        public final int r;
        public final int g;
        public final int b;
        
        public Pixel(int a, int r, int g, int b) {
            this.depth = 4;
            this.a = a;
            this.r = r;
            this.g = g;
            this.b = b;
        }
        
        public Pixel(int r, int g, int b) {
            this.depth = 3;
            this.a = 1;
            this.r = r;
            this.g = g;
            this.b = b;
        }
        
        public float intensity() {
            return (this.r + this.g + this.b) * this.a / 255f;
        }
    
        @Override
        public String toString() {
            return String.format("[a: %d, r: %d, g: %d, b: %d]", a, r, g, b);
        }
    }
}
