package com.portfoliopro.trading;

import com.portfoliopro.trading.PerformanceCalculator.Fill;
import com.portfoliopro.trading.PerformanceCalculator.LedgerEntry;
import com.portfoliopro.trading.PerformanceCalculator.Point;
import com.portfoliopro.trading.PerformanceCalculator.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/** Every expected figure below was worked out by hand from the inputs shown. */
@DisplayName("Performance calculator")
class PerformanceCalculatorTest {

    private static final LocalDate MON = LocalDate.of(2026, 9, 21);
    private static final LocalDate TUE = MON.plusDays(1);
    private static final LocalDate WED = MON.plusDays(2);
    private static final LocalDate THU = MON.plusDays(3);
    private static final LocalDate FRI = MON.plusDays(4);
    private static final LocalDate SAT = MON.plusDays(5);
    private static final LocalDate SUN = MON.plusDays(6);

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static LedgerEntry ledger(LocalDate day, String balance) {
        return new LedgerEntry(day, d(balance));
    }

    private static Fill fill(LocalDate day, String symbol, long quantity, String price) {
        return new Fill(day, symbol, quantity, d(price));
    }

    private static NavigableMap<LocalDate, BigDecimal> closes(Object... dayAndPrice) {
        NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
        for (int i = 0; i < dayAndPrice.length; i += 2) {
            series.put((LocalDate) dayAndPrice[i], d((String) dayAndPrice[i + 1]));
        }
        return series;
    }

    private static List<String> values(Result result) {
        return result.points().stream().map(p -> p.date().getDayOfWeek().name().substring(0, 3) + " " + p.value().stripTrailingZeros().toPlainString()).toList();
    }

    @Test
    @DisplayName("an account that has only its opening deposit is worth its cash every day")
    void onlyCash() {
        Result result = PerformanceCalculator.compute(MON, WED, List.of(ledger(MON, "100000")), List.of(), Map.of());

        assertThat(result.points()).extracting(Point::date).containsExactly(MON, WED);
        assertThat(result.points()).allSatisfy(p -> {
            assertThat(p.value()).isEqualByComparingTo("100000");
            assertThat(p.invested()).isEqualByComparingTo("0");
        });
        assertThat(result.estimated()).isEmpty();
    }

    @Test
    @DisplayName("buying moves money from cash into shares, so value only changes as the price does")
    void buyThenPricesMove() {
        // Tuesday: buy 10 AAPL at 100 -> cash 100000 - 1000 = 99000.
        Result result = PerformanceCalculator.compute(
                MON, THU,
                List.of(ledger(MON, "100000"), ledger(TUE, "99000")),
                List.of(fill(TUE, "AAPL", 10, "100")),
                Map.of("AAPL", closes(MON, "99", TUE, "100", WED, "110", THU, "120")));

        // Mon: no shares yet. Tue: 99000 + 10x100. Wed: 99000 + 10x110. Thu: 99000 + 10x120.
        assertThat(values(result)).containsExactly("MON 100000", "TUE 100000", "WED 100100", "THU 100200");
        assertThat(result.points().get(2).invested()).isEqualByComparingTo("1100");
        assertThat(result.points().get(2).cash()).isEqualByComparingTo("99000");
    }

    @Test
    @DisplayName("a sale reduces the shares held and credits the cash, on the day it happens")
    void partialSale() {
        // Buy 10 @ 100 on Mon (cash 99000); sell 4 @ 120 on Wed (cash 99000 + 480 = 99480).
        Result result = PerformanceCalculator.compute(
                MON, THU,
                List.of(ledger(MON, "99000"), ledger(WED, "99480")),
                List.of(fill(MON, "AAPL", 10, "100"), fill(WED, "AAPL", -4, "120")),
                Map.of("AAPL", closes(MON, "100", TUE, "110", WED, "120", THU, "130")));

        // Mon 99000+1000, Tue 99000+1100, Wed 99480 + 6x120, Thu 99480 + 6x130.
        assertThat(values(result)).containsExactly("MON 100000", "TUE 100100", "WED 100200", "THU 100260");
    }

    @Test
    @DisplayName("a fully sold position stops counting, and its proceeds stay in cash")
    void closedPosition() {
        // Buy 5 @ 100, sell 5 @ 90: cash 100000 - 500 + 450 = 99950.
        Result result = PerformanceCalculator.compute(
                MON, WED,
                List.of(ledger(MON, "99500"), ledger(TUE, "99950")),
                List.of(fill(MON, "AAPL", 5, "100"), fill(TUE, "AAPL", -5, "90")),
                Map.of("AAPL", closes(MON, "100", TUE, "90", WED, "50")));

        // Wednesday's price of 50 no longer matters: nothing is held.
        assertThat(values(result)).containsExactly("MON 100000", "TUE 99950", "WED 99950");
        assertThat(result.points().get(2).invested()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("a weekend carries Friday's close forward, and a trade on a Saturday still gets a point")
    void weekendsAndCarryForward() {
        Result result = PerformanceCalculator.compute(
                FRI, SUN,
                List.of(ledger(MON, "99000"), ledger(SAT, "98000")),
                List.of(fill(MON, "AAPL", 10, "100"), fill(SAT, "AAPL", 10, "105")),
                Map.of("AAPL", closes(THU, "104", FRI, "106")));

        // Fri: 99000 + 10x106. Sat: cash 98000, 20 shares at Friday's 106 -> 98000 + 2120. Sun: same.
        assertThat(values(result)).containsExactly("FRI 100060", "SAT 100120", "SUN 100120");
    }

    @Test
    @DisplayName("a window that starts after the trades still counts the shares bought before it")
    void windowStartsLate() {
        Result result = PerformanceCalculator.compute(
                WED, THU,
                List.of(ledger(MON, "99000")),
                List.of(fill(MON, "AAPL", 10, "100")),
                Map.of("AAPL", closes(MON, "100", TUE, "101", WED, "102", THU, "103")));

        assertThat(result.points()).extracting(Point::date).containsExactly(WED, THU);
        assertThat(values(result)).containsExactly("WED 100020", "THU 100030");
    }

    @Test
    @DisplayName("with no price history a holding is valued at its last trade price, and reported as estimated")
    void fallsBackToTradePrice() {
        Result result = PerformanceCalculator.compute(
                MON, WED,
                List.of(ledger(MON, "99000")),
                List.of(fill(MON, "AAPL", 10, "100")),
                Map.of());

        // Every calendar day, so the estimate is drawn as flat rather than as a slant between event days.
        assertThat(values(result)).containsExactly("MON 100000", "TUE 100000", "WED 100000");
        assertThat(result.estimated()).containsExactly("AAPL");
    }

    @Test
    @DisplayName("a symbol with real history leaves weekends out; only one without gets every calendar day")
    void calendarDaysOnlyWhenNeeded() {
        var withHistory = PerformanceCalculator.compute(
                FRI, SUN, List.of(ledger(FRI, "99000")), List.of(fill(FRI, "AAPL", 10, "100")),
                Map.of("AAPL", closes(FRI, "100")));
        assertThat(withHistory.points()).extracting(Point::date).containsExactly(FRI, SUN);

        var without = PerformanceCalculator.compute(
                FRI, SUN, List.of(ledger(FRI, "99000")), List.of(fill(FRI, "AAPL", 10, "100")), Map.of());
        assertThat(without.points()).extracting(Point::date).containsExactly(FRI, SAT, SUN);
    }

    @Test
    @DisplayName("before a series begins, a symbol is valued at its trade price; from then on at the close")
    void seriesStartsAfterTheTrade() {
        Result result = PerformanceCalculator.compute(
                MON, WED,
                List.of(ledger(MON, "99000")),
                List.of(fill(MON, "AAPL", 10, "100")),
                Map.of("AAPL", closes(TUE, "120", WED, "130")));

        // Mon has no close on or before it -> trade price 100. Tue and Wed use the closes.
        assertThat(values(result)).containsExactly("MON 100000", "TUE 100200", "WED 100300");
        assertThat(result.estimated()).containsExactly("AAPL");
    }

    @Test
    @DisplayName("two symbols are valued independently and summed")
    void twoSymbols() {
        Result result = PerformanceCalculator.compute(
                MON, TUE,
                List.of(ledger(MON, "97000")),
                List.of(fill(MON, "AAPL", 10, "100"), fill(MON, "MSFT", 20, "100")),
                Map.of("AAPL", closes(MON, "100", TUE, "110"), "MSFT", closes(MON, "100", TUE, "95")));

        // Tue: 97000 + 10x110 + 20x95 = 97000 + 1100 + 1900.
        assertThat(values(result)).containsExactly("MON 100000", "TUE 100000");
        assertThat(result.points().get(1).invested()).isEqualByComparingTo("3000");
    }

    @Test
    @DisplayName("prices with many decimals are summed without float error")
    void exactDecimals() {
        Result result = PerformanceCalculator.compute(
                MON, MON,
                List.of(ledger(MON, "99999.9000")),
                List.of(fill(MON, "X", 3, "0.1")),
                Map.of("X", closes(MON, "0.1")));

        // 3 x 0.1 must be exactly 0.3, and 99999.9 + 0.3 exactly 100000.2.
        assertThat(result.points().get(0).invested()).isEqualByComparingTo("0.3");
        assertThat(result.points().get(0).value()).isEqualByComparingTo("100000.2");
    }
}
