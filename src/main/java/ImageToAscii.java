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
    // Make method parameter
    public static final List<String> DEFAULT_GLYPHS = List.of(" .,:;i1tfLCG08@".split(""));
    
    public static String convertImageToAscii(BufferedImage image, int targetWidth, float contrastScaleFactor, List<String> glyphs) {
        image = adjustImage(image, targetWidth, contrastScaleFactor);
        
        final IntARGBPixel[] pixels = getARGBPixels(image);
        
        StringBuilder resultBuilder = new StringBuilder();
        
        for(IntARGBPixel p: pixels) {
            String correspondingGlyph = glyphs.get(Math.round(p.quantize(glyphs.size()).intensity));
            resultBuilder.append(correspondingGlyph);
        }
        
        return resultBuilder.toString();
    }
    
    public static String convertImageToAscii(BufferedImage image) {
        return convertImageToAscii(image, image.getWidth(), 0.9f, DEFAULT_GLYPHS);
    }
    
    private static BufferedImage adjustImage(BufferedImage image, int targetWidth, float contrastScaleFactor) {
        BufferedImage img = image;
        // Unify image type
        // Possible Colored version in the future (?)
        img = convertToIntARGB(img);
        
        float resizeRatio = (float) targetWidth / img.getWidth();
        img = resize(img, targetWidth, Math.round(img.getHeight() * resizeRatio * 0.5f));
        RescaleOp rescaleOp = new RescaleOp(contrastScaleFactor, 0, null);
        rescaleOp.filter(img, img);
        
        return img;
    }
    
    private static BufferedImage convertToIntARGB(BufferedImage image) {
        if(image.getType() == BufferedImage.TYPE_INT_ARGB)
            return image;
        
        BufferedImage img = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        img.getGraphics().drawImage(image, 0, 0, null);
        img.getGraphics().dispose();
        return img;
    }
    
    private static BufferedImage resize(BufferedImage source, int width, int height, Object renderingHint) {
        BufferedImage img = new BufferedImage(width, height, source.getType());
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, renderingHint);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return img;
    }
    
    private static BufferedImage resize(BufferedImage source, int width, int height) {
        return resize(source, width, height, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    }
    
    private static IntARGBPixel[] getARGBPixels(BufferedImage image) {
        final int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        final int width = image.getWidth();
        final int height = image.getHeight();
        
        IntARGBPixel[] result = new IntARGBPixel[height * width];
        
        for(int i = 0; i < result.length; i++) {
            final int a = (pixels[i] >> 24) & 0xff;
            final int r = (pixels[i] >> 16) & 0xff;
            final int g = (pixels[i] >> 8) & 0xff;
            final int b = pixels[i] & 0xff;
            result[i] = new IntARGBPixel(a, r, g, b);
        }
        return result;
    }
    
    public static class IntARGBPixel {
        private static final float ONE_THIRD = 1 / 3f;
        private static final float INV_MAX = 1 / 255f;
    
        public final float intensity;
        public final int depth;
        public final int a;
        public final int r;
        public final int g;
        public final int b;
        
        
        public IntARGBPixel(int a, int r, int g, int b) {
            this.intensity = (r + g + b) * ONE_THIRD * a * INV_MAX;
            this.depth = 4;
            this.a = a;
            this.r = r;
            this.g = g;
            this.b = b;
        }
        
        public IntARGBPixel quantize(int numColour) {
            int r = Math.round((INV_MAX*this.r) * (numColour - 1));
            int g = Math.round((INV_MAX*this.g) * (numColour - 1));
            int b = Math.round((INV_MAX*this.b) * (numColour - 1));
            return new IntARGBPixel(this.a, r, g, b);
        }
    
        @Override
        public String toString() {
            return String.format("[a: %d, r: %d, g: %d, b: %d, I: %.2f]", a, r, g, b, intensity);
        }
    }
}
