package bookstore.service;

import com.bookstore.dto.auth.LoginRequest;
import com.bookstore.dto.auth.RegisterRequest;
import com.bookstore.entity.User;
import com.bookstore.exception.ConflictException;
import com.bookstore.exception.UnauthorizedException;
import com.bookstore.repository.UserRepository;
import com.bookstore.service.impl.AuthServiceImpl;
import com.bookstore.service.impl.UserDetailsServiceImpl;
import com.bookstore.service.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository users;
    @Mock private PasswordEncoder passwords;
    @Mock private JwtService jwt;
    @Mock private UserDetailsServiceImpl userDetails;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(users, passwords, jwt, userDetails);
    }

    @Test
    void duplicateEmailIsCheckedAfterNormalization() {
        when(users.existsByEmail("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("User", "USER@example.com", "password")))
                .isInstanceOf(ConflictException.class);
        verify(users, never()).save(any());
        verifyNoInteractions(passwords, jwt);
    }

    @Test
    void incorrectPasswordDoesNotGenerateToken() {
        User user = new User();
        user.setPassword("encoded");
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("incorrect", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("USER@example.com", "incorrect")))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(jwt, userDetails);
    }

    @Test
    void validLoginReturnsTokenAndUserDetails() {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setName("User");
        user.setPassword("encoded");
        UserDetails principal = mock(UserDetails.class);
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("password", "encoded")).thenReturn(true);
        when(userDetails.loadUserByUsername("user@example.com")).thenReturn(principal);
        when(jwt.generateToken(principal)).thenReturn("token");

        var response = service.login(new LoginRequest("USER@example.com", "password"));

        assertThat(response.token()).isEqualTo("token");
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("user@example.com");
    }
}
