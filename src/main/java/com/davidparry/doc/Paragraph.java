package com.davidparry.doc;

import java.util.List;

public record Paragraph(String guid, String heading, String title, List<Section> sections) {
}
