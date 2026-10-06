package net.lunacy.visuals.render;
/** Smoothed, bounded controller; recovery is slower than load shedding. */
public final class AdaptiveBudget {
    private double factor = 1, average;
    public double sample(double fps, double target) {
        if (!Double.isFinite(fps) || fps <= 0 || target <= 0) return factor;
        average = average == 0 ? fps : average * .85 + fps * .15;
        if (average < target * .90) factor = Math.max(.20, factor - .035);
        else if (average > target * 1.06) factor = Math.min(1, factor + .008);
        return factor;
    }
    public double factor() { return factor; }
    public void reset() { factor = 1; average = 0; }
}
