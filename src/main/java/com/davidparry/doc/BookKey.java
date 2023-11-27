package com.davidparry.doc;

import java.util.Objects;

public record BookKey(String chapterGuid, String chapterTitle, String paragraphGuid, String paragraphTitle) {
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BookKey bookKey)) {
            return false;
        }
        return Objects.equals(chapterGuid, bookKey.chapterGuid) && Objects.equals(paragraphGuid,
                bookKey.paragraphGuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chapterGuid, paragraphGuid);
    }
}
