package com.bookstore.service.order;

import com.bookstore.entity.Book;
import com.bookstore.entity.Order;
import com.bookstore.entity.OrderItem;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderStockRestorer {

    private final BookRepository bookRepository;

    public void restore(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return;
        }
        List<Long> bookIds = order.getItems().stream()
                .map(item -> item.getBook().getId())
                .distinct()
                .sorted()
                .toList();
        Map<Long, Book> books = bookRepository.findAllByIdForUpdate(bookIds).stream()
                .collect(Collectors.toMap(Book::getId, Function.identity()));
        for (OrderItem item : order.getItems()) {
            Book book = books.get(item.getBook().getId());
            if (book == null) {
                throw new ResourceNotFoundException("Book not found: " + item.getBook().getId());
            }
            book.setStock(book.getStock() + item.getQuantity());
        }
    }
}
