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
        throw new AssertionError("Missing UI label: "+label);
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
}
