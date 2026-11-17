package com.example.foodlog;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class AutoSyncTest {
    private LogStore store;
    private final ArrayDeque<Runnable> work=new ArrayDeque<>();
    @Before public void setup()throws Exception {
        var context=RuntimeEnvironment.getApplication();context.deleteDatabase("food-log.db");store=new LogStore(context);
        store.add("Milk");save("100");store.applySynced(store.snapshot(),"owner",1);
    }
    private void save(String grams){store.save("2026-11-17",store.foods("2026-11-17",true).get(0),grams);}
    private JSONObject response(JSONObject snapshot,long revision)throws Exception{return new JSONObject().put("owner","owner").put("revision",revision).put("snapshot",snapshot);}
    private String grams(JSONObject snapshot)throws Exception {var entries=snapshot.getJSONObject("entries");return entries.getJSONObject(entries.keys().next()).getString("grams");}
    @Test public void uploadsLocalEditsWithoutPrompting()throws Exception {
        JSONObject remote=store.snapshot();save("125");AtomicInteger posts=new AtomicInteger();
        AutoSync sync=new AutoSync(store,body->{if(body==null)return response(remote,1);posts.incrementAndGet();assertEquals("125",grams(body.getJSONObject("snapshot")));return response(body.getJSONObject("snapshot"),2);},work::add,Runnable::run);
        sync.sync();sync.sync();assertEquals(1,work.size());work.remove().run();work.remove().run();
        assertEquals(1,posts.get());assertFalse(sync.busy());assertEquals("Synced automatically",sync.status());assertEquals(2,store.syncBase().getLong("revision"));
    }
    @Test public void editsDuringUploadSurviveAndAreUploadedOnNextPass()throws Exception {
        JSONObject[] remote={store.snapshot()};long[] revision={1};save("125");
        AutoSync sync=new AutoSync(store,body->{if(body!=null){remote[0]=body.getJSONObject("snapshot");revision[0]++;}return response(remote[0],revision[0]);},work::add,Runnable::run);
        sync.sync();work.remove().run(); // GET merges and queues POST of 125 g.
        save("193");work.remove().run();
        assertEquals("193",grams(store.snapshot()));assertEquals("125",grams(store.syncBase().getJSONObject("snapshot")));
        sync.sync();work.remove().run();work.remove().run();
        assertEquals("193",grams(remote[0]));assertEquals("Synced automatically",sync.status());
    }
    @Test public void downloadsRemoteChanges()throws Exception {
        JSONObject remote=store.snapshot();var entries=remote.getJSONObject("entries");entries.getJSONObject(entries.keys().next()).put("grams","250");
        AutoSync sync=new AutoSync(store,body->{assertNull(body);return response(remote,2);},work::add,Runnable::run);
        sync.sync();work.remove().run();assertEquals("250",grams(store.snapshot()));
    }
    @Test public void conflictsDoNotUploadOrOverwrite()throws Exception {
        JSONObject remote=store.snapshot();var entries=remote.getJSONObject("entries");entries.getJSONObject(entries.keys().next()).put("grams","250");save("193");
        AutoSync sync=new AutoSync(store,body->{assertNull(body);return response(remote,2);},work::add,Runnable::run);
        sync.sync();work.remove().run();assertTrue(work.isEmpty());assertTrue(sync.status().contains("Conflicting"));assertEquals("193",grams(store.snapshot()));assertEquals(1,store.syncBase().getLong("revision"));
    }
    @Test public void offlineFailurePreservesDataAndNextAttemptRetries()throws Exception {
        save("125");AtomicInteger calls=new AtomicInteger();JSONObject before=store.snapshot();
        AutoSync sync=new AutoSync(store,body->{calls.incrementAndGet();throw new java.io.IOException();},work::add,Runnable::run);
        sync.sync();work.remove().run();assertFalse(sync.busy());assertTrue(sync.status().contains("retry"));assertTrue(SyncMerge.equal(before,store.snapshot()));
        sync.sync();work.remove().run();assertEquals(2,calls.get());
    }
    @Test public void changedAccountNeverUploads()throws Exception {
        JSONObject remote=response(SyncMerge.empty(),0).put("owner","someone-else");save("125");
        AutoSync sync=new AutoSync(store,body->{assertNull(body);return remote;},work::add,Runnable::run);
        sync.sync();work.remove().run();assertTrue(work.isEmpty());assertTrue(sync.status().contains("Account changed"));assertEquals("125",grams(store.snapshot()));
    }
    @Test public void firstRunWaitsForExplicitSignIn()throws Exception {
        store.applySynced(store.snapshot(),"",0);
        AutoSync sync=new AutoSync(store,body->{fail("No request before linking an account");return null;},work::add,Runnable::run);
        sync.sync();assertTrue(work.isEmpty());assertFalse(sync.busy());assertTrue(sync.status().contains("Sign in once"));
    }
    @Test public void mainScreenSyncsOnOpenAfterEditsAndStopsTimerWhenPaused()throws Exception {
        AutoSync sync=new AutoSync(store,body->response(body==null?store.snapshot():body.getJSONObject("snapshot"),2),work::add,Runnable::run);
        var app=RuntimeEnvironment.getApplication();
        org.robolectric.util.ReflectionHelpers.setField(app,"sync",sync);
        try(var controller=org.robolectric.Robolectric.buildActivity(MainActivity.class).setup()) {
            var main=org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper());
            main.idle();assertEquals(1,work.size());work.remove().run();
            java.util.List<android.widget.EditText> inputs=org.robolectric.util.ReflectionHelpers.getField(controller.get(),"weightInputs");
            inputs.get(0).setText("150");
            main.idleFor(java.time.Duration.ofSeconds(1));assertTrue(work.isEmpty());
            main.idleFor(java.time.Duration.ofSeconds(1));assertEquals(1,work.size());work.remove().run();
            main.idleFor(java.time.Duration.ofSeconds(15));assertEquals(1,work.size());work.remove().run();
            controller.pause();main.idleFor(java.time.Duration.ofSeconds(30));assertTrue(work.isEmpty());
        }
    }

}
