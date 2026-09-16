/**
 * WORK IN PROGRESS -- Creating a Seed-Based Random Number Generator
 * 
 * Random numbers are a big part of building games and interesting experiences.
 * However, sometimes, you want the ability to use the same "random" values
 * more than once. For example, when Minecraft generates a world, it uses a seed.
 * The World generation appears random, but if you use the same seed, you will
 * get the same world each time, because the randomization will run exactly the same.
 * 
 * This can be useful in your games either where you want to be able to test particular
 * situations (if it's random every time, it can be harder to test), or where you want
 * to generate something randomly but also allow the user to repeat the same pattern, 
 * share it with their friends, etc.
 */

public class StringSeedRandom {
    private static final int MULTIPLIER = 23;
    private static final int MODULUS = 1000007;
    private int seed;
    private String seedWord;

    public StringSeedRandom (){
        String tempSeed = "";
        for (int i = 0; i < 6; i++){
            
        }
    }

    public StringSeedRandom(String seedWord) {
        this.seedWord = seedWord;
        seed = calculateSeed(seedWord);
    }

    private int calculateSeed(String seedWord) {
        int hash = 0;
        for (int i = 0; i < seedWord.length(); i++) {
            hash = (hash * MULTIPLIER + seedWord.charAt(i)) % MODULUS;
        }
        return hash;
    }

    public boolean nextBoolean() {
        return nextInt(2) == 1;
    }

    public int nextInt(int bound) {
        seed = (seed * MULTIPLIER) % MODULUS;
        return seed % bound;
    }

    public static void testNums (String seed){
        StringSeedRandom[] seeds = new StringSeedRandom[4];
        seeds[0] = new StringSeedRandom("aaaaaa");
        seeds[1] = new StringSeedRandom("aaaaaa");
        seeds[2] = new StringSeedRandom("bbbbbb");
        seeds[3] = new StringSeedRandom("bbbbbb");
        for (int i = 0; i < seeds.length; i++){
            System.out.print("i: " + i + " ==> ");
            for (int j = 0; j < 10; j++) {
                int randomInt = seeds[i].nextInt(100);
                System.out.print(randomInt + " ");
            }
            System.out.println();   
        }
    }

    public static void main(String[] args) {
        String seedWord = "banana"; // Replace with your desired seed word
        StringSeedRandom generator = new StringSeedRandom(seedWord);

        for (int i = 0; i < 10; i++) {
            int randomInt = generator.nextInt(100);
            System.out.println(randomInt);
        }
    }

    private String getStringSeed (){
        return seedWord;
    }

}
