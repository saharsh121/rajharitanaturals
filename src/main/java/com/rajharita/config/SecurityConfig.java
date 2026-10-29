package com.rajharita.config;

import com.rajharita.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public UserDetailsService unavailableUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Password authentication is handled by the account services");
        };
    }

    @Bean
    public UserSessionGuardFilter userSessionGuardFilter(
            UserRepository userRepository,
            com.rajharita.repositories.AdminRepository adminRepository,
            @Value("${app.auth.require-admin-approval:false}") boolean requireAdminApproval) {
        return new UserSessionGuardFilter(userRepository, adminRepository, requireAdminApproval);
    }

    @Bean
    public FilterRegistrationBean<UserSessionGuardFilter> userSessionGuardFilterRegistration(
            UserSessionGuardFilter filter) {
        FilterRegistrationBean<UserSessionGuardFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           UserSessionGuardFilter userSessionGuardFilter,
                                           SecurityContextRepository securityContextRepository)
            throws Exception {
        http
            .addFilterBefore(userSessionGuardFilter, AuthorizationFilter.class)
            .securityContext(context -> context.securityContextRepository(securityContextRepository))
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers(
                    "/api/farmer/**",
                    "/api/investment/**",
                    "/api/odop/**",
                    "/api/b2b/**"
                ))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/",
                    "/login",
                    "/register",
                    "/contact",
                    "/admin",
                    "/farmer",
                    "/investment",
                    "/odop",
                    "/b2b",
                    "/api/auth/csrf",
                    "/api/auth/register",
                    "/api/auth/verify-otp",
                    "/api/auth/login",
                    "/api/auth/request-password-reset",
                    "/api/auth/reset-password",
                    "/api/coupons/available",
                    "/api/showcase/images",
                    "/api/showcase/settings",
                    "/api/admin/login",
                    "/api/admin/registration/request",
                    "/api/admin/registration/verify",
                    "/api/farmer/**",
                    "/api/investment/**",
                    "/api/odop/**",
                    "/api/b2b/**",
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/products/**",
                    "/about/**",
                    "/uploads/**",
                    "/error"
                ).permitAll()
                .requestMatchers("/admin/dashboard", "/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/account").hasAnyRole("CUSTOMER", "STAFF")
                .requestMatchers("/api/auth/logout").authenticated()
                .requestMatchers("/api/auth/me", "/api/auth/profile", "/api/auth/password",
                        "/api/auth/account/**", "/api/auth/addresses/**",
                        "/api/auth/feedback", "/api/auth/support-requests",
                        "/api/cart/**", "/api/orders/**")
                    .hasAnyRole("CUSTOMER", "STAFF")
                .anyRequest().permitAll()
            )
            .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                (request, response, authenticationException) -> response.sendRedirect("/login"),
                request -> "/account".equals(request.getRequestURI())
            ).accessDeniedHandler((request, response, accessDeniedException) -> {
                if ("/account".equals(request.getRequestURI())) {
                    response.sendRedirect("/login");
                    return;
                }
                response.sendError(org.springframework.http.HttpStatus.FORBIDDEN.value());
            }))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        return http.build();
    }
}