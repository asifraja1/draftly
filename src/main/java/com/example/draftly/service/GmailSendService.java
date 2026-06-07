package com.example.draftly.service;

import com.example.draftly.config.GmailConfig;
import com.example.draftly.entity.GmailAccount;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Properties;

@Service
@RequiredArgsConstructor
public class GmailSendService {

    private final GmailConfig gmailConfig;

    public void sendReply(GmailAccount gmailAccount, String toEmail, String threadId, String body) throws Exception {
        Gmail gmail = gmailConfig.getGmailService(gmailAccount);

        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);
        email.setFrom(new InternetAddress(gmailAccount.getGmailAddress()));
        email.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(toEmail));
        email.setSubject("Re: (Draftly reply)");
        email.setText(body);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        email.writeTo(buffer);
        String encodedEmail = Base64.getUrlEncoder().encodeToString(buffer.toByteArray());

        Message message = new Message();
        message.setRaw(encodedEmail);
        message.setThreadId(threadId);

        gmail.users().messages().send("me", message).execute();

        System.out.println("[GMAIL] Reply sent to " + toEmail + " in thread " + threadId);
    }
}
