package plun1331.skykings_playtime.client;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class PlaytimeAPI {
    public static HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static String getBaseUrl() {
        return "http://localhost:8787";
    }

    public static boolean validateAPIKey(String key) {
        try {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder()
                        .uri(java.net.URI.create(getBaseUrl() + "/@me"))
                        .header("Authorization", key)
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static CompletableFuture<Boolean> publishPlaytimeRecords(
            String key, LinkedList<PlaytimeRecord> records) {
        JSONArray payload = new JSONArray();
        for (PlaytimeRecord record : records) {
            JSONObject entry = new JSONObject();
            entry.put("start", record.start().toInstant().getEpochSecond());
            entry.put("end", record.end().toInstant().getEpochSecond());
            entry.put("type", record.type());
            entry.put("map", record.map());
            payload.add(entry);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(getBaseUrl() + "/@me/playtime"))
                .header("Authorization", key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toJSONString()))
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(response -> response.statusCode() >= 200 && response.statusCode() < 300);
    }
}
