package com.processpuzzle.orgadmin.adapters.inbound;

import com.processpuzzle.orgadmin.api.RegistrationApi;
import com.processpuzzle.orgadmin.model.UserRegistration;
import com.processpuzzle.orgadmin.usecases.inbound.RegisterUser;
import com.processpuzzle.orgadmin.usecases.outbound.SelfRegistrationPort;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for self-registration. Deliberately not {@code @LogClass}: that would log the request
 * argument, which carries a password.
 *
 * <p>The client address is {@code X-Real-IP} when present, because the platform's NgInx sets it from
 * the real peer and the backend sits behind it; the servlet's own remote address would be the proxy
 * for everybody. A deployment that exposes this backend directly, without a proxy that overwrites
 * the header, lets a caller choose its own rate-limit bucket.
 */
@RestController
public class RegistrationEndpoint implements RegistrationApi {

    private final RegisterUser registerUser;
    private final HttpServletRequest request;

    public RegistrationEndpoint(RegisterUser registerUser, HttpServletRequest request) {
        this.registerUser = registerUser;
        this.request = request;
    }

    @Override
    public ResponseEntity<Void> registerUser(UserRegistration input) {
        registerUser.execute(clientKey(), new SelfRegistrationPort.Registration(
                input.getEmail().trim(), input.getPassword(), input.getFirstName(), input.getLastName()));
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    private String clientKey() {
        String forwarded = request.getHeader("X-Real-IP");
        return forwarded != null && !forwarded.isBlank() ? forwarded.trim() : request.getRemoteAddr();
    }
}
