package com.portfoliopro.analysis;

import com.portfoliopro.analysis.dto.Reading;
import com.portfoliopro.market.Candle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** What the indicators say in words, and that none of it is ever advice (rule 8). */
@DisplayName("Indicator readings")
class ReadingsTest {

    private static final List<String> BANNED = List.of("buy", "sell", "bullish", "bearish", "should", "recommend");

    /** Builds a Computed of {@code n} bars with every series undefined, to be filled in per test. */
    private static Fixture fixture(int n, String close) {
        return new Fixture(n, close);
    }

    private static final class Fixture {
        final int n;
        final List<Candle> candles = new ArrayList<>();
        final List<BigDecimal> sma20;
        final List<BigDecimal> sma50;
        final List<BigDecimal> rsi;
        final List<BigDecimal> histogram;
        final List<BigDecimal> upper;
        final List<BigDecimal> lower;

        Fixture(int n, String close) {
            this.n = n;
            // Built here, not as field initialisers, which would run before `n` is set.
            sma20 = nulls();
            sma50 = nulls();
            rsi = nulls();
            histogram = nulls();
            upper = nulls();
            lower = nulls();
            for (int i = 0; i < n; i++) {
                BigDecimal c = new BigDecimal(close);
                candles.add(new Candle(Instant.EPOCH.plusSeconds(i * 86400L), c, c, c, c, 1));
            }
        }

        List<BigDecimal> nulls() {
            return new ArrayList<>(Arrays.asList(new BigDecimal[n]));
        }

        IndicatorCalculator.Computed readingsComputed() {
            List<BigDecimal> none = nulls();
            return new IndicatorCalculator.Computed(none, none, none, none, none, none, none, none, none, none);
        }

        List<Reading> readings() {
            List<BigDecimal> none = nulls();
            IndicatorCalculator.Computed computed = new IndicatorCalculator.Computed(
                    sma20, sma50, none, rsi, none, none, histogram, upper, none, lower);
            return Readings.describe(candles, computed, "day");
        }

        void last(List<BigDecimal> series, String value) {
            series.set(n - 1, new BigDecimal(value));
        }

        void at(List<BigDecimal> series, int index, String value) {
            series.set(index, new BigDecimal(value));
        }
    }

    private static String label(List<Reading> readings, String indicator) {
        return readings.stream().filter(r -> r.indicator().equals(indicator)).map(Reading::label).findFirst().orElse(null);
    }

    @Test
    @DisplayName("RSI is described by zone: 70 and above overbought, 30 and below oversold, otherwise neutral")
    void rsiZones() {
        for (var expectation : List.of(
                new String[] {"74.26", "RSI is 74.3, in the overbought zone"},
                new String[] {"70", "RSI is 70.0, in the overbought zone"},
                new String[] {"30", "RSI is 30.0, in the oversold zone"},
                new String[] {"12.5", "RSI is 12.5, in the oversold zone"},
                new String[] {"50", "RSI is 50.0, in the neutral range"})) {
            Fixture f = fixture(3, "100");
            f.last(f.rsi, expectation[0]);
            assertThat(label(f.readings(), "RSI (14)")).isEqualTo(expectation[1]);
        }
    }

    @Test
    @DisplayName("MACD is reported as crossing only on the bar the histogram changes sign")
    void macdCross() {
        Fixture up = fixture(3, "100");
        up.at(up.histogram, 1, "-0.10");
        up.at(up.histogram, 2, "0.20");
        assertThat(label(up.readings(), "MACD (12, 26, 9)")).isEqualTo("MACD crossed above its signal line");

        Fixture down = fixture(3, "100");
        down.at(down.histogram, 1, "0.10");
        down.at(down.histogram, 2, "-0.20");
        assertThat(label(down.readings(), "MACD (12, 26, 9)")).isEqualTo("MACD crossed below its signal line");

        Fixture above = fixture(3, "100");
        above.at(above.histogram, 1, "0.10");
        above.at(above.histogram, 2, "0.30");
        assertThat(label(above.readings(), "MACD (12, 26, 9)")).isEqualTo("MACD is above its signal line");

        Fixture below = fixture(3, "100");
        below.at(below.histogram, 1, "-0.10");
        below.at(below.histogram, 2, "-0.30");
        assertThat(label(below.readings(), "MACD (12, 26, 9)")).isEqualTo("MACD is below its signal line");
    }

    @Test
    @DisplayName("price is placed against the 50-bar average, named in the bars' own unit")
    void priceVersusAverage() {
        Fixture above = fixture(3, "100");
        above.last(above.sma50, "90");
        assertThat(label(above.readings(), "Moving average (50)")).isEqualTo("Price is above its 50-day average");

        Fixture below = fixture(3, "100");
        below.last(below.sma50, "110");
        assertThat(label(below.readings(), "Moving average (50)")).isEqualTo("Price is below its 50-day average");
    }

    @Test
    @DisplayName("a crossing of the 20 and 50 averages is reported for five bars, then forgotten")
    void averagesCross() {
        Fixture recent = fixture(10, "100");
        for (int i = 0; i < 10; i++) {
            recent.at(recent.sma50, i, "100");
            recent.at(recent.sma20, i, i < 7 ? "99" : "101"); // crosses above at index 7
        }
        assertThat(label(recent.readings(), "Moving averages (20, 50)"))
                .isEqualTo("The 20-day average crossed above the 50-day average recently");

        Fixture old = fixture(20, "100");
        for (int i = 0; i < 20; i++) {
            old.at(old.sma50, i, "100");
            old.at(old.sma20, i, i < 5 ? "99" : "101"); // crossed at index 5, fourteen bars ago
        }
        assertThat(label(old.readings(), "Moving averages (20, 50)")).isNull();

        Fixture below = fixture(10, "100");
        for (int i = 0; i < 10; i++) {
            below.at(below.sma50, i, "100");
            below.at(below.sma20, i, i < 9 ? "101" : "99");
        }
        assertThat(label(below.readings(), "Moving averages (20, 50)"))
                .isEqualTo("The 20-day average crossed below the 50-day average recently");
    }

    @Test
    @DisplayName("price is placed against the Bollinger bands")
    void bollinger() {
        Fixture above = fixture(3, "120");
        above.last(above.upper, "110");
        above.last(above.lower, "90");
        assertThat(label(above.readings(), "Bollinger Bands (20, 2)")).isEqualTo("Price is above the upper band");

        Fixture below = fixture(3, "80");
        below.last(below.upper, "110");
        below.last(below.lower, "90");
        assertThat(label(below.readings(), "Bollinger Bands (20, 2)")).isEqualTo("Price is below the lower band");

        Fixture within = fixture(3, "100");
        within.last(within.upper, "110");
        within.last(within.lower, "90");
        assertThat(label(within.readings(), "Bollinger Bands (20, 2)")).isEqualTo("Price is within the bands");
    }

    @Test
    @DisplayName("an indicator with no value yet says nothing, and no candles means no readings")
    void undefinedIsSilent() {
        assertThat(fixture(3, "100").readings()).isEmpty();
        assertThat(Readings.describe(List.of(), fixture(1, "1").readingsComputed(), "day")).isEmpty();
    }

    @Test
    @DisplayName("no reading ever contains advice: no buy, sell, bullish, bearish, should or recommend")
    void neverAdvises() {
        List<String> everything = new ArrayList<>();
        for (String rsi : List.of("90", "50", "10")) {
            for (String hist : List.of("-1", "1")) {
                Fixture f = fixture(10, "100");
                f.last(f.rsi, rsi);
                f.at(f.histogram, 8, hist.equals("1") ? "-1" : "1");
                f.last(f.histogram, hist);
                f.last(f.sma50, "90");
                f.last(f.upper, "95");
                f.last(f.lower, "85");
                for (int i = 0; i < 10; i++) {
                    f.at(f.sma50, i, "100");
                    f.at(f.sma20, i, i < 8 ? "99" : "101");
                }
                f.readings().forEach(r -> {
                    everything.add(r.indicator());
                    everything.add(r.label());
                });
            }
        }
        assertThat(everything).isNotEmpty();
        for (String text : everything) {
            for (String banned : BANNED) {
                assertThat(text.toLowerCase()).as("'%s' must not contain '%s'", text, banned).doesNotContain(banned);
            }
        }
        assertThat(IndicatorService.DISCLAIMER.toLowerCase()).doesNotContain("buy", "sell", "recommend");
    }
}
