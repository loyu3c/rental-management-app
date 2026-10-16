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
    private LeaseEditorSession leaseEditor;
    private boolean destroyingLeaseEditor=false;
    private static final java.util.Set<String> leaseAttachmentImports=java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private static final class LeaseEditorSession {
        android.app.Dialog dialog;JSONObject attachments;String token;boolean saved;
        java.util.function.Supplier<JSONObject> snapshot;Runnable refresh;
    }

    private String propertyId="", unitId="";
    private static final String[] KEYS={"properties","units","leases","invoices","receipts","accounts","expenses","transfers","reconciliations","deposits","settlements","meterReadings","expenseCategories","owners","reportShares"};
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
        if(state!=null&&state.getString("leaseEditor")!=null)try{restoreLeaseEditor(new JSONObject(state.getString("leaseEditor")));}catch(Exception e){error(e);}
    }
    @Override protected void onResume(){super.onResume();watchLeaseAttachment();}
    @Override protected void onDestroy() {
        destroyingLeaseEditor=true;if(leaseEditor!=null&&leaseEditor.dialog!=null)leaseEditor.dialog.dismiss();
        for(AlertDialog dialog:new ArrayList<>(livePanels.keySet()))dialog.dismiss();livePanels.clear();expenseImages.shutdown();super.onDestroy();
    }
    @Override public void onSaveInstanceState(Bundle out) {
        out.putInt("tab", selected); out.putString("property",propertyId); out.putString("unit",unitId);
        if(leaseEditor!=null&&leaseEditor.dialog!=null&&leaseEditor.dialog.isShowing())out.putString("leaseEditor",leaseEditor.snapshot.get().toString());
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
        background.setColor(Color.rgb(255, 252, 247)); background.setCornerRadius(dp(16));background.setStroke(dp(1),Color.rgb(237,227,214)); box.setBackground(background);
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
            primaryAction("待辦總覽",this::taskOverview);
            action("租約到期提醒",this::leaseAlerts);
            card("不動產建檔", "已建立 " + data("properties").length() + " 筆不動產、" + data("units").length() + " 個出租單位。\n請從不動產入口新增。");
        } else if (selected == 1) {
            rentScreen();
        } else if (selected == 2) {
            accountingScreen();
        } else if (selected == 3) {
            propertyScreen();
        } else {
            primaryAction("房東的每月總報表",this::ownerReport);action("房東與不動產／帳戶歸屬管理",this::ownerManager);action("租客報表與連結",this::tenantReportPicker);
            actionGroup("帳務報表",new String[]{"支出與各棟分攤","押金總覽","退租結算與待收／待退","帳戶流水與存摺對帳"},new Runnable[]{this::expenseReport,this::depositOverview,this::settlementOverview,this::accountsDialog});
            actionGroup("資料備份與同步",new String[]{"匯出完整備份","從檔案還原資料","Google同步設定／狀態"},new Runnable[]{this::exportBackup,this::importBackup,this::googleSyncSettings});
            action("待辦總覽",this::taskOverview);
            card("報表說明","App 是帳務來源；Google 設定完成後可手動同步房東月報。新版部署可分別授權房東與租客的獨立報表，手機變更後需重新同步。歷史補登與押金請依實際收付核對。");
        }
        LinearLayout nav = new LinearLayout(this); nav.setGravity(Gravity.CENTER);
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            int[] icons={R.drawable.nav_home,R.drawable.nav_money,R.drawable.nav_ledger,R.drawable.nav_property,R.drawable.nav_report};
            LinearLayout button=new LinearLayout(this);button.setOrientation(LinearLayout.VERTICAL);button.setGravity(Gravity.CENTER);button.setPadding(dp(2),dp(8),dp(2),dp(8));button.setMinimumHeight(dp(64));
            ImageView icon=new ImageView(this);icon.setImageResource(icons[i]);icon.setColorFilter(i==selected?Color.rgb(104,70,39):Color.rgb(115,112,106));icon.setScaleType(ImageView.ScaleType.FIT_CENTER);button.addView(icon,new LinearLayout.LayoutParams(dp(22),dp(22)));
            TextView label=text(tabs[i],12,i==selected?Color.rgb(104,70,39):Color.DKGRAY);label.setGravity(Gravity.CENTER);label.setPadding(0,dp(4),0,0);label.setIncludeFontPadding(false);button.addView(label,new LinearLayout.LayoutParams(-1,-2));
            GradientDrawable navBg=new GradientDrawable();navBg.setColor(i==selected?Color.rgb(232,213,187):Color.rgb(248,244,237));navBg.setCornerRadius(dp(12));button.setBackground(surface(i==selected?Color.rgb(232,213,187):Color.rgb(248,244,237),12));button.setClickable(true);button.setFocusable(true);button.setContentDescription(tabs[i]);button.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES);icon.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);label.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            button.setOnClickListener(v -> { selected = index; render(); });
            nav.addView(button,new LinearLayout.LayoutParams(0,-2,1));
        }
        root.addView(nav); setContentView(root);
    }

    private JSONArray data(String key) {
        try { if(key.equals("expenseCategories")&&!getSharedPreferences("catalog",MODE_PRIVATE).contains(key))return ExpenseCategories.defaults();return new JSONArray(getSharedPreferences("catalog", MODE_PRIVATE).getString(key, "[]")); }
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
    private Button styledButton(String label,boolean primary,boolean danger) {
        Button button=new Button(this);button.setText(display(label));button.setAllCaps(false);button.setTextSize(15);button.setTypeface(null,Typeface.BOLD);button.setIncludeFontPadding(false);
        button.setMinHeight(dp(48));button.setMinimumHeight(dp(48));button.setMinWidth(0);button.setMinimumWidth(0);button.setPadding(dp(16),dp(12),dp(16),dp(12));button.setStateListAnimator(null);
        int ink=danger?Color.rgb(160,65,51):Color.rgb(100,67,43);
        button.setTextColor(primary?Color.WHITE:ink);
        GradientDrawable shape=new GradientDrawable();shape.setCornerRadius(dp(14));shape.setColor(primary?Color.rgb(112,77,50):Color.rgb(255,252,247));
        if(!primary)shape.setStroke(dp(1),danger?Color.rgb(227,198,189):Color.rgb(223,210,194));
        button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(35,112,77,50)),shape,null));
        return button;
    }
    private void action(String label,Runnable run) { addAction(label,run,false); }
    private void primaryAction(String label,Runnable run) { addAction(label,run,true); }
    private void addAction(String label,Runnable run,boolean primary) {
        Button b=styledButton(label,primary,label.startsWith("撤銷"));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,dp(6),0,dp(6));
        b.setOnClickListener(v->run.run());body.addView(b,params);
    }
    private void shortcut(LinearLayout row,String label,int iconId,Runnable run) {
        LinearLayout button=new LinearLayout(this);button.setOrientation(LinearLayout.VERTICAL);button.setGravity(Gravity.CENTER);button.setPadding(dp(4),dp(12),dp(4),dp(12));button.setMinimumHeight(dp(92));
        ImageView icon=new ImageView(this);icon.setImageResource(iconId);icon.setColorFilter(Color.rgb(104,70,39));icon.setScaleType(ImageView.ScaleType.FIT_CENTER);button.addView(icon,new LinearLayout.LayoutParams(dp(26),dp(26)));
        TextView caption=text(label,13,Color.rgb(90,61,37));caption.setGravity(Gravity.CENTER);caption.setIncludeFontPadding(false);caption.setPadding(0,dp(8),0,0);button.addView(caption,new LinearLayout.LayoutParams(-1,-2));
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(238,226,211));bg.setCornerRadius(dp(12));button.setBackground(surface(Color.rgb(238,226,211),14));button.setClickable(true);button.setFocusable(true);button.setContentDescription(label);button.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES);icon.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);caption.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);button.setOnClickListener(v->run.run());
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-1,1);params.setMargins(dp(3),dp(6),dp(3),dp(6));row.addView(button,params);
    }
    private void actionGroup(String title,String[] labels,Runnable[] runs) {
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(255,252,247));bg.setCornerRadius(dp(12));box.setBackground(bg);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,dp(5),0,dp(5));body.addView(box,params);
        Button toggle=styledButton(title+"  ▾",false,false);toggle.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);box.addView(toggle,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout items=new LinearLayout(this);items.setOrientation(LinearLayout.VERTICAL);items.setVisibility(android.view.View.GONE);box.addView(items);
        for(int i=0;i<labels.length;i++){final Runnable run=runs[i];Button item=styledButton(labels[i]+"  ›",false,false);item.setTextSize(14);item.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);item.setOnClickListener(v->run.run());LinearLayout.LayoutParams itemParams=new LinearLayout.LayoutParams(-1,-2);itemParams.setMargins(dp(8),dp(4),dp(8),dp(4));items.addView(item,itemParams);}
        toggle.setOnClickListener(v->{boolean open=items.getVisibility()!=android.view.View.VISIBLE;items.setVisibility(open?android.view.View.VISIBLE:android.view.View.GONE);toggle.setText(title+(open?"  ▴":"  ▾"));toggle.setContentDescription(title+(open?"，已展開":"，已收合"));});toggle.setContentDescription(title+"，已收合");
    }
    private void cardAction(LinearLayout box,String label,Runnable run) {
        LinearLayout buttons;
        if(box.getTag() instanceof LinearLayout)buttons=(LinearLayout)box.getTag();
        else {
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.BOTTOM|Gravity.END);
            View last=box.getChildCount()>1?box.getChildAt(1):null;
            if(last instanceof TextView){box.removeView(last);last.setPadding(0,dp(4),dp(10),0);row.addView(last,new LinearLayout.LayoutParams(0,-2,1));}
            else {View spacer=new View(this);row.addView(spacer,new LinearLayout.LayoutParams(0,0,1));}
            buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.VERTICAL);buttons.setGravity(Gravity.END);row.addView(buttons,new LinearLayout.LayoutParams(-2,-2));box.addView(row,Math.min(1,box.getChildCount()),new LinearLayout.LayoutParams(-1,-2));box.setTag(buttons);
        }
        Button button=styledButton(label,false,label.startsWith("撤銷"));button.setTextSize(13);button.setPadding(dp(12),dp(8),dp(12),dp(8));button.setMinimumWidth(dp(64));button.setOnClickListener(v->run.run());button.setContentDescription(label+"，此筆紀錄");
        buttons.addView(button,new LinearLayout.LayoutParams(-2,-2));
    }
    private void markVoided(LinearLayout box) {
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(239,237,233));bg.setStroke(dp(1),Color.rgb(221,218,211));bg.setCornerRadius(dp(16));box.setBackground(bg);
        for(int i=0;i<box.getChildCount();i++)if(box.getChildAt(i) instanceof TextView){TextView value=(TextView)box.getChildAt(i);value.setTextColor(Color.rgb(126,123,117));value.setPaintFlags(value.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);value.setTypeface(null,Typeface.NORMAL);}
        TextView status=text("已撤銷 · 僅供歷史查閱",12,Color.rgb(110,107,101));status.setPadding(0,dp(4),0,0);box.addView(status);
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
        LinearLayout row=card(title+"  ›",detail);row.setBackground(surface(Color.rgb(255,252,247),16));row.setMinimumHeight(dp(64));row.setClickable(true);row.setFocusable(true);row.setOnClickListener(v->run.run());row.setContentDescription(title+"，點擊查看");
    }
    private android.graphics.drawable.Drawable surface(int color,int radius) {
        GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));shape.setStroke(dp(1),Color.rgb(228,216,200));
        return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(30,112,77,50)),shape,null);
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
    private String status(JSONObject unit) {return Occupancy.status(data("units"),data("leases"),unit,today());}
    private String currentTenant(JSONObject unit) {JSONObject lease=Occupancy.current(data("units"),data("leases"),unit,today());return lease==null?"":lease.optString("tenant");}
    private void occupancySummary(LinearLayout box,String property){
        int[] counts=Occupancy.counts(data("units"),data("leases"),property,today());LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(6),0,0);row.addView(occupancyLabel("出租中 "+fmt(counts[0]),true),new LinearLayout.LayoutParams(0,-2,1));row.addView(occupancyLabel("未出租 "+fmt(counts[1]),false),new LinearLayout.LayoutParams(0,-2,1));box.addView(row);
    }
    private LinearLayout occupancyLabel(String label,boolean active){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);addOccupancyLight(row,active);row.addView(text(label,14,active?Color.rgb(54,112,78):Color.rgb(112,112,106)));row.setContentDescription(label);return row;
    }
    private void addOccupancyLight(LinearLayout row,boolean active){
        View dot=new View(this);GradientDrawable circle=new GradientDrawable();circle.setShape(GradientDrawable.OVAL);circle.setColor(active?Color.rgb(34,197,94):Color.rgb(157,158,154));circle.setStroke(dp(2),active?Color.rgb(187,247,208):Color.rgb(224,225,221));dot.setBackground(circle);dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(18),dp(18));params.rightMargin=dp(8);row.addView(dot,params);
    }
    private void unitOccupancyCard(JSONObject unit){
        String st=status(unit);boolean active=st.startsWith("出租中");LinearLayout box=card(unit.optString("code")+"  ›",unit.optString("type")+"\n"+tenantLabel(unit));View title=box.getChildAt(0);box.removeView(title);LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);addOccupancyLight(heading,active);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));box.addView(heading,0);String label=st.equals("停用出租")?st:active?st:"未出租"+(st.equals("未建租約")?"":" · "+st);box.addView(text(label,14,active?Color.rgb(54,112,78):Color.rgb(112,112,106)),1);box.setClickable(true);box.setFocusable(true);box.setForeground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.rgb(235,221,202)),null,new android.graphics.drawable.ColorDrawable(Color.WHITE)));box.setOnClickListener(v->openUnit(unit));box.setContentDescription(unit.optString("code")+"，"+label+"，"+tenantLabel(unit));
    }
    private String tenantLabel(JSONObject unit) {String tenant=currentTenant(unit);return tenant.isEmpty()?"目前無租客":"目前租客："+tenant;}
    private void propertyScreen() {
        JSONObject property=find("properties",propertyId),unit=find("units",unitId);
        if(property==null) {propertyId="";unitId="";primaryAction("＋ 新增不動產",this::addProperty);
            JSONArray properties=data("properties"),units=data("units");
            LinearLayout overview=card("出租概況","依今天有效租約、已啟用出租單位計數；待起租與到期單位歸入未出租。");occupancySummary(overview,null);
            if(properties.length()==0)card("尚未建檔","先新增建築物或土地，再建立房間、車格或土地出租範圍。");
            for(int i=0;i<properties.length();i++) {JSONObject p=properties.optJSONObject(i);int count=0;for(int j=0;j<units.length();j++)if(p.optString("id").equals(units.optJSONObject(j).optString("propertyId")))count++;
                LinearLayout propertyBox=card(p.optString("name")+"  ›",p.optString("type")+" · 建檔 "+fmt(count)+" 個單位（含停用）");occupancySummary(propertyBox,p.optString("id"));propertyBox.setClickable(true);propertyBox.setFocusable(true);propertyBox.setForeground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.rgb(235,221,202)),null,new android.graphics.drawable.ColorDrawable(Color.WHITE)));propertyBox.setOnClickListener(v->{propertyId=p.optString("id");render();});}
            return;
        }
        action("‹ 所有不動產",()->{propertyId="";unitId="";render();});
        if(unit!=null&&unit.optString("propertyId").equals(propertyId)) { unitScreen(property,unit);return; }
        unitId="";LinearLayout propertyOverview=card(property.optString("name"),property.optString("type")+" · 全部出租單位");occupancySummary(propertyOverview,propertyId);
        action("修改不動產名稱",()->editName("properties",property,"name","不動產名稱"));
        if(property.optString("type").equals("建築物"))action("切換整棟／分房出租",()->rentalMode(property));
        primaryAction("＋ 新增出租單位",()->addUnit(propertyId,property.optString("name")));
        EditText search=field(body,"搜尋代號或租客姓名");
        Spinner filter=choice(body,"出租狀態",new String[]{"啟用出租","全部（含停用）","停用出租","出租中","未出租","待起租","租約已到期","未建租約"});
        Spinner sort=choice(body,new String[]{"代號由小到大","代號由大到小"});
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);body.addView(list);
        Runnable refresh=()->{
            list.removeAllViews();List<JSONObject> rows=new ArrayList<>();JSONArray units=data("units");
            for(int i=0;i<units.length();i++) {JSONObject u=units.optJSONObject(i);String st=status(u);
                if(!propertyId.equals(u.optString("propertyId"))||!(u.optString("code")+" "+currentTenant(u)).toLowerCase(Locale.ROOT).contains(search.getText().toString().trim().toLowerCase(Locale.ROOT)))continue;
                String selectedStatus=filter.getSelectedItem().toString();if(selectedStatus.equals("啟用出租")&&!RentalUnits.enabled(u))continue;if(selectedStatus.equals("停用出租")&&RentalUnits.enabled(u))continue;if(filter.getSelectedItemPosition()>=3&&(!RentalUnits.enabled(u)||(selectedStatus.equals("未出租")?st.startsWith("出租中"):!st.startsWith(selectedStatus))))continue;rows.add(u);}
            rows.sort((x,y)->natural(x.optString("code"),y.optString("code"))*(sort.getSelectedItemPosition()==0?1:-1));
            LinearLayout previous=body;body=list;
            for(JSONObject u:rows)unitOccupancyCard(u);
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
        action(RentalUnits.enabled(unit)?"停用出租（保留歷史）":"啟用出租",()->toggleRentalUnit(unit));
        if(RentalUnits.enabled(unit))action("＋ 新增租約／換新租客",()->addLease(unit));
        JSONArray leases=data("leases");List<JSONObject> rows=new ArrayList<>();
        for(int i=0;i<leases.length();i++)if(unit.optString("id").equals(leases.optJSONObject(i).optString("unitId")))rows.add(leases.optJSONObject(i));
        rows.sort((x,y)->y.optString("start").compareTo(x.optString("start")));
        for(JSONObject l:rows) {
            card(l.optString("tenant"),l.optString("start")+" ～ "+l.optString("end")+(l.has("initialMeter")&&!l.isNull("initialMeter")?"\n起始電度："+fmt(l.optLong("initialMeter")):"\n起始電度：尚未設定")+(l.has("terminatedOn")?"\n已退租："+l.optString("terminatedOn"):"")+"\n"+RentTerms.label(l)+" $"+l.optLong("rent")+" · "+l.optString("cycle")+"\n約定押金 $"+l.optLong("deposit")+"（約定金額；實收看押金明細）\n"+l.optString("email")+"\n"+l.optString("utilities")+(l.optString("utilities").equals("管理者代收水電")?(l.isNull("water")?" · 水費待設定":" · 月水費 $"+l.optLong("water")):""));
            if(!l.has("terminatedOn"))action("修改 "+l.optString("tenant")+" 的租約",()->leaseForm(unit,l));
            action(l.optString("contractFile").isEmpty()?"上傳 PDF 合約":"更換 PDF 合約",()->pickContract(l.optString("id")));
            if(!l.optString("contractFile").isEmpty())action("開啟 PDF 合約",()->viewContract(l));
            if(!l.optString("initialMeterPhoto").isEmpty())action("查看起始電表照片",()->viewMeterPhoto(l.optString("initialMeterPhoto")));
            action("租客專屬報表與連結",()->tenantReport(l.optString("id")));
            action("查看帳單與收款",()->leaseLedger(l));
        }
    }
    private void toggleRentalUnit(JSONObject unit){
        boolean enabled=!RentalUnits.enabled(unit);new AlertDialog.Builder(this).setMessage((enabled?"啟用":"停用")+unit.optString("code")+"？停用不列入出租／空房統計，歷史帳務保留。").setNegativeButton("取消",null).setPositiveButton("確認",(d,w)->{try{JSONArray next=RentalUnits.setEnabled(data("units"),data("leases"),unit.optString("id"),enabled,today());if(saveChecked("units",next))render();}catch(Exception e){error(e);}}).show();
    }
    private void rentalMode(JSONObject property){
        new AlertDialog.Builder(this).setTitle("切換出租方式").setItems(new String[]{"整棟出租：啟用整棟、停用房間","分房出租：停用整棟、啟用房間"},(d,n)->{try{JSONArray next=RentalUnits.switchMode(data("units"),data("leases"),property.optString("id"),n==0,today());new AlertDialog.Builder(this).setMessage("切換為"+(n==0?"整棟":"分房")+"出租？車格與土地出租範圍維持原設定，歷史租約與帳務保留。").setNegativeButton("取消",null).setPositiveButton("確認切換",(c,w)->{try{JSONArray checked=RentalUnits.switchMode(data("units"),data("leases"),property.optString("id"),n==0,today());if(saveChecked("units",checked))render();}catch(Exception e){error(e);}}).show();}catch(Exception e){error(e);}}).show();
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
                item.put("propertyId", propertyId); item.put("code", value); item.put("type", type.getSelectedItem().toString());boolean enabled=true;for(int k=0;k<items.length();k++){JSONObject other=items.optJSONObject(k);if(propertyId.equals(other.optString("propertyId"))&&RentalUnits.enabled(other)&&((item.optString("type").equals("整棟")&&RentalUnits.room(other))||(RentalUnits.room(item)&&other.optString("type").equals("整棟"))))enabled=false;}item.put("enabled",enabled);
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
                boolean scope = (key.equals("properties")||key.equals("accounts")) || item.optString("propertyId").equals(original.optString("propertyId"));
                if (!item.optString("id").equals(original.optString("id")) && scope && item.optString(column).equalsIgnoreCase(next)) {
                    value.setError(key.equals("accounts")?"帳戶名稱已存在，請使用不同名稱":"名稱或代號已存在"); return;
                }
            }
            try {
                for (int i = 0; i < items.length(); i++) if (items.optJSONObject(i).optString("id").equals(original.optString("id")))
                    items.optJSONObject(i).put(column, next);
                if (key.equals("accounts")?saveChecked(key,items):save(key, items)) { dialog.dismiss(); render(); }
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
    private void leaseForm(JSONObject unit,JSONObject original){leaseForm(unit,original,null);}
    private void leaseForm(JSONObject unit, JSONObject original,JSONObject restored) {
        if(original!=null&&original.has("terminatedOn")){error(new IllegalArgumentException("已退租租約保留歷史，請新增下一位租客的租約"));return;}
        if(original==null&&!RentalUnits.enabled(unit)){error(new IllegalArgumentException("請先啟用出租單位"));return;}
        boolean locked=original!=null&&hasBilling(original.optString("id"));
        LinearLayout layout = form();layout.setPadding(dp(12),dp(4),dp(12),dp(8));
        LinearLayout tenantSection=expenseSection(layout,"租客資訊"),termSection=expenseSection(layout,"租期與租金"),utilitySection=expenseSection(layout,"水電設定"),contractSection=expenseSection(layout,"租約附件");
        EditText tenant = field(tenantSection, "租客姓名"), email = field(tenantSection, "查看報表用 Gmail");
        EditText start = dateField(termSection, "起租日",false), end = dateField(termSection, "到期日",false);start.setText("");end.setText("");
        EditText rent = number(termSection, "月租金（年繳也填每月金額）"), day = number(termSection, "每月應繳日 1–31");
        Spinner pricing=choice(termSection,"租金計價",RentTerms.PRICING);Spinner cycle = choice(termSection,"繳款週期",RentTerms.CYCLES);
        EditText firstDue=dateField(termSection,"首次應繳日",false);firstDue.setText("");TextView termsHelp=text("按月：每期從起租月份計算；季繳 3 個月、半年繳 6 個月、年繳 12 個月。首末不足月按該月天數比例，整期四捨五入。按日包含起租日與到期日；短租水電請於開單時填實收費用。提醒顯示在首頁待辦，不會推播。",12,Color.DKGRAY);termSection.addView(termsHelp);
        EditText deposit = number(termSection, "約定押金，無押金填0");
        Spinner utilities = choice(utilitySection, new String[]{"租客自行繳水電", "管理者代收水電"});
        EditText water = number(utilitySection, "代收固定月水費，可留空待設定");
        EditText rate = number(utilitySection, "每度電費（只套用新開水電帳單）");rate.setText("5");
        EditText initialMeter=number(utilitySection,"起始電度（起租時電表讀數，可稍後補登）");
        for(View input:new View[]{tenant,email,start,end,rent,day,pricing,cycle,firstDue,deposit,utilities,water,rate,initialMeter})polishExpenseInput(input);tenant.setHint("租客姓名");email.setHint("查看報表用 Gmail");rent.setHint("每月金額");day.setHint("1–31");deposit.setHint("無押金填 0");water.setHint("可稍後設定");rate.setHint("每度電價");initialMeter.setHint("起租時電表讀數");email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        LinearLayout emailRow=(LinearLayout)email.getParent();TextView emailLabel=(TextView)emailRow.getChildAt(0);emailRow.removeView(emailLabel);tenantSection.addView(emailLabel,tenantSection.indexOfChild(emailRow));emailLabel.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
        LeaseEditorSession editor=new LeaseEditorSession();editor.token=restored==null?UUID.randomUUID().toString():restored.optString("token");try{editor.attachments=restored==null?LeaseAttachments.draft(original):restored.getJSONObject("attachments");}catch(Exception e){error(e);return;}leaseEditor=editor;
        LinearLayout attachmentRows=new LinearLayout(this);attachmentRows.setOrientation(LinearLayout.VERTICAL);contractSection.addView(attachmentRows,new LinearLayout.LayoutParams(-1,-2));
        editor.refresh=()->drawLeaseAttachments(editor,attachmentRows);editor.refresh.run();
        contractSection.addView(text("附件與租約一起儲存；取消不會變更原附件。",12,Color.DKGRAY));
        if(original==null){
            utilities.setSelection(1);water.setText("200");
            start.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence value,int a,int c,int d){}public void onTextChanged(CharSequence value,int a,int before,int count){try{end.setText(pricing.getSelectedItemPosition()==0?LeaseDates.oneYearAfter(value.toString()):value.toString());}catch(Exception e){end.setText("");}}public void afterTextChanged(Editable value){}});
        }
        if(original!=null) {
            if(original.has("initialMeter")&&!original.isNull("initialMeter"))initialMeter.setText(original.optString("initialMeter"));if(hasMeterHistory(original.optString("id"))&&original.has("initialMeter")&&!original.isNull("initialMeter")){initialMeter.setEnabled(false);utilitySection.addView(text("已有抄表或帳單，起始電度保留原紀錄；讀數修正請使用抄表流程。",14,Color.DKGRAY));}
            tenant.setText(original.optString("tenant"));email.setText(original.optString("email"));start.setText(original.optString("start"));end.setText(original.optString("end"));
            rent.setText(original.optString("rent"));day.setText(original.optString("dueDay"));deposit.setText(original.optString("deposit"));
            cycle.setSelection(java.util.Arrays.asList(RentTerms.CYCLES).indexOf(original.optString("cycle")));pricing.setSelection(java.util.Arrays.asList(RentTerms.PRICING).indexOf(RentTerms.pricing(original)));firstDue.setText(original.optString("firstDue",original.optString("start")));utilities.setSelection(original.optString("utilities").equals("管理者代收水電")?1:0);
            if(!original.isNull("water"))water.setText(original.optString("water"));rate.setText(original.optString("electricRate","5"));
            if(hasBilling(original.optString("id"))) {
                termSection.addView(text("已有帳務：租金條件鎖定，水電設定只套用新帳單。新版週期如需延長請另建續租租約，避免已開立期程與金額不一致；舊版月帳務仍可延長到期日。",14,Color.rgb(137,83,15)));
                for(EditText f:new EditText[]{start,rent,day,deposit})f.setEnabled(false);cycle.setEnabled(false);pricing.setEnabled(false);firstDue.setEnabled(false);
            }
        }
        EditText[] draftInputs={tenant,email,start,end,rent,day,firstDue,deposit,water,rate,initialMeter};String[] draftKeys={"tenant","email","start","end","rent","day","firstDue","deposit","water","rate","initialMeter"};
        if(restored!=null)try{JSONObject values=restored.getJSONObject("values");for(int i=0;i<draftInputs.length;i++)draftInputs[i].setText(values.optString(draftKeys[i]));pricing.setSelection(values.optInt("pricing"));cycle.setSelection(values.optInt("cycle"));utilities.setSelection(values.optInt("utilities"));}catch(Exception e){error(e);return;}
        if(locked&&!RentTerms.scheduled(original)){((View)firstDue.getParent()).setVisibility(View.GONE);termsHelp.setText("舊租約已有帳務，保留原按月開單方式；新租約才套用新的繳款期程。");}else ((View)day.getParent()).setVisibility(View.GONE);
        AdapterView.OnItemSelectedListener termsListener=new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int n,long id){boolean monthly=pricing.getSelectedItemPosition()==0;if(original==null&&!monthly){try{if(end.getText().toString().equals(LeaseDates.oneYearAfter(start.getText().toString())))end.setText(start.getText());}catch(Exception ignored){}}if(!monthly&&cycle.getSelectedItemPosition()!=4)cycle.setSelection(4);if(monthly&&cycle.getSelectedItemPosition()==4)cycle.setSelection(0);cycle.setEnabled(!locked&&monthly);TextView rentName=(TextView)((LinearLayout)rent.getParent()).getChildAt(0);rentName.setText(monthly?"月租金":pricing.getSelectedItemPosition()==1?"每日租金":"整段總價");rent.setHint(monthly?"每月金額":pricing.getSelectedItemPosition()==1?"每日金額":"整段租期總額");}};pricing.setOnItemSelectedListener(termsListener);
        start.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence value,int a,int b,int c){if(firstDue.getText().toString().isEmpty())firstDue.setText(value);}});
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(layout);
        android.app.Dialog dialog=new android.app.Dialog(this,android.R.style.Theme_Material_Light_NoActionBar);LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(Color.rgb(248,244,237));TextView title=text(unit.optString("code")+" · "+(original==null?"新增租約":"修改租約"),20,Color.rgb(77,57,39));title.setTypeface(null,Typeface.BOLD);title.setPadding(dp(16),dp(12),dp(16),dp(10));page.addView(title,new LinearLayout.LayoutParams(-1,-2));page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer=new LinearLayout(this);footer.setPadding(dp(12),dp(6),dp(12),dp(6));footer.setBackgroundColor(Color.rgb(255,252,247));Button cancel=styledButton("取消",false,false),saveButton=styledButton("儲存",true,false);LinearLayout.LayoutParams cancelParams=new LinearLayout.LayoutParams(0,dp(48),1);cancelParams.rightMargin=dp(8);footer.addView(cancel,cancelParams);footer.addView(saveButton,new LinearLayout.LayoutParams(0,dp(48),1));page.addView(footer,new LinearLayout.LayoutParams(-1,-2));dialog.setContentView(page);dialog.setCanceledOnTouchOutside(false);cancel.setOnClickListener(v->dialog.dismiss());editor.dialog=dialog;
        editor.snapshot=()->{JSONObject snapshot=new JSONObject();try{JSONObject values=new JSONObject();for(int i=0;i<draftInputs.length;i++)values.put(draftKeys[i],draftInputs[i].getText().toString());values.put("pricing",pricing.getSelectedItemPosition()).put("cycle",cycle.getSelectedItemPosition()).put("utilities",utilities.getSelectedItemPosition());snapshot.put("token",editor.token).put("unitId",unit.optString("id")).put("original",original==null?JSONObject.NULL:original).put("attachments",editor.attachments).put("values",values).put("scroll",scroll.getScrollY());}catch(Exception e){throw new IllegalStateException(e);}return snapshot;};
        dialog.setOnDismissListener(d->{if(!destroyingLeaseEditor){try{for(String name:LeaseAttachments.discard(editor.attachments,editor.saved))new File(photoDir(),name).delete();}catch(Exception e){error(e);}getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingLeaseAttachment").commit();}if(leaseEditor==editor)leaseEditor=null;});
        saveButton.setOnClickListener(w -> {
            if(getSharedPreferences("workflow",MODE_PRIVATE).contains("pendingLeaseAttachment")){Toast.makeText(this,"附件仍在處理，請完成後再儲存",Toast.LENGTH_SHORT).show();return;}
            String person = tenant.getText().toString().trim(), mail = email.getText().toString().trim();
            if (person.isEmpty()) { tenant.setError("請填姓名"); return; }
            if (!Patterns.EMAIL_ADDRESS.matcher(mail).matches() || !mail.toLowerCase(Locale.ROOT).endsWith("@gmail.com")) {
                email.setError("請填有效 Gmail"); return;
            }
            LocalDate from, to;
            try { from = LocalDate.parse(dateValue(start)); to = LocalDate.parse(dateValue(end));
                if (to.isBefore(from)) throw fieldError(end,"到期日不得早於起租日");
            } catch (Exception e) { error(e); return; }
            long monthly, due, held, waterAmount, electricRate, initialReading;
            try { monthly = amount(rent, false); due = locked&&!RentTerms.scheduled(original)?amount(day,false):LocalDate.parse(dateValue(firstDue)).getDayOfMonth(); held = amount(deposit, false); waterAmount = amount(water, true); electricRate=amount(rate,false);initialReading=amount(initialMeter,true);
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
                JSONObject lease = original==null?new JSONObject():new JSONObject(original.toString()); lease.put("id", original==null?UUID.randomUUID().toString():original.optString("id")); lease.put("unitId", unit.optString("id"));
                lease.put("unitCodeSnapshot", unit.optString("code")); lease.put("tenant", person); lease.put("email", mail);
                lease.put("start", from.toString()); lease.put("end", to.toString()); lease.put("rent", monthly);
                if(!locked){lease.put("termsVersion",1).put("pricing",pricing.getSelectedItem().toString()).put("firstDue",dateValue(firstDue));}LeaseAttachments.apply(editor.attachments,lease);if(!lease.optString("initialMeterPhoto").isEmpty()){android.graphics.Bitmap check=photoPreview(new File(photoDir(),lease.getString("initialMeterPhoto")));check.recycle();}if(!lease.optString("contractFile").isEmpty())validateContractPdf(new File(photoDir(),lease.getString("contractFile")));
                lease.put("dueDay", due); lease.put("cycle", cycle.getSelectedItem().toString()); lease.put("deposit", held);
                lease.put("utilities", utilities.getSelectedItem().toString()); lease.put("electricRate", electricRate);lease.put("initialMeter",initialReading<0?JSONObject.NULL:initialReading);
                lease.put("water", utilities.getSelectedItemPosition() == 0 || waterAmount == -1 ? JSONObject.NULL : waterAmount);
                if(original!=null&&!mail.equalsIgnoreCase(original.optString("email")))requireUnshared("tenant",original.optString("id"));
                RentTerms.validate(lease);if(RentTerms.scheduled(lease))RentTerms.period(lease,YearMonth.from(from));
                if(original!=null){JSONObject latest=find("leases",original.optString("id"));if(latest==null||!latest.toString().equals(original.toString()))throw new IllegalArgumentException("租約已變更，請重新開啟修改");if(hasBilling(original.optString("id")))LeaseDates.validateBilledEdit(original,lease);}
                if(original==null)leases.put(lease);else for(int i=0;i<leases.length();i++)if(leases.optJSONObject(i).optString("id").equals(original.optString("id")))leases.put(i,lease);
                if (save("leases", leases)) { editor.saved=true;dialog.dismiss();render();refreshPanels(); }
            } catch (Exception e) { error(e); }
        });dialog.show();dialog.getWindow().setLayout(-1,-1);dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);if(restored!=null)scroll.post(()->scroll.scrollTo(0,restored.optInt("scroll")));
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
    private void paymentHistory() {
        LinearLayout layout=form();Spinner scope=choice(layout,"月份範圍",new String[]{"指定月份","全部月份"});
        EditText month=dateField(layout,"交易月份",true);month.setText(YearMonth.from(today()).toString());View monthRow=(View)month.getParent();
        Spinner kind=choice(layout,"收款類型",new String[]{"全部","租金水電","收到押金","退還押金","結算補繳"});
        EditText search=labeledField(layout,"搜尋租客／單位／帳戶／備註");LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);layout.addView(list,new LinearLayout.LayoutParams(-1,-2));int[] limit={20};
        Runnable[] draw={null};draw[0]=()->{
            list.removeAllViews();monthRow.setVisibility(scope.getSelectedItemPosition()==0?View.VISIBLE:View.GONE);LinearLayout previous=body;body=list;
            try {
                JSONObject result=PaymentHistory.query(fullBackup(),scope.getSelectedItemPosition()==0?dateValue(month):"",kind.getSelectedItem().toString(),search.getText().toString());JSONArray rows=result.getJSONArray("rows");
                card("符合查詢 "+rows.length()+" 筆","租金水電實收 $"+result.getLong("rent")+"\n押金收取 $"+result.getLong("depositReceived")+" · 退還 $"+result.getLong("depositRefunded")+"\n結算補繳 $"+result.getLong("topups")+"\n上述交易淨流入 $"+result.getLong("net"));
                card("對照存摺時請留意","依實際收付日期查詢。押金不是租金收入；抵付費用不是匯款，不列入。合計依目前篩選的全部紀錄計算，並非帳戶餘額；請到帳戶流水核對其他支出與轉帳。");
                for(int i=0;i<Math.min(limit[0],rows.length());i++){JSONObject row=rows.getJSONObject(i);tapCard(row.getString("date")+" · "+row.getString("kind")+" $"+row.getLong("amount"),row.getString("code")+" · "+row.getString("tenant")+"\n帳戶："+row.getString("account")+(row.getString("note").isEmpty()?"":"\n"+row.getString("note")),()->{JSONObject lease=find("leases",row.optString("leaseId"));if(lease!=null)leaseLedger(lease);});}
                if(rows.length()==0)card("沒有符合的收付紀錄","可調整月份、類型或搜尋字詞；撤銷紀錄及非現金抵付不列入。");
                if(rows.length()>limit[0])action("載入更多紀錄",()->{limit[0]+=20;draw[0].run();});
            }catch(Exception e){error(e);}finally{body=previous;}
        };
        Runnable reset=()->{limit[0]=20;draw[0].run();};AdapterView.OnItemSelectedListener listener=new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int n,long id){reset.run();}};scope.setOnItemSelectedListener(listener);kind.setOnItemSelectedListener(listener);
        TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){reset.run();}public void afterTextChanged(Editable e){}};month.addTextChangedListener(watcher);search.addTextChangedListener(watcher);
        draw[0].run();showPanel("收款紀錄查詢",layout,draw[0]);
    }
    private void rentScreen() {
        action("收款紀錄查詢",this::paymentHistory);
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
    private void taskOverview() {
        LinearLayout layout=form();String[] selectedKind={"全部"};
        String[] kinds={"全部","逾期待收","未完成開單","待抄電表","結算待收","押金待退"};
        Spinner filter=choice(layout,"待辦類型",kinds);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);layout.addView(list,new LinearLayout.LayoutParams(-1,-2));
        Runnable refresh=()->{
            list.removeAllViews();LinearLayout previous=body;body=list;
            try {JSONArray tasks=TaskOverview.build(fullBackup(),today());int count=0;
                card("待辦事項 "+tasks.length()+" 項",YearMonth.from(today())+" 本月追蹤；同一租約可有多項待辦。逾期僅包含應繳日已過的帳單。點選即可處理，不會自動開單、收款或退款。");
                for(String kind:kinds){if(kind.equals("全部")||!selectedKind[0].equals("全部")&&!selectedKind[0].equals(kind))continue;boolean heading=false;
                    for(int i=0;i<tasks.length();i++){JSONObject task=tasks.getJSONObject(i);if(!kind.equals(task.getString("kind")))continue;
                        if(!heading){TextView section=text(kind,18,Color.rgb(100,67,43));section.setTypeface(null,Typeface.BOLD);body.addView(section);heading=true;}
                        tapCard(task.getString("title"),task.getString("detail"),()->{JSONObject lease=find("leases",task.optString("leaseId"));if(lease==null)return;if(kind.equals("待抄電表"))meterPanel(lease);else if(kind.equals("未完成開單"))billingForm(lease);else leaseLedger(lease);});count++;
                    }
                }
                if(count==0)card("目前沒有符合的待辦","未開帳單不代表已繳清；尚未補登的歷史資料仍需人工核對。");
            }catch(Exception e){error(e);}finally{body=previous;}
        };
        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int n,long id){selectedKind[0]=kinds[n];refresh.run();}});
        refresh.run();showPanel("待辦總覽",layout,refresh);
    }
    private void leaseAlerts() {
        LinearLayout layout=form();Runnable refresh=()->{
            layout.removeAllViews();LinearLayout previous=body;body=layout;
            try {
                JSONArray rows=LeaseAlerts.upcoming(data("leases"),today(),60);
                card("租約到期提醒","未退租且已到期，或未來 60 天到期的租約。僅供追蹤，不會自動退租、改帳單或傳送通知。");
                if(rows.length()==0)card("目前沒有到期事項","已退租租約不列入；房間與獨立車位均會依租約日期追蹤。");
                for(int i=0;i<rows.length();i++){
                    JSONObject lease=rows.getJSONObject(i);long days=java.time.temporal.ChronoUnit.DAYS.between(today(),LocalDate.parse(lease.getString("end")));
                    tapCard(lease.optString("unitCodeSnapshot")+" · "+lease.optString("tenant"),(days<0?"已到期 "+(-days)+" 天，待處理":days==0?"今天到期":"距到期 "+days+" 天")+"\n到期日 "+lease.getString("end"),()->leaseLedger(lease));
                }
            }catch(Exception e){error(e);}finally{body=previous;}
        };refresh.run();showPanel("租約到期提醒",layout,refresh);
    }
    private void leaseLedger(JSONObject lease) {
        String leaseId=lease.optString("id");int[] tab={0};
        LinearLayout page=form();page.setPadding(dp(16),dp(8),dp(16),dp(12));page.setBackgroundColor(Color.rgb(248,244,237));
        LinearLayout header=form();header.setPadding(0,0,0,dp(10));page.addView(header);
        LinearLayout tabs=new LinearLayout(this);tabs.setPadding(dp(4),dp(4),dp(4),dp(4));GradientDrawable track=new GradientDrawable();track.setColor(Color.rgb(237,229,217));track.setCornerRadius(dp(16));tabs.setBackground(track);page.addView(tabs,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this);scroll.setClipToPadding(false);scroll.setPadding(0,dp(8),0,0);LinearLayout content=form();content.setPadding(0,0,0,dp(12));scroll.addView(content);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        AlertDialog dialog=new AlertDialog.Builder(this).setView(page).create();
        String[] names={"總覽","帳單","押金","退租結算"};Button[] buttons=new Button[names.length];
        Runnable refresh=()->{
            JSONObject current=find("leases",leaseId);header.removeAllViews();
            header.addView(text(current==null?"租約已不存在":current.optString("unitCodeSnapshot")+" · "+current.optString("tenant"),22,Color.rgb(90,61,37)));
            if(current!=null)header.addView(text(current.has("terminatedOn")?"已退租 · "+current.optString("terminatedOn"):"租期 "+current.optString("start")+" ～ "+current.optString("end"),14,Color.DKGRAY));
            for(int i=0;i<buttons.length;i++)if(buttons[i]!=null){buttons[i].setTextColor(Color.rgb(90,61,37));GradientDrawable pill=new GradientDrawable();pill.setCornerRadius(dp(12));pill.setColor(i==tab[0]?Color.rgb(255,252,247):Color.TRANSPARENT);buttons[i].setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(25,112,77,50)),pill,null));buttons[i].setTypeface(null,i==tab[0]?Typeface.BOLD:Typeface.NORMAL);buttons[i].setContentDescription(names[i]+(i==tab[0]?"，目前分頁":"，切換分頁"));buttons[i].setSelected(i==tab[0]);}
            drawLeaseLedger(content,current,tab[0]);
        };
        for(int i=0;i<names.length;i++){final int index=i;Button b=new Button(this);buttons[i]=b;b.setText(names[i]);b.setTextSize(13);b.setAllCaps(false);b.setIncludeFontPadding(false);b.setStateListAnimator(null);b.setPadding(0,dp(10),0,dp(10));b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(48));tabs.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->{tab[0]=index;refresh.run();scroll.scrollTo(0,0);});}
        Button close=styledButton("返回不動產",false,false);close.setOnClickListener(v->dialog.dismiss());LinearLayout.LayoutParams closeParams=new LinearLayout.LayoutParams(-1,-2);closeParams.setMargins(0,dp(8),0,0);page.addView(close,closeParams);
        livePanels.put(dialog,()->{int y=scroll.getScrollY();refresh.run();scroll.post(()->scroll.scrollTo(0,y));});dialog.setOnDismissListener(d->livePanels.remove(dialog));refresh.run();dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.rgb(248,244,237)));dialog.getWindow().setLayout(-1,-1);}
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
                if(!lease.has("terminatedOn"))primaryAction("＋ 收到租金",()->receiptForm(lease));
                else {if(settlement!=null&&Settlements.due(settlement)>0)primaryAction("＋ 收到結算補繳",()->settlementPaymentForm(lease));if(deposit.getLong("held")>0)action("退還剩餘押金",()->depositForm(lease,true));}
                card("收款摘要","租金與水電實收累計 $"+received+"\n尚未分配 $"+credit+"\n押金與結算補繳另外記錄。");
                if(credit>0)action("分配先前尚未分配的收款",()->allocateCredit(lease));
                card("租約資料",lease.optString("start")+" ～ "+lease.optString("end")+"\n"+RentTerms.label(lease)+" $"+lease.optLong("rent")+" · 約定押金 $"+lease.optLong("deposit"));
                return;
            }
            if(tab==2){depositCard(lease);return;}
            if(tab==3){if(settlement==null)card("尚未退租結算","退租時分開記錄費用與押金收退，再核對實際退款或補繳。");settlementCard(lease);return;}
            if(!lease.has("terminatedOn"))primaryAction("＋ 建立當月帳單（租金＋水電）",()->billingForm(lease));
            action("抄電表／查看照片",()->meterPanel(lease));
            Map<String,List<JSONObject>> months=new TreeMap<>(Collections.reverseOrder());
            for(int i=0;i<invoices.length();i++) {JSONObject r=invoices.getJSONObject(i);if(lease.optString("id").equals(r.getString("leaseId")))months.computeIfAbsent(r.getString("month"),key->new ArrayList<>()).add(r);}
            for(Map.Entry<String,List<JSONObject>> entry:months.entrySet()) {
                long rent=0,water=0,electric=0,paid=0,total=0;StringBuilder detail=new StringBuilder();boolean hasRent=false,hasUtility=false;
                for(JSONObject r:entry.getValue()) {
                    if(r.has("periodStart"))detail.append("\n租金涵蓋 ").append(r.optString("periodStart")).append(" ～ ").append(r.optString("periodEnd")).append(" · 應繳 ").append(r.optString("due"));
                    rent+=r.getLong("rent");water+=r.getLong("water");electric+=r.getLong("electricity");paid+=Ledger.paid(r.getString("id"),receipts);total+=Ledger.total(r);
                    if(r.getString("kind").equals("租金"))hasRent=true;else{hasUtility=true;detail.append("\n電表 ").append(fmt(r.getLong("previous"))).append(" → ").append(fmt(r.getLong("current"))).append(" · 每度 $").append(r.getLong("rate"));}
                }
                String utility=hasUtility?"水費 $"+water+"／電費 $"+electric:lease.optString("utilities").equals("管理者代收水電")?"水電尚未建立":"水電自行繳納";
                LinearLayout monthBox=card(entry.getKey()+" · 當月帳單","應收 $"+total+" · 已付 $"+paid+"\n待繳 $"+(total-paid));
                LinearLayout details=form();details.setVisibility(View.GONE);monthBox.addView(details);
                LinearLayout monthParent=body;body=details;
                LinearLayout billDetails=card("帳單內容",(hasRent?"租金 $"+rent:"租金尚未建立")+"\n"+utility+detail+"\n已建立合計 $"+total+" · 已付 $"+paid+"\n尚需繳付 $"+(total-paid));
                for(JSONObject bill:entry.getValue())if(bill.has("meterReadingId")){JSONObject meter=find("meterReadings",bill.optString("meterReadingId"));if(meter!=null&&!meter.optString("file").isEmpty())action("查看此帳單的電表照片",()->viewMeterPhoto(meter.optString("file")));}
                final String billMonth=entry.getKey();action("複製 "+billMonth+" 繳費明細",()->copyBill(lease,billMonth));
                for(JSONObject r:entry.getValue())if(!lease.has("terminatedOn")&&Ledger.paid(r.getString("id"),receipts)==0)cardAction(billDetails,"撤銷"+r.optString("kind"),()->deleteInvoice(r));
                body=monthParent;
                cardAction(monthBox,"展開／收合明細",()->details.setVisibility(details.getVisibility()==View.GONE?View.VISIBLE:View.GONE));
            }
            if(months.isEmpty())card("尚無帳單","建立當月租金與水電後，會顯示在這裡。");
            card("收款紀錄","實際租金與水電收款，以及結算抵付紀錄。");
            for(int i=receipts.length()-1;i>=0;i--) {JSONObject r=receipts.getJSONObject(i);if(!lease.optString("id").equals(r.getString("leaseId")))continue;
                LinearLayout receiptBox=card(r.getString("date")+(r.optBoolean("cash",true)?" · 實收 $":" · 結算抵付 $")+r.getLong("amount"),(r.optString("accountId").isEmpty()?r.getString("account"):accountName(r.optString("accountId")))+"\n已分配 $"+Ledger.allocated(r)+" · 尚未分配 $"+(r.getLong("amount")-Ledger.allocated(r))+"\n"+r.optString("note"));
                if(!lease.has("terminatedOn"))cardAction(receiptBox,"撤銷",()->deleteReceipt(r));
            }
        }catch(Exception e){error(e);}finally{body=previous;}
    }
    private void billingForm(JSONObject lease) {
        if(lease.has("terminatedOn")){error(new IllegalArgumentException("已退租，帳單已結算"));return;}
        LinearLayout layout=form();
        layout.addView(text("租金、水費與電費在這裡一起建立；已存在的帳單沿用原金額，不會重複開立。新租約依繳款週期帶入本期租金；水電仍按選取月份另結，可填實際應收。",14,Color.DKGRAY));
        EditText month=dateField(layout,"帳單月份",true);month.setText(YearMonth.from(today()).toString());
        TextView rentLabel=text("租金",15,Color.DKGRAY);layout.addView(rentLabel);
        EditText rent=number(layout,"本月租金");rent.setText(lease.optString("rent"));if(RentTerms.scheduled(lease)){LocalDate begin=LocalDate.parse(lease.optString("start")),finish=LocalDate.parse(lease.optString("end"));if(today().isBefore(begin)||today().isAfter(finish))month.setText(YearMonth.from(begin).toString());}
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
                JSONObject existingRent=Ledger.forMonth(invoices,lease.optString("id"),RentTerms.rentMonth(lease,ym),"租金"),existingUtility=Ledger.forMonth(invoices,lease.optString("id"),ym,"水電");
                boolean collect=lease.optString("utilities").equals("管理者代收水電"),needUtility=collect&&existingUtility==null;
                JSONObject plan=Ledger.monthlyPlan(lease,ym,existingRent==null?amount(rent,false):0,needUtility?amount(water,true):0,needUtility?amount(prior,false):0,needUtility?amount(current,false):0,invoices,data("receipts"));
                JSONObject r=plan.getJSONObject("rent"),u=plan.optJSONObject("utility");
                summary.setText("租金 $"+r.getLong("rent")+(u==null?"\n水電：租客自行繳納":"\n水費 $"+u.getLong("water")+" · 電費 $"+u.getLong("electricity"))
                    +"\n本期租金＋本月水電合計 $"+plan.getLong("total")+"\n已分配收款 $"+plan.getLong("paid")+"\n本期帳單尚需繳付 $"+plan.getLong("remaining"));
            }catch(Exception e){summary.setText("請填完整月份、金額與電表讀數，完成後顯示本月合計。\n水費未知請先確認；帳單不代表已收款。");}
        };
        Runnable loadMonth=()->{
            try {
                YearMonth ym=YearMonth.parse(dateValue(month));JSONArray invoices=data("invoices"),receipts=data("receipts");
                JSONObject r=Ledger.forMonth(invoices,lease.optString("id"),RentTerms.rentMonth(lease,ym),"租金"),u=Ledger.forMonth(invoices,lease.optString("id"),ym,"水電");
                refreshing[0]=true;
                rent.setEnabled(r==null);rent.setText(r==null?String.valueOf(RentTerms.suggested(lease,ym)):r.optString("rent"));
                rentLabel.setText(r==null?(RentTerms.scheduled(lease)?"本期 "+RentTerms.period(lease,ym).getString("from")+" ～ "+RentTerms.period(lease,ym).getString("to")+" · 應繳 "+RentTerms.period(lease,ym).getString("due"):"租金（尚未建立）"):"租金帳單已建立 · 已付 $"+Ledger.paid(r.getString("id"),receipts)+"，沿用原帳單");
                boolean collect=lease.optString("utilities").equals("管理者代收水電");utilities.setVisibility(collect||u!=null?android.view.View.VISIBLE:android.view.View.GONE);
                water.setEnabled(u==null);prior.setEnabled(u==null);current.setEnabled(u==null);
                water.setText(u!=null?u.optString("water"):lease.isNull("water")?"":lease.optString("water"));
                JSONObject reading=MeterReadings.latest(data("meterReadings"),lease.optString("id"),ym.toString());
                JSONObject previousPeriod=MeterReadings.previousPeriod(data("meterReadings"),invoices,lease.optString("id"),ym.toString());
                prior.setText(u!=null?u.optString("previous"):reading!=null?reading.optString("previous"):previousPeriod==null?LeaseFiles.initialReading(lease,ym.toString()):previousPeriod.optString("current"));current.setText(u!=null?u.optString("current"):reading==null?"":reading.optString("current"));
                rateLabel.setText(u!=null?"水電帳單已建立，沿用原讀數與每度 $"+u.optLong("rate"):"每度 $"+lease.optLong("electricRate",5)+"；跨租客不自動沿用讀數"+(reading!=null?" · 已帶入抄表日期 "+reading.optString("date"):previousPeriod==null?(LeaseFiles.initialReading(lease,ym.toString()).isEmpty()?" · 上期讀數請自行填寫":" · 已帶入租約起始電度"):" · 上期讀數帶入 "+previousPeriod.optString("month")+" "+previousPeriod.optString("source")));
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
            JSONObject existingRent=Ledger.forMonth(before,lease.optString("id"),RentTerms.rentMonth(lease,ym),"租金"),existingUtility=Ledger.forMonth(before,lease.optString("id"),ym,"水電");
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
            if(property!=null)Owners.paymentAccount(fullBackup(),property.optString("id"),selectedDraftAccount(accounts,account).getString("id"));
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
        card("押金餘額 $"+sums.getLong("held"), "約定 $"+lease.getLong("deposit")+"\n累計已收 $"+sums.getLong("received")+" · 已退 $"+sums.getLong("refunded")+"\n抵付費用 $"+sums.getLong("applied")+" · 持有／待退餘額 $"+sums.getLong("held")+"\n押金不是租金收入；退租時可另做分項結算。");
        if(!lease.has("terminatedOn"))primaryAction("＋ 收到押金",()->depositForm(lease,false));if(sums.getLong("held")>0)action("退還押金",()->depositForm(lease,true));
        JSONArray rows=data("deposits");List<JSONObject> sorted=new ArrayList<>();for(int i=0;i<rows.length();i++)if(lease.getString("id").equals(rows.getJSONObject(i).getString("leaseId")))sorted.add(rows.getJSONObject(i));
        sorted.sort(Comparator.comparing((JSONObject r)->r.optString("date")).reversed());
        for(JSONObject r:sorted){LinearLayout record=card(r.getString("date")+" · "+r.getString("kind")+" $"+r.getLong("amount"),accountName(r.getString("accountId"))+"\n"+r.getString("note")+(r.getBoolean("void")?"\n已撤銷："+r.optString("voidReason"):""));
            if(r.getBoolean("void"))markVoided(record);else if(!lease.has("terminatedOn"))cardAction(record,"撤銷",()->voidMovement("deposits",r));}
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
        if(Settlements.due(s)>0)primaryAction("＋ 收到結算補繳",()->settlementPaymentForm(lease));
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
        Runnable load=()->{try{YearMonth ym=YearMonth.from(LocalDate.parse(dateValue(date)));JSONObject r=Ledger.forMonth(data("invoices"),lease.optString("id"),RentTerms.rentMonth(lease,ym),"租金"),u=Ledger.forMonth(data("invoices"),lease.optString("id"),ym,"水電");rent.setEnabled(r==null);JSONObject finalTerms=new JSONObject(lease.toString());if(RentTerms.scheduled(lease))finalTerms.put("end",dateValue(date));rent.setText(r==null?String.valueOf(RentTerms.suggested(finalTerms,ym)):r.optString("rent"));boolean edit=collect&&u==null;for(EditText f:new EditText[]{water,prior,current})f.setEnabled(edit);water.setText(u!=null?u.optString("water"):collect?(lease.isNull("water")?"":lease.optString("water")):"0");prior.setText(u!=null?u.optString("previous"):collect?LeaseFiles.initialReading(lease,ym.toString()):"0");current.setText(u!=null?u.optString("current"):collect?"":"0");}catch(Exception ignored){}};
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
        JSONObject backup=new JSONObject().put("format","rental-catalog").put("version",12);for(String key:KEYS)backup.put(key,data(key));return backup;
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
            primaryAction("＋ 新增銀行／共用現金帳戶",this::accountForm);return;
        }
        LinearLayout shortcuts=new LinearLayout(this);body.addView(shortcuts,new LinearLayout.LayoutParams(-1,-2));
        shortcut(shortcuts,"記支出",R.drawable.nav_ledger,this::expenseForm);
        shortcut(shortcuts,"語音記帳",R.drawable.action_voice,()->voiceEditor(""));
        shortcut(shortcuts,"提款／轉帳",R.drawable.action_transfer,this::transferForm);
        actionGroup("帳戶管理",new String[]{"新增銀行／共用現金帳戶","查看帳戶流水與對帳","對應舊收款的實際帳戶"},new Runnable[]{this::accountForm,this::accountsDialog,this::linkReceipts});
        actionGroup("更多功能",new String[]{"支出類別管理","押金總覽／收付","退租結算與待收／待退"},new Runnable[]{this::categoryManager,this::depositOverview,this::settlementOverview});
        TextView recent=text("近期紀錄",20,Color.rgb(77,57,39));recent.setTypeface(null,Typeface.BOLD);body.addView(recent);
        LinearLayout page=body,filters=new LinearLayout(this);filters.setOrientation(LinearLayout.VERTICAL);filters.setVisibility(expenseFiltersOpen?android.view.View.VISIBLE:android.view.View.GONE);
        Button filterToggle=styledButton("搜尋與篩選"+(expenseFiltersOpen?"  ▴":"  ▾"),false,false);filterToggle.setOnClickListener(v->{expenseFiltersOpen=!expenseFiltersOpen;filters.setVisibility(expenseFiltersOpen?View.VISIBLE:View.GONE);filterToggle.setText("搜尋與篩選"+(expenseFiltersOpen?"  ▴":"  ▾"));filterToggle.setContentDescription(expenseFiltersOpen?"搜尋與篩選，已展開":"搜尋與篩選，已收合");});page.addView(filterToggle,new LinearLayout.LayoutParams(-1,-2));page.addView(filters,new LinearLayout.LayoutParams(-1,-2));body=filters;
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
                String title=r.optString("date")+" · "+(transfer?"轉帳":categoryName(r.optString("category")))+" $"+r.optLong("amount");String detail=r.optString("description")+(voided?"\n已撤銷錯誤紀錄":"");
                if(transfer)detail+="\n"+accountName(r.optString("fromId"))+" → "+accountName(r.optString("toId"));else{detail+="\n付款："+accountName(r.optString("accountId"));JSONArray splits=r.optJSONArray("splits");if(splits!=null)for(int j=0;j<splits.length();j++)detail+="\n"+propertyName(splits.optJSONObject(j).optString("propertyId"))+" $"+splits.optJSONObject(j).optLong("amount");}
                if(!(title+" "+detail+" "+display(title+" "+detail)).toLowerCase(Locale.ROOT).contains(query))continue;totals.add(r);matches++;if(shown>=expenseLimit)continue;shown++;if(!transfer)expenseCard(r);else {LinearLayout box=card(title,detail);if(voided)markVoided(box);else cardAction(box,"撤銷",()->voidMovement("transfers",r));}
            }
            summary.setText("符合條件的有效支出 $"+totals.expenses+" · 轉帳 $"+totals.transfers+"\n包含尚未載入的符合紀錄；已撤銷不計，轉帳不列支出。" );
            if(matches==0)card("沒有符合條件的紀錄",rows.isEmpty()?"可以逐步補登半年資料。":"請調整月份、紀錄類型或搜尋文字。");else {body.addView(text("顯示 "+shown+" / "+matches+" 筆",14,Color.DKGRAY));if(matches>shown)action("載入更多紀錄",()->{expenseLimit+=15;drawExpenseRecords(records);});}
        }catch(Exception e){error(e);}finally{body=previous;}
    }
    private String receiptTenant(JSONObject receipt) {JSONObject lease=find("leases",receipt.optString("leaseId"));return lease==null?"":lease.optString("unitCodeSnapshot")+" · "+lease.optString("tenant");}
    private String categoryName(String id){return ExpenseCategories.name(data("expenseCategories"),id);}
    private void expenseCard(JSONObject row) throws JSONException {
        LinearLayout box=card("","");box.removeAllViews();
        TextView title=text(row.getString("date")+" · $"+fmt(row.getLong("amount")),17,Color.rgb(77,57,39));title.setTypeface(null,Typeface.BOLD);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.TOP);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        if(!row.optBoolean("void")){
            Button more=styledButton("⋯",false,false);more.setTextSize(24);more.setMinWidth(0);more.setMinimumWidth(0);more.setPadding(0,0,0,0);more.setContentDescription("此筆支出的更多操作：修改或撤銷");
            more.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.rgb(235,221,202)),new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT),new android.graphics.drawable.ColorDrawable(Color.WHITE)));
            heading.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));more.setOnClickListener(v->{PopupMenu menu=new PopupMenu(this,more);menu.getMenu().add(0,1,0,"修改");menu.getMenu().add(0,2,1,"撤銷");menu.setOnMenuItemClickListener(item->{JSONObject current=find("expenses",row.optString("id"));if(current==null||current.optBoolean("void")){Toast.makeText(this,"此筆紀錄已變更，請重新查看",Toast.LENGTH_SHORT).show();render();return true;}if(item.getItemId()==1)editExpense(current);else if(item.getItemId()==2)voidMovement("expenses",current);return true;});menu.show();});
        }
        box.addView(heading,new LinearLayout.LayoutParams(-1,-2));
        TextView descriptionText=text(row.getString("description"),17,Color.rgb(77,57,39));descriptionText.setTypeface(null,Typeface.BOLD);box.addView(descriptionText,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout content=new LinearLayout(this);content.setGravity(Gravity.CENTER_VERTICAL);LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);left.setPadding(0,dp(6),dp(10),0);content.addView(left,new LinearLayout.LayoutParams(0,-2,1));
        left.addView(text("分類："+categoryName(row.getString("category"))+"\n付款帳戶："+accountName(row.getString("accountId")),14,Color.rgb(112,105,92)));
        if(row.has("history")){Button history=styledButton("修改紀錄 · "+row.getJSONArray("history").length()+" 次",false,false);history.setTextSize(12);history.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);history.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.rgb(235,221,202)),new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT),new android.graphics.drawable.ColorDrawable(Color.WHITE)));history.setOnClickListener(v->expenseHistory(row.optString("id")));left.addView(history,new LinearLayout.LayoutParams(-1,-2));}
        JSONArray photos=ExpensePhotos.photos(row);if(photos.length()>0){LinearLayout right=new LinearLayout(this);right.setOrientation(LinearLayout.VERTICAL);right.setGravity(Gravity.END);ImageView thumbnail=new ImageView(this);thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);thumbnail.setBackground(surface(Color.rgb(239,237,233),8));right.addView(thumbnail,new LinearLayout.LayoutParams(dp(80),dp(80)));loadExpenseThumbnail(thumbnail,photos.getString(0));TextView count=text("照片 "+photos.length()+" 張",12,Color.rgb(112,105,92));right.addView(count);right.setContentDescription("查看此筆支出的 "+photos.length()+" 張照片");right.setOnClickListener(v->expensePhotoGallery(row));thumbnail.setOnClickListener(v->expensePhotoGallery(row));count.setOnClickListener(v->expensePhotoGallery(row));content.addView(right,new LinearLayout.LayoutParams(-2,-2));}
        box.addView(content,new LinearLayout.LayoutParams(-1,-2));
        JSONArray splits=row.getJSONArray("splits");Button toggle=styledButton("分攤明細 · "+splits.length()+" 個不動產  ▾",false,false);toggle.setTextSize(13);toggle.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);toggle.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.rgb(235,221,202)),new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT),new android.graphics.drawable.ColorDrawable(Color.WHITE)));box.addView(toggle,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout details=new LinearLayout(this);details.setOrientation(LinearLayout.VERTICAL);details.setVisibility(View.GONE);for(int i=0;i<splits.length();i++){JSONObject split=splits.getJSONObject(i);details.addView(text(propertyName(split.getString("propertyId"))+"  $"+fmt(split.getLong("amount")),14,Color.rgb(112,105,92)));}box.addView(details);
        toggle.setContentDescription("分攤明細，已收合");toggle.setOnClickListener(v->{boolean open=details.getVisibility()!=View.VISIBLE;details.setVisibility(open?View.VISIBLE:View.GONE);toggle.setText("分攤明細 · "+splits.length()+" 個不動產"+(open?"  ▴":"  ▾"));toggle.setContentDescription("分攤明細，"+(open?"已展開":"已收合"));});
        if(row.optBoolean("void")){markExpenseVoided(box);box.addView(text("已撤銷 · "+row.optString("voidReason"),12,Color.GRAY));}
    }
    private void markExpenseVoided(View view){
        if(view instanceof TextView&&!(view instanceof Button)){TextView text=(TextView)view;text.setTextColor(Color.GRAY);text.setTypeface(null,Typeface.NORMAL);text.setPaintFlags(text.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);}
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)markExpenseVoided(group.getChildAt(i));}
        if(view instanceof LinearLayout&&view.getParent()==body){GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(239,237,233));bg.setCornerRadius(dp(16));view.setBackground(bg);}
    }
    private void editExpense(JSONObject original){try{JSONObject draft=new JSONObject().put("date",original.getString("date")).put("amount",original.getLong("amount")).put("accountId",original.getString("accountId")).put("category",original.getString("category")).put("raw",original.getString("description")).put("splits",original.getJSONArray("splits")).put("propertyIds",new JSONArray());expenseForm(draft,new JSONObject(original.toString()));}catch(Exception e){error(e);}}
    private void expenseHistory(String id){
        LinearLayout layout=form();Runnable refresh=()->{layout.removeAllViews();LinearLayout previous=body;body=layout;try{JSONObject current=find("expenses",id);if(current==null)return;LinearLayout currentCard=card("目前版本",expenseVersionDetail(current)+(current.optBoolean("void")?"\n已撤銷":""));expenseVersionPhotos(currentCard,current);JSONArray history=current.optJSONArray("history");if(history!=null)for(int i=history.length()-1;i>=0;i--){JSONObject entry=history.getJSONObject(i);String time=java.time.Instant.parse(entry.getString("changedAt")).atZone(ZoneId.of("Asia/Taipei")).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));JSONObject prior=entry.getJSONObject("previous");LinearLayout historyCard=card("修改前版本 · "+time,expenseVersionDetail(prior));expenseVersionPhotos(historyCard,prior);}card("紀錄用途","修改前版本只供查閱，不列入餘額與報表。");}catch(Exception e){error(e);}finally{body=previous;}};refresh.run();showPanel("支出修改紀錄",layout,refresh);
    }
    private void expenseVersionPhotos(LinearLayout box,JSONObject row) throws JSONException {
        JSONArray photos=ExpensePhotos.photos(row);box.addView(text("照片憑證："+(photos.length()==0?"無":photos.length()+" 張"),14,Color.rgb(112,105,92)));
        if(photos.length()==0)return;
        LinearLayout strip=new LinearLayout(this);box.addView(strip,new LinearLayout.LayoutParams(-1,-2));
        for(int i=0;i<photos.length();i++){ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(surface(Color.rgb(239,237,233),10));image.setClipToOutline(true);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(88),1);if(i>0)params.leftMargin=dp(8);strip.addView(image,params);loadExpenseThumbnail(image,photos.getString(i));image.setContentDescription("查看此版本照片，第 "+(i+1)+" 張");image.setOnClickListener(v->expensePhotoGallery(row));}
    }
    private LinearLayout expenseSection(LinearLayout layout,String title){
        LinearLayout section=new LinearLayout(this);section.setOrientation(LinearLayout.VERTICAL);section.setPadding(0,dp(4),0,dp(4));layout.addView(section,new LinearLayout.LayoutParams(-1,-2));View divider=new View(this);divider.setBackgroundColor(Color.rgb(228,216,200));section.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));TextView heading=text(title,14,Color.rgb(90,61,37));heading.setTypeface(null,Typeface.BOLD);section.addView(heading);return section;
    }
    private void polishExpenseInput(View input){
        LinearLayout row=(LinearLayout)input.getParent();TextView label=(TextView)row.getChildAt(0);label.setTextSize(13);label.setLayoutParams(new LinearLayout.LayoutParams(dp(100),-2));row.setPadding(0,dp(2),0,dp(2));input.setBackground(surface(Color.rgb(250,247,242),8));input.setPadding(dp(10),dp(6),dp(10),dp(6));input.setMinimumHeight(dp(48));input.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
    }
    private String expenseVersionDetail(JSONObject row) throws JSONException {
        String detail="項目："+row.getString("description")+"\n金額：$"+fmt(row.getLong("amount"))+"\n付款日期："+row.getString("date")+"\n分類："+categoryName(row.getString("category"))+"\n付款帳戶："+accountName(row.getString("accountId"));JSONArray splits=row.getJSONArray("splits");for(int i=0;i<splits.length();i++){JSONObject split=splits.getJSONObject(i);detail+="\n"+propertyName(split.getString("propertyId"))+" $"+fmt(split.getLong("amount"));}return detail;
    }
    private void categoryManager(){
        LinearLayout layout=form();Runnable refresh=()->{layout.removeAllViews();LinearLayout previous=body;body=layout;try{
            action("＋ 新增支出類別",()->categoryEdit(null));card("類別用途","用於支出分類、各類合計與語音辨識。改名會統一顯示歷史紀錄；停用只隱藏新增選項，既有支出保留。排序會同步套用到記帳選單，可將常用類別移到最前。");
            JSONArray rows=data("expenseCategories");for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);LinearLayout box=card(r.getString("name"),r.getBoolean("active")?"使用中":"已停用 · 歷史紀錄保留");cardAction(box,"排序",()->categoryOrder(r.optString("id")));cardAction(box,"修改",()->categoryEdit(r));cardAction(box,r.getBoolean("active")?"停用":"啟用",()->{try{JSONArray next=data("expenseCategories");for(int j=0;j<next.length();j++)if(next.getJSONObject(j).getString("id").equals(r.getString("id")))next.getJSONObject(j).put("active",!r.getBoolean("active"));if(saveChecked("expenseCategories",next))render();}catch(Exception e){error(e);}});}
        }catch(Exception e){error(e);}finally{body=previous;}};refresh.run();showPanel("支出類別管理",layout,refresh);
    }
    private void categoryOrder(String id){
        JSONArray rows=data("expenseCategories");int position=-1;for(int i=0;i<rows.length();i++)if(id.equals(rows.optJSONObject(i).optString("id")))position=i;if(position<0)return;
        final int current=position;List<String> labels=new ArrayList<>();List<Integer> targets=new ArrayList<>();
        if(current>0){labels.add("移到最前");targets.add(0);labels.add("上移一位");targets.add(current-1);}if(current<rows.length()-1){labels.add("下移一位");targets.add(current+1);labels.add("移到最後");targets.add(rows.length()-1);}
        new AlertDialog.Builder(this).setTitle(categoryName(id)+" · 排序").setItems(labels.toArray(new String[0]),(d,w)->{try{
            JSONArray latest=data("expenseCategories");if(!latest.toString().equals(rows.toString()))throw new IllegalArgumentException("類別已變更，請重新排序");
            if(saveChecked("expenseCategories",ExpenseCategories.move(latest,id,targets.get(w))))render();
        }catch(Exception e){error(e);}}).setNegativeButton("取消",null).show();
    }
    private void categoryEdit(JSONObject original){
        LinearLayout layout=form();EditText name=labeledField(layout,"類別名稱");if(original!=null)name.setText(original.optString("name"));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(original==null?"新增支出類別":"修改支出類別").setView(layout).setNegativeButton("取消",null).setPositiveButton("儲存",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try{String n=requiredText(name);JSONArray next=data("expenseCategories");if(original==null)next.put(new JSONObject().put("id",UUID.randomUUID().toString()).put("name",n).put("active",true));else for(int i=0;i<next.length();i++)if(next.getJSONObject(i).getString("id").equals(original.getString("id")))next.getJSONObject(i).put("name",n);ExpenseCategories.validate(next,data("expenses"));if(saveChecked("expenseCategories",next)){dialog.dismiss();render();}}catch(Exception e){name.setError(e.getMessage());name.requestFocus();}}));dialog.show();
    }
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
        TextView summary=text("",15,Color.DKGRAY);layout.addView(summary);
        StringBuilder split=new StringBuilder("分攤草稿：\n");JSONArray ids=draft.getJSONArray("propertyIds"),splits=draft.getJSONArray("splits");for(int i=0;i<ids.length();i++)split.append(propertyName(ids.getString(i))).append("\n");for(int i=0;i<splits.length();i++)split.append(propertyName(splits.getJSONObject(i).getString("propertyId"))).append(" $").append(splits.getJSONObject(i).getLong("amount")).append("\n");if(ids.length()==0)split.append("待選不動產／逐棟填寫\n");TextView splitView=text(split.toString(),15,Color.DKGRAY);layout.addView(splitView);
        List<JSONObject> leases=new ArrayList<>();String[] options;JSONArray all=data("leases");for(int i=0;i<all.length();i++)if(!all.getJSONObject(i).has("terminatedOn"))leases.add(all.getJSONObject(i));options=new String[leases.size()+1];options[0]="請選租約（收到租金才需要）";for(int i=0;i<leases.size();i++){JSONObject l=leases.get(i),unit=find("units",l.optString("unitId"));options[i+1]=(unit==null?"":propertyName(unit.optString("propertyId"))+" · ")+l.optString("unitCodeSnapshot")+" · "+l.optString("tenant")+" · "+l.optString("start");}
        Spinner lease=choice(layout,"收租房號／租客",options);for(int i=0;i<leases.size();i++)if(leases.get(i).optString("id").equals(draft.optString("leaseId")))lease.setSelection(i+1);
        View leaseRow=(View)lease.getParent();TextView warning=text("",14,Color.rgb(137,83,15));layout.addView(warning);
        Runnable updateType=()->{
            int selected=kind.getSelectedItemPosition();leaseRow.setVisibility(selected==2?View.VISIBLE:View.GONE);splitView.setVisibility(selected==1?View.VISIBLE:View.GONE);
            String base="草稿日期："+(draft.optString("date").isEmpty()?"待補選":draft.optString("date"))+"\n總金額："+(draft.isNull("amount")?"待填":"$"+draft.optLong("amount"));
            if(selected==1)base+="\n支出分類："+(draft.optString("category").isEmpty()?"待選":categoryName(draft.optString("category")));
            if(selected==1||selected==2)base+="\n"+(selected==1?"付款帳戶：":"收款帳戶：")+(draft.optString("accountId").isEmpty()?"待選":accountName(draft.optString("accountId")));
            if(selected==3)base+="\n轉出："+(draft.optString("fromId").isEmpty()?"待選":accountName(draft.optString("fromId")))+"\n轉入："+(draft.optString("toId").isEmpty()?"待選":accountName(draft.optString("toId")));
            summary.setText(base);StringBuilder checks=new StringBuilder("需要核對：\n");
            if(selected==0)checks.append("• 請選帳務類型\n");if(draft.optString("date").isEmpty())checks.append("• 日期待補選\n");if(draft.isNull("amount"))checks.append("• 金額待填\n");
            if((selected==1||selected==2)&&draft.optString("accountId").isEmpty())checks.append(selected==1?"• 付款帳戶待選\n":"• 收款帳戶待選\n");
            if(selected==2&&lease.getSelectedItemPosition()==0)checks.append("• 收租房號／租客待選\n");if(selected==3&&(draft.optString("fromId").isEmpty()||draft.optString("toId").isEmpty()))checks.append("• 轉出／轉入帳戶待選\n");
            String chosen=selected==1?"支出":selected==2?"收款":selected==3?"轉帳":"";if(chosen.equals(draft.optString("kind"))){JSONArray originalWarnings=draft.optJSONArray("warnings");if(originalWarnings!=null)for(int i=0;i<originalWarnings.length();i++)checks.append("• ").append(originalWarnings.optString(i)).append("\n");}
            checks.append("請核對辨識文字及金額。下一頁仍可修改相關欄位，確認後才會入帳。");warning.setText(checks.toString());
        };
        AdapterView.OnItemSelectedListener linked=new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int n,long id){updateType.run();}};kind.setOnItemSelectedListener(linked);lease.setOnItemSelectedListener(linked);updateType.run();
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("檢查語音記帳草稿").setView(scroll).setNegativeButton("返回修改",null).setPositiveButton("帶入表單",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try{int selected=kind.getSelectedItemPosition();if(selected==0)throw new IllegalArgumentException("請選擇帳務類型");if(selected==2&&lease.getSelectedItemPosition()==0)throw new IllegalArgumentException("請選擇收租房號與租客");draft.put("kind",selected==1?"支出":selected==2?"收款":"轉帳");dialog.dismiss();source.dismiss();if(selected==1)expenseForm(draft);else if(selected==3)transferForm(draft);else receiptForm(leases.get(lease.getSelectedItemPosition()-1),draft);}catch(Exception e){error(e);}}));dialog.show();
    }
    private void voiceResult(int result,Intent intent) {
        if(result!=RESULT_OK||intent==null){voiceEditor("");return;}ArrayList<String> results=intent.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);if(results==null||results.isEmpty()){Toast.makeText(this,"未辨識到文字，請重試或手動輸入",Toast.LENGTH_LONG).show();voiceEditor("");return;}
        if(results.size()==1){voiceEditor(results.get(0));return;}
        new AlertDialog.Builder(this).setTitle("選擇正確的語音文字").setItems(results.toArray(new String[0]),(d,n)->voiceEditor(results.get(n))).setNegativeButton("返回草稿",(d,n)->voiceEditor("")).show();
    }

    private void expenseForm() {expenseForm(null);}
    private void expenseForm(JSONObject draft) {expenseForm(draft,null);}
    private void expenseForm(JSONObject draft,JSONObject original) {
        List<JSONObject> accounts=accountRows();JSONArray properties=data("properties");
        if(accounts.isEmpty()||properties.length()==0){error(new IllegalArgumentException("請先建立不動產與付款帳戶"));return;}
        addDraftAccountPlaceholder(accounts,draft);
        final List<String> photoNames=new ArrayList<>(),ownedPhotos=new ArrayList<>();
        try{JSONArray initial=draft!=null&&draft.has("photos")?ExpensePhotos.photos(draft):original!=null?ExpensePhotos.photos(original):new JSONArray();for(int i=0;i<initial.length();i++)photoNames.add(initial.getString(i));JSONArray owned=draft==null?null:draft.optJSONArray("ownedPhotos");if(owned!=null)for(int i=0;i<owned.length();i++)ownedPhotos.add(owned.getString(i));}catch(Exception e){error(e);return;}
        boolean[] handedOff={false},savedPhotos={false};
        LinearLayout layout=form();layout.setPadding(dp(12),dp(4),dp(12),dp(8));LinearLayout paymentSection=expenseSection(layout,"付款資訊");EditText date=dateField(paymentSection,"實際付款日期",false);date.setText(today().toString());Spinner account=accountChoice(paymentSection,accounts,"付款來源");polishExpenseInput(date);polishExpenseInput(account);
        List<JSONObject> categoryRows=new ArrayList<>();JSONArray categoryCatalog=data("expenseCategories");for(int i=0;i<categoryCatalog.length();i++)if(categoryCatalog.optJSONObject(i).optBoolean("active")||(original!=null&&categoryCatalog.optJSONObject(i).optString("id").equals(original.optString("category"))))categoryRows.add(categoryCatalog.optJSONObject(i));
        if(categoryRows.isEmpty()){error(new IllegalArgumentException("請先啟用至少一個支出類別"));return;}
        String[] categories=new String[categoryRows.size()+1];categories[0]="請選支出分類";for(int i=0;i<categoryRows.size();i++)categories[i+1]=categoryRows.get(i).optString("name");
        LinearLayout itemSection=expenseSection(layout,"支出內容");Spinner category=choice(itemSection,"支出類別",categories);EditText description=labeledField(itemSection,"項目／備註，例如B、C棟燈具更換");polishExpenseInput(category);polishExpenseInput(description);LinearLayout descriptionRow=(LinearLayout)description.getParent();TextView descriptionLabel=(TextView)descriptionRow.getChildAt(0);descriptionRow.removeView(descriptionLabel);itemSection.addView(descriptionLabel,itemSection.indexOfChild(descriptionRow));descriptionLabel.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));description.setSingleLine(false);description.setMinLines(1);description.setMaxLines(5);description.setGravity(Gravity.TOP|Gravity.START);
        LinearLayout photoSection=expenseSection(layout,"照片憑證");
        LinearLayout splitSection=expenseSection(layout,"金額與分攤");EditText total=labeledNumber(splitSection,"總付款金額");polishExpenseInput(total);
        TextView help=text("均分會覆寫各棟金額，仍可手動調整。不分攤的留空；分攤合計須等於付款金額。餘數由上至下各加 1 元。房貸只記總扣款，不拆本金利息。",13,Color.rgb(112,105,92));help.setVisibility(View.GONE);Button helpButton=styledButton("ⓘ",false,false);helpButton.setTextSize(18);helpButton.setPadding(0,0,0,0);helpButton.setMinWidth(0);helpButton.setMinimumWidth(0);helpButton.setContentDescription("顯示或收合分攤填寫說明");TextView splitHeading=(TextView)splitSection.getChildAt(1);splitSection.removeView(splitHeading);LinearLayout splitHeader=new LinearLayout(this);splitHeader.setGravity(Gravity.CENTER_VERTICAL);splitHeader.addView(splitHeading,new LinearLayout.LayoutParams(0,-2,1));splitHeader.addView(helpButton,new LinearLayout.LayoutParams(dp(48),dp(48)));splitSection.addView(splitHeader,1);splitSection.addView(help);helpButton.setOnClickListener(v->help.setVisibility(help.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        List<EditText> fields=new ArrayList<>();for(int i=0;i<properties.length();i++){EditText input=labeledNumber(splitSection,properties.optJSONObject(i).optString("name"));polishExpenseInput(input);fields.add(input);}
        if(draft!=null){date.setText(draft.optString("date"));setDraftAmount(total,draft);description.setText(draft.optString("raw"));selectDraftAccount(account,accounts,draft.optString("accountId"));for(int c=0;c<categoryRows.size();c++)if(categoryRows.get(c).optString("id").equals(draft.optString("category")))category.setSelection(c+1);try{JSONArray splits=draft.getJSONArray("splits"),ids=draft.getJSONArray("propertyIds");for(int i=0;i<fields.size();i++){String id=properties.optJSONObject(i).optString("id");for(int j=0;j<splits.length();j++)if(splits.getJSONObject(j).getString("propertyId").equals(id))fields.get(i).setText(splits.getJSONObject(j).optString("amount"));if(splits.length()==0&&ids.length()==1&&ids.getString(0).equals(id)&&!draft.isNull("amount"))fields.get(i).setText(draft.optString("amount"));}}catch(Exception e){error(e);}}
        TextView sumLabel=text("分攤合計：待輸入",16,Color.rgb(90,61,37));splitSection.addView(sumLabel);
        TextWatcher watch=new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){try{long sum=0;for(EditText f:fields){long n=amount(f,true);if(n>0)sum+=n;}long paid=amount(total,true);sumLabel.setText("分攤合計 $"+sum+(paid<0?"":" · 差額 $"+(paid-sum)));}catch(Exception e){sumLabel.setText("金額格式不正確");}}public void afterTextChanged(Editable e){}};
        total.addTextChangedListener(watch);for(EditText f:fields)f.addTextChangedListener(watch);watch.onTextChanged("",0,0,0);
        Button equal=styledButton("均分",false,false);equal.setTextSize(13);equal.setMinWidth(0);equal.setMinimumWidth(0);equal.setPadding(dp(10),dp(8),dp(10),dp(8));equal.setContentDescription("將總付款金額均分至下方全部不動產");
        LinearLayout totalRow=(LinearLayout)total.getParent();LinearLayout.LayoutParams equalParams=new LinearLayout.LayoutParams(-2,-2);equalParams.leftMargin=dp(6);totalRow.addView(equal,equalParams);
        equal.setOnClickListener(v->{try{
            long paid=amount(total,false);long[] values=Bookkeeping.equalSplit(paid,fields.size());
            for(int i=0;i<fields.size();i++)fields.get(i).setText(String.valueOf(values[i]));
            Toast.makeText(this,"已均分至 "+fields.size()+" 個不動產，可再手動調整"+(paid%fields.size()==0?"":"；餘數由上至下各加1元"),Toast.LENGTH_LONG).show();
        }catch(Exception e){total.setError(e.getMessage());total.requestFocus();}});

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(layout);
        android.app.Dialog dialog=new android.app.Dialog(this,android.R.style.Theme_Material_Light_NoActionBar);LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(Color.rgb(248,244,237));TextView pageTitle=text(original==null?"新增支出":"修改支出",20,Color.rgb(77,57,39));pageTitle.setTypeface(null,Typeface.BOLD);pageTitle.setPadding(dp(16),dp(12),dp(16),dp(10));page.addView(pageTitle,new LinearLayout.LayoutParams(-1,-2));page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout footer=new LinearLayout(this);footer.setPadding(dp(12),dp(6),dp(12),dp(6));footer.setBackgroundColor(Color.rgb(255,252,247));Button cancel=styledButton("取消",false,false),previewButton=styledButton("預覽",true,false);LinearLayout.LayoutParams cancelParams=new LinearLayout.LayoutParams(0,dp(48),1);cancelParams.rightMargin=dp(8);footer.addView(cancel,cancelParams);footer.addView(previewButton,new LinearLayout.LayoutParams(0,dp(48),1));page.addView(footer,new LinearLayout.LayoutParams(-1,-2));dialog.setContentView(page);cancel.setOnClickListener(v->dialog.dismiss());dialog.setCanceledOnTouchOutside(false);
        Runnable refreshPhotos=()->{photoSection.removeAllViews();photoSection.addView(text("照片憑證 · "+photoNames.size()+" / 3 張",14,Color.DKGRAY));
            LinearLayout strip=new LinearLayout(this);photoSection.addView(strip,new LinearLayout.LayoutParams(-1,-2));
            for(String name:new ArrayList<>(photoNames)){android.widget.FrameLayout tile=new android.widget.FrameLayout(this);LinearLayout.LayoutParams tileParams=new LinearLayout.LayoutParams(dp(88),dp(80));tileParams.rightMargin=dp(8);strip.addView(tile,tileParams);ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(surface(Color.rgb(239,237,233),8));image.setClipToOutline(true);tile.addView(image,new android.widget.FrameLayout.LayoutParams(-1,-1));loadExpenseThumbnail(image,name);image.setContentDescription("查看照片憑證");image.setOnClickListener(v->viewMeterPhoto(name));Button remove=styledButton("×",false,false);remove.setTextSize(22);remove.setMinWidth(0);remove.setMinimumWidth(0);remove.setPadding(0,0,0,0);remove.setContentDescription("移除此張照片");remove.setBackground(surface(Color.argb(235,255,252,247),20));android.widget.FrameLayout.LayoutParams removeParams=new android.widget.FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.END);tile.addView(remove,removeParams);remove.setOnClickListener(v->{photoNames.remove(name);((Runnable)photoSection.getTag()).run();});}
            if(photoNames.size()<3){Button add=styledButton("＋ 拍照／選相簿",false,false);add.setTextSize(13);photoSection.addView(add,new LinearLayout.LayoutParams(-1,dp(48)));add.setOnClickListener(v->{try{JSONObject snapshot=new JSONObject().put("date",date.getText().toString()).put("raw",description.getText().toString()).put("amount",total.getText().toString()).put("accountId",accounts.get(account.getSelectedItemPosition()).optString("id")).put("category",category.getSelectedItemPosition()==0?"":categoryRows.get(category.getSelectedItemPosition()-1).optString("id")).put("propertyIds",new JSONArray()).put("photos",new JSONArray(photoNames)).put("ownedPhotos",new JSONArray(ownedPhotos));JSONArray values=new JSONArray();for(int i=0;i<fields.size();i++)values.put(new JSONObject().put("propertyId",properties.optJSONObject(i).optString("id")).put("amount",fields.get(i).getText().toString()));snapshot.put("splits",values);pickExpensePhoto(snapshot,original,dialog,handedOff);}catch(Exception e){error(e);}});}
        };photoSection.setTag(refreshPhotos);refreshPhotos.run();
        dialog.setOnDismissListener(d->{if(!handedOff[0]&&!savedPhotos[0])for(String name:ownedPhotos)if(ExpensePhotos.photoName(name))new File(photoDir(),name).delete();if(!handedOff[0])getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingExpensePhoto").commit();});
        previewButton.setOnClickListener(w->{try{
            JSONArray splits=new JSONArray();for(int i=0;i<fields.size();i++){long n=amount(fields.get(i),true);if(n>0)splits.put(new JSONObject().put("propertyId",properties.getJSONObject(i).getString("id")).put("amount",n));}
            if(category.getSelectedItem().toString().equals("請選支出分類"))throw new IllegalArgumentException("請選擇支出類別");
            JSONObject a=selectedDraftAccount(accounts,account);for(int k=0;k<splits.length();k++)Owners.paymentAccount(fullBackup(),splits.getJSONObject(k).getString("propertyId"),a.getString("id"));JSONObject row=Bookkeeping.expense(dateValue(date),a.getString("id"),categoryRows.get(category.getSelectedItemPosition()-1).getString("id"),requiredText(description),amount(total,false),splits);
            row.put("photos",new JSONArray(photoNames));for(String name:photoNames){android.graphics.Bitmap check=photoPreview(new File(photoDir(),name));check.recycle();}
            if(original!=null){validateBackup(fullBackup().put("expenses",ExpenseEdits.replace(data("expenses"),original,row,java.time.Instant.now().toString())));}
            StringBuilder preview=new StringBuilder(row.getString("date")+" · "+categoryName(row.getString("category"))+"\n"+row.getString("description")+"\n付款："+a.getString("name")+" $"+row.getLong("amount"));
            for(int i=0;i<splits.length();i++)preview.append("\n").append(propertyName(splits.getJSONObject(i).getString("propertyId"))).append(" $").append(splits.getJSONObject(i).getLong("amount"));
            preview.append("\n照片憑證：").append(photoNames.size()).append(" 張");
            if(original!=null)preview.append("\n修改會保留原版本，只計算修改後金額；原對帳快照保留，餘額與報表會重算。");
            preview.append(LocalDate.parse(row.getString("date")).isBefore(LocalDate.parse(a.getString("start")))?"\n起算日前歷史：不再扣期初餘額。":"\n只扣付款帳戶一次，不因分攤重複扣款。");
            new AlertDialog.Builder(this).setMessage(display(String.valueOf(preview))).setNegativeButton("修改",null).setPositiveButton(original==null?"確認支出":"確認修改",(d,b)->{try{
                JSONArray rows=data("expenses");for(int i=0;i<rows.length();i++){JSONObject old=rows.getJSONObject(i);if(original!=null&&old.getString("id").equals(original.getString("id")))continue;if(old.getString("id").equals(row.getString("id")))return;
                    if(!old.optBoolean("void")&&old.getString("date").equals(row.getString("date"))&&old.getString("accountId").equals(row.getString("accountId"))&&old.getString("category").equals(row.getString("category"))&&old.getString("description").equals(row.getString("description"))&&old.getLong("amount")==row.getLong("amount"))throw new IllegalArgumentException("相同日期、帳戶、項目與金額已有紀錄，請核對；若為另一筆請填交易辨識。");}
                if(original==null)rows.put(row);else rows=ExpenseEdits.replace(rows,original,row,java.time.Instant.now().toString());if(saveChecked("expenses",rows)){savedPhotos[0]=true;for(String name:ownedPhotos)if(!photoNames.contains(name))new File(photoDir(),name).delete();dialog.dismiss();render();}
            }catch(Exception e){error(e);}}).show();
        }catch(Exception e){error(e);}});dialog.show();dialog.getWindow().setLayout(-1,-1);dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
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
    private void showPanel(String title,LinearLayout layout,Runnable refresh) {showPanel(title,layout,refresh,()->title);}
    private void showPanel(String title,LinearLayout layout,Runnable refresh,java.util.function.Supplier<String> currentTitle) {
        LinearLayout page=form();page.setPadding(dp(16),dp(12),dp(16),dp(12));page.setBackgroundColor(Color.rgb(248,244,237));
        TextView heading=text(title,21,Color.rgb(83,60,40));heading.setTypeface(null,Typeface.BOLD);page.addView(heading);
        ScrollView scroll=new ScrollView(this);layout.setPadding(0,dp(8),0,dp(12));scroll.addView(layout);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        AlertDialog dialog=new AlertDialog.Builder(this).setView(page).create();Button close=styledButton("關閉",false,false);close.setOnClickListener(v->dialog.dismiss());page.addView(close,new LinearLayout.LayoutParams(-1,-2));
        if(refresh!=null)livePanels.put(dialog,()->{int y=scroll.getScrollY();refresh.run();heading.setText(currentTitle.get());scroll.post(()->scroll.scrollTo(0,y));});
        dialog.setOnDismissListener(d->livePanels.remove(dialog));dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.rgb(248,244,237)));dialog.getWindow().setLayout(-1,-1);}
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
        LinearLayout layout=form();Button rename=styledButton("修改帳戶名稱",false,false);rename.setOnClickListener(v->{JSONObject current=find("accounts",account.optString("id"));if(current!=null)editName("accounts",current,"name","帳戶名稱");});layout.addView(rename);EditText date=dateField(layout,"查看／對帳日期（當日結束餘額）",false);date.setText(today().toString());
        Button edit=styledButton("修正期初設定（填錯時使用）",false,false);edit.setOnClickListener(v->editOpening(find("accounts",account.optString("id"))));layout.addView(edit);
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
                for(int i=0;i<receipts.length();i++){JSONObject r=receipts.getJSONObject(i);if(r.optBoolean("cash",true)&&id.equals(r.optString("accountId")))lines.add(new JSONObject().put("date",r.getString("date")).put("label","收款 "+receiptTenant(r)+" · "+accountName(r.optString("accountId"))).put("delta",r.getLong("amount")).put("note",r.optString("note")));}
                for(int i=0;i<expenses.length();i++){JSONObject r=expenses.getJSONObject(i);if(id.equals(r.getString("accountId"))&&!r.optBoolean("void"))lines.add(new JSONObject().put("date",r.getString("date")).put("label",categoryName(r.getString("category"))).put("delta",-r.getLong("amount")).put("note",r.getString("description")).put("sourceKey","expenses").put("sourceId",r.getString("id")));}
                for(int i=0;i<transfers.length();i++){JSONObject r=transfers.getJSONObject(i);if(r.optBoolean("void"))continue;boolean from=id.equals(r.getString("fromId")),to=id.equals(r.getString("toId"));if(from||to)lines.add(new JSONObject().put("date",r.getString("date")).put("label","轉帳 "+accountName(r.getString("fromId"))+" → "+accountName(r.getString("toId"))).put("delta",from?-r.getLong("amount"):r.getLong("amount")).put("note",r.getString("description")).put("sourceKey","transfers").put("sourceId",r.getString("id")));}
                JSONArray deposits=data("deposits");for(int i=0;i<deposits.length();i++){JSONObject r=deposits.getJSONObject(i);if(r.getString("kind").equals("押金抵付費用")||r.getBoolean("void")||!id.equals(r.getString("accountId")))continue;
                    lines.add(new JSONObject().put("date",r.getString("date")).put("label",r.getString("kind")+" · "+receiptTenant(r)).put("delta",r.getString("kind").equals("收到押金")?r.getLong("amount"):-r.getLong("amount")).put("note",r.getString("note")).put("sourceKey","deposits").put("sourceId",r.getString("id")));}
                JSONArray settlements=data("settlements");for(int i=0;i<settlements.length();i++){JSONObject st=settlements.getJSONObject(i);JSONArray payments=st.getJSONArray("payments");for(int j=0;j<payments.length();j++){JSONObject p=payments.getJSONObject(j);if(id.equals(p.getString("accountId")))lines.add(new JSONObject().put("date",p.getString("date")).put("label","退租結算補繳 · "+st.getString("unitCodeSnapshot")+" · "+st.getString("tenantSnapshot")).put("delta",p.getLong("amount")).put("note",p.getString("note")));}}
                lines.sort(Comparator.comparing((JSONObject r)->r.optString("date")).reversed());int n=0;
                for(JSONObject r:lines) {if(LocalDate.parse(r.getString("date")).isAfter(LocalDate.parse(end)))continue;long delta=r.getLong("delta");LinearLayout statementBox=card(r.getString("date")+" · "+r.getString("label"),(delta>=0?"+":"")+fmt(delta)+"\n"+r.getString("note")+(LocalDate.parse(r.getString("date")).isBefore(LocalDate.parse(start))?"\n歷史補登，未加減期初餘額":""));n++;if(r.has("sourceKey")){JSONObject source=find(r.optString("sourceKey"),r.optString("sourceId"));if(source!=null&&!(r.optString("sourceKey").equals("deposits")&&settled(source.optString("leaseId"))))cardAction(statementBox,"撤銷",()->voidMovement(r.optString("sourceKey"),source));}}
                if(n==0)card("尚無此日期前的流水","請記帳或對應舊收款；未有紀錄不代表存摺沒有交易。");
                JSONArray checks=data("reconciliations");for(int i=checks.length()-1;i>=0;i--){JSONObject r=checks.getJSONObject(i);if(!id.equals(r.getString("accountId")))continue;
                    long current=balance(currentAccount,r.getString("date"));card("對帳紀錄 "+r.getString("date"),"實際 $"+r.getLong("actual")+" · 當時帳面 $"+r.getLong("book")+"\n當時差額 $"+(r.getLong("actual")-r.getLong("book"))+"\n依目前登錄重算 $"+current+" · 差額 $"+(r.getLong("actual")-current)+"\n"+r.optString("note"));}
            }catch(Exception e){summary.setText("請輸入有效日期，且不得早於帳戶起算日。");}finally{body=previous;}
        };
        date.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){refresh.run();}public void afterTextChanged(Editable e){}});refresh.run();showPanel(account.optString("name")+" 流水與對帳",layout,refresh,()->accountName(account.optString("id"))+" 流水與對帳");
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
    private JSONObject reportShare(String audience,String id){JSONArray rows=data("reportShares");for(int i=0;i<rows.length();i++)if(audience.equals(rows.optJSONObject(i).optString("audience"))&&id.equals(rows.optJSONObject(i).optString("subjectId")))return rows.optJSONObject(i);return null;}
    private void requireUnshared(String audience,String id){JSONObject shared=reportShare(audience,id);if(shared!=null&&!shared.optString("status").equals("revoked"))throw new IllegalArgumentException("請先到報表連結管理停止原分享，再變更查看者或歸屬");}
    private void ownerManager(){
        LinearLayout layout=form();Runnable refresh=()->{layout.removeAllViews();LinearLayout previous=body;body=layout;try{primaryAction("＋ 新增房東",()->ownerForm(null));card("歸屬設定","每個不動產與帳戶可指定一位房東；未指定的資料不會出現在任何房東雲端報表。既有帳務不會自動移動或改金額。");JSONArray owners=data("owners");for(int i=0;i<owners.length();i++){JSONObject owner=owners.getJSONObject(i);LinearLayout box=card(owner.getString("name"),owner.getString("email"));cardAction(box,"修改房東",()->ownerForm(owner));cardAction(box,"管理報表連結",()->reportLinks("owner",owner.optString("id")));}for(String key:new String[]{"properties","accounts"}){JSONArray rows=data(key);for(int i=0;i<rows.length();i++){JSONObject item=rows.getJSONObject(i),owner=find("owners",item.optString("ownerId"));LinearLayout box=card((key.equals("properties")?"不動產：":"帳戶：")+item.getString("name"),owner==null?"尚未指定房東":owner.getString("name"));cardAction(box,"指定房東",()->assignOwner(key,item));}}}catch(Exception e){error(e);}finally{body=previous;}};refresh.run();showPanel("房東與歸屬管理",layout,refresh);
    }
    private void ownerForm(JSONObject original){LinearLayout layout=form();EditText name=field(layout,"房東名稱"),email=field(layout,"查看報表用 Gmail");if(original!=null){name.setText(original.optString("name"));email.setText(original.optString("email"));}AlertDialog dialog=new AlertDialog.Builder(this).setTitle(original==null?"新增房東":"修改房東").setView(layout).setNegativeButton("取消",null).setPositiveButton("儲存",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try{String n=requiredText(name),e=requiredText(email).toLowerCase(Locale.ROOT);if(!Owners.email(e))throw fieldError(email,"請填有效 Gmail");if(original!=null&&!e.equalsIgnoreCase(original.optString("email")))requireUnshared("owner",original.getString("id"));JSONObject row=original==null?new JSONObject().put("id",UUID.randomUUID().toString()):new JSONObject(original.toString());row.put("name",n).put("email",e);JSONArray rows=data("owners");if(original==null)rows.put(row);else for(int i=0;i<rows.length();i++)if(row.getString("id").equals(rows.getJSONObject(i).getString("id")))rows.put(i,row);if(saveChecked("owners",rows)){dialog.dismiss();render();}}catch(Exception e){error(e);}}));dialog.show();}
    private void assignOwner(String key,JSONObject item){try{JSONArray owners=data("owners");String[] options=new String[owners.length()+1];options[0]="尚未指定（不公開）";for(int i=0;i<owners.length();i++)options[i+1]=owners.getJSONObject(i).getString("name");new AlertDialog.Builder(this).setTitle(item.optString("name")+"：指定房東").setItems(options,(d,n)->{try{String id=n==0?"":owners.getJSONObject(n-1).getString("id"),old=item.optString("ownerId");if(!id.equals(old)){if(!old.isEmpty())requireUnshared("owner",old);if(!id.isEmpty())requireUnshared("owner",id);}JSONArray rows=data(key);for(int i=0;i<rows.length();i++)if(item.getString("id").equals(rows.getJSONObject(i).getString("id")))rows.getJSONObject(i).put("ownerId",id);if(saveChecked(key,rows))render();}catch(Exception e){error(e);}}).setNegativeButton("取消",null).show();}catch(Exception e){error(e);}}
    private void reportLinks(String audience,String id){
        LinearLayout layout=form();Runnable refresh=()->{layout.removeAllViews();LinearLayout previous=body;body=layout;try{JSONObject recipient=find(audience.equals("owner")?"owners":"leases",id);if(recipient==null)return;String email=recipient.optString("email");card(audience.equals("owner")?recipient.optString("name"):recipient.optString("unitCodeSnapshot"),"查看帳號："+email);JSONObject link=reportShare(audience,id);card("分享狀態",link==null?"尚未同步或確認分享權限":link.optString("status").equals("revoked")?"上次已確認停止分享 · "+syncTime(link.optString("syncedAt")):!link.optString("status").equals("shared")?"分享狀態待確認，請重新同步或停止分享":"上次確認可查看 · "+syncTime(link.optString("syncedAt"))+"\n帳號："+link.optString("email")+"\n手機修改後需要重新同步；狀態以 Google 實際權限為準。");
            if(audience.equals("owner"))action("選月份並更新房東報表",this::ownerReport);else action("預覽並更新租客報表",()->tenantReport(id));
            if(link!=null&&link.optString("status").equals("shared")){action("開啟報表連結",()->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(SyncProtocol.reportUrl(link.optString("url")))));}catch(Exception e){error(e);}});action("複製報表連結",()->{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(android.content.ClipData.newPlainText("報表連結",link.optString("url")));Toast.makeText(this,"已複製；僅指定 Gmail 可查看",Toast.LENGTH_LONG).show();});}action("停止 Google 分享（保留手機資料）",()->sharedSync(audience,id,null,true));
        }catch(Exception e){error(e);}finally{body=previous;}};refresh.run();showPanel("報表連結管理",layout,refresh);
    }
    private void tenantReport(String id){try{JSONObject report=SharedReports.tenant(fullBackup(),id),terms=report.getJSONObject("terms"),deposit=report.getJSONObject("depositSummary");LinearLayout layout=form();LinearLayout previous=body;body=layout;try{card("租約："+terms.getString("code"),terms.getString("start")+" ～ "+terms.getString("end")+"\n約定押金 $"+fmt(terms.getLong("deposit"))+" · 持有押金 $"+fmt(deposit.getLong("held")));card("報表範圍","僅此份租約的押金、租期、租金水電帳單與歷史繳費；附有合約時同步私人 PDF。不含其他租客、房東帳戶或管理備註。");JSONArray bills=report.getJSONArray("bills");for(int i=0;i<bills.length();i++){JSONObject b=bills.getJSONObject(i);card(b.getString("month")+" · "+b.getString("kind"),"租金 $"+fmt(b.getLong("rent"))+" · 水費 $"+fmt(b.getLong("water"))+" · 電費 $"+fmt(b.getLong("electricity"))+"\n已抵付 $"+fmt(b.getLong("paid"))+" · 未繳 $"+fmt(b.getLong("remaining")));}action("更新並授權租客查看",()->sharedSync("tenant",id,report,false));action("連結與停止分享",()->reportLinks("tenant",id));}finally{body=previous;}showPanel("租客專屬報表預覽",layout,null);}catch(Exception e){error(e);}}
    private void sharedSync(String audience,String subjectId,JSONObject report,boolean revoke){
        if(googleSyncRunning){error(new IllegalArgumentException("同步正在執行，請稍候"));return;}
        try{JSONObject recipient=find(audience.equals("owner")?"owners":"leases",subjectId);if(recipient==null)throw new IllegalArgumentException("報表對象不存在");String email=recipient.getString("email").toLowerCase(Locale.ROOT);if(!Owners.email(email))throw new IllegalArgumentException("請先填有效的查看 Gmail");JSONObject settings=SyncSettings.load(this);if(settings==null){googleSyncSettings();return;}JSONObject old=reportShare(audience,subjectId);if(old!=null&&!old.optString("endpoint",settings.getString("url")).equals(settings.getString("url"))&&!old.optString("status").equals("revoked"))throw new IllegalArgumentException("仍有舊 Google 服務的分享，請先改回原部署網址停止分享");
            new AlertDialog.Builder(this).setMessage(revoke?"停止此對象的報表與合約分享？不會刪除手機帳務。":"將此報表"+(audience.equals("tenant")&&report.optBoolean("hasContract")?"與合約 PDF":"")+"同步到你的 Google 雲端，並僅授權 "+email+" 唯讀查看？不會寄送通知信，連結由你自行傳送。").setNegativeButton("取消",null).setPositiveButton(revoke?"停止分享":"確認更新與分享",(d,w)->{
                googleSyncRunning=true;final String configuration=getSharedPreferences("googleSync",MODE_PRIVATE).getString("connection","");AlertDialog progress=busy(revoke?"正在停止分享…":"正在更新報表與確認權限…");new Thread(()->{try{JSONObject request=new JSONObject().put("audience",audience).put("subjectId",subjectId).put("email",email);if(!revoke){request.put("data",report);if(audience.equals("tenant")){String name=recipient.optString("contractFile");if(!name.isEmpty()){File file=new File(photoDir(),name);validateContractPdf(file);byte[] bytes=java.nio.file.Files.readAllBytes(file.toPath());request.put("contract",new JSONObject().put("base64",android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)));}}}SyncProtocol.send(settings.getString("url"),settings.getString("token"),"sharedCheck",null);JSONObject response=SyncProtocol.send(settings.getString("url"),settings.getString("token"),revoke?"sharedRevoke":"sharedSync",request);runOnUiThread(()->{googleSyncRunning=false;progress.dismiss();try{if(!configuration.equals(getSharedPreferences("googleSync",MODE_PRIVATE).getString("connection","")))throw new IllegalArgumentException("同步設定已改變，請重新確認 Google 分享狀態");JSONObject link=new JSONObject(response.toString()).put("endpoint",settings.getString("url"));JSONArray rows=data("reportShares");boolean replaced=false;for(int i=0;i<rows.length();i++)if(audience.equals(rows.getJSONObject(i).optString("audience"))&&subjectId.equals(rows.getJSONObject(i).optString("subjectId"))){rows.put(i,link);replaced=true;break;}if(!replaced)rows.put(link);if(saveChecked("reportShares",rows)){render();refreshPanels();Toast.makeText(this,revoke?"已確認停止分享":"已更新並確認指定帳號可查看",Toast.LENGTH_LONG).show();reportLinks(audience,subjectId);}}catch(Exception e){error(e);}});
                }catch(Exception e){runOnUiThread(()->{googleSyncRunning=false;progress.dismiss();markShareUnconfirmed(audience,subjectId);error(new IllegalArgumentException(e.getMessage()==null?"分享未完成，請更新 Google 部署並重試":e.getMessage()));});}}).start();}).show();
        }catch(Exception e){error(e);}
    }
    private void markShareUnconfirmed(String audience,String id){try{JSONArray rows=data("reportShares");JSONObject item=null;for(int i=0;i<rows.length();i++)if(audience.equals(rows.getJSONObject(i).optString("audience"))&&id.equals(rows.getJSONObject(i).optString("subjectId")))item=rows.getJSONObject(i);if(item==null){item=new JSONObject().put("audience",audience).put("subjectId",id);rows.put(item);}item.put("status","unconfirmed");JSONObject settings=SyncSettings.load(this);if(settings!=null)item.put("endpoint",settings.optString("url"));save("reportShares",rows);refreshPanels();}catch(Exception ignored){}}
    private void tenantReportPicker(){JSONArray leases=data("leases");String[] names=new String[leases.length()];for(int i=0;i<leases.length();i++)names[i]=leases.optJSONObject(i).optString("unitCodeSnapshot")+" · "+leases.optJSONObject(i).optString("tenant")+" · "+leases.optJSONObject(i).optString("start");new AlertDialog.Builder(this).setTitle("選擇租約").setItems(names,(d,n)->tenantReport(leases.optJSONObject(n).optString("id"))).setNegativeButton("取消",null).show();}
    private void ownerReport() {
        LinearLayout layout=form();JSONArray owners=data("owners");String[] choices=new String[owners.length()+1];choices[0]="全部（管理者本機，不分享）";for(int i=0;i<owners.length();i++)choices[i+1]=owners.optJSONObject(i).optString("name");Spinner ownerChoice=choice(layout,"報表房東",choices);EditText month=dateField(layout,"報表月份",true);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);layout.addView(list);
        Runnable refresh=()->{LinearLayout previous=body;body=list;list.removeAllViews();try {
            String ownerId=ownerChoice.getSelectedItemPosition()==0?"":owners.getJSONObject(ownerChoice.getSelectedItemPosition()-1).getString("id");JSONObject report=ownerId.isEmpty()?OwnerReport.build(fullBackup(),YearMonth.parse(dateValue(month))):SharedReports.owner(fullBackup(),YearMonth.parse(dateValue(month)),ownerId);JSONObject total=report.getJSONObject("total");
            card("每月總覽",ownerSummary(total));
            card("報表口徑","帳單應收按帳單月份；抵付狀態截至月底，包含押金抵付。實收按收到日期，租金與水電合併。押金不列收入，提款轉帳不列支出。未建帳單不代表已繳清；此淨收款不是銀行餘額或會計損益。");
            if(!ownerId.isEmpty()){action("更新並分享此房東報表",()->sharedSync("owner",ownerId,report,false));action("報表連結與停止分享",()->reportLinks("owner",ownerId));}else card("分享前請先選房東","管理者總表含全部資料，僅供本機查看與匯出。請先在房東與歸屬管理指定不動產、帳戶，再選房東產生去個資報表。");
            action("匯出Excel報表（千位逗點／分工作表）",()->exportOwnerWorkbook(report));
            action("匯出此月份CSV（原始資料）",()->exportOwnerReport(report));
            JSONArray properties=report.getJSONArray("properties");for(int i=0;i<properties.length();i++){JSONObject row=properties.getJSONObject(i);card(row.getString("name"),ownerSummary(row));}
            if(!ownerId.isEmpty()){JSONArray units=report.getJSONArray("units");for(int i=0;i<units.length();i++){JSONObject u=units.getJSONObject(i);card(propertyName(u.getString("propertyId"))+" · "+u.getString("code"),u.getString("status")+(u.has("end")?" · 租期至 "+u.getString("end"):""));}JSONArray accounts=report.getJSONArray("accounts");for(int i=0;i<accounts.length();i++){JSONObject account=accounts.getJSONObject(i);card(account.getString("name"),account.optBoolean("withheld")?"帳戶含尚未核對歸屬的交易，暫不公開餘額":"月底帳面餘額 $"+fmt(account.getLong("balance")));}}
            JSONArray tenants=report.getJSONArray("tenants");for(int i=0;i<tenants.length();i++){JSONObject row=tenants.getJSONObject(i);card(propertyName(row.getString("propertyId"))+" · "+row.getString("code")+(ownerId.isEmpty()?" · "+row.getString("name"):""),row.getString("status")+"\n本月已建帳單 $"+row.getLong("billed")+" · 月底已抵付 $"+row.getLong("paid")+"\n本月帳單未付 $"+row.getLong("remaining")+"\n本月租金水電實收 $"+row.getLong("cash")+" · 結算補繳 $"+row.getLong("topups")+"\n月底持有押金 $"+row.getLong("held"));}
        }catch(Exception e){card("報表無法產生","請檢查帳務資料："+e.getMessage());}finally{body=previous;}};
        month.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){refresh.run();}public void afterTextChanged(Editable e){}});refresh.run();ownerChoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int n,long id){refresh.run();}});showPanel("房東的每月總報表",layout,refresh);
    }
    private String ownerSummary(JSONObject row)throws JSONException{return "本月已建帳單 $"+row.getLong("billed")+" · "+row.getInt("invoiceCount")+" 筆\n截至月底已抵付 $"+row.getLong("paid")+" · 本月帳單未付 $"+row.getLong("remaining")+"\n本月租金水電實收 $"+row.getLong("cash")+"\n本月結算補繳實收 $"+row.getLong("topups")+"\n本月分攤支出 $"+row.getLong("expenses")+"\n本月淨收款 $"+row.getLong("net")+"（不含押金及轉帳）\n月底持有押金 $"+row.getLong("held");}
    private String syncTime(String value) {try{return java.time.Instant.parse(value).atZone(ZoneId.of("Asia/Taipei")).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));}catch(Exception e){return "尚未同步";}}
    private void testSharedConnection(){try{JSONObject settings=SyncSettings.load(this);if(settings==null)return;if(googleSyncRunning)return;googleSyncRunning=true;AlertDialog progress=busy("正在確認新版 Google 報表服務…");new Thread(()->{try{SyncProtocol.send(settings.getString("url"),settings.getString("token"),"sharedCheck",null);runOnUiThread(()->{googleSyncRunning=false;progress.dismiss();getSharedPreferences("googleSync",MODE_PRIVATE).edit().putString("status","新版連線成功，尚未上傳帳務或分享").commit();new AlertDialog.Builder(this).setMessage("新版連線成功。請先設定房東歸屬，再到房東／租客報表預覽並確認分享。").setPositiveButton("知道了",null).show();});}catch(Exception e){runOnUiThread(()->{googleSyncRunning=false;progress.dismiss();error(new IllegalArgumentException("新版服務尚未確認，請檢查 Code.gs、Shared.gs、資訊清單與重新部署設定。\n"+e.getMessage()));});}}).start();}catch(Exception e){error(e);}}
    private void googleSyncSettings() {
        LinearLayout layout=form();EditText url=field(layout,"Google部署網址（/exec）"),token=field(layout,"App同步金鑰");token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);token.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        try {JSONObject settings=SyncSettings.load(this);if(settings!=null){url.setText(settings.getString("url"));token.setText(settings.getString("token"));}}catch(Exception e){layout.addView(text("設定無法解密，請重新填寫連線資訊。",14,Color.DKGRAY));}
        android.content.SharedPreferences prefs=getSharedPreferences("googleSync",MODE_PRIVATE);layout.addView(text("需部署新版 Code.gs、Shared.gs 與資訊清單，重新授權 Sheets／Drive 後更新部署。房東與租客分開手動同步，成功確認權限後才提供連結。金鑰只保存在手機，不放完整備份或GitHub。",14,Color.DKGRAY));layout.addView(text("最後成功月份："+prefs.getString("lastMonth","尚未同步")+"\n最後成功時間："+syncTime(prefs.getString("lastSuccess",""))+"\n最近結果："+prefs.getString("status","尚未測試連線"),14,Color.DKGRAY));
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Google同步設定／狀態").setView(scroll).setNegativeButton("關閉",null).setNeutralButton("測試連線",null).setPositiveButton("保存設定",null).create();dialog.setOnShowListener(v->{Runnable save=()->{try{if(googleSyncRunning)throw new IllegalArgumentException("同步正在執行，請完成後再變更設定");String endpoint=requiredText(url),secret=requiredText(token);JSONObject current=null;try{current=SyncSettings.load(this);}catch(Exception ignored){}if(current==null||!endpoint.equals(current.optString("url"))||!secret.equals(current.optString("token")))SyncSettings.save(this,endpoint,secret);dialog.dismiss();render();}catch(Exception e){error(e);}};dialog.getButton(-1).setOnClickListener(w->save.run());dialog.getButton(-3).setOnClickListener(w->{save.run();if(!dialog.isShowing())testSharedConnection();});});dialog.show();
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
                for(int ci=0;ci<data("expenseCategories").length();ci++){String category=data("expenseCategories").optJSONObject(ci).optString("id");long sum=0;for(int i=0;i<expenses.length();i++){JSONObject r=expenses.getJSONObject(i);if(!r.optBoolean("void")&&r.getString("date").startsWith(ym.toString())&&category.equals(r.getString("category")))sum+=r.getLong("amount");}card(categoryName(category),"$"+sum);}
                List<JSONObject> rows=new ArrayList<>();for(int i=0;i<expenses.length();i++){JSONObject r=expenses.getJSONObject(i);if(!r.optBoolean("void")&&r.getString("date").startsWith(ym.toString()))rows.add(r);}rows.sort(Comparator.comparing((JSONObject r)->r.optString("date")).reversed());
                for(JSONObject r:rows){StringBuilder detail=new StringBuilder(accountName(r.getString("accountId"))+"\n"+r.getString("description"));JSONArray splits=r.getJSONArray("splits");for(int i=0;i<splits.length();i++)detail.append("\n").append(propertyName(splits.getJSONObject(i).getString("propertyId"))).append(" $").append(splits.getJSONObject(i).getLong("amount"));
                    expenseCard(r);}
            }catch(Exception e){card("請選擇有效月份","點選月份欄位選取年與月");}finally{body=previous;}
        };
        month.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){refresh.run();}public void afterTextChanged(Editable e){}});refresh.run();showPanel("支出報表",layout,refresh);
    }

    private static final int CAMERA_PHOTO=43, PICK_PHOTO=44;
    private static final int PICK_CONTRACT=48;
    private static final int CAMERA_EXPENSE=49, PICK_EXPENSE=50;
    private static final int CAMERA_INITIAL=51, PICK_INITIAL=52;
    private static final int CAMERA_LEASE_ATTACHMENT=53, PICK_LEASE_PHOTO=54, PICK_LEASE_PDF=55;
    private final java.util.concurrent.ExecutorService expenseImages=java.util.concurrent.Executors.newFixedThreadPool(2);
    private void loadExpenseThumbnail(ImageView image,String name){
        image.setContentDescription("照片縮圖，點擊查看");expenseImages.execute(()->{try{File file=new File(photoDir(),name);android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();opts.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(file.getPath(),opts);if(opts.outWidth<=0||opts.outHeight<=0)throw new IOException();opts.inJustDecodeBounds=false;opts.inSampleSize=1;while(Math.max(opts.outWidth,opts.outHeight)/opts.inSampleSize>256)opts.inSampleSize*=2;android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(file.getPath(),opts);if(bitmap==null)throw new IOException();runOnUiThread(()->{if(isDestroyed()){bitmap.recycle();return;}image.setImageBitmap(bitmap);});}catch(Exception e){runOnUiThread(()->image.setContentDescription("照片無法載入，請查看完整備份"));}});
    }
    private void expensePhotoGallery(JSONObject row){
        try{JSONArray photos=ExpensePhotos.photos(row);LinearLayout layout=form();layout.addView(text("共 "+photos.length()+" 張照片；點擊可開啟原圖",14,Color.DKGRAY));for(int i=0;i<photos.length();i++){String name=photos.getString(i);ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);layout.addView(image,new LinearLayout.LayoutParams(-1,dp(220)));loadExpenseThumbnail(image,name);image.setOnClickListener(v->viewMeterPhoto(name));}ScrollView scroll=new ScrollView(this);scroll.addView(layout);new AlertDialog.Builder(this).setTitle("支出照片憑證").setView(scroll).setPositiveButton("關閉",null).show();}catch(Exception e){error(e);}
    }
    private void pickExpensePhoto(JSONObject draft,JSONObject original,android.app.Dialog source,boolean[] handedOff){
        new AlertDialog.Builder(this).setTitle("照片來源").setItems(new String[]{"開啟相機拍照","從相簿／檔案選取"},(d,n)->{String name="exp-"+UUID.randomUUID()+".jpg";try{
            JSONObject pending=new JSONObject().put("draft",draft).put("original",original==null?JSONObject.NULL:original).put("file",name);if(!getSharedPreferences("workflow",MODE_PRIVATE).edit().putString("pendingExpensePhoto",pending.toString()).commit())throw new IOException("草稿保存失敗");
            if(n==0){Uri uri=photoUri(name);Intent camera=new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).putExtra(android.provider.MediaStore.EXTRA_OUTPUT,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);camera.setClipData(android.content.ClipData.newRawUri("expense",uri));startActivityForResult(camera,CAMERA_EXPENSE);}else startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),PICK_EXPENSE);
            handedOff[0]=true;source.dismiss();
        }catch(Exception e){getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingExpensePhoto").commit();new File(photoDir(),name).delete();error(new IllegalArgumentException("無法啟動相機或相簿，表單資料保留，請重試"));}}).setNegativeButton("取消",null).show();
    }
    private void reopenExpensePhotoDraft(JSONObject pending,boolean added){
        try{JSONObject draft=pending.getJSONObject("draft");String file=pending.getString("file");if(added){JSONArray names=draft.getJSONArray("photos"),owned=draft.getJSONArray("ownedPhotos");names.put(file);owned.put(file);ExpensePhotos.photos(draft);}else new File(photoDir(),file).delete();getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingExpensePhoto").commit();expenseForm(draft,pending.isNull("original")?null:pending.getJSONObject("original"));}catch(Exception e){error(e);}
    }
    private void expensePhotoResult(int request,int result,Intent intent){
        String raw=getSharedPreferences("workflow",MODE_PRIVATE).getString("pendingExpensePhoto",null);if(raw==null)return;try{JSONObject pending=new JSONObject(raw);String name=pending.getString("file");if(!ExpensePhotos.photoName(name))throw new IOException();revokeUriPermission(photoUri(name),Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if(result!=RESULT_OK||(request==PICK_EXPENSE&&(intent==null||intent.getData()==null))){reopenExpensePhotoDraft(pending,false);return;}
            AlertDialog progress=busy("正在讀取照片…");new Thread(()->{try{
                File file=new File(photoDir(),name);if(request==PICK_EXPENSE){try(InputStream in=getContentResolver().openInputStream(intent.getData());OutputStream out=new FileOutputStream(file)){if(in==null)throw new IOException();byte[] buffer=new byte[8192];int count;long total=0;while((count=in.read(buffer))!=-1){total+=count;if(total>BackupArchive.PHOTO_LIMIT)throw new IOException();out.write(buffer,0,count);}}}
                android.graphics.Bitmap bitmap=photoPreview(file);bitmap.recycle();runOnUiThread(()->{progress.dismiss();reopenExpensePhotoDraft(pending,true);});
            }catch(Exception e){runOnUiThread(()->{progress.dismiss();reopenExpensePhotoDraft(pending,false);error(new IllegalArgumentException("照片無法讀取，請選25MB以下可開啟的圖片"));});}}).start();
        }catch(Exception e){error(e);}
    }
    private boolean hasMeterHistory(String leaseId){
        if(hasBilling(leaseId))return true;JSONArray rows=data("meterReadings");for(int i=0;i<rows.length();i++)if(leaseId.equals(rows.optJSONObject(i).optString("leaseId")))return true;return false;
    }
    private boolean leaseAttachmentPending(){if(getSharedPreferences("workflow",MODE_PRIVATE).contains("pendingLeaseAttachment")){Toast.makeText(this,"附件正在處理，請稍候",Toast.LENGTH_SHORT).show();return true;}return false;}
    private Button leaseAttachmentButton(String label){Button button=styledButton(label,false,false);button.setTextSize(13);button.setMinWidth(0);button.setMinimumWidth(0);button.setPadding(dp(4),0,dp(4),0);return button;}
    private void drawLeaseAttachments(LeaseEditorSession editor,LinearLayout layout){
        layout.removeAllViews();String photo=editor.attachments.optString("photo"),pdf=editor.attachments.optString("pdf");
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(4),0,dp(10));layout.addView(row,new LinearLayout.LayoutParams(-1,-2));
        android.widget.FrameLayout thumbnail=new android.widget.FrameLayout(this);LinearLayout.LayoutParams thumbParams=new LinearLayout.LayoutParams(dp(80),dp(80));thumbParams.rightMargin=dp(12);row.addView(thumbnail,thumbParams);
        if(!photo.isEmpty()){ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(surface(Color.rgb(239,237,233),10));image.setClipToOutline(true);thumbnail.addView(image,new android.widget.FrameLayout.LayoutParams(-1,-1));loadExpenseThumbnail(image,photo);image.setOnClickListener(v->viewMeterPhoto(photo));Button remove=leaseAttachmentButton("×");remove.setTextSize(22);remove.setBackground(surface(Color.argb(235,255,252,247),20));remove.setContentDescription("移除起始電表照片，儲存後生效");thumbnail.addView(remove,new android.widget.FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.END));remove.setOnClickListener(v->{try{if(leaseAttachmentPending())return;LeaseAttachments.remove(editor.attachments,false);editor.refresh.run();}catch(Exception e){error(e);}});}else{TextView empty=text("照片",14,Color.GRAY);empty.setGravity(Gravity.CENTER);empty.setBackground(surface(Color.rgb(239,237,233),10));thumbnail.addView(empty,new android.widget.FrameLayout.LayoutParams(-1,-1));}
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.addView(text("起始電表照片",14,Color.rgb(90,61,37)));info.addView(text(photo.isEmpty()?"尚未附上":"已選取 · 點縮圖查看",12,Color.GRAY));row.addView(info,new LinearLayout.LayoutParams(0,-2,1));Button choosePhoto=leaseAttachmentButton(photo.isEmpty()?"新增":"更換");row.addView(choosePhoto,new LinearLayout.LayoutParams(dp(72),dp(48)));choosePhoto.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("起始電表照片").setItems(new String[]{"拍照","從相簿選取"},(d,n)->pickLeaseAttachment(editor,n==0?CAMERA_LEASE_ATTACHMENT:PICK_LEASE_PHOTO)).setNegativeButton("取消",null).show());
        View line=new View(this);line.setBackgroundColor(Color.rgb(228,216,200));layout.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        LinearLayout pdfRow=new LinearLayout(this);pdfRow.setGravity(Gravity.CENTER_VERTICAL);pdfRow.setPadding(0,dp(8),0,dp(4));layout.addView(pdfRow,new LinearLayout.LayoutParams(-1,-2));LinearLayout pdfInfo=new LinearLayout(this);pdfInfo.setOrientation(LinearLayout.VERTICAL);pdfInfo.addView(text("PDF 合約",14,Color.rgb(90,61,37)));TextView name=text(pdf.isEmpty()?"尚未附上":editor.attachments.optString("pdfName","租賃合約.pdf"),13,Color.GRAY);name.setMaxLines(4);name.setEllipsize(android.text.TextUtils.TruncateAt.END);pdfInfo.addView(name);pdfRow.addView(pdfInfo,new LinearLayout.LayoutParams(0,-2,1));if(!pdf.isEmpty()){pdfInfo.setContentDescription("開啟所選 PDF 合約");pdfInfo.setOnClickListener(v->{try{viewContract(new JSONObject().put("contractFile",pdf));}catch(Exception e){error(e);}});Button more=leaseAttachmentButton("⋯");more.setContentDescription("PDF 合約操作");pdfRow.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));more.setOnClickListener(v->{PopupMenu menu=new PopupMenu(this,more);menu.getMenu().add("開啟 PDF");menu.getMenu().add("移除 PDF");menu.setOnMenuItemClickListener(item->{try{if(item.getTitle().toString().equals("移除 PDF")){if(leaseAttachmentPending())return true;LeaseAttachments.remove(editor.attachments,true);editor.refresh.run();}else viewContract(new JSONObject().put("contractFile",pdf));}catch(Exception e){error(e);}return true;});menu.show();});}Button choosePdf=leaseAttachmentButton(pdf.isEmpty()?"選取":"更換");pdfRow.addView(choosePdf,new LinearLayout.LayoutParams(dp(72),dp(48)));choosePdf.setOnClickListener(v->pickLeaseAttachment(editor,PICK_LEASE_PDF));
    }
    private void restoreLeaseEditor(JSONObject state)throws JSONException{
        JSONObject unit=find("units",state.getString("unitId"));if(unit==null)throw new IllegalArgumentException("出租單位不存在，無法恢復租約草稿");leaseForm(unit,state.isNull("original")?null:state.getJSONObject("original"),state);
    }
    private void pickLeaseAttachment(LeaseEditorSession editor,int request){
        if(getSharedPreferences("workflow",MODE_PRIVATE).contains("pendingLeaseAttachment")){Toast.makeText(this,"附件正在處理，請稍候",Toast.LENGTH_SHORT).show();return;}
        String file=UUID.randomUUID()+(request==PICK_LEASE_PDF?".pdf":".jpg");try{JSONObject pending=new JSONObject().put("draft",editor.snapshot.get()).put("file",file).put("token",editor.token).put("pdf",request==PICK_LEASE_PDF);
            if(!getSharedPreferences("workflow",MODE_PRIVATE).edit().putString("pendingLeaseAttachment",pending.toString()).commit())throw new IOException("草稿保存失敗");
            if(request==CAMERA_LEASE_ATTACHMENT){Uri uri=photoUri(file);Intent camera=new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).putExtra(android.provider.MediaStore.EXTRA_OUTPUT,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);camera.setClipData(android.content.ClipData.newRawUri("lease-meter",uri));startActivityForResult(camera,request);}else startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(request==PICK_LEASE_PDF?"application/pdf":"image/*"),request);
        }catch(Exception e){getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingLeaseAttachment").commit();new File(photoDir(),file).delete();error(new IllegalArgumentException("無法開啟附件來源，表單與原附件保留，請重試"));}
    }
    private void leaseAttachmentResult(int request,int result,Intent intent){
        String raw=getSharedPreferences("workflow",MODE_PRIVATE).getString("pendingLeaseAttachment",null);if(raw==null)return;
        try{JSONObject pending=new JSONObject(raw);String filename=pending.getString("file");boolean pdf=pending.getBoolean("pdf");if(pdf?!LeaseFiles.pdfName(filename):!MeterReadings.photoName(filename))throw new IOException("暫存附件無效");revokeUriPermission(photoUri(filename),Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);File file=new File(photoDir(),filename);
            if(result!=RESULT_OK||(request!=CAMERA_LEASE_ATTACHMENT&&(intent==null||intent.getData()==null))){file.delete();pending.put("ready",true);getSharedPreferences("workflow",MODE_PRIVATE).edit().putString("pendingLeaseAttachment",pending.toString()).commit();finishLeaseAttachment();return;}
            Uri uri=intent==null?null:intent.getData();String displayName="租賃合約.pdf";if(pdf)try(android.database.Cursor cursor=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst()&&cursor.getString(0)!=null)displayName=cursor.getString(0);}catch(Exception ignored){}final String label=displayName;
            leaseAttachmentImports.add(filename);pending.put("processing",true);getSharedPreferences("workflow",MODE_PRIVATE).edit().putString("pendingLeaseAttachment",pending.toString()).commit();Toast.makeText(this,"正在讀取附件…",Toast.LENGTH_SHORT).show();
            new Thread(()->{try{
                if(request!=CAMERA_LEASE_ATTACHMENT)try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(file)){if(in==null)throw new IOException();byte[] buffer=new byte[8192];long total=0;int n;while((n=in.read(buffer))!=-1){total+=n;if(total>BackupArchive.PHOTO_LIMIT)throw new IOException("附件超過25MB");out.write(buffer,0,n);}}
                if(pdf)validateContractPdf(file);else {android.graphics.Bitmap bitmap=photoPreview(file);bitmap.recycle();}
                LeaseAttachments.select(pending.getJSONObject("draft").getJSONObject("attachments"),filename,label,pdf);
            }catch(Exception e){file.delete();try{pending.put("error","附件無法讀取，請選可開啟、未加密且25MB以下的檔案；原附件與表單保留。");}catch(Exception ignored){}}
            try{String latest=getSharedPreferences("workflow",MODE_PRIVATE).getString("pendingLeaseAttachment",null);if(latest==null||!new JSONObject(latest).getString("file").equals(filename)){file.delete();return;}pending.put("ready",true).put("processing",false);getSharedPreferences("workflow",MODE_PRIVATE).edit().putString("pendingLeaseAttachment",pending.toString()).commit();runOnUiThread(()->{if(!isDestroyed())finishLeaseAttachment();});}catch(Exception e){file.delete();}finally{leaseAttachmentImports.remove(filename);}}).start();
        }catch(Exception e){getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingLeaseAttachment").commit();error(e);}
    }
    private void finishLeaseAttachment(){
        String raw=getSharedPreferences("workflow",MODE_PRIVATE).getString("pendingLeaseAttachment",null);if(raw==null)return;try{JSONObject pending=new JSONObject(raw);if(!pending.optBoolean("ready"))return;JSONObject state=pending.getJSONObject("draft");if(leaseEditor==null)restoreLeaseEditor(state);else if(leaseEditor.token.equals(pending.getString("token"))){leaseEditor.attachments=state.getJSONObject("attachments");leaseEditor.refresh.run();}else{new File(photoDir(),pending.getString("file")).delete();}getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingLeaseAttachment").commit();if(pending.has("error"))error(new IllegalArgumentException(pending.getString("error")));}catch(Exception e){getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingLeaseAttachment").commit();error(e);}
    }
    private void watchLeaseAttachment(){
        if(isDestroyed()||isFinishing())return;String raw=getSharedPreferences("workflow",MODE_PRIVATE).getString("pendingLeaseAttachment",null);if(raw==null)return;try{JSONObject pending=new JSONObject(raw);if(pending.optBoolean("ready"))finishLeaseAttachment();else if(pending.optBoolean("processing")){if(!leaseAttachmentImports.contains(pending.optString("file"))){new File(photoDir(),pending.optString("file")).delete();pending.put("ready",true).put("processing",false).put("error","附件讀取已中斷，表單與原附件已保留，請重新選取。");getSharedPreferences("workflow",MODE_PRIVATE).edit().putString("pendingLeaseAttachment",pending.toString()).commit();finishLeaseAttachment();}else getWindow().getDecorView().postDelayed(this::watchLeaseAttachment,300);}}catch(Exception e){getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingLeaseAttachment").commit();}
    }
    private void pickInitialMeterPhoto(JSONObject lease){
        new AlertDialog.Builder(this).setTitle("起始電表照片").setItems(new String[]{"拍照","從相簿選取"},(d,n)->{try{
            JSONObject current=find("leases",lease.optString("id"));if(current==null)throw new IllegalArgumentException("找不到租約");String name=UUID.randomUUID()+".jpg";JSONObject pending=new JSONObject().put("leaseId",current.getString("id")).put("snapshot",current.toString()).put("file",name);
            if(!getSharedPreferences("workflow",MODE_PRIVATE).edit().putString("pendingInitialPhoto",pending.toString()).commit())throw new IOException("無法保存照片作業");
            if(n==0){Uri uri=photoUri(name);Intent camera=new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).putExtra(android.provider.MediaStore.EXTRA_OUTPUT,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);camera.setClipData(android.content.ClipData.newRawUri("initial-meter",uri));startActivityForResult(camera,CAMERA_INITIAL);}else startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),PICK_INITIAL);
        }catch(Exception e){getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingInitialPhoto").commit();error(new IllegalArgumentException("無法啟動相機或相簿，租約已儲存，可重新選取照片"));}}).show();
    }
    private void initialMeterPhotoResult(int request,int result,Intent intent){
        String raw=getSharedPreferences("workflow",MODE_PRIVATE).getString("pendingInitialPhoto",null);if(raw==null)return;
        final JSONObject pending;try{pending=new JSONObject(raw);if(!MeterReadings.photoName(pending.getString("file")))throw new IOException();}catch(Exception e){getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingInitialPhoto").commit();error(new IllegalArgumentException("照片作業遺失，請重新選取"));return;}
        File file=new File(photoDir(),pending.optString("file"));if(result!=RESULT_OK||(request==PICK_INITIAL&&(intent==null||intent.getData()==null))){file.delete();getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingInitialPhoto").commit();return;}
        Uri uri=intent==null?null:intent.getData();AlertDialog progress=busy("正在讀取起始電表照片…");new Thread(()->{try{
            if(request==PICK_INITIAL)try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(file)){if(in==null)throw new IOException();byte[] buffer=new byte[8192];long total=0;int n;while((n=in.read(buffer))!=-1){total+=n;if(total>BackupArchive.PHOTO_LIMIT)throw new IOException("照片超過25MB");out.write(buffer,0,n);}}
            android.graphics.Bitmap bitmap=photoPreview(file);runOnUiThread(()->{progress.dismiss();boolean[] saved={false};LinearLayout layout=form();layout.addView(text("起始電表照片 · "+pending.optString("leaseId"),13,Color.DKGRAY));ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setImageBitmap(bitmap);layout.addView(image,new LinearLayout.LayoutParams(-1,dp(240)));AlertDialog confirm=new AlertDialog.Builder(this).setTitle("確認起始電表照片").setView(layout).setNegativeButton("取消",null).setPositiveButton("儲存照片",null).create();confirm.setOnDismissListener(d->{if(!saved[0])file.delete();getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingInitialPhoto").commit();});confirm.setOnShowListener(d->confirm.getButton(-1).setOnClickListener(v->{try{JSONObject current=find("leases",pending.getString("leaseId"));if(current==null||!current.toString().equals(pending.getString("snapshot")))throw new IllegalArgumentException("租約已變更，請重新選取照片");JSONArray next=data("leases");for(int i=0;i<next.length();i++)if(pending.getString("leaseId").equals(next.getJSONObject(i).optString("id")))next.getJSONObject(i).put("initialMeterPhoto",file.getName());if(saveChecked("leases",next)){saved[0]=true;confirm.dismiss();render();refreshPanels();Toast.makeText(this,"起始電表照片已保存",Toast.LENGTH_LONG).show();}}catch(Exception e){error(e);}}));confirm.show();});
        }catch(Exception e){file.delete();getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingInitialPhoto").commit();runOnUiThread(()->{progress.dismiss();error(new IllegalArgumentException("照片讀取失敗，請選擇可開啟且25MB以下的圖片；租約仍已儲存"));});}}).start();
    }
    private void pickContract(String leaseId){
        if(!getSharedPreferences("workflow",MODE_PRIVATE).edit().putString("pendingContractLease",leaseId).commit()){error(new IllegalArgumentException("無法準備合約上傳"));return;}
        try{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/pdf").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(pick,PICK_CONTRACT);}catch(Exception e){getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingContractLease").commit();error(new IllegalArgumentException("無法開啟PDF檔案選擇器"));}
    }
    private void validateContractPdf(File file) throws Exception {
        LeaseFiles.validatePdf(file);try(android.os.ParcelFileDescriptor descriptor=android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY);android.graphics.pdf.PdfRenderer pdf=new android.graphics.pdf.PdfRenderer(descriptor)){if(pdf.getPageCount()<1)throw new IOException("PDF合約沒有頁面");}
    }
    private void viewContract(JSONObject lease){
        try{String name=lease.getString("contractFile");if(!LeaseFiles.pdfName(name))throw new IOException();LeaseFiles.validatePdf(new File(photoDir(),name));startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(photoUri(name),"application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(Exception e){error(new IllegalArgumentException("無法開啟合約，請確認PDF檔案及手機PDF閱讀程式"));}
    }
    private void contractResult(int result,Intent intent){
        String leaseId=getSharedPreferences("workflow",MODE_PRIVATE).getString("pendingContractLease","");getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingContractLease").commit();if(result!=RESULT_OK||intent==null||intent.getData()==null)return;
        JSONObject lease=find("leases",leaseId);if(lease==null){error(new IllegalArgumentException("找不到合約所屬租約，請重新選取"));return;}final String snapshot=lease.toString();Uri uri=intent.getData();String filename="租賃合約.pdf";
        try(android.database.Cursor cursor=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())filename=cursor.getString(0);}catch(Exception ignored){}
        final String displayName=filename==null?"租賃合約.pdf":filename;File file=new File(photoDir(),UUID.randomUUID()+".pdf");AlertDialog progress=busy("正在保存PDF合約…");new Thread(()->{try{
            try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(file)){if(in==null)throw new IOException();byte[] buffer=new byte[8192];long total=0;int n;while((n=in.read(buffer))!=-1){total+=n;if(total>BackupArchive.PHOTO_LIMIT)throw new IOException("PDF超過25MB");out.write(buffer,0,n);}}
            validateContractPdf(file);runOnUiThread(()->{progress.dismiss();try{JSONObject latest=find("leases",leaseId);if(latest==null||!latest.toString().equals(snapshot))throw new IllegalArgumentException("租約已變更，請重新上傳合約");JSONArray next=data("leases");for(int i=0;i<next.length();i++)if(leaseId.equals(next.getJSONObject(i).getString("id"))){next.getJSONObject(i).put("contractFile",file.getName()).put("contractName",displayName);break;}if(!saveChecked("leases",next)){file.delete();return;}render();refreshPanels();Toast.makeText(this,"PDF合約已保存到手機",Toast.LENGTH_LONG).show();}catch(Exception e){file.delete();error(e);}});
        }catch(Exception e){file.delete();runOnUiThread(()->{progress.dismiss();error(new IllegalArgumentException("合約保存失敗：請選取可開啟、未加密且25MB以下的PDF"));});}}).start();
    }
    private File photoDir() {File dir=new File(getFilesDir(),"meter-photos");if(!dir.isDirectory())dir.mkdirs();return dir;}
    private Uri photoUri(String file) {return new Uri.Builder().scheme("content").authority(getPackageName()+".photos").appendPath(file).build();}
    private void meterPanel(JSONObject lease) {
        LinearLayout layout=form();Runnable refresh=()->{layout.removeAllViews();LinearLayout previous=body;body=layout;try{
            card("抄電表與照片",lease.optString("unitCodeSnapshot")+" · "+lease.optString("tenant")+"\n紀錄不會自動開單；建立帳單時帶入同租約、同月份最新有效讀數。照片可點開檢查。已開帳單不會被改動。");
            action("＋ 新增抄表紀錄",()->meterForm(lease));JSONArray rows=data("meterReadings");for(int i=rows.length()-1;i>=0;i--){JSONObject r=rows.getJSONObject(i);if(!lease.optString("id").equals(r.optString("leaseId")))continue;
                LinearLayout meterBox=card(r.getString("month")+" · 抄表日 "+r.getString("date"),"電表 "+fmt(r.getLong("previous"))+" → "+fmt(r.getLong("current"))+" · 用量 "+fmt(r.getLong("current")-r.getLong("previous"))+" 度\n"+r.getString("note")+(r.getBoolean("void")?"\n已撤銷錯誤紀錄":""));
                if(r.getBoolean("void"))markVoided(meterBox);
                if(!r.getString("file").isEmpty())cardAction(meterBox,"照片",()->viewMeterPhoto(r.optString("file")));
                if(!r.getBoolean("void"))cardAction(meterBox,"撤銷",()->{try{JSONArray next=data("meterReadings");for(int j=0;j<next.length();j++)if(next.getJSONObject(j).getString("id").equals(r.getString("id")))next.getJSONObject(j).put("void",true);if(saveChecked("meterReadings",next))render();}catch(Exception e){error(e);}});
            }
        }catch(Exception e){error(e);}finally{body=previous;}};refresh.run();showPanel("抄電表／照片",layout,refresh);
    }
    private void viewMeterPhoto(String name) {
        try{File f=new File(photoDir(),name);if(!f.isFile())throw new IOException("照片缺少，請從完整ZIP備份還原");Intent view=new Intent(Intent.ACTION_VIEW).setDataAndType(photoUri(name),getContentResolver().getType(photoUri(name))).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(view);}catch(Exception e){error(new IllegalArgumentException("無法開啟照片，請確認有圖片檢視程式及照片檔案"));}
    }
    private void meterForm(JSONObject lease) {
        LinearLayout layout=form();EditText month=dateField(layout,"用電帳單月份",true),date=dateField(layout,"實際抄表日期",false),prior=number(layout,"上期／起始電表讀數"),current=number(layout,"本期電表讀數"),note=field(layout,"抄表備註");month.setText(YearMonth.from(today()).toString());
        TextView origin=text("",14,Color.rgb(137,83,15));layout.addView(origin);
        Runnable loadPrevious=()->{try{JSONObject previous=MeterReadings.previousPeriod(data("meterReadings"),data("invoices"),lease.optString("id"),dateValue(month));prior.setText(previous==null?LeaseFiles.initialReading(lease,dateValue(month)):previous.optString("current"));current.setText("");origin.setText(previous==null?(LeaseFiles.initialReading(lease,dateValue(month)).isEmpty()?"上一個月份沒有紀錄，請自行填上期讀數。":"已帶入租約起始電度；請核對。"):"已帶入 "+previous.optString("month")+" "+previous.optString("source")+" 的本期讀數；可自行修正。");}catch(Exception e){origin.setText("請先選擇用電帳單月份。");}};
        month.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){loadPrevious.run();}public void afterTextChanged(Editable e){}});loadPrevious.run();
        layout.addView(text("照片是讀數依據。同一租約只帶入上一個月份，不沿用其他租客。缺月份或換表請自行確認起始讀數。",14,Color.DKGRAY));
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
        final String snapshot=raw;AlertDialog progress=busy("正在匯出帳務、合約與照片，請稍候…");new Thread(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException();BackupArchive.write(out,new JSONObject(snapshot),photoDir());runOnUiThread(()->{progress.dismiss();pendingBackup=null;getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingBackup").commit();Toast.makeText(this,"完整ZIP備份已匯出，包含照片、PDF合約與帳務",Toast.LENGTH_LONG).show();});}catch(Exception e){runOnUiThread(()->{progress.dismiss();error(new IllegalArgumentException("備份匯出失敗："+e.getMessage()+"；請刪除未完成檔案並重新匯出"));});}}).start();
    }
    private void readArchive(Uri uri) {
        File staging=new File(getCacheDir(),"restore-"+UUID.randomUUID());AlertDialog progress=busy("正在檢查備份與照片…");new Thread(()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException();JSONObject backup=BackupArchive.read(in,staging);validateBackup(backup);for(String file:LeaseFiles.files(backup))validateContractPdf(new File(staging,file));for(String file:MeterReadings.files(backup.getJSONArray("meterReadings")))photoPreview(new File(staging,file)).recycle();for(String file:InitialMeterPhotos.files(backup))photoPreview(new File(staging,file)).recycle();for(String file:ExpensePhotos.files(backup))photoPreview(new File(staging,file)).recycle();
            runOnUiThread(()->{progress.dismiss();new AlertDialog.Builder(this).setMessage("已驗證備份、合約與照片。確定替換全部資料？舊v1–v5沒有抄表紀錄／照片；v1–v3沒有押金收付；v1–v4沒有退租結算。請保留目前的完整備份。").setNegativeButton("取消",(d,w)->clearStaging(staging)).setOnCancelListener(d->clearStaging(staging)).setPositiveButton("確認還原",(d,w)->restoreArchive(backup,staging)).show();});
        }catch(Exception e){clearStaging(staging);runOnUiThread(()->{progress.dismiss();error(new IllegalArgumentException("備份無效或照片缺少："+e.getMessage()+"；原資料未替換"));});}}).start();
    }
    private void restoreArchive(JSONObject backup,File staging) {
        AlertDialog progress=busy("正在還原帳務與照片…");new Thread(()->{List<File> added=new ArrayList<>();boolean committed=false;try{
            JSONArray rows=backup.getJSONArray("meterReadings");for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);String old=row.getString("file");if(old.isEmpty())continue;String name=UUID.randomUUID()+".jpg";File destination=new File(photoDir(),name);if(!new File(staging,old).renameTo(destination))throw new IOException("照片還原失敗");added.add(destination);row.put("file",name);}
            for(int i=0;i<backup.getJSONArray("leases").length();i++){JSONObject lease=backup.getJSONArray("leases").getJSONObject(i);String old=lease.optString("initialMeterPhoto");if(old.isEmpty())continue;String name=UUID.randomUUID()+".jpg";File destination=new File(photoDir(),name);if(!new File(staging,old).renameTo(destination))throw new IOException("起始電表照片還原失敗");added.add(destination);lease.put("initialMeterPhoto",name);}
            Map<String,String> expenseMapping=new HashMap<>();for(String old:ExpensePhotos.files(backup)){String name="exp-"+UUID.randomUUID()+".jpg";File destination=new File(photoDir(),name);if(!new File(staging,old).renameTo(destination))throw new IOException("支出照片還原失敗");added.add(destination);expenseMapping.put(old,name);}ExpensePhotos.remap(backup,expenseMapping);
            JSONArray contractLeases=backup.getJSONArray("leases");for(int i=0;i<contractLeases.length();i++){JSONObject lease=contractLeases.getJSONObject(i);String old=lease.optString("contractFile");if(old.isEmpty())continue;String name=UUID.randomUUID()+".pdf";File destination=new File(photoDir(),name);if(!new File(staging,old).renameTo(destination))throw new IOException("PDF合約還原失敗");added.add(destination);lease.put("contractFile",name);}
            JSONArray shareRows=backup.getJSONArray("reportShares");for(int i=0;i<shareRows.length();i++)shareRows.getJSONObject(i).put("status","unconfirmed");
            validateBackup(backup);android.content.SharedPreferences.Editor editor=getSharedPreferences("catalog",MODE_PRIVATE).edit();for(String key:KEYS)editor.putString(key,backup.getJSONArray(key).toString());if(!editor.commit())throw new IOException("帳務保存失敗");committed=true;clearStaging(staging);runOnUiThread(()->{progress.dismiss();propertyId="";unitId="";render();body.post(this::refreshPanels);Toast.makeText(this,"資料與照片已還原",Toast.LENGTH_LONG).show();});
        }catch(Exception e){if(!committed)for(File file:added)file.delete();clearStaging(staging);runOnUiThread(()->{progress.dismiss();error(new IllegalArgumentException("還原失敗："+e.getMessage()));});}}).start();
    }
    private void validateBackup(JSONObject backup) throws Exception {
        if (!"rental-catalog".equals(backup.getString("format")) || (backup.getInt("version") < 1 || backup.getInt("version") > 12))
            throw new IllegalArgumentException();
        if(backup.getInt("version")==1) {backup.put("invoices",new JSONArray());backup.put("receipts",new JSONArray());}
        if(backup.getInt("version")<3)for(String key:new String[]{"accounts","expenses","transfers","reconciliations"})backup.put(key,new JSONArray());
        if(backup.getInt("version")<4)backup.put("deposits",new JSONArray());
        if(backup.getInt("version")<5)backup.put("settlements",new JSONArray());
        if(backup.getInt("version")<6)backup.put("meterReadings",new JSONArray());
        if(backup.getInt("version")<7)backup.put("expenseCategories",ExpenseCategories.defaults());
        if(backup.getInt("version")<12){backup.put("owners",new JSONArray());backup.put("reportShares",new JSONArray());}
        backup.getJSONArray("reportShares");Owners.validate(backup);
        LeaseFiles.validate(backup);InitialMeterPhotos.files(backup);ExpensePhotos.files(backup);
        java.util.Set<String> properties = new java.util.HashSet<>(), units = new java.util.HashSet<>(), leases = new java.util.HashSet<>();
        java.util.Set<String> codes = new java.util.HashSet<>();
        JSONArray p = backup.getJSONArray("properties"), u = backup.getJSONArray("units"), l = backup.getJSONArray("leases");
        for (int i=0; i<p.length(); i++) {
            JSONObject item=p.getJSONObject(i);
            if (item.getString("id").isEmpty() || !properties.add(item.getString("id")) || item.getString("name").trim().isEmpty()
                || !(item.getString("type").equals("土地") || item.getString("type").equals("建築物"))) throw new IllegalArgumentException();
        }
        for (int i=0; i<u.length(); i++) {
            JSONObject item=u.getJSONObject(i); String code=item.getString("code").trim();if(item.has("enabled"))item.getBoolean("enabled");
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
            RentTerms.validate(lease);if(!Arrays.asList("租客自行繳水電","管理者代收水電").contains(lease.getString("utilities")))throw new IllegalArgumentException();
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
        Set<String> shareKeys=new HashSet<>();JSONArray shares=backup.getJSONArray("reportShares");for(int i=0;i<shares.length();i++){JSONObject share=shares.getJSONObject(i);String audience=share.getString("audience"),id=share.getString("subjectId");if(!Arrays.asList("owner","tenant").contains(audience)||!shareKeys.add(audience+"_"+id)||Owners.find(backup.getJSONArray(audience.equals("owner")?"owners":"leases"),id)==null||!Arrays.asList("shared","revoked","unconfirmed").contains(share.getString("status")))throw new IllegalArgumentException("分享紀錄無效");if(share.optString("status").equals("shared")){SyncProtocol.reportUrl(share.getString("url"));if(!Owners.email(share.getString("email")))throw new IllegalArgumentException("分享 Gmail 無效");}if(share.has("endpoint"))SyncProtocol.endpoint(share.getString("endpoint"));}
        Ledger.validate(invoices,backup.getJSONArray("receipts"),leases);Bookkeeping.validate(backup);
    }
    @Override protected void onActivityResult(int request,int result,Intent intent) {
        super.onActivityResult(request,result,intent);
        if(request==EXPORT_WORKBOOK){if(result==RESULT_OK&&intent!=null&&intent.getData()!=null)writeOwnerWorkbook(intent.getData());else getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingWorkbook").commit();return;}
        if(request==EXPORT_REPORT){if(result==RESULT_OK&&intent!=null&&intent.getData()!=null)writeOwnerReport(intent.getData());else getSharedPreferences("workflow",MODE_PRIVATE).edit().remove("pendingReport").commit();return;}
        if(request==VOICE_INPUT){voiceResult(result,intent);return;}
        if(request==CAMERA_LEASE_ATTACHMENT||request==PICK_LEASE_PHOTO||request==PICK_LEASE_PDF){leaseAttachmentResult(request,result,intent);return;}
        if(request==CAMERA_INITIAL||request==PICK_INITIAL){initialMeterPhotoResult(request,result,intent);return;}
        if(request==CAMERA_EXPENSE||request==PICK_EXPENSE){expensePhotoResult(request,result,intent);return;}
        if(request==PICK_CONTRACT){contractResult(result,intent);return;}
        if(request==CAMERA_PHOTO||request==PICK_PHOTO){photoResult(request,result,intent);return;}
        if(result!=RESULT_OK||intent==null||intent.getData()==null)return;
        if(request==EXPORT)exportArchive(intent.getData());else if(request==IMPORT)readArchive(intent.getData());
    }
}

