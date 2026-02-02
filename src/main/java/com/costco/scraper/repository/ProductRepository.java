package com.costco.scraper.repository;

import com.costco.scraper.model.Product;
import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends FirestoreReactiveRepository<Product> {
    // Reactive repository for Product entity
    // Custom query methods can be added here if needed
}
