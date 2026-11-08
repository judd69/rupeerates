package rupeerates;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.StringJoiner;

/**
 * Client for the Frankfurter API (https://api.frankfurter.app) — free,
 * keyless, ECB reference rates. Frankfurter answers plain HTTP with a 301
 * to HTTPS, so the client MUST follow redirects (HttpClient.Redirect.NORMAL).
 *
 * <p>RupeeRates never invents rates: if the network fails and no
 * {@code --from-file} snapshot was given, the caller gets a
 * {@link DataException} and the CLI exits 1 with a clear message.
 */
public final class FrankfurterClient {

    public static final String BASE_URL = "https://api.frankfurter.app";

    private final HttpClient http;

    public FrankfurterClient() {
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /** Thrown when rates cannot be obtained (network down, bad response, bad snapshot). */
    public static final class DataException extends Exception {
        public DataException(String message) {
            super(message);
        }

        public DataException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Fetches the latest rates for one base and several targets.
     * Frankfurter takes a single {@code from}; multiple {@code to} values
     * are comma-separated.
     */
    public Rates.Latest latest(String from, String... to) throws DataException {
        StringJoiner targets = new StringJoiner(",");
        for (String t : to) {
            targets.add(t);
        }
        String url = BASE_URL + "/latest?from=" + from + "&to=" + targets;
        String body = get(url);
        try {
            return Rates.parseLatest(body);
        } catch (IllegalArgumentException e) {
            throw new DataException("Could not parse the Frankfurter response: " + e.getMessage(), e);
        }
    }

    /** Fetches the daily series for one pair over the last {@code days} days. */
    public Rates.Series series(String from, String to, int days) throws DataException {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days);
        String url = BASE_URL + "/" + start + ".." + end + "?from=" + from + "&to=" + to;
        String body = get(url);
        try {
            return Rates.parseSeries(body, from, to);
        } catch (IllegalArgumentException e) {
            throw new DataException("Could not parse the Frankfurter response: " + e.getMessage(), e);
        }
    }

    /** Loads a saved Frankfurter JSON snapshot (latest or timeseries) from disk. */
    public static String readSnapshot(Path path) throws DataException {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new DataException("Cannot read snapshot file " + path + ": " + e.getMessage(), e);
        }
    }

    /** Parses a snapshot file as a /latest response. */
    public static Rates.Latest latestFromFile(Path path) throws DataException {
        try {
            return Rates.parseLatest(readSnapshot(path));
        } catch (IllegalArgumentException e) {
            throw new DataException("Snapshot " + path + " is not a Frankfurter /latest response: "
                    + e.getMessage(), e);
        }
    }

    /** Parses a snapshot file as a timeseries response for the given pair. */
    public static Rates.Series seriesFromFile(Path path, String from, String to) throws DataException {
        try {
            return Rates.parseSeries(readSnapshot(path), from, to);
        } catch (IllegalArgumentException e) {
            throw new DataException("Snapshot " + path + " is not a Frankfurter timeseries response for "
                    + from + "→" + to + ": " + e.getMessage(), e);
        }
    }

    private String get(String url) throws DataException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataException(
                    "Could not reach api.frankfurter.app (" + e.getMessage() + "). "
                            + "Check your connection, or replay a saved snapshot with --from-file. "
                            + "RupeeRates never shows invented rates.", e);
        }
        if (response.statusCode() != 200) {
            throw new DataException("Frankfurter returned HTTP " + response.statusCode()
                    + " for " + url + ". Try again later, or replay a saved snapshot with --from-file.");
        }
        return response.body();
    }
}
