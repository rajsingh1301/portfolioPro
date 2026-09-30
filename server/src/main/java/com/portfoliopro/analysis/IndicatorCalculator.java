package com.portfoliopro.analysis;

import com.portfoliopro.market.Candle;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The indicator maths, on decimals. Pure: candles in, aligned series out, no I/O, so it
 * can be checked against independent implementations of the same formulas.
 *
 * <p>ta4j does the arithmetic, but it does not decide where a series becomes trustworthy.
 * Here each series is {@code null} until it has seen a full window, so an average of
 * three bars is never passed off as a 50-bar average.
 */
public final class IndicatorCalculator {

    static final int SMA_SHORT = 20;
    static final int SMA_LONG = 50;
    static final int EMA_PERIOD = 20;
    static final int RSI_PERIOD = 14;
    static final int MACD_FAST = 12;
    static final int MACD_SLOW = 26;
    static final int MACD_SIGNAL = 9;
    static final int BOLLINGER_PERIOD = 20;
    private static final int BOLLINGER_K = 2;

    private IndicatorCalculator() {
    }

    /** Each list has one entry per candle, in order; {@code null} where the indicator is not yet defined. */
    public record Computed(
            List<BigDecimal> sma20,
            List<BigDecimal> sma50,
            List<BigDecimal> ema20,
            List<BigDecimal> rsi14,
            List<BigDecimal> macdLine,
            List<BigDecimal> macdSignal,
            List<BigDecimal> macdHistogram,
            List<BigDecimal> bollingerUpper,
            List<BigDecimal> bollingerMiddle,
            List<BigDecimal> bollingerLower) {
    }

    /** {@code candles} must be ascending by time; {@code barDuration} is how long one candle spans. */
    public static Computed compute(List<Candle> candles, Duration barDuration) {
        NumFactory numbers = DecimalNumFactory.getInstance();
        BarSeries series = new BaseBarSeriesBuilder().withName("candles").withNumFactory(numbers).build();
        for (Candle candle : candles) {
            series.barBuilder()
                    .timePeriod(barDuration)
                    .endTime(candle.time().plus(barDuration))
                    .openPrice(numbers.numOf(candle.open()))
                    .highPrice(numbers.numOf(candle.high()))
                    .lowPrice(numbers.numOf(candle.low()))
                    .closePrice(numbers.numOf(candle.close()))
                    .volume(numbers.numOf(candle.volume()))
                    .add();
        }

        Indicator<Num> close = new ClosePriceIndicator(series);
        SMAIndicator sma20 = new SMAIndicator(close, SMA_SHORT);
        SMAIndicator sma50 = new SMAIndicator(close, SMA_LONG);
        EMAIndicator ema20 = new EMAIndicator(close, EMA_PERIOD);
        RSIIndicator rsi = new RSIIndicator(close, RSI_PERIOD);
        MACDIndicator macd = new MACDIndicator(close, MACD_FAST, MACD_SLOW);
        EMAIndicator signal = macd.getSignalLine(MACD_SIGNAL);
        Indicator<Num> histogram = macd.getHistogram(MACD_SIGNAL);

        BollingerBandsMiddleIndicator middle = new BollingerBandsMiddleIndicator(new SMAIndicator(close, BOLLINGER_PERIOD));
        // Population deviation, which is what Bollinger's own definition uses.
        Indicator<Num> deviation = StandardDeviationIndicator.ofPopulation(close, BOLLINGER_PERIOD);
        Num k = numbers.numOf(BOLLINGER_K);
        Indicator<Num> upper = new BollingerBandsUpperIndicator(middle, deviation, k);
        Indicator<Num> lower = new BollingerBandsLowerIndicator(middle, deviation, k);

        // First index at which each series has seen a full window. ta4j leaves an EMA
        // undefined until the index reaches its period, one bar later than the textbook
        // convention, and the MACD line inherits that. Following it errs on the side of
        // a series that starts a bar late, never one that starts a bar early. The signal
        // is an EMA of the MACD line, so it needs its own nine bars on top of that.
        int macdStart = MACD_SLOW;
        int signalStart = macdStart + MACD_SIGNAL - 1;
        return new Computed(
                column(sma20, SMA_SHORT - 1),
                column(sma50, SMA_LONG - 1),
                column(ema20, EMA_PERIOD),
                column(rsi, RSI_PERIOD),
                column(macd, macdStart),
                column(signal, signalStart),
                column(histogram, signalStart),
                column(upper, BOLLINGER_PERIOD - 1),
                column(middle, BOLLINGER_PERIOD - 1),
                column(lower, BOLLINGER_PERIOD - 1));
    }

    private static List<BigDecimal> column(Indicator<Num> indicator, int firstDefined) {
        int count = indicator.getBarSeries().getBarCount();
        List<BigDecimal> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Num value = i < firstDefined ? null : indicator.getValue(i);
            values.add(Num.isNaNOrNull(value) ? null : value.bigDecimalValue());
        }
        return values;
    }
}
