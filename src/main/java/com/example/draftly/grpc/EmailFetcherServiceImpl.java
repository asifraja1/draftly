package com.example.draftly.grpc;

import com.example.draftly.entity.EmailMessage;
import com.example.draftly.repository.EmailMessageRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * gRPC service implementation. Serves the last N emails of a thread to the
 * Python agent so it can build richer drafts with conversation context.
 *
 * Backed by the local email_message table (synced from Gmail), so it needs no
 * extra Gmail API calls.
 */
@Component
@RequiredArgsConstructor
public class EmailFetcherServiceImpl extends EmailFetcherServiceGrpc.EmailFetcherServiceImplBase {

    private final EmailMessageRepository emailMessageRepository;

    @Override
    public void getThreadEmails(ThreadRequest request,
                                StreamObserver<ThreadEmailsResponse> responseObserver) {
        ThreadEmailsResponse.Builder resp = ThreadEmailsResponse.newBuilder();
        try {
            String threadId = request.getThreadId();
            String userId   = request.getUserId();   // the gmail account email
            int limit       = request.getLimit() > 0 ? request.getLimit() : 5;

            List<EmailMessage> emails;
            if (userId != null && !userId.isBlank()) {
                emails = emailMessageRepository
                        .findByThreadIdAndGmailAccount_GmailAddressOrderByReceivedAtDesc(threadId, userId);
            } else {
                emails = emailMessageRepository.findByThreadIdOrderByReceivedAtDesc(threadId);
            }

            emails.stream()
                    .limit(limit)
                    .forEach(e -> resp.addEmails(Email.newBuilder()
                            .setId(nullSafe(e.getMessageId()))
                            .setFrom(nullSafe(e.getSender()))
                            .setSubject(nullSafe(e.getSubject()))
                            .setBody(nullSafe(e.getBody()))
                            .setTimestamp(e.getReceivedAt() != null ? e.getReceivedAt().toString() : "")
                            .build()));

            System.out.println("[gRPC] GetThreadEmails thread=" + threadId
                    + " user=" + userId + " → returned " + Math.min(emails.size(), limit) + " email(s)");

        } catch (Exception ex) {
            resp.setError("server error: " + ex.getMessage());
            System.err.println("[gRPC] GetThreadEmails error: " + ex.getMessage());
        }
        responseObserver.onNext(resp.build());
        responseObserver.onCompleted();
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
