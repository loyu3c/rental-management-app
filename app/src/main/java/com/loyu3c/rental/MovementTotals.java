package com.loyu3c.rental;

import org.json.JSONObject;

/** Totals for all matching movements, independently of display page size. */
final class MovementTotals {
    long expenses, transfers;
    void add(JSONObject movement) {
        if(movement.optBoolean("void"))return;
        long amount=movement.optLong("amount");
        if(movement.has("fromId"))transfers=Math.addExact(transfers,amount);
        else expenses=Math.addExact(expenses,amount);
    }
}
