package api;


import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import com.google.gson.Gson;

public class CrptApi {
	

    // ─── Поля для ограничения количества запросов ─────────────────────
    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final Deque<Long> requestTimestamps = new ArrayDeque<>();
    private final Object lock = new Object();

    // ─── HTTP клиент и сериализация JSON ─────────────────────────────
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    // ─── URL API и токен авторизации ─────────────────────────────────
    private final String apiUrl = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final String token; // Токен передается через конструктор

    // ─── Конструктор ─────────────────────────────────────────────────
    public CrptApi(TimeUnit timeUnit, int requestLimit, String token) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.token = token;
    }

    // ─── Основной метод для создания документа ──────────────────────
    public void createDocument(Document document, String signature) {
        waitIfLimitExceeded(); // Ожидание, если лимит превышен

        try {
            String json = gson.toJson(document);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Статус: " + response.statusCode());
            System.out.println("Ответ: " + response.body());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─── Метод ограничения количества запросов (rate limiter) ────────
    private void waitIfLimitExceeded() {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            long interval = timeUnit.toMillis(1);

            // Удаление устаревших временных меток
            while (!requestTimestamps.isEmpty() && now - requestTimestamps.peekFirst() >= interval) {
                requestTimestamps.pollFirst();
            }

            // Если лимит превышен, ждем
            while (requestTimestamps.size() >= requestLimit) {
                try {
                    long sleep = interval - (now - requestTimestamps.peekFirst());
                    lock.wait(Math.max(sleep, 1));
                    now = System.currentTimeMillis();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            requestTimestamps.addLast(now);
            lock.notifyAll();
        }
    }

    // ─── Вложенные классы данных (Document и Product) ────────────────

    public static class Document {
        public String description;
        public String doc_id = UUID.randomUUID().toString();
        public String doc_status = "DRAFT";
        public String doc_type = "LP_INTRODUCE_GOODS";
        public String importRequest = "MANUAL";
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public String production_type = "OWN_PRODUCTION";
        public String production_date = LocalDate.now().toString();
        public String reg_date = LocalDate.now().toString();
        public String reg_number;
        public List<Product> products = new ArrayList<>();
    }

    public static class Product {
        public String certificate_document;
        public String certificate_document_date;
        public String certificate_document_number;
        public String owner_inn;
        public String producer_inn;
        public String production_date = LocalDate.now().toString();
        public String tnved_code;
        public String uit_code;
        public String uitu_code;
    }

    // ─── Тестовый запуск (main метод) ────────────────────────────────
    public static void main(String[] args) {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 5, "your_token_here");

        Document doc = new Document();
        doc.description = "Тестовый документ";
        doc.owner_inn = "1234567890";
        doc.participant_inn = "1234567890";
        doc.producer_inn = "1234567890";
        doc.reg_number = "REG-001";

        Product product = new Product();
        product.certificate_document = "СЕРТИФИКАТ";
        product.certificate_document_date = "2023-01-01";
        product.certificate_document_number = "CERT123";
        product.owner_inn = "1234567890";
        product.producer_inn = "1234567890";
        product.tnved_code = "12345678";
        product.uit_code = "UIT001";
        product.uitu_code = "UITU001";

        doc.products.add(product);

        api.createDocument(doc, "example-signature");
    }
}
