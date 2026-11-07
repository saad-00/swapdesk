package io.github.saad00.swapdesk;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Smoke test: the whole application context starts against a real PostgreSQL. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SwapdeskApplicationTests {

    @Test
    void contextLoads() {
    }
}
