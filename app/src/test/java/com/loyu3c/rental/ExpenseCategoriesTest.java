package com.loyu3c.rental;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ExpenseCategoriesTest {
    @Test public void renameAndDisablePreserveExpenseIdentity() throws Exception {
        JSONArray categories=ExpenseCategories.defaults(),expenses=new JSONArray().put(new JSONObject().put("category","修繕"));String before=expenses.toString();categories.getJSONObject(0).put("name","維修材料").put("active",false);ExpenseCategories.validate(categories,expenses);assertEquals("維修材料",ExpenseCategories.name(categories,"修繕"));assertEquals(before,expenses.toString());
    }
    @Test public void duplicateAndMissingReferencesAreRejected() throws Exception {
        JSONArray categories=ExpenseCategories.defaults();categories.put(new JSONObject().put("id","custom").put("name","清潔打掃").put("active",true));try{ExpenseCategories.validate(categories,new JSONArray());fail();}catch(IllegalArgumentException expected){}
        try{ExpenseCategories.validate(ExpenseCategories.defaults(),new JSONArray().put(new JSONObject().put("category","missing")));fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void catalogRoundTripKeepsCustomKeysAndNames() throws Exception {
        JSONArray categories=ExpenseCategories.defaults();categories.put(new JSONObject().put("id","custom-id").put("name","庭院整理").put("active",true));JSONObject backup=new JSONObject().put("expenseCategories",categories);JSONArray restored=ExpenseCategories.catalog(new JSONObject(backup.toString()));ExpenseCategories.validate(restored,new JSONArray().put(new JSONObject().put("category","custom-id")));assertEquals("庭院整理",ExpenseCategories.name(restored,"custom-id"));assertEquals(5,ExpenseCategories.catalog(new JSONObject()).length());
    }
}
