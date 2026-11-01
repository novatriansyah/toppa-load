package com.toppaload;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {
    @Test
    void verifiesModularMonolithBoundaries() {
        ApplicationModules.of(ToppaLoadApplication.class).verify();
    }
}
