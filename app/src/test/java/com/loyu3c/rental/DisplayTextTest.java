package com.loyu3c.rental;

import org.junit.Test;
import static org.junit.Assert.*;

public class DisplayTextTest {
    @Test public void currencyUsesThousandsWhileDatesAndUnitCodesRemainIntact() {
        assertEquals("2026-10 R1401 房貸 $12,345 · 差額 $-1,260",DisplayText.money("2026-10 R1401 房貸 $12345 · 差額 $-1260"));
        assertEquals("9,760",DisplayText.number(9760));
    }
    @Test public void repeatedFormattingDoesNotCorruptFormattedMoneyOrUserNotes() {
        String raw="合計 $9550 · 備註 $999999999999999999999";
        String once=DisplayText.money(raw);assertEquals(once,DisplayText.money(once));assertTrue(once.contains("$999999999999999999999"));
    }
}
