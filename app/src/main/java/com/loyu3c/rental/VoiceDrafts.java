package com.loyu3c.rental;
import org.json.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;

/** Conservative local suggestions only. No drafts create ledger transactions. */
public final class VoiceDrafts {
    private static final String NUM="[0-9零〇一二兩三四五六七八九十百千萬億]+";
    public static long integer(String value) {
        String s=value.replace("兩","二").replace("〇","零");if(s.matches("[0-9]+")){long n=Long.parseLong(s);if(n>1000000000L)throw new IllegalArgumentException();return n;}
        if(!s.matches("[零一二三四五六七八九十百千萬億]+"))throw new IllegalArgumentException();
        if(!s.matches(".*[十百千萬億].*")){StringBuilder digits=new StringBuilder();for(char c:s.toCharArray())digits.append("零一二三四五六七八九".indexOf(c));long n=Long.parseLong(digits.toString());if(n>1000000000L)throw new IllegalArgumentException();return n;}
        long total=0,section=0,num=0,lastUnit=0;boolean digit=false;
        for(char c:s.toCharArray()){int d="零一二三四五六七八九".indexOf(c);if(d>=0){if(digit&&num!=0)throw new IllegalArgumentException();num=d;digit=true;continue;}
            long unit=c=='十'?10:c=='百'?100:c=='千'?1000:c=='萬'?10000:100000000;
            if(unit<10000){if(lastUnit>0&&unit>=lastUnit)throw new IllegalArgumentException();section=Math.addExact(section,Math.multiplyExact(num==0?1:num,unit));lastUnit=unit;}
            else{section=Math.addExact(section,num);if(section==0)throw new IllegalArgumentException();total=Math.addExact(total,Math.multiplyExact(section,unit));section=0;lastUnit=0;}
            num=0;digit=false;
        }
        // 三千五 / 一萬二 have ambiguous omitted units; leave for manual entry.
        if((num>0&&lastUnit>10&&!s.matches(".*零[一二三四五六七八九]$"))||s.matches(".*[萬億][一二三四五六七八九]$"))throw new IllegalArgumentException();long n=Math.addExact(total,Math.addExact(section,num));if(n>1000000000L)throw new IllegalArgumentException();return n;
    }
    private static boolean has(String text,String... tokens){for(String token:tokens)if(text.contains(token))return true;return false;}
    private static boolean named(String text,String name){return !name.isEmpty()&&Pattern.compile("(?<![0-9A-Za-z])"+Pattern.quote(name)+"(?![0-9A-Za-z])",Pattern.CASE_INSENSITIVE).matcher(text).find();}
    private static List<JSONObject> entities(String text,JSONArray rows,String column) throws JSONException {
        List<JSONObject> ordered=new ArrayList<>(),matched=new ArrayList<>();for(int i=0;i<rows.length();i++)ordered.add(rows.getJSONObject(i));ordered.sort(Comparator.comparingInt((JSONObject r)->r.optString(column).length()).reversed());
        for(JSONObject r:ordered){String name=r.getString(column);boolean shorter=false;for(JSONObject old:matched)if(old.getString(column).contains(name))shorter=true;if(!shorter&&named(text,name))matched.add(r);}return matched;
    }
    static String transactionDate(String text,LocalDate today,JSONArray warnings) {
        List<String> dates=new ArrayList<>();boolean invalid=false,inferred=false;
        String token="[0-9零〇一二兩三四五六七八九十]+";
        Matcher m=Pattern.compile("(?<![0-9])(?:(20[0-9]{2})[-/年])?("+token+")[月]("+token+")(?:日|號)|(?<![0-9])(20[0-9]{2})[-/](\\d{1,2})[-/](\\d{1,2})(?![0-9])").matcher(text);
        while(m.find())try{
            boolean slash=m.group(4)!=null;String year=slash?m.group(4):m.group(1);
            int y=year==null?today.getYear():Integer.parseInt(year);
            int month=(int)integer(slash?m.group(5):m.group(2)),day=(int)integer(slash?m.group(6):m.group(3));
            dates.add(LocalDate.of(y,month,day).toString());if(year==null)inferred=true;
        }catch(Exception e){invalid=true;}
        for(String relative:new String[]{"今天","昨天","前天"})if(text.contains(relative))dates.add(today.minusDays(relative.equals("今天")?0:relative.equals("昨天")?1:2).toString());
        if(invalid){warnings.put("日期無效，請點日曆補選");return "";}
        if(dates.size()>1){warnings.put("說了多個日期，請指定交易日期");return "";}
        if(dates.size()==1){if(inferred)warnings.put("未說年份，日期先使用今年（"+today.getYear()+"年），請確認");return dates.get(0);}
        return "";
    }
    public static JSONObject parse(String raw,LocalDate today,JSONObject catalog) throws JSONException {
        if(raw.trim().isEmpty()||raw.length()>4000)throw new IllegalArgumentException("請輸入4000字以內的記帳內容");String text=raw.trim().replace('，',',').replaceAll("(?<=\\d),(?=\\d)","");
        JSONArray warnings=new JSONArray();String kind="",category="",date="";boolean transfer=has(text,"提款","提領","提取","轉帳","轉入","轉出"),receipt=has(text,"收到租金","收租","租金收入","收到房租")||Pattern.compile("(?:收到|收取).{0,40}(?:租金|房租)").matcher(text).find(),expense=has(text,"修繕","維修","更換","電梯保養","清潔","打掃","房貸","支出","付款","花了");
        int types=(transfer?1:0)+(receipt?1:0)+(expense?1:0);if(types==1)kind=transfer?"轉帳":receipt?"收款":"支出";else warnings.put("請選擇這一筆是支出、收到租金或提款／轉帳");
        boolean blocked=has(text,"押金","退款","退還","退租","手續費");if(blocked)warnings.put("押金／退款／退租請使用專用入口；含手續費請拆成轉帳與支出兩筆");
        if(has(text,"電梯保養"))category="電梯保養";else if(has(text,"清潔","打掃"))category="清潔打掃";else if(has(text,"房貸"))category="房貸扣款";else if(has(text,"修繕","維修","更換"))category="修繕";else if(has(text,"其他費用"))category="其他費用";
        date=transactionDate(text,today,warnings);
        Object amount=JSONObject.NULL;List<Long> amounts=new ArrayList<>();boolean bad=false;Matcher money=Pattern.compile("("+NUM+")\\s*(?:元|塊)").matcher(text);while(money.find())try{amounts.add(integer(money.group(1)));}catch(Exception e){bad=true;}
        Matcher total=Pattern.compile("(?:總共|合計|總額|總金額|共計)\\s*("+NUM+")\\s*(?:元|塊)").matcher(text);List<Long> totals=new ArrayList<>();while(total.find())try{totals.add(integer(total.group(1)));}catch(Exception e){bad=true;}
        if(!bad&&totals.size()==1)amount=totals.get(0);else if(!bad&&amounts.size()==1)amount=amounts.get(0);else warnings.put("金額未明確或有多筆金額，請自行填總金額（建議說金額加元）");
        if(Pattern.compile("(?:負|[-－])\\s*"+NUM+"\\s*(?:元|塊)").matcher(text).find()||Pattern.compile(NUM+"(?:點|\\.)"+NUM).matcher(text).find()){amount=JSONObject.NULL;warnings.put("本版只接受正整數金額，請核對");}
        JSONArray properties=catalog.getJSONArray("properties"),accounts=catalog.getJSONArray("accounts"),leases=catalog.getJSONArray("leases"),units=catalog.getJSONArray("units");
        List<JSONObject> ps=entities(text,properties,"name"),as=entities(text,accounts,"name");JSONArray propertyIds=new JSONArray(),splits=new JSONArray();
        for(JSONObject p:ps){propertyIds.put(p.getString("id"));Matcher part=Pattern.compile(Pattern.quote(p.getString("name"))+"\\s*(?:分攤|修繕|維修)?\\s*("+NUM+")\\s*(?:元|塊)",Pattern.CASE_INSENSITIVE).matcher(text);if(part.find())try{splits.put(new JSONObject().put("propertyId",p.getString("id")).put("amount",integer(part.group(1))));}catch(Exception ignored){}}
        String accountId="",fromId="",toId="";if(!kind.equals("轉帳")&&as.size()==1)accountId=as.get(0).getString("id");
        if(kind.equals("轉帳")){for(JSONObject a:as){String name=a.getString("name");if(Pattern.compile("(?:從|由|轉出)\\s*"+Pattern.quote(name),Pattern.CASE_INSENSITIVE).matcher(text).find())fromId=fromId.isEmpty()?a.getString("id"):"?";if(Pattern.compile("(?:到|至|轉入)\\s*"+Pattern.quote(name),Pattern.CASE_INSENSITIVE).matcher(text).find())toId=toId.isEmpty()?a.getString("id"):"?";}if(fromId.equals("?"))fromId="";if(toId.equals("?"))toId="";}
        List<JSONObject> candidates=new ArrayList<>();for(int i=0;i<leases.length();i++){JSONObject l=leases.getJSONObject(i);if(l.has("terminatedOn"))continue;JSONObject unit=null;for(int j=0;j<units.length();j++)if(units.getJSONObject(j).getString("id").equals(l.getString("unitId")))unit=units.getJSONObject(j);if(unit==null)continue;
            boolean room=named(text,unit.getString("code")),person=named(text,l.getString("tenant"));if(!room&&!person)continue;if(!ps.isEmpty()){boolean property=false;for(JSONObject p:ps)if(p.getString("id").equals(unit.getString("propertyId")))property=true;if(!property)continue;}
            LocalDate check=date.isEmpty()?today:LocalDate.parse(date);if(check.isBefore(LocalDate.parse(l.getString("start")))||check.isAfter(Settlements.effectiveEnd(l)))continue;candidates.add(l);}
        JSONArray categoryCatalog=ExpenseCategories.catalog(catalog);List<String> categoryMatches=new ArrayList<>();for(int i=0;i<categoryCatalog.length();i++){JSONObject c=categoryCatalog.getJSONObject(i);if(c.getBoolean("active")&&text.contains(c.getString("name")))categoryMatches.add(c.getString("id"));}
        if(categoryMatches.size()==1){category=categoryMatches.get(0);if(kind.isEmpty())kind="支出";}else if(categoryMatches.size()>1){category="";warnings.put("辨識到多個支出類別，請自行選擇");}
        boolean enabledCategory=false;for(int i=0;i<categoryCatalog.length();i++)if(categoryCatalog.getJSONObject(i).getString("id").equals(category)&&categoryCatalog.getJSONObject(i).getBoolean("active"))enabledCategory=true;if(!enabledCategory)category="";
        if(date.isEmpty())warnings.put("日期待補選");if(kind.equals("支出")&&category.isEmpty())warnings.put("支出分類待選");if(kind.equals("收款")&&candidates.size()!=1)warnings.put("租約未唯一辨識，請選房號與租客");if(kind.equals("轉帳")&&(fromId.isEmpty()||toId.isEmpty()))warnings.put("轉出／轉入帳戶需分別確認");else if(!kind.equals("轉帳")&&accountId.isEmpty())warnings.put("實際付款／收款帳戶待選");
        if(kind.equals("支出")&&ps.size()>1&&splits.length()!=ps.size())warnings.put("多棟分攤未明確，請逐棟填金額，不自動平均");
        return new JSONObject().put("raw",raw.trim()).put("kind",kind).put("category",category).put("date",date).put("amount",amount).put("accountId",accountId).put("fromId",fromId).put("toId",toId).put("propertyIds",propertyIds).put("splits",splits).put("leaseId",candidates.size()==1?candidates.get(0).getString("id"):"").put("warnings",warnings).put("blocked",blocked);
    }
}
