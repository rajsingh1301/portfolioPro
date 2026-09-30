package com.portfoliopro.analysis;

import com.portfoliopro.market.Candle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * ta4j is checked against independent implementations of the textbook formulas, written
 * here from the definitions rather than by calling ta4j a second way. That catches the
 * mistakes a library does not: a wrong period, a wrong window, a sample rather than
 * population deviation, a series read backwards.
 */
@DisplayName("Indicator calculator")
class IndicatorCalculatorTest {

    private static final Duration DAY = Duration.ofDays(1);
    private static final int BARS = 300;

    private final double[] closes = closes();
    private final IndicatorCalculator.Computed computed = IndicatorCalculator.compute(candles(closes), DAY);

    @Test
    @DisplayName("SMA is the plain mean of the last N closes, and undefined before N bars exist")
    void sma() {
        assertWarmup(computed.sma20(), 19);
        assertWarmup(computed.sma50(), 49);
        for (int i = 19; i < BARS; i++) {
            assertThat(computed.sma20().get(i).doubleValue()).isCloseTo(mean(closes, i, 20), within(1e-9));
        }
        for (int i = 49; i < BARS; i++) {
            assertThat(computed.sma50().get(i).doubleValue()).isCloseTo(mean(closes, i, 50), within(1e-9));
        }
    }

    @Test
    @DisplayName("Bollinger bands are the 20-bar mean plus and minus two population standard deviations")
    void bollinger() {
        assertWarmup(computed.bollingerMiddle(), 19);
        for (int i = 19; i < BARS; i++) {
            double mean = mean(closes, i, 20);
            double deviation = populationDeviation(closes, i, 20);
            assertThat(computed.bollingerMiddle().get(i).doubleValue()).isCloseTo(mean, within(1e-9));
            assertThat(computed.bollingerUpper().get(i).doubleValue()).isCloseTo(mean + 2 * deviation, within(1e-6));
            assertThat(computed.bollingerLower().get(i).doubleValue()).isCloseTo(mean - 2 * deviation, within(1e-6));
        }
    }

    @Test
    @DisplayName("EMA(20) matches alpha = 2/(N+1) smoothing once past its seed")
    void ema() {
        double[] reference = ema(closes, 20);
        assertWarmup(computed.ema20(), 20);
        // The seed differs between conventions but its weight decays away; by the end of
        // 300 bars the two must agree to far more places than a chart can show.
        for (int i = 200; i < BARS; i++) {
            assertThat(computed.ema20().get(i).doubleValue()).isCloseTo(reference[i], within(1e-4));
        }
    }

    @Test
    @DisplayName("RSI(14) matches Wilder's smoothing once past its seed, and stays within 0 to 100")
    void rsi() {
        double[] reference = wilderRsi(closes, 14);
        assertWarmup(computed.rsi14(), 14);
        for (int i = 14; i < BARS; i++) {
            double value = computed.rsi14().get(i).doubleValue();
            assertThat(value).isBetween(0.0, 100.0);
            if (i >= 200) {
                assertThat(value).isCloseTo(reference[i], within(1e-3));
            }
        }
    }

    @Test
    @DisplayName("MACD is EMA(12) minus EMA(26), its signal is EMA(9) of that, and the histogram is the difference")
    void macd() {
        double[] fast = ema(closes, 12);
        double[] slow = ema(closes, 26);
        double[] line = new double[BARS];
        for (int i = 0; i < BARS; i++) {
            line[i] = fast[i] - slow[i];
        }
        double[] signal = ema(line, 9);

        assertWarmup(computed.macdLine(), 26);
        assertWarmup(computed.macdSignal(), 34);
        assertWarmup(computed.macdHistogram(), 34);
        for (int i = 200; i < BARS; i++) {
            assertThat(computed.macdLine().get(i).doubleValue()).isCloseTo(line[i], within(1e-4));
            assertThat(computed.macdSignal().get(i).doubleValue()).isCloseTo(signal[i], within(1e-4));
            assertThat(computed.macdHistogram().get(i).doubleValue()).isCloseTo(line[i] - signal[i], within(1e-4));
        }
    }

    @Test
    @DisplayName("every series has one entry per candle, and a series shorter than its window is all undefined")
    void alignment() {
        assertThat(computed.sma20()).hasSize(BARS);
        assertThat(computed.macdSignal()).hasSize(BARS);

        IndicatorCalculator.Computed tiny = IndicatorCalculator.compute(candles(java.util.Arrays.copyOf(closes, 10)), DAY);
        assertThat(tiny.sma20()).hasSize(10).containsOnlyNulls();
        assertThat(tiny.rsi14()).hasSize(10).containsOnlyNulls();
        assertThat(tiny.macdLine()).hasSize(10).containsOnlyNulls();
    }

    // ---- independent reference implementations ----

    private static void assertWarmup(List<BigDecimal> series, int firstDefined) {
        for (int i = 0; i < firstDefined; i++) {
            assertThat(series.get(i)).as("index %d is before the window is full", i).isNull();
        }
        assertThat(series.get(firstDefined)).as("index %d is the first with a full window", firstDefined).isNotNull();
    }

    private static double mean(double[] values, int end, int window) {
        double sum = 0;
        for (int i = end - window + 1; i <= end; i++) sum += values[i];
        return sum / window;
    }

    private static double populationDeviation(double[] values, int end, int window) {
        double mean = mean(values, end, window);
        double squares = 0;
        for (int i = end - window + 1; i <= end; i++) squares += (values[i] - mean) * (values[i] - mean);
        return Math.sqrt(squares / window);
    }

    private static double[] ema(double[] values, int period) {
        double alpha = 2.0 / (period + 1);
        double[] result = new double[values.length];
        result[0] = values[0];
        for (int i = 1; i < values.length; i++) {
            result[i] = alpha * values[i] + (1 - alpha) * result[i - 1];
        }
        return result;
    }

    private static double[] wilderRsi(double[] values, int period) {
        double[] rsi = new double[values.length];
        double gain = 0;
        double loss = 0;
        for (int i = 1; i <= period; i++) {
            double change = values[i] - values[i - 1];
            gain += Math.max(change, 0);
            loss += Math.max(-change, 0);
        }
        gain /= period;
        loss /= period;
        rsi[period] = 100 - 100 / (1 + gain / loss);
        for (int i = period + 1; i < values.length; i++) {
            double change = values[i] - values[i - 1];
            gain = (gain * (period - 1) + Math.max(change, 0)) / period;
            loss = (loss * (period - 1) + Math.max(-change, 0)) / period;
            rsi[i] = 100 - 100 / (1 + gain / loss);
        }
        return rsi;
    }

    /** A reproducible random walk with drift, so the test never depends on a market. */
    private static double[] closes() {
        Random random = new Random(42);
        double[] values = new double[BARS];
        double price = 100;
        for (int i = 0; i < BARS; i++) {
            price += random.nextGaussian() * 1.5 + 0.02;
            values[i] = Math.round(price * 10000) / 10000.0;
        }
        return values;
    }

    private static List<Candle> candles(double[] closes) {
        List<Candle> candles = new ArrayList<>();
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        for (int i = 0; i < closes.length; i++) {
            BigDecimal close = BigDecimal.valueOf(closes[i]);
            candles.add(new Candle(start.plus(DAY.multipliedBy(i)), close, close, close, close, 1000));
        }
        return candles;
    }
}
