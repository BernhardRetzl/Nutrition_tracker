package com.example.foodlog;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import org.json.JSONObject;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Main-thread coordinator; only HTTP runs on the worker. Shared across rotations. */
final class AutoSync {
    interface Transport { JSONObject request(JSONObject body) throws Exception; }
    interface Listener { void changed(boolean dataChanged); }
    static final class NeedsAttention extends Exception { NeedsAttention(String message){super(message);} }
    private final LogStore store;
    private final Transport transport;
    private final Executor worker;
    private final Executor main;
    private Listener listener;
    private boolean busy;
    private String status="Sign in once using Sync to enable automatic sync";
    AutoSync(Context context) {
        this(new LogStore(context.getApplicationContext()),AutoSync::http,
            Executors.newSingleThreadExecutor(),r->new Handler(Looper.getMainLooper()).post(r));
    }
    AutoSync(LogStore store,Transport transport,Executor worker,Executor main){this.store=store;this.transport=transport;this.worker=worker;this.main=main;}
    void observe(Listener listener){this.listener=listener;}
    boolean busy(){return busy;}
    String status(){return status;}
    private void report(String message,boolean changed){status=message;if(listener!=null)listener.changed(changed);}
    void sync(){
        if(busy)return;
        try {
            if(store.syncBase().getString("owner").isEmpty()){report("Sign in once using Sync to enable automatic sync",false);return;}
        }catch(Exception e){report("Saved locally · sync unavailable",false);return;}
        busy=true;report("Syncing…",false);
        request(null,remote->{
            try {
                JSONObject base=store.syncBase(),local=store.snapshot();
                if(!base.getString("owner").equals(remote.getString("owner")))throw new NeedsAttention("Account changed · tap Sync to sign in again");
                var merge=SyncMerge.merge(base.getJSONObject("snapshot"),local,remote.getJSONObject("snapshot"));
                if(!merge.conflicts().isEmpty())throw new NeedsAttention("Conflicting edits · tap Sync to review");
                if(SyncMerge.equal(merge.snapshot(),remote.getJSONObject("snapshot"))){complete(local,remote);return;}
                JSONObject payload=new JSONObject().put("revision",remote.getLong("revision")).put("snapshot",merge.snapshot());
                request(payload,saved->complete(local,saved));
            }catch(Exception e){failed(e);}
        });
    }
    private void request(JSONObject body,java.util.function.Consumer<JSONObject> success){
        worker.execute(()->{
            try {JSONObject response=transport.request(body);main.execute(()->success.accept(response));}
            catch(Exception e){main.execute(()->failed(e));}
        });
    }
    private void complete(JSONObject sentLocal,JSONObject saved){
        try {
            String owner=store.syncBase().getString("owner");
            if(!owner.equals(saved.getString("owner")))throw new NeedsAttention("Account changed · tap Sync to sign in again");
            JSONObject current=store.snapshot(),server=saved.getJSONObject("snapshot");
            // Rebase edits made while HTTP was in flight; never replace newer local weights.
            var follow=SyncMerge.merge(sentLocal,current,server);
            if(!follow.conflicts().isEmpty())throw new NeedsAttention("Conflicting edits · tap Sync to review");
            boolean changed=!SyncMerge.equal(current,follow.snapshot());
            store.applySynced(follow.snapshot(),owner,saved.getLong("revision"),server);
            busy=false;report(SyncMerge.equal(follow.snapshot(),server)?"Synced automatically":"Saved locally · sync pending",changed);
        }catch(Exception e){failed(e);}
    }
    private void failed(Exception e){busy=false;report(e instanceof NeedsAttention?e.getMessage():"Saved locally · will retry sync automatically",false);}
    private static JSONObject http(JSONObject body)throws Exception {
        String address=SyncActivity.SITE+"/api/sync";
        String cookies=CookieManager.getInstance().getCookie(address);
        if(cookies==null||cookies.isEmpty())throw new NeedsAttention("Sign-in expired · tap Sync to reconnect");
        HttpsURLConnection connection=(HttpsURLConnection)new URL(address).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(15000);
            connection.setRequestProperty("Cookie",cookies);connection.setRequestProperty("Accept","application/json");
            connection.setRequestProperty("Origin",SyncActivity.SITE);connection.setUseCaches(false);
            if(body!=null){connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");try(var out=connection.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int code=connection.getResponseCode();
            if(code==401||code==403||(code>=300&&code<400))throw new NeedsAttention("Sign-in expired · tap Sync to reconnect");
            if(code==409)throw new java.io.IOException("Revision changed; retry");
            if(code!=200)throw new java.io.IOException("Sync failed");
            if(connection.getContentType()==null||!connection.getContentType().contains("application/json"))throw new NeedsAttention("Sign-in required · tap Sync to reconnect");
            try(var in=connection.getInputStream();var bytes=new java.io.ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int count;
                while((count=in.read(buffer))!=-1){if(bytes.size()+count>5000000)throw new java.io.IOException("Response too large");bytes.write(buffer,0,count);}
                return new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            }
        }finally{connection.disconnect();}
    }
}
