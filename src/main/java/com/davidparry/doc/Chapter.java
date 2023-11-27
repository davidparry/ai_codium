package com.davidparry.doc;

import java.util.List;

public record Chapter(String guid, String heading, String title, List<Paragraph> paragraphs) {
}
