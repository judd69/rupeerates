package rupeerates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** CLI behaviour and exit codes, run against the real saved snapshots. */
class CliTest {

    private record Run(int code, String out, String err) {
    }

    private static Run run(String... args) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);
        int code = Main.run(args, out, err);
        return new Run(code, outBytes.toString(StandardCharsets.UTF_8),
                errBytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void helpExitsZero() {
        Run r = run("--help");
        assertEquals(0, r.code());
        assertTrue(r.out().contains("convert AMOUNT FROM TO"));
    }

    @Test
    void versionExitsZero() {
        Run r = run("--version");
        assertEquals(0, r.code());
        assertTrue(r.out().contains("rupeerates 1.0.0"));
    }

    @Test
    void unknownCommandExitsTwo() {
        Run r = run("frobnicate");
        assertEquals(2, r.code());
        assertTrue(r.err().contains("unknown command"));
    }

    @Test
    void unknownOptionExitsTwo() {
        Run r = run("--bogus");
        assertEquals(2, r.code());
    }

    @Test
    void unknownCurrencyExitsTwo() {
        Run r = run("--from-file", "examples/latest-usd.json", "convert", "10", "XXX", "INR");
        assertEquals(2, r.code());
        assertTrue(r.err().contains("XXX"));
    }

    @Test
    void aedExitsTwoWithHonestReason() {
        Run r = run("--from-file", "examples/latest-usd.json", "convert", "10", "AED", "INR");
        assertEquals(2, r.code());
        assertTrue(r.err().contains("not published by the European Central Bank"));
    }

    @Test
    void missingSnapshotFileExitsOne() {
        Run r = run("--from-file", "examples/no-such-file.json");
        assertEquals(1, r.code());
        assertTrue(r.err().contains("Cannot read snapshot file"));
    }

    @Test
    void latestTableFromSnapshot() {
        Run r = run("--from-file", "examples/latest-usd.json");
        assertEquals(0, r.code());
        assertTrue(r.out().contains("1 USD = ₹93.92"));
        assertTrue(r.out().contains("2026-11-06"));
        assertTrue(r.out().contains("EUR"));
        assertTrue(r.out().contains("NOT your bank's TT rate"));
    }

    @Test
    void latestTableJsonFromSnapshot() {
        Run r = run("--json", "--from-file", "examples/latest-usd.json");
        assertEquals(0, r.code());
        assertTrue(r.out().contains("\"USD\": 93.92"));
        assertTrue(r.out().contains("\"date\": \"2026-11-06\""));
    }

    @Test
    void convertFromSnapshot() {
        Run r = run("--from-file", "examples/latest-usd.json", "convert", "500", "USD", "INR");
        assertEquals(0, r.code());
        assertTrue(r.out().contains("500.00 USD = 46,960.00 INR"));
        assertTrue(r.out().contains("93.9200"));
    }

    @Test
    void convertWithFeeFromSnapshot() {
        Run r = run("--from-file", "examples/latest-usd.json",
                "convert", "500", "USD", "INR", "--fee", "1.5");
        assertEquals(0, r.code());
        assertTrue(r.out().contains("₹46,960.00"));
        assertTrue(r.out().contains("₹46,255.60"));
        assertTrue(r.out().contains("₹704.40"));
    }

    @Test
    void convertJsonFromSnapshot() {
        Run r = run("--json", "--from-file", "examples/latest-usd.json",
                "convert", "500", "USD", "INR", "--fee", "1.5");
        assertEquals(0, r.code());
        assertTrue(r.out().contains("\"converted\": 46960.0"));
        assertTrue(r.out().contains("\"hidden_cost\": 704.4"));
    }

    @Test
    void convertMissingCurrencyInSnapshotExitsOne() {
        // JPY is not in the saved snapshot, so the pair cannot be priced offline.
        Run r = run("--from-file", "examples/latest-usd.json", "convert", "10", "JPY", "INR");
        assertEquals(1, r.code());
        assertTrue(r.err().contains("does not contain"));
    }

    @Test
    void trendFromSeriesSnapshot() {
        Run r = run("--from-file", "examples/usd-inr-30d.json", "trend", "USD", "INR");
        assertEquals(0, r.code());
        assertTrue(r.out().contains("21 trading days"));
        assertTrue(r.out().contains("96.7300"));
        assertTrue(r.out().contains("93.9200"));
        assertTrue(r.out().contains("95.0238"));
        assertTrue(r.out().contains("-2.90%"));
    }

    @Test
    void trendWithLatestSnapshotExitsOne() {
        // A /latest snapshot is not a timeseries.
        Run r = run("--from-file", "examples/latest-usd.json", "trend", "USD", "INR");
        assertEquals(1, r.code());
        assertTrue(r.err().contains("not a Frankfurter timeseries"));
    }

    @Test
    void badAmountExitsTwo() {
        Run r = run("--from-file", "examples/latest-usd.json", "convert", "abc", "USD", "INR");
        assertEquals(2, r.code());
        assertTrue(r.err().contains("not an amount"));
    }

    @Test
    void dashboardHtmlContainsLiveData() throws Exception {
        Rates.Latest latest = FrankfurterClient.latestFromFile(java.nio.file.Path.of("examples/latest-usd.json"));
        String html = Main.dashboardHtml(latest);
        assertTrue(html.contains("1 USD = ₹93.92"));
        assertTrue(html.contains("2026-11-06"));
        assertTrue(html.contains("NOT your bank's"));
        assertTrue(html.contains("judd69.github.io/rupeerates"));
    }

    @Test
    void serveDashboardStartsOnEphemeralPort() throws Exception {
        // The sandbox blocks loopback TCP for the test client, so this only
        // asserts the server starts and binds; the HTML body is covered above.
        Rates.Latest latest = FrankfurterClient.latestFromFile(java.nio.file.Path.of("examples/latest-usd.json"));
        HttpServer server = Main.startDashboard(latest, 0);
        try {
            assertTrue(server.getAddress().getPort() > 0);
        } finally {
            server.stop(0);
        }
    }
}
