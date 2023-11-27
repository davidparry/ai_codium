package com.davidparry;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public record JsonChatResponse(@SerializedName("answer_found") String answerFound,
                               @SerializedName("heading_number") String headingNumber,
                               @SerializedName("ai_response") String aiResponse) implements Serializable {

}
