package com.davidparry.doc;

import java.util.List;

public record Book(String guid, String version,
         String name,
         String bookType,
         String createDateTime, List<Chapter> chapters) {
}
