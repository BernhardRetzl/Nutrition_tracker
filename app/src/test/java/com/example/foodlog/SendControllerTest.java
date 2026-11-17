package com.example.foodlog;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class SendControllerTest {
    private final List<Runnable> queue=new ArrayList<>();
    @Before public void reset() {RuntimeEnvironment.getApplication().getSharedPreferences("send_status",0).edit().clear().commit();}
    private void finish() {queue.remove(0).run();shadowOf(android.os.Looper.getMainLooper()).idle();}
    @Test public void sendsOnceForRapidTapsAndKeepsSnapshotAcrossObserverChanges() {
        List<String> delivered=new ArrayList<>();
        SendController sender=new SendController(RuntimeEnvironment.getApplication(),queue::add,(from,to,password,date,csv)->delivered.add(date+":"+csv));
        sender.send("a@gmail.com","b@example.com",()->"fake-secret","2026-11-16","original CSV");
        sender.send("a@gmail.com","b@example.com",()->"fake-secret","2026-11-17","changed CSV");
        assertTrue(sender.sending());assertEquals(1,queue.size());
        sender.observe(null);finish();
        assertEquals(List.of("2026-11-16:original CSV"),delivered);
        assertFalse(sender.sending());assertTrue(sender.takeResult().contains("Gmail accepted"));
        assertEquals("",sender.takeResult());
        assertFalse(RuntimeEnvironment.getApplication().getSharedPreferences("send_status",0).getBoolean("pending",true));
    }
    @Test public void authFailureIsReportedWithoutLeakingSecretsOrRetrying() {
        SendController sender=new SendController(RuntimeEnvironment.getApplication(),queue::add,(from,to,password,date,csv)->{throw new javax.mail.AuthenticationFailedException("private-server-detail");});
        sender.send("a@gmail.com","b@example.com",()->"fake-secret","2026-11-16","csv");finish();
        String result=sender.takeResult();assertTrue(result.contains("rejected the login"));
        assertFalse(result.contains("private-server-detail"));assertFalse(sender.sending());assertTrue(queue.isEmpty());
    }
    @Test public void interruptedAttemptNeverAutomaticallyResends() {
        var context=RuntimeEnvironment.getApplication();context.getSharedPreferences("send_status",0).edit().putBoolean("pending",true).commit();
        SendController sender=new SendController(context,queue::add,(from,to,password,date,csv)->fail("Must not send"));
        assertFalse(sender.sending());assertTrue(queue.isEmpty());
        assertTrue(sender.takeResult().contains("Check Gmail Sent"));
    }
    @Test public void connectionFailureReportsUncertainOutcomeAndKeepsRetryManual() {
        SendController sender=new SendController(RuntimeEnvironment.getApplication(),queue::add,(from,to,password,date,csv)->{throw new java.net.SocketTimeoutException();});
        sender.send("a@gmail.com","b@example.com",()->"fake-secret","2026-11-16","csv");finish();
        assertTrue(sender.takeResult().contains("could not be confirmed"));assertFalse(sender.sending());assertTrue(queue.isEmpty());
    }
}
