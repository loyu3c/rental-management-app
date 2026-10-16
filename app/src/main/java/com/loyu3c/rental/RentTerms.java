package com.loyu3c.rental;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.json.*;

/** Explicit opt-in terms; legacy leases retain posted monthly invoices. */
public final class RentTerms {
    public static final String[] CYCLES={"月繳","季繳","半年繳","年繳","一次付清"};
    public static final String[] PRICING={"按月","按日","整段固定總價"};
    public static boolean scheduled(JSONObject lease){return lease.optInt("termsVersion")>=1;}
    public static int months(JSONObject lease){String c=lease.optString("cycle","月繳");return c.equals("季繳")?3:c.equals("半年繳")?6:c.equals("年繳")?12:1;}
    public static String pricing(JSONObject lease){return lease.optString("pricing","按月");}
    public static void validate(JSONObject lease)throws JSONException{
        if(!Arrays.asList(CYCLES).contains(lease.getString("cycle"))||!Arrays.asList(PRICING).contains(pricing(lease)))throw new IllegalArgumentException("租金計價或繳款週期無效");
        if(!pricing(lease).equals("按月")&&!lease.getString("cycle").equals("一次付清"))throw new IllegalArgumentException("按日／整段固定總價請使用一次付清");
        if(pricing(lease).equals("按月")&&lease.getString("cycle").equals("一次付清"))throw new IllegalArgumentException("一次付清請選按日或整段固定總價");
        if(scheduled(lease))LocalDate.parse(lease.getString("firstDue"));
    }
    public static JSONObject period(JSONObject lease,YearMonth selected)throws JSONException{
        LocalDate start=LocalDate.parse(lease.getString("start")),end=LocalDate.parse(lease.getString("end"));
        if(selected.atEndOfMonth().isBefore(start)||selected.atDay(1).isAfter(end))throw new IllegalArgumentException("月份不在租期內");
        boolean once=lease.optString("cycle").equals("一次付清");int step=months(lease);long index=once?0:ChronoUnit.MONTHS.between(YearMonth.from(start),selected)/step;
        // Monthly groups use calendar months, aligned with the existing per-month ledger.
        YearMonth first=once?YearMonth.from(start):YearMonth.from(start).plusMonths(index*step);
        LocalDate from=first.atDay(1).isBefore(start)?start:first.atDay(1),to=once?end:first.plusMonths(step).atDay(1).minusDays(1);if(to.isAfter(end))to=end;
        LocalDate due=LocalDate.parse(lease.optString("firstDue",start.toString())).plusMonths(index*step);
        long base=Ledger.money(lease,"rent"),amount;
        if(pricing(lease).equals("按日"))amount=Math.multiplyExact(base,ChronoUnit.DAYS.between(start,end)+1);
        else if(pricing(lease).equals("整段固定總價"))amount=base;
        else {BigDecimal total=BigDecimal.ZERO;for(YearMonth m=YearMonth.from(from);!m.isAfter(YearMonth.from(to));m=m.plusMonths(1)){LocalDate a=m.atDay(1).isBefore(from)?from:m.atDay(1),b=m.atEndOfMonth().isAfter(to)?to:m.atEndOfMonth();long days=ChronoUnit.DAYS.between(a,b)+1;total=total.add(BigDecimal.valueOf(base).multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(m.lengthOfMonth()),12,RoundingMode.HALF_UP));}amount=total.setScale(0,RoundingMode.HALF_UP).longValueExact();}
        if(amount<0||amount>1000000000L)throw new IllegalArgumentException("本期租金超出範圍");
        return new JSONObject().put("month",first.toString()).put("from",from.toString()).put("to",to.toString()).put("due",due.toString()).put("amount",amount);
    }
    public static YearMonth rentMonth(JSONObject lease,YearMonth month)throws JSONException{return scheduled(lease)?YearMonth.parse(period(lease,month).getString("month")):month;}
    public static long suggested(JSONObject lease,YearMonth month)throws JSONException{return scheduled(lease)?period(lease,month).getLong("amount"):lease.getLong("rent");}
    public static String label(JSONObject lease){return pricing(lease).equals("按日")?"每日租金":pricing(lease).equals("整段固定總價")?"整段租金":"月租金";}
}
