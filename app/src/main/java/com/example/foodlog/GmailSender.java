package com.example.foodlog;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Properties;
import javax.activation.DataHandler;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

final class GmailSender {
    static Properties properties() {
        Properties p=new Properties();
        p.setProperty("mail.smtp.host","smtp.gmail.com");p.setProperty("mail.smtp.port","465");
        p.setProperty("mail.smtp.auth","true");p.setProperty("mail.smtp.ssl.enable","true");
        p.setProperty("mail.smtp.ssl.checkserveridentity","true");
        p.setProperty("mail.smtp.connectiontimeout","15000");p.setProperty("mail.smtp.timeout","30000");
        p.setProperty("mail.smtp.writetimeout","30000");p.setProperty("mail.smtp.quitwait","false");
        return p;
    }
    static MimeMessage message(Session session,String from,String to,String day,String csv) throws Exception {
        MimeMessage message=new MimeMessage(session);
        message.setFrom(new InternetAddress(from,true));message.setRecipient(Message.RecipientType.TO,new InternetAddress(to,true));
        message.setSubject("Food log — "+day,"UTF-8");message.setSentDate(new Date());
        MimeBodyPart body=new MimeBodyPart();body.setText("My food log for "+day+" is attached. All weights are in grams.","UTF-8");
        MimeBodyPart attachment=new MimeBodyPart();
        attachment.setDataHandler(new DataHandler(new ByteArrayDataSource(csv.getBytes(StandardCharsets.UTF_8),"text/csv; charset=UTF-8")));
        attachment.setFileName("food-log-"+day+".csv");attachment.setDisposition(javax.mail.Part.ATTACHMENT);
        MimeMultipart parts=new MimeMultipart();parts.addBodyPart(body);parts.addBodyPart(attachment);
        message.setContent(parts);message.saveChanges();return message;
    }
    static void send(String from,String to,String password,String day,String csv) throws Exception {
        Session session=Session.getInstance(properties());session.setDebug(false);
        MimeMessage message=message(session,from,to,day,csv);
        Transport transport=session.getTransport("smtp");
        try {
            transport.connect("smtp.gmail.com",465,from,password);
            transport.sendMessage(message,message.getAllRecipients());
        } finally {
            // A failed QUIT must not turn an accepted message into a reported failure.
            try { transport.close(); } catch(javax.mail.MessagingException ignored) { }
        }
    }
}
