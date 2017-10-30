package com.example.ws;

import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

public class ExampleWebSocketServlet extends WebSocketServlet {
    
    @Override
    public void configure(final WebSocketServletFactory factory)
    {
        factory.getPolicy().setIdleTimeout(60000);
        factory.register(ExampleSocket.class);
    }
}