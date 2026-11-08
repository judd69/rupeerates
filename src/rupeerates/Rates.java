package rupeerates;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Exchange-rate data model, parsed from Frankfurter API responses.
 *
 * <p>Frankfurter returns rates as "units of currency per 1 unit of base"
 * (e.g. base USD, INR 93.92 means 1 USD = 93.92 INR). RupeeRates shows
 * INR-based pairs ("1 USD = ₹93.92"), so every pair is derived by
 * inversion against the INR leg:
 *
 * <pre>
 *   INR per 1 unit of C = rates["INR"] / rates[C]   (rates[base] is 1.0)
 * </pre>
 *
 * <p>That single formula works whatever the base is: for a live fetch with
 * base INR the denominator is the rate itself (rates["INR"] = 1.0), and for
 * a USD-based snapshot the cross goes through the USD leg.
 */
public final class Rates {

    /** Currencies the ECB publishes (and Frankfurter therefore serves). */
    public static final Set<String> SUPPORTED = Set.of(
            "AUD", "BGN", "BRL", "CAD", "CHF", "CNY", "CZK", "DKK", "EUR",
            "GBP", "HKD", "HUF", "IDR", "ILS", "INR", "ISK", "JPY", "KRW",
            "MXN", "MYR", "NOK", "NZD", "PHP", "PLN", "RON", "SEK", "SGD",
            "THB", "TRY", "USD", "ZAR");

    /** Major pairs shown on the default table, in display order. */
    public static final List<String> MAJORS = List.of(
            "USD", "EUR", "GBP", "AUD", "CAD", "SGD", "JPY", "CHF", "CNY", "NZD", "SEK", "NOK");

    private Rates() {
    }

    /** Validates a currency code. Throws IllegalArgumentException with a clean message. */
    public static String validate(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Missing currency code");
        }
        String upper = code.trim().toUpperCase();
        if (upper.equals("AED")) {
            throw new IllegalArgumentException(
                    "AED is not published by the European Central Bank, so Frankfurter has no AED rate. "
                            + "Pick another currency — see https://www.frankfurter.app/docs/ for the full list.");
        }
        if (!upper.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Not a currency code: '" + code + "' (expected 3 letters, e.g. USD)");
        }
        if (!SUPPORTED.contains(upper)) {
            throw new IllegalArgumentException(
                    "Unknown or unsupported currency: '" + upper + "' — the ECB does not publish it via Frankfurter.");
        }
        return upper;
    }

    /** One day of rates: base currency, ECB reference date, currency → units per 1 base. */
    public record Latest(String base, LocalDate date, Map<String, Double> rates) {
        /** Rate of {@code currency} per 1 {@code base} (1.0 when currency == base). */
        public double rateOf(String currency) {
            if (currency.equals(base)) {
                return 1.0;
            }
            Double r = rates.get(currency);
            if (r == null) {
                throw new IllegalArgumentException(
                        "No rate for " + currency + " in the " + base + " snapshot dated " + date);
            }
            return r;
        }

        /** How many INR one unit of {@code currency} buys (inversion through the INR leg). */
        public double inrPerUnit(String currency) {
            if (currency.equals("INR")) {
                return 1.0;
            }
            return rateOf("INR") / rateOf(currency);
        }

        /** Cross rate: how many {@code to} one unit of {@code from} buys. */
        public double cross(String from, String to) {
            if (from.equals(to)) {
                return 1.0;
            }
            return rateOf(to) / rateOf(from);
        }
    }

    /** A date → rate series for one pair, in chronological order. */
    public record Series(String base, String target, LocalDate start, LocalDate end,
                         TreeMap<LocalDate, Double> points) {
        public int size() {
            return points.size();
        }
    }

    /** Parses a Frankfurter /latest response. */
    public static Latest parseLatest(String json) {
        Map<String, Object> root = Json.obj(Json.parse(json));
        String base = Json.str(Json.field(root, "base"));
        LocalDate date = LocalDate.parse(Json.str(Json.field(root, "date")));
        Map<String, Object> raw = Json.obj(Json.field(root, "rates"));
        Map<String, Double> rates = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            rates.put(e.getKey(), Json.num(e.getValue()));
        }
        if (!rates.containsKey("INR") && !base.equals("INR")) {
            throw new IllegalArgumentException("Snapshot has no INR leg — cannot build INR-based pairs");
        }
        return new Latest(base, date, rates);
    }

    /**
     * Parses a Frankfurter timeseries response ({@code /{start}..{end}?from=X&to=Y}).
     * The {@code rates} object maps dates to single-currency objects.
     */
    public static Series parseSeries(String json, String from, String to) {
        Map<String, Object> root = Json.obj(Json.parse(json));
        String base = Json.str(Json.field(root, "base"));
        LocalDate start = LocalDate.parse(Json.str(Json.field(root, "start_date")));
        LocalDate end = LocalDate.parse(Json.str(Json.field(root, "end_date")));
        Map<String, Object> raw = Json.obj(Json.field(root, "rates"));
        TreeMap<LocalDate, Double> points = new TreeMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            Map<String, Object> day = Json.obj(e.getValue());
            Object v = day.get(to);
            if (v == null) {
                throw new IllegalArgumentException("Timeseries day " + e.getKey() + " has no " + to + " rate");
            }
            points.put(LocalDate.parse(e.getKey()), Json.num(v));
        }
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Timeseries has no data points");
        }
        return new Series(base, to, start, end, points);
    }

    /** One row of the INR-based majors table. */
    public record PairRow(String code, double inrPerUnit) {
    }

    /** INR-based rows for every major currency present in the snapshot, in MAJORS order. */
    public static List<PairRow> majorPairs(Latest latest) {
        List<PairRow> rows = new ArrayList<>();
        for (String code : MAJORS) {
            if (code.equals(latest.base()) || latest.rates().containsKey(code)) {
                rows.add(new PairRow(code, latest.inrPerUnit(code)));
            }
        }
        return rows;
    }
}
