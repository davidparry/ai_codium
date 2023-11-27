package com.davidparry;

import com.davidparry.doc.Book;
import com.davidparry.doc.InsertDocument;
import com.davidparry.doc.ReadParseBook;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.theokanning.openai.client.OpenAiApi;
import com.theokanning.openai.embedding.Embedding;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.embedding.EmbeddingResult;
import com.theokanning.openai.service.OpenAiService;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Retrofit;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.theokanning.openai.service.OpenAiService.defaultClient;
import static com.theokanning.openai.service.OpenAiService.defaultObjectMapper;
import static com.theokanning.openai.service.OpenAiService.defaultRetrofit;

public class VectorEmbedder {
    public final String MONGO_URI;
    private static final Logger logger = LoggerFactory.getLogger(AIRunner.class);

    public static final String USER_SESSION_ID = "ALFRED-ASSISTANT";
    private final OpenAiService OPENAI_SERVICE;
    public VectorEmbedder (String key, String mongoUri, String orgId) {
            MONGO_URI = mongoUri;
            OPENAI_SERVICE = aiService(key, orgId);
    }

    public void doVectors() {
        Optional<Book> book = ReadParseBook.parse();
        book.ifPresent(b -> {
            List<InsertDocument> insertDocuments = flattenBookToDocuments(b);
            logger.debug("doc {}", insertDocuments);
            List<Optional<InsertDocument>> documentsToInsert =
                    insertDocuments.stream().map(document -> loadVectorsForInsertion(document))
                                   .collect(Collectors.toList());
            Gson gson = new Gson();
            final List<Document> inserts = new ArrayList<>();
            documentsToInsert.forEach(insertDocument -> {
                insertDocument.ifPresent(d -> {
                    Map<String, Object> map = gson.fromJson(gson.toJson(d), Map.class);
                    inserts.add(new Document(map));
                });

            });
            logger.debug("Docs to insert {}", documentsToInsert);
            try (MongoClient mongoClient = new MongoClient(new MongoClientURI(MONGO_URI))) {
                MongoDatabase database = mongoClient.getDatabase("sandbox_vectors");
                MongoCollection<Document> collection = database.getCollection("vector_records");
                collection.insertMany(inserts);
            }
        });
    }
    public VectorResponse getEmbeddings(String text) {
        if (text != null && text.length() > 2) {
            try {
                EmbeddingRequest embeddingRequest =
                        EmbeddingRequest.builder().model("text-embedding-ada-002").input(List.of(text))
                                        .user(USER_SESSION_ID).build();
                EmbeddingResult result = OPENAI_SERVICE.createEmbeddings(embeddingRequest);
                List<Embedding> embeddings = result.getData();
                if (embeddings != null && embeddings.size() > 0) {
                    return new VectorResponse(embeddings, result.getUsage(), 0, text);
                } else {
                    return new VectorResponse(null, null, -2, text);
                }
            } catch (Exception er) {
                logger.error("Error getting embeddings for this String:'{}'", text, er);
            }
        }
        return new VectorResponse(null, null, -1, null);
    }
    public Optional<InsertDocument> loadVectorsForInsertion(InsertDocument document) {
        VectorResponse response = getEmbeddings(document.section().content());
        if (response.status() == 0) {
            if (response.embeddings() != null && response.embeddings().get(0) != null) {
                List<Double> toEmbed = response.embeddings().get(0).getEmbedding();
                if (response.embeddings().size() != 1) {
                    logger.error("embeddings are more than one and why size of embeddings is {}",
                            response.embeddings().size());
                }
                return Optional.of(new InsertDocument(document, toEmbed, response.usage().getTotalTokens()));
            }
        }
        return Optional.empty();
    }
    public static List<InsertDocument> flattenBookToDocuments(Book book) {
        return book.chapters().stream().flatMap(chapter -> chapter.paragraphs().stream().flatMap(
                           paragraph -> paragraph.sections().stream()
                                                 .map(section -> new InsertDocument(chapter.guid(), chapter.title(),
                                                         paragraph.guid(), paragraph.title(), section))))
                   .collect(Collectors.toList());
    }


    public OpenAiService aiService(String clientToken, String orgId) {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        ObjectMapper mapper = defaultObjectMapper();
        OkHttpClient client = defaultClient(clientToken, Duration.ofSeconds(60)).newBuilder()
                                                                                //.addInterceptor(loggingInterceptor)
                                                                                .addInterceptor(new Interceptor() {
                                                                                    @Override
                                                                                    public Response intercept(
                                                                                            Chain chain)
                                                                                            throws IOException {
                                                                                        Request originalRequest =
                                                                                                chain.request();
                                                                                        Request modifiedRequest =
                                                                                                originalRequest.newBuilder()
                                                                                                               .header("OpenAI-Organization",
                                                                                                                       orgId) // Set your header here
                                                                                                               .build();
                                                                                        return chain.proceed(
                                                                                                modifiedRequest);
                                                                                    }
                                                                                }).eventListener(
                        new HttpEventListener()).build();
        Retrofit retrofit = defaultRetrofit(client, mapper);

        OpenAiApi api = retrofit.create(OpenAiApi.class);
        OpenAiService service = new OpenAiService(api);
        return service;
    }
}
