package com.example.foodlog;

import android.net.Uri;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.widget.Button;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class SyncActivityTest {
    @Test public void loginPageAtPhoneUrlDoesNotStartSyncAndCanNavigateToSignIn() {
        try(var controller=Robolectric.buildActivity(SyncActivity.class).setup()) {
            SyncActivity activity=controller.get();
            WebView web=ReflectionHelpers.getField(activity,"web");
            var client=shadowOf(web).getWebViewClient();
            WebResourceRequest request=new WebResourceRequest(){
                public Uri getUrl(){return Uri.parse(SyncActivity.SITE+"/phone");}
                public boolean isForMainFrame(){return true;}
                public boolean isRedirect(){return false;}
                public boolean hasGesture(){return false;}
                public String getMethod(){return "GET";}
                public Map<String,String> getRequestHeaders(){return Map.of();}
            };
            client.onPageStarted(web,web.getUrl(),null);
            client.onReceivedHttpError(web,request,new WebResourceResponse("text/html","UTF-8",401,"Unauthorized",Map.of(),null));
            client.onPageFinished(web,web.getUrl());
            assertNull(shadowOf(web).getLastEvaluatedJavascript());
            assertNull(ShadowAlertDialog.getLatestAlertDialog());
            assertFalse(activity.isFinishing());
            Button signIn=ReflectionHelpers.getField(activity,"signIn");
            signIn.performClick();
            assertEquals(SyncActivity.SITE+"/signin-with-chatgpt?return_to=%2Fphone",shadowOf(web).getLastLoadedUrl());
            // Returning from sign-in starts the authenticated API check.
            web.loadUrl(SyncActivity.SITE+"/phone");
            client.onPageStarted(web,web.getUrl(),null);
            client.onPageFinished(web,web.getUrl());
            assertNotNull(shadowOf(web).getLastEvaluatedJavascript());
        }
    }
    @Test public void unauthenticatedApiResponsesKeepLoginOpenAndLeaveLogsUntouched() throws Exception {
        try(var controller=Robolectric.buildActivity(SyncActivity.class).setup()) {
            SyncActivity activity=controller.get();
            LogStore store=ReflectionHelpers.getField(activity,"store");
            store.add("Milk");store.save("2026-11-17",store.foods("2026-11-17",true).get(0),"250");
            var before=store.snapshot();
            AtomicBoolean merged=new AtomicBoolean();
            for(int code:new int[]{401,403,200})activity.handleResponse(code,"<!DOCTYPE html><h1>Sign in required</h1>",r->merged.set(true));
            assertFalse(merged.get());assertFalse(activity.isFinishing());
            assertNull(ShadowAlertDialog.getLatestAlertDialog());
            assertTrue(SyncMerge.equal(before,store.snapshot()));
            Button signIn=ReflectionHelpers.getField(activity,"signIn");assertEquals(View.VISIBLE,signIn.getVisibility());
        }
    }
    @Test public void authenticatedJsonProceedsToSync() {
        try(var controller=Robolectric.buildActivity(SyncActivity.class).setup()) {
            SyncActivity activity=controller.get();AtomicBoolean merged=new AtomicBoolean();
            activity.handleResponse(200,"{\"owner\":\"user\",\"revision\":0,\"snapshot\":{\"foods\":{},\"recipes\":{},\"entries\":{}}}",r->merged.set(true));
            assertTrue(merged.get());assertNull(ShadowAlertDialog.getLatestAlertDialog());
            Button signIn=ReflectionHelpers.getField(activity,"signIn");assertEquals(View.GONE,signIn.getVisibility());
        }
    }
    @Test public void serverFailureStillShowsAnError() {
        try(var controller=Robolectric.buildActivity(SyncActivity.class).setup()) {
            controller.get().handleResponse(500,"failure",r->fail("Must not merge"));
            assertNotNull(ShadowAlertDialog.getLatestAlertDialog());
        }
    }
    @Test public void malformedSuccessCannotStartMerge() {
        try(var controller=Robolectric.buildActivity(SyncActivity.class).setup()) {
            controller.get().handleResponse(200,"{}",r->fail("Must not merge"));
            assertNotNull(ShadowAlertDialog.getLatestAlertDialog());
        }
    }
}
