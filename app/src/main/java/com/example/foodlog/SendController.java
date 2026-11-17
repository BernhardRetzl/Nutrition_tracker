package com.example.foodlog;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

final class SendController {
    interface Delivery { void send(String from,String to,String password,String day,String csv) throws Exception; }
    interface Password { String read() throws Exception; }
    private final SharedPreferences prefs;
    private final Executor executor;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Delivery delivery;
    private boolean sending;
    private Runnable observer;
    SendController(Context context) { this(context,Executors.newSingleThreadExecutor(),GmailSender::send); }
    SendController(Context context,Executor executor,Delivery delivery) {
        this.executor=executor;this.delivery=delivery;
        prefs=context.getSharedPreferences("send_status",Context.MODE_PRIVATE);
        if(prefs.getBoolean("pending",false)) {
            prefs.edit().putBoolean("pending",false).putString("result","Sending was interrupted. Check Gmail Sent before trying again; the email may already have been accepted.").apply();
        }
    }
    boolean sending() { return sending; }
    void observe(Runnable observer) { this.observer=observer; if(observer!=null)observer.run(); }
    String takeResult() {
        String result=prefs.getString("result","");prefs.edit().remove("result").apply();return result;
    }
    void send(String from,String to,Password password,String day,String csv) {
        if(sending)return;
        if(!prefs.edit().putBoolean("pending",true).remove("result").commit()) {
            prefs.edit().putString("result","Could not save sending status. Nothing was sent. Please try again.").apply();
            if(observer!=null)observer.run();return;
        }
        sending=true;if(observer!=null)observer.run();
        // Capture an immutable CSV snapshot; edits and date changes cannot alter this send.
        executor.execute(()->{
            String result;
            try {
                String secret=password.read();
                delivery.send(from,to,secret,day,csv);
                result="Gmail accepted the log for "+day+" to "+to+".";
            } catch(javax.mail.AuthenticationFailedException e) {
                result="Gmail rejected the login. Check your Gmail address and app password under Email. Use a Google app password, not your normal password.";
            } catch(java.security.GeneralSecurityException e) {
                result="Could not unlock the saved app password. Enter it again under Email.";
            } catch(Exception e) {
                result="Sending could not be confirmed. Check your connection and Gmail Sent before retrying, to avoid duplicates. Your log is still saved.";
            }
            final String outcome=result;
            main.post(()->{
                sending=false;prefs.edit().putBoolean("pending",false).putString("result",outcome).apply();
                if(observer!=null)observer.run();
            });
        });
    }
}
