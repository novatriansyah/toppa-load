package com.toppaload.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/system")
public class SystemInfoController {

    private final WibClock wibClock;
    private final String appVersion;

    public SystemInfoController(
            WibClock wibClock,
            @Value("${info.app.version:0.0.1-SNAPSHOT}") String appVersion) {
        this.wibClock = wibClock;
        this.appVersion = appVersion;
    }

    @GetMapping("/info")
    public SystemInfoResponse getSystemInfo() {
        return new SystemInfoResponse(
                "Toppa Load",
                appVersion,
                WibClock.WIB.getId(),
                wibClock.todayWib()
        );
    }

    public record SystemInfoResponse(
            String appName,
            String version,
            String workTimezone,
            LocalDate currentWorkDate
    ) {}
}
