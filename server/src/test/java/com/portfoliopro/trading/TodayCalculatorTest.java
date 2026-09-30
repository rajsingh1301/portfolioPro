package com.portfoliopro.trading;

import com.portfoliopro.trading.TodayCalculator.Fill;
import com.portfoliopro.trading.TodayCalculator.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Every expected figure was worked out by hand; the reasoning is beside each case. */
@DisplayName("Day P&L calculator")
class TodayCalculatorTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static Fill fill(long quantity, String price) {
        return new Fill(quantity, d(price));
    }

    private static BigDecimal dayPnl(long quantityNow, String price, String previousClose, Fill... fills) {
        return TodayCalculator.compute(new Position("X", quantityNow, d(price), d(previousClose), List.of(fills))).dayPnl();
    }

    @Test
    @DisplayName("shares held since before move by the change from the previous close")
    void heldBefore() {
        // 10 shares, 100 -> 105: 10 x 5.
        assertThat(dayPnl(10, "105", "100")).isEqualByComparingTo("50");
        // and a fall is a loss: 2 shares, 50 -> 48.50 is -3.
        assertThat(dayPnl(2, "48.5", "50")).isEqualByComparingTo("-3");
    }

    @Test
    @DisplayName("shares bought today have not moved from the previous close, only from what was paid")
    void boughtToday() {
        // Bought 3 at 329.40 and the price is still 329.40, though the previous close was 338.07.
        // The naive 3 x (329.40 - 338.07) would say -26.01; the truth is nothing has happened yet.
        assertThat(dayPnl(3, "329.40", "338.07", fill(3, "329.40"))).isEqualByComparingTo("0");
        // Bought 3 at 100, now 105, previous close 98: 3 x (105 - 100) = 15, not 3 x (105 - 98) = 21.
        assertThat(dayPnl(3, "105", "98", fill(3, "100"))).isEqualByComparingTo("15");
    }

    @Test
    @DisplayName("selling part of a position today counts what the sold shares made against the previous close")
    void soldSome() {
        // Held 10 (previous close 100), sold 4 at 110, 6 left, price now 105.
        // 6 left moved +5 each = 30; the 4 sold went at 110 vs 100 = +40. Together 70.
        // As the formula: 6x105 - 10x100 - (-4x110) = 630 - 1000 + 440.
        assertThat(dayPnl(6, "105", "100", fill(-4, "110"))).isEqualByComparingTo("70");
    }

    @Test
    @DisplayName("adding to a position splits it: the old shares against the close, the new against the price paid")
    void boughtMore() {
        // Held 10 (previous close 100), bought 5 more at 104, 15 now at 105.
        // Old 10: +5 each = 50. New 5: 105 - 104 = +1 each = 5. Together 55.
        // As the formula: 15x105 - 10x100 - 5x104 = 1575 - 1000 - 520.
        assertThat(dayPnl(15, "105", "100", fill(5, "104"))).isEqualByComparingTo("55");
    }

    @Test
    @DisplayName("a position opened and closed today has made exactly what the round trip made")
    void roundTrip() {
        // Bought 5 at 100, sold 5 at 110, nothing left, price now 108 (irrelevant): +50.
        assertThat(dayPnl(0, "108", "97", fill(5, "100"), fill(-5, "110"))).isEqualByComparingTo("50");
        // Bought 5 at 100, sold 5 at 90: -50.
        assertThat(dayPnl(0, "108", "97", fill(5, "100"), fill(-5, "90"))).isEqualByComparingTo("-50");
    }

    @Test
    @DisplayName("selling everything that was held before today locks in the move from the previous close")
    void soldAll() {
        // Held 10 (previous close 100), sold all 10 at 106: 10 x 6 = +60.
        assertThat(dayPnl(0, "104", "100", fill(-10, "106"))).isEqualByComparingTo("60");
    }

    @Test
    @DisplayName("the per-share change and its percentage are against the previous close")
    void perShare() {
        var result = TodayCalculator.compute(new Position("X", 4, d("105"), d("100"), List.of()));
        assertThat(result.dayChange()).isEqualByComparingTo("5");
        assertThat(result.dayChangePercent()).isEqualByComparingTo("5");
        assertThat(result.quantity()).isEqualTo(4);

        var down = TodayCalculator.compute(new Position("X", 1, d("47.5"), d("50"), List.of()));
        assertThat(down.dayChangePercent()).isEqualByComparingTo("-5");
    }

    @Test
    @DisplayName("a zero previous close gives no percentage instead of dividing by zero")
    void zeroPreviousClose() {
        var result = TodayCalculator.compute(new Position("X", 1, d("10"), d("0"), List.of()));
        assertThat(result.dayChangePercent()).isEqualByComparingTo("0");
        assertThat(result.dayPnl()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("decimals are exact: 3 shares at 0.1 bought today and now 0.3 each is exactly 0.6 up")
    void exactDecimals() {
        // 3 x 0.3 = 0.9 worth now, paid 3 x 0.1 = 0.3, so +0.6. A float sum would not land on 0.6.
        assertThat(dayPnl(3, "0.3", "0.2", fill(3, "0.1"))).isEqualByComparingTo("0.6");
    }
}
