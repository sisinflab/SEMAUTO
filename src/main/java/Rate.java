/**
 * Created by bellini on 27/03/17.
 */

public class Rate implements Comparable<Rate> {
    private static final int min = 0;
    private static final int max = 1;
    private static final int A = 1;
    private static final int B = 5;

    private Integer itemId;
    private Double rate;

    Rate(Integer itemId, Double rate) {
        this.itemId = itemId;
        this.rate = rate;
    }

    public Integer getItemId() {
        return itemId;
    }

    public void setItemId(Integer itemId) {
        this.itemId = itemId;
    }

    public Double getRate() {
        return rate;
    }

    public void setRate(Double rate) {
        this.rate = rate;
    }

    public static double getScaledDown(double x) {
        return (((max - min) * (x - A)) / (B - A)) + min;
    }

    public static double getScaledUp(double x) {
        return (((B - A) * (x - min)) / (max - min)) + A;
    }

    @Override
    public String toString() {
        return String.format("%d:%f", itemId, rate);
    }

    @Override
    public int compareTo(Rate t) {
        return Double.compare(t.rate, this.rate);
    }
}