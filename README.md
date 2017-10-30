# MongoDB Changestream Chat

Example code using Websockets and MongoDB Changestreams to implement a persisted chatroom with multiple servers.

## To Build
```
cd mongodb_changestream_chat
mvn install
mvn package
```

## To Demo
Start a local 3 node MongoDB replica set on port 3000, 3001, and 3002.

In two terminal windows startup two instances of the Java server
```
export PORT=9000; java -cp target/classes:target/dependency/* com.example.server.Main
export PORT=9001; java -cp target/classes:target/dependency/* com.example.server.Main
```

In two browser windows browse to:
http://localhost:9000 and http://localhost:9001
