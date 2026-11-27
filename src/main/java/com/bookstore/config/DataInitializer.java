package com.bookstore.config;

import com.bookstore.entity.Book;
import com.bookstore.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final BookRepository bookRepository;


    @Override
    public void run(String... args) {
        if (bookRepository.count() > 0) {
            return;
        }

        bookRepository.saveAll(List.of(
                Book.builder().title("The Pragmatic Programmer").author("Andrew Hunt").price(new BigDecimal("29.99")).stock(10).active(true).build(),
                Book.builder().title("Clean Code").author("Robert C. Martin").price(new BigDecimal("34.50")).stock(8).active(true).build(),
                Book.builder().title("Spring in Action").author("Craig Walls").price(new BigDecimal("39.99")).stock(5).active(true).build(),
                Book.builder().title("Designing Data-Intensive Applications").author("Martin Kleppmann").price(new BigDecimal("42.75")).stock(3).active(true).build()
        ));
    }
}
