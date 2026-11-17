package com.example.foodlog;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebResourceResponse;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.function.Consumer;

public class SyncActivity extends Activity {
    static final String SITE="https://food-log-br-sync-20261117.flutedhummingbird4.chatgpt.site";
    private WebView web;
    private TextView status;
    private LogStore store;
    private Button signIn;
    private boolean signInRequired;
    private int navigation;
    private boolean started=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);store=new LogStore(this);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        status=new TextView(this);status.setPadding(20,16,20,16);status.setText("Sign in with the same ChatGPT account used on your computer. Your Gmail password stays on this phone.");layout.addView(status);
        Button close=new Button(this);close.setText("Back to Food Log");close.setOnClickListener(v->finish());layout.addView(close);
        web=new WebView(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        signIn=new Button(this);signIn.setText("Sign in with ChatGPT");
        signIn.setOnClickListener(v->web.loadUrl(SITE+"/signin-with-chatgpt?return_to=%2Fphone"));layout.addView(signIn);
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView view,String url,Bitmap icon){navigation++;started=false;signInRequired=false;handler.removeCallbacksAndMessages(null);}
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){
                if(request.isForMainFrame()&&(response.getStatusCode()==401||response.getStatusCode()==403)){signInRequired=true;awaitSignIn();}
            }
            @Override public void onPageFinished(WebView view,String url){
                if(trusted()&&!started){
                    if(signInRequired){awaitSignIn();return;}
                    started=true;CookieManager.getInstance().flush();begin();
                }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !"https".equals(request.getUrl().getScheme());}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())status.setText("Could not connect. Your phone's log is still saved. Try Sync when online.");}
        });
        layout.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(layout);web.loadUrl(SITE+"/phone");
    }
    private boolean trusted(){Uri u=Uri.parse(web.getUrl()==null?"":web.getUrl());return "https".equals(u.getScheme())&&Uri.parse(SITE).getHost().equals(u.getHost())&&"/phone".equals(u.getPath())&&(u.getPort()==-1||u.getPort()==443);}
    private void request(JSONObject body,Consumer<JSONObject> done) {
        if(!trusted()){fail("Sign in to the Food Log website first.");return;}
        String options=body==null?"{cache:'no-store'}":"{method:'POST',headers:{'Content-Type':'application/json'},body:"+JSONObject.quote(body.toString())+"}";
        web.evaluateJavascript("window.__foodLogSync=null;fetch('/api/sync',"+options+").then(async r=>({status:r.status,body:await r.text()})).then(x=>window.__foodLogSync=JSON.stringify(x)).catch(()=>window.__foodLogSync=JSON.stringify({status:0,body:'Connection failed'}));",null);
        poll(done,0,navigation);
    }
    private void poll(Consumer<JSONObject> done,int attempt,int page){
        if(isFinishing()||isDestroyed()||page!=navigation)return;
        if(!trusted()){awaitSignIn();return;}
        if(attempt>150){fail("Sync timed out. Your changes remain on your phone. Try again.");return;}
        web.evaluateJavascript("window.__foodLogSync",value->{
            if(page!=navigation||isFinishing()||isDestroyed())return;
            try {
                if(value==null||value.equals("null")){handler.postDelayed(()->poll(done,attempt+1,page),200);return;}
                String text=new JSONArray("["+value+"]").getString(0);JSONObject result=new JSONObject(text);
                handleResponse(result.getInt("status"),result.getString("body"),done);
            }catch(Exception e){fail("Could not read the sync response. Your local log is unchanged.");}
        });
    }
    private void awaitSignIn(){
        started=false;
        status.setText("Sign in with the same ChatGPT account used on your computer. Sync will continue after sign-in. If access is denied, check that you are using the account that owns the website.");
        signIn.setVisibility(View.VISIBLE);
    }
    // An HTTP login page can have exactly the same URL as the connected page.
    // Only an authenticated JSON response is allowed to reach the merge/upload step.
    void handleResponse(int code,String body,Consumer<JSONObject> done){
        if(code==401||code==403||(code==200&&body.trim().startsWith("<"))){awaitSignIn();return;}
        if(code==409){fail("Another device saved changes. Tap Sync again to merge them.");return;}
        if(code!=200){fail("Could not sync (HTTP "+code+"). Your local log is unchanged. Please try again.");return;}
        try {
            JSONObject response=new JSONObject(body);
            if(response.getString("owner").isEmpty()||!response.has("revision")||!response.has("snapshot"))throw new IllegalArgumentException();
            signIn.setVisibility(View.GONE);
            status.setText("Syncing foods, recipes and history…");
            done.accept(response);
        }catch(Exception e){fail("Could not read the sync response. Your local log is unchanged.");}
    }
    private String description(Object value) {
        if(!(value instanceof JSONObject row))return "Removed";
        if(row.has("grams"))return row.optString("grams")+" g · "+row.optString("date");
        return row.optBoolean("active")?"On your list":"Removed from your list";
    }
    private void begin(){
        status.setText("Checking sign-in…");
        request(null,remote->{try {
            JSONObject local=store.snapshot(),base=store.syncBase();String owner=remote.getString("owner");
            if(!base.getString("owner").isEmpty()&&!base.getString("owner").equals(owner)){fail("This phone is already linked to a different account. Sign in with the original account to protect your data.");return;}
            SyncMerge.Result result=SyncMerge.merge(base.getJSONObject("snapshot"),local,remote.getJSONObject("snapshot"));
            if(result.conflicts().isEmpty()){upload(result.snapshot(),remote);return;}
            StringBuilder message=new StringBuilder("These entries changed on both devices. Other changes will merge.\n\n");
            for(var c:result.conflicts()) {
                JSONObject row=c.local() instanceof JSONObject?(JSONObject)c.local():(JSONObject)c.remote();
                message.append(row.optString("name","Food")).append("\nPhone: ").append(description(c.local())).append("\nBrowser: ").append(description(c.remote())).append("\n\n");
            }
            new AlertDialog.Builder(this).setTitle("Review conflicting edits").setMessage(message.toString())
                .setPositiveButton("Keep phone conflicts",(d,w)->upload(result.snapshot(),remote))
                .setNegativeButton("Keep browser conflicts",(d,w)->{try{SyncMerge.chooseRemote(result);upload(result.snapshot(),remote);}catch(Exception e){fail("Could not merge changes.");}})
                .setNeutralButton("Cancel",(d,w)->finish()).setCancelable(false).show();
        }catch(Exception e){fail("Could not prepare your log for sync. Nothing was removed.");}});
    }
    private void upload(JSONObject snapshot,JSONObject remote){try {
        if(SyncMerge.equal(snapshot,remote.getJSONObject("snapshot"))){apply(snapshot,remote);return;}
        request(new JSONObject().put("revision",remote.getLong("revision")).put("snapshot",snapshot),saved->apply(snapshot,saved));
    }catch(Exception e){fail("Could not upload changes. Your local log is unchanged.");}}
    private void apply(JSONObject snapshot,JSONObject saved){try {
        store.applySynced(snapshot,saved.getString("owner"),saved.getLong("revision"));
        getSharedPreferences("sync_ui",MODE_PRIVATE).edit().putLong("last_sync",System.currentTimeMillis()).apply();
        android.widget.Toast.makeText(this,"Synced with browser",android.widget.Toast.LENGTH_LONG).show();setResult(RESULT_OK);finish();
    }catch(Exception e){fail("Could not save the synced log on this phone. Your original local data is kept; try Sync again.");}}
    private void fail(String message){status.setText(message);new AlertDialog.Builder(this).setTitle("Sync incomplete").setMessage(message).setPositiveButton("Back",(d,w)->finish()).show();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(web!=null)web.destroy();if(store!=null)store.close();super.onDestroy();}
}
