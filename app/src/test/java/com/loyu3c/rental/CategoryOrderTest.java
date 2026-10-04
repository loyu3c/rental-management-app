package com.loyu3c.rental;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
public class CategoryOrderTest {
    @Test public void sortingPreservesIdentityStatusAndBackupOrder() throws Exception {
        JSONArray rows=ExpenseCategories.defaults();rows.getJSONObject(4).put("active",false);String before=rows.toString();
        JSONArray moved=ExpenseCategories.move(rows,"清潔打掃",0);assertEquals(before,rows.toString());assertEquals("清潔打掃",moved.getJSONObject(0).getString("id"));assertFalse(moved.getJSONObject(0).getBoolean("active"));
        ExpenseCategories.validate(moved,new JSONArray().put(new JSONObject().put("category","修繕")));
        assertEquals(moved.toString(),new JSONArray(moved.toString()).toString());
        assertEquals(before,ExpenseCategories.move(moved,"清潔打掃",4).toString());
        try{ExpenseCategories.move(rows,"missing",0);fail();}catch(IllegalArgumentException expected){}
    }
}
