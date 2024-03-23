package com.selsup;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


public class CrptApi {

	private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
	private static final ObjectMapper objectMapper = new ObjectMapper();
	private final Semaphore semaphore;

	public CrptApi(TimeUnit timeUnit, int requestLimit) {
		this.semaphore = new Semaphore(requestLimit);
		
		// Configure date format
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		objectMapper.setDateFormat(dateFormat);

		Runnable semaphoreCleaner = () -> {
			try {
				while (true) {
					semaphore.release(requestLimit - semaphore.availablePermits());
					//there are no specifications about how much must be an interval, I choose 1
					Thread.sleep(timeUnit.toMillis(1));
				}
			} catch (InterruptedException ignored) {
			}
		};
		Thread thread = new Thread(semaphoreCleaner);
		thread.start();
		thread.setDaemon(true);
	}

	public void createDocument(ProductDocument document, String signature) {
		try {
			semaphore.acquire();
			URL url = new URL(API_URL);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Content-Type", "application/json");
			connection.setDoOutput(true);

			try (OutputStream outputStream = connection.getOutputStream()) {
				String requestBody = objectMapper.writeValueAsString(document);
				outputStream.write(requestBody.getBytes());
				// the task does not indicate where to get the Signature, so I took it from the
				// request header
				connection.setRequestProperty("Signature", signature);
			}

			// maybe there is option to return responseCode
			int responseCode = connection.getResponseCode();
			connection.disconnect();
		} catch (IOException | InterruptedException e) {

			e.printStackTrace();
		} finally {
			semaphore.release();
		}

	}

	//inner class to represent an  Document in POJO
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class ProductDocument {
		private Description description;
		private String doc_id;
		private String doc_status;
		private String doc_type;
		private boolean importRequest;
		private String owner_inn;
		private String participant_inn;
		private String producer_inn;
		private LocalDate production_date;
		private String production_type; 
		private ArrayList<Product> products;
		private LocalDate reg_date;
		private   String reg_number;
	}

	//inner class to represent an  Document Description in POJO
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class Description {
		String participantInn;
	}
	
	//inner class to represent an  Document Product in POJO
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class Product{
		String certificate_document; 
        LocalDate certificate_document_date; 
        String certificate_document_number; 
        String owner_inn;
        String producer_inn; 
        LocalDate production_date;
        String tnved_code;
        String uit_code;
        String uitu_code;
	}

	public static void main(String[] args) {
		CrptApi api = new CrptApi(TimeUnit.SECONDS, 10);
        api.createDocument(new ProductDocument(), "signature");

	}

}
