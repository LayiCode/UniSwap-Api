package com.olamide.UniSwap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Uses the H2 test profile like the other integration tests, so this doesn't
// need a live MySQL just to boot the context.
@SpringBootTest
@ActiveProfiles("test")
class UniSwapApplicationTests {

	@Test
	void contextLoads() {
	}

}
