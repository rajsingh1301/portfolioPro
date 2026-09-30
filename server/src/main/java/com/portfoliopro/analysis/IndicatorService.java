package com.portfoliopro.analysis;

import com.portfoliopro.analysis.dto.IndicatorPoint;
import com.portfoliopro.analysis.dto.IndicatorResponse;
import com.portfoliopro.common.exception.NotFoundException;
import com.portfoliopro.market.Candle;
import com.portfoliopro.market.CandleRange;
import com.portfoliopro.market.MarketService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class IndicatorService {

    static final String DISCLAIMER =
            "Indicators describe past prices. They are frequently wrong and are not advice.";

    private final MarketService marketService;

    public IndicatorService(MarketService marketService) {
        this.marketService = marketService;
    }

    /**
     * Indicators need history before their first value, so a short range cannot be
     * computed on its own candles: a 50-bar average over a month of daily bars would be
     * empty. The daily ranges are therefore computed on the one-year series and then cut
     * back to the range asked for. That series is the same one the chart already caches,
     * so it costs no extra provider call.
     */
    public IndicatorResponse indicators(String symbol, String rangeLabel) {
        CandleRange range = CandleRange.fromLabel(rangeLabel)
                .orElseThrow(() -> new NotFoundException("Unknown range: " + rangeLabel));
        CandleRange basis = switch (range) {
            case MONTH, HALF_YEAR, YEAR -> CandleRange.YEAR;
            default -> range;
        };

        List<Candle> candles = marketService.candleSeries(symbol, basis);
        IndicatorCalculator.Computed computed = IndicatorCalculator.compute(candles, basis.barDuration());
        int from = Math.max(candles.size() - range.size(), 0);

        return new IndicatorResponse(
                MarketService.normalizeSymbol(symbol),
                range.label(),
                points(candles, computed.sma20(), from, 4),
                points(candles, computed.sma50(), from, 4),
                points(candles, computed.ema20(), from, 4),
                points(candles, computed.rsi14(), from, 2),
                new IndicatorResponse.Macd(
                        points(candles, computed.macdLine(), from, 4),
                        points(candles, computed.macdSignal(), from, 4),
                        points(candles, computed.macdHistogram(), from, 4)),
                new IndicatorResponse.Bollinger(
                        points(candles, computed.bollingerUpper(), from, 4),
                        points(candles, computed.bollingerMiddle(), from, 4),
                        points(candles, computed.bollingerLower(), from, 4)),
                Readings.describe(candles, computed, basis.barUnit()),
                DISCLAIMER);
    }

    private static List<IndicatorPoint> points(List<Candle> candles, List<BigDecimal> values, int from, int scale) {
        List<IndicatorPoint> points = new ArrayList<>();
        for (int i = from; i < candles.size(); i++) {
            BigDecimal value = values.get(i);
            if (value != null) {
                points.add(new IndicatorPoint(
                        candles.get(i).time().getEpochSecond(),
                        value.setScale(scale, RoundingMode.HALF_UP).toPlainString()));
            }
        }
        return points;
    }
}
