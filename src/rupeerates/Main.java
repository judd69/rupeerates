package rupeerates;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * RupeeRates CLI.
 *
 * <pre>
 *   java -jar build/rupeerates.jar                     today's rates table (live)
 *   java -jar build/rupeerates.jar convert 500 USD INR
 *   java -jar build/rupeerates.jar convert 500 USD INR --fee 1.5
 *   java -jar build/rupeerates.jar trend USD INR [--days 30]
 *   java -jar build/rupeerates.jar --json convert 100 EUR INR
 *   java -jar build/rupeerates.jar --from-file examples/latest-usd.json
 *   java -jar build/rupeerates.jar --serve [port]
 *   java -jar build/rupeerates.jar --help | --version
 * </pre>
 *
 * Exit codes: 0 ok, 1 data / API error, 2 usage error. RupeeRates never
 * invents rates: if the Frankfurter API cannot be reached and no
 * {@code --from-file} snapshot was given, it prints a clear error and exits 1.
 */
public final class Main {

    public static final String VERSION = "1.0.0";

    private static final String HELP = """
            RupeeRates — live INR exchange rates (ECB reference, mid-market) via Frankfurter.
            Usage: java -jar build/rupeerates.jar [options] [command]
              (no command)              today's rates table: USD→INR headline + majors in ₹
              convert AMOUNT FROM TO    convert at the ECB reference rate
                --fee PCT               also show mid-market value, after-fee value, hidden cost
              trend FROM TO [--days N]  30-day series summary: min/max/average/change % (default 30)
              --json                    machine-readable JSON output (all commands)
              --from-file FILE          replay a saved Frankfurter snapshot instead of the network
              --serve [port]            serve a live dashboard on localhost (default 8080)
              --help                    this help
              --version                 print the version
            Rates are ECB reference (mid-market) rates — NOT your bank's TT rate.
            AED is not published by the ECB and is not available via Frankfurter.
            Exit codes: 0 ok, 1 data/API error, 2 usage error.""";

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        // ₹ and — must survive a C-locale terminal.
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        System.exit(run(args, System.out, System.err));
    }

    /** Runs the CLI; returns the process exit code (testable without System.exit). */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        boolean json = false;
        boolean serve = false;
        int servePort = 8080;
        Path fromFile = null;
        List<String> positional = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help", "-h" -> {
                    out.println(HELP);
                    return 0;
                }
                case "--version", "-v" -> {
                    out.println("rupeerates " + VERSION);
                    return 0;
                }
                case "--json" -> json = true;
                // Subcommand flags (--fee, --days) belong to convert/trend;
                // pass them through as positional tokens for the subcommand parser.
                case "--fee", "--days" -> positional.add(args[i]);
                case "--serve" -> {
                    serve = true;
                    if (i + 1 < args.length && args[i + 1].matches("\\d+")) {
                        servePort = Integer.parseInt(args[++i]);
                    }
                }
                case "--from-file" -> {
                    if (i + 1 >= args.length) {
                        err.println("error: --from-file needs a file path");
                        return 2;
                    }
                    fromFile = Path.of(args[++i]);
                }
                default -> {
                    if (args[i].startsWith("--")) {
                        err.println("error: unknown option '" + args[i] + "' — see --help");
                        return 2;
                    }
                    positional.add(args[i]);
                }
            }
        }

        try {
            if (serve) {
                return serve(fromFile, servePort, out, err);
            }
            if (positional.isEmpty()) {
                return latest(fromFile, json, out, err);
            }
            switch (positional.get(0)) {
                case "convert" -> {
                    return convert(positional, fromFile, json, out, err);
                }
                case "trend" -> {
                    return trend(positional, fromFile, json, out, err);
                }
                default -> {
                    err.println("error: unknown command '" + positional.get(0) + "' — see --help");
                    return 2;
                }
            }
        } catch (FrankfurterClient.DataException e) {
            err.println("error: " + e.getMessage());
            return 1;
        } catch (IllegalArgumentException e) {
            err.println("error: " + e.getMessage());
            return 2;
        }
    }


    /** Prints output with exactly one trailing newline (text formatters already end with one). */
    private static void emit(PrintStream out, String text) {
        out.print(text.endsWith("\n") ? text : text + "\n");
    }

    private static int latest(Path fromFile, boolean json, PrintStream out, PrintStream err)
            throws FrankfurterClient.DataException {
        Rates.Latest latest;
        if (fromFile != null) {
            latest = FrankfurterClient.latestFromFile(fromFile);
        } else {
            latest = new FrankfurterClient().latest("INR",
                    Rates.MAJORS.toArray(new String[0]));
        }
        emit(out, json ? Formatter.latestJson(latest) : Formatter.latestTable(latest));
        return 0;
    }

    private static int convert(List<String> positional, Path fromFile, boolean json,
                               PrintStream out, PrintStream err) throws FrankfurterClient.DataException {
        if (positional.size() < 4) {
            err.println("error: usage: convert AMOUNT FROM TO [--fee PCT]");
            return 2;
        }
        double amount;
        try {
            amount = Double.parseDouble(positional.get(1));
        } catch (NumberFormatException e) {
            err.println("error: not an amount: '" + positional.get(1) + "'");
            return 2;
        }
        String from = Rates.validate(positional.get(2));
        String to = Rates.validate(positional.get(3));
        double feePct = -1;
        for (int i = 4; i < positional.size(); i++) {
            if (positional.get(i).equals("--fee") && i + 1 < positional.size()) {
                try {
                    feePct = Double.parseDouble(positional.get(++i));
                } catch (NumberFormatException e) {
                    err.println("error: not a fee percentage: '" + positional.get(i) + "'");
                    return 2;
                }
            } else {
                err.println("error: unexpected argument '" + positional.get(i) + "' — see --help");
                return 2;
            }
        }

        Rates.Latest latest;
        if (fromFile != null) {
            latest = FrankfurterClient.latestFromFile(fromFile);
        } else {
            latest = new FrankfurterClient().latest(from, to);
        }
        double rate;
        try {
            rate = latest.cross(from, to);
        } catch (IllegalArgumentException e) {
            throw new FrankfurterClient.DataException(
                    "The snapshot does not contain a " + from + "→" + to + " rate. "
                            + "Use a snapshot whose base covers both currencies, or run live.");
        }
        Converter.Conversion conversion = Converter.convert(amount, from, to, rate, latest.date().toString());
        if (feePct >= 0) {
            Converter.FeeResult fee = Converter.withFee(conversion, feePct);
            emit(out, json ? Formatter.feeJson(fee) : Formatter.feeResult(fee));
        } else {
            emit(out, json ? Formatter.conversionJson(conversion) : Formatter.conversion(conversion));
        }
        return 0;
    }

    private static int trend(List<String> positional, Path fromFile, boolean json,
                             PrintStream out, PrintStream err) throws FrankfurterClient.DataException {
        if (positional.size() < 3) {
            err.println("error: usage: trend FROM TO [--days N]");
            return 2;
        }
        String from = Rates.validate(positional.get(1));
        String to = Rates.validate(positional.get(2));
        int days = 30;
        for (int i = 3; i < positional.size(); i++) {
            if (positional.get(i).equals("--days") && i + 1 < positional.size()) {
                try {
                    days = Integer.parseInt(positional.get(++i));
                } catch (NumberFormatException e) {
                    err.println("error: not a day count: '" + positional.get(i) + "'");
                    return 2;
                }
                if (days < 1 || days > 365) {
                    err.println("error: --days must be between 1 and 365");
                    return 2;
                }
            } else {
                err.println("error: unexpected argument '" + positional.get(i) + "' — see --help");
                return 2;
            }
        }

        Rates.Series series;
        if (fromFile != null) {
            series = FrankfurterClient.seriesFromFile(fromFile, from, to);
            if (!series.base().equals(from)) {
                throw new FrankfurterClient.DataException(
                        "The snapshot is a " + series.base() + "→" + series.target()
                                + " series, not " + from + "→" + to + ".");
            }
        } else {
            series = new FrankfurterClient().series(from, to, days);
        }
        List<Double> values = List.copyOf(series.points().values());
        List<String> dates = series.points().keySet().stream().map(Object::toString).toList();
        Converter.TrendStats stats = Converter.trend(values, dates);
        emit(out, json ? Formatter.trendJson(series, stats) : Formatter.trend(series, stats));
        return 0;
    }

    private static int serve(Path fromFile, int port, PrintStream out, PrintStream err)
            throws FrankfurterClient.DataException {
        Rates.Latest latest;
        if (fromFile != null) {
            latest = FrankfurterClient.latestFromFile(fromFile);
        } else {
            latest = new FrankfurterClient().latest("INR", Rates.MAJORS.toArray(new String[0]));
        }
        try {
            HttpServer server = startDashboard(latest, port);
            out.println("RupeeRates dashboard at http://localhost:" + server.getAddress().getPort() + "/");
            out.println("ECB reference rates, " + latest.date() + " — press Ctrl+C to stop.");
            // Block until the process is killed; the server runs on its own threads.
            Object lock = new Object();
            synchronized (lock) {
                lock.wait();
            }
            return 0;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            err.println("error: could not start the dashboard server: " + e.getMessage());
            return 1;
        }
    }

    /** Starts the dashboard server (non-blocking; used by --serve and tests). */
    public static HttpServer startDashboard(Rates.Latest latest, int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        String page = dashboardHtml(latest);
        byte[] body = page.getBytes(StandardCharsets.UTF_8);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        return server;
    }

    /** Small live dashboard page generated from the fetched rates. */
    public static String dashboardHtml(Rates.Latest latest) {
        StringBuilder rows = new StringBuilder();
        for (Rates.PairRow row : Rates.majorPairs(latest)) {
            rows.append("<tr><td>1 ").append(row.code()).append("</td><td>₹")
                    .append(Formatter.money(row.inrPerUnit())).append("</td></tr>\n");
        }
        return "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
                + "<title>RupeeRates — live INR exchange rates</title>\n"
                + "<style>body{font-family:system-ui,sans-serif;max-width:640px;margin:40px auto;"
                + "padding:0 16px;color:#111;background:#FAFAF7}h1{font-size:2rem}"
                + "table{border-collapse:collapse;width:100%;background:#fff}"
                + "td,th{padding:10px 12px;border-bottom:1px solid #eee;text-align:left}"
                + ".note{color:#5B5B58;font-size:.9rem}</style>\n</head>\n<body>\n"
                + "<h1>RupeeRates</h1>\n<p>1 USD = ₹" + Formatter.money(latest.inrPerUnit("USD"))
                + " &middot; ECB reference rates, " + latest.date() + "</p>\n"
                + "<table><tr><th>Pair</th><th>INR per unit</th></tr>\n" + rows + "</table>\n"
                + "<p class=\"note\">ECB reference (mid-market) rates via Frankfurter — NOT your bank's "
                + "TT rate. Banks buy lower; the gap is the spread. Full dashboard: "
                + "<a href=\"https://judd69.github.io/rupeerates/\">judd69.github.io/rupeerates</a></p>\n"
                + "</body>\n</html>\n";
    }
}
