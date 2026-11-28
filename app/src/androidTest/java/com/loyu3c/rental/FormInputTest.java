package com.loyu3c.rental;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

/** Runs on a real emulator window/IME, not a mocked EditText. Never confirms transactions. */
@RunWith(AndroidJUnit4.class)
public class FormInputTest {
    private Instrumentation instrumentation;
    private MainActivity activity;
    private JSONObject lease;

    @Before public void launch() throws Exception {
        instrumentation=InstrumentationRegistry.getInstrumentation();
        Context context=instrumentation.getTargetContext();
        context.getSharedPreferences("catalog",0).edit().clear()
            .putString("properties","[{\"id\":\"p\",\"name\":\"Test property\",\"type\":\"建築物\"}]")
            .putString("units","[{\"id\":\"u\",\"propertyId\":\"p\",\"code\":\"101\",\"type\":\"房間\",\"enabled\":true}]")
            .putString("accounts","[{\"id\":\"a\",\"name\":\"Test cash\",\"type\":\"現金\",\"start\":\"2026-01-01\",\"opening\":0,\"propertyId\":\"\"}]")
            .commit();
        lease=new JSONObject("{\"id\":\"l\",\"unitId\":\"u\",\"unitCodeSnapshot\":\"101\",\"tenant\":\"Test\",\"email\":\"test@gmail.com\",\"start\":\"2026-01-01\",\"end\":\"2027-12-31\",\"rent\":8000,\"dueDay\":5,\"cycle\":\"月繳\",\"deposit\":8000,\"utilities\":\"管理者代收水電\",\"water\":200,\"electricRate\":5,\"initialMeter\":100}");
        activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        AccessibilityServiceInfo info=instrumentation.getUiAutomation().getServiceInfo();
        info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        instrumentation.getUiAutomation().setServiceInfo(info);
        instrumentation.waitForIdleSync();
    }

    @After public void finish(){if(activity!=null)instrumentation.runOnMainSync(()->activity.finish());}

    private void open(String name,Class<?>[] types,Object... values) throws Exception {
        Method method=MainActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);
        Throwable[] failure={null};instrumentation.runOnMainSync(()->{try{method.invoke(activity,values);}catch(Throwable e){failure[0]=e;}});
        if(failure[0]!=null)throw new AssertionError("Opening "+name,failure[0]);
        instrumentation.waitForIdleSync();
    }

    private AccessibilityNodeInfo editable(AccessibilityNodeInfo node,String hint){
        if(node==null)return null;
        String label=String.valueOf(node.getHintText())+" "+node.getText();
        if(node.isEditable()&&node.isEnabled()&&label.contains(hint))return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=editable(node.getChild(i),hint);if(found!=null)return found;}
        return null;
    }

    private AccessibilityNodeInfo awaitField(String hint){
        long until=SystemClock.uptimeMillis()+8000;
        do{AccessibilityNodeInfo field=editable(instrumentation.getUiAutomation().getRootInActiveWindow(),hint);if(field!=null)return field;SystemClock.sleep(100);}while(SystemClock.uptimeMillis()<until);
        throw new AssertionError("No editable field: "+hint);
    }

    private void typeWithKeyboard(String hint){
        AccessibilityNodeInfo field=awaitField(hint);
        assertTrue("Field click",field.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        long until=SystemClock.uptimeMillis()+8000;boolean keyboard=false;
        do{for(AccessibilityWindowInfo window:instrumentation.getUiAutomation().getWindows())if(window.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD)keyboard=true;if(keyboard)break;SystemClock.sleep(100);}while(SystemClock.uptimeMillis()<until);
        assertTrue("Tapping "+hint+" must show the soft keyboard",keyboard);
        instrumentation.sendStringSync("123");instrumentation.waitForIdleSync();
        until=SystemClock.uptimeMillis()+8000;String actual="<stale>";
        do{if(field.refresh())actual=String.valueOf(field.getText());if(actual.contains("123"))break;SystemClock.sleep(100);}while(SystemClock.uptimeMillis()<until);
        assertTrue("Actual typed characters must reach "+hint+"; observed="+actual,actual.contains("123"));
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
    }

    @Test public void newPropertyAcceptsText() throws Exception {
        open("addProperty",new Class<?>[]{});typeWithKeyboard("不動產名稱");
    }
    @Test public void billAcceptsMeterNumber() throws Exception {
        open("billingForm",new Class<?>[]{JSONObject.class},lease);typeWithKeyboard("本期電表");
    }
    @Test public void newLeaseAcceptsTenantText() throws Exception {
        JSONObject unit=new JSONObject("{\"id\":\"u\",\"propertyId\":\"p\",\"code\":\"101\",\"type\":\"房間\",\"enabled\":true}");
        open("addLease",new Class<?>[]{JSONObject.class},unit);typeWithKeyboard("租客姓名");
    }
    @Test public void expenseAcceptsDescriptionText() throws Exception {
        open("expenseForm",new Class<?>[]{});typeWithKeyboard("項目／備註");
    }

    private AccessibilityNodeInfo labelled(AccessibilityNodeInfo node,String label,boolean clickable){
        if(node==null)return null;
        String text=String.valueOf(node.getText())+" "+node.getContentDescription();
        if(text.contains(label)&&(!clickable||node.isClickable()))return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=labelled(node.getChild(i),label,clickable);if(found!=null)return found;}
        return null;
    }
    private AccessibilityNodeInfo awaitLabel(String label,boolean clickable){
        long until=SystemClock.uptimeMillis()+8000;
        do{AccessibilityNodeInfo node=labelled(instrumentation.getUiAutomation().getRootInActiveWindow(),label,clickable);if(node!=null)return node;SystemClock.sleep(100);}while(SystemClock.uptimeMillis()<until);
        try{reviewScreenshot("failed-ui.png");}catch(Exception ignored){}
        throw new AssertionError("Missing UI label: "+label+"; active window: "+uiLabels(instrumentation.getUiAutomation().getRootInActiveWindow()));
    }
    @Test public void resetSearchRestoresExpenseWithoutChangingLedger() throws Exception {
        Context context=instrumentation.getTargetContext();
        String records="[{\"id\":\"e\",\"date\":\"2026-10-01\",\"accountId\":\"a\",\"category\":\"repair\",\"description\":\"Repair check\",\"amount\":900,\"splits\":[{\"propertyId\":\"p\",\"amount\":900}]}]";
        context.getSharedPreferences("catalog",0).edit().putString("expenses",records).commit();
        java.lang.reflect.Field selected=MainActivity.class.getDeclaredField("selected");selected.setAccessible(true);
        instrumentation.runOnMainSync(()->{try{selected.setInt(activity,2);}catch(Exception e){throw new RuntimeException(e);}});
        open("render",new Class<?>[]{});
        assertTrue(awaitLabel("搜尋與篩選",true).performAction(AccessibilityNodeInfo.ACTION_CLICK));
        typeWithKeyboard("搜尋項目");
        awaitLabel("沒有符合條件的紀錄",false);
        assertTrue(awaitLabel("重設搜尋與篩選",true).performAction(AccessibilityNodeInfo.ACTION_CLICK));
        awaitLabel("Repair check",false);
        assertEquals(records,context.getSharedPreferences("catalog",0).getString("expenses",null));
    }
    @Test public void photoOpensInsideAppAndBackReturnsToSource() throws Exception {
        Context context=instrumentation.getTargetContext();
        Method photoDir=MainActivity.class.getDeclaredMethod("photoDir");photoDir.setAccessible(true);
        java.io.File dir=(java.io.File)photoDir.invoke(activity);dir.mkdirs();java.io.File file=new java.io.File(dir,"viewer-test.jpg");
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(64,96,android.graphics.Bitmap.Config.ARGB_8888);
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,out);}finally{bitmap.recycle();}
        try{
            open("viewMeterPhoto",new Class<?>[]{String.class},file.getName());
            awaitLabel("照片大圖",false);
            assertEquals(context.getPackageName(),String.valueOf(instrumentation.getUiAutomation().getRootInActiveWindow().getPackageName()));
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);instrumentation.waitForIdleSync();
            AccessibilityNodeInfo source=awaitLabel("暖色帳本",false);
            assertEquals(context.getPackageName(),String.valueOf(source.getPackageName()));
            assertNull(labelled(instrumentation.getUiAutomation().getRootInActiveWindow(),"照片大圖",false));
        }finally{file.delete();}
    }

    @Test public void refundReasonAcceptsInputWithoutPostingMoney() throws Exception {
        Context context=instrumentation.getTargetContext();
        JSONObject receipt=new JSONObject("{\"id\":\"R\",\"leaseId\":\"l\",\"date\":\"2026-01-01\",\"account\":\"Test cash\",\"accountId\":\"a\",\"amount\":200,\"allocations\":[],\"note\":\"\"}");
        String before=new org.json.JSONArray().put(receipt).toString();
        context.getSharedPreferences("catalog",0).edit().putString("leases",new org.json.JSONArray().put(lease).toString()).putString("receipts",before).commit();
        open("rentRefundForm",new Class<?>[]{JSONObject.class},receipt);typeWithKeyboard("退款原因");
        assertEquals(before,context.getSharedPreferences("catalog",0).getString("receipts",null));
    }
    @Test public void voidExpenseStartsCollapsedAndCanExpandReason() throws Exception {
        Context context=instrumentation.getTargetContext();
        String records="[{\"id\":\"e\",\"date\":\"2026-10-01\",\"accountId\":\"a\",\"category\":\"repair\",\"description\":\"Wrong expense\",\"amount\":900,\"void\":true,\"voidReason\":\"Duplicate entry\",\"splits\":[{\"propertyId\":\"p\",\"amount\":900}]}]";
        context.getSharedPreferences("catalog",0).edit().putString("expenses",records).commit();
        java.lang.reflect.Field selected=MainActivity.class.getDeclaredField("selected");selected.setAccessible(true);
        instrumentation.runOnMainSync(()->{try{selected.setInt(activity,2);}catch(Exception e){throw new RuntimeException(e);}});open("render",new Class<?>[]{});
        assertNull(labelled(instrumentation.getUiAutomation().getRootInActiveWindow(),"Duplicate entry",false));
        assertTrue(awaitLabel("已撤銷／作廢",true).performAction(AccessibilityNodeInfo.ACTION_CLICK));awaitLabel("Duplicate entry",false);
        assertEquals(records,context.getSharedPreferences("catalog",0).getString("expenses",null));
    }

    @Test public void compactLedgerShowsMoreRowsAndOpensFullDetailsAndExport() throws Exception {
        Context context=instrumentation.getTargetContext();org.json.JSONArray rows=new org.json.JSONArray();
        for(int i=0;i<25;i++)rows.put(new JSONObject().put("id","e"+i).put("date","2026-11-28").put("accountId","a").put("category","repair").put("description",i==0?"Repair check with a very long description to inspect in full detail":"Repair check "+i).put("amount",900+i).put("splits",new org.json.JSONArray().put(new JSONObject().put("propertyId","p").put("amount",900+i))));
        String before=rows.toString();context.getSharedPreferences("catalog",0).edit().putString("expenses",before).commit();
        java.lang.reflect.Field selected=MainActivity.class.getDeclaredField("selected");selected.setAccessible(true);
        instrumentation.runOnMainSync(()->{try{selected.setInt(activity,2);}catch(Exception e){throw new RuntimeException(e);}});open("render",new Class<?>[]{});
        awaitLabel("Repair check",false);awaitLabel("匯出",true);assertNull(labelled(instrumentation.getUiAutomation().getRootInActiveWindow(),"此筆紀錄更多操作",true));
        android.content.ContentValues picture=new android.content.ContentValues();picture.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"compact-ledger.png");picture.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");picture.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/RentalUiReview");android.net.Uri screenshot=context.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,picture);assertNotNull(screenshot);android.graphics.Bitmap screen=instrumentation.getUiAutomation().takeScreenshot();try(java.io.OutputStream out=context.getContentResolver().openOutputStream(screenshot)){assertNotNull(out);assertTrue(screen.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}finally{screen.recycle();}
        assertTrue(awaitLabel("開啟收支全貌檢視",true).performAction(AccessibilityNodeInfo.ACTION_CLICK));awaitLabel("收支全貌",false);assertTrue("Overview should show at least seven records",awaitLabel("Repair check 6",false).isVisibleToUser());
        android.content.ContentValues whole=new android.content.ContentValues();whole.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"ledger-overview.png");whole.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");whole.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/RentalUiReview");android.net.Uri wholeUri=context.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,whole);android.graphics.Bitmap wholeScreen=instrumentation.getUiAutomation().takeScreenshot();try(java.io.OutputStream out=context.getContentResolver().openOutputStream(wholeUri)){wholeScreen.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{wholeScreen.recycle();}instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);instrumentation.waitForIdleSync();
        AccessibilityNodeInfo entry=awaitLabel("Repair check with a very long description",false);while(entry!=null&&!entry.isClickable())entry=entry.getParent();assertNotNull("Row must open details",entry);assertTrue(entry.performAction(AccessibilityNodeInfo.ACTION_CLICK));awaitLabel("with a very long description",false);
        assertTrue(awaitLabel("此筆支出的更多操作",true).performAction(AccessibilityNodeInfo.ACTION_CLICK));awaitLabel("修改",false);awaitLabel("撤銷",false);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);instrumentation.waitForIdleSync();awaitLabel("此筆支出的更多操作",true);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);instrumentation.waitForIdleSync();awaitLabel("收支紀錄",false);
        assertTrue(awaitLabel("匯出",true).performAction(AccessibilityNodeInfo.ACTION_CLICK));awaitLabel("檔案格式",false);awaitLabel("包含已撤銷紀錄",false);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);instrumentation.waitForIdleSync();assertEquals(before,context.getSharedPreferences("catalog",0).getString("expenses",null));
    }

    private String uiLabels(AccessibilityNodeInfo node){if(node==null)return "<no active window>";StringBuilder value=new StringBuilder();appendLabels(node,value);return value.toString();}
    private void appendLabels(AccessibilityNodeInfo node,StringBuilder out){if(node==null||out.length()>8000)return;out.append("[").append(node.getText()).append(" | ").append(node.getContentDescription()).append("]");for(int i=0;i<node.getChildCount();i++)appendLabels(node.getChild(i),out);}
    private void reviewScreenshot(String name)throws Exception{
        Context context=instrumentation.getTargetContext();android.content.ContentValues values=new android.content.ContentValues();values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,name);values.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/RentalUiReview");android.net.Uri uri=context.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);assertNotNull(uri);android.graphics.Bitmap bitmap=instrumentation.getUiAutomation().takeScreenshot();try(java.io.OutputStream out=context.getContentResolver().openOutputStream(uri)){assertNotNull(out);assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}
    }
    private void listSection(int page,int section)throws Exception{
        java.lang.reflect.Field selected=MainActivity.class.getDeclaredField("selected"),sections=MainActivity.class.getDeclaredField("sections");selected.setAccessible(true);sections.setAccessible(true);instrumentation.runOnMainSync(()->{try{selected.setInt(activity,page);((int[])sections.get(activity))[page]=section;}catch(Exception e){throw new RuntimeException(e);}});open("render",new Class<?>[]{});
    }
    @Test public void compactRentPaymentsAndAccountsKeepDetailsAndTotals()throws Exception{
        Context context=instrumentation.getTargetContext();org.json.JSONArray leases=new org.json.JSONArray(),units=new org.json.JSONArray(),accounts=new org.json.JSONArray(),receipts=new org.json.JSONArray(),deposits=new org.json.JSONArray();
        for(int i=0;i<10;i++){
            String code=String.format(java.util.Locale.US,"T%02d",i);leases.put(new JSONObject(lease.toString()).put("id","l"+i).put("unitId","u"+i).put("unitCodeSnapshot",code).put("tenant","租客"+i));units.put(new JSONObject().put("id","u"+i).put("propertyId","p").put("code",code).put("type","房間").put("enabled",true));accounts.put(new JSONObject().put("id","bank"+i).put("name","Bank"+i).put("type","銀行").put("start","2026-01-01").put("opening",i*100).put("propertyId",""));if(i<8)receipts.put(new JSONObject().put("id","r"+i).put("leaseId","l"+i).put("date","2026-11-15").put("accountId","bank0").put("account","Bank0").put("amount",1000).put("cash",true).put("note","完整收款備註"+i).put("allocations",new org.json.JSONArray()));
        }
        for(int i=0;i<2;i++)deposits.put(new JSONObject().put("id","d"+i).put("leaseId","l0").put("date","2026-11-14").put("accountId","bank0").put("kind",i==0?"收到押金":"退還押金").put("amount",i==0?2000:500).put("void",false).put("note","押金測試"));
        String beforeReceipts=receipts.toString(),beforeDeposits=deposits.toString();context.getSharedPreferences("catalog",0).edit().putString("leases",leases.toString()).putString("units",units.toString()).putString("accounts",accounts.toString()).putString("receipts",beforeReceipts).putString("deposits",beforeDeposits).commit();
        listSection(1,0);assertTrue("Six leases should be visible",awaitLabel("T05",false).isVisibleToUser());reviewScreenshot("rent-list.png");assertTrue(awaitLabel("T00",true).performAction(AccessibilityNodeInfo.ACTION_CLICK));awaitLabel("概況",false);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);instrumentation.waitForIdleSync();awaitLabel("應收帳務",false);
        listSection(1,1);java.lang.reflect.Field scope=MainActivity.class.getDeclaredField("paymentScope");scope.setAccessible(true);java.lang.reflect.Field month=MainActivity.class.getDeclaredField("paymentMonth");month.setAccessible(true);instrumentation.runOnMainSync(()->{try{scope.setInt(activity,0);month.set(activity,"2026-11");}catch(Exception e){throw new RuntimeException(e);}});open("render",new Class<?>[]{});awaitLabel("淨流入 $9,500",false);assertTrue("Six payments should be visible",awaitLabel("T05",false).isVisibleToUser());reviewScreenshot("payment-list.png");assertTrue(awaitLabel("查看符合查詢",true).performAction(AccessibilityNodeInfo.ACTION_CLICK));awaitLabel("押金收取 $2,000",false);awaitLabel("退還 $500",false);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);instrumentation.waitForIdleSync();assertTrue(awaitLabel("2026-11-15 · 租金水電",true).performAction(AccessibilityNodeInfo.ACTION_CLICK));awaitLabel("收款明細",false);awaitLabel("完整收款備註",false);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);instrumentation.waitForIdleSync();awaitLabel("淨流入 $9,500",false);
        listSection(2,2);assertTrue("Six accounts should be visible",awaitLabel("Bank5",false).isVisibleToUser());reviewScreenshot("accounts-list.png");assertTrue(awaitLabel("Bank0",true).performAction(AccessibilityNodeInfo.ACTION_CLICK));awaitLabel("Bank0 流水與對帳",false);awaitLabel("帳面餘額 $9,500",false);instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);instrumentation.waitForIdleSync();assertEquals(beforeReceipts,context.getSharedPreferences("catalog",0).getString("receipts",null));assertEquals(beforeDeposits,context.getSharedPreferences("catalog",0).getString("deposits",null));
    }

}
