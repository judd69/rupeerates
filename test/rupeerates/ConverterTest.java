package rupeerates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Conversion math, fee math, and trend stats — verified against the real 30-day series. */
class ConverterTest {

    // The real 30-day USD→INR series (21 trading days, 2026-10-09 → 2026-11-06).
    private static final List<Double> SERIES = List.of(
            96.73, 95.78, 95.28, 95.16, 95.06, 94.96, 94.93, 94.93, 94.47, 94.82,
            94.94, 95.18, 95.12, 95.22, 95.27, 94.95, 94.91, 94.75, 94.64, 94.48, 93.92);
    private static final List<String> DATES = List.of(
            "2026-10-09", "2026-10-12", "2026-10-13", "2026-10-14", "2026-10-15",
            "2026-10-16", "2026-10-19", "2026-10-20", "2026-10-21", "2026-10-22",
            "2026-10-23", "2026-10-26", "2026-10-27", "2026-10-28", "2026-10-29",
            "2026-10-30", "2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05", "2026-11-06");

    @Test
    void convertsAtMidMarketRate() {
        Converter.Conversion c = Converter.convert(500, "USD", "INR", 93.92, "2026-11-06");
        assertEquals(46960.0, c.converted(), 0.001);
        assertEquals(93.92, c.rate(), 0.0001);
    }

    @Test
    void convertsZeroAndSameCurrency() {
        assertEquals(0.0, Converter.convert(0, "USD", "INR", 93.92, "2026-11-06").converted(), 0.0);
        assertEquals(100.0, Converter.convert(100, "USD", "USD", 1.0, "2026-11-06").converted(), 0.0);
    }

    @Test
    void rejectsNegativeAmountAndBadRate() {
        assertThrows(IllegalArgumentException.class,
                () -> Converter.convert(-5, "USD", "INR", 93.92, "2026-11-06"));
        assertThrows(IllegalArgumentException.class,
                () -> Converter.convert(5, "USD", "INR", 0, "2026-11-06"));
    }

    @Test
    void feeMathMatchesTheWorkedExample() {
        // 500 USD → INR at 93.92 = ₹46,960; 1.5% fee → ₹46,255.60 received, ₹704.40 hidden.
        Converter.Conversion c = Converter.convert(500, "USD", "INR", 93.92, "2026-11-06");
        Converter.FeeResult f = Converter.withFee(c, 1.5);
        assertEquals(46960.0, f.midMarketValue(), 0.001);
        assertEquals(46255.60, f.afterFeeValue(), 0.01);
        assertEquals(704.40, f.hiddenCost(), 0.01);
    }

    @Test
    void zeroFeeMeansNoHiddenCost() {
        Converter.Conversion c = Converter.convert(100, "EUR", "INR", 104.86, "2026-11-06");
        Converter.FeeResult f = Converter.withFee(c, 0);
        assertEquals(0.0, f.hiddenCost(), 0.0);
        assertEquals(f.midMarketValue(), f.afterFeeValue(), 0.0);
    }

    @Test
    void rejectsFeeOutside0To100() {
        Converter.Conversion c = Converter.convert(100, "USD", "INR", 93.92, "2026-11-06");
        assertThrows(IllegalArgumentException.class, () -> Converter.withFee(c, -1));
        assertThrows(IllegalArgumentException.class, () -> Converter.withFee(c, 101));
    }

    @Test
    void trendStatsOnRealSeries() {
        Converter.TrendStats s = Converter.trend(SERIES, DATES);
        assertEquals(21, s.days());
        assertEquals(96.73, s.first(), 0.001);
        assertEquals(93.92, s.last(), 0.001);
        assertEquals(93.92, s.min(), 0.001);
        assertEquals(96.73, s.max(), 0.001);
        // Sum is 1995.50 over 21 trading days.
        assertEquals(95.02, s.avg(), 0.01);
        // Rupee strengthened: (93.92 - 96.73) / 96.73 ≈ -2.90%.
        assertEquals(-2.90, s.changePct(), 0.01);
        assertEquals("2026-10-09", s.firstDate());
        assertEquals("2026-11-06", s.lastDate());
    }

    @Test
    void trendRejectsEmptySeries() {
        assertThrows(IllegalArgumentException.class, () -> Converter.trend(List.of(), List.of()));
    }

    @Test
    void sparklineHasOneBlockPerValue() {
        String spark = Converter.sparkline(SERIES);
        assertEquals(21, spark.length());
        assertTrue(spark.startsWith("█"), "first (highest) value should be the top block");
        assertTrue(spark.endsWith("▁"), "last (lowest) value should be the bottom block");
    }

    @Test
    void sparklineFlatSeriesIsMidBlock() {
        assertEquals("▄▄▄", Converter.sparkline(List.of(5.0, 5.0, 5.0)));
    }
}
