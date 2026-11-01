package com.toppaload.shared;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@Controller
public class SpaForwardingController {

    private static final List<String> EXCLUDED_PREFIXES = List.of(
            "/api",
            "/actuator",
            "/oauth2",
            "/login",
            "/v3/api-docs",
            "/swagger-ui"
    );

    @GetMapping(value = {
            "/{path:[^\\.]*}",
            "/*/{path:[^\\.]*}",
            "/*/*/{path:[^\\.]*}",
            "/*/*/*/{path:[^\\.]*}",
            "/*/*/*/*/{path:[^\\.]*}"
    })
    public String forwardSpa(HttpServletRequest request) throws NoResourceFoundException {
        String uri = request.getRequestURI();
        if (isExcluded(uri)) {
            String resourcePath = (uri != null && uri.startsWith("/")) ? uri.substring(1) : uri;
            throw new NoResourceFoundException(HttpMethod.GET, resourcePath, "No static resource " + resourcePath + ".");
        }
        return "forward:/index.html";
    }

    private boolean isExcluded(String uri) {
        if (uri == null) {
            return false;
        }
        for (String prefix : EXCLUDED_PREFIXES) {
            if (uri.equals(prefix) || uri.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }
}
