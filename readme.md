# Pictoprime4J

This is a program used to generate prime numbers from pictures, based on the 
[original version](https://github.com/TotalTechGeek/pictoprime) written in javascript.

The program follows the same principles and pipelines as the original, but it now converts images to ASCII and checks
for primality using plain-old Java, so dependencies like OpenSSL and GraphicsMagick are no longer needed.

Written in Java 19, but it should work with Java 15 and later, or perhaps Java 13 if the text blocks are removed.

## Example
```java
class TestDrive {
    public static void main(String[] args) {
        final String result = Pictoprime.create("path/to/image.png", 64, 0.9f, true);
        System.out.println(result);
    }
}
```
