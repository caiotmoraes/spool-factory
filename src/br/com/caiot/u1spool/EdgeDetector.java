package br.com.caiot.u1spool;

/** Only fresh rising edges after arming count. All timing uses a monotonic clock. */
public final class EdgeDetector {
    private boolean[] previous;
    private int mask;
    private long deadline, firstEdge;
    public boolean armed;
    public void arm(boolean[] baseline, long now, long timeout) {
        previous = baseline.clone(); mask = 0; firstEdge = -1;
        deadline = now + timeout; armed = true;
    }
    public void cancel() { armed = false; }
    /** -1 waiting; -2 ambiguous; -3 expired; -4 removed before stable; 0..3 selected. */
    public int update(boolean[] current, long now) {
        if (!armed) return -1;
        if (now >= deadline) { cancel(); return -3; }
        for (int i=0;i<4;i++) {
            if (current[i] && !previous[i]) {
                mask |= 1 << i;
                if (firstEdge < 0) firstEdge = now;
            }
        }
        previous = current.clone();
        if (Integer.bitCount(mask)>1) { cancel(); return -2; }
        if (mask!=0 && now-firstEdge>=1200) {
            int ch=Integer.numberOfTrailingZeros(mask);
            if (!current[ch]) { cancel(); return -4; }
            return ch;
        }
        return -1;
    }
}
