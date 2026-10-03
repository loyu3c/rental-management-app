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
    private String pendingBackup;
    private String propertyId="", unitId="";
    private static final String[] KEYS={"properties","units","leases","invoices","receipts"};
    private LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Taipei")); }
    private static final int EXPORT = 41, IMPORT = 42;
    private final String[] tabs = {"首頁", "收租", "記帳", "不動產", "報表"};
    private int selected = 0;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) { selected = state.getInt("tab", 0); propertyId=state.getString("property", ""); unitId=state.getString("unit", ""); }
        render();
    }
    @Override public void onSaveInstanceState(Bundle out) {
        out.putInt("tab", selected); out.putString("property",propertyId); out.putString("unit",unitId);
        super.onSaveInstanceState(out);
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
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
        root.addView(text("整合測試版 · 資料存在手機；Google、拍照與語音未啟用", 13, Color.rgb(137, 83, 15)));
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
                for(int i=0;i<receipts.length();i++) if(receipts.getJSONObject(i).getString("date").startsWith(month)) received+=Ledger.money(receipts.getJSONObject(i),"amount");
                card(month+" 收租", "本月已開帳單 $"+billed+"\n本月實際收款 $"+received+"\n所有帳單未付 $"+unpaid+"（含未到期）\n已逾期 $"+overdue);
            } catch(Exception e) { card("收租資料異常", "請先匯出備份並停止收款登錄。"); }
            card("銀行與共用零用金", "A、B、C 三個銀行帳戶獨立追蹤；現金合併管理。\n期初餘額與對帳資料待設定。");
            card("不動產建檔", "已建立 " + data("properties").length() + " 筆不動產、" + data("units").length() + " 個出租單位。\n請從不動產入口新增。");
        } else if (selected == 1) {
            rentScreen();
        } else if (selected == 2) {
            card("支出與分攤", "修繕、電梯保養、房貸總扣款。\n選銀行或現金付款，再填各棟分攤金額。");
            card("提款與轉帳", "銀行轉零用金是資金移動，不列為費用。");
            card("語音帳務草稿", "說話 → 轉文字與整理欄位 → 補齊資料 → 你確認。\n確認前不改變餘額。");
            card("押金", "收取、退款與扣抵分開追蹤。");
        } else if (selected == 3) {
            propertyScreen();
        } else {
            card("爸爸的總報表", "三棟總覽、各棟收支、停車收入、押金與帳戶流水。\n不顯示電話。");
            card("租客的獨立報表", "每租約獨立檔案，只看自己的租期、押金與繳款紀錄。");
            card("存摺對帳與歷史補登", "同日期比較實際與帳面餘額。\n起算日前資料不重複影響期初餘額。");
            action("匯出完整備份", this::exportBackup);
            action("從檔案還原資料", this::importBackup);
            card("Google 同步：未設定", "目前尚未連接 Google，也沒有上傳任何資料。");
        }
        LinearLayout nav = new LinearLayout(this); nav.setGravity(Gravity.CENTER);
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            Button button = new Button(this); button.setText(tabs[i]); button.setTextSize(13);
            button.setAllCaps(false); button.setPadding(0, 0, 0, 0);
            button.setTextColor(i == selected ? Color.rgb(104, 70, 39) : Color.DKGRAY);
            GradientDrawable navBg=new GradientDrawable();navBg.setColor(i==selected?Color.rgb(232,213,187):Color.rgb(248,244,237));navBg.setCornerRadius(dp(12));button.setBackground(navBg);
            button.setOnClickListener(v -> { selected = index; render(); });
            nav.addView(button, new LinearLayout.LayoutParams(0, dp(56), 1));
        }
        root.addView(nav); setContentView(root);
    }

    private JSONArray data(String key) {
        try { return new JSONArray(getSharedPreferences("catalog", MODE_PRIVATE).getString(key, "[]")); }
        catch (JSONException e) {
            new AlertDialog.Builder(this).setMessage("建檔資料無法讀取，請勿繼續新增。").setPositiveButton("知道了", null).show();
            throw new IllegalStateException("Invalid saved catalog", e);
        }
    }
    private boolean save(String key, JSONArray values) {
        boolean ok = getSharedPreferences("catalog", MODE_PRIVATE).edit().putString(key, values.toString()).commit();
        if (!ok) Toast.makeText(this, "保存失敗，請重試", Toast.LENGTH_LONG).show();
        return ok;
    }
    private void action(String label, Runnable run) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        b.setTextColor(Color.rgb(90, 61, 37));
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(238,226,211));bg.setCornerRadius(dp(12));b.setBackground(bg);b.setMinHeight(dp(48));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,dp(5),0,dp(5));
        b.setOnClickListener(v -> run.run()); body.addView(b,params);
    }
    private EditText field(LinearLayout layout, String hint) {
        EditText field = new EditText(this); field.setHint(hint); field.setSingleLine(true);
        field.setTextSize(16); layout.addView(field); return field;
    }
    private Spinner choice(LinearLayout layout, String[] names) {
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        layout.addView(spinner); return spinner;
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
            LocalDate from=LocalDate.parse(l.optString("start")),to=LocalDate.parse(l.optString("end"));
            if(!today().isBefore(from)&&!today().isAfter(to))return whole&&!other.optString("id").equals(unit.optString("id"))?"出租中（整棟租約）":"出租中";
            if(from.isAfter(today()))future=true;else history=true;
        }
        return future?"待起租":history?"租約已到期":"未建租約";
    }
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
        EditText search=field(body,"搜尋代號，例如 R1401、C1");
        Spinner filter=choice(body,new String[]{"全部","出租中","待起租","租約已到期","未建租約"});
        Spinner sort=choice(body,new String[]{"代號由小到大","代號由大到小"});
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);body.addView(list);
        Runnable refresh=()->{
            list.removeAllViews();List<JSONObject> rows=new ArrayList<>();JSONArray units=data("units");
            for(int i=0;i<units.length();i++) {JSONObject u=units.optJSONObject(i);String st=status(u);
                if(!propertyId.equals(u.optString("propertyId"))||!u.optString("code").toLowerCase(Locale.ROOT).contains(search.getText().toString().trim().toLowerCase(Locale.ROOT)))continue;
                if(filter.getSelectedItemPosition()>0&&!st.startsWith(filter.getSelectedItem().toString()))continue;rows.add(u);}
            rows.sort((x,y)->natural(x.optString("code"),y.optString("code"))*(sort.getSelectedItemPosition()==0?1:-1));
            LinearLayout previous=body;body=list;
            for(JSONObject u:rows)tapCard(u.optString("code"),u.optString("type")+" · "+status(u),()->openUnit(u));
            if(rows.isEmpty())card("沒有符合的單位","可調整搜尋或篩選條件。");body=previous;
        };
        search.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){refresh.run();}public void afterTextChanged(Editable e){} });
        AdapterView.OnItemSelectedListener listener=new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,android.view.View v,int n,long id){refresh.run();}public void onNothingSelected(AdapterView<?> p){}};
        filter.setOnItemSelectedListener(listener);sort.setOnItemSelectedListener(listener);refresh.run();
    }
    private void unitScreen(JSONObject property,JSONObject unit) {
        action("‹ "+property.optString("name")+" 的出租單位",()->{unitId="";render();});
        card(unit.optString("code"),unit.optString("type")+" · "+status(unit));
        action("修改代號",()->editName("units",unit,"code","出租代號"));
        action("＋ 新增租約／換新租客",()->addLease(unit));
        JSONArray leases=data("leases");List<JSONObject> rows=new ArrayList<>();
        for(int i=0;i<leases.length();i++)if(unit.optString("id").equals(leases.optJSONObject(i).optString("unitId")))rows.add(leases.optJSONObject(i));
        rows.sort((x,y)->y.optString("start").compareTo(x.optString("start")));
        for(JSONObject l:rows) {
            card(l.optString("tenant"),l.optString("start")+" ～ "+l.optString("end")+"\n月租 $"+l.optLong("rent")+" · "+l.optString("cycle")+"\n約定押金 $"+l.optLong("deposit")+"（未登錄押金收付）\n"+l.optString("email")+"\n"+l.optString("utilities")+(l.optString("utilities").equals("管理者代收水電")?(l.isNull("water")?" · 水費待設定":" · 月水費 $"+l.optLong("water")):""));
            action("修改 "+l.optString("tenant")+" 的租約",()->leaseForm(unit,l));
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
        EditText input = field(layout, hint); input.setInputType(InputType.TYPE_CLASS_NUMBER); return input;
    }
    private long amount(EditText field, boolean optional) {
        String value = field.getText().toString().trim();
        if (value.isEmpty() && optional) return -1;
        long result = Long.parseLong(value);
        if (result < 0 || result > 1000000000L) throw new IllegalArgumentException();
        return result;
    }
    private void addLease(JSONObject unit) { leaseForm(unit,null); }
    private void leaseForm(JSONObject unit, JSONObject original) {
        LinearLayout layout = form();
        EditText tenant = field(layout, "租客姓名"), email = field(layout, "查看報表用 Gmail");
        EditText start = field(layout, "起租日 YYYY-MM-DD"), end = field(layout, "到期日 YYYY-MM-DD");
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
                layout.addView(text("已有帳單：租期、租金、押金與繳款週期鎖定。姓名、Gmail和水電設定可修改；水電設定只套用新帳單。換租客請新增租約。",14,Color.rgb(137,83,15)));
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
            try { from = LocalDate.parse(start.getText().toString().trim()); to = LocalDate.parse(end.getText().toString().trim());
                if (to.isBefore(from)) throw new IllegalArgumentException();
            } catch (Exception e) { end.setError("日期需為 YYYY-MM-DD，結束不得早於開始"); return; }
            long monthly, due, held, waterAmount, electricRate;
            try { monthly = amount(rent, false); due = amount(day, false); held = amount(deposit, false); waterAmount = amount(water, true); electricRate=amount(rate,false);
                if (monthly <= 0 || due < 1 || due > 31) throw new IllegalArgumentException();
            } catch (Exception e) { rent.setError("請檢查租金、押金、水費及應繳日"); return; }
            JSONArray leases = data("leases"), units = data("units");
            for (int i = 0; i < leases.length(); i++) {
                JSONObject old = leases.optJSONObject(i); if(original!=null&&old.optString("id").equals(original.optString("id")))continue; JSONObject oldUnit = null;
                for (int j = 0; j < units.length(); j++) if (units.optJSONObject(j).optString("id").equals(old.optString("unitId")))
                    oldUnit = units.optJSONObject(j);
                boolean same = unit.optString("id").equals(old.optString("unitId"));
                boolean whole = oldUnit != null && unit.optString("propertyId").equals(oldUnit.optString("propertyId"))
                    && (unit.optString("type").equals("整棟") || oldUnit.optString("type").equals("整棟"));
                if ((same || whole) && !to.isBefore(LocalDate.parse(old.optString("start"))) && !from.isAfter(LocalDate.parse(old.optString("end")))) {
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
        for(String key:new String[]{"invoices","receipts"}) {JSONArray rows=data(key);for(int i=0;i<rows.length();i++)if(rows.optJSONObject(i).optString("leaseId").equals(leaseId))return true;}return false;
    }
    private void error(Exception e) { new AlertDialog.Builder(this).setMessage(e.getMessage()==null?"資料格式不正確，請檢查輸入。":e.getMessage()).setPositiveButton("知道了",null).show(); }
    private void rentScreen() {
        card("帳單與收款", "選擇租約查看或開立帳單，再依存摺登錄一筆合併匯款。\n金額為整數新臺幣；未開帳單不代表沒有欠款。");
        JSONArray leases=data("leases");
        if(leases.length()==0) {card("尚無租約","請先到不動產建立出租單位與租約。");return;}
        EditText search=field(body,"搜尋姓名、不動產或代號");
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);body.addView(list);
        Runnable refresh=()->{
            list.removeAllViews();LinearLayout previous=body;body=list;int count=0;
            List<JSONObject> sorted=new ArrayList<>();for(int i=0;i<leases.length();i++)sorted.add(leases.optJSONObject(i));
            sorted.sort((x,y)->y.optString("start").compareTo(x.optString("start")));
            for(JSONObject l:sorted) {
                JSONObject unit=find("units",l.optString("unitId")),p=unit==null?null:find("properties",unit.optString("propertyId"));
                String title=(p==null?"":p.optString("name")+" / ")+(unit==null?l.optString("unitCodeSnapshot"):unit.optString("code"))+" · "+l.optString("tenant");
                if(!title.toLowerCase(Locale.ROOT).contains(search.getText().toString().trim().toLowerCase(Locale.ROOT)))continue;
                tapCard(title,l.optString("start")+" ～ "+l.optString("end")+" · "+l.optString("cycle"),()->leaseLedger(l));count++;
            }
            if(count==0)card("沒有符合的租約","調整搜尋條件。歷史租約仍會保留。");body=previous;
        };
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){refresh.run();}public void afterTextChanged(Editable e){}});refresh.run();
    }
    private void leaseLedger(JSONObject lease) {
        LinearLayout previous=body;LinearLayout content=form();body=content;
        try {
            JSONArray invoices=data("invoices"),receipts=data("receipts");long debt=0,received=0,credit=0;
            for(int i=0;i<invoices.length();i++) {JSONObject r=invoices.getJSONObject(i);if(lease.optString("id").equals(r.getString("leaseId")))debt+=Ledger.total(r)-Ledger.paid(r.getString("id"),receipts);}
            for(int i=0;i<receipts.length();i++) {JSONObject r=receipts.getJSONObject(i);if(lease.optString("id").equals(r.getString("leaseId"))){received+=Ledger.money(r,"amount");credit+=Ledger.money(r,"amount")-Ledger.allocated(r);}}
            card(lease.optString("tenant")+" · "+lease.optString("unitCodeSnapshot"),"帳單未付 $"+debt+"（含未到期）\n實際收款累計 $"+received+"\n尚未分配收款 $"+credit+"\n押金不包含於上述租金收款。");
            action("＋ 開立租金／輸入每月電表",()->billingForm(lease));
            action("＋ 登錄一筆合併匯款",()->receiptForm(lease));
            if(credit>0)action("分配先前尚未分配的收款",()->allocateCredit(lease));
            List<JSONObject> rows=new ArrayList<>();for(int i=0;i<invoices.length();i++)if(lease.optString("id").equals(invoices.getJSONObject(i).getString("leaseId")))rows.add(invoices.getJSONObject(i));
            rows.sort(Comparator.comparing((JSONObject r)->r.optString("month")).reversed().thenComparing(r->r.optString("kind")));
            for(JSONObject r:rows) {
                long paid=Ledger.paid(r.getString("id"),receipts),left=Ledger.total(r)-paid;
                card(r.getString("month")+" · "+r.getString("kind"),"租金 $"+r.getLong("rent")+"／水費 $"+r.getLong("water")+"／電費 $"+r.getLong("electricity")
                    +(r.getString("kind").equals("水電")?"\n電表 "+r.getLong("previous")+" → "+r.getLong("current")+" · 每度 $"+r.getLong("rate"):"")
                    +"\n應繳 "+r.getString("due")+" · 已付 $"+paid+" · 未付 $"+left);
                if(paid==0)action("撤銷 "+r.optString("month")+" 的"+r.optString("kind")+"帳單",()->deleteInvoice(r));
            }
            for(int i=receipts.length()-1;i>=0;i--) {JSONObject r=receipts.getJSONObject(i);if(!lease.optString("id").equals(r.getString("leaseId")))continue;
                card(r.getString("date")+" · 實收 $"+r.getLong("amount"),r.getString("account")+"\n已分配 $"+Ledger.allocated(r)+" · 尚未分配 $"+(r.getLong("amount")-Ledger.allocated(r))+"\n"+r.optString("note"));
                action("撤銷此筆錯誤收款",()->deleteReceipt(r));
            }
        }catch(Exception e){error(e);}finally{body=previous;}
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        new AlertDialog.Builder(this).setTitle("帳單與收款").setView(scroll).setPositiveButton("關閉",null).show();
    }
    private void billingForm(JSONObject lease) {
        LinearLayout layout=form();
        layout.addView(text("租金不會自動按天比例計算。首末月請填實際應收；年繳則逐月建立租金帳單。水電另外按月建立。",14,Color.DKGRAY));
        EditText month=field(layout,"帳單月份 YYYY-MM");month.setText(YearMonth.from(today()).toString());
        Spinner kind=choice(layout,new String[]{"單月租金","年繳租金（最多12個月）","當月水電與電表"});
        EditText rent=number(layout,"應收月租金");rent.setText(lease.optString("rent"));
        EditText prior=number(layout,"上期／起始電表讀數"),current=number(layout,"本期電表讀數");
        layout.addView(text("僅選當月水電時需要讀數；水費依租約。拍照尚未啟用。跨租客讀數不會自動沿用。",14,Color.DKGRAY));
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("開立帳單").setView(scroll).setNegativeButton("取消",null).setPositiveButton("預覽",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try {
            YearMonth from=YearMonth.parse(month.getText().toString().trim());JSONArray invoices=data("invoices"),added=new JSONArray();
            int n=kind.getSelectedItemPosition()==1?12:1;
            for(int i=0;i<n;i++) {
                YearMonth target=from.plusMonths(i);
                if(i>0&&target.atDay(1).isAfter(LocalDate.parse(lease.getString("end"))))break;
                JSONObject row=Ledger.invoice(lease,target,kind.getSelectedItemPosition()==2,kind.getSelectedItemPosition()==2?0:amount(rent,false),kind.getSelectedItemPosition()==2?amount(prior,false):0,kind.getSelectedItemPosition()==2?amount(current,false):0);
                Ledger.addUnique(invoices,row);added.put(row);
            }
            long sum=0;for(int i=0;i<added.length();i++)sum+=Ledger.total(added.getJSONObject(i));
            String preview=added.length()+" 張"+added.getJSONObject(0).getString("kind")+"帳單，合計 $"+sum+"。\n"+from+" 起；不代表已收款。\n首末月、年繳折扣或不足12月時，請確認每月金額正確。";
            new AlertDialog.Builder(this).setMessage(preview).setNegativeButton("返回修改",null).setPositiveButton("確認開立",(d,b)->{try{
                JSONArray latest=data("invoices");for(int i=0;i<added.length();i++)Ledger.addUnique(latest,added.getJSONObject(i));
                if(save("invoices",latest)){dialog.dismiss();render();Toast.makeText(this,"帳單已建立，重新開啟租約可查看",Toast.LENGTH_LONG).show();}
            }catch(Exception e){error(e);}}).show();
        }catch(Exception e){error(e);}}));dialog.show();
    }
    private String allocationSummary(JSONObject receipt,JSONArray invoices) throws JSONException {
        StringBuilder text=new StringBuilder();JSONArray parts=receipt.getJSONArray("allocations");
        for(int i=0;i<parts.length();i++){JSONObject p=parts.getJSONObject(i);for(int j=0;j<invoices.length();j++) {JSONObject r=invoices.getJSONObject(j);if(r.getString("id").equals(p.getString("invoiceId")))text.append(r.getString("month")).append(" ").append(r.getString("kind")).append(" $ ").append(p.getLong("amount")).append("\n");}}
        return text+"尚未分配 $"+(receipt.getLong("amount")-Ledger.allocated(receipt));
    }
    private void receiptForm(JSONObject lease) {
        LinearLayout layout=form();EditText date=field(layout,"實際匯款日期 YYYY-MM-DD");date.setText(today().toString());
        JSONObject unit=find("units",lease.optString("unitId")),property=unit==null?null:find("properties",unit.optString("propertyId"));
        EditText account=field(layout,"實際收款帳戶代稱，不需填完整帳號");if(property!=null)account.setText(property.optString("name")+"帳戶");
        EditText money=number(layout,"存摺核對的合併匯款金額"),note=field(layout,"備註／交易辨識，建議填存摺摘要");
        layout.addView(text("依最早月份分配，當月租金先於水電。年繳請先開立對應租金帳單；有水電舊欠款時也會先抵付。請核對下個畫面的分配清單。押金另行管理。",14,Color.DKGRAY));
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("核對存摺收款").setView(scroll).setNegativeButton("取消",null).setPositiveButton("預覽分配",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try {
            JSONArray invoices=data("invoices"),receipts=data("receipts");JSONObject r=Ledger.receipt(lease.getString("id"),date.getText().toString().trim(),account.getText().toString(),amount(money,false),note.getText().toString(),invoices,receipts);
            for(int i=0;i<receipts.length();i++){JSONObject old=receipts.getJSONObject(i);if(old.getString("leaseId").equals(r.getString("leaseId"))&&old.getString("date").equals(r.getString("date"))&&old.getString("account").equals(r.getString("account"))&&old.getLong("amount")==r.getLong("amount"))throw new IllegalArgumentException("同租約、日期、帳戶與金額已有收款，請先核對避免重複。");}
            new AlertDialog.Builder(this).setMessage("實際收款 $"+r.getLong("amount")+"\n"+allocationSummary(r,invoices)).setNegativeButton("返回修改",null).setPositiveButton("確認入帳",(d,b)->{try{
                JSONArray latest=data("receipts");for(int i=0;i<latest.length();i++) {JSONObject old=latest.getJSONObject(i);if(old.getString("id").equals(r.getString("id")))return;
                    if(old.getString("leaseId").equals(r.getString("leaseId"))&&old.getString("date").equals(r.getString("date"))&&old.getString("account").equals(r.getString("account"))&&old.getLong("amount")==r.getLong("amount"))throw new IllegalArgumentException("此筆收款已有相同紀錄，未重複入帳。");}
                latest.put(r);Set<String> ids=new HashSet<>();JSONArray ls=data("leases");for(int i=0;i<ls.length();i++)ids.add(ls.getJSONObject(i).getString("id"));Ledger.validate(data("invoices"),latest,ids);
                if(save("receipts",latest)){dialog.dismiss();render();Toast.makeText(this,"收款已登錄，重新開啟租約可查看",Toast.LENGTH_LONG).show();}
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
            new AlertDialog.Builder(this).setMessage("僅調整分配，不新增銀行收款。\n"+preview).setNegativeButton("取消",null).setPositiveButton("確認分配",(d,b)->{
                if(!data("receipts").toString().equals(receipts.toString())) {error(new IllegalArgumentException("資料已改變，請重新分配"));return;}
                if(save("receipts",next)){render();Toast.makeText(this,"分配完成",Toast.LENGTH_LONG).show();}
            }).show();
        }catch(Exception e){error(e);}
    }
    private void deleteInvoice(JSONObject invoice) {
        new AlertDialog.Builder(this).setMessage("僅限尚未收款的錯誤帳單可撤銷。確定撤銷此張帳單？").setNegativeButton("取消",null).setPositiveButton("撤銷",(d,b)->{try{
            if(Ledger.paid(invoice.getString("id"),data("receipts"))!=0)throw new IllegalArgumentException("已有收款分配，不能撤銷帳單。");
            JSONArray rows=data("invoices"),next=new JSONArray();for(int i=0;i<rows.length();i++)if(!rows.getJSONObject(i).getString("id").equals(invoice.getString("id")))next.put(rows.getJSONObject(i));
            if(save("invoices",next)){render();Toast.makeText(this,"已撤銷；請關閉舊明細再重新查看",Toast.LENGTH_LONG).show();}
        }catch(Exception e){error(e);}}).show();
    }
    private void deleteReceipt(JSONObject receipt) {
        new AlertDialog.Builder(this).setMessage("撤銷錯誤登錄會移除此筆收款與所有分配，恢復帳單未付金額。實際退款不能用撤銷代替。確定？").setNegativeButton("取消",null).setPositiveButton("撤銷錯誤登錄",(d,b)->{try{
            JSONArray rows=data("receipts"),next=new JSONArray();for(int i=0;i<rows.length();i++)if(!rows.getJSONObject(i).getString("id").equals(receipt.getString("id")))next.put(rows.getJSONObject(i));
            if(save("receipts",next)){render();Toast.makeText(this,"已撤銷；請關閉舊明細再重新查看",Toast.LENGTH_LONG).show();}
        }catch(Exception e){error(e);}}).show();
    }

    private void exportBackup() {
        try {
            JSONObject backup = new JSONObject();
            backup.put("format", "rental-catalog"); backup.put("version", 2);
            for (String key : KEYS) backup.put(key, data(key));
            pendingBackup = backup.toString(2);
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "rental-catalog-" + LocalDate.now() + ".json");
            startActivityForResult(intent, EXPORT);
        } catch (Exception e) { Toast.makeText(this, "無法準備備份", Toast.LENGTH_LONG).show(); }
    }
    private void importBackup() {
        new AlertDialog.Builder(this).setMessage("還原會替換所有資料（包含帳單與收款）。請先匯出完整備份；舊版備份沒有帳務紀錄。")
            .setNegativeButton("取消", null).setPositiveButton("選擇檔案", (d,w) -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*");
                startActivityForResult(intent, IMPORT);
            }).show();
    }
    private void validateBackup(JSONObject backup) throws Exception {
        if (!"rental-catalog".equals(backup.getString("format")) || (backup.getInt("version") != 1 && backup.getInt("version") != 2))
            throw new IllegalArgumentException();
        if(backup.getInt("version")==1) {backup.put("invoices",new JSONArray());backup.put("receipts",new JSONArray());}
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
                if(conflict&&!LocalDate.parse(lease.getString("end")).isBefore(LocalDate.parse(other.getString("start")))&&!LocalDate.parse(lease.getString("start")).isAfter(LocalDate.parse(other.getString("end"))))throw new IllegalArgumentException("備份租約重疊");
            }
        }
        JSONArray invoices=backup.getJSONArray("invoices");
        for(int i=0;i<invoices.length();i++) {JSONObject invoice=invoices.getJSONObject(i);for(int j=0;j<l.length();j++) {JSONObject lease=l.getJSONObject(j);if(!lease.getString("id").equals(invoice.getString("leaseId")))continue;
            YearMonth month=YearMonth.parse(invoice.getString("month"));
            if(month.atEndOfMonth().isBefore(LocalDate.parse(lease.getString("start")))||month.atDay(1).isAfter(LocalDate.parse(lease.getString("end"))))throw new IllegalArgumentException("帳單月份超出租期");
        }}
        Ledger.validate(invoices,backup.getJSONArray("receipts"),leases);
    }
    @Override protected void onActivityResult(int request, int result, Intent intent) {
        super.onActivityResult(request, result, intent);
        if (result != RESULT_OK || intent == null || intent.getData() == null) return;
        Uri uri = intent.getData();
        try {
            if (request == EXPORT) {
                if (pendingBackup == null) { JSONObject b=new JSONObject().put("format","rental-catalog").put("version",2);for(String key:KEYS)b.put(key,data(key));pendingBackup=b.toString(2); }
                try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) throw new IOException();
                    out.write(pendingBackup.getBytes(StandardCharsets.UTF_8));
                }
                pendingBackup = null;
                Toast.makeText(this, "備份已匯出，包含姓名及Gmail，請妥善保存", Toast.LENGTH_LONG).show();
            } else if (request == IMPORT) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new IOException();
                    byte[] chunk = new byte[4096]; int n;
                    while ((n=in.read(chunk)) != -1) { if (buffer.size()+n > 2000000) throw new IOException(); buffer.write(chunk,0,n); }
                }
                JSONObject backup = new JSONObject(buffer.toString("UTF-8")); validateBackup(backup);
                new AlertDialog.Builder(this).setMessage("已驗證備份，確定替換所有建檔、帳單與收款？舊版備份還原後帳務為空。")
                    .setNegativeButton("取消", null).setPositiveButton("確認還原", (d,w) -> {
                        try {
                            android.content.SharedPreferences.Editor editor = getSharedPreferences("catalog",MODE_PRIVATE).edit();
                            for (String key : KEYS) editor.putString(key,backup.getJSONArray(key).toString());
                            if (!editor.commit()) throw new IOException();
                            propertyId="";unitId="";render(); Toast.makeText(this,"還原完成",Toast.LENGTH_LONG).show();
                        } catch (Exception e) { Toast.makeText(this,"還原未完成",Toast.LENGTH_LONG).show(); }
                    }).show();
            }
        } catch (Exception e) { Toast.makeText(this,"檔案讀寫失敗或備份無效，請重試",Toast.LENGTH_LONG).show(); }
    }

}

