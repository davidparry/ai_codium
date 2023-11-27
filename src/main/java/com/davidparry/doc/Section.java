package com.davidparry.doc;

public record Section(String guid, String heading, String title, String content, int score) {
    public Section(String guid, String heading, String title, String content) {
        this(guid,heading,title,content,-1);
    }
}
