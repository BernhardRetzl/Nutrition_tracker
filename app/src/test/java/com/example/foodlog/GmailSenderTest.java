package com.example.foodlog;

import org.junit.Test;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class GmailSenderTest {
    @Test public void buildsEmailWithUtf8CsvAttachmentAndCorrectRecipient() throws Exception {
        Session session=Session.getInstance(GmailSender.properties());
        String csv="Date,Recipe,Food,Grams\r\n\"2026-11-16\",\"Smoothie\",\"Müsli\",250\r\n";
        MimeMessage original=GmailSender.message(session,"sender@gmail.com","recipient@example.org","2026-11-16",csv);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();original.writeTo(bytes);
        MimeMessage parsed=new MimeMessage(session,new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals("sender@gmail.com",parsed.getFrom()[0].toString());
        assertEquals("recipient@example.org",parsed.getAllRecipients()[0].toString());
        assertEquals("Food log — 2026-11-16",parsed.getSubject());
        MimeMultipart parts=(MimeMultipart)parsed.getContent();assertEquals(2,parts.getCount());
        var attachment=parts.getBodyPart(1);
        assertEquals("food-log-2026-11-16.csv",attachment.getFileName());
        assertEquals("attachment",attachment.getDisposition());
        assertEquals(csv,new String(attachment.getInputStream().readAllBytes(),StandardCharsets.UTF_8));
    }
}
