package rupeerates;

import java.util.List;

/**
 * Conversion and remittance math. All rates here are ECB reference
 * (mid-market) rates — the rate Google shows, not the rate a bank pays.
 * The fee math is the point of RupeeRates: a bank or remittance service
 * quotes a lower rate (or charges a fee), and the difference between the
 * mid-market value and what actually lands is the hidden cost.
 */
public final class Converter {

    private Converter() {
    }

    /** Result of converting an amount at a mid-market rate. */
    public record Conversion(double amount, String from, String to, double rate,
                             double converted, String date) {
    }

    /** Result of converting with a fee/spread: what lands vs the mid-market value. */
    public record FeeResult(Conversion conversion, double feePct,
                            double midMarketValue, double afterFeeValue, double hiddenCost) {
    }

    /** Converts {@code amount} of {@code from} into {@code to} at the given cross rate. */
    public static Conversion convert(double amount, String from, String to, double rate, String date) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount must not be negative: " + amount);
        }
        if (rate <= 0) {
            throw new IllegalArgumentException("Rate must be positive: " + rate);
        }
        return new Conversion(amount, from, to, rate, amount * rate, date);
    }

    /**
     * Applies a fee/spread of {@code feePct} percent to a conversion.
     * The fee is taken as a percentage of the converted (target-currency)
     * amount: hidden cost = converted × feePct / 100.
     */
    public static FeeResult withFee(Conversion conversion, double feePct) {
        if (feePct < 0 || feePct > 100) {
            throw new IllegalArgumentException("Fee must be between 0 and 100 percent: " + feePct);
        }
        double hidden = conversion.converted() * feePct / 100.0;
        return new FeeResult(conversion, feePct, conversion.converted(),
                conversion.converted() - hidden, hidden);
    }

    /** Trend statistics over a series of daily rates, oldest → newest. */
    public record TrendStats(int days, String firstDate, double first, String lastDate, double last,
                             double min, double max, double avg, double changePct) {
    }

    /** Min / max / average / first→last change over the series points. */
    public static TrendStats trend(List<Double> values, List<String> dates) {
        if (values == null || values.isEmpty() || values.size() != dates.size()) {
            throw new IllegalArgumentException("Trend needs at least one dated value");
        }
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double sum = 0;
        for (double v : values) {
            min = Math.min(min, v);
            max = Math.max(max, v);
            sum += v;
        }
        double first = values.get(0);
        double last = values.get(values.size() - 1);
        double changePct = (last - first) / first * 100.0;
        return new TrendStats(values.size(), dates.get(0), first, dates.get(dates.size() - 1), last,
                min, max, sum / values.size(), changePct);
    }

    /**
     * A tiny text sparkline for a series of values, using block characters
     * ▁▂▃▄▅▆▇█ (lowest → highest). One character per value.
     */
    public static String sparkline(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        char[] blocks = {'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        StringBuilder out = new StringBuilder();
        for (double v : values) {
            int idx = (max == min) ? 3 : (int) Math.round((v - min) / (max - min) * 7);
            out.append(blocks[Math.max(0, Math.min(7, idx))]);
        }
        return out.toString();
    }
}
