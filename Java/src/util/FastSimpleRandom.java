package util;

public class FastSimpleRandom {
    // 64 byte cache lines are typical, so there are 8 slots per cache line.
    // This means that the probability that any two threads have false sharing is
    // p = 8 / #slots.  If there are n processors, each of which is running 1
    // thread, then the probability that no other threads have false sharing with
    // the current thread is (1-p)^(n-1).  If p is small, that is about
    // 1 - (n-1)p, which is pretty close to 1 - np.  If we want the probability
    // of false conflict for a thread to be less than k, then we need np < k, or
    // p < k/n, or 8/Slots < k/n, or #slots > 8n/k.  If we let k = 1/8, then we
    // get #slots=64*n.
    private static final int Mask;

    private static final long[] states;

    static {
        final int min = 64 * Runtime.getRuntime().availableProcessors();
        int slots = 1;
        while (slots < min)
            slots *= 2;
        Mask = slots - 1;
        states = new long[Mask + 1];
        for (int i = 0; i < Mask + 1; ++i) {
            states[i] = i * 0x123456789abcdefL;
        }
    }

    public static int nextInt() {
        final int id = (((int) Thread.currentThread().getId()) * 13) & Mask;

        final long next = step(states[id]);
        states[id] = next;

        return extract(next);
    }

    private static long step(final long x) {
        return x * 2862933555777941757L + 3037000493L;
    }

    private static int extract(final long x) {
        return (int)(x >> 30);
    }
}
