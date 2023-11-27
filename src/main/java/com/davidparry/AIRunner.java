package com.davidparry;

import com.davidparry.doc.BookKey;
import com.davidparry.doc.Section;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.theokanning.openai.client.OpenAiApi;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.theokanning.openai.service.OpenAiService.defaultClient;
import static com.theokanning.openai.service.OpenAiService.defaultObjectMapper;
import static com.theokanning.openai.service.OpenAiService.defaultRetrofit;

// Press Shift twice to open the Search Everywhere dialog and type `show whitespaces`,
// then press Enter. You can now see whitespace characters in your code.
public class AIRunner implements SystemMessages {
    public static final String USER_SESSION_ID = "ALFRED-ASSISTANT";
    private static final Logger logger = LoggerFactory.getLogger(AIRunner.class);

    public final String MONGO_URI;
    private final OpenAiService OPENAI_SERVICE;

    public AIRunner(String key, String mongoUri, String orgId) {
        MONGO_URI = mongoUri;
        OPENAI_SERVICE = aiService(key, orgId);
    }

    public ParsedChatResponse search(String question) {

        VectorResponse vectorResponse = getEmbeddings(question);
        List<Double> searchVector = vectorResponse.embeddings().stream().flatMap(
                                                          embedding -> embedding.getEmbedding().stream()) // Convert each List<Double> to a Stream<Double>
                                                  .collect(Collectors.toList());
        Map<BookKey, List<Section>> vectorResults = vectorSearch(searchVector, 20);

        logger.debug("Results {}", vectorResults);


        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new ChatMessage("system", PRIMER));
        createPreambleChatMessages(vectorResults, chatMessages);
        chatMessages.add(new ChatMessage("user", question));
        chatMessages.add(new ChatMessage("system", SYSTEM_INSTRUCTION));
        logger.debug("chat messages {}", chatMessages);

        ChatResponse chatResponse = converse(chatMessages);

        logger.debug("{}", chatResponse.responses().get(0));
        return chatResponse.responses().get(0);

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

    public List<ChatMessage> createPreambleChatMessages(Map<BookKey, List<Section>> vectorResults,
                                                        List<ChatMessage> chatMessages) {
        for (BookKey key : vectorResults.keySet()) {
            List<Section> sections = vectorResults.get(key);
            String message = String.format(PREFACE_STRING, key.chapterTitle(), key.paragraphTitle());
            for (Section section : sections) {
                message = message + String.format(SECTION_STRING, section.heading(), section.title(),
                        section.content());
            }
            chatMessages.add(new ChatMessage("system", message));
        }
        return chatMessages;
    }


    public Map<BookKey, List<Section>> vectorSearch(List<Double> questionEmbeddings, int limit) {
        try (MongoClient mongoClient = new MongoClient(new MongoClientURI(MONGO_URI))) {
            MongoDatabase database = mongoClient.getDatabase("sandbox_vectors");
            MongoCollection<Document> collection = database.getCollection("vector_records");
            Document vectorOptions = new Document();
            vectorOptions.append("queryVector", questionEmbeddings);
            vectorOptions.append("path", "vectors");
            vectorOptions.append("numCandidates", 500);
            vectorOptions.append("limit", limit);
            vectorOptions.append("index", "vector_index");
            Document vectorQuery = new Document("$vectorSearch", vectorOptions);
            AggregateIterable<Document> result = collection.aggregate(List.of(vectorQuery));
            return loadFromDocument(result);
        } catch (Exception er) {
            logger.error("Failed to search vector database embeddings search embeddings where {}",
                    questionEmbeddings, er);
        }
        return null;
    }

    public Map<BookKey, List<Section>> loadFromDocument(AggregateIterable<Document> result) {
        Map<BookKey, List<Section>> chaptersParagraph = new HashMap<>();
        int score = 500;
        for (Document doc : result) {
            score--;
            BookKey key = new BookKey(doc.getString("chapterGuid"), doc.getString("chapterTitle"),
                    doc.getString("paragraphGuid"), doc.getString("paragraphTitle"));

            List<Section> sections = chaptersParagraph.get(key);
            if (sections == null) {
                sections = new ArrayList<>();
            }
            Document docSection = doc.get("section", Document.class);
            if (docSection != null) {
                sections.add(new Section(docSection.getString("guid"), docSection.getString("heading"),
                        docSection.getString("title"), docSection.getString("content"), score));
            }
            chaptersParagraph.put(key, sections);
        }
        return chaptersParagraph;
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


    public ChatResponse converse(List<ChatMessage> messages) {
        try {
            ChatCompletionRequest chatRequest =
                    ChatCompletionRequest.builder().model("gpt-4-1106-preview").messages(messages).temperature(0.0)
                                         .topP(0.0).frequencyPenalty(0.5).build();
            ChatCompletionResult result = OPENAI_SERVICE.createChatCompletion(chatRequest);

            logger.debug("result of chat {}", result);
            List<ParsedChatResponse> responses = new ArrayList<>();
            int status = 0;
            String finishReason = "stop";
            for (ChatCompletionChoice choice : result.getChoices()) {
                if ("stop".equalsIgnoreCase(choice.getFinishReason())) {
                    JsonChatResponse jsonChatResponse = null;
                    try {
                        Gson gson = new Gson();
                        jsonChatResponse = gson.fromJson(choice.getMessage().getContent(), JsonChatResponse.class);
                    } catch (Error e) {
                        logger.error(
                                "Chat bot from openAI did not return valid json as instructed what it returned '{}'",
                                choice.getMessage().getContent());
                        status = 3;
                    }
                    responses.add(new ParsedChatResponse(choice.getMessage().getContent(), jsonChatResponse));
                } else {
                    logger.debug("Choice not stopped {}", choice);
                    status = 2;
                    finishReason = choice.getFinishReason();
                }
            }
            return new ChatResponse(result.getUsage().getTotalTokens(), responses, status,
                    "Reason to stop conversation was:" + finishReason);

        } catch (Exception e) {
            logger.error("Error conversing with the model chatMessages where {}", messages, e);
            return new ChatResponse(1, e.getMessage());
        }

    }
}

