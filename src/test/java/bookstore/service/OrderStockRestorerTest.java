package bookstore.service;

import com.bookstore.entity.Book;
import com.bookstore.entity.Order;
import com.bookstore.entity.OrderItem;
import com.bookstore.exception.ResourceNotFoundException;
import com.bookstore.repository.BookRepository;
import com.bookstore.service.order.OrderStockRestorer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OrderStockRestorerTest {

    @Test
    void restoresQuantityUsingLockedInventoryRows() {
        Book book = new Book();
        book.setId(1L);
        book.setStock(2);
        OrderItem item = new OrderItem();
        item.setBook(book);
        item.setQuantity(3);
        Order order = new Order();
        order.setItems(List.of(item));
        BookRepository books = mock(BookRepository.class);
        when(books.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(book));

        new OrderStockRestorer(books).restore(order);

        assertThat(book.getStock()).isEqualTo(5);
        verify(books).findAllByIdForUpdate(List.of(1L));
    }

    @Test
    void missingInventoryRowDoesNotSilentlyRestore() {
        Book book = new Book();
        book.setId(1L);
        OrderItem item = new OrderItem();
        item.setBook(book);
        item.setQuantity(1);
        Order order = new Order();
        order.setItems(List.of(item));
        BookRepository books = mock(BookRepository.class);
        when(books.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of());

        assertThatThrownBy(() -> new OrderStockRestorer(books).restore(order))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
