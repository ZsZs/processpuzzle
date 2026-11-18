package com.processpuzzle.chat.conversation.adapters.inbound;

import com.processpuzzle.chat.api.ChatPrivacyApi;
import com.processpuzzle.chat.conversation.usecases.inbound.EraseChatUser;
import com.processpuzzle.chat.model.ChatErasureResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the generated {@code ChatPrivacyApi}: erasing a user's chat data. */
@RestController
public class ChatPrivacyEndpoint implements ChatPrivacyApi {

    private final EraseChatUser erase;

    public ChatPrivacyEndpoint(EraseChatUser erase) {
        this.erase = erase;
    }

    @Override
    public ResponseEntity<ChatErasureResult> eraseChatUser(String orgKey, String userSub) {
        EraseChatUser.Result result = erase.erase(orgKey, userSub);
        return ResponseEntity.ok(new ChatErasureResult().messages(result.messages()).conversations(result.conversations()).readMarkers(result.readMarkers()));
    }
}
