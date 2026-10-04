package com.loyu3c.rental;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class PaymentHistoryTest {
    private JSONObject payment(String id,String date,long amount) throws Exception {return new JSONObject().put("id",id).put("date",date).put("leaseId","L").put("accountId","A").put("amount",amount);}
    private JSONObject backup() throws Exception {return new JSONObject().put("leases",new JSONArray().put(new JSONObject().put("id","L").put("tenant","王小明").put("unitCodeSnapshot","C1"))).put("accounts",new JSONArray().put(new JSONObject().put("id","A").put("name","農會"))).put("receipts",new JSONArray()).put("deposits",new JSONArray()).put("settlements",new JSONArray());}
    @Test public void actualCashOnlyAndSeparateDepositFlowsWithoutDoubleCounting() throws Exception {
        JSONObject b=backup();b.getJSONArray("receipts").put(payment("rent","2026-10-02",1000)).put(payment("offset","2026-10-03",500).put("cash",false)).put(payment("generated","2026-10-04",200).put("cash",false));
        b.getJSONArray("deposits").put(payment("deposit","2026-10-01",3000).put("kind","收到押金")).put(payment("refund","2026-10-05",2500).put("kind","退還押金")).put(payment("used","2026-10-03",500).put("kind","押金抵付費用")).put(payment("void","2026-10-01",9999).put("kind","收到押金").put("void",true));
        b.getJSONArray("settlements").put(new JSONObject().put("leaseId","L").put("payments",new JSONArray().put(payment("topup","2026-10-04",200))));String before=b.toString();JSONObject result=PaymentHistory.query(b,"2026-10","全部","");
        assertEquals(4,result.getJSONArray("rows").length());assertEquals(1000,result.getLong("rent"));assertEquals(3000,result.getLong("depositReceived"));assertEquals(2500,result.getLong("depositRefunded"));assertEquals(200,result.getLong("topups"));assertEquals(1700,result.getLong("net"));assertEquals("refund",result.getJSONArray("rows").getJSONObject(0).getString("id").split(":")[1]);assertEquals(before,b.toString());
    }
    @Test public void monthKindAndSearchFilterRowsAndTotalsTogether() throws Exception {
        JSONObject b=backup();b.getJSONArray("receipts").put(payment("old","2026-09-30",100)).put(payment("new","2026-10-01",12500));
        assertEquals(1,PaymentHistory.query(b,"2026-10","全部","王小明").getJSONArray("rows").length());assertEquals(1,PaymentHistory.query(b,"","租金水電","12,500").getJSONArray("rows").length());assertEquals(12600,PaymentHistory.query(b,"","全部","農會").getLong("rent"));assertEquals(0,PaymentHistory.query(b,"2026-10","退還押金","C1").getLong("net"));
    }
}
