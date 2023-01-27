import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.util.List;

public class Pictoprime {
    public static String create(String filePath, int widths, float contrastScaleFactor, boolean sophie) throws IOException {
        BufferedImage image;
        URL imageUrl = Pictoprime.class.getResource(filePath);

        if(imageUrl == null)
            throw new RuntimeException("Image URL from file path is null");

        image = ImageIO.read(imageUrl);

        if(image == null)
            throw new RuntimeException("Loaded image is null");
        
        return create(image, widths, contrastScaleFactor, sophie);
    }
    
    public static String create(String filePath) throws IOException {
        return create(filePath, 32, 0.9f, false);
    }

    public static String create(BufferedImage image, int widths, float contrastScaleFactor, boolean sophie) {
        String imageNum = ImageToAscii.convertImageToAscii(image, widths, contrastScaleFactor, List.of("8049922777".split("")));

        System.out.println(formatPrime(imageNum, widths));

        String primeImage = PrimeSearch.findPrime(new BigInteger(imageNum), sophie);

        return formatPrime(primeImage, widths);
    }

    public static String create(BufferedImage image) {
        return create(image, 32, 0.9f, false);
    }
    
    public static String formatPrime(String prime, int width) {
        return prime.replaceAll(String.format(".{%d}", width),  "$0\n");
    }
}
