package com.costco.scraper.service;

import com.costco.scraper.model.Product;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import com.google.cloud.firestore.WriteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
public class FirestoreService {

    private final Firestore firestore;

    private static final String COLLECTION_NAME = "products";
    private static final int BATCH_SIZE = 500;

    @Autowired(required = false)
    public FirestoreService(Firestore firestore) {
        this.firestore = firestore;
        if (firestore == null) {
            log.warn("Firestore not available - data persistence disabled");
        }
    }

    private boolean isAvailable() {
        if (firestore == null) {
            log.warn("Firestore not configured");
            return false;
        }
        return true;
    }

    /**
     * Save or update a single product
     */
    public void saveProduct(Product product) {
        if (!isAvailable()) return;

        try {
            product.setUpdatedAt(Instant.now());
            if (product.getScrapedAt() == null) {
                product.setScrapedAt(Instant.now());
            }

            DocumentReference docRef = firestore.collection(COLLECTION_NAME)
                    .document(product.getProductId());

            DocumentSnapshot snapshot = docRef.get().get();
            if (snapshot.exists()) {
                docRef.set(productToMap(product)).get();
                log.debug("Updated product: {}", product.getProductId());
            } else {
                docRef.set(productToMap(product)).get();
                log.debug("Created product: {}", product.getProductId());
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving product {}: {}", product.getProductId(), e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Save multiple products in batches
     */
    public void saveProducts(List<Product> products) {
        if (!isAvailable()) {
            log.info("Skipping save - Firestore not available. Would have saved {} products",
                    products != null ? products.size() : 0);
            return;
        }

        if (products == null || products.isEmpty()) {
            log.warn("No products to save");
            return;
        }

        log.info("Saving {} products to Firestore", products.size());
        Instant now = Instant.now();

        List<List<Product>> batches = partitionList(products, BATCH_SIZE);
        int batchNumber = 0;

        for (List<Product> batch : batches) {
            batchNumber++;
            try {
                WriteBatch writeBatch = firestore.batch();

                for (Product product : batch) {
                    product.setUpdatedAt(now);
                    if (product.getScrapedAt() == null) {
                        product.setScrapedAt(now);
                    }

                    DocumentReference docRef = firestore.collection(COLLECTION_NAME)
                            .document(product.getProductId());
                    writeBatch.set(docRef, productToMap(product));
                }

                ApiFuture<List<WriteResult>> future = writeBatch.commit();
                List<WriteResult> results = future.get();
                log.info("Batch {}/{} committed: {} documents", batchNumber, batches.size(), results.size());

            } catch (InterruptedException | ExecutionException e) {
                log.error("Error saving batch {}: {}", batchNumber, e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        log.info("Finished saving all products");
    }

    /**
     * Get a product by ID
     */
    public Product getProduct(String productId) {
        if (!isAvailable()) return null;

        try {
            DocumentSnapshot snapshot = firestore.collection(COLLECTION_NAME)
                    .document(productId)
                    .get()
                    .get();

            if (snapshot.exists()) {
                return mapToProduct(snapshot);
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error getting product {}: {}", productId, e.getMessage());
            Thread.currentThread().interrupt();
        }
        return null;
    }

    /**
     * Check if a product exists
     */
    public boolean productExists(String productId) {
        if (!isAvailable()) return false;

        try {
            DocumentSnapshot snapshot = firestore.collection(COLLECTION_NAME)
                    .document(productId)
                    .get()
                    .get();
            return snapshot.exists();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error checking product existence {}: {}", productId, e.getMessage());
            Thread.currentThread().interrupt();
        }
        return false;
    }

    /**
     * Delete a product
     */
    public void deleteProduct(String productId) {
        if (!isAvailable()) return;

        try {
            firestore.collection(COLLECTION_NAME)
                    .document(productId)
                    .delete()
                    .get();
            log.debug("Deleted product: {}", productId);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error deleting product {}: {}", productId, e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get count of all products
     */
    public long getProductCount() {
        if (!isAvailable()) return 0;

        try {
            return firestore.collection(COLLECTION_NAME)
                    .get()
                    .get()
                    .size();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error getting product count: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
        return 0;
    }

    private Map<String, Object> productToMap(Product product) {
        Map<String, Object> map = new HashMap<>();
        map.put("productId", product.getProductId());
        map.put("name", product.getName());
        map.put("price", product.getPrice());
        map.put("originalPrice", product.getOriginalPrice());
        map.put("discount", product.getDiscount());
        map.put("imageUrl", product.getImageUrl());
        map.put("imageUrls", product.getImageUrls());
        map.put("category", product.getCategory());
        map.put("subCategory", product.getSubCategory());
        map.put("productUrl", product.getProductUrl());
        map.put("description", product.getDescription());
        map.put("availability", product.getAvailability());
        map.put("scrapedAt", product.getScrapedAt() != null ? product.getScrapedAt().toString() : null);
        map.put("updatedAt", product.getUpdatedAt() != null ? product.getUpdatedAt().toString() : null);
        return map;
    }

    @SuppressWarnings("unchecked")
    private Product mapToProduct(DocumentSnapshot snapshot) {
        return Product.builder()
                .productId(snapshot.getString("productId"))
                .name(snapshot.getString("name"))
                .price(snapshot.getDouble("price"))
                .originalPrice(snapshot.getDouble("originalPrice"))
                .discount(snapshot.getString("discount"))
                .imageUrl(snapshot.getString("imageUrl"))
                .imageUrls((List<String>) snapshot.get("imageUrls"))
                .category(snapshot.getString("category"))
                .subCategory(snapshot.getString("subCategory"))
                .productUrl(snapshot.getString("productUrl"))
                .description(snapshot.getString("description"))
                .availability(snapshot.getBoolean("availability"))
                .scrapedAt(parseInstant(snapshot.getString("scrapedAt")))
                .updatedAt(parseInstant(snapshot.getString("updatedAt")))
                .build();
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private <T> List<List<T>> partitionList(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
