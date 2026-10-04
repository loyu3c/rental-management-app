package com.loyu3c.rental;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class MovementTotalsTest {
    @Test public void transfersAndVoidsDoNotIncreaseExpenseTotal() throws Exception {
        MovementTotals totals=new MovementTotals();
        totals.add(new JSONObject().put("amount",3000));
        totals.add(new JSONObject().put("amount",10000).put("fromId","bank"));
        totals.add(new JSONObject().put("amount",500).put("void",true));
        totals.add(new JSONObject().put("amount",7000).put("fromId","bank").put("void",true));
        assertEquals(3000,totals.expenses);assertEquals(10000,totals.transfers);
    }
    @Test public void totalIsNotLimitedToFirstDisplayPageAndUsesLong() throws Exception {
        MovementTotals totals=new MovementTotals();
        for(int i=0;i<20;i++)totals.add(new JSONObject().put("amount",1000000000L));
        assertEquals(20000000000L,totals.expenses);assertEquals(0,totals.transfers);
    }
}
