package rupeerates;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * Text and JSON rendering for the CLI. Money is formatted with grouping
 * (₹46,960.00) and rates to 4 decimals; ₹ prints correctly because Main
 * installs a UTF-8 PrintStream (see the Skycast ₹/— lesson).
 */
public final class Formatter {

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00",
            DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat RATE = new DecimalFormat("#,##0.0000",
            DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat PCT = new DecimalFormat("0.00",
            DecimalFormatSymbols.getInstance(Locale.US));

    private Formatter() {
    }

    public static String money(double value) {
        return MONEY.format(value);
    }

    public static String rate(double value) {
        return RATE.format(value);
    }

    public static String pct(double value) {
        return PCT.format(value);
    }

    /** "₹93.92" for INR amounts, plain number otherwise. */
    public static String amount(double value, String currency) {
        if (currency.equals("INR")) {
            return "₹" + money(value);
        }
        return money(value) + " " + currency;
    }

    /** The default table: USD→INR headline plus the INR-based majors table. */
    public static String latestTable(Rates.Latest latest) {
        StringBuilder out = new StringBuilder();
        out.append("RupeeRates — ECB reference rates (mid-market), ").append(latest.date()).append("\n");
        out.append("1 USD = ₹").append(money(latest.inrPerUnit("USD"))).append("\n\n");
        out.append("1 unit buys (INR):\n");
        for (Rates.PairRow row : Rates.majorPairs(latest)) {
            out.append(String.format("  %-4s ₹%s%n", row.code(), money(row.inrPerUnit())));
        }
        out.append("\nECB reference (mid-market) rates via Frankfurter — NOT your bank's TT rate.\n");
        out.append("Banks buy lower; the gap is the spread.\n");
        return out.toString();
    }

    public static String conversion(Converter.Conversion c) {
        return String.format("%s %s = %s %s%nRate: 1 %s = %s %s  (ECB reference, %s)%n",
                money(c.amount()), c.from(), money(c.converted()), c.to(),
                c.from(), rate(c.rate()), c.to(), c.date());
    }

    public static String feeResult(Converter.FeeResult f) {
        Converter.Conversion c = f.conversion();
        StringBuilder out = new StringBuilder();
        out.append(conversion(c));
        out.append(String.format("Mid-market value:   %s%n", amount(f.midMarketValue(), c.to())));
        out.append(String.format("Fee / spread %.2f%%:  -%s%n", f.feePct(), amount(f.hiddenCost(), c.to())));
        out.append(String.format("You actually receive: %s%n", amount(f.afterFeeValue(), c.to())));
        out.append(String.format("Hidden cost:        %s%n", amount(f.hiddenCost(), c.to())));
        return out.toString();
    }

    public static String trend(Rates.Series series, Converter.TrendStats stats) {
        List<Double> values = List.copyOf(series.points().values());
        StringBuilder out = new StringBuilder();
        out.append(String.format("%s → %s, last %d trading days (%s to %s)%n",
                series.base(), series.target(), stats.days(), stats.firstDate(), stats.lastDate()));
        out.append(Converter.sparkline(values)).append("\n");
        out.append(String.format("  Start:   %s %s%n", stats.firstDate(), rate(stats.first())));
        out.append(String.format("  End:     %s %s%n", stats.lastDate(), rate(stats.last())));
        out.append(String.format("  Min:     %s%n", rate(stats.min())));
        out.append(String.format("  Max:     %s%n", rate(stats.max())));
        out.append(String.format("  Average: %s%n", rate(stats.avg())));
        String dir = stats.changePct() >= 0 ? "weaker" : "stronger";
        out.append(String.format("  Change:  %s%% (%s %s vs %s)%n",
                (stats.changePct() >= 0 ? "+" : "") + pct(stats.changePct()),
                series.target(), dir, series.base()));
        return out.toString();
    }

    // ---- JSON output (hand-built; zero dependencies) ----

    public static String latestJson(Rates.Latest latest) {
        StringBuilder out = new StringBuilder();
        out.append("{\n  \"base\": \"").append(latest.base()).append("\",\n");
        out.append("  \"date\": \"").append(latest.date()).append("\",\n");
        out.append("  \"source\": \"ECB reference rates via api.frankfurter.dev/v1 (mid-market)\",\n");
        out.append("  \"inr_per_unit\": {");
        List<Rates.PairRow> rows = Rates.majorPairs(latest);
        for (int i = 0; i < rows.size(); i++) {
            Rates.PairRow row = rows.get(i);
            out.append(i == 0 ? "\n" : ",\n");
            out.append("    \"").append(row.code()).append("\": ").append(row.inrPerUnit());
        }
        out.append(rows.isEmpty() ? "}" : "\n  }");
        out.append("\n}");
        return out.toString();
    }

    public static String conversionJson(Converter.Conversion c) {
        return "{\n"
                + "  \"amount\": " + c.amount() + ",\n"
                + "  \"from\": \"" + c.from() + "\",\n"
                + "  \"to\": \"" + c.to() + "\",\n"
                + "  \"rate\": " + c.rate() + ",\n"
                + "  \"converted\": " + c.converted() + ",\n"
                + "  \"date\": \"" + c.date() + "\",\n"
                + "  \"source\": \"ECB reference rates via api.frankfurter.dev/v1 (mid-market)\"\n"
                + "}";
    }

    public static String feeJson(Converter.FeeResult f) {
        Converter.Conversion c = f.conversion();
        String base = conversionJson(c);
        // Insert the fee fields before the closing brace.
        return base.substring(0, base.length() - 2)
                + ",\n  \"fee_pct\": " + f.feePct() + ",\n"
                + "  \"mid_market_value\": " + f.midMarketValue() + ",\n"
                + "  \"after_fee_value\": " + f.afterFeeValue() + ",\n"
                + "  \"hidden_cost\": " + f.hiddenCost() + "\n}";
    }

    public static String trendJson(Rates.Series series, Converter.TrendStats stats) {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"from\": \"").append(series.base()).append("\",\n");
        out.append("  \"to\": \"").append(series.target()).append("\",\n");
        out.append("  \"days\": ").append(stats.days()).append(",\n");
        out.append("  \"start\": \"").append(stats.firstDate()).append("\",\n");
        out.append("  \"end\": \"").append(stats.lastDate()).append("\",\n");
        out.append("  \"first\": ").append(stats.first()).append(",\n");
        out.append("  \"last\": ").append(stats.last()).append(",\n");
        out.append("  \"min\": ").append(stats.min()).append(",\n");
        out.append("  \"max\": ").append(stats.max()).append(",\n");
        out.append("  \"average\": ").append(stats.avg()).append(",\n");
        out.append("  \"change_pct\": ").append(stats.changePct()).append(",\n");
        out.append("  \"source\": \"ECB reference rates via api.frankfurter.dev/v1 (mid-market)\"\n");
        out.append("}");
        return out.toString();
    }
}
