import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.math.BigInteger;
import java.net.URL;
import java.util.List;

public class Pictoprime {
    public static String create(String filePath, int widths, float contrastScaleFactor, boolean sophie) {
        BufferedImage image;
        try {
            URL imageUrl = Pictoprime.class.getResource(filePath);
            if(imageUrl != null)
                image = ImageIO.read(imageUrl);
            else
                throw new RuntimeException();
        } catch(Exception e) {
            e.printStackTrace();
            return "Unable to load image";
        }
        
        ImageToAscii converter = new ImageToAscii(List.of("8049922777".split("")));
        String imageNum = converter.convertImageToAscii(image, widths, contrastScaleFactor);
        
        System.out.println(formatPrime(imageNum, widths));
        
        String primeImage = PrimeSearch.findPrime(new BigInteger(imageNum), sophie);
        return formatPrime(primeImage, widths);
    }
    
    public static String create(String filePath) {
        return create(filePath, 128, 0.9f, false);
    }
    
    public static String formatPrime(String prime, int width) {
        return prime.replaceAll(String.format(".{%d}", width),  "$0\n");
    }
}