package com.davidparry.doc;

import java.util.Map;

public record BookPrompt(Map<BookKey, Section> bookAggregation) {
}
