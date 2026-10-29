package com.codereviewer.ai;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import com.codereviewer.model.codeIssue;
import com.codereviewer.utils.PromptBuilder;

public class AIReviewer {

    private static final String OLLAMA_URL =
            "http://localhost:11434/api/generate";

    private static final String OLLAMA_STATUS_URL =
            "http://localhost:11434/api/tags";

    // This must exactly match a model shown by: ollama list
    private static final String MODEL =
            "qwen2.5-coder:1.5b";

    // Local models can take several minutes on laptops with limited RAM.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration STATUS_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration GENERATION_TIMEOUT = Duration.ofMinutes(10);

    private final HttpClient httpClient;

    public AIReviewer() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    /**
     * Returns true when the Ollama API is reachable.
     * This does not guarantee that MODEL has been downloaded.
     */
    public boolean isConfigured() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_STATUS_URL))
                .timeout(STATUS_TIMEOUT)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
            return response.statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Generates an AI review using the existing PromptBuilder.
     *
     * @param sourceCode Java source code being reviewed
     * @param staticIssues findings from the static analyzers
     * @return AI-generated review text
     */
    public String review(String sourceCode, List<codeIssue> staticIssues) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new IllegalArgumentException("Source code cannot be empty.");
        }

        String prompt = PromptBuilder.build(sourceCode, staticIssues);
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalStateException("PromptBuilder produced an empty prompt.");
        }

        return sendToOllama(prompt);
    }

    /**
     * Sends a prompt to the local Ollama API.
     */
    private String sendToOllama(String prompt) {
        String jsonBody = "{"
                + "\"model\":\"" + escapeJson(MODEL) + "\","
                + "\"prompt\":\"" + escapeJson(prompt) + "\","
                + "\"stream\":false,"
                + "\"keep_alive\":\"5m\""
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_URL))
                .timeout(GENERATION_TIMEOUT)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String detail = response.body() == null ? "" : response.body();
                throw new RuntimeException(
                        "Ollama returned HTTP " + response.statusCode()
                                + ". Response: " + detail
                                + ". Confirm the model name using `ollama list`."
                );
            }

            String answer = extractJsonString(response.body(), "response");
            if (answer == null || answer.isBlank()) {
                String detail = response.body() == null ? "" : response.body();
                throw new RuntimeException(
                        "Ollama responded, but no non-empty 'response' field "
                                + "was found. Response: " + detail
                );
            }

            return answer.trim();

        } catch (HttpTimeoutException e) {
            throw new RuntimeException(
                    "Ollama did not finish generating a response within "
                            + GENERATION_TIMEOUT.toMinutes()
                            + " minutes. Check Ollama and try a smaller input. "
                            + "On a laptop with limited RAM, the model may be slow.",
                    e
            );
        } catch (ConnectException e) {
            throw new RuntimeException(
                    "Cannot connect to Ollama at " + OLLAMA_URL
                            + ". Start the Ollama application and verify that "
                            + "http://localhost:11434/api/tags is reachable.",
                    e
            );
        } catch (IOException e) {
            throw new RuntimeException(
                    "I/O error while communicating with Ollama at " + OLLAMA_URL
                            + ": " + e.getMessage(),
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(
                    "The request to Ollama was interrupted.", e
            );
        }
    }

    /**
     * Escapes a Java string for use as a JSON string value.
     */
    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }

        StringBuilder escaped = new StringBuilder(text.length() + 32);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
            }
        }
        return escaped.toString();
    }

    /**
     * Extracts a JSON string field and decodes its escape sequences.
     * This avoids relying on the response field appearing without whitespace.
     */
    private String extractJsonString(String json, String fieldName) {
        if (json == null || fieldName == null) {
            return null;
        }

        String key = "\"" + fieldName + "\"";
        int keyStart = json.indexOf(key);
        if (keyStart < 0) {
            return null;
        }

        int colon = json.indexOf(':', keyStart + key.length());
        if (colon < 0) {
            return null;
        }

        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }

        if (i >= json.length() || json.charAt(i) != '"') {
            return null;
        }
        i++;

        StringBuilder value = new StringBuilder();
        boolean escaped = false;

        while (i < json.length()) {
            char c = json.charAt(i++);

            if (escaped) {
                switch (c) {
                    case '"':
                        value.append('"');
                        break;
                    case '\\':
                        value.append('\\');
                        break;
                    case '/':
                        value.append('/');
                        break;
                    case 'b':
                        value.append('\b');
                        break;
                    case 'f':
                        value.append('\f');
                        break;
                    case 'n':
                        value.append('\n');
                        break;
                    case 'r':
                        value.append('\r');
                        break;
                    case 't':
                        value.append('\t');
                        break;
                    case 'u':
                        if (i + 4 > json.length()) {
                            return null;
                        }
                        try {
                            value.append((char) Integer.parseInt(
                                    json.substring(i, i + 4), 16));
                        } catch (NumberFormatException ex) {
                            return null;
                        }
                        i += 4;
                        break;
                    default:
                        return null;
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return value.toString();
            } else {
                value.append(c);
            }
        }

        return null;
    }

    /**
     * Standalone smoke test for Ollama + Qwen.
     */
    public static void main(String[] args) {
        AIReviewer reviewer = new AIReviewer();

        if (!reviewer.isConfigured()) {
            System.err.println(
                    "Ollama is not reachable. Start Ollama and test "
                            + "http://localhost:11434/api/tags."
            );
            return;
        }

        String sourceCode = """
                public class TestCode {
                    public void test() {
                        int a = 10;
                        int b = 0;
                        int result = a / b;
                        System.out.println(result);
                    }
                }
                """;

        try {
            String review = reviewer.review(sourceCode, List.of());

            System.out.println("========================================");
            System.out.println("             AI CODE REVIEW");
            System.out.println("========================================");
            System.out.println(review);

        } catch (RuntimeException e) {
            System.err.println("AI Reviewer Error: " + e.getMessage());
        }
    }
}
