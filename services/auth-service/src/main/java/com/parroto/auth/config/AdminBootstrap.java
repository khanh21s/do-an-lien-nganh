package com.parroto.auth.config;

import com.parroto.auth.entity.User;
import com.parroto.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Locale;

/**
 * Seeds the single admin account on startup from ADMIN_EMAIL / ADMIN_PASSWORD (.env).
 *
 * <ul>
 *   <li>Both blank → nothing happens (admin can still be created by registering an
 *       ADMIN_EMAILS address).</li>
 *   <li>Account missing → created as an email/password user.</li>
 *   <li>Account present → left untouched, unless ADMIN_PASSWORD_RESET=true, which sets the
 *       password to ADMIN_PASSWORD (use once to recover access, then switch it back off).</li>
 * </ul>
 * Admin rights themselves still come from the ADMIN_EMAILS allow-list checked by every
 * service; a warning is logged when ADMIN_EMAIL is not on it.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final boolean resetPassword;
    private final String adminEmailsCsv;

    public AdminBootstrap(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${admin.bootstrap.email:}") String email,
                          @Value("${admin.bootstrap.password:}") String password,
                          @Value("${admin.bootstrap.reset-password:false}") boolean resetPassword,
                          @Value("${admin.emails:}") String adminEmailsCsv) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        this.password = password == null ? "" : password;
        this.resetPassword = resetPassword;
        this.adminEmailsCsv = adminEmailsCsv == null ? "" : adminEmailsCsv;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isEmpty() || password.isEmpty()) {
            log.info("Admin bootstrap skipped (ADMIN_EMAIL / ADMIN_PASSWORD not set)");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            log.warn("Admin bootstrap skipped: ADMIN_PASSWORD must be at least {} characters", MIN_PASSWORD_LENGTH);
            return;
        }
        boolean allowListed = Arrays.stream(adminEmailsCsv.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .anyMatch(email::equals);
        if (!allowListed) {
            log.warn("ADMIN_EMAIL {} is not in ADMIN_EMAILS - the account will exist but have no admin rights", email);
        }

        userRepository.findByEmail(email).ifPresentOrElse(user -> {
            if (!resetPassword) {
                log.info("Admin account {} already exists (password unchanged)", email);
                return;
            }
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setAuthProvider("email");
            user.setIsActive(true);
            userRepository.save(user);
            log.warn("Admin account {}: password reset from ADMIN_PASSWORD (set ADMIN_PASSWORD_RESET=false again)", email);
        }, () -> {
            userRepository.save(User.builder()
                    .email(email)
                    .passwordHash(passwordEncoder.encode(password))
                    .authProvider("email")
                    .cefrLevel("C1")
                    .build());
            log.info("Admin account {} created", email);
        });
    }
}
