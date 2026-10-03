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
import android.text.InputType;
import android.util.Patterns;

public class MainActivity extends Activity {
    private LinearLayout body;
    private String pendingBackup;
    private static final int EXPORT = 41, IMPORT = 42;
    private final String[] tabs = {"首頁", "收租", "記帳", "不動產", "報表"};
    private int selected = 0;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) selected = state.getInt("tab", 0);
        render();
    }
    @Override public void onSaveInstanceState(Bundle out) {
        out.putInt("tab", selected);
        super.onSaveInstanceState(out);
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(6), 0, dp(6)); return view;
    }
    private void card(String heading, String detail) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18), dp(12), dp(18), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(255, 252, 247)); background.setCornerRadius(dp(16)); box.setBackground(background);
        TextView title = text(heading, 18, Color.rgb(77, 57, 39)); title.setTypeface(null, Typeface.BOLD);
        box.addView(title); box.addView(text(detail, 15, Color.rgb(115, 102, 87)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(8), 0, dp(8)); body.addView(box, params);
    }
    private void render() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(248, 244, 237)); root.setPadding(dp(16), dp(16), dp(16), dp(8));
        TextView heading = text("暖色帳本 / " + tabs[selected], 26, Color.rgb(83, 60, 40));
        heading.setTypeface(null, Typeface.BOLD); root.addView(heading);
        root.addView(text("試用版 · 建檔僅存手機；記帳與雲端功能尚未啟用", 13, Color.rgb(137, 83, 15)));
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (selected == 0) {
            card("每月收租與支出", "尚未建檔，金額不顯示為零。\n本期應收、已收、欠款與各棟支出將在這裡呈現。");
            card("銀行與共用零用金", "A、B、C 三個銀行帳戶獨立追蹤；現金合併管理。\n期初餘額與對帳資料待設定。");
            card("不動產建檔", "已建立 " + data("properties").length() + " 筆不動產、" + data("units").length() + " 個出租單位。\n請從不動產入口新增。");
        } else if (selected == 1) {
            card("每月抄表", "選房間 → 拍照或上傳 → 輸入讀數 → 確認電費。\n每度 5 元；水費依租約設定。");
            card("核對合併收款", "租金、水費、電費分項列帳，合併匯款只記一筆。\n支援部分繳款、跨月與年繳分配。");
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
            action("匯出建檔備份", this::exportBackup);
            action("從檔案還原建檔", this::importBackup);
            card("Google 同步：未設定", "目前尚未連接 Google，也沒有上傳任何資料。");
        }
        LinearLayout nav = new LinearLayout(this); nav.setGravity(Gravity.CENTER);
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            Button button = new Button(this); button.setText(tabs[i]); button.setTextSize(13);
            button.setAllCaps(false); button.setPadding(0, 0, 0, 0);
            button.setTextColor(i == selected ? Color.rgb(104, 70, 39) : Color.DKGRAY);
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
        b.setOnClickListener(v -> run.run()); body.addView(b);
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
    private void propertyScreen() {
        card("不動產與出租單位", "建築物、土地、房間與獨立車格。代號可用 R1401、C1。\n可修改名称及代號、建立租約；備份尚未啟用，請先用測試資料。");
        action("＋ 新增不動產", this::addProperty);
        JSONArray properties = data("properties"), units = data("units");
        if (properties.length() == 0) { card("尚未建檔", "先新增建築物或土地，再建立出租單位。"); return; }
        for (int i = 0; i < properties.length(); i++) {
            JSONObject p = properties.optJSONObject(i);
            StringBuilder detail = new StringBuilder(p.optString("type"));
            int count = 0;
            for (int j = 0; j < units.length(); j++) {
                JSONObject u = units.optJSONObject(j);
                if (p.optString("id").equals(u.optString("propertyId"))) {
                    detail.append("\n").append(u.optString("code")).append(" · ").append(u.optString("type")); count++;
                }
            }
            if (count == 0) detail.append("\n尚無出租單位");
            card(p.optString("name"), detail.toString());
            final JSONObject currentProperty = p;
            action("修改名稱", () -> editName("properties", currentProperty, "name", "不動產名稱"));
            final String id = p.optString("id"), name = p.optString("name");
            action("＋ " + name + "：新增出租單位", () -> addUnit(id, name));
            for (int j = 0; j < units.length(); j++) {
                final JSONObject u = units.optJSONObject(j);
                if (!id.equals(u.optString("propertyId"))) continue;
                action(u.optString("code") + "：修改代號", () -> editName("units", u, "code", "出租代號"));
                action(u.optString("code") + "：新增租約", () -> addLease(u));
                JSONArray leases = data("leases");
                for (int k = 0; k < leases.length(); k++) {
                    JSONObject l = leases.optJSONObject(k);
                    if (u.optString("id").equals(l.optString("unitId"))) card(u.optString("code") + " · " + l.optString("tenant"),
                        l.optString("start") + " ～ " + l.optString("end") + "\\n月租 $" + l.optLong("rent") + " · " + l.optString("cycle") +
                        "\\n約定押金 $" + l.optLong("deposit") + "（不是已收款）" + "\\n" + l.optString("email") +
                        "\\n" + l.optString("utilities") + (l.isNull("water") ? " · 水費待設定" : " · 水費 $" + l.optLong("water")));
                }
            }
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
    private void addLease(JSONObject unit) {
        LinearLayout layout = form();
        EditText tenant = field(layout, "租客姓名"), email = field(layout, "查看報表用 Gmail");
        EditText start = field(layout, "起租日 YYYY-MM-DD"), end = field(layout, "到期日 YYYY-MM-DD");
        EditText rent = number(layout, "月租金（年繳也填每月金額）"), day = number(layout, "每月應繳日 1–31");
        Spinner cycle = choice(layout, new String[]{"月繳", "年繳"});
        EditText deposit = number(layout, "約定押金，無押金填0");
        Spinner utilities = choice(layout, new String[]{"租客自行繳水電", "管理者代收水電"});
        EditText water = number(layout, "代收固定月水費，可留空待設定");
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
            long monthly, due, held, waterAmount;
            try { monthly = amount(rent, false); due = amount(day, false); held = amount(deposit, false); waterAmount = amount(water, true);
                if (monthly <= 0 || due < 1 || due > 31) throw new IllegalArgumentException();
            } catch (Exception e) { rent.setError("請檢查租金、押金、水費及應繳日"); return; }
            JSONArray leases = data("leases"), units = data("units");
            for (int i = 0; i < leases.length(); i++) {
                JSONObject old = leases.optJSONObject(i); JSONObject oldUnit = null;
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
                JSONObject lease = new JSONObject(); lease.put("id", UUID.randomUUID().toString()); lease.put("unitId", unit.optString("id"));
                lease.put("unitCodeSnapshot", unit.optString("code")); lease.put("tenant", person); lease.put("email", mail);
                lease.put("start", from.toString()); lease.put("end", to.toString()); lease.put("rent", monthly);
                lease.put("dueDay", due); lease.put("cycle", cycle.getSelectedItem().toString()); lease.put("deposit", held);
                lease.put("utilities", utilities.getSelectedItem().toString()); lease.put("electricRate", 5);
                lease.put("water", utilities.getSelectedItemPosition() == 0 || waterAmount == -1 ? JSONObject.NULL : waterAmount);
                leases.put(lease); if (save("leases", leases)) { dialog.dismiss(); render(); }
            } catch (JSONException e) { tenant.setError("保存格式錯誤"); }
        })); dialog.show();
    }


    private void exportBackup() {
        try {
            JSONObject backup = new JSONObject();
            backup.put("format", "rental-catalog"); backup.put("version", 1);
            for (String key : new String[]{"properties", "units", "leases"}) backup.put(key, data(key));
            pendingBackup = backup.toString(2);
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "rental-catalog-" + LocalDate.now() + ".json");
            startActivityForResult(intent, EXPORT);
        } catch (Exception e) { Toast.makeText(this, "無法準備備份", Toast.LENGTH_LONG).show(); }
    }
    private void importBackup() {
        new AlertDialog.Builder(this).setMessage("還原會替換目前的建檔資料。請先匯出備份；只接受本App的建檔備份檔。")
            .setNegativeButton("取消", null).setPositiveButton("選擇檔案", (d,w) -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*");
                startActivityForResult(intent, IMPORT);
            }).show();
    }
    private void validateBackup(JSONObject backup) throws Exception {
        if (!"rental-catalog".equals(backup.getString("format")) || backup.getInt("version") != 1)
            throw new IllegalArgumentException();
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
    }
    @Override protected void onActivityResult(int request, int result, Intent intent) {
        super.onActivityResult(request, result, intent);
        if (result != RESULT_OK || intent == null || intent.getData() == null) return;
        Uri uri = intent.getData();
        try {
            if (request == EXPORT) {
                if (pendingBackup == null) throw new IOException();
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
                new AlertDialog.Builder(this).setMessage("已驗證備份，確定替換目前的不動產、單位與租約？")
                    .setNegativeButton("取消", null).setPositiveButton("確認還原", (d,w) -> {
                        try {
                            android.content.SharedPreferences.Editor editor = getSharedPreferences("catalog",MODE_PRIVATE).edit();
                            for (String key : new String[]{"properties","units","leases"}) editor.putString(key,backup.getJSONArray(key).toString());
                            if (!editor.commit()) throw new IOException();
                            render(); Toast.makeText(this,"還原完成",Toast.LENGTH_LONG).show();
                        } catch (Exception e) { Toast.makeText(this,"還原未完成",Toast.LENGTH_LONG).show(); }
                    }).show();
            }
        } catch (Exception e) { Toast.makeText(this,"檔案讀寫失敗或備份無效，請重試",Toast.LENGTH_LONG).show(); }
    }

}
