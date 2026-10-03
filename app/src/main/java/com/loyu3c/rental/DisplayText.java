package com.loyu3c.rental;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.regex.*;

/** Format currency for display only; never change stored numbers, dates or unit codes. */
public final class DisplayText {
    private static final Pattern MONEY=Pattern.compile("\\$\\s*(-?\\d+)(?![\\d,])");
    public static String number(long value) {return NumberFormat.getIntegerInstance(Locale.US).format(value);}
    public static String money(String text) {
        Matcher matcher=MONEY.matcher(text);StringBuffer out=new StringBuffer();
        while(matcher.find()) {
            String replacement=matcher.group();
            try {replacement="$"+number(Long.parseLong(matcher.group(1)));}catch(NumberFormatException ignored){}
            matcher.appendReplacement(out,Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);return out.toString();
    }
}
