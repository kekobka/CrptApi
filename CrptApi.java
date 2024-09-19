import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


public class CrptApi {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Document {
        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<Product> products;
        private String reg_date;
        private String reg_number;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Description {
        private String participantInn;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Product {
        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;
    }

    private static final String URL_API = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final Semaphore rateLimiter;
    private final ScheduledExecutorService scheduler;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.rateLimiter = new Semaphore(requestLimit);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();

        scheduler.scheduleAtFixedRate(() -> rateLimiter.release(requestLimit - rateLimiter.availablePermits()), 0, 1, timeUnit);
    }

    private String convertToJson(Document document) {
        String jsonDocument = null;
        try {
            jsonDocument = objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return jsonDocument;
    }


    public void createDocument(Document document, String signature) throws InterruptedException {
        rateLimiter.acquire();

        try (httpClient) {
            HttpPost request = new HttpPost(URL_API);
            StringEntity entity = new StringEntity(convertToJson(document));
            request.setEntity(entity);
            request.setHeader("Accept", "application/json");
            request.setHeader("Content-type", "application/json");
            request.setHeader("Signature", signature);
            HttpClientResponseHandler<String> responseHandler = new BasicHttpClientResponseHandler();

            httpClient.execute(request, responseHandler);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            scheduler.schedule(() -> rateLimiter.release(), 1, TimeUnit.SECONDS);
        }

    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        scheduler.shutdown();
    }
}

