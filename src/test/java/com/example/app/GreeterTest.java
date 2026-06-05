package com.example.app;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GreeterTest {

    @Test
    void greetReturnsExpectedString() {
        Greeter greeter = new Greeter("World");
        assertEquals("Hello, World!", greeter.greet());
    }
}
