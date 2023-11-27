package com.davidparry.doc;

import com.davidparry.AIRunner;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class ReadParseBook {
    private static final Logger logger = LoggerFactory.getLogger(AIRunner.class);


    public static Optional<Book> parse() {
        try (InputStreamReader reader = new InputStreamReader(
                ReadParseBook.class.getClassLoader().getResourceAsStream("book.json"),
                StandardCharsets.UTF_8)) {
        Gson gson = new Gson();
            Book book = gson.fromJson(reader, Book.class);
            logger.debug("Book is {}", book);
            return Optional.of(book);
        } catch (Exception e) {
            logger.error("failed to read book file ", e);
        }
        return Optional.empty();
    }

}
