package com.davidparry.doc;

import java.util.List;

public record InsertDocument(String chapterGuid, String chapterTitle, String paragraphGuid, String paragraphTitle,
                             Section section, List<Double> vectors, long tokens) {

    public InsertDocument(String chapterGuid, String chapterTitle, String paragraphGuid, String paragraphTitle,
                          Section section) {
        this(chapterGuid, chapterTitle, paragraphGuid, paragraphTitle, section, null, 0);
    }

    public InsertDocument(InsertDocument document, List<Double> vectors, long tokens) {
        this(document.chapterGuid(), document.chapterTitle(), document.paragraphGuid(), document.paragraphTitle(),
                document.section(), vectors, tokens);
    }

}
