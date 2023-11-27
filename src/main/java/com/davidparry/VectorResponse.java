package com.davidparry;

import com.theokanning.openai.Usage;
import com.theokanning.openai.embedding.Embedding;

import java.util.List;

public record VectorResponse(List<Embedding> embeddings, Usage usage, int status, String question) {
}
