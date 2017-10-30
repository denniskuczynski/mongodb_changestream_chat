package com.example.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.*;
import com.mongodb.client.*;
import com.mongodb.client.model.changestream.*;
import org.bson.*;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * WebSocket demo using MongoDB Changestreams for publish/subscribe
 * 
 * Assumes a local replica set has been started on ports 3000,3001,3002
 */
public class ExampleSocket implements WebSocketListener {
    final static int MAX_CLIENTS = 1000;
    final static int MAX_HISTORY = 1000;
    final static String MONGO_CLIENT_URI = "mongodb://localhost:3000,localhost:3001,localhost:3002";

    private static List<Message> _history;
    private static List<Session> _clients;
    private static MongoClient _mongoClient;
    private static boolean _initialized = false;

    private Session _client;
    private ObjectMapper _mapper;

    private static synchronized void initializeStatic() {
        if (_initialized) {
            return;
        }

        try {
            System.out.println("Initializing ExampleSocket");

            _history = new ArrayList<>();
            _clients = new CopyOnWriteArrayList<>();
            _mongoClient = new MongoClient(new MongoClientURI(MONGO_CLIENT_URI));
            
            // Load the most recent 1000 documents
            final MongoDatabase db = _mongoClient.getDatabase("chat");
            final MongoCollection<Document> historyCollection = db.getCollection("history");
            final List<Document> historyDocuments = new ArrayList();
            historyCollection.find()
                .sort(new Document("$natural", -1))
                .limit(1000)
                .into(historyDocuments);
            Collections.reverse(historyDocuments);
            (new CollectionWatcher(historyCollection, _clients)).start();
            
            synchronized(_history) {
                _history.addAll(
                    historyDocuments.stream()
                        .map(doc -> new Message((String)doc.get("address"), (String)doc.get("text")))
                        .collect(Collectors.toList()));
            }

            _initialized = true;
            System.out.println("ExampleSocket initialized");
        } catch(Throwable t) {
            System.out.println("ExampleSocket initialization failed");
            t.printStackTrace();
        }
    }

    public ExampleSocket() {
        initializeStatic();
        _mapper = new ObjectMapper();
    }

    @Override
    public void onWebSocketBinary(final byte[] payload, final int offset, final int len) {
        /* only interested in text messages */
    }

    @Override
    public void onWebSocketClose(final int statusCode, final String reason) {
        _clients.remove(_client);
        System.out.println("Removed Connection: "+_client);
        _client = null;
    }

    @Override
    public void onWebSocketConnect(final Session session) {
        if (!_initialized) {
            throw new IllegalStateException("Connection not initialized");
        }

        try {
            if (_clients.size() >= MAX_CLIENTS) {
                throw new IllegalStateException("Too many clients, max="+MAX_CLIENTS);
            }

            _client = session;
            _clients.add(_client);
            System.out.println("Accepted Connection: "+session);

            // If we have previous messages stored, send them to the client upon establishing the connection
            String json = null;
            synchronized(_history) {
                if (!_history.isEmpty()) {
                    json = _mapper.writeValueAsString(new Wrapper("history", _history));
                }
            }
            if (json != null) {
                _client.getRemote().sendString(json);
            }
        } catch(Throwable t) {
            t.printStackTrace(System.err);
        }
    }

    @Override
    public void onWebSocketError(final Throwable cause) {
        cause.printStackTrace(System.err);
    }

    @Override
    public void onWebSocketText(String text) {
        try {
            // Whenever we receive a message write it to the watched MongoDB Collection
            final MongoDatabase db = _mongoClient.getDatabase("chat");
            db.getCollection("history").insertOne(
                new Document("address", _client.getRemoteAddress().toString())
                    .append("text", text));
        } catch(Throwable t) {
            t.printStackTrace(System.err);
        }
    }

    /** 
     * Thread watching MongoDB collection for updates to broadcast to clients
     */
    private static class CollectionWatcher extends Thread {
        private final MongoCollection _collection;
        private final List<Session> _clients;
        private final ObjectMapper _mapper;

        public CollectionWatcher(final MongoCollection collection, final List<Session> clients) {
            _collection = collection;
            _clients = clients;
            _mapper = new ObjectMapper();
        }

        public void run() {
            try {
                _collection.watch().forEach(new Block<ChangeStreamDocument<Document>>() {
                    @Override
                    public void apply(final ChangeStreamDocument<Document> changeStreamDocument) {
                        try {
                            final BsonDocument resumeToken = changeStreamDocument.getResumeToken();
                            final OperationType operationType = changeStreamDocument.getOperationType();
            
                            // We only care about inserts for now -- though we should probably do something with INVALIDATE
                            if (operationType != OperationType.INSERT) {
                                return;
                            }
            
                            final Document fullDocument = (Document)changeStreamDocument.getFullDocument();
                            final String address = (String)fullDocument.get("address");
                            final String text = (String)fullDocument.get("text");
            
                            // Update cached history of recent messages
                            final Message message = new Message(address, text);
                            synchronized(_history) {
                                _history.add(message);
                                if (_history.size() > MAX_HISTORY) {
                                    _history.remove(0);
                                }
                            }

                            // Broadcast to clients
                            final String json = _mapper.writeValueAsString(new Wrapper("message", message));
                            for (final Session client : _clients) {
                                client.getRemote().sendString(json);
                            }
                        } catch(Throwable t) {
                            System.out.println("Error processing collection change operation");
                            t.printStackTrace(System.err);
                        }
                    }
                });
            } catch(Throwable t) {
                System.out.println("Error watching collection");
                t.printStackTrace(System.err);
            }
        }
    }

    // Objects for JSON Serialization

    private static class Wrapper {
        @JsonProperty
        private String type;

        @JsonProperty
        private Object data;

        public Wrapper(final String type, final Object data) {
            this.type = type;
            this.data = data;
        }
    }

    private static class Message {
        @JsonProperty
        private String address;

        @JsonProperty
        private String text;
        
        @JsonProperty
        private long time;

        public Message(final String address, final String text) {
            this.address = address;
            this.text = text;
            this.time = (new Date()).getTime();
        }
    }
}