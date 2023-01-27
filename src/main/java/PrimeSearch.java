import com.google.gson.Gson;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class PrimeSearch {
    private static final Random RANDOM = new Random();
    
    private static final List<BigInteger> SMALL_PRIMES = generatePrimes(17389)
            .stream().map(BigInteger::valueOf).toList();
    
    private static final Settings settings = loadSettings();
    
    private static final Map<Character, Character[]> ALLOWED_MODIFICATIONS = settings.getAllowedModifications();
    
    private static final Map<Character, String> LAST_DIGIT_SUBSTITUTION = settings.getLastDigitModification();
    
    private static final int CORE_COUNT = Runtime.getRuntime().availableProcessors();
    private static final ExecutorService executors = Executors.newFixedThreadPool(CORE_COUNT);
    
    /**
     * Searches for a prime number by adjusting the original number.
     */
    public static String findPrime(BigInteger original, boolean sophie) {
        long startTime = System.nanoTime();
        
        original = swapLastDigit(original);
        
        BigInteger keyFrame = original;
        int attempts = 0;
        int failedViable = 0;
        
        Set<BigInteger> tested = new HashSet<>();
        int rekeyAt = keyFrame.toString().length();
        
        RekeyChecker checker = new RekeyChecker(rekeyAt, 4*rekeyAt, 160);
        while(true) {
            List<BigInteger> tests = generateTests(tested, keyFrame);
            
            try {
                BigInteger result = executors.invokeAny(tests.stream().map(i -> (Callable<BigInteger>)
                        () -> {
                            if(i.isProbablePrime(1))
                                return i;
                            throw new RuntimeException("Not Prime");
                        }).collect(Collectors.toList()));
    
                executors.shutdownNow();
                long timeTakenMilli = TimeUnit.MILLISECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
                
                if(sophie) {
                    System.out.println("Trying to find almost Sophie Germain");
                    BigInteger sophieGermain = findAlmostSophieGermain(result);
                    if(sophieGermain != null) {
                        System.out.printf("""
                                {
                                    prime: %s,
                                    attempts: %d,
                                    simultaneous: %d,
                                    distinctTested: %d,
                                    sophieGermain: %s,
                                    Time: %d ms
                                }
                                %n""", result, attempts, CORE_COUNT, tested.size(), sophieGermain, timeTakenMilli);
                        return result.toString();
                    }
                    System.out.println("Did not find any almost Sophie Germain");
                }
                System.out.printf("""
                        {
                            prime: %s,
                            attempts: %d,
                            simultaneous: %d,
                            distinctTested: %d,
                            Time: %d ms
                        }
                        """, result, attempts, CORE_COUNT, tested.size(), timeTakenMilli);
                return result.toString();
            } catch(Exception ignored) {}
            
            attempts++;
            if(tests.isEmpty())
                failedViable++;
            
            switch(checker.checkAndUpdateKeyFrame(tested, failedViable)) {
                case REKEY -> keyFrame = generateExtraTest(tested, keyFrame);
                case RESTART -> keyFrame = generateExtraTest(tested, original);
                case DEGENERATE -> keyFrame = original =
                        replaceRandomCharacter(original,
                                new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'});
            }
        }
    }
    
    public static String findPrime(BigInteger original) {
        return findPrime(original, false);
    }
    
    private static BigInteger swapLastDigit(BigInteger original) {
        String strOriginal = original.toString();
        char lastDigit = strOriginal.charAt(strOriginal.length() - 1);
        if(LAST_DIGIT_SUBSTITUTION.containsKey(lastDigit)) {
            strOriginal = strOriginal.substring(0, strOriginal.length() - 1)
                    + LAST_DIGIT_SUBSTITUTION.get(lastDigit);
            return new BigInteger(strOriginal);
        }
        return original;
    }
    
    private static String formatReport(BigInteger result, int attempts, int numTested, boolean sophie, long startTime) {
        long timeTakenMilli = TimeUnit.MILLISECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
        String report = String.format("""
                        {
                            prime: %s,
                            attempts: %d,
                            simultaneous: %d,
                            distinctTested: %d,
                            Time: %d ms
                        }
                        """, result, attempts, CORE_COUNT, numTested, startTime);
    
        if(sophie) {
            BigInteger sophieGermain = findAlmostSophieGermain(result);
            if(sophieGermain != null)
                return String.format("""
                            {
                                prime: %s,
                                attempts: %d,
                                simultaneous: %d,
                                distinctTested: %d,
                                sophieGermain: %s,
                                Time: %d ms
                            }
                        """, result, attempts, CORE_COUNT, numTested, sophieGermain, startTime);
        }
        
        return report;
    }
    
    /**
     * Generates a collection of tests that are not in the tested set.
     * This is used so we may execute the tests in parallel.
     */
    private static List<BigInteger> generateTests(Set<BigInteger> tested, BigInteger keyFrame) {
        List<BigInteger> arr = new ArrayList<>();
        
        for(int i = 0, c = 0; i < CORE_COUNT && c <= 256; i++, c++) {
            arr.add(i, null);
            do {
                arr.set(i, generateExtraTest(tested, keyFrame));
                tested.add(arr.get(i));
                c++;
            } while(!isNotDivisibleBySmallPrimes(arr.get(i)) && c <= 256 && tested.size() <= keyFrame.toString().length());
        }
        
        
        if(arr.size() == 1)
            return arr.stream().filter(PrimeSearch::isNotDivisibleBySmallPrimes).collect(Collectors.toList());
        return arr;
    }
    
    /**
     * Tries to generate a test that is not in the tested set.
     * The algorithm is written rather naively, but this is fine (although inefficient) because
     * it takes up very little of the CPU time. The real crunch comes from the prime checking :)
     */
    private static BigInteger generateExtraTest(Set<BigInteger> tested, BigInteger keyFrame) {
        BigInteger val;
        int c = 0;
        do {
            val = replaceRandomCharacter(keyFrame);
        } while(tested.contains(val) && c++ <= 256);
        return val;
    }
    
    /**
     * Replaces one of the characters in the keyframe with a random character.
     */
    private static BigInteger replaceRandomCharacter(BigInteger keyFrame) {
        return replaceRandomCharacter(keyFrame, null);
    }
    
    private static BigInteger replaceRandomCharacter(BigInteger keyFrame, char[] specified) {
        int index = findModifiableCharIndex(keyFrame);
        
        // replace that position in the string
        String keyFrameString = keyFrame.toString();
        char[] replacements = specified != null ?
                specified :
                toPrimitive(ALLOWED_MODIFICATIONS.get(keyFrameString.charAt(index)));
        char replaceWith = replacements[RANDOM.nextInt(replacements.length)];
        
        keyFrameString = keyFrameString.substring(0, index) + replaceWith + keyFrameString.substring(index + 1);
        return new BigInteger(keyFrameString);
    }
    
    /**
     * Finds a character in the provided keyframe that the algorithm is allowed to modify.
     */
    private static int findModifiableCharIndex(BigInteger keyFrame) {
        String strKeyFrame = keyFrame.toString();
        int index;
        do { // do not replace the last character; nextInt() is exclusive at the upper bound
            index = RANDOM.nextInt(strKeyFrame.length() - 1);
        } while(!ALLOWED_MODIFICATIONS.containsKey(strKeyFrame.charAt(index)));
        return index;
    }
    
    private static char[] toPrimitive(Character[] arr) {
        final char[] result = new char[arr.length];
        for(int i = 0; i < arr.length; i++)
            result[i] = arr[i];
        return result;
    }
    
    private static BigInteger findAlmostSophieGermain(BigInteger val) {
        AlmostSophieGermainMultipliersPseudoGenerator gen = new AlmostSophieGermainMultipliersPseudoGenerator();
        boolean done = false;
        while(!done) {
            List<BigInteger> tests = new ArrayList<>();
            for(int i = 0; i < CORE_COUNT; i++) {
                BigInteger next = gen.next();
                if(next.equals(BigInteger.valueOf(-1)))
                    done = true;
                else
                    tests.add(next);
            }
            Optional<BigInteger> possiblePrime = tests.stream().map(i -> i.multiply(val.add(BigInteger.ONE)))
                    .filter(PrimeSearch::isNotDivisibleBySmallPrimes).findAny();
            if(possiblePrime.isPresent())
                return possiblePrime.get();
        }
        
        return null;
    }
    
    private static boolean isNotDivisibleBySmallPrimes(BigInteger value) {
        for(BigInteger v : SMALL_PRIMES)
            if(value.remainder(v).equals(BigInteger.ZERO))
                return false;
        return true;
    }
    
    /**
     * Returns a list of primes from 2 to at most n using the Sieve of Eratosthenes.
     * https://en.wikipedia.org/wiki/Sieve_of_Eratosthenes
     * https://www.baeldung.com/java-generate-prime-numbers
     * @param n The upper bound of the primes to generate
     * @return  A list of primes smaller than n
     */
    public static List<Integer> generatePrimes(int n) {
        int sqrtN = (int) Math.sqrt(n);
        boolean[] nums = new boolean[n + 1];
        Arrays.fill(nums, true);
        
        for(int i = 2; i <= sqrtN; i++)
            if(nums[i])
                for(int j = 2*i; j <= n; j += i)
                    nums[j] = false;
        
        List<Integer> primes = new LinkedList<>();
        for(int i = 2; i < nums.length; i++)
            if(nums[i])
                primes.add(i);
        
        return primes;
    }
    
    private static Settings loadSettings() {
        try {
            URL settingsURL = PrimeSearch.class.getClassLoader().getResource("settings.json");

            if(settingsURL == null)
                return new Settings();

            String settingsJson =  new String(Files.readAllBytes(Paths.get(settingsURL.toURI())));
            Gson gson = new Gson();
            return gson.fromJson(settingsJson, Settings.class);
        } catch(IOException | URISyntaxException exception) {
            exception.printStackTrace();
        }
        return new Settings();
    }
    
    private static class Settings {
        private final Map<Character, Character[]> allowedModifications = new HashMap<>() {{
            put('0', new Character[] {'8', '9', '5'});
            put('1', new Character[] {'7'});
            put('2', new Character[] {'6'});
            put('4', new Character[] {'9'});
            put('5', new Character[] {'0'});
            put('6', new Character[] {'2'});
            put('7', new Character[] {'1'});
            put('8', new Character[] {'0', '9'});
            put('9', new Character[] {'4'});
        }};
        private final Map<Character, String> lastDigitModification = new HashMap<>() {{
            put('0', "3");
            put('2', "3");
            put('4', "9");
            put('6', "9");
            put('8', "9");
            put('5', "3");
        }};
    
        public Map<Character, Character[]> getAllowedModifications() {
            return allowedModifications;
        }
    
        public Map<Character, String> getLastDigitModification() {
            return lastDigitModification;
        }
    }
    
    private static class RekeyChecker {
        boolean shouldRekey;
        int rekeyCheck;
        int rekeyCheckNum;
        boolean shouldRestart;
        int restartCheck;
        int restartCheckNum;
        boolean shouldDegenerate;
        int degenerateCheck;
        int degenerateCheckNum;
        
        private enum rekeyState {
            NORMAL,
            REKEY,
            RESTART,
            DEGENERATE
        }
        
        private RekeyChecker(int rekeyCheck, int restartCheck, int degenerateCheck) {
            this.rekeyCheck = rekeyCheck;
            this.rekeyCheckNum = rekeyCheck;
            this.restartCheck = restartCheck;
            this.restartCheckNum = restartCheck;
            this.degenerateCheck = degenerateCheck;
            this.degenerateCheckNum = degenerateCheck;
        }
        
        private rekeyState checkAndUpdateKeyFrame(Set<BigInteger> tested, int failedViable) {
            shouldRekey = tested.size() > rekeyCheck;
            if(shouldRekey)
                rekeyCheck = rekeyCheckNum + (int) Math.floor(tested.size()/rekeyCheckNum)*rekeyCheckNum;
    
            shouldRestart = tested.size() > restartCheck;
            if(shouldRestart)
                restartCheck = restartCheckNum + (int) Math.floor(tested.size()/restartCheckNum)*restartCheckNum;
    
            shouldDegenerate = failedViable > degenerateCheck;
            if(shouldDegenerate)
                degenerateCheck = degenerateCheckNum + (int) Math.floor(tested.size()/degenerateCheckNum)*degenerateCheckNum;
    
            // If we reach the rekey point (every "rekeyAt" attempts), we adjust a single digit in the keyframe, and use that as the new keyframe.
            // This is used to try to prevent degradation of quality of the visual.
            if(tested.size() == 0 || shouldRekey)
                return rekeyState.REKEY;
            if(shouldRestart)
                return rekeyState.RESTART;
            if(shouldDegenerate)
                return rekeyState.DEGENERATE;
            return rekeyState.NORMAL;
        }
    }
    
    /**
     * This generates a series of multipliers that are multiplied to the original prime to try to find a new prime.
     */
    private static class AlmostSophieGermainMultipliersPseudoGenerator {
        private int index = -1;
        public BigInteger next() {
            index++;
            
            // 2, 4, 6, 8, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 200, 300, 400, 500, 600, 700, 800, 900
            return switch(index) {
                case 0:
                    yield BigInteger.TWO;
                case 1:
                    yield BigInteger.valueOf(4);
                case 2:
                    yield BigInteger.valueOf(6);
                case 3:
                    yield BigInteger.valueOf(8);
                default:
                    int j = ((index - 4) % 9) + 1;
                    int i = ((index - 4) / 9) + 1;
                    if(i > 32)  // Size Limit
                        yield BigInteger.valueOf(-1);
                    yield BigInteger.TEN.pow(i).multiply(BigInteger.valueOf(j));
            };
        }
    }
}
