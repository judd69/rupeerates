package rupeerates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Parsing + inversion, verified against the two real Frankfurter snapshots. */
class RatesTest {

    private static final String LATEST_JSON = """
            {"amount":1.0,"base":"USD","date":"2026-11-06","rates":{"AUD":1.4554,"CAD":1.4066,"EUR":0.89566,"GBP":0.74711,"INR":93.92,"SGD":1.2816}}""";

    private static final String SERIES_JSON = """
            {"amount":1.0,"base":"USD","start_date":"2026-10-09","end_date":"2026-11-06","rates":{"2026-10-09":{"INR":96.73},"2026-10-12":{"INR":95.78},"2026-10-13":{"INR":95.28},"2026-10-14":{"INR":95.16},"2026-10-15":{"INR":95.06},"2026-10-16":{"INR":94.96},"2026-10-19":{"INR":94.93},"2026-10-20":{"INR":94.93},"2026-10-21":{"INR":94.47},"2026-10-22":{"INR":94.82},"2026-10-23":{"INR":94.94},"2026-10-26":{"INR":95.18},"2026-10-27":{"INR":95.12},"2026-10-28":{"INR":95.22},"2026-10-29":{"INR":95.27},"2026-10-30":{"INR":94.95},"2026-11-02":{"INR":94.91},"2026-11-03":{"INR":94.75},"2026-11-04":{"INR":94.64},"2026-11-05":{"INR":94.48},"2026-11-06":{"INR":93.92}}}""";

    @Test
    void parsesLatestSnapshot() {
        Rates.Latest latest = Rates.parseLatest(LATEST_JSON);
        assertEquals("USD", latest.base());
        assertEquals("2026-11-06", latest.date().toString());
        assertEquals(93.92, latest.rateOf("INR"), 0.0001);
        assertEquals(0.89566, latest.rateOf("EUR"), 0.00001);
    }

    @Test
    void parsesSavedLatestFile() throws Exception {
        Rates.Latest latest = FrankfurterClient.latestFromFile(Path.of("examples/latest-usd.json"));
        assertEquals("USD", latest.base());
        assertEquals(93.92, latest.rateOf("INR"), 0.0001);
    }

    @Test
    void invertsToInrPerUnit() {
        Rates.Latest latest = Rates.parseLatest(LATEST_JSON);
        assertEquals(93.92, latest.inrPerUnit("USD"), 0.0001);
        // 1 EUR = 93.92 / 0.89566 ≈ ₹104.86
        assertEquals(104.86, latest.inrPerUnit("EUR"), 0.01);
        // 1 GBP = 93.92 / 0.74711 ≈ ₹125.71
        assertEquals(125.71, latest.inrPerUnit("GBP"), 0.01);
        // 1 AUD = 93.92 / 1.4554 ≈ ₹64.53
        assertEquals(64.53, latest.inrPerUnit("AUD"), 0.01);
        assertEquals(1.0, latest.inrPerUnit("INR"), 0.0001);
    }

    @Test
    void crossRateThroughBase() {
        Rates.Latest latest = Rates.parseLatest(LATEST_JSON);
        // 1 EUR in USD = 0.89566^-1... EUR→INR cross: 93.92/0.89566; USD→EUR: 0.89566
        assertEquals(0.89566, latest.cross("USD", "EUR"), 0.00001);
        assertEquals(93.92 / 0.89566, latest.cross("EUR", "INR"), 0.001);
        assertEquals(1.0, latest.cross("USD", "USD"), 0.0);
    }

    @Test
    void majorsTableOnlyIncludesCurrenciesInSnapshot() {
        Rates.Latest latest = Rates.parseLatest(LATEST_JSON);
        List<String> codes = Rates.majorPairs(latest).stream().map(Rates.PairRow::code).toList();
        // Snapshot has USD/EUR/GBP/AUD/CAD/SGD; JPY/CHF/CNY/NZD/SEK/NOK are absent.
        assertTrue(codes.containsAll(List.of("USD", "EUR", "GBP", "AUD", "CAD", "SGD")));
        assertTrue(codes.stream().noneMatch(c -> c.equals("JPY") || c.equals("CHF")));
    }

    @Test
    void parsesSeriesSnapshot() {
        Rates.Series series = Rates.parseSeries(SERIES_JSON, "USD", "INR");
        assertEquals(21, series.size());
        assertEquals("2026-10-09", series.start().toString());
        assertEquals("2026-11-06", series.end().toString());
        assertEquals(96.73, series.points().firstEntry().getValue(), 0.001);
        assertEquals(93.92, series.points().lastEntry().getValue(), 0.001);
    }

    @Test
    void parsesSavedSeriesFile() throws Exception {
        Rates.Series series = FrankfurterClient.seriesFromFile(
                Path.of("examples/usd-inr-30d.json"), "USD", "INR");
        assertEquals(21, series.size());
    }

    @Test
    void rejectsUnknownCurrency() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Rates.validate("XXX"));
        assertTrue(e.getMessage().contains("XXX"));
    }

    @Test
    void rejectsAedWithHonestReason() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Rates.validate("AED"));
        assertTrue(e.getMessage().contains("not published by the European Central Bank"));
    }

    @Test
    void rejectsMalformedCode() {
        assertThrows(IllegalArgumentException.class, () -> Rates.validate("US"));
        assertThrows(IllegalArgumentException.class, () -> Rates.validate("USDD"));
        assertThrows(IllegalArgumentException.class, () -> Rates.validate("12X"));
    }

    @Test
    void acceptsLowercaseCode() {
        assertEquals("USD", Rates.validate("usd"));
    }

    @Test
    void rejectsLatestWithoutInrLeg() {
        assertThrows(IllegalArgumentException.class, () -> Rates.parseLatest(
                "{\"base\":\"USD\",\"date\":\"2026-11-06\",\"rates\":{\"EUR\":0.89566}}"));
    }
}
