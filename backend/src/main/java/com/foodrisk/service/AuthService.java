package com.foodrisk.service;

import com.foodrisk.dto.AuthResponse;
import com.foodrisk.dto.LoginRequest;
import com.foodrisk.dto.RegisterRequest;
import com.foodrisk.dto.RegisterResponse;
import com.foodrisk.dto.UserResponse;
import com.foodrisk.entity.User;
import com.foodrisk.exception.EmailAlreadyExistsException;
import com.foodrisk.repository.UserRepository;
import com.foodrisk.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing user registration and authentication business flows.
 *
 * Enforces BCrypt password hashing, email uniqueness checks, and JWT token issuance.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /**
     * Registers a new user with BCrypt password hashing.
     *
     * @param request registration request DTO
     * @return registration response DTO
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        String trimmedName = request.name().trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException("An account with this email already exists.");
        }

        String hashedPassword = passwordEncoder.encode(request.password());
        User user = new User(trimmedName, normalizedEmail, hashedPassword);
        User savedUser = userRepository.save(user);

        UserResponse userResponse = new UserResponse(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail()
        );

        return new RegisterResponse("Registration successful", userResponse);
    }

    /**
     * Authenticates a user using Spring Security and issues a stateless JWT token.
     *
     * @param request login request DTO
     * @return authentication response DTO with JWT
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        // Performs BCrypt verification via Spring Security's AuthenticationManager
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
        );

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + normalizedEmail));

        String token = jwtService.generateToken(user.getEmail());

        UserResponse userResponse = new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail()
        );

        return new AuthResponse(
                token,
                "Bearer",
                jwtService.getExpirationSeconds(),
                userResponse
        );
    }
}
