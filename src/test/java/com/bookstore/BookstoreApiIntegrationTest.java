package com.bookstore;

import com.bookstore.dto.AddCartItemRequest;
import com.bookstore.dto.auth.AuthResponse;
import com.bookstore.dto.auth.RegisterRequest;
import com.bookstore.entity.*;
import com.bookstore.exception.BadRequestException;
import com.bookstore.exception.InsufficientStockException;
import com.bookstore.repository.*;
import com.bookstore.service.OrderService;
import com.bookstore.service.PaymentService;
import com.bookstore.service.order.OrderExpirationScheduler;
import com.bookstore.service.order.OrderExpirationService;
import com.bookstore.service.payment.CardPaymentGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.orders.reservation-duration=PT2M",
        "app.orders.expiration-check-interval=PT1H"
})
class BookstoreApiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderExpirationScheduler orderExpirationScheduler;

    @Autowired
    private OrderExpirationService orderExpirationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private CardPaymentGateway cardPaymentGateway;

    @BeforeEach
    void setUp() {
        when(cardPaymentGateway.charge(any(), any())).thenReturn(true);
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        userRepository.deleteAll();
        bookRepository.deleteAll();
    }

    @Test
    void registerLoginAndListBooks() throws Exception {
        RegisterRequest request = new RegisterRequest("Alice", "alice@example.com", "password123");

        String registerJson = objectMapper.writeValueAsString(request);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alice@example.com"));

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidRequestsReturnConsistentValidationErrors() throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Valid User", "validation@example.com", "password123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readValue(response, AuthResponse.class).token();

        mockMvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":-1,\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(put("/api/cart/items/{bookId}", 0).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/orders/not-a-number").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "x".repeat(256)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void malformedBearerTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/cart").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationDetectsDuplicateEmailRegardlessOfCase() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("First User", "same@example.com", "password123"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Second User", "SAME@example.com", "password123"))))
                .andExpect(status().isConflict());
    }

    @Test
    void openApiDescribesBookAndPaymentEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/books']").exists())
                .andExpect(jsonPath("$.paths['/api/orders/{orderId}/pay'].post.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    @Test
    void addToCartAndCreateOrder() throws Exception {
        Book book = bookRepository.save(Book.builder()
                .title("Effective Java")
                .author("Joshua Bloch")
                .price(new BigDecimal("28.99"))
                .stock(5)
                .active(true)
                .build());

        RegisterRequest request = new RegisterRequest("Bob", "bob@example.com", "password123");

        String registerJson = objectMapper.writeValueAsString(request);
        String registerResponse = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        AuthResponse authResponse = objectMapper.readValue(registerResponse, AuthResponse.class);
        String token = authResponse.token();

        AddCartItemRequest addCartItemRequest = new AddCartItemRequest(book.getId(), 2);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addCartItemRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].bookId").value(book.getId().intValue()))
                .andExpect(jsonPath("$.items[0].quantity").value(2));

        String orderResponse = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "order-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(57.98))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long orderId = objectMapper.readTree(orderResponse).path("orderId").asLong();
        Book updatedBook = bookRepository.findById(book.getId()).orElseThrow();
        updatedBook.setPrice(new BigDecimal("99.00"));
        bookRepository.save(updatedBook);
        mockMvc.perform(get("/api/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitPrice").value(28.99))
                .andExpect(jsonPath("$.totalAmount").value(57.98));

        String otherUserResponse = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Other", "other@example.com", "password123"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String otherToken = objectMapper.readValue(otherUserResponse, AuthResponse.class).token();
        mockMvc.perform(post("/api/orders/{orderId}/pay", orderId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/orders/{orderId}/pay", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(post("/api/orders/{orderId}/pay", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void singleStockShouldOnlyAllowOneSuccessfulOrderUnderRace() throws Exception {
        Book book = bookRepository.save(Book.builder()
                .title("Last Copy")
                .author("Tester")
                .price(new BigDecimal("15.00"))
                .stock(1)
                .active(true)
                .build());

        User userOne = userRepository.save(createUser("user1@example.com", "User One"));
        User userTwo = userRepository.save(createUser("user2@example.com", "User Two"));

        Cart cartOne = new Cart();
        cartOne.setUser(userOne);
        cartOne.getItems().add(createCartItem(cartOne, book, 1));
        cartRepository.save(cartOne);

        Cart cartTwo = new Cart();
        cartTwo.setUser(userTwo);
        cartTwo.getItems().add(createCartItem(cartTwo, book, 1));
        cartRepository.save(cartTwo);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        Future<?> futureOne = executor.submit(() -> {
            latch.await();
            try {
                orderService.createOrder(userOne.getId(), UUID.randomUUID().toString());
                successCount.incrementAndGet();
            } catch (Exception ignored) {
            }
            return null;
        });

        Future<?> futureTwo = executor.submit(() -> {
            latch.await();
            try {
                orderService.createOrder(userTwo.getId(), UUID.randomUUID().toString());
                successCount.incrementAndGet();
            } catch (Exception ignored) {
            }
            return null;
        });

        latch.countDown();
        futureOne.get(30, TimeUnit.SECONDS);
        futureTwo.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
    }

    @Test
    void insufficientStockRollsBackAllInventoryChangesAndRetainsCart() {
        Book available = bookRepository.save(Book.builder().title("Available").author("Tester")
                .price(new BigDecimal("10.00")).stock(2).active(true).build());
        Book unavailable = bookRepository.save(Book.builder().title("Unavailable").author("Tester")
                .price(new BigDecimal("10.00")).stock(0).active(true).build());
        User user = userRepository.save(createUser("stock@example.com", "Stock User"));
        Cart cart = new Cart();
        cart.setUser(user);
        cart.getItems().add(createCartItem(cart, available, 1));
        cart.getItems().add(createCartItem(cart, unavailable, 1));
        cartRepository.save(cart);

        assertThatThrownBy(() -> orderService.createOrder(user.getId(), null))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(bookRepository.findById(available.getId()).orElseThrow().getStock()).isEqualTo(2);
        assertThat(bookRepository.findById(unavailable.getId()).orElseThrow().getStock()).isZero();
        assertThat(orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).isEmpty();
        Integer remainingItems = transactionTemplate.execute(status ->
                cartRepository.findByUserId(user.getId()).orElseThrow().getItems().size());
        assertThat(remainingItems).isEqualTo(2);
    }

    @Test
    void failedPaymentShouldPersistFailureAndRestoreStock() throws Exception {
        Book book = bookRepository.save(Book.builder()
                .title("Payment Test")
                .author("Tester")
                .price(new BigDecimal("15.00"))
                .stock(2)
                .active(true)
                .build());
        String registerResponse = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Payment User", "payment@example.com", "password123"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = objectMapper.readValue(registerResponse, AuthResponse.class).token();
        User user = userRepository.findByEmail("payment@example.com").orElseThrow();
        Cart cart = new Cart();
        cart.setUser(user);
        cart.getItems().add(createCartItem(cart, book, 1));
        cartRepository.save(cart);

        Long orderId = orderService.createOrder(user.getId(), UUID.randomUUID().toString()).orderId();
        assertThat(bookRepository.findById(book.getId()).orElseThrow().getStock()).isEqualTo(1);

        mockMvc.perform(post("/api/orders/{orderId}/pay", orderId)
                        .header("Authorization", "Bearer " + token)
                        .param("paymentMethod", "WIRE"))
                .andExpect(status().isBadRequest());

        Order failedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(failedOrder.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(bookRepository.findById(book.getId()).orElseThrow().getStock()).isEqualTo(2);
        assertThatThrownBy(() -> paymentService.payForOrder(user.getId(), orderId, "CARD"))
                .isInstanceOf(BadRequestException.class);
        assertThat(bookRepository.findById(book.getId()).orElseThrow().getStock()).isEqualTo(2);
    }

    @Test
    void scheduledExpirationRestoresStockOnlyForExpiredPendingOrders() {
        Book book = createReservationBook();
        User user = userRepository.save(createUser("expiration@example.com", "Expiration User"));
        User pendingUser = userRepository.save(createUser("pending@example.com", "Pending User"));
        User paidUser = userRepository.save(createUser("paid@example.com", "Paid User"));
        Long expiredId = createOrderForBook(user, book);
        Long pendingId = createOrderForBook(pendingUser, book);
        Long paidId = createOrderForBook(paidUser, book);
        paymentService.payForOrder(paidUser.getId(), paidId, "CARD");

        assertThat(orderRepository.findById(pendingId).orElseThrow().getExpiresAt())
                .isAfter(LocalDateTime.now().plusSeconds(100))
                .isBefore(LocalDateTime.now().plusSeconds(121));
        setExpiry(expiredId, LocalDateTime.now().minusMinutes(1));
        setExpiry(paidId, LocalDateTime.now().minusMinutes(1));
        orderExpirationScheduler.expireOrders();
        orderExpirationScheduler.expireOrders();

        assertThat(orderRepository.findById(expiredId).orElseThrow().getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(orderRepository.findById(pendingId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(orderRepository.findById(paidId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(bookRepository.findById(book.getId()).orElseThrow().getStock()).isEqualTo(3);
    }

    @Test
    void paymentAfterDeadlineExpiresOrderWithoutWaitingForScheduler() {
        Book book = createReservationBook();
        User user = userRepository.save(createUser("late@example.com", "Late User"));
        Long orderId = createOrderForBook(user, book);
        setExpiry(orderId, LocalDateTime.now().minusSeconds(1));

        assertThatThrownBy(() -> paymentService.payForOrder(user.getId(), orderId, "CARD"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(bookRepository.findById(book.getId()).orElseThrow().getStock()).isEqualTo(5);
        User anotherUser = userRepository.save(createUser("next@example.com", "Next User"));
        Long nextOrderId = createOrderForBook(anotherUser, book);
        assertThat(orderRepository.findById(nextOrderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(bookRepository.findById(book.getId()).orElseThrow().getStock()).isEqualTo(4);
    }

    @Test
    void legacyPendingOrdersWithoutExpiryAreReleased() {
        Book book = createReservationBook();
        User user = userRepository.save(createUser("legacy@example.com", "Legacy User"));
        Long orderId = createOrderForBook(user, book);
        jdbcTemplate.update("UPDATE \"orders\" SET \"expires_at\" = NULL, \"createdAt\" = ? WHERE \"id\" = ?",
                LocalDateTime.now().minusMinutes(3), orderId);

        orderExpirationScheduler.expireOrders();

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(bookRepository.findById(book.getId()).orElseThrow().getStock()).isEqualTo(5);
    }

    @Test
    void concurrentPaymentAndExpirationReleaseStockOnce() throws Exception {
        Book book = createReservationBook();
        User user = userRepository.save(createUser("race@example.com", "Race User"));
        Long orderId = createOrderForBook(user, book);
        setExpiry(orderId, LocalDateTime.now().minusSeconds(1));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> payment = executor.submit(() -> {
                start.await();
                assertThatThrownBy(() -> paymentService.payForOrder(user.getId(), orderId, "CARD"))
                        .isInstanceOf(BadRequestException.class);
                return null;
            });
            Future<?> expiration = executor.submit(() -> {
                start.await();
                orderExpirationService.expireOrder(orderId);
                return null;
            });
            start.countDown();
            payment.get(30, TimeUnit.SECONDS);
            expiration.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(bookRepository.findById(book.getId()).orElseThrow().getStock()).isEqualTo(5);
    }

    @Test
    void paymentStartedBeforeDeadlineCompletesWhileExpirationWaits() throws Exception {
        Book book = createReservationBook();
        User user = userRepository.save(createUser("payrace@example.com", "Payment Race User"));
        Long orderId = createOrderForBook(user, book);
        LocalDateTime deadline = LocalDateTime.now().plusSeconds(4);
        setExpiry(orderId, deadline);
        CountDownLatch charging = new CountDownLatch(1);
        CountDownLatch finishCharge = new CountDownLatch(1);
        when(cardPaymentGateway.charge(any(), any())).thenAnswer(invocation -> {
            charging.countDown();
            if (!finishCharge.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Payment was not released");
            }
            return true;
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> payment = executor.submit(() -> paymentService.payForOrder(user.getId(), orderId, "CARD"));
            assertThat(charging.await(5, TimeUnit.SECONDS)).isTrue();
            while (LocalDateTime.now().isBefore(deadline)) {
                Thread.sleep(25);
            }
            CountDownLatch expirationStarted = new CountDownLatch(1);
            Future<?> expiration = executor.submit(() -> {
                expirationStarted.countDown();
                orderExpirationService.expireOrder(orderId);
            });
            assertThat(expirationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            finishCharge.countDown();
            payment.get(20, TimeUnit.SECONDS);
            expiration.get(20, TimeUnit.SECONDS);
        } finally {
            finishCharge.countDown();
            executor.shutdownNow();
        }

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(bookRepository.findById(book.getId()).orElseThrow().getStock()).isEqualTo(4);
    }

    private Book createReservationBook() {
        return bookRepository.save(Book.builder()
                .title("Reservation Test")
                .author("Tester")
                .price(new BigDecimal("15.00"))
                .stock(5)
                .active(true)
                .build());
    }

    private Long createOrderForBook(User user, Book book) {
        return transactionTemplate.execute(status -> {
            Cart cart = cartRepository.findByUserId(user.getId()).orElseGet(() -> {
                Cart newCart = new Cart();
                newCart.setUser(user);
                return newCart;
            });
            cart.getItems().add(createCartItem(cart, book, 1));
            cartRepository.save(cart);
            return orderService.createOrder(user.getId(), UUID.randomUUID().toString()).orderId();
        });
    }

    private void setExpiry(Long orderId, LocalDateTime expiresAt) {
        jdbcTemplate.update("UPDATE \"orders\" SET \"expires_at\" = ? WHERE \"id\" = ?", expiresAt, orderId);
    }

    private User createUser(String email, String name) {
        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setPassword("encoded-password");
        user.setRole(Role.USER);
        return user;
    }

    private CartItem createCartItem(Cart cart, Book book, int quantity) {
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setBook(book);
        item.setQuantity(quantity);
        return item;
    }
}
