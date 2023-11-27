package com.davidparry;

import java.util.ArrayList;
import java.util.List;

public record ChatResponse(long tokensUsed, List<ParsedChatResponse> responses, int status, String statusMessage) {

    public ChatResponse(int status, String statusMessage) {
        this(0, new ArrayList<>(), status, statusMessage);
    }


}
