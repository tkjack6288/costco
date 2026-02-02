package com.costco.scraper.model;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collectionName = "products")
public class Product {

    @DocumentId
    private String productId;

    private String name;

    private Double price;

    private Double originalPrice;

    private String discount;

    private String imageUrl;

    private List<String> imageUrls;

    private String category;

    private String subCategory;

    private String productUrl;

    private String description;

    private Boolean availability;

    private Instant scrapedAt;

    private Instant updatedAt;
}
