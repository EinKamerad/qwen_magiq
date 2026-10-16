package rorlite.engine;

import java.util.Random;

/** Глобальный RNG игры. */
public final class Rng {
    public static final Random R = new Random();

    private Rng() {}

    public static double d(double min, double max) { return min + R.nextDouble() * (max - min); }
    public static int i(int min, int maxIncl) { return min + R.nextInt(maxIncl - min + 1); }
    public static boolean chance(double p) { return R.nextDouble() < p; }
    public static <T> T pick(T[] arr) { return arr[R.nextInt(arr.length)]; }
    public static <T> T pick(java.util.List<T> list) { return list.get(R.nextInt(list.size())); }
}
