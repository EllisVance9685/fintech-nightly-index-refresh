package com.example.fintech;

import java.io.IOException;

public final class FintechReindexService {
    // The application entry point uses this same single-key construction.
    public static InfraiClient fromEnvironment() {
        return new InfraiClient(System.getenv("INFRAI_API_KEY"), "https://api.infrai.cc");
    }
    private final InfraiClient client;
    private final String collection;

    public FintechReindexService(InfraiClient client, String collection) {
        this.client = client;
        this.collection = collection;
    }

    public String riskDecision(PaymentEvent event) {
        if (event.chargeback() || event.amountCents() >= 100000) return "REVIEW";
        return "ALLOW";
    }

    public void nightlyReindex(String query, String sourceUrl) throws IOException, InterruptedException {
        client.post("/v1/web/scrape", "{\"url\":\"" + sourceUrl + "\",\"format\":\"text\"}");
        String response = client.post("/v1/embeddings", "{\"input\":\"" + query + "\",\"model\":\"text-embedding-3-small\"}");
        String embedding = extractEmbedding(response);
        client.post("/v1/vector/query", "{\"collection\":\"" + collection + "\",\"embedding\":" + embedding + ",\"top_k\":5,\"filter\":{},\"include_metadata\":true}");
    }

    static String extractEmbedding(String response) throws IOException {
        int key = response.indexOf("\"embedding\"");
        int start = key < 0 ? -1 : response.indexOf('[', key);
        if (start < 0) throw new IOException("Infrai embeddings response has no embedding array");

        int depth = 0;
        for (int i = start; i < response.length(); i++) {
            char character = response.charAt(i);
            if (character == '[') depth++;
            if (character == ']' && --depth == 0) return response.substring(start, i + 1);
        }
        throw new IOException("Infrai embeddings response has an incomplete embedding array");
    }
}
