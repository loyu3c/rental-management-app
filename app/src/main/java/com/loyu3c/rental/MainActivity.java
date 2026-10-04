package com.loyu3c.rental;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import java.io.*;
import java.nio.charset.StandardCharsets;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.app.AlertDialog;
import org.json.*;
import java.util.UUID;
import java.util.Locale;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import android.text.TextWatcher;
import android.text.Editable;
import android.text.InputType;
import android.util.Patterns;

public class MainActivity extends Activity {
    private LinearLayout body;
    private final Map<AlertDialog,Runnable> livePanels=new LinkedHashMap<>();
    private String display(String value) { return DisplayText.money(value); }
    private String fmt(long value) { return DisplayText.number(value); }
    private void refreshPanels() {
        for(Map.Entry<AlertDialog,Runnable> panel:new ArrayList<>(livePanels.entrySet()))if(panel.getKey().isShowing())panel.getValue().run();
    }
    private boolean googleSyncRunning=false;
    private String pendingBackup;
    private String propertyId="", unitId="";
    private static final String[] KEYS={"properties","units","leases","invoices","receipts","accounts","expenses","transfers","reconciliations","deposits","settlements","meterReadings"};
    private LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Taipei")); }
    private static final int EXPORT = 41, IMPORT = 42;
    private final String[] tabs = {"首頁", "收租", "記帳", "不動產", "報表"};
    private int selected = 0;
    private String rentSearch="";private int rentFilter=0;
    private String expenseSearch="",expenseMonth="";private int expenseFilter=0,expenseLimit=15;private boolean expenseFiltersOpen=false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) { selected = state.getInt("tab", 0); propertyId=state.getString("property", ""); unitId=state.getString("unit", ""); }
        render();
    }
    @Override protected void onDestroy() {
        for(AlertDialog dialog:new ArrayList<>(livePanels.keySet()))dialog.dismiss();livePanels.clear();super.onDestroy();
    }
    @Override public void onSaveInstanceState(Bundle out) {
        out.putInt("tab", selected); out.putString("property",propertyId); out.putString("unit",unitId);
        super.onSaveInstanceState(out);
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this) { @Override public void setText(CharSequence value,BufferType type) { super.setText(value==null?null:display(value.toString()),type); } };
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(6), 0, dp(6)); return view;
    }
    private LinearLayout card(String heading, String detail) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18), dp(12), dp(18), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(255, 252, 247)); background.setCornerRadius(dp(16)); box.setBackground(background);
        TextView title = text(heading, 18, Color.rgb(77, 57, 39)); title.setTypeface(null, Typeface.BOLD);
        box.addView(title); box.addView(text(detail, 15, Color.rgb(115, 102, 87)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(8), 0, dp(8)); body.addView(box, params); return box;
    }
    private void render() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(248, 244, 237)); root.setPadding(dp(16), dp(16), dp(16), dp(8));
        TextView heading = text("暖色帳本 / " + tabs[selected], 26, Color.rgb(83, 60, 40));
        heading.setTypeface(null, Typeface.BOLD); root.addView(heading);
        root.addView(text("整合測試版 · 資料存在手機；"+(getSharedPreferences("googleSync",MODE_PRIVATE).contains("connection")?"Google已設定（手動同步）":"Google同步未設定"), 13, Color.rgb(137, 83, 15)));
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (selected == 0) {
            try {
                JSONArray invoices=data("invoices"),receipts=data("receipts"); long billed=0,received=0,unpaid=0,overdue=0;
                String month=YearMonth.from(today()).toString();
                for(int i=0;i<invoices.length();i++) { JSONObject r=invoices.getJSONObject(i);long debt=Ledger.total(r)-Ledger.paid(r.getString("id"),receipts);
                    unpaid+=debt;if(LocalDate.parse(r.getString("due")).isBefore(today())) overdue+=debt;
                    if(month.equals(r.getString("month"))) billed+=Ledger.total(r);
                }
                for(int i=0;i<receipts.length();i++) if(receipts.getJSONObject(i).optBoolean("cash",true)&&receipts.getJSONObject(i).getString("date").startsWith(month)) received+=Ledger.money(receipts.getJSONObject(i),"amount");
                long settlementReceived=0;JSONArray settlements=data("settlements");for(int i=0;i<settlements.length();i++){JSONArray payments=settlements.getJSONObject(i).getJSONArray("payments");for(int j=0;j<payments.length();j++)if(payments.getJSONObject(j).getString("date").startsWith(month))settlementReceived+=payments.getJSONObject(j).getLong("amount");}
                card("本月退租結算補繳","實際收到 $"+settlementReceived+"；押金抵付不算銀行匯款。");
                card(month+" 收租", "本月已開帳單 $"+billed+"\n本月實際收款 $"+received+"\n所有帳單未付 $"+unpaid+"（含未到期）\n已逾期 $"+overdue);
            } catch(Exception e) { card("收租資料異常", "請先匯出備份並停止收款登錄。"); }
            try {
                YearMonth ym=YearMonth.from(today());card("本月已登錄支出", "$"+Bookkeeping.expenseTotal(data("expenses"),ym.atDay(1).toString(),ym.atEndOfMonth().toString(),null)+"（含房貸總扣款；不含提款轉帳）");
                card("銀行與共用零用金",data("accounts").length()+" 個已設定帳戶；未對應的舊收款 "+unlinkedReceipts()+" 筆。\n請到記帳查看各帳戶餘額、流水與對帳。押金另行追蹤，不是可用租金收入。");
            }catch(Exception e){card("資金資料異常","請先匯出備份，暫停記帳。");}
            card("不動產建檔", "已建立 " + data("properties").length() + " 筆不動產、" + data("units").length() + " 個出租單位。\n請從不動產入口新增。");
        } else if (selected == 1) {
            rentScreen();
        } else if (selected == 2) {
            accountingScreen();
        } else if (selected == 3) {
            propertyScreen();
        } else {
            action("爸爸的每月總報表／CSV匯出",this::ownerReport);
            card("租客雲端報表：尚未啟用", "Google連線與每位租客的獨立查看權限仍待設定。");
            action("本地支出與各棟分攤報表",this::expenseReport);
            action("押金總覽",this::depositOverview);
            action("退租結算與待收／待退",this::settlementOverview);
            action("帳戶流水與存摺對帳",this::accountsDialog);
            card("歷史補登", "起算日前歷史交易列入報表，不重複改變期初餘額。\n帳務未齊，餘額需核對；押金另行追蹤，不是可用租金收入。");
            action("匯出完整備份", this::exportBackup);
            action("從檔案還原資料", this::importBackup);
            action("Google同步設定／狀態",this::googleSyncSettings);
        }
        LinearLayout nav = new LinearLayout(this); nav.setGravity(Gravity.CENTER);
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            int[] icons={R.drawable.nav_home,R.drawable.nav_money,R.drawable.nav_ledger,R.drawable.nav_property,R.drawable.nav_report};
            LinearLayout button=new LinearLayout(this);button.setOrientation(LinearLayout.VERTICAL);button.setGravity(Gravity.CENTER);button.setPadding(dp(2),dp(8),dp(2),dp(8));button.setMinimumHeight(dp(64));
            ImageView icon=new ImageView(this);icon.setImageResource(icons[i]);icon.setColorFilter(i==selected?Color.rgb(104,70,39):Color.rgb(115,112,106));icon.setScaleType(ImageView.ScaleType.FIT_CENTER);button.addView(icon,new LinearLayout.LayoutParams(dp(22),dp(22)));
            TextView label=text(tabs[i],12,i==selected?Color.rgb(104,70,39):Color.DKGRAY);label.setGravity(Gravity.CENTER);label.setPadding(0,dp(4),0,0);label.setIncludeFontPadding(false);button.addView(label,new LinearLayout.LayoutParams(-1,-2));
            GradientDrawable navBg=new GradientDrawable();navBg.setColor(i==selected?Color.rgb(232,213,187):Color.rgb(248,244,237));navBg.setCornerRadius(dp(12));button.setBackground(navBg);button.setClickable(true);button.setFocusable(true);button.setContentDescription(tabs[i]);button.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES);icon.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);label.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            button.setOnClickListener(v -> { selected = index; render(); });
            nav.addView(button,new LinearLayout.LayoutParams(0,-2,1));
        }
        root.addView(nav); setContentView(root);
    }

    private JSONArray data(String key) {
        try { return new JSONArray(getSharedPreferences("catalog", MODE_PRIVATE).getString(key, "[]")); }
        catch (JSONException e) {
            new AlertDialog.Builder(this).setMessage(display(String.valueOf("建檔資料無法讀取，請勿繼續新增。"))).setPositiveButton("知道了", null).show();
            throw new IllegalStateException("Invalid saved catalog", e);
        }
    }
    private boolean save(String key, JSONArray values) {
        if(data("settlements").length()>0)try{validateBackup(fullBackup().put(key,values));}catch(Exception e){error(e);return false;}
        boolean ok = getSharedPreferences("catalog", MODE_PRIVATE).edit().putString(key, values.toString()).commit();
        if (!ok) Toast.makeText(this, "保存失敗，請重試", Toast.LENGTH_LONG).show();
        if(ok)body.post(this::refreshPanels);
        return ok;
    }
    private void action(String label, Runnable run) {
        Button b = new Button(this); b.setText(display(label)); b.setAllCaps(false);
        b.setTextColor(Color.rgb(90, 61, 37));
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(238,226,211));bg.setCornerRadius(dp(12));b.setBackground(bg);b.setMinHeight(dp(48));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,dp(5),0,dp(5));
        b.setOnClickListener(v -> run.run()); body.addView(b,params);
    }
    private void shortcut(LinearLayout row,String label,int iconId,Runnable run) {
        LinearLayout button=new LinearLayout(this);button.setOrientation(LinearLayout.VERTICAL);button.setGravity(Gravity.CENTER);button.setPadding(dp(4),dp(12),dp(4),dp(12));button.setMinimumHeight(dp(92));
        ImageView icon=new ImageView(this);icon.setImageResource(iconId);icon.setColorFilter(Color.rgb(104,70,39));icon.setScaleType(ImageView.ScaleType.FIT_CENTER);button.addView(icon,new LinearLayout.LayoutParams(dp(26),dp(26)));
        TextView caption=text(label,13,Color.rgb(90,61,37));caption.setGravity(Gravity.CENTER);caption.setIncludeFontPadding(false);caption.setPadding(0,dp(8),0,0);button.addView(caption,new LinearLayout.LayoutParams(-1,-2));
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(238,226,211));bg.setCornerRadius(dp(12));button.setBackground(bg);button.setClickable(true);button.setFocusable(true);button.setContentDescription(label);button.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES);icon.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);caption.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);button.setOnClickListener(v->run.run());
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-1,1);params.setMargins(dp(3),dp(6),dp(3),dp(6));row.addView(button,params);
    }
    private void actionGroup(String title,String[] labels,Runnable[] runs) {
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(255,252,247));bg.setCornerRadius(dp(12));box.setBackground(bg);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,dp(5),0,dp(5));body.addView(box,params);
        Button toggle=new Button(this);toggle.setAllCaps(false);toggle.setText(title+"  ▾");toggle.setTextColor(Color.rgb(90,61,37));toggle.setBackgroundColor(Color.TRANSPARENT);toggle.setMinHeight(dp(52));box.addView(toggle);
        LinearLayout items=new LinearLayout(this);items.setOrientation(LinearLayout.VERTICAL);items.setVisibility(android.view.View.GONE);box.addView(items);
        for(int i=0;i<labels.length;i++){final Runnable run=runs[i];Button item=new Button(this);item.setAllCaps(false);item.setText(labels[i]);item.setTextSize(14);item.setTextColor(Color.rgb(90,61,37));item.setBackgroundColor(Color.TRANSPARENT);item.setMinHeight(dp(48));item.setOnClickListener(v->run.run());items.addView(item,new LinearLayout.LayoutParams(-1,-2));}
        toggle.setOnClickListener(v->{boolean open=items.getVisibility()!=android.view.View.VISIBLE;items.setVisibility(open?android.view.View.VISIBLE:android.view.View.GONE);toggle.setText(title+(open?"  ▴":"  ▾"));toggle.setContentDescription(title+(open?"，已展開":"，已收合"));});toggle.setContentDescription(title+"，已收合");
    }
    private void cardAction(LinearLayout box,String label,Runnable run) {
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.END);box.addView(row,new LinearLayout.LayoutParams(-1,-2));
        Button button=new Button(this);button.setText(label);button.setAllCaps(false);button.setTextSize(14);button.setTextColor(Color.rgb(125,81,48));button.setMinHeight(dp(48));button.setMinimumWidth(dp(80));
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.TRANSPARENT);bg.setStroke(dp(1),Color.rgb(183,146,112));bg.setCornerRadius(dp(10));button.setBackground(bg);button.setPadding(dp(16),0,dp(16),0);button.setOnClickListener(v->run.run());button.setContentDescription(label+"此筆紀錄");
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,dp(48));params.setMargins(0,dp(8),0,0);row.addView(button,params);
    }
    private LinearLayout inputRow(LinearLayout layout,String label) {
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=text(label,14,Color.rgb(90,61,37));name.setPadding(0,dp(8),dp(8),dp(8));
        row.addView(name,new LinearLayout.LayoutParams(dp(88),-2));layout.addView(row,new LinearLayout.LayoutParams(-1,-2));return row;
    }
    private String fieldLabel(String hint) {
        if(hint.contains("收款帳戶"))return "收款帳戶";
        if(hint.startsWith("實際收到押金"))return "押金金額";
        if(hint.startsWith("搜尋"))return "搜尋";
        if(hint.contains("Gmail"))return "Gmail";
        if(hint.startsWith("月租金")||hint.equals("本月租金"))return "月租金";
        if(hint.contains("應繳日"))return "應繳日";
        if(hint.startsWith("約定押金"))return "約定押金";
        if(hint.contains("月水費"))return "月水費";
        if(hint.startsWith("每度電費"))return "每度電價";
        if(hint.startsWith("上期／起始電表"))return "上期電表";
        if(hint.startsWith("本期電表"))return "本期電表";
        if(hint.startsWith("查看／對帳日期"))return "對帳日期";
        if(hint.startsWith("實際收到金額"))return "收到金額";
        if(hint.startsWith("備註／"))return "備註";
        if(hint.startsWith("起算日開始前"))return "期初餘額";
        if(hint.startsWith("項目／"))return "支出項目";
        if(hint.startsWith("修正起算日開始前"))return "修正餘額";
        if(hint.startsWith("存摺當日結束"))return "實際餘額";
        String shortName=hint.split("[，,（(：:]")[0];return shortName.length()>7?shortName.substring(0,7):shortName;
    }
    private EditText field(LinearLayout layout, String hint) {
        LinearLayout row=inputRow(layout,fieldLabel(hint));EditText field=new EditText(this);
        field.setHint(hint);field.setSingleLine(true);field.setTextSize(16);row.addView(field,new LinearLayout.LayoutParams(0,-2,1));return field;
    }
    private EditText dateField(LinearLayout layout,String label,boolean monthOnly) {
        EditText input=field(layout,label);input.setText(monthOnly?YearMonth.from(today()).toString():today().toString());
        input.setInputType(InputType.TYPE_NULL);input.setKeyListener(null);input.setCursorVisible(false);
        input.setShowSoftInputOnFocus(false);input.setLongClickable(false);input.setFocusable(true);input.setFocusableInTouchMode(false);
        input.setOnClickListener(v->{
            if(!input.isEnabled())return;
            if(monthOnly) {
                YearMonth chosen;try{chosen=YearMonth.parse(input.getText().toString());}catch(Exception e){chosen=YearMonth.from(today());}
                LinearLayout pickers=form();pickers.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout years=new LinearLayout(this),months=new LinearLayout(this);years.setOrientation(LinearLayout.VERTICAL);months.setOrientation(LinearLayout.VERTICAL);
                years.addView(text("年",16,Color.DKGRAY));months.addView(text("月",16,Color.DKGRAY));
                NumberPicker year=new NumberPicker(this),month=new NumberPicker(this);
                year.setMinValue(1900);year.setMaxValue(2200);year.setValue(Math.max(1900,Math.min(2200,chosen.getYear())));year.setWrapSelectorWheel(false);
                month.setMinValue(1);month.setMaxValue(12);month.setValue(chosen.getMonthValue());
                year.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);month.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                years.addView(year);months.addView(month);pickers.addView(years,new LinearLayout.LayoutParams(0,-2,1));pickers.addView(months,new LinearLayout.LayoutParams(0,-2,1));
                new AlertDialog.Builder(this).setTitle("選擇"+label).setView(pickers).setNegativeButton("取消",null).setPositiveButton("選定",(d,w)->input.setText(YearMonth.of(year.getValue(),month.getValue()).toString())).show();
            } else {
                LocalDate chosen;try{chosen=LocalDate.parse(input.getText().toString());}catch(Exception e){chosen=today();}
                android.app.DatePickerDialog dialog=new android.app.DatePickerDialog(this,(picker,year,month,day)->input.setText(LocalDate.of(year,month+1,day).toString()),chosen.getYear(),chosen.getMonthValue()-1,chosen.getDayOfMonth());
                dialog.setTitle("選擇"+label);dialog.show();
            }
        });return input;
    }

    private Spinner choice(LinearLayout layout,String[] names) {
        String first=names.length==0?"":names[0];String label=first.equals("月繳")?"繳款週期":first.equals("租客自行繳水電")?"水電方式":first.equals("銀行")?"帳戶類型":first.equals("建築物")?"不動產類型":first.equals("房間")?"單位類型":first.startsWith("代號由")?"排序":first.equals("修繕")?"支出類別":first.startsWith("全部")?"狀態篩選":"選項";
        return choice(layout,label,names);
    }
    private Spinner choice(LinearLayout layout,String label,String[] names) {
        LinearLayout row=inputRow(layout,label);Spinner spinner=new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));spinner.setTag(label);row.addView(spinner,new LinearLayout.LayoutParams(0,dp(52),1));return spinner;
    }
    private LinearLayout form() {
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(8), dp(20), dp(8)); return layout;
    }
    @Override public void onBackPressed() {
        if(selected==3&&!unitId.isEmpty()) { unitId="";render(); }
        else if(selected==3&&!propertyId.isEmpty()) { propertyId="";render(); }
        else if(selected!=0) { selected=0;render(); } else super.onBackPressed();
    }
    private JSONObject find(String key,String id) {
        JSONArray rows=data(key);for(int i=0;i<rows.length();i++) if(rows.optJSONObject(i).optString("id").equals(id)) return rows.optJSONObject(i);
        return null;
    }
    private void openUnit(JSONObject unit) { selected=3;propertyId=unit.optString("propertyId");unitId=unit.optString("id");render(); }
    private void tapCard(String title,String detail,Runnable run) {
        LinearLayout row=card(title+"  ›",detail);row.setOnClickListener(v->run.run());row.setContentDescription(title+"，點擊查看");
    }
    private int natural(String a,String b) {
        int i=0,j=0;
        while(i<a.length()&&j<b.length()) {
            char x=a.charAt(i),y=b.charAt(j);
            if(x>='0'&&x<='9'&&y>='0'&&y<='9') {
                int ai=i,bj=j;while(i<a.length()&&a.charAt(i)>='0'&&a.charAt(i)<='9')i++;while(j<b.length()&&b.charAt(j)>='0'&&b.charAt(j)<='9')j++;
                int n=new java.math.BigInteger(a.substring(ai,i)).compareTo(new java.math.BigInteger(b.substring(bj,j)));if(n!=0)return n;
            } else {int n=Character.compare(Character.toLowerCase(x),Character.toLowerCase(y));if(n!=0)return n;i++;j++;}
        }
        return Integer.compare(a.length(),b.length());
    }
    private String status(JSONObject unit) {
        boolean future=false,history=false;JSONArray leases=data("leases"),units=data("units");
        for(int i=0;i<leases.length();i++) {
            JSONObject l=leases.optJSONObject(i),other=null;
            for(int j=0;j<units.length();j++) if(units.optJSONObject(j).optString("id").equals(l.optString("unitId")))other=units.optJSONObject(j);
            boolean whole=other!=null&&other.optString("propertyId").equals(unit.optString("propertyId"))&&other.optString("type").equals("整棟");
            if(!l.optString("unitId").equals(unit.optString("id"))&&!whole)continue;
            LocalDate from=LocalDate.parse(l.optString("start")),to=LocalDate.parse(l.optString("terminatedOn",l.optString("end")));
            if(!today().isBefore(from)&&!today().isAfter(to))return whole&&!other.optString("id").equals(unit.optString("id"))?"出租中（整棟租約）":"出租中";
            if(from.isAfter(today()))future=true;else history=true;
        }
        return future?"待起租":history?"租約已到期":"未建租約";
    }
    private String currentTenant(JSONObject unit) {
        JSONArray leases=data("leases"),units=data("units");
        for(int i=0;i<leases.length();i++) {
            JSONObject lease=leases.optJSONObject(i),leasedUnit=null;
            for(int j=0;j<units.length();j++)if(units.optJSONObject(j).optString("id").equals(lease.optString("unitId")))leasedUnit=units.optJSONObject(j);
            boolean same=unit.optString("id").equals(lease.optString("unitId"));
            boolean whole=leasedUnit!=null&&leasedUnit.optString("propertyId").equals(unit.optString("propertyId"))&&leasedUnit.optString("type").equals("整棟");
            if(!same&&!whole)continue;
            LocalDate start=LocalDate.parse(lease.optString("start")),end=LocalDate.parse(lease.optString("terminatedOn",lease.optString("end")));
            if(!today().isBefore(start)&&!today().isAfter(end))return lease.optString("tenant");
        }
        return "";
    }
    private String tenantLabel(JSONObject unit) {String tenant=currentTenant(unit);return tenant.isEmpty()?"目前無租客":"目前租客："+tenant;}
    private void propertyScreen() {
        JSONObject property=find("properties",propertyId),unit=find("units",unitId);
        if(property==null) {propertyId="";unitId="";action("＋ 新增不動產",this::addProperty);
            JSONArray properties=data("properties"),units=data("units");
            if(properties.length()==0)card("尚未建檔","先新增建築物或土地，再建立房間、車格或土地出租範圍。");
            for(int i=0;i<properties.length();i++) {JSONObject p=properties.optJSONObject(i);int count=0;for(int j=0;j<units.length();j++)if(p.optString("id").equals(units.optJSONObject(j).optString("propertyId")))count++;
                tapCard(p.optString("name"),p.optString("type")+" · "+count+" 個出租單位",()->{propertyId=p.optString("id");render();});}
            return;
        }
        action("‹ 所有不動產",()->{propertyId="";unitId="";render();});
        if(unit!=null&&unit.optString("propertyId").equals(propertyId)) { unitScreen(property,unit);return; }
        unitId="";card(property.optString("name"),property.optString("type"));
        action("修改不動產名稱",()->editName("properties",property,"name","不動產名稱"));
        action("＋ 新增出租單位",()->addUnit(propertyId,property.optString("name")));
        EditText search=field(body,"搜尋代號或租客姓名");
        Spinner filter=choice(body,new String[]{"全部","出租中","待起租","租約已到期","未建租約"});
        Spinner sort=choice(body,new String[]{"代號由小到大","代號由大到小"});
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);body.addView(list);
        Runnable refresh=()->{
            list.removeAllViews();List<JSONObject> rows=new ArrayList<>();JSONArray units=data("units");
            for(int i=0;i<units.length();i++) {JSONObject u=units.optJSONObject(i);String st=status(u);
                if(!propertyId.equals(u.optString("propertyId"))||!(u.optString("code")+" "+currentTenant(u)).toLowerCase(Locale.ROOT).contains(search.getText().toString().trim().toLowerCase(Locale.ROOT)))continue;
                if(filter.getSelectedItemPosition()>0&&!st.startsWith(filter.getSelectedItem().toString()))continue;rows.add(u);}
            rows.sort((x,y)->natural(x.optString("code"),y.optString("code"))*(sort.getSelectedItemPosition()==0?1:-1));
            LinearLayout previous=body;body=list;
            for(JSONObject u:rows)tapCard(u.optString("code"),u.optString("type")+" · "+status(u)+"\n"+tenantLabel(u),()->openUnit(u));
            if(rows.isEmpty())card("沒有符合的單位","可調整搜尋或篩選條件。");body=previous;
        };
        search.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){refresh.run();}public void afterTextChanged(Editable e){} });
        AdapterView.OnItemSelectedListener listener=new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,android.view.View v,int n,long id){refresh.run();}public void onNothingSelected(AdapterView<?> p){}};
        filter.setOnItemSelectedListener(listener);sort.setOnItemSelectedListener(listener);refresh.run();
    }
    private void unitScreen(JSONObject property,JSONObject unit) {
        action("‹ "+property.optString("name")+" 的出租單位",()->{unitId="";render();});
        card(unit.optString("code"),unit.optString("type")+" · "+status(unit)+"\n"+tenantLabel(unit));
        action("修改代號",()->editName("units",unit,"code","出租代號"));
        action("＋ 新增租約／換新租客",()->addLease(unit));
        JSONArray leases=data("leases");List<JSONObject> rows=new ArrayList<>();
        for(int i=0;i<leases.length();i++)if(unit.optString("id").equals(leases.optJSONObject(i).optString("unitId")))rows.add(leases.optJSONObject(i));
        rows.sort((x,y)->y.optString("start").compareTo(x.optString("start")));
        for(JSONObject l:rows) {
            card(l.optString("tenant"),l.optString("start")+" ～ "+l.optString("end")+(l.has("terminatedOn")?"\n已退租："+l.optString("terminatedOn"):"")+"\n月租 $"+l.optLong("rent")+" · "+l.optString("cycle")+"\n約定押金 $"+l.optLong("deposit")+"（約定金額；實收看押金明細）\n"+l.optString("email")+"\n"+l.optString("utilities")+(l.optString("utilities").equals("管理者代收水電")?(l.isNull("water")?" · 水費待設定":" · 月水費 $"+l.optLong("water")):""));
            if(!l.has("terminatedOn"))action("修改 "+l.optString("tenant")+" 的租約",()->leaseForm(unit,l));
            action("查看帳單與收款",()->leaseLedger(l));
        }
    }
    private void addProperty() {
        LinearLayout layout = form();
        EditText name = field(layout, "不動產名稱，例如 A 棟、停車場土地");
        Spinner type = choice(layout, new String[]{"建築物", "土地"});
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("新增不動產").setView(layout)
            .setNegativeButton("取消", null).setPositiveButton("儲存", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w -> {
            String value = name.getText().toString().trim();
            if (value.isEmpty()) { name.setError("請填名稱"); return; }
            JSONArray items = data("properties");
            for (int i = 0; i < items.length(); i++) if (items.optJSONObject(i).optString("name").equalsIgnoreCase(value)) {
                name.setError("此名稱已存在"); return;
            }
            try {
                JSONObject item = new JSONObject(); item.put("id", UUID.randomUUID().toString());
                item.put("name", value); item.put("type", type.getSelectedItem().toString()); items.put(item);
                if (save("properties", items)) { dialog.dismiss(); render(); }
            } catch (JSONException e) { Toast.makeText(this, "資料格式錯誤", Toast.LENGTH_LONG).show(); }
        }));
        dialog.show();
    }
    private void addUnit(String propertyId, String propertyName) {
        LinearLayout layout = form();
        EditText code = field(layout, "代號，例如 C1、R1401");
        Spinner type = choice(layout, new String[]{"房間", "整棟", "車格", "土地出租範圍", "其他空間"});
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(propertyName + "：出租單位").setView(layout)
            .setNegativeButton("取消", null).setPositiveButton("儲存", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w -> {
            String value = code.getText().toString().trim();
            if (value.isEmpty()) { code.setError("請填代號"); return; }
            JSONArray items = data("units");
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item.optString("propertyId").equals(propertyId) && item.optString("code").equalsIgnoreCase(value)) {
                    code.setError("同一不動產內代號不能重複"); return;
                }
            }
            try {
                JSONObject item = new JSONObject(); item.put("id", UUID.randomUUID().toString());
                item.put("propertyId", propertyId); item.put("code", value); item.put("type", type.getSelectedItem().toString());
                items.put(item); if (save("units", items)) { dialog.dismiss(); render(); }
            } catch (JSONException e) { Toast.makeText(this, "資料格式錯誤", Toast.LENGTH_LONG).show(); }
        }));
        dialog.show();
    }


    private void editName(String key, JSONObject original, String column, String title) {
        LinearLayout layout = form(); EditText value = field(layout, title);
        value.setText(original.optString(column));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("修改" + title).setView(layout)
            .setNegativeButton("取消", null).setPositiveButton("儲存", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(w -> {
            String next = value.getText().toString().trim();
            if (next.isEmpty()) { value.setError("不可空白"); return; }
            JSONArray items = data(key);
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                boolean scope = key.equals("properties") || item.optString("propertyId").equals(original.optString("propertyId"));
                if (!item.optString("id").equals(original.optString("id")) && scope && item.optString(column).equalsIgnoreCase(next)) {
                    value.setError("名稱或代號已存在"); return;
                }
            }
            try {
                for (int i = 0; i < items.length(); i++) if (items.optJSONObject(i).optString("id").equals(original.optString("id")))
                    items.optJSONObject(i).put(column, next);
                if (save(key, items)) { dialog.dismiss(); render(); }
            } catch (JSONException e) { value.setError("保存格式錯誤"); }
        })); dialog.show();
    }
    private EditText number(LinearLayout layout, String hint) {
        EditText input = field(layout, hint); input.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,"));input.setRawInputType(InputType.TYPE_CLASS_NUMBER);
        boolean[] formatting={false};
        input.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int c,int d){} public void onTextChanged(CharSequence s,int a,int b,int c){}
            public void afterTextChanged(Editable value){
                if(formatting[0])return;String raw=value.toString().replace(",", "");if(raw.isEmpty())return;
                try {
                    String grouped=fmt(Long.parseLong(raw));if(grouped.equals(value.toString()))return;
                    int cursor=Math.max(0,input.getSelectionStart()),digits=0;for(int i=0;i<Math.min(cursor,value.length());i++)if(Character.isDigit(value.charAt(i)))digits++;
                    formatting[0]=true;input.setText(grouped);int position=0,seen=0;while(position<grouped.length()&&seen<digits){if(Character.isDigit(grouped.charAt(position)))seen++;position++;}input.setSelection(position);
                }catch(NumberFormatException ignored){}finally{formatting[0]=false;}
            }
        });return input;
    }
    private IllegalArgumentException fieldError(EditText input,String message) {
        input.setError(message);input.requestFocus();return new IllegalArgumentException(message);
    }
    private String inputName(EditText input) {
        String hint=String.valueOf(input.getHint());return hint.split("[，,（(：:]")[0];
    }
    private String requiredText(EditText input) {
        String value=input.getText().toString().trim();if(value.isEmpty())throw fieldError(input,"請填寫"+inputName(input));input.setError(null);return value;
    }
    private String dateValue(EditText input) {
        String value=input.getText().toString().trim();String name=inputName(input);
        if(value.isEmpty())throw fieldError(input,"請選擇"+name);
        try {if(name.contains("月份"))YearMonth.parse(value);else LocalDate.parse(value);}
        catch(java.time.format.DateTimeParseException e){throw fieldError(input,name+"格式不正確，請點選日曆重新選擇");}
        input.setError(null);return value;
    }
    private long amount(EditText field, boolean optional) {
        String value=field.getText().toString().trim().replace(",", "");String name=inputName(field);
        if(value.isEmpty()){if(optional){field.setError(null);return -1;}throw fieldError(field,"請填寫"+name);}
        long result;try{result=Long.parseLong(value);}catch(NumberFormatException e){throw fieldError(field,name+"請填寫整數，不可包含文字或小數");}
        if(result<0||result>1000000000L)throw fieldError(field,name+"須介於0與1,000,000,000之間");
        field.setError(null);return result;
    }
    private void addLease(JSONObject unit) { leaseForm(unit,null); }
    private void leaseForm(JSONObject unit, JSONObject original) {
        if(original!=null&&original.has("terminatedOn")){error(new IllegalArgumentException("已退租租約保留歷史，請新增下一位租客的租約"));return;}
        LinearLayout layout = form();
        EditText tenant = field(layout, "租客姓名"), email = field(layout, "查看報表用 Gmail");
        EditText start = dateField(layout, "起租日",false), end = dateField(layout, "到期日",false);start.setText("");end.setText("");
        EditText rent = number(layout, "月租金（年繳也填每月金額）"), day = number(layout, "每月應繳日 1–31");
        Spinner cycle = choice(layout, new String[]{"月繳", "年繳"});
        EditText deposit = number(layout, "約定押金，無押金填0");
        Spinner utilities = choice(layout, new String[]{"租客自行繳水電", "管理者代收水電"});
        EditText water = number(layout, "代收固定月水費，可留空待設定");
        EditText rate = number(layout, "每度電費（只套用新開水電帳單）");rate.setText("5");
        if(original!=null) {
            tenant.setText(original.optString("tenant"));email.setText(original.optString("email"));start.setText(original.optString("start"));end.setText(original.optString("end"));
            rent.setText(original.optString("rent"));day.setText(original.optString("dueDay"));deposit.setText(original.optString("deposit"));
            cycle.setSelection(original.optString("cycle").equals("年繳")?1:0);utilities.setSelection(original.optString("utilities").equals("管理者代收水電")?1:0);
            if(!original.isNull("water"))water.setText(original.optString("water"));rate.setText(original.optString("electricRate","5"));
            if(hasBilling(original.optString("id"))) {
                layout.addView(text("已有帳務：租期、租金、押金與繳款週期鎖定。姓名、Gmail和水電設定可修改；水電設定只套用新帳單。換租客請新增租約。",14,Color.rgb(137,83,15)));
                for(EditText f:new EditText[]{start,end,rent,day,deposit})f.setEnabled(false);cycle.setEnabled(false);
            }
        }
        ScrollView scroll = new ScrollView(this); scroll.addView(layout);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(unit.optString("code") + "：租約")
            .setView(scroll).setNegativeButton("取消", null).setPositiveButton("儲存", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(w -> {
            String person = tenant.getText().toString().trim(), mail = email.getText().toString().trim();
            if (person.isEmpty()) { tenant.setError("請填姓名"); return; }
            if (!Patterns.EMAIL_ADDRESS.matcher(mail).matches() || !mail.toLowerCase(Locale.ROOT).endsWith("@gmail.com")) {
                email.setError("請填有效 Gmail"); return;
            }
            LocalDate from, to;
            try { from = LocalDate.parse(dateValue(start)); to = LocalDate.parse(dateValue(end));
                if (to.isBefore(from)) throw fieldError(end,"到期日不得早於起租日");
            } catch (Exception e) { error(e); return; }
            long monthly, due, held, waterAmount, electricRate;
            try { monthly = amount(rent, false); due = amount(day, false); held = amount(deposit, false); waterAmount = amount(water, true); electricRate=amount(rate,false);
                if(monthly<=0)throw fieldError(rent,"月租金須大於0");if(due<1||due>31)throw fieldError(day,"每月應繳日須介於1與31之間");
            } catch (Exception e) { error(e); return; }
            JSONArray leases = data("leases"), units = data("units");
            for (int i = 0; i < leases.length(); i++) {
                JSONObject old = leases.optJSONObject(i); if(original!=null&&old.optString("id").equals(original.optString("id")))continue; JSONObject oldUnit = null;
                for (int j = 0; j < units.length(); j++) if (units.optJSONObject(j).optString("id").equals(old.optString("unitId")))
                    oldUnit = units.optJSONObject(j);
                boolean same = unit.optString("id").equals(old.optString("unitId"));
                boolean whole = oldUnit != null && unit.optString("propertyId").equals(oldUnit.optString("propertyId"))
                    && (unit.optString("type").equals("整棟") || oldUnit.optString("type").equals("整棟"));
                if ((same || whole) && !to.isBefore(LocalDate.parse(old.optString("start"))) && !from.isAfter(LocalDate.parse(old.optString("terminatedOn",old.optString("end"))))) {
                    end.setError("租期與既有租約重疊"); return;
                }
            }
            try {
                JSONObject lease = new JSONObject(); lease.put("id", original==null?UUID.randomUUID().toString():original.optString("id")); lease.put("unitId", unit.optString("id"));
                lease.put("unitCodeSnapshot", unit.optString("code")); lease.put("tenant", person); lease.put("email", mail);
                lease.put("start", from.toString()); lease.put("end", to.toString()); lease.put("rent", monthly);
                lease.put("dueDay", due); lease.put("cycle", cycle.getSelectedItem().toString()); lease.put("deposit", held);
                lease.put("utilities", utilities.getSelectedItem().toString()); lease.put("electricRate", electricRate);
                lease.put("water", utilities.getSelectedItemPosition() == 0 || waterAmount == -1 ? JSONObject.NULL : waterAmount);
                if(original==null)leases.put(lease);else for(int i=0;i<leases.length();i++)if(leases.optJSONObject(i).optString("id").equals(original.optString("id")))leases.put(i,lease);
                if (save("leases", leases)) { dialog.dismiss(); render(); }
            } catch (JSONException e) { tenant.setError("保存格式錯誤"); }
        })); dialog.show();
    }


    private boolean hasBilling(String leaseId) {
        for(String key:new String[]{"invoices","receipts","deposits"}) {JSONArray rows=data(key);for(int i=0;i<rows.length();i++)if(rows.optJSONObject(i).optString("leaseId").equals(leaseId))return true;}return false;
    }
    private void error(Exception e) { new AlertDialog.Builder(this).setMessage(display(String.valueOf(e.getMessage()==null?"資料格式不正確，請檢查輸入。":e.getMessage()))).setPositiveButton("知道了",null).show(); }
    private void copyBill(JSONObject lease,String month) {
        try {
            JSONArray invoices=data("invoices"),receipts=data("receipts");long rent=0,water=0,electric=0,paid=0,total=0;boolean rental=false,utility=false;String due="";
            StringBuilder reading=new StringBuilder();
            for(int i=0;i<invoices.length();i++){JSONObject r=invoices.getJSONObject(i);if(!lease.getString("id").equals(r.getString("leaseId"))||!month.equals(r.getString("month")))continue;
                rent+=r.getLong("rent");water+=r.getLong("water");electric+=r.getLong("electricity");total+=Ledger.total(r);paid+=Ledger.paid(r.getString("id"),receipts);
                if(due.isEmpty()||r.getString("due").compareTo(due)<0)due=r.getString("due");
                if(r.getString("kind").equals("租金"))rental=true;else{utility=true;reading.append("\n電表：").append(fmt(r.getLong("previous"))).append(" → ").append(fmt(r.getLong("current"))).append("\n電價：每度 $").append(r.getLong("rate"));}
            }
            if(!rental||(lease.getString("utilities").equals("管理者代收水電")&&!utility))throw new IllegalArgumentException("請先算好水電並建立完整當月帳單，再複製繳費明細。");
            String message=display(lease.getString("unitCodeSnapshot")+" · "+lease.getString("tenant")+"\n"+month+" 繳費明細\n租金：$"+rent
                +(utility?"\n水費：$"+water+"\n電費：$"+electric+reading:"\n水電自行繳納")+"\n合計：$"+total+"\n已付：$"+paid+"\n尚需繳付：$"+(total-paid)+"\n應繳日期："+due);
            android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("當月繳費明細",message));Toast.makeText(this,"已複製，可自行貼到LINE通知租客",Toast.LENGTH_LONG).show();
        }catch(Exception e){error(e);}
    }
    private void rentScreen() {
        card("帳單與收款", "先確認租金與水電建立當月帳單，再登錄收到租金。未開帳單不代表沒有欠款。");
        if(data("leases").length()==0){card("尚無租約","請先到不動產建立出租單位與租約。");return;}
        EditText search=field(body,"搜尋姓名、不動產或代號");search.setText(rentSearch);
        Spinner filter=choice(body,new String[]{"全部租約","有未繳金額（含未到期）","已有帳單且全部繳清","尚未開帳單"});filter.setSelection(rentFilter);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);body.addView(list);
        Runnable refresh=()->{
            list.removeAllViews();LinearLayout previous=body;body=list;
            try {
                JSONArray leases=data("leases"),invoices=data("invoices"),receipts=data("receipts");List<JSONObject> sorted=new ArrayList<>();for(int i=0;i<leases.length();i++)sorted.add(leases.getJSONObject(i));
                sorted.sort((x,y)->y.optString("start").compareTo(x.optString("start")));int count=0;
                for(JSONObject l:sorted) {
                    JSONObject unit=find("units",l.getString("unitId")),property=unit==null?null:find("properties",unit.optString("propertyId"));
                    String title=(property==null?"":property.optString("name")+" / ")+(unit==null?l.optString("unitCodeSnapshot"):unit.optString("code"))+" · "+l.optString("tenant");
                    if(!title.toLowerCase(Locale.ROOT).contains(search.getText().toString().trim().toLowerCase(Locale.ROOT)))continue;
                    long debt=0;int billed=0;for(int i=0;i<invoices.length();i++){JSONObject r=invoices.getJSONObject(i);if(l.getString("id").equals(r.getString("leaseId"))){billed++;debt+=Ledger.total(r)-Ledger.paid(r.getString("id"),receipts);}}
                    int selectedFilter=filter.getSelectedItemPosition();if(selectedFilter==1&&debt<=0||selectedFilter==2&&(billed==0||debt!=0)||selectedFilter==3&&billed!=0)continue;
                    String status=billed==0?"尚未開帳單":debt>0?"尚未繳付 $"+debt+"（含未到期）":"已開帳單全部繳清";
                    tapCard(title,l.getString("start")+" ～ "+l.getString("end")+"\n"+status,()->leaseLedger(l));count++;
                }
                if(count==0)card("沒有符合的租約","可調整搜尋或繳款狀態；歷史紀錄仍保留。");
            }catch(Exception e){error(e);}finally{body=previous;}
        };
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){rentSearch=s.toString();refresh.run();}public void afterTextChanged(Editable e){}});
        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,android.view.View v,int n,long id){rentFilter=n;refresh.run();}public void onNothingSelected(AdapterView<?> p){}});refresh.run();
    }
    private void leaseLedger(JSONObject lease) {
        String leaseId=lease.optString("id");int[] tab={0};
        LinearLayout page=form();page.setBackgroundColor(Color.rgb(248,244,237));
        LinearLayout header=form();page.addView(header);
        LinearLayout tabs=new LinearLayout(this);page.addView(tabs,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this);LinearLayout content=form();scroll.addView(content);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        AlertDialog dialog=new AlertDialog.Builder(this).setView(page).create();
        String[] names={"總覽","帳單","押金","退租結算"};Button[] buttons=new Button[names.length];
        Runnable refresh=()->{
            JSONObject current=find("leases",leaseId);header.removeAllViews();
            header.addView(text(current==null?"租約已不存在":current.optString("unitCodeSnapshot")+" · "+current.optString("tenant"),22,Color.rgb(90,61,37)));
            if(current!=null)header.addView(text(current.has("terminatedOn")?"已退租 · "+current.optString("terminatedOn"):"租期 "+current.optString("start")+" ～ "+current.optString("end"),14,Color.DKGRAY));
            for(int i=0;i<buttons.length;i++)if(buttons[i]!=null){buttons[i].setTextColor(Color.rgb(90,61,37));buttons[i].setBackgroundColor(i==tab[0]?Color.rgb(232,213,187):Color.rgb(248,244,237));buttons[i].setSelected(i==tab[0]);}
            drawLeaseLedger(content,current,tab[0]);
        };
        for(int i=0;i<names.length;i++){final int index=i;Button b=new Button(this);buttons[i]=b;b.setText(names[i]);b.setTextSize(13);b.setAllCaps(false);b.setPadding(0,dp(8),0,dp(8));b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(48));tabs.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->{tab[0]=index;refresh.run();scroll.scrollTo(0,0);});}
        Button close=new Button(this);close.setText("返回");close.setAllCaps(false);close.setOnClickListener(v->dialog.dismiss());page.addView(close,new LinearLayout.LayoutParams(-1,-2));
        livePanels.put(dialog,()->{int y=scroll.getScrollY();refresh.run();scroll.post(()->scroll.scrollTo(0,y));});dialog.setOnDismissListener(d->livePanels.remove(dialog));refresh.run();dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-1);
    }
    private void drawLeaseLedger(LinearLayout content,JSONObject lease,int tab) {
        content.removeAllViews();LinearLayout previous=body;body=content;
        try {
            if(lease==null){card("租約已不存在","請返回不動產重新選擇。");return;}
            JSONArray invoices=data("invoices"),receipts=data("receipts");long debt=0,received=0,credit=0;
            for(int i=0;i<invoices.length();i++){JSONObject r=invoices.getJSONObject(i);if(lease.optString("id").equals(r.getString("leaseId")))debt+=Ledger.total(r)-Ledger.paid(r.getString("id"),receipts);}
            for(int i=0;i<receipts.length();i++){JSONObject r=receipts.getJSONObject(i);if(lease.optString("id").equals(r.getString("leaseId"))){if(r.optBoolean("cash",true))received+=Ledger.money(r,"amount");credit+=Ledger.money(r,"amount")-Ledger.allocated(r);}}
            JSONObject settlement=Settlements.forLease(data("settlements"),lease.getString("id"));
            JSONObject deposit=Deposits.summary(lease.getString("id"),data("deposits"));
            if(tab==0){
                card(settlement==null?"帳單待繳（含未到期）":"結算待補繳","$"+(settlement==null?debt:Settlements.due(settlement)));
                card("持有押金","$"+deposit.getLong("held"));
                if(!lease.has("terminatedOn"))action("＋ 收到租金",()->receiptForm(lease));
                else {if(settlement!=null&&Settlements.due(settlement)>0)action("＋ 收到結算補繳",()->settlementPaymentForm(lease));if(deposit.getLong("held")>0)action("退還剩餘押金",()->depositForm(lease,true));}
                card("收款摘要","租金與水電實收累計 $"+received+"\n尚未分配 $"+credit+"\n押金與結算補繳另外記錄。");
                if(credit>0)action("分配先前尚未分配的收款",()->allocateCredit(lease));
                card("租約資料",lease.optString("start")+" ～ "+lease.optString("end")+"\n月租金 $"+lease.optLong("rent")+" · 約定押金 $"+lease.optLong("deposit"));
                return;
            }
            if(tab==2){depositCard(lease);return;}
            if(tab==3){if(settlement==null)card("尚未退租結算","退租時分開記錄費用與押金收退，再核對實際退款或補繳。");settlementCard(lease);return;}
            if(!lease.has("terminatedOn"))action("＋ 建立當月帳單（租金＋水電）",()->billingForm(lease));
            action("抄電表／查看照片",()->meterPanel(lease));
            Map<String,List<JSONObject>> months=new TreeMap<>(Collections.reverseOrder());
            for(int i=0;i<invoices.length();i++) {JSONObject r=invoices.getJSONObject(i);if(lease.optString("id").equals(r.getString("leaseId")))months.computeIfAbsent(r.getString("month"),key->new ArrayList<>()).add(r);}
            for(Map.Entry<String,List<JSONObject>> entry:months.entrySet()) {
                long rent=0,water=0,electric=0,paid=0,total=0;StringBuilder detail=new StringBuilder();boolean hasRent=false,hasUtility=false;
                for(JSONObject r:entry.getValue()) {
                    rent+=r.getLong("rent");water+=r.getLong("water");electric+=r.getLong("electricity");paid+=Ledger.paid(r.getString("id"),receipts);total+=Ledger.total(r);
                    if(r.getString("kind").equals("租金"))hasRent=true;else{hasUtility=true;detail.append("\n電表 ").append(fmt(r.getLong("previous"))).append(" → ").append(fmt(r.getLong("current"))).append(" · 每度 $").append(r.getLong("rate"));}
                }
                String utility=hasUtility?"水費 $"+water+"／電費 $"+electric:lease.optString("utilities").equals("管理者代收水電")?"水電尚未建立":"水電自行繳納";
                LinearLayout monthBox=card(entry.getKey()+" · 當月帳單","應收 $"+total+" · 已付 $"+paid+"\n待繳 $"+(total-paid));
                LinearLayout details=form();details.setVisibility(View.GONE);monthBox.addView(details);
                LinearLayout monthParent=body;body=details;
                card("帳單內容",(hasRent?"租金 $"+rent:"租金尚未建立")+"\n"+utility+detail+"\n已建立合計 $"+total+" · 已付 $"+paid+"\n尚需繳付 $"+(total-paid));
                for(JSONObject bill:entry.getValue())if(bill.has("meterReadingId")){JSONObject meter=find("meterReadings",bill.optString("meterReadingId"));if(meter!=null&&!meter.optString("file").isEmpty())action("查看此帳單的電表照片",()->viewMeterPhoto(meter.optString("file")));}
                final String billMonth=entry.getKey();action("複製 "+billMonth+" 繳費明細",()->copyBill(lease,billMonth));
                for(JSONObject r:entry.getValue())if(!lease.has("terminatedOn")&&Ledger.paid(r.getString("id"),receipts)==0)action("撤銷 "+r.optString("month")+" 的"+r.optString("kind")+"項目",()->deleteInvoice(r));
                body=monthParent;
                cardAction(monthBox,"展開／收合明細",()->details.setVisibility(details.getVisibility()==View.GONE?View.VISIBLE:View.GONE));
            }
            if(months.isEmpty())card("尚無帳單","建立當月租金與水電後，會顯示在這裡。");
            card("收款紀錄","實際租金與水電收款，以及結算抵付紀錄。");
            for(int i=receipts.length()-1;i>=0;i--) {JSONObject r=receipts.getJSONObject(i);if(!lease.optString("id").equals(r.getString("leaseId")))continue;
                card(r.getString("date")+(r.optBoolean("cash",true)?" · 實收 $":" · 結算抵付 $")+r.getLong("amount"),r.getString("account")+"\n已分配 $"+Ledger.allocated(r)+" · 尚未分配 $"+(r.getLong("amount")-Ledger.allocated(r))+"\n"+r.optString("note"));
                if(!lease.has("terminatedOn"))action("撤銷此筆錯誤收款",()->deleteReceipt(r));
            }
        }catch(Exception e){error(e);}finally{body=previous;}
    }
    private void billingForm(JSONObject lease) {
        if(lease.has("terminatedOn")){error(new IllegalArgumentException("已退租，帳單已結算"));return;}
        LinearLayout layout=form();
        layout.addView(text("租金、水費與電費在這裡一起建立；已存在的帳單沿用原金額，不會重複開立。首末月租金請填實際應收。",14,Color.DKGRAY));
        EditText month=dateField(layout,"帳單月份",true);month.setText(YearMonth.from(today()).toString());
        TextView rentLabel=text("租金",15,Color.DKGRAY);layout.addView(rentLabel);
        EditText rent=number(layout,"本月租金");rent.setText(lease.optString("rent"));
        LinearLayout utilities=form();layout.addView(utilities);
        utilities.addView(text("水費與電表（可帶入已存抄表讀數）",15,Color.DKGRAY));
        EditText water=number(utilities,"固定月水費，未知請先確認"),prior=number(utilities,"上期／起始電表讀數"),current=number(utilities,"本期電表讀數");
        TextView rateLabel=text("",14,Color.DKGRAY);utilities.addView(rateLabel);
        TextView summary=text("",16,Color.rgb(90,61,37));layout.addView(summary);
        boolean[] refreshing={false};
        Runnable update=()->{
            if(refreshing[0])return;
            try {
                YearMonth ym=YearMonth.parse(dateValue(month));JSONArray invoices=data("invoices");
                JSONObject existingRent=Ledger.forMonth(invoices,lease.optString("id"),ym,"租金"),existingUtility=Ledger.forMonth(invoices,lease.optString("id"),ym,"水電");
                boolean collect=lease.optString("utilities").equals("管理者代收水電"),needUtility=collect&&existingUtility==null;
                JSONObject plan=Ledger.monthlyPlan(lease,ym,existingRent==null?amount(rent,false):0,needUtility?amount(water,true):0,needUtility?amount(prior,false):0,needUtility?amount(current,false):0,invoices,data("receipts"));
                JSONObject r=plan.getJSONObject("rent"),u=plan.optJSONObject("utility");
                summary.setText("租金 $"+r.getLong("rent")+(u==null?"\n水電：租客自行繳納":"\n水費 $"+u.getLong("water")+" · 電費 $"+u.getLong("electricity"))
                    +"\n本月帳單合計 $"+plan.getLong("total")+"\n已分配收款 $"+plan.getLong("paid")+"\n本月尚需繳付 $"+plan.getLong("remaining"));
            }catch(Exception e){summary.setText("請填完整月份、金額與電表讀數，完成後顯示本月合計。\n水費未知請先確認；帳單不代表已收款。");}
        };
        Runnable loadMonth=()->{
            try {
                YearMonth ym=YearMonth.parse(dateValue(month));JSONArray invoices=data("invoices"),receipts=data("receipts");
                JSONObject r=Ledger.forMonth(invoices,lease.optString("id"),ym,"租金"),u=Ledger.forMonth(invoices,lease.optString("id"),ym,"水電");
                refreshing[0]=true;
                rent.setEnabled(r==null);rent.setText(r==null?lease.optString("rent"):r.optString("rent"));
                rentLabel.setText(r==null?"租金（尚未建立）":"租金帳單已建立 · 已付 $"+Ledger.paid(r.getString("id"),receipts)+"，沿用原帳單");
                boolean collect=lease.optString("utilities").equals("管理者代收水電");utilities.setVisibility(collect||u!=null?android.view.View.VISIBLE:android.view.View.GONE);
                water.setEnabled(u==null);prior.setEnabled(u==null);current.setEnabled(u==null);
                water.setText(u!=null?u.optString("water"):lease.isNull("water")?"":lease.optString("water"));
                JSONObject reading=MeterReadings.latest(data("meterReadings"),lease.optString("id"),ym.toString());
                prior.setText(u!=null?u.optString("previous"):reading==null?"":reading.optString("previous"));current.setText(u!=null?u.optString("current"):reading==null?"":reading.optString("current"));
                rateLabel.setText(u!=null?"水電帳單已建立，沿用原讀數與每度 $"+u.optLong("rate"):"每度 $"+lease.optLong("electricRate",5)+"；跨租客不自動沿用讀數"+(reading==null?"":" · 已帶入抄表日期 "+reading.optString("date")));
            }catch(Exception ignored){}finally{refreshing[0]=false;}update.run();
        };
        TextWatcher totals=new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){update.run();}public void afterTextChanged(Editable e){}};
        for(EditText f:new EditText[]{rent,water,prior,current})f.addTextChangedListener(totals);
        month.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){loadMonth.run();}public void afterTextChanged(Editable e){}});
        loadMonth.run();
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("建立當月帳單").setView(scroll).setNegativeButton("取消",null).setPositiveButton("預覽整筆帳單",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try {
            YearMonth ym=YearMonth.parse(dateValue(month));JSONArray before=data("invoices");
            JSONObject existingRent=Ledger.forMonth(before,lease.optString("id"),ym,"租金"),existingUtility=Ledger.forMonth(before,lease.optString("id"),ym,"水電");
            boolean needUtility=lease.optString("utilities").equals("管理者代收水電")&&existingUtility==null;
            JSONObject plan=Ledger.monthlyPlan(lease,ym,existingRent==null?amount(rent,false):0,needUtility?amount(water,true):0,needUtility?amount(prior,false):0,needUtility?amount(current,false):0,before,data("receipts"));
            JSONArray added=plan.getJSONArray("added");JSONObject r=plan.getJSONObject("rent"),u=plan.optJSONObject("utility");
            JSONObject meter=MeterReadings.latest(data("meterReadings"),lease.optString("id"),ym.toString());if(needUtility&&u!=null&&meter!=null&&meter.getLong("previous")==u.getLong("previous")&&meter.getLong("current")==u.getLong("current"))u.put("meterReadingId",meter.getString("id"));
            String preview=ym+"\n租金 $"+r.getLong("rent")+(u==null?"\n水電自行繳納":"\n水費 $"+u.getLong("water")+"\n電費 $"+u.getLong("electricity"))
                +"\n合計 $"+plan.getLong("total")+"\n已付 $"+plan.getLong("paid")+" · 尚需繳付 $"+plan.getLong("remaining")+"\n"+(added.length()==0?"此月份已完整建立，無需重複開單。":"只新增尚未建立的項目，不新增收款紀錄。");
            AlertDialog.Builder confirm=new AlertDialog.Builder(this).setMessage(display(String.valueOf(preview))).setNegativeButton("返回",null);
            if(added.length()>0)confirm.setPositiveButton("確認建立",(d,b)->{try{
                JSONArray latest=data("invoices");if(!latest.toString().equals(before.toString()))throw new IllegalArgumentException("帳單已改變，請重新預覽。");
                for(int i=0;i<added.length();i++)Ledger.addUnique(latest,added.getJSONObject(i));
                if(save("invoices",latest)){dialog.dismiss();render();Toast.makeText(this,"本月帳單已建立",Toast.LENGTH_LONG).show();}
            }catch(Exception e){error(e);}});
            confirm.show();
        }catch(Exception e){error(e);}}));dialog.show();
    }
    private String allocationSummary(JSONObject receipt,JSONArray invoices) throws JSONException {
        StringBuilder text=new StringBuilder();JSONArray parts=receipt.getJSONArray("allocations");
        for(int i=0;i<parts.length();i++){JSONObject p=parts.getJSONObject(i);for(int j=0;j<invoices.length();j++) {JSONObject r=invoices.getJSONObject(j);if(r.getString("id").equals(p.getString("invoiceId")))text.append(r.getString("month")).append(" ").append(r.getString("kind")).append(" $ ").append(p.getLong("amount")).append("\n");}}
        return text+"尚未分配 $"+(receipt.getLong("amount")-Ledger.allocated(receipt));
    }
    private void receiptForm(JSONObject lease) {receiptForm(lease,null);}
    private void receiptForm(JSONObject lease,JSONObject draft) {
        if(lease.has("terminatedOn")){settlementPaymentForm(lease);return;}
        List<JSONObject> accounts=accountRows();if(accounts.isEmpty()){error(new IllegalArgumentException("請先到記帳建立實際收款帳戶"));return;}
        addDraftAccountPlaceholder(accounts,draft);
        LinearLayout layout=form();EditText date=dateField(layout,"收到租金日期",false);date.setText(today().toString());
        JSONObject unit=find("units",lease.optString("unitId")),property=unit==null?null:find("properties",unit.optString("propertyId"));
        Spinner account=accountChoice(layout,accounts,"核對存摺的實際收款帳戶");if(draft==null&&property!=null)for(int i=0;i<accounts.size();i++)if(accounts.get(i).optString("propertyId").equals(property.optString("id"))){account.setSelection(i);break;}
        EditText money=number(layout,"實際收到金額（含一起繳的水電費）"),note=field(layout,"備註／交易辨識，建議填存摺摘要");
        if(draft!=null){date.setText(draft.optString("date"));setDraftAmount(money,draft);note.setText(draft.optString("raw"));selectDraftAccount(account,accounts,draft.optString("accountId"));}
        layout.addView(text("依最早月份分配，當月租金先於水電。請先算好水電並建立當月完整帳單；有舊欠款時會先抵付。請核對下個畫面的分配清單。押金另行管理。",14,Color.DKGRAY));
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("收到租金").setView(scroll).setNegativeButton("取消",null).setPositiveButton("預覽分配",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try {
            JSONArray invoices=data("invoices"),receipts=data("receipts");JSONObject r=Ledger.receipt(lease.getString("id"),dateValue(date),selectedDraftAccount(accounts,account).getString("name"),amount(money,false),note.getText().toString(),invoices,receipts).put("accountId",selectedDraftAccount(accounts,account).getString("id"));
            for(int i=0;i<receipts.length();i++){JSONObject old=receipts.getJSONObject(i);if(old.getString("leaseId").equals(r.getString("leaseId"))&&old.getString("date").equals(r.getString("date"))&&Bookkeeping.sameReceiptAccount(old,r)&&old.getLong("amount")==r.getLong("amount"))throw new IllegalArgumentException("同租約、日期、帳戶與金額已有收款，請先核對避免重複。");}
            new AlertDialog.Builder(this).setMessage(display(String.valueOf("實際收款 $"+r.getLong("amount")+"\n"+allocationSummary(r,invoices)))).setNegativeButton("返回修改",null).setPositiveButton("確認入帳",(d,b)->{try{
                JSONArray latest=data("receipts");for(int i=0;i<latest.length();i++) {JSONObject old=latest.getJSONObject(i);if(old.getString("id").equals(r.getString("id")))return;
                    if(old.getString("leaseId").equals(r.getString("leaseId"))&&old.getString("date").equals(r.getString("date"))&&Bookkeeping.sameReceiptAccount(old,r)&&old.getLong("amount")==r.getLong("amount"))throw new IllegalArgumentException("此筆收款已有相同紀錄，未重複入帳。");}
                latest.put(r);Set<String> ids=new HashSet<>();JSONArray ls=data("leases");for(int i=0;i<ls.length();i++)ids.add(ls.getJSONObject(i).getString("id"));Ledger.validate(data("invoices"),latest,ids);
                if(saveChecked("receipts",latest)){dialog.dismiss();render();Toast.makeText(this,"收款已登錄",Toast.LENGTH_LONG).show();}
            }catch(Exception e){error(e);}}).show();
        }catch(Exception e){error(e);}}));dialog.show();
    }
    private void allocateCredit(JSONObject lease) {
        try {
            JSONArray invoices=data("invoices"),receipts=data("receipts");JSONArray next=new JSONArray(receipts.toString());StringBuilder preview=new StringBuilder();boolean changed=false;
            for(int i=0;i<next.length();i++) {
                JSONObject r=next.getJSONObject(i);if(!lease.getString("id").equals(r.getString("leaseId")))continue;
                long left=r.getLong("amount")-Ledger.allocated(r);if(left==0)continue;
                JSONObject allocation=Ledger.receipt(lease.getString("id"),r.getString("date"),r.getString("account"),left,r.optString("note"),invoices,next);
                JSONArray added=allocation.getJSONArray("allocations"),parts=r.getJSONArray("allocations");
                for(int j=0;j<added.length();j++) {
                    JSONObject a=added.getJSONObject(j);boolean merged=false;for(int k=0;k<parts.length();k++)if(parts.getJSONObject(k).getString("invoiceId").equals(a.getString("invoiceId"))){parts.getJSONObject(k).put("amount",parts.getJSONObject(k).getLong("amount")+a.getLong("amount"));merged=true;}
                    if(!merged)parts.put(a);changed=true;
                }
                preview.append(r.getString("date")).append("\n").append(allocationSummary(allocation,invoices)).append("\n");
            }
            if(!changed)throw new IllegalArgumentException("目前沒有可分配的未付帳單，請先開立帳單。");
            new AlertDialog.Builder(this).setMessage(display(String.valueOf("僅調整分配，不新增銀行收款。\n"+preview))).setNegativeButton("取消",null).setPositiveButton("確認分配",(d,b)->{
                if(!data("receipts").toString().equals(receipts.toString())) {error(new IllegalArgumentException("資料已改變，請重新分配"));return;}
                if(save("receipts",next)){render();Toast.makeText(this,"分配完成",Toast.LENGTH_LONG).show();}
            }).show();
        }catch(Exception e){error(e);}
    }
    private void deleteInvoice(JSONObject invoice) {
        new AlertDialog.Builder(this).setMessage(display(String.valueOf("僅限尚未收款的錯誤帳單可撤銷。確定撤銷此張帳單？"))).setNegativeButton("取消",null).setPositiveButton("撤銷",(d,b)->{try{
            if(Ledger.paid(invoice.getString("id"),data("receipts"))!=0)throw new IllegalArgumentException("已有收款分配，不能撤銷帳單。");
            JSONArray rows=data("invoices"),next=new JSONArray();for(int i=0;i<rows.length();i++)if(!rows.getJSONObject(i).getString("id").equals(invoice.getString("id")))next.put(rows.getJSONObject(i));
            if(save("invoices",next)){render();Toast.makeText(this,"已撤銷",Toast.LENGTH_LONG).show();}
        }catch(Exception e){error(e);}}).show();
    }
    private void deleteReceipt(JSONObject receipt) {
        new AlertDialog.Builder(this).setMessage(display(String.valueOf("撤銷錯誤登錄會移除此筆收款與所有分配，恢復帳單未付金額。實際退款不能用撤銷代替。確定？"))).setNegativeButton("取消",null).setPositiveButton("撤銷錯誤登錄",(d,b)->{try{
            JSONArray rows=data("receipts"),next=new JSONArray();for(int i=0;i<rows.length();i++)if(!rows.getJSONObject(i).getString("id").equals(receipt.getString("id")))next.put(rows.getJSONObject(i));
            if(save("receipts",next)){render();Toast.makeText(this,"已撤銷",Toast.LENGTH_LONG).show();}
        }catch(Exception e){error(e);}}).show();
    }

    private void depositCard(JSONObject lease) throws JSONException {
        JSONObject sums=Deposits.summary(lease.getString("id"),data("deposits"));
        card("押金", "約定 $"+lease.getLong("deposit")+"\n累計已收 $"+sums.getLong("received")+" · 已退 $"+sums.getLong("refunded")+"\n抵付費用 $"+sums.getLong("applied")+" · 持有／待退餘額 $"+sums.getLong("held")+"\n押金不是租金收入；退租時可另做分項結算。");
        if(!lease.has("terminatedOn"))action("＋ 收到押金",()->depositForm(lease,false));action("退還押金",()->depositForm(lease,true));
        JSONArray rows=data("deposits");List<JSONObject> sorted=new ArrayList<>();for(int i=0;i<rows.length();i++)if(lease.getString("id").equals(rows.getJSONObject(i).getString("leaseId")))sorted.add(rows.getJSONObject(i));
        sorted.sort(Comparator.comparing((JSONObject r)->r.optString("date")).reversed());
        for(JSONObject r:sorted){card(r.getString("date")+" · "+r.getString("kind")+" $"+r.getLong("amount"),accountName(r.getString("accountId"))+"\n"+r.getString("note")+(r.getBoolean("void")?"\n已撤銷："+r.optString("voidReason"):""));
            if(!r.getBoolean("void")&&!lease.has("terminatedOn"))action("撤銷此筆錯誤押金紀錄",()->voidMovement("deposits",r));}
    }
    private void depositForm(JSONObject lease,boolean refund) {
        if(lease.has("terminatedOn")&&!refund){error(new IllegalArgumentException("已退租不能新增押金收取"));return;}
        List<JSONObject> accounts=accountRows();if(accounts.isEmpty()){error(new IllegalArgumentException("請先到記帳建立銀行或共用現金帳戶"));return;}
        LinearLayout layout=form();String kind=refund?"退還押金":"收到押金";
        EditText date=dateField(layout,refund?"退款日期":"收取日期",false);Spinner account=accountChoice(layout,accounts,refund?"退款帳戶":"收款帳戶");
        JSONObject unit=find("units",lease.optString("unitId"));if(unit!=null)for(int i=0;i<accounts.size();i++)if(accounts.get(i).optString("propertyId").equals(unit.optString("propertyId"))){account.setSelection(i);break;}
        EditText amount=labeledNumber(layout,refund?"實際退款金額":"實際收到押金金額"),note=labeledField(layout,"備註／交易辨識");
        layout.addView(text("約定押金 $"+lease.optLong("deposit")+"。請依實際金流登錄；補登起算日前收付只補押金餘額，不重複加減期初資金。退款不能超過該租約當時持有押金。",14,Color.DKGRAY));
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(kind).setView(scroll).setNegativeButton("取消",null).setPositiveButton("預覽",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try{
            JSONObject row=Deposits.movement(lease.getString("id"),accounts.get(account.getSelectedItemPosition()).getString("id"),dateValue(date),kind,amount(amount,false),note.getText().toString())
                .put("tenantSnapshot",lease.getString("tenant")).put("unitCodeSnapshot",lease.getString("unitCodeSnapshot"));
            JSONArray before=data("deposits"),proposed=new JSONArray(before.toString()).put(row);Bookkeeping.validate(fullBackup().put("deposits",proposed));
            JSONObject totals=Deposits.summary(lease.getString("id"),proposed);JSONObject selectedAccount=accounts.get(account.getSelectedItemPosition());
            String preview=lease.getString("unitCodeSnapshot")+" · "+lease.getString("tenant")+"\n"+row.getString("date")+" · "+kind+" $"+row.getLong("amount")+"\n帳戶："+selectedAccount.getString("name")+"\n確認後持有／待退 $"+totals.getLong("held")
                +(LocalDate.parse(row.getString("date")).isBefore(LocalDate.parse(selectedAccount.getString("start")))?"\n起算日前歷史，不重複加減期初資金。":"\n帳戶資金只加減一次，不列租金收入或支出。")
                +(totals.getLong("received")>lease.getLong("deposit")?"\n累計實收高於約定押金，請確認實際金額正確。":"");
            new AlertDialog.Builder(this).setMessage(display(preview)).setNegativeButton("修改",null).setPositiveButton("確認"+kind,(d,b)->{try{
                JSONArray latest=data("deposits");for(int i=0;i<latest.length();i++){JSONObject old=latest.getJSONObject(i);if(old.getString("id").equals(row.getString("id")))return;
                    if(!old.getBoolean("void")&&old.getString("leaseId").equals(row.getString("leaseId"))&&old.getString("accountId").equals(row.getString("accountId"))&&old.getString("date").equals(row.getString("date"))&&old.getString("kind").equals(row.getString("kind"))&&old.getLong("amount")==row.getLong("amount")&&old.getString("note").equals(row.getString("note")))throw new IllegalArgumentException("已有相同押金紀錄，請核對；若為另一筆請填不同交易辨識。");}
                latest.put(row);if(saveChecked("deposits",latest)){dialog.dismiss();render();}
            }catch(Exception e){error(e);}}).show();
        }catch(Exception e){error(e);}}));dialog.show();
    }
    private void depositOverview() {
        LinearLayout content=form();Runnable refresh=()->{
            content.removeAllViews();LinearLayout previous=body;body=content;try{
                JSONObject sums=Deposits.summary(null,data("deposits"));card("押金總覽","累計收到 $"+sums.getLong("received")+" · 已退 $"+sums.getLong("refunded")+"\n持有／待退 $"+sums.getLong("held")+"\n僅含已登錄紀錄；押金不列租金收入或費用。");
                JSONArray leases=data("leases");for(int i=0;i<leases.length();i++){JSONObject lease=leases.getJSONObject(i),held=Deposits.summary(lease.getString("id"),data("deposits"));
                    tapCard(lease.getString("unitCodeSnapshot")+" · "+lease.getString("tenant"),"約定 $"+lease.getLong("deposit")+" · 已收 $"+held.getLong("received")+"\n已退 $"+held.getLong("refunded")+" · 抵付 $"+held.getLong("applied")+" · 持有／待退 $"+held.getLong("held"),()->leaseLedger(lease));}
            }catch(Exception e){error(e);}finally{body=previous;}
        };refresh.run();showPanel("押金總覽",content,refresh);
    }

    private void settlementOverview() {
        LinearLayout layout=form();Runnable refresh=()->{layout.removeAllViews();LinearLayout previous=body;body=layout;try{JSONArray rows=data("settlements");long due=0,refund=0;for(int i=0;i<rows.length();i++){JSONObject s=rows.getJSONObject(i);due+=Settlements.due(s);refund+=Deposits.summary(s.getString("leaseId"),data("deposits")).getLong("held");}card("退租結算總覽","尚待補繳 $"+due+" · 押金尚待退 $"+refund);for(int i=rows.length()-1;i>=0;i--){JSONObject s=rows.getJSONObject(i),lease=find("leases",s.getString("leaseId"));tapCard(s.getString("unitCodeSnapshot")+" · "+s.getString("tenantSnapshot"),s.getString("date")+"\n待補繳 $"+Settlements.due(s)+" · 待退 $"+Deposits.summary(s.getString("leaseId"),data("deposits")).getLong("held"),()->leaseLedger(lease));}if(rows.length()==0)card("尚無退租結算","從租約的帳單與收款選擇退租／結算費用。");}catch(Exception e){error(e);}finally{body=previous;}};refresh.run();showPanel("退租結算總覽",layout,refresh);
    }
    private boolean settled(String leaseId) {
        try{return Settlements.forLease(data("settlements"),leaseId)!=null;}catch(Exception e){return true;}
    }
    private boolean saveSettlement(JSONObject proposed,JSONObject before) throws Exception {
        if(!fullBackup().toString().equals(before.toString()))throw new IllegalArgumentException("資料已變動，請重新預覽結算");
        validateBackup(proposed);android.content.SharedPreferences.Editor editor=getSharedPreferences("catalog",MODE_PRIVATE).edit();
        for(String key:KEYS)editor.putString(key,proposed.getJSONArray(key).toString());
        if(!editor.commit())throw new IOException("結算保存失敗");render();body.post(this::refreshPanels);return true;
    }
    private void settlementCard(JSONObject lease) throws JSONException {
        JSONObject s=Settlements.forLease(data("settlements"),lease.getString("id"));
        if(s==null){action("退租／結算費用",()->settlementForm(lease));return;}
        long held=Deposits.summary(lease.getString("id"),data("deposits")).getLong("held");
        card("已退租 · "+s.getString("date"),"租金與水電原帳單尚欠 $"+s.getLong("invoiceDebt")+"\n另收修繕費 $"+s.getLong("repairs")+" · 其他費用 $"+s.getLong("other")+"\n結算費用合計 $"+s.getLong("total")+"\n押金抵付費用 $"+s.getLong("depositUsed")+"\n尚需補繳 $"+Settlements.due(s)+" · 押金尚待退 $"+held+"\n"+s.getString("note"));
        LinearLayout sourceBox=card("結算來源","原帳單欠款明細，點開查看。");LinearLayout sourceDetails=form();sourceDetails.setVisibility(View.GONE);sourceBox.addView(sourceDetails);LinearLayout sourceParent=body;body=sourceDetails;
        JSONArray lines=s.getJSONArray("lines");for(int i=0;i<lines.length();i++){JSONObject r=lines.getJSONObject(i);card("結算原帳單 · "+r.getString("month")+" "+r.getString("kind"),"租金 $"+r.getLong("rent")+" · 水費 $"+r.getLong("water")+" · 電費 $"+r.getLong("electricity")+"\n原合計 $"+r.getLong("total")+" · 結算前已付 $"+r.getLong("paidBefore")+" · 結算時未付 $"+r.getLong("remaining"));}
        body=sourceParent;cardAction(sourceBox,"展開／收合原帳單",()->sourceDetails.setVisibility(sourceDetails.getVisibility()==View.GONE?View.VISIBLE:View.GONE));
        if(Settlements.due(s)>0)action("＋ 收到結算補繳",()->settlementPaymentForm(lease));
        if(held>0)action("退還剩餘押金",()->depositForm(lease,true));
        action("複製退租結算明細",()->{try{String msg=s.getString("unitCodeSnapshot")+" · "+s.getString("tenantSnapshot")+"\n退租日 "+s.getString("date")+"\n原帳單未付 $"+s.getLong("invoiceDebt")+"\n修繕費 $"+s.getLong("repairs")+" · 其他 $"+s.getLong("other")+"\n結算費用 $"+s.getLong("total")+"\n押金抵付 $"+s.getLong("depositUsed")+"\n尚需補繳 $"+Settlements.due(s)+" · 押金待退 $"+Deposits.summary(lease.getString("id"),data("deposits")).getLong("held")+"\n"+s.getString("note");android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(android.content.ClipData.newPlainText("退租結算",display(msg)));Toast.makeText(this,"結算明細已複製",Toast.LENGTH_SHORT).show();}catch(Exception e){error(e);}});
        action("同一單位新增下一位租客",()->{JSONObject unit=find("units",lease.optString("unitId"));if(unit!=null)addLease(unit);});
        JSONArray ps=s.getJSONArray("payments");for(int i=ps.length()-1;i>=0;i--){JSONObject p=ps.getJSONObject(i);card(p.getString("date")+" · 實收結算補繳 $"+p.getLong("amount"),accountName(p.getString("accountId"))+"\n"+p.getString("note"));}
    }
    private void settlementForm(JSONObject lease) {
        if(settled(lease.optString("id"))){error(new IllegalArgumentException("此租約已結算"));return;}
        LinearLayout layout=form();EditText date=dateField(layout,"退租日期",false);date.setText(today().isAfter(LocalDate.parse(lease.optString("end")))?lease.optString("end"):today().toString());
        layout.addView(text("最後一期租金自行填寫，水電沿用原帳單；尚未開立的部分會一起建立。修繕／其他費用是向租客收取，不是付給師傅的支出。",14,Color.DKGRAY));
        EditText rent=number(layout,"最後一期租金"),water=number(layout,"最後一期水費"),prior=number(layout,"上期電表讀數"),current=number(layout,"最後電表讀數"),repairs=number(layout,"向租客收修繕費"),other=number(layout,"其他結算費用"),note=field(layout,"結算說明／費用明細");
        repairs.setText("0");other.setText("0");boolean collect=lease.optString("utilities").equals("管理者代收水電");
        Runnable load=()->{try{YearMonth ym=YearMonth.from(LocalDate.parse(dateValue(date)));JSONObject r=Ledger.forMonth(data("invoices"),lease.optString("id"),ym,"租金"),u=Ledger.forMonth(data("invoices"),lease.optString("id"),ym,"水電");rent.setEnabled(r==null);rent.setText(r==null?lease.optString("rent"):r.optString("rent"));boolean edit=collect&&u==null;for(EditText f:new EditText[]{water,prior,current})f.setEnabled(edit);water.setText(u!=null?u.optString("water"):collect?(lease.isNull("water")?"":lease.optString("water")):"0");prior.setText(u!=null?u.optString("previous"):collect?"":"0");current.setText(u!=null?u.optString("current"):collect?"":"0");}catch(Exception ignored){}};
        date.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){load.run();}public void afterTextChanged(Editable e){}});load.run();
        layout.addView(text("既有月份金額鎖定並沿用；自行繳水電的租約不代收。確認結算後保留並鎖定原帳務，不自動退款。請先備份並核對，這版尚不提供取消已確認結算。",14,Color.DKGRAY));
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("退租結算費用").setView(scroll).setNegativeButton("取消",null).setPositiveButton("預覽結算",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try{
            JSONObject before=fullBackup(),proposed=Settlements.plan(before,lease.getString("id"),dateValue(date),amount(rent,false),amount(water,false),amount(prior,false),amount(current,false),amount(repairs,false),amount(other,false),note.getText().toString());validateBackup(proposed);
            JSONObject s=Settlements.forLease(proposed.getJSONArray("settlements"),lease.getString("id"));StringBuilder preview=new StringBuilder("退租日 "+s.getString("date")+"\n");JSONArray lines=s.getJSONArray("lines");for(int i=0;i<lines.length();i++){JSONObject r=lines.getJSONObject(i);preview.append(r.getString("month")).append(" ").append(r.getString("kind")).append("（租金 $").append(r.getLong("rent")).append("、水費 $").append(r.getLong("water")).append("、電費 $").append(r.getLong("electricity")).append("）尚欠 $").append(r.getLong("remaining")).append("\n");}
            preview.append("修繕費 $").append(s.getLong("repairs")).append(" · 其他 $").append(s.getLong("other")).append("\n費用合計 $").append(s.getLong("total")).append("\n持有押金 $").append(s.getLong("depositBefore")).append("\n押金抵付 $").append(s.getLong("depositUsed")).append("\n應退押金 $").append(s.getLong("depositBefore")-s.getLong("depositUsed")).append(" · 需補繳 $").append(Settlements.due(s)).append("\n確認只建立費用、抵付與退租紀錄；實際退款／補繳另外登錄。");
            new AlertDialog.Builder(this).setMessage(display(preview.toString())).setNegativeButton("返回修改",null).setPositiveButton("確認退租結算",(d,b)->{try{if(saveSettlement(proposed,before))dialog.dismiss();}catch(Exception e){error(e);}}).show();
        }catch(Exception e){error(e);}}));dialog.show();
    }
    private void settlementPaymentForm(JSONObject lease) {
        try{JSONObject s=Settlements.forLease(data("settlements"),lease.getString("id"));if(s==null||Settlements.due(s)<=0)throw new IllegalArgumentException("目前沒有待補繳結算費用");
            List<JSONObject> accounts=accountRows();if(accounts.isEmpty())throw new IllegalArgumentException("請先建立實際收款帳戶");LinearLayout layout=form();EditText date=dateField(layout,"補繳收款日期",false);Spinner account=accountChoice(layout,accounts,"實際收款帳戶");EditText money=number(layout,"實際收到補繳金額"),note=field(layout,"備註／交易辨識");layout.addView(text("尚需補繳 $"+Settlements.due(s)+"；可分次收款。這筆實際金流只計入帳戶一次。",14,Color.DKGRAY));
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("收到結算補繳").setView(layout).setNegativeButton("取消",null).setPositiveButton("預覽",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try{
                JSONObject before=fullBackup(),proposed=Settlements.payment(before,lease.getString("id"),dateValue(date),accounts.get(account.getSelectedItemPosition()).getString("id"),amount(money,false),note.getText().toString());validateBackup(proposed);JSONObject next=Settlements.forLease(proposed.getJSONArray("settlements"),lease.getString("id"));
                new AlertDialog.Builder(this).setMessage(display("實際收款 $"+amount(money,false)+"\n帳戶："+accounts.get(account.getSelectedItemPosition()).getString("name")+"\n收款日期："+date.getText()+"\n確認後尚欠 $"+Settlements.due(next))).setNegativeButton("修改",null).setPositiveButton("確認收款",(d,b)->{try{if(saveSettlement(proposed,before))dialog.dismiss();}catch(Exception e){error(e);}}).show();
            }catch(Exception e){error(e);}}));dialog.show();
        }catch(Exception e){error(e);}
    }

    private JSONObject fullBackup() throws JSONException {
        JSONObject backup=new JSONObject().put("format","rental-catalog").put("version",6);for(String key:KEYS)backup.put(key,data(key));return backup;
    }
    private boolean saveChecked(String key,JSONArray rows) throws JSONException {
        JSONObject backup=fullBackup().put(key,rows);Bookkeeping.validate(backup);return save(key,rows);
    }
    private EditText labeledNumber(LinearLayout layout,String label) {return number(layout,label);}
    private EditText labeledField(LinearLayout layout,String label) {return field(layout,label);}
    private List<JSONObject> accountRows() {
        List<JSONObject> result=new ArrayList<>();JSONArray accounts=data("accounts");for(int i=0;i<accounts.length();i++)result.add(accounts.optJSONObject(i));return result;
    }
    private Spinner accountChoice(LinearLayout layout,List<JSONObject> accounts,String label) {
        String[] names=new String[accounts.size()];for(int i=0;i<names.length;i++)names[i]=accounts.get(i).optString("name")+" · "+accounts.get(i).optString("type");return choice(layout,fieldLabel(label),names);
    }
    private void accountingScreen() {
        if(data("accounts").length()==0) {
            card("尚未設定帳戶","請依存摺建立各銀行帳戶、共用現金的起算日與期初餘額。未設定不顯示為零。");
            action("＋ 新增銀行／共用現金帳戶",this::accountForm);return;
        }
        LinearLayout shortcuts=new LinearLayout(this);body.addView(shortcuts,new LinearLayout.LayoutParams(-1,-2));
        shortcut(shortcuts,"記支出",R.drawable.nav_ledger,this::expenseForm);
        shortcut(shortcuts,"語音記帳",R.drawable.action_voice,()->voiceEditor(""));
        shortcut(shortcuts,"提款／轉帳",R.drawable.action_transfer,this::transferForm);
        actionGroup("帳戶管理",new String[]{"新增銀行／共用現金帳戶","查看帳戶流水與對帳","對應舊收款的實際帳戶"},new Runnable[]{this::accountForm,this::accountsDialog,this::linkReceipts});
        actionGroup("更多功能",new String[]{"押金總覽／收付","退租結算與待收／待退"},new Runnable[]{this::depositOverview,this::settlementOverview});
        TextView recent=text("近期紀錄",20,Color.rgb(77,57,39));recent.setTypeface(null,Typeface.BOLD);body.addView(recent);
        LinearLayout page=body,filters=new LinearLayout(this);filters.setOrientation(LinearLayout.VERTICAL);filters.setVisibility(expenseFiltersOpen?android.view.View.VISIBLE:android.view.View.GONE);
        action("搜尋與篩選  ▾",()->{expenseFiltersOpen=!expenseFiltersOpen;filters.setVisibility(expenseFiltersOpen?android.view.View.VISIBLE:android.view.View.GONE);});page.addView(filters,new LinearLayout.LayoutParams(-1,-2));body=filters;
        EditText search=field(body,"搜尋項目、帳戶或不動產");search.setText(expenseSearch);
        Spinner type=choice(body,"紀錄類型",new String[]{"全部紀錄","有效支出","有效轉帳","已撤銷"});type.setSelection(expenseFilter);
        Spinner scope=choice(body,"月份範圍",new String[]{"全部月份","指定月份"});scope.setSelection(expenseMonth.isEmpty()?0:1);
        EditText month=dateField(body,"紀錄月份",true);month.setText(expenseMonth.isEmpty()?YearMonth.from(today()).toString():expenseMonth);android.view.View monthRow=(android.view.View)month.getParent();monthRow.setVisibility(expenseMonth.isEmpty()?android.view.View.GONE:android.view.View.VISIBLE);
        body=page;TextView filterSummary=text("目前："+(expenseMonth.isEmpty()?"全部月份":expenseMonth)+" · "+new String[]{"全部紀錄","有效支出","有效轉帳","已撤銷"}[expenseFilter]+(expenseSearch.trim().isEmpty()?"":" · 搜尋："+expenseSearch),13,Color.DKGRAY);body.addView(filterSummary);
        LinearLayout records=new LinearLayout(this);records.setOrientation(LinearLayout.VERTICAL);body.addView(records,new LinearLayout.LayoutParams(-1,-2));
        Runnable draw=()->{filterSummary.setText("目前："+(expenseMonth.isEmpty()?"全部月份":expenseMonth)+" · "+new String[]{"全部紀錄","有效支出","有效轉帳","已撤銷"}[expenseFilter]+(expenseSearch.trim().isEmpty()?"":" · 搜尋："+expenseSearch));drawExpenseRecords(records);};
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){expenseSearch=s.toString();expenseLimit=15;draw.run();}public void afterTextChanged(Editable e){}});
        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,android.view.View v,int n,long id){expenseFilter=n;expenseLimit=15;draw.run();}});
        scope.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,android.view.View v,int n,long id){expenseMonth=n==0?"":month.getText().toString();monthRow.setVisibility(n==0?android.view.View.GONE:android.view.View.VISIBLE);expenseLimit=15;draw.run();}});
        month.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){if(scope.getSelectedItemPosition()==1){expenseMonth=s.toString();expenseLimit=15;draw.run();}}public void afterTextChanged(Editable e){}});draw.run();
    }
    private void drawExpenseRecords(LinearLayout records) {
        LinearLayout previous=body;body=records;records.removeAllViews();try {
            JSONArray expenses=data("expenses"),transfers=data("transfers");List<JSONObject> rows=new ArrayList<>();for(int i=0;i<expenses.length();i++)rows.add(expenses.optJSONObject(i));for(int i=0;i<transfers.length();i++)rows.add(transfers.optJSONObject(i));rows.sort(Comparator.comparing((JSONObject r)->r.optString("date")).reversed());
            MovementTotals totals=new MovementTotals();TextView summary=text("",14,Color.rgb(90,61,37));body.addView(summary);
            int matches=0,shown=0;String query=expenseSearch.trim().toLowerCase(Locale.ROOT);
            for(JSONObject r:rows){boolean transfer=r.has("fromId"),voided=r.optBoolean("void");if(!expenseMonth.isEmpty()&&!r.optString("date").startsWith(expenseMonth))continue;if(expenseFilter==1&&(transfer||voided)||expenseFilter==2&&(!transfer||voided)||expenseFilter==3&&!voided)continue;
                String title=r.optString("date")+" · "+(transfer?"轉帳":r.optString("category"))+" $"+r.optLong("amount");String detail=r.optString("description")+(voided?"\n已撤銷錯誤紀錄":"");
                if(transfer)detail+="\n"+accountName(r.optString("fromId"))+" → "+accountName(r.optString("toId"));else{detail+="\n付款："+accountName(r.optString("accountId"));JSONArray splits=r.optJSONArray("splits");if(splits!=null)for(int j=0;j<splits.length();j++)detail+="\n"+propertyName(splits.optJSONObject(j).optString("propertyId"))+" $"+splits.optJSONObject(j).optLong("amount");}
                if(!(title+" "+detail+" "+display(title+" "+detail)).toLowerCase(Locale.ROOT).contains(query))continue;totals.add(r);matches++;if(shown>=expenseLimit)continue;shown++;LinearLayout box=card(title,detail);if(!voided)cardAction(box,"撤銷",()->voidMovement(transfer?"transfers":"expenses",r));
            }
            summary.setText("符合條件的有效支出 $"+totals.expenses+" · 轉帳 $"+totals.transfers+"\n包含尚未載入的符合紀錄；已撤銷不計，轉帳不列支出。" );
            if(matches==0)card("沒有符合條件的紀錄",rows.isEmpty()?"可以逐步補登半年資料。":"請調整月份、紀錄類型或搜尋文字。");else {body.addView(text("顯示 "+shown+" / "+matches+" 筆",14,Color.DKGRAY));if(matches>shown)action("載入更多紀錄",()->{expenseLimit+=15;drawExpenseRecords(records);});}
        }finally{body=previous;}
    }
    private String receiptTenant(JSONObject receipt) {JSONObject lease=find("leases",receipt.optString("leaseId"));return lease==null?"":lease.optString("unitCodeSnapshot")+" · "+lease.optString("tenant");}
    private String accountName(String id) {JSONObject a=find("accounts",id);return a==null?"帳戶未設定":a.optString("name");}
    private String propertyName(String id) {JSONObject p=find("properties",id);return p==null?"不動產不存在":p.optString("name");}
    private long balance(JSONObject account,String date) throws JSONException {return Bookkeeping.balance(account,date,data("receipts"),data("expenses"),data("transfers"),data("deposits"),data("settlements"));}
    private void accountForm() {
        LinearLayout layout=form();EditText name=labeledField(layout,"帳戶代稱，例如A帳戶、共用現金");Spinner type=choice(layout,new String[]{"銀行","現金"});
        EditText start=dateField(layout,"帳戶起算日",false);start.setText(today().toString());EditText opening=labeledNumber(layout,"起算日開始前的餘額，若無填0");
        JSONArray properties=data("properties");String[] options=new String[properties.length()+1];options[0]="不綁定／共用現金";for(int i=0;i<properties.length();i++)options[i+1]=properties.optJSONObject(i).optString("name");
        Spinner property=choice(layout,"對應不動產",options);
        layout.addView(text("請以存摺餘額為準，起算日當天及之後交易會加減。之前的歷史交易只供報表，不再次加減。\n期初設定若填錯，可在帳戶流水修正；請先匯出備份，修正會重算餘額。",14,Color.DKGRAY));
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("設定資金帳戶").setView(scroll).setNegativeButton("取消",null).setPositiveButton("預覽",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try{
            String pid=type.getSelectedItemPosition()==1||property.getSelectedItemPosition()==0?"":properties.getJSONObject(property.getSelectedItemPosition()-1).getString("id");
            JSONObject row=Bookkeeping.account(requiredText(name),type.getSelectedItem().toString(),dateValue(start),amount(opening,false),pid);
            JSONArray proposed=data("accounts");proposed.put(row);Bookkeeping.validate(fullBackup().put("accounts",proposed));
            new AlertDialog.Builder(this).setMessage(display(String.valueOf(row.getString("name")+"\n起算日 "+row.getString("start")+"\n當日開始前餘額 $"+row.getLong("opening")+"\n請確認日期與餘額，這不是一筆收入。"))).setNegativeButton("修改",null).setPositiveButton("確認設定",(d,b)->{try{
                JSONArray latest=data("accounts");for(int i=0;i<latest.length();i++)if(latest.getJSONObject(i).getString("id").equals(row.getString("id")))return;latest.put(row);
                if(saveChecked("accounts",latest)){dialog.dismiss();render();}
            }catch(Exception e){error(e);}}).show();
        }catch(Exception e){error(e);}}));dialog.show();
    }
    private static final int VOICE_INPUT=45;
    private void addDraftAccountPlaceholder(List<JSONObject> accounts,JSONObject draft) {
        if(draft==null)return;accounts.add(0,new JSONObject());try{accounts.get(0).put("id","").put("name","請選實際帳戶").put("type","待選");}catch(Exception e){throw new IllegalStateException(e);}
    }
    private JSONObject selectedDraftAccount(List<JSONObject> accounts,Spinner spinner) throws JSONException {JSONObject row=accounts.get(spinner.getSelectedItemPosition());if(row.getString("id").isEmpty())throw new IllegalArgumentException("請選擇"+String.valueOf(spinner.getTag()));return row;}
    private void selectDraftAccount(Spinner spinner,List<JSONObject> accounts,String id) {spinner.setSelection(0);if(!id.isEmpty())for(int i=0;i<accounts.size();i++)if(accounts.get(i).optString("id").equals(id))spinner.setSelection(i);}
    private void setDraftAmount(EditText input,JSONObject draft){input.setText(draft.isNull("amount")?"":draft.optString("amount"));}
    private void selectDraftChoice(Spinner spinner,String value) {for(int i=0;i<spinner.getCount();i++)if(value.equals(spinner.getItemAtPosition(i).toString()))spinner.setSelection(i);}
    private void voiceEditor(String initial) {
        LinearLayout layout=form();layout.addView(text("說完先核對文字，再整理帳務草稿。所有草稿都需確認後才會入帳；語音服務依手機提供，可能需要網路。",14,Color.DKGRAY));
        EditText input=field(layout,"語音文字／可手動修改");input.setSingleLine(false);input.setMinLines(3);input.setText(initial.isEmpty()?getSharedPreferences("workflow",MODE_PRIVATE).getString("voiceText",""):initial);
        layout.addView(text("例：今天A棟修繕總共三千元，從共用現金付款。\n例：今天收到R1401租金一萬元，匯入A帳戶。\n例：今天從A帳戶提款一萬元到共用現金。\n帳戶與棟名請用App裡的代稱；一次說一筆。",14,Color.DKGRAY));
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("語音記帳／文字草稿").setView(scroll).setNegativeButton("取消",null).setNeutralButton("🎤 開始說話",null).setPositiveButton("整理草稿",null).create();
        dialog.setOnShowListener(v->{dialog.getButton(-3).setOnClickListener(w->{getSharedPreferences("workflow",MODE_PRIVATE).edit().putString("voiceText",input.getText().toString()).commit();try{
            Intent voice=new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE,"zh-TW").putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS,3).putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT,"一次說一筆：日期、類型、金額、棟別／房號與帳戶");startActivityForResult(voice,VOICE_INPUT);dialog.dismiss();
        }catch(android.content.ActivityNotFoundException e){error(new IllegalArgumentException("手機未提供語音辨識，可使用鍵盤語音輸入或直接填文字"));}});
            dialog.getButton(-1).setOnClickListener(w->{try{JSONObject draft=VoiceDrafts.parse(input.getText().toString(),today(),fullBackup());getSharedPreferences("workflow",MODE_PRIVATE).edit().putString("voiceText",input.getText().toString()).commit();if(draft.getBoolean("blocked"))throw new IllegalArgumentException("押金／退款／退租請用專用入口；提款含手續費請拆成轉帳及支出兩筆。請修改文字後重試。");voiceDraftReview(draft,dialog);}catch(Exception e){error(e);}});});dialog.show();
    }
    private void voiceDraftReview(JSONObject draft,AlertDialog source) throws JSONException {
        LinearLayout layout=form();layout.addView(text("辨識文字：\n"+draft.getString("raw"),16,Color.DKGRAY));Spinner kind=choice(layout,"這筆帳務類型",new String[]{"請選類型","支出／房貸扣款","收到租金","提款／轉帳"});kind.setSelection(draft.optString("kind").equals("支出")?1:draft.optString("kind").equals("收款")?2:draft.optString("kind").equals("轉帳")?3:0);
        layout.addView(text("草稿日期："+(draft.optString("date").isEmpty()?"待補選":draft.optString("date"))+"\n總金額："+(draft.isNull("amount")?"待填":"$"+draft.optLong("amount"))+"\n分類："+(draft.optString("category").isEmpty()?"待選":draft.optString("category"))+"\n付款／收款帳戶："+(draft.optString("accountId").isEmpty()?"待選":accountName(draft.optString("accountId")))+"\n轉出："+(draft.optString("fromId").isEmpty()?"待選":accountName(draft.optString("fromId")))+"\n轉入："+(draft.optString("toId").isEmpty()?"待選":accountName(draft.optString("toId"))),15,Color.DKGRAY));
        StringBuilder split=new StringBuilder("分攤草稿：\n");JSONArray ids=draft.getJSONArray("propertyIds"),splits=draft.getJSONArray("splits");for(int i=0;i<ids.length();i++)split.append(propertyName(ids.getString(i))).append("\n");for(int i=0;i<splits.length();i++)split.append(propertyName(splits.getJSONObject(i).getString("propertyId"))).append(" $").append(splits.getJSONObject(i).getLong("amount")).append("\n");if(ids.length()==0)split.append("待選不動產／逐棟填寫\n");layout.addView(text(split.toString(),15,Color.DKGRAY));
        List<JSONObject> leases=new ArrayList<>();String[] options;JSONArray all=data("leases");for(int i=0;i<all.length();i++)if(!all.getJSONObject(i).has("terminatedOn"))leases.add(all.getJSONObject(i));options=new String[leases.size()+1];options[0]="請選租約（收到租金才需要）";for(int i=0;i<leases.size();i++){JSONObject l=leases.get(i),unit=find("units",l.optString("unitId"));options[i+1]=(unit==null?"":propertyName(unit.optString("propertyId"))+" · ")+l.optString("unitCodeSnapshot")+" · "+l.optString("tenant")+" · "+l.optString("start");}
        Spinner lease=choice(layout,"收租房號／租客",options);for(int i=0;i<leases.size();i++)if(leases.get(i).optString("id").equals(draft.optString("leaseId")))lease.setSelection(i+1);
        StringBuilder warnings=new StringBuilder("需要核對：\n");JSONArray ws=draft.getJSONArray("warnings");for(int i=0;i<ws.length();i++)warnings.append("• ").append(ws.getString(i)).append("\n");warnings.append("下一頁仍可修改日期、金額、帳戶和分攤；不會直接入帳。");layout.addView(text(warnings.toString(),14,Color.rgb(137,83,15)));
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("檢查語音記帳草稿").setView(scroll).setNegativeButton("返回修改",null).setPositiveButton("帶入表單",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try{int selected=kind.getSelectedItemPosition();if(selected==0)throw new IllegalArgumentException("請選擇帳務類型");if(selected==2&&lease.getSelectedItemPosition()==0)throw new IllegalArgumentException("請選擇收租房號與租客");draft.put("kind",selected==1?"支出":selected==2?"收款":"轉帳");dialog.dismiss();source.dismiss();if(selected==1)expenseForm(draft);else if(selected==3)transferForm(draft);else receiptForm(leases.get(lease.getSelectedItemPosition()-1),draft);}catch(Exception e){error(e);}}));dialog.show();
    }
    private void voiceResult(int result,Intent intent) {
        if(result!=RESULT_OK||intent==null){voiceEditor("");return;}ArrayList<String> results=intent.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);if(results==null||results.isEmpty()){Toast.makeText(this,"未辨識到文字，請重試或手動輸入",Toast.LENGTH_LONG).show();voiceEditor("");return;}
        if(results.size()==1){voiceEditor(results.get(0));return;}
        new AlertDialog.Builder(this).setTitle("選擇正確的語音文字").setItems(results.toArray(new String[0]),(d,n)->voiceEditor(results.get(n))).setNegativeButton("返回草稿",(d,n)->voiceEditor("")).show();
    }

    private void expenseForm() {
        new AlertDialog.Builder(this).setTitle("記錄支出／房貸扣款")
            .setItems(new String[]{"手動填寫支出","🎤 語音輸入支出（先核對草稿）"},(d,n)->{
                if(n==0)expenseForm(null);else voiceEditor("");
            }).setNegativeButton("取消",null).show();
    }
    private void expenseForm(JSONObject draft) {
        List<JSONObject> accounts=accountRows();JSONArray properties=data("properties");
        if(accounts.isEmpty()||properties.length()==0){error(new IllegalArgumentException("請先建立不動產與付款帳戶"));return;}
        addDraftAccountPlaceholder(accounts,draft);
        LinearLayout layout=form();EditText date=dateField(layout,"實際付款日期",false);date.setText(today().toString());Spinner account=accountChoice(layout,accounts,"付款來源");
        String[] categories=Bookkeeping.CATEGORIES;if(draft!=null){categories=new String[Bookkeeping.CATEGORIES.length+1];categories[0]="請選支出分類";System.arraycopy(Bookkeeping.CATEGORIES,0,categories,1,Bookkeeping.CATEGORIES.length);}
        Spinner category=choice(layout,"支出類別",categories);EditText description=labeledField(layout,"項目／備註，例如B、C棟燈具更換");EditText total=labeledNumber(layout,"總付款金額");
        layout.addView(text("各棟分攤金額：不分攤的留空。總和需等於付款；房貸只記每月總扣款，不拆本金利息。",14,Color.DKGRAY));
        List<EditText> fields=new ArrayList<>();for(int i=0;i<properties.length();i++)fields.add(labeledNumber(layout,properties.optJSONObject(i).optString("name")));
        if(draft!=null){date.setText(draft.optString("date"));setDraftAmount(total,draft);description.setText(draft.optString("raw"));selectDraftAccount(account,accounts,draft.optString("accountId"));selectDraftChoice(category,draft.optString("category"));try{JSONArray splits=draft.getJSONArray("splits"),ids=draft.getJSONArray("propertyIds");for(int i=0;i<fields.size();i++){String id=properties.optJSONObject(i).optString("id");for(int j=0;j<splits.length();j++)if(splits.getJSONObject(j).getString("propertyId").equals(id))fields.get(i).setText(splits.getJSONObject(j).optString("amount"));if(splits.length()==0&&ids.length()==1&&ids.getString(0).equals(id)&&!draft.isNull("amount"))fields.get(i).setText(draft.optString("amount"));}}catch(Exception e){error(e);}}
        TextView sumLabel=text("分攤合計：待輸入",16,Color.rgb(90,61,37));layout.addView(sumLabel);
        TextWatcher watch=new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){try{long sum=0;for(EditText f:fields){long n=amount(f,true);if(n>0)sum+=n;}long paid=amount(total,true);sumLabel.setText("分攤合計 $"+sum+(paid<0?"":" · 差額 $"+(paid-sum)));}catch(Exception e){sumLabel.setText("金額格式不正確");}}public void afterTextChanged(Editable e){}};
        total.addTextChangedListener(watch);for(EditText f:fields)f.addTextChangedListener(watch);
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("支出與分攤").setView(scroll).setNegativeButton("取消",null).setPositiveButton("預覽",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try{
            JSONArray splits=new JSONArray();for(int i=0;i<fields.size();i++){long n=amount(fields.get(i),true);if(n>0)splits.put(new JSONObject().put("propertyId",properties.getJSONObject(i).getString("id")).put("amount",n));}
            if(category.getSelectedItem().toString().equals("請選支出分類"))throw new IllegalArgumentException("請選擇支出類別");
            JSONObject a=selectedDraftAccount(accounts,account);JSONObject row=Bookkeeping.expense(dateValue(date),a.getString("id"),category.getSelectedItem().toString(),requiredText(description),amount(total,false),splits);
            StringBuilder preview=new StringBuilder(row.getString("date")+" · "+row.getString("category")+"\n"+row.getString("description")+"\n付款："+a.getString("name")+" $"+row.getLong("amount"));
            for(int i=0;i<splits.length();i++)preview.append("\n").append(propertyName(splits.getJSONObject(i).getString("propertyId"))).append(" $").append(splits.getJSONObject(i).getLong("amount"));
            preview.append(LocalDate.parse(row.getString("date")).isBefore(LocalDate.parse(a.getString("start")))?"\n起算日前歷史：不再扣期初餘額。":"\n只扣付款帳戶一次，不因分攤重複扣款。");
            new AlertDialog.Builder(this).setMessage(display(String.valueOf(preview))).setNegativeButton("修改",null).setPositiveButton("確認支出",(d,b)->{try{
                JSONArray rows=data("expenses");for(int i=0;i<rows.length();i++){JSONObject old=rows.getJSONObject(i);if(old.getString("id").equals(row.getString("id")))return;
                    if(!old.optBoolean("void")&&old.getString("date").equals(row.getString("date"))&&old.getString("accountId").equals(row.getString("accountId"))&&old.getString("category").equals(row.getString("category"))&&old.getString("description").equals(row.getString("description"))&&old.getLong("amount")==row.getLong("amount"))throw new IllegalArgumentException("相同日期、帳戶、項目與金額已有紀錄，請核對；若為另一筆請填交易辨識。");}
                rows.put(row);if(saveChecked("expenses",rows)){dialog.dismiss();render();}
            }catch(Exception e){error(e);}}).show();
        }catch(Exception e){error(e);}}));dialog.show();
    }
    private void transferForm() {transferForm(null);}
    private void transferForm(JSONObject draft) {
        List<JSONObject> accounts=accountRows();if(accounts.size()<2){error(new IllegalArgumentException("需先建立至少兩個帳戶，例如銀行與共用現金"));return;}
        addDraftAccountPlaceholder(accounts,draft);
        LinearLayout layout=form();EditText date=dateField(layout,"轉帳／提款日期",false);date.setText(today().toString());Spinner from=accountChoice(layout,accounts,"轉出帳戶"),to=accountChoice(layout,accounts,"轉入帳戶");to.setSelection(1);
        EditText value=labeledNumber(layout,"轉帳金額"),note=labeledField(layout,"備註，例如A帳戶提領零用金");layout.addView(text("銀行提款時選銀行→共用現金；不增加收入或支出。提款手續費請另外記支出。",14,Color.DKGRAY));
        if(draft!=null){date.setText(draft.optString("date"));setDraftAmount(value,draft);note.setText(draft.optString("raw"));selectDraftAccount(from,accounts,draft.optString("fromId"));selectDraftAccount(to,accounts,draft.optString("toId"));}
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("資金轉移").setView(scroll).setNegativeButton("取消",null).setPositiveButton("預覽",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try{
            JSONObject a=selectedDraftAccount(accounts,from),b=selectedDraftAccount(accounts,to);JSONObject row=Bookkeeping.transfer(dateValue(date),a.getString("id"),b.getString("id"),amount(value,false),note.getText().toString());
            boolean historyFrom=LocalDate.parse(row.getString("date")).isBefore(LocalDate.parse(a.getString("start"))),historyTo=LocalDate.parse(row.getString("date")).isBefore(LocalDate.parse(b.getString("start")));
            new AlertDialog.Builder(this).setMessage(display(String.valueOf(row.getString("date")+"\n"+a.getString("name")+" → "+b.getString("name")+"\n金額 $"+row.getLong("amount")+"\n這是資金移動，不是支出。"+(historyFrom||historyTo?"\n早於起算日的帳戶不再加減，請確認各帳戶期初日期。":"")))).setNegativeButton("修改",null).setPositiveButton("確認轉帳",(d,w2)->{try{
                JSONArray rows=data("transfers");for(int i=0;i<rows.length();i++){JSONObject old=rows.getJSONObject(i);if(old.getString("id").equals(row.getString("id")))return;
                    if(!old.optBoolean("void")&&old.getString("date").equals(row.getString("date"))&&old.getString("fromId").equals(row.getString("fromId"))&&old.getString("toId").equals(row.getString("toId"))&&old.getLong("amount")==row.getLong("amount")&&old.getString("description").equals(row.getString("description")))throw new IllegalArgumentException("已有相同轉帳，請核對避免重複；另一筆請填交易辨識。");}
                rows.put(row);if(saveChecked("transfers",rows)){dialog.dismiss();render();}
            }catch(Exception e){error(e);}}).show();
        }catch(Exception e){error(e);}}));dialog.show();
    }
    private void voidMovement(String key,JSONObject row) {
        LinearLayout layout=form();EditText reason=labeledField(layout,"錯誤原因，實際退款請勿使用撤銷");
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("撤銷錯誤紀錄").setView(layout).setNegativeButton("取消",null).setPositiveButton("確認撤銷",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try{
            if(reason.getText().toString().trim().isEmpty())throw new IllegalArgumentException("請填撤銷原因");JSONArray rows=data(key);
            for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("id").equals(row.getString("id")))rows.getJSONObject(i).put("void",true).put("voidReason",reason.getText().toString().trim()).put("voidDate",today().toString());
            if(saveChecked(key,rows)){dialog.dismiss();render();}
        }catch(Exception e){error(e);}}));dialog.show();
    }
    private int unlinkedReceipts() {JSONArray rows=data("receipts");int n=0;for(int i=0;i<rows.length();i++)if(rows.optJSONObject(i).optBoolean("cash",true)&&rows.optJSONObject(i).optString("accountId").isEmpty())n++;return n;}
    private void showPanel(String title,LinearLayout layout) {showPanel(title,layout,null);}
    private void showPanel(String title,LinearLayout layout,Runnable refresh) {
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("關閉",null).create();
        if(refresh!=null)livePanels.put(dialog,()->{int y=scroll.getScrollY();refresh.run();scroll.post(()->scroll.scrollTo(0,y));});
        dialog.setOnDismissListener(d->livePanels.remove(dialog));dialog.show();
    }
    private void accountsDialog() {
        LinearLayout layout=form();Runnable refresh=()->drawAccounts(layout);refresh.run();showPanel("資金帳戶",layout,refresh);
    }
    private void drawAccounts(LinearLayout layout) {
        layout.removeAllViews();LinearLayout previous=body;body=layout;
        try{
            card("帳戶與共用現金","餘額依已登錄的實際收款、支出及轉帳計算。押金收付另計資金、不列租金；未對應的舊收款 "+unlinkedReceipts()+" 筆不會算入任何帳戶。\n負餘額可能表示資料未補齊，請核對存摺。");
            for(JSONObject account:accountRows()) {
                String amount;try{amount="$"+balance(account,today().toString());}catch(IllegalArgumentException e){amount="起算日在未來，今日不計算";}
                tapCard(account.getString("name"),"帳面餘額 "+amount+"\n起算 "+account.getString("start")+" · 期初 $"+account.getLong("opening"),()->accountStatement(account));
            }
        }catch(Exception e){error(e);}finally{body=previous;}
    }
    private void accountStatement(JSONObject account) {
        LinearLayout layout=form();EditText date=dateField(layout,"查看／對帳日期（當日結束餘額）",false);date.setText(today().toString());
        Button edit=new Button(this);edit.setText("修正期初設定（填錯時使用）");edit.setOnClickListener(v->editOpening(find("accounts",account.optString("id"))));layout.addView(edit);
        TextView summary=text("",16,Color.rgb(90,61,37));layout.addView(summary);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);layout.addView(list);
        Runnable refresh=()->{
            list.removeAllViews();LinearLayout previous=body;body=list;
            try{
                JSONObject currentAccount=find("accounts",account.optString("id"));String end=dateValue(date);long book=balance(currentAccount,end);String start=currentAccount.getString("start"),id=currentAccount.getString("id");
                summary.setText("帳面餘額 $"+book+"\n未對應帳戶的舊收款 "+unlinkedReceipts()+" 筆；實際餘額以存摺／點算現金核對。\n起算日前紀錄不再影響餘額。");
                action("輸入存摺／實際現金餘額對帳",()->reconciliationForm(currentAccount,end));
                List<JSONObject> lines=new ArrayList<>();
                JSONArray receipts=data("receipts"),expenses=data("expenses"),transfers=data("transfers");
                for(int i=0;i<receipts.length();i++){JSONObject r=receipts.getJSONObject(i);if(r.optBoolean("cash",true)&&id.equals(r.optString("accountId")))lines.add(new JSONObject().put("date",r.getString("date")).put("label","收款 "+receiptTenant(r)+" · "+r.getString("account")).put("delta",r.getLong("amount")).put("note",r.optString("note")));}
                for(int i=0;i<expenses.length();i++){JSONObject r=expenses.getJSONObject(i);if(id.equals(r.getString("accountId"))&&!r.optBoolean("void"))lines.add(new JSONObject().put("date",r.getString("date")).put("label",r.getString("category")).put("delta",-r.getLong("amount")).put("note",r.getString("description")).put("sourceKey","expenses").put("sourceId",r.getString("id")));}
                for(int i=0;i<transfers.length();i++){JSONObject r=transfers.getJSONObject(i);if(r.optBoolean("void"))continue;boolean from=id.equals(r.getString("fromId")),to=id.equals(r.getString("toId"));if(from||to)lines.add(new JSONObject().put("date",r.getString("date")).put("label","轉帳 "+accountName(r.getString("fromId"))+" → "+accountName(r.getString("toId"))).put("delta",from?-r.getLong("amount"):r.getLong("amount")).put("note",r.getString("description")).put("sourceKey","transfers").put("sourceId",r.getString("id")));}
                JSONArray deposits=data("deposits");for(int i=0;i<deposits.length();i++){JSONObject r=deposits.getJSONObject(i);if(r.getString("kind").equals("押金抵付費用")||r.getBoolean("void")||!id.equals(r.getString("accountId")))continue;
                    lines.add(new JSONObject().put("date",r.getString("date")).put("label",r.getString("kind")+" · "+receiptTenant(r)).put("delta",r.getString("kind").equals("收到押金")?r.getLong("amount"):-r.getLong("amount")).put("note",r.getString("note")).put("sourceKey","deposits").put("sourceId",r.getString("id")));}
                JSONArray settlements=data("settlements");for(int i=0;i<settlements.length();i++){JSONObject st=settlements.getJSONObject(i);JSONArray payments=st.getJSONArray("payments");for(int j=0;j<payments.length();j++){JSONObject p=payments.getJSONObject(j);if(id.equals(p.getString("accountId")))lines.add(new JSONObject().put("date",p.getString("date")).put("label","退租結算補繳 · "+st.getString("unitCodeSnapshot")+" · "+st.getString("tenantSnapshot")).put("delta",p.getLong("amount")).put("note",p.getString("note")));}}
                lines.sort(Comparator.comparing((JSONObject r)->r.optString("date")).reversed());int n=0;
                for(JSONObject r:lines) {if(LocalDate.parse(r.getString("date")).isAfter(LocalDate.parse(end)))continue;long delta=r.getLong("delta");card(r.getString("date")+" · "+r.getString("label"),(delta>=0?"+":"")+fmt(delta)+"\n"+r.getString("note")+(LocalDate.parse(r.getString("date")).isBefore(LocalDate.parse(start))?"\n歷史補登，未加減期初餘額":""));n++;if(r.has("sourceKey")){JSONObject source=find(r.optString("sourceKey"),r.optString("sourceId"));if(source!=null&&!(r.optString("sourceKey").equals("deposits")&&settled(source.optString("leaseId"))))action("撤銷此筆錯誤紀錄",()->voidMovement(r.optString("sourceKey"),source));}}
                if(n==0)card("尚無此日期前的流水","請記帳或對應舊收款；未有紀錄不代表存摺沒有交易。");
                JSONArray checks=data("reconciliations");for(int i=checks.length()-1;i>=0;i--){JSONObject r=checks.getJSONObject(i);if(!id.equals(r.getString("accountId")))continue;
                    long current=balance(currentAccount,r.getString("date"));card("對帳紀錄 "+r.getString("date"),"實際 $"+r.getLong("actual")+" · 當時帳面 $"+r.getLong("book")+"\n當時差額 $"+(r.getLong("actual")-r.getLong("book"))+"\n依目前登錄重算 $"+current+" · 差額 $"+(r.getLong("actual")-current)+"\n"+r.optString("note"));}
            }catch(Exception e){summary.setText("請輸入有效日期，且不得早於帳戶起算日。");}finally{body=previous;}
        };
        date.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){refresh.run();}public void afterTextChanged(Editable e){}});refresh.run();showPanel(account.optString("name")+" 流水與對帳",layout,refresh);
    }
    private void reconciliationForm(JSONObject account,String date) {
        LinearLayout layout=form();EditText actual=labeledNumber(layout,"存摺當日結束餘額／點算現金");EditText note=labeledField(layout,"核對備註");
        layout.addView(text("只記錄比對差額，不修改帳面、不自動補收入或支出。請先補齊未對應的收款與交易。",14,Color.DKGRAY));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(date+" 對帳").setView(layout).setNegativeButton("取消",null).setPositiveButton("預覽",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try{
            long book=balance(account,date),real=amount(actual,false);JSONObject row=new JSONObject().put("id",UUID.randomUUID().toString()).put("accountId",account.getString("id")).put("date",date).put("actual",real).put("book",book).put("note",note.getText().toString().trim());
            new AlertDialog.Builder(this).setMessage(display(String.valueOf("帳面 $"+book+"\n實際 $"+real+"\n差額（實際－帳面）$"+(real-book)+"\n保存不會改變任何餘額。"))).setNegativeButton("修改",null).setPositiveButton("保存核對紀錄",(d,b)->{try{
                if(balance(account,date)!=book)throw new IllegalArgumentException("帳務已改變，請重新核對");JSONArray rows=data("reconciliations");for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("id").equals(row.getString("id")))return;rows.put(row);
                if(saveChecked("reconciliations",rows)){dialog.dismiss();Toast.makeText(this,"對帳紀錄已保存",Toast.LENGTH_LONG).show();}
            }catch(Exception e){error(e);}}).show();
        }catch(Exception e){error(e);}}));dialog.show();
    }
    private void editOpening(JSONObject account) {
        LinearLayout layout=form();EditText date=dateField(layout,"修正起算日",false);date.setText(account.optString("start"));EditText opening=labeledNumber(layout,"修正起算日開始前餘額");opening.setText(account.optString("opening"));EditText reason=labeledField(layout,"修正原因，請先匯出備份");
        layout.addView(text("僅用於期初設定填錯；不是補帳或對帳調整。修改會依新起算日重算所有帳面餘額，原對帳快照保留。",14,Color.DKGRAY));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("修正期初設定").setView(layout).setNegativeButton("取消",null).setPositiveButton("預覽變更",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try{
            String start=dateValue(date);LocalDate.parse(start);long value=amount(opening,false);String note=reason.getText().toString().trim();if(note.isEmpty())throw new IllegalArgumentException("請填修正原因");
            new AlertDialog.Builder(this).setMessage(display(String.valueOf("原起算 "+account.optString("start")+" · 期初 $"+account.optLong("opening")+"\n改為 "+start+" · 期初 $"+value+"\n這會重算帳面，並保留修正原因；不新增交易。"))).setNegativeButton("取消",null).setPositiveButton("確認修正期初",(d,b)->{try{
                JSONArray rows=data("accounts");for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("id").equals(account.getString("id"))){JSONObject r=rows.getJSONObject(i);JSONArray history=r.optJSONArray("openingHistory");if(history==null)history=new JSONArray();history.put(new JSONObject().put("start",r.getString("start")).put("opening",r.getLong("opening")).put("reason",note).put("changedOn",today().toString()));r.put("openingHistory",history).put("start",start).put("opening",value);}
                if(saveChecked("accounts",rows)){dialog.dismiss();render();Toast.makeText(this,"期初已修正",Toast.LENGTH_LONG).show();}
            }catch(Exception e){error(e);}}).show();
        }catch(Exception e){error(e);}}));dialog.show();
    }
    private static final int EXPORT_REPORT=46,EXPORT_WORKBOOK=47;
    private void ownerReport() {
        LinearLayout layout=form();EditText month=dateField(layout,"報表月份",true);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);layout.addView(list);
        Runnable refresh=()->{LinearLayout previous=body;body=list;list.removeAllViews();try {
            JSONObject report=OwnerReport.build(fullBackup(),YearMonth.parse(dateValue(month)));JSONObject total=report.getJSONObject("total");
            card("每月總覽",ownerSummary(total));
            card("報表口徑","帳單應收按帳單月份；抵付狀態截至月底，包含押金抵付。實收按收到日期，租金與水電合併。押金不列收入，提款轉帳不列支出。未建帳單不代表已繳清；此淨收款不是銀行餘額或會計損益。");
            action("立即同步此月份到Google試算表",()->googleSync(report,false));
            action("匯出Excel報表（千位逗點／分工作表）",()->exportOwnerWorkbook(report));
            action("匯出此月份CSV（原始資料）",()->exportOwnerReport(report));
            JSONArray properties=report.getJSONArray("properties");for(int i=0;i<properties.length();i++){JSONObject row=properties.getJSONObject(i);card(row.getString("name"),ownerSummary(row));}
            JSONArray tenants=report.getJSONArray("tenants");for(int i=0;i<tenants.length();i++){JSONObject row=tenants.getJSONObject(i);card(propertyName(row.getString("propertyId"))+" · "+row.getString("code")+" · "+row.getString("name"),row.getString("status")+"\n本月已建帳單 $"+row.getLong("billed")+" · 月底已抵付 $"+row.getLong("paid")+"\n本月帳單未付 $"+row.getLong("remaining")+"\n本月租金水電實收 $"+row.getLong("cash")+" · 結算補繳 $"+row.getLong("topups")+"\n月底持有押金 $"+row.getLong("held"));}
        }catch(Exception e){card("報表無法產生","請檢查帳務資料："+e.getMessage());}finally{body=previous;}};
        month.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){refresh.run();}public void afterTextChanged(Editable e){}});refresh.run();showPanel("爸爸的每月總報表",layout,refresh);
    }
    private String ownerSummary(JSONObject row)throws JSONException{return "本月已建帳單 $"+row.getLong("billed")+" · "+row.getInt("invoiceCount")+" 筆\n截至月底已抵付 $"+row.getLong("paid")+" · 本月帳單未付 $"+row.getLong("remaining")+"\n本月租金水電實收 $"+row.getLong("cash")+"\n本月結算補繳實收 $"+row.getLong("topups")+"\n本月分攤支出 $"+row.getLong("expenses")+"\n本月淨收款 $"+row.getLong("net")+"（不含押金及轉帳）\n月底持有押金 $"+row.getLong("held");}
    private String syncTime(String value) {try{return java.time.Instant.parse(value).atZone(ZoneId.of("Asia/Taipei")).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));}catch(Exception e){return "尚未同步";}}
    private void googleSyncSettings() {
        LinearLayout layout=form();EditText url=field(layout,"Google部署網址（/exec）"),token=field(layout,"App同步金鑰");token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);token.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        try {JSONObject settings=SyncSettings.load(this);if(settings!=null){url.setText(settings.getString("url"));token.setText(settings.getString("token"));}}catch(Exception e){layout.addView(text("設定無法解密，請重新填寫連線資訊。",14,Color.DKGRAY));}
        android.content.SharedPreferences prefs=getSharedPreferences("googleSync",MODE_PRIVATE);layout.addView(text("第一版需手動同步房東月報。Google端先部署專案內google-sync服務，執行初始化後把部署網址及金鑰填在這裡。金鑰只保存在手機，不放完整備份或GitHub。",14,Color.DKGRAY));layout.addView(text("最後成功月份："+prefs.getString("lastMonth","尚未同步")+"\n最後成功時間："+syncTime(prefs.getString("lastSuccess",""))+"\n最近結果："+prefs.getString("status","尚未測試連線"),14,Color.DKGRAY));
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Google同步設定／狀態").setView(scroll).setNegativeButton("關閉",null).setNeutralButton("測試連線",null).setPositiveButton("保存設定",null).create();dialog.setOnShowListener(v->{Runnable save=()->{try{if(googleSyncRunning)throw new IllegalArgumentException("同步正在執行，請完成後再變更設定");String endpoint=requiredText(url),secret=requiredText(token);JSONObject current=null;try{current=SyncSettings.load(this);}catch(Exception ignored){}if(current==null||!endpoint.equals(current.optString("url"))||!secret.equals(current.optString("token")))SyncSettings.save(this,endpoint,secret);dialog.dismiss();render();}catch(Exception e){error(e);}};dialog.getButton(-1).setOnClickListener(w->save.run());dialog.getButton(-3).setOnClickListener(w->{save.run();if(!dialog.isShowing())googleSync(null,true);});});dialog.show();
    }
    private void googleSync(JSONObject report,boolean checkOnly) {
        if(googleSyncRunning){error(new IllegalArgumentException("同步正在執行，請稍候"));return;}JSONObject settings;try{settings=SyncSettings.load(this);if(settings==null){googleSyncSettings();return;}}catch(Exception e){error(new IllegalArgumentException("同步設定無法讀取，請重新設定"));return;}
        googleSyncRunning=true;String configuration=getSharedPreferences("googleSync",MODE_PRIVATE).getString("connection","");AlertDialog progress=busy(checkOnly?"正在測試Google連線…":"正在同步選定月份報表…");new Thread(()->{try{
            JSONObject response=SyncProtocol.send(settings.getString("url"),settings.getString("token"),checkOnly?"check":"sync",report);android.content.SharedPreferences prefs=getSharedPreferences("googleSync",MODE_PRIVATE);if(!configuration.equals(prefs.getString("connection","")))throw new IOException("同步設定已變更，請重新測試連線");android.content.SharedPreferences.Editor edit=prefs.edit().putString("reportUrl",response.getString("url")).putString("status",checkOnly?"連線成功，尚未同步本次報表":"成功同步選定月份（手機後續變動需再同步）");if(!checkOnly)edit.putString("lastMonth",response.getString("month")).putString("lastSuccess",response.getString("syncedAt"));edit.commit();runOnUiThread(()->{googleSyncRunning=false;if(isFinishing()||isDestroyed())return;progress.dismiss();new AlertDialog.Builder(this).setMessage(checkOnly?"連線成功，未上傳帳務。回到房東月報選月份後，點立即同步。":"此月份房東報表已更新；之後修改帳務需再同步。租客雲端報表尚未啟用。").setNegativeButton("關閉",null).setPositiveButton("開啟試算表",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(response.optString("url"))));}catch(Exception e){error(new IllegalArgumentException("手機沒有可開啟試算表的App"));}}).show();});
        }catch(Exception e){String message=e instanceof java.net.SocketTimeoutException?"同步逾時；Google可能已收到，請核對試算表再重試，同月份重試不會追加交易。":e.getMessage();getSharedPreferences("googleSync",MODE_PRIVATE).edit().putString("status",message==null?"同步失敗，請檢查網路及部署設定":message).commit();runOnUiThread(()->{googleSyncRunning=false;if(isFinishing()||isDestroyed())return;progress.dismiss();error(new IllegalArgumentException(message==null?"同步失敗，請檢查網路及部署設定":message));});}
        }).start();
    }
    private void exportOwnerWorkbook(JSONObject report) {
        try {if(!getSharedPreferences("workflow",MODE_PRIVATE).edit().putString("pendingWorkbook",report.toString()).commit())throw new IOException("報表快照保存失敗");Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").putExtra(Intent.EXTRA_TITLE,"rental-owner-"+report.getString("month")+".xlsx");startActivityForResult(intent,EXPORT_WORKBOOK);}catch(Exception e){error(e);}
    }
    private void writeOwnerWorkbook(Uri uri) {
        String snapshot=getSharedPreferences("workflow",MODE_PRIVATE).getString("pendingWorkbook",null);if(snapshot==null){error(new IllegalArgumentException("報表快照遺失，請重新匯出"));return;}AlertDialog progress=busy("正在匯出Excel報表…");new Thread(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("無法開啟檔案");OwnerWorkbook.write(out,new JSONObject(snapshot));runOnUiThread(()->{progress.dismiss();getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingWorkbook").commit();Toast.makeText(this,"Excel報表已匯出，金額含千位逗點格式",Toast.LENGTH_LONG).show();});}catch(Exception e){runOnUiThread(()->{progress.dismiss();error(new IllegalArgumentException("Excel匯出失敗，請刪除未完成檔案並重試："+e.getMessage()));});}}).start();
    }
    private void exportOwnerReport(JSONObject report) {
        try {String csv=OwnerReport.csv(report);if(!getSharedPreferences("workflow",MODE_PRIVATE).edit().putString("pendingReport",csv).commit())throw new IOException("報表快照保存失敗");Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/csv").putExtra(Intent.EXTRA_TITLE,"rental-owner-"+report.getString("month")+".csv");startActivityForResult(intent,EXPORT_REPORT);}catch(Exception e){error(e);}
    }
    private void writeOwnerReport(Uri uri) {
        String csv=getSharedPreferences("workflow",MODE_PRIVATE).getString("pendingReport",null);if(csv==null){error(new IllegalArgumentException("報表快照遺失，請重新匯出"));return;}AlertDialog progress=busy("正在匯出報表…");new Thread(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("無法開啟檔案");out.write(csv.getBytes(StandardCharsets.UTF_8));runOnUiThread(()->{progress.dismiss();getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingReport").commit();Toast.makeText(this,"CSV已匯出；可自行匯入Google試算表",Toast.LENGTH_LONG).show();});}catch(Exception e){runOnUiThread(()->{progress.dismiss();error(new IllegalArgumentException("報表匯出失敗，請刪除未完成檔案並重試："+e.getMessage()));});}}).start();
    }
    private void receiptAccountForm(JSONObject receipt,List<JSONObject> accounts) {
        LinearLayout layout=form();Spinner selected=accountChoice(layout,accounts,"核對後的實際收款帳戶");for(int i=0;i<accounts.size();i++)if(accounts.get(i).optString("id").equals(receipt.optString("accountId")))selected.setSelection(i);
        layout.addView(text("原記錄："+receipt.optString("account")+"\n目前對應："+accountName(receipt.optString("accountId"))+"\n只更正帳戶關聯，不重複收款或改帳單分配。",14,Color.DKGRAY));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("對應／更正收款帳戶").setView(layout).setNegativeButton("取消",null).setPositiveButton("確認核對後帳戶",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try{
            JSONArray latest=data("receipts");for(int j=0;j<latest.length();j++)if(latest.getJSONObject(j).getString("id").equals(receipt.getString("id")))latest.getJSONObject(j).put("accountId",accounts.get(selected.getSelectedItemPosition()).getString("id"));
            if(saveChecked("receipts",latest)){dialog.dismiss();render();Toast.makeText(this,"帳戶對應已保存",Toast.LENGTH_LONG).show();}
        }catch(Exception e){error(e);}}));dialog.show();
    }
    private void linkReceipts() {
        List<JSONObject> accounts=accountRows();if(accounts.isEmpty()){error(new IllegalArgumentException("請先建立帳戶"));return;}
        LinearLayout layout=form();layout.addView(text("逐筆核對存摺後選擇實際收款帳戶。不猜帳戶、不新增收款；起算日前不再加減餘額。",14,Color.DKGRAY));
        CheckBox include=new CheckBox(this);include.setText("也顯示已對應收款（可更正帳戶）");layout.addView(include);EditText search=field(layout,"搜尋日期、原帳戶文字或金額");
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);layout.addView(list);
        Runnable refresh=()->{list.removeAllViews();LinearLayout previous=body;body=list;try{
            JSONArray receipts=data("receipts");int count=0;for(int i=receipts.length()-1;i>=0;i--){JSONObject r=receipts.getJSONObject(i);if(!r.optBoolean("cash",true))continue;if(!include.isChecked()&&!r.optString("accountId").isEmpty())continue;
                String label=r.getString("date")+" · "+receiptTenant(r)+" · "+r.getString("account")+" · $"+r.getLong("amount");if(!label.contains(search.getText().toString().trim()))continue;count++;
                tapCard(label,"目前對應："+accountName(r.optString("accountId")),()->receiptAccountForm(r,accounts));}
            if(count==0)card("沒有符合的收款","已對應收款依實際日期與起算日計入帳面。可勾選顯示已對應紀錄或調整搜尋。");
        }catch(Exception e){error(e);}finally{body=previous;}};
        include.setOnCheckedChangeListener((b,checked)->refresh.run());search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){refresh.run();}public void afterTextChanged(Editable e){}});refresh.run();showPanel("對應／更正收款帳戶",layout,refresh);
    }
    private void expenseReport() {
        LinearLayout layout=form();EditText month=dateField(layout,"支出月份",true);month.setText(YearMonth.from(today()).toString());
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);layout.addView(list);
        Runnable refresh=()->{
            list.removeAllViews();LinearLayout previous=body;body=list;try{
                YearMonth ym=YearMonth.parse(dateValue(month));JSONArray expenses=data("expenses"),properties=data("properties");String start=ym.atDay(1).toString(),end=ym.atEndOfMonth().toString();
                card(ym+" 已登錄支出","合計 $"+Bookkeeping.expenseTotal(expenses,start,end,null)+"\n包含房貸整筆扣款；此為現金支出，不是會計損益。轉帳與已撤銷錯誤不列支出。資料仍在補登，未登錄不表示沒有費用。");
                for(int i=0;i<properties.length();i++){JSONObject p=properties.getJSONObject(i);card(p.getString("name"),"分攤支出 $"+Bookkeeping.expenseTotal(expenses,start,end,p.getString("id")));}
                for(String category:Bookkeeping.CATEGORIES){long sum=0;for(int i=0;i<expenses.length();i++){JSONObject r=expenses.getJSONObject(i);if(!r.optBoolean("void")&&r.getString("date").startsWith(ym.toString())&&category.equals(r.getString("category")))sum+=r.getLong("amount");}card(category,"$"+sum);}
                List<JSONObject> rows=new ArrayList<>();for(int i=0;i<expenses.length();i++){JSONObject r=expenses.getJSONObject(i);if(!r.optBoolean("void")&&r.getString("date").startsWith(ym.toString()))rows.add(r);}rows.sort(Comparator.comparing((JSONObject r)->r.optString("date")).reversed());
                for(JSONObject r:rows){StringBuilder detail=new StringBuilder(accountName(r.getString("accountId"))+"\n"+r.getString("description"));JSONArray splits=r.getJSONArray("splits");for(int i=0;i<splits.length();i++)detail.append("\n").append(propertyName(splits.getJSONObject(i).getString("propertyId"))).append(" $").append(splits.getJSONObject(i).getLong("amount"));
                    LinearLayout box=card(r.getString("date")+" · "+r.getString("category")+" $"+r.getLong("amount"),detail.toString());cardAction(box,"撤銷",()->voidMovement("expenses",r));}
            }catch(Exception e){card("請選擇有效月份","點選月份欄位選取年與月");}finally{body=previous;}
        };
        month.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){refresh.run();}public void afterTextChanged(Editable e){}});refresh.run();showPanel("支出報表",layout,refresh);
    }

    private static final int CAMERA_PHOTO=43, PICK_PHOTO=44;
    private File photoDir() {File dir=new File(getFilesDir(),"meter-photos");if(!dir.isDirectory())dir.mkdirs();return dir;}
    private Uri photoUri(String file) {return new Uri.Builder().scheme("content").authority(getPackageName()+".photos").appendPath(file).build();}
    private void meterPanel(JSONObject lease) {
        LinearLayout layout=form();Runnable refresh=()->{layout.removeAllViews();LinearLayout previous=body;body=layout;try{
            card("抄電表與照片",lease.optString("unitCodeSnapshot")+" · "+lease.optString("tenant")+"\n紀錄不會自動開單；建立帳單時帶入同租約、同月份最新有效讀數。照片可點開檢查。已開帳單不會被改動。");
            action("＋ 新增抄表紀錄",()->meterForm(lease));JSONArray rows=data("meterReadings");for(int i=rows.length()-1;i>=0;i--){JSONObject r=rows.getJSONObject(i);if(!lease.optString("id").equals(r.optString("leaseId")))continue;
                card(r.getString("month")+" · 抄表日 "+r.getString("date"),"電表 "+fmt(r.getLong("previous"))+" → "+fmt(r.getLong("current"))+" · 用量 "+fmt(r.getLong("current")-r.getLong("previous"))+" 度\n"+r.getString("note")+(r.getBoolean("void")?"\n已撤銷錯誤紀錄":""));
                if(!r.getString("file").isEmpty())action("查看電表照片",()->viewMeterPhoto(r.optString("file")));
                if(!r.getBoolean("void"))action("撤銷此筆錯誤抄表",()->{try{JSONArray next=data("meterReadings");for(int j=0;j<next.length();j++)if(next.getJSONObject(j).getString("id").equals(r.getString("id")))next.getJSONObject(j).put("void",true);if(saveChecked("meterReadings",next))render();}catch(Exception e){error(e);}});
            }
        }catch(Exception e){error(e);}finally{body=previous;}};refresh.run();showPanel("抄電表／照片",layout,refresh);
    }
    private void viewMeterPhoto(String name) {
        try{File f=new File(photoDir(),name);if(!f.isFile())throw new IOException("照片缺少，請從完整ZIP備份還原");Intent view=new Intent(Intent.ACTION_VIEW).setDataAndType(photoUri(name),"image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(view);}catch(Exception e){error(new IllegalArgumentException("無法開啟照片，請確認有圖片檢視程式及照片檔案"));}
    }
    private void meterForm(JSONObject lease) {
        LinearLayout layout=form();EditText month=dateField(layout,"用電帳單月份",true),date=dateField(layout,"實際抄表日期",false),prior=number(layout,"上期／起始電表讀數"),current=number(layout,"本期電表讀數"),note=field(layout,"抄表備註");month.setText(YearMonth.from(today()).toString());
        layout.addView(text("照片是讀數依據，請先填前後讀數。換租客的起始讀數請自行確認，不沿用上一位。換表／讀數倒退需另行處理。",14,Color.DKGRAY));
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("新增抄表紀錄").setView(scroll).setNegativeButton("取消",null).setNeutralButton("只存讀數",null).setPositiveButton("附上照片",null).create();
        dialog.setOnShowListener(v->{dialog.getButton(-3).setOnClickListener(w->{try{JSONObject row=MeterReadings.reading(lease,dateValue(month),dateValue(date),amount(prior,false),amount(current,false),"",note.getText().toString());confirmMeterPhoto(row,dialog);}catch(Exception e){error(e);}});
            dialog.getButton(-1).setOnClickListener(w->{try{JSONObject row=MeterReadings.reading(lease,dateValue(month),dateValue(date),amount(prior,false),amount(current,false),UUID.randomUUID()+".jpg",note.getText().toString());
                new AlertDialog.Builder(this).setTitle("電表照片來源").setItems(new String[]{"開啟相機拍照","從相簿／檔案選照片"},(d,n)->{try{
                    getSharedPreferences("workflow",MODE_PRIVATE).edit().putString("pendingMeter",row.toString()).commit();
                    if(n==0){Uri uri=photoUri(row.getString("file"));Intent camera=new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).putExtra(android.provider.MediaStore.EXTRA_OUTPUT,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);camera.setClipData(android.content.ClipData.newRawUri("meter",uri));photoDir();startActivityForResult(camera,CAMERA_PHOTO);}
                    else startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),PICK_PHOTO);dialog.dismiss();
                }catch(Exception e){getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingMeter").commit();error(new IllegalArgumentException("無法啟動相機或相簿，請改選另一個來源"));}}).show();
            }catch(Exception e){error(e);}});});dialog.show();
    }
    private android.graphics.Bitmap photoPreview(File file) throws IOException {
        android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();opts.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(file.getPath(),opts);
        if(opts.outWidth<=0||opts.outHeight<=0||file.length()==0||file.length()>BackupArchive.PHOTO_LIMIT)throw new IOException("照片無效或超過25MB");opts.inJustDecodeBounds=false;opts.inSampleSize=1;while(Math.max(opts.outWidth,opts.outHeight)/opts.inSampleSize>1000)opts.inSampleSize*=2;
        android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(file.getPath(),opts);if(bitmap==null)throw new IOException("無法讀取照片");return bitmap;
    }
    private void confirmMeterPhoto(JSONObject row,AlertDialog source) {
        try{LinearLayout layout=form();layout.addView(text(row.getString("month")+" · "+row.getString("date")+"\n電表 "+fmt(row.getLong("previous"))+" → "+fmt(row.getLong("current"))+"\n"+row.getString("note")+"\n確認只保存抄表紀錄；不直接開帳單。",16,Color.DKGRAY));
            String file=row.getString("file");if(!file.isEmpty()){ImageView preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setImageBitmap(photoPreview(new File(photoDir(),file)));layout.addView(preview,new LinearLayout.LayoutParams(-1,dp(220)));preview.setOnClickListener(v->viewMeterPhoto(file));layout.addView(text("點照片可用圖片程式查看原圖；請核對讀數清楚。",14,Color.DKGRAY));}
            ScrollView scroll=new ScrollView(this);scroll.addView(layout);boolean[] saved={false};AlertDialog confirm=new AlertDialog.Builder(this).setTitle("確認抄表與照片").setView(scroll).setNegativeButton("取消",null).setPositiveButton("儲存",null).create();confirm.setOnDismissListener(d->{if(!saved[0]&&!file.isEmpty())new File(photoDir(),file).delete();getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingMeter").commit();});
            confirm.setOnShowListener(v->confirm.getButton(-1).setOnClickListener(w->{try{JSONArray next=data("meterReadings");for(int i=0;i<next.length();i++)if(next.getJSONObject(i).getString("id").equals(row.getString("id")))return;next.put(row);if(saveChecked("meterReadings",next)){saved[0]=true;confirm.dismiss();if(source!=null)source.dismiss();render();Toast.makeText(this,"抄表紀錄已保存，可開帳單帶入讀數",Toast.LENGTH_LONG).show();}}catch(Exception e){error(e);}}));confirm.show();
        }catch(Exception e){getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingMeter").commit();try{if(!row.getString("file").isEmpty())new File(photoDir(),row.getString("file")).delete();}catch(Exception ignored){}error(e);}
    }
    private void photoResult(int request,int result,Intent intent) {
        String pending=getSharedPreferences("workflow",MODE_PRIVATE).getString("pendingMeter",null);if(pending==null)return;
        try{JSONObject row=new JSONObject(pending);String file=row.getString("file");revokeUriPermission(photoUri(file),Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if(result!=RESULT_OK||(request==PICK_PHOTO&&(intent==null||intent.getData()==null))){new File(photoDir(),file).delete();getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingMeter").commit();return;}
            if(request==CAMERA_PHOTO){confirmMeterPhoto(row,null);return;}
            Uri uri=intent.getData();Toast.makeText(this,"正在讀取照片…",Toast.LENGTH_SHORT).show();new Thread(()->{try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(new File(photoDir(),file))){if(in==null)throw new IOException();byte[] buffer=new byte[8192];int n;long total=0;while((n=in.read(buffer))!=-1){total+=n;if(total>BackupArchive.PHOTO_LIMIT)throw new IOException("照片超過25MB");out.write(buffer,0,n);}runOnUiThread(()->confirmMeterPhoto(row,null));}catch(Exception e){new File(photoDir(),file).delete();getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingMeter").commit();runOnUiThread(()->error(new IllegalArgumentException("照片讀取失敗，請選擇25MB以下圖片")));}}).start();
        }catch(Exception e){error(e);}
    }

    private void exportBackup() {
        try {pendingBackup=fullBackup().toString(2);getSharedPreferences("workflow",MODE_PRIVATE).edit().putString("pendingBackup",pendingBackup).commit();Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip");intent.putExtra(Intent.EXTRA_TITLE,"rental-full-"+today()+".zip");startActivityForResult(intent,EXPORT);}catch(Exception e){error(e);}
    }
    private void importBackup() {
        new AlertDialog.Builder(this).setMessage("還原會替換全部資料及照片參照，請先匯出完整ZIP備份。舊JSON備份仍可讀取，但不含新版抄表照片；舊版本也可能不含押金或結算。").setNegativeButton("取消",null).setPositiveButton("選擇備份檔案",(d,w)->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),IMPORT)).show();
    }
    private void clearStaging(File dir) {File[] files=dir.listFiles();if(files!=null)for(File f:files)f.delete();dir.delete();}
    private AlertDialog busy(String message) {AlertDialog d=new AlertDialog.Builder(this).setMessage(message).setCancelable(false).create();d.show();return d;}
    private void exportArchive(Uri uri) {
        String raw=pendingBackup;if(raw==null)raw=getSharedPreferences("workflow",MODE_PRIVATE).getString("pendingBackup",null);if(raw==null){error(new IllegalArgumentException("備份快照遺失，請重新匯出"));return;}
        final String snapshot=raw;AlertDialog progress=busy("正在匯出帳務與照片，請稍候…");new Thread(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException();BackupArchive.write(out,new JSONObject(snapshot),photoDir());runOnUiThread(()->{progress.dismiss();pendingBackup=null;getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingBackup").commit();Toast.makeText(this,"完整ZIP備份已匯出，包含照片與帳務",Toast.LENGTH_LONG).show();});}catch(Exception e){runOnUiThread(()->{progress.dismiss();error(new IllegalArgumentException("備份匯出失敗："+e.getMessage()+"；請刪除未完成檔案並重新匯出"));});}}).start();
    }
    private void readArchive(Uri uri) {
        File staging=new File(getCacheDir(),"restore-"+UUID.randomUUID());AlertDialog progress=busy("正在檢查備份與照片…");new Thread(()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException();JSONObject backup=BackupArchive.read(in,staging);validateBackup(backup);for(String file:MeterReadings.files(backup.getJSONArray("meterReadings")))photoPreview(new File(staging,file)).recycle();
            runOnUiThread(()->{progress.dismiss();new AlertDialog.Builder(this).setMessage("已驗證備份與照片。確定替換全部資料？舊v1–v5沒有抄表紀錄／照片；v1–v3沒有押金收付；v1–v4沒有退租結算。請保留目前的完整備份。").setNegativeButton("取消",(d,w)->clearStaging(staging)).setOnCancelListener(d->clearStaging(staging)).setPositiveButton("確認還原",(d,w)->restoreArchive(backup,staging)).show();});
        }catch(Exception e){clearStaging(staging);runOnUiThread(()->{progress.dismiss();error(new IllegalArgumentException("備份無效或照片缺少："+e.getMessage()+"；原資料未替換"));});}}).start();
    }
    private void restoreArchive(JSONObject backup,File staging) {
        AlertDialog progress=busy("正在還原帳務與照片…");new Thread(()->{List<File> added=new ArrayList<>();boolean committed=false;try{
            JSONArray rows=backup.getJSONArray("meterReadings");for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);String old=row.getString("file");if(old.isEmpty())continue;String name=UUID.randomUUID()+".jpg";File destination=new File(photoDir(),name);if(!new File(staging,old).renameTo(destination))throw new IOException("照片還原失敗");added.add(destination);row.put("file",name);}
            validateBackup(backup);android.content.SharedPreferences.Editor editor=getSharedPreferences("catalog",MODE_PRIVATE).edit();for(String key:KEYS)editor.putString(key,backup.getJSONArray(key).toString());if(!editor.commit())throw new IOException("帳務保存失敗");committed=true;clearStaging(staging);runOnUiThread(()->{progress.dismiss();propertyId="";unitId="";render();body.post(this::refreshPanels);Toast.makeText(this,"資料與照片已還原",Toast.LENGTH_LONG).show();});
        }catch(Exception e){if(!committed)for(File file:added)file.delete();clearStaging(staging);runOnUiThread(()->{progress.dismiss();error(new IllegalArgumentException("還原失敗："+e.getMessage()));});}}).start();
    }
    private void validateBackup(JSONObject backup) throws Exception {
        if (!"rental-catalog".equals(backup.getString("format")) || (backup.getInt("version") < 1 || backup.getInt("version") > 6))
            throw new IllegalArgumentException();
        if(backup.getInt("version")==1) {backup.put("invoices",new JSONArray());backup.put("receipts",new JSONArray());}
        if(backup.getInt("version")<3)for(String key:new String[]{"accounts","expenses","transfers","reconciliations"})backup.put(key,new JSONArray());
        if(backup.getInt("version")<4)backup.put("deposits",new JSONArray());
        if(backup.getInt("version")<5)backup.put("settlements",new JSONArray());
        if(backup.getInt("version")<6)backup.put("meterReadings",new JSONArray());
        java.util.Set<String> properties = new java.util.HashSet<>(), units = new java.util.HashSet<>(), leases = new java.util.HashSet<>();
        java.util.Set<String> codes = new java.util.HashSet<>();
        JSONArray p = backup.getJSONArray("properties"), u = backup.getJSONArray("units"), l = backup.getJSONArray("leases");
        for (int i=0; i<p.length(); i++) {
            JSONObject item=p.getJSONObject(i);
            if (item.getString("id").isEmpty() || !properties.add(item.getString("id")) || item.getString("name").trim().isEmpty()
                || !(item.getString("type").equals("土地") || item.getString("type").equals("建築物"))) throw new IllegalArgumentException();
        }
        for (int i=0; i<u.length(); i++) {
            JSONObject item=u.getJSONObject(i); String code=item.getString("code").trim();
            if (item.getString("id").isEmpty() || !units.add(item.getString("id")) || !properties.contains(item.getString("propertyId"))
                || code.isEmpty() || !codes.add(item.getString("propertyId")+"|"+code.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException();
            if (!java.util.Arrays.asList("房間","整棟","車格","土地出租範圍","其他空間").contains(item.getString("type"))) throw new IllegalArgumentException();
        }
        for (int i=0; i<l.length(); i++) {
            JSONObject item=l.getJSONObject(i);
            if (item.getString("id").isEmpty() || !leases.add(item.getString("id")) || !units.contains(item.getString("unitId"))
                || item.getString("tenant").trim().isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(item.getString("email")).matches()
                || LocalDate.parse(item.getString("end")).isBefore(LocalDate.parse(item.getString("start")))
                || item.getLong("rent") <= 0 || item.getLong("rent") > 1000000000L || item.getLong("deposit") < 0
                || item.getInt("dueDay") < 1 || item.getInt("dueDay") > 31
                || (!item.isNull("water") && item.getLong("water") < 0)) throw new IllegalArgumentException();
        }
        for(int i=0;i<l.length();i++) {
            JSONObject lease=l.getJSONObject(i);Ledger.money(lease,"rent");Ledger.money(lease,"deposit");Ledger.money(lease,"electricRate");
            if(!lease.isNull("water"))Ledger.money(lease,"water");
            if(!Arrays.asList("月繳","年繳").contains(lease.getString("cycle"))||!Arrays.asList("租客自行繳水電","管理者代收水電").contains(lease.getString("utilities")))throw new IllegalArgumentException();
            JSONObject unit=null;for(int k=0;k<u.length();k++)if(u.getJSONObject(k).getString("id").equals(lease.getString("unitId")))unit=u.getJSONObject(k);
            for(int j=i+1;j<l.length();j++) {
                JSONObject other=l.getJSONObject(j),otherUnit=null;for(int k=0;k<u.length();k++)if(u.getJSONObject(k).getString("id").equals(other.getString("unitId")))otherUnit=u.getJSONObject(k);
                boolean conflict=lease.getString("unitId").equals(other.getString("unitId")) || (unit!=null&&otherUnit!=null&&unit.getString("propertyId").equals(otherUnit.getString("propertyId"))&&(unit.getString("type").equals("整棟")||otherUnit.getString("type").equals("整棟")));
                if(conflict&&Settlements.overlaps(lease,other))throw new IllegalArgumentException("備份租約重疊");
            }
        }
        JSONArray invoices=backup.getJSONArray("invoices");
        for(int i=0;i<invoices.length();i++) {JSONObject invoice=invoices.getJSONObject(i);for(int j=0;j<l.length();j++) {JSONObject lease=l.getJSONObject(j);if(!lease.getString("id").equals(invoice.getString("leaseId")))continue;
            YearMonth month=YearMonth.parse(invoice.getString("month"));
            if(month.atEndOfMonth().isBefore(LocalDate.parse(lease.getString("start")))||month.atDay(1).isAfter(LocalDate.parse(lease.getString("end"))))throw new IllegalArgumentException("帳單月份超出租期");
        }}
        Ledger.validate(invoices,backup.getJSONArray("receipts"),leases);Bookkeeping.validate(backup);
    }
    @Override protected void onActivityResult(int request,int result,Intent intent) {
        super.onActivityResult(request,result,intent);
        if(request==EXPORT_WORKBOOK){if(result==RESULT_OK&&intent!=null&&intent.getData()!=null)writeOwnerWorkbook(intent.getData());else getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingWorkbook").commit();return;}
        if(request==EXPORT_REPORT){if(result==RESULT_OK&&intent!=null&&intent.getData()!=null)writeOwnerReport(intent.getData());else getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingReport").commit();return;}
        if(request==VOICE_INPUT){voiceResult(result,intent);return;}
        if(request==CAMERA_PHOTO||request==PICK_PHOTO){photoResult(request,result,intent);return;}
        if(result!=RESULT_OK||intent==null||intent.getData()==null)return;
        if(request==EXPORT)exportArchive(intent.getData());else if(request==IMPORT)readArchive(intent.getData());
    }
}
