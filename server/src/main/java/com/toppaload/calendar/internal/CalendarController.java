package com.toppaload.calendar.internal;

import com.toppaload.calendar.CalendarFeedService;
import com.toppaload.calendar.CalendarFeedTokenDto;
import com.toppaload.identity.AuthenticatedActor;
import com.toppaload.identity.UserDirectory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/calendar")
class CalendarController {

    private final CalendarFeedService calendarFeedService;
    private final UserDirectory userDirectory;

    CalendarController(CalendarFeedService calendarFeedService, UserDirectory userDirectory) {
        this.calendarFeedService = calendarFeedService;
        this.userDirectory = userDirectory;
    }

    @GetMapping("/token")
    public ResponseEntity<CalendarFeedTokenDto> getFeedToken(HttpServletRequest request) {
        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        String baseUrl = extractBaseUrl(request);
        return ResponseEntity.ok(calendarFeedService.getOrCreateToken(actor.userId(), baseUrl));
    }

    @PostMapping("/token/rotate")
    public ResponseEntity<CalendarFeedTokenDto> rotateFeedToken(HttpServletRequest request) {
        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        String baseUrl = extractBaseUrl(request);
        return ResponseEntity.ok(calendarFeedService.rotateToken(actor.userId(), baseUrl));
    }

    @GetMapping(value = "/feed/{token}.ics", produces = "text/calendar; charset=UTF-8")
    public ResponseEntity<String> getFeedStream(@PathVariable String token) {
        String icsContent = calendarFeedService.generateICalendarFeed(token);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"));
        headers.setContentDispositionFormData("inline", "toppaload-schedule.ics");
        headers.setCacheControl("no-cache, no-store, must-revalidate");

        return ResponseEntity.ok().headers(headers).body(icsContent);
    }

    private String extractBaseUrl(HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null) scheme = request.getScheme();

        String host = request.getHeader("X-Forwarded-Host");
        if (host == null) {
            host = request.getServerName();
            int port = request.getServerPort();
            if ((scheme.equals("http") && port != 80) || (scheme.equals("https") && port != 443)) {
                host += ":" + port;
            }
        }
        return scheme + "://" + host;
    }
}

