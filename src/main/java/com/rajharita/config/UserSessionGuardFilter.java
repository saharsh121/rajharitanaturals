package com.rajharita.config;

import com.rajharita.repositories.AdminRepository;
import com.rajharita.repositories.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class UserSessionGuardFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final boolean requireAdminApproval;

    public UserSessionGuardFilter(UserRepository userRepository, AdminRepository adminRepository,
                                  boolean requireAdminApproval) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.requireAdminApproval = requireAdminApproval;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") && !"/account".equals(path) && !"/admin/dashboard".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean invalidUser = isCustomer(authentication) && !hasValidAccount(authentication.getName());
        boolean invalidAdmin = isAdmin(authentication) && !hasValidAdmin(authentication.getName());
        if (invalidUser || invalidAdmin) {
            SecurityContextHolder.clearContext();
            var session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            if ("/account".equals(request.getRequestURI())
                    || "/admin/dashboard".equals(request.getRequestURI())) {
                response.sendRedirect(invalidAdmin ? "/admin" : "/login");
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Account is no longer active\"}");
            }
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isCustomer(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(authority ->
                    authority.getAuthority().equals("ROLE_CUSTOMER")
                            || authority.getAuthority().equals("ROLE_STAFF"));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    private boolean hasValidAdmin(String username) {
        return adminRepository.findByUsername(username)
                .map(admin -> admin.isActive())
                .orElse(false);
    }

    private boolean hasValidAccount(String username) {
        return userRepository.findByUsername(username)
                .map(user -> user.isVerified() && user.isActive()
                        && (!requireAdminApproval || user.isApproved()))
                .orElse(false);
    }
}
