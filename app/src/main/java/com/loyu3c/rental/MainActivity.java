package com.loyu3c.rental;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;

public class MainActivity extends Activity {
    private LinearLayout body;
    private final String[] tabs = {"首頁", "收租", "記帳", "房屋", "報表"};
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
        background.setColor(Color.WHITE); background.setCornerRadius(dp(16)); box.setBackground(background);
        TextView title = text(heading, 18, Color.rgb(28, 62, 60)); title.setTypeface(null, Typeface.BOLD);
        box.addView(title); box.addView(text(detail, 15, Color.rgb(77, 89, 99)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(8), 0, dp(8)); body.addView(box, params);
    }
    private void render() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(241, 245, 244)); root.setPadding(dp(16), dp(16), dp(16), dp(8));
        TextView heading = text("租務管理 / " + tabs[selected], 26, Color.rgb(25, 73, 67));
        heading.setTypeface(null, Typeface.BOLD); root.addView(heading);
        root.addView(text("介面預覽 · 尚未啟用記帳、拍照、語音或 Google 同步", 13, Color.rgb(137, 83, 15)));
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (selected == 0) {
            card("每月收租與支出", "尚未建檔，金額不顯示為零。\n本期應收、已收、欠款與各棟支出將在這裡呈現。");
            card("銀行與共用零用金", "A、B、C 三個銀行帳戶獨立追蹤；現金合併管理。\n期初餘額與對帳資料待設定。");
            card("待辦事項", "建檔後顯示未抄表、欠款、水費待設定與同步待辦。");
        } else if (selected == 1) {
            card("每月抄表", "選房間 → 拍照或上傳 → 輸入讀數 → 確認電費。\n每度 5 元；水費依租約設定。");
            card("核對合併收款", "租金、水費、電費分項列帳，合併匯款只記一筆。\n支援部分繳款、跨月與年繳分配。");
        } else if (selected == 2) {
            card("支出與分攤", "修繕、電梯保養、房貸總扣款。\n選銀行或現金付款，再填各棟分攤金額。");
            card("提款與轉帳", "銀行轉零用金是資金移動，不列為費用。");
            card("語音帳務草稿", "說話 → 轉文字與整理欄位 → 補齊資料 → 你確認。\n確認前不改變餘額。");
            card("押金", "收取、退款與扣抵分開追蹤。");
        } else if (selected == 3) {
            card("房屋與租約", "A、B、C 棟，可分房或整棟出租。\n租客更換時新增租約，保留舊帳。");
            card("獨立停車出租", "獨立承租人、租約、帳單及收款，不綁定房客。");
            card("退租結算", "最後抄表、欠款、預收款與押金結算。\n房間可已交還，舊租約仍保留待結算。");
        } else {
            card("爸爸的總報表", "三棟總覽、各棟收支、停車收入、押金與帳戶流水。\n不顯示電話。");
            card("租客的獨立報表", "每租約獨立檔案，只看自己的租期、押金與繳款紀錄。");
            card("存摺對帳與歷史補登", "同日期比較實際與帳面餘額。\n起算日前資料不重複影響期初餘額。");
            card("Google 同步：未設定", "目前尚未連接 Google，也沒有上傳任何資料。");
        }
        LinearLayout nav = new LinearLayout(this); nav.setGravity(Gravity.CENTER);
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            Button button = new Button(this); button.setText(tabs[i]); button.setTextSize(13);
            button.setAllCaps(false); button.setPadding(0, 0, 0, 0);
            button.setTextColor(i == selected ? Color.rgb(0, 105, 87) : Color.DKGRAY);
            button.setOnClickListener(v -> { selected = index; render(); });
            nav.addView(button, new LinearLayout.LayoutParams(0, dp(56), 1));
        }
        root.addView(nav); setContentView(root);
    }
}
