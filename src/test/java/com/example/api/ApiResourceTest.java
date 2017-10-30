package com.example;

import javax.ws.rs.core.Application;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

import com.example.api.ApiResource;

public class ApiResourceTest extends JerseyTest {

    @Override
    protected Application configure() {
        return new ResourceConfig(ApiResource.class);
    }

    /**
     * Test to see that the message "Got it!" is sent in the response.
     */
    @Test
    public void testHelloWorld() {
        final String responseMsg = target().path("/helloworld").request().get(String.class);

        assertEquals("Hello World!", responseMsg);
    }
}
