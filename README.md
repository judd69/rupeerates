# RupeeRates

**What your money is actually worth in rupees.** A live INR exchange-rate dashboard and converter on real ECB reference rates — plus the remittance math nobody shows you: the rate Google quotes is never the rate your bank pays.

The rupee moves every day, and the number on a search page is the mid-market rate. Banks buy lower — on 7 Nov 2026 the mid-market was ₹94.14 per dollar while Indian banks' TT buying rates were ₹93.50 (Central Bank of India), ₹93.55 (City Union Bank) and ₹92.28 (DBS). That gap of ₹0.80–₹3.77 per dollar is the spread, and on $500 it quietly costs ₹400–₹1,885. RupeeRates shows the honest mid-market rate, converts at it, and — with `--fee` — shows what actually lands in your account and what the fee quietly takes.

## What it does

- **Today's rates table** — USD→INR headline plus the major pairs in rupees (1 EUR = ₹104.86 …).
- **`convert <amount> <FROM> <TO>`** — converts at the ECB reference rate, with the rate and date shown.
- **`convert … --fee <pct>`** — mid-market value vs after-fee value vs the hidden cost, in the target currency.
- **`trend <FROM> <TO> [--days 30]`** — 30-day series summary: start, end, min, max, average, change %, plus a text sparkline.
- **`--json`** on every command for machine-readable output.
- **`--serve [port]`** — a built-in dashboard on localhost.
- **`--from-file <snapshot>`** — offline mode: replay a saved Frankfurter JSON snapshot instead of the network.
- **Interactive dashboard** in `docs/` — converter hero, live pair table with day-change, a 30-day SVG chart that draws itself, a "what you actually receive" fee slider, saved pairs, shareable `?from=&to=` URLs, 30-minute auto-refresh. Served live at https://judd69.github.io/rupeerates/ once the repo exists and Pages is flipped.

Data: ECB reference rates via the [Frankfurter API](https://www.frankfurter.app/) — free, keyless. RupeeRates **never invents rates**: if the API cannot be reached and no `--from-file` snapshot was given, it prints a clear error and exits 1.

**AED is not listed.** The ECB does not publish AED, so Frankfurter has no AED rate — the CLI and the page say so honestly instead of faking one.

## How it works

Frankfurter returns rates as "units of currency per 1 unit of base" (e.g. base USD, INR 93.92 means 1 USD = ₹93.92). RupeeRates shows INR-based pairs, so every pair is derived by inversion through the INR leg:

```
INR per 1 unit of C = rates["INR"] / rates[C]        (rates[base] is 1.0)
```

That single formula works whatever the base is: for a live fetch with base INR the denominator is the rate itself, and for a USD-based snapshot the cross goes through the USD leg. Frankfurter takes a single `from`, so the CLI fetches one base and inverts (it does not issue one call per currency). Frankfurter answers plain HTTP with a 301 to HTTPS, so the Java `HttpClient` is built with `Redirect.NORMAL` — without it, requests fail.

## Requirements

- Java 21+ (built and tested with Temurin 21). No shipped dependencies — plain `javac`, standard library only.
- JUnit 5 (test-only, in `lib/`, gitignored) for the test suite.

## Build & test

```sh
./build.sh        # compile + run all 46 JUnit 5 tests + package build/rupeerates.jar
```

## Run

```sh
java -jar build/rupeerates.jar                              # today's rates table (live)
java -jar build/rupeerates.jar convert 500 USD INR
java -jar build/rupeerates.jar convert 500 USD INR --fee 1.5
java -jar build/rupeerates.jar trend USD INR                 # 30-day summary
java -jar build/rupeerates.jar trend USD INR --days 7
java -jar build/rupeerates.jar --json convert 100 EUR INR
java -jar build/rupeerates.jar --serve 8080                 # dashboard on localhost:8080
java -jar build/rupeerates.jar --from-file examples/latest-usd.json
java -jar build/rupeerates.jar --help
java -jar build/rupeerates.jar --version
```

Exit codes: `0` ok, `1` data/API error, `2` usage error.

## Example output

All outputs below are real runs against the saved snapshots in `examples/` (Frankfurter responses fetched 2026-11-08; latest dated 2026-11-06 — Friday, since 7 Nov is a Saturday and the ECB publishes no weekend rates).

```sh
$ java -jar build/rupeerates.jar --from-file examples/latest-usd.json
RupeeRates — ECB reference rates (mid-market), 2026-11-06
1 USD = ₹93.92

1 unit buys (INR):
  USD  ₹93.92
  EUR  ₹104.86
  GBP  ₹125.71
  AUD  ₹64.53
  CAD  ₹66.77
  SGD  ₹73.28

ECB reference (mid-market) rates via Frankfurter — NOT your bank's TT rate.
Banks buy lower; the gap is the spread.
```

```sh
$ java -jar build/rupeerates.jar --from-file examples/latest-usd.json convert 500 USD INR
500.00 USD = 46,960.00 INR
Rate: 1 USD = 93.9200 INR  (ECB reference, 2026-11-06)
```

```sh
$ java -jar build/rupeerates.jar --from-file examples/latest-usd.json convert 500 USD INR --fee 1.5
500.00 USD = 46,960.00 INR
Rate: 1 USD = 93.9200 INR  (ECB reference, 2026-11-06)
Mid-market value:   ₹46,960.00
Fee / spread 1.50%:  -₹704.40
You actually receive: ₹46,255.60
Hidden cost:        ₹704.40
```

```sh
$ java -jar build/rupeerates.jar --from-file examples/usd-inr-30d.json trend USD INR
USD → INR, last 21 trading days (2026-10-09 to 2026-11-06)
█▆▄▄▄▄▄▄▂▃▄▄▄▄▄▄▃▃▃▂▁
  Start:   2026-10-09 96.7300
  End:     2026-11-06 93.9200
  Min:     93.9200
  Max:     96.7300
  Average: 95.0238
  Change:  -2.90% (INR stronger vs USD)
```

```sh
$ java -jar build/rupeerates.jar --json --from-file examples/latest-usd.json
{
  "base": "USD",
  "date": "2026-11-06",
  "source": "ECB reference rates via api.frankfurter.app (mid-market)",
  "inr_per_unit": {
    "USD": 93.92,
    "EUR": 104.86121965924569,
    "GBP": 125.71107333592107,
    "AUD": 64.53208739865329,
    "CAD": 66.77093701123276,
    "SGD": 73.28339575530586
  }
}
```

```sh
$ java -jar build/rupeerates.jar --json --from-file examples/latest-usd.json convert 500 USD INR --fee 1.5
{
  "amount": 500.0,
  "from": "USD",
  "to": "INR",
  "rate": 93.92,
  "converted": 46960.0,
  "date": "2026-11-06",
  "source": "ECB reference rates via api.frankfurter.app (mid-market)",
  "fee_pct": 1.5,
  "mid_market_value": 46960.0,
  "after_fee_value": 46255.6,
  "hidden_cost": 704.4
}
```

```sh
$ java -jar build/rupeerates.jar --json --from-file examples/usd-inr-30d.json trend USD INR
{
  "from": "USD",
  "to": "INR",
  "days": 21,
  "start": "2026-10-09",
  "end": "2026-11-06",
  "first": 96.73,
  "last": 93.92,
  "min": 93.92,
  "max": 96.73,
  "average": 95.02380952380955,
  "change_pct": -2.9049932802646565,
  "source": "ECB reference rates via api.frankfurter.app (mid-market)"
}
```

Cross-currency conversion goes through the snapshot's base (here USD), so it works offline too:

```sh
$ java -jar build/rupeerates.jar --from-file examples/latest-usd.json convert 100 EUR INR
100.00 EUR = 10,486.12 INR
Rate: 1 EUR = 104.8612 INR  (ECB reference, 2026-11-06)
```

Errors are explicit, with the right exit code — no invented rates, ever:

```sh
$ java -jar build/rupeerates.jar                       # live, in a sandbox that blocks JVM HTTPS
error: Could not reach api.frankfurter.app (Unrecognized SSL message, plaintext connection?). Check your connection, or replay a saved snapshot with --from-file. RupeeRates never shows invented rates.
# exit 1

$ java -jar build/rupeerates.jar --from-file examples/latest-usd.json convert 10 AED INR
error: AED is not published by the European Central Bank, so Frankfurter has no AED rate. Pick another currency — see https://www.frankfurter.app/docs/ for the full list.
# exit 2

$ java -jar build/rupeerates.jar --from-file examples/latest-usd.json convert 10 XXX INR
error: Unknown or unsupported currency: 'XXX' — the ECB does not publish it via Frankfurter.
# exit 2
```

**Live-run caveat:** in this build sandbox, JVM outbound HTTPS is blocked, so a live CLI run fails with the clear error above (exit 1) — that is the designed behaviour, not a bug. All code paths are proven with `--from-file` against the two real snapshots, and the `docs/` dashboard calls Frankfurter directly from the browser, where the same endpoints work (the snapshots in `examples/` were fetched from them with curl).

## Project layout

```
src/rupeerates/
  Main.java               CLI: commands, flags, exit codes, --serve dashboard
  FrankfurterClient.java  HttpClient (follows redirects), snapshot loading
  Rates.java              model + Frankfurter JSON parsing + INR inversion
  Converter.java          conversion math, fee/hidden-cost math, trend stats, sparkline
  Formatter.java          text + JSON output, ₹ formatting
  Json.java               minimal zero-dependency JSON parser
test/rupeerates/
  JsonTest.java  RatesTest.java  ConverterTest.java  CliTest.java   (46 tests)
examples/
  latest-usd.json         Frankfurter /latest?from=USD, 2026-11-06 (real snapshot)
  usd-inr-30d.json        Frankfurter 30-day USD→INR series, 21 trading days (real snapshot)
docs/index.html           self-contained interactive dashboard (inline CSS+JS, zero external assets)
build.sh                  compile + test + package build/rupeerates.jar
```

## Live demo

https://judd69.github.io/rupeerates/ — flip Pages on (Settings → Pages → branch `main`, folder `/docs`) after pushing.

## License

MIT — see [LICENSE](LICENSE).
