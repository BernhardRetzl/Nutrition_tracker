package com.example.foodlog;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.json.JSONObject;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class SyncTest {
    JSONObject food() throws Exception{return new JSONObject().put("name","Milk").put("active",true).put("recipeId","");}
    JSONObject entry(String grams) throws Exception{return new JSONObject().put("foodId","milk").put("date","2026-11-17").put("name","Milk").put("grams",grams);}
    JSONObject copy(JSONObject v)throws Exception{return new JSONObject(v.toString());}
    @Test public void mergesUnrelatedEditsAndFlagsSameWeightConflicts() throws Exception {
        JSONObject b=SyncMerge.empty();b.getJSONObject("foods").put("milk",food());
        JSONObject l=copy(b),r=copy(b);l.getJSONObject("entries").put("milk@2026-11-17",entry("125"));r.getJSONObject("foods").put("oats",food().put("name","Oats"));
        var merge=SyncMerge.merge(b,l,r);assertTrue(merge.conflicts().isEmpty());assertTrue(merge.snapshot().getJSONObject("foods").has("oats"));assertEquals("125",merge.snapshot().getJSONObject("entries").getJSONObject("milk@2026-11-17").getString("grams"));
        r.getJSONObject("entries").put("milk@2026-11-17",entry("68"));merge=SyncMerge.merge(b,l,r);assertEquals(1,merge.conflicts().size());SyncMerge.chooseRemote(merge);assertEquals("68",merge.snapshot().getJSONObject("entries").getJSONObject("milk@2026-11-17").getString("grams"));
    }
    @Test public void removalVersusNewWeightRequiresReview() throws Exception {
        JSONObject b=SyncMerge.empty();b.getJSONObject("foods").put("milk",food());JSONObject l=copy(b),r=copy(b);
        l.getJSONObject("foods").getJSONObject("milk").put("active",false);r.getJSONObject("entries").put("milk@2026-11-17",entry("250"));
        var merged=SyncMerge.merge(b,l,r);assertEquals(2,merged.conflicts().size());assertEquals(0,merged.snapshot().getJSONObject("entries").length());
        SyncMerge.chooseRemote(merged);assertTrue(merged.snapshot().getJSONObject("foods").getJSONObject("milk").getBoolean("active"));assertEquals(1,merged.snapshot().getJSONObject("entries").length());
    }
    @Test public void importsExistingLogsAndKeepsStableIdsAcrossSync()throws Exception {
        var c=RuntimeEnvironment.getApplication();c.deleteDatabase("food-log.db");
        try(var db=new LogStore(c)) {
            db.add("Milk");long recipe=db.addRecipe("Smoothie");db.add("Banana",recipe);var foods=db.foods("2026-11-17",true);
            db.save("2026-11-16",foods.get(0),"250");db.save("2026-11-17",foods.get(1),"125");
            JSONObject original=db.snapshot();assertEquals(2,original.getJSONObject("foods").length());
            JSONObject remote=copy(original);String bananaKey="";var it=remote.getJSONObject("foods").keys();while(it.hasNext()){String key=it.next();if(remote.getJSONObject("foods").getJSONObject(key).getString("name").equals("Banana"))bananaKey=key;}
            remote.getJSONObject("entries").getJSONObject(bananaKey+"@2026-11-17").put("grams","193");db.applySynced(remote,"owner",2);
            assertEquals(foods.get(1).id(),db.foods("2026-11-17",true).get(1).id());assertTrue(db.csv("2026-11-17").contains(",193"));assertTrue(db.csv("2026-11-16").contains(",250"));assertTrue(SyncMerge.equal(remote,db.snapshot()));assertEquals(2,db.syncBase().getInt("revision"));
        }
    }
    @Test public void failedImportRollsBackLocalHistory()throws Exception {
        var c=RuntimeEnvironment.getApplication();c.deleteDatabase("food-log.db");try(var db=new LogStore(c)){db.add("Milk");db.save("2026-11-17",db.foods("2026-11-17",true).get(0),"250");JSONObject before=db.snapshot(),bad=copy(before);bad.getJSONObject("entries").put("bad",entry("300").put("foodId","missing"));assertThrows(Exception.class,()->db.applySynced(bad,"owner",1));assertTrue(SyncMerge.equal(before,db.snapshot()));}
    }
}
