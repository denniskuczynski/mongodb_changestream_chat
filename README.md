# MongoDB Changestream Chat

Quick proof of concept using Websockets and MongoDB Changestreams to implement a persisted chatroom with multiple servers.

## To Build
`cd mongodb_changestream_chat`
mvn install
mvn package

## To Demo
In two terminal windows startup two instances of the Java server
`export PORT=9001; java -cp target/classes:target/dependency/* com.example.server.Main`
`export PORT=9001; java -cp target/classes:target/dependency/* com.example.server.Main`

In two browser windows browse to:
http://localhost:9000 and http://localhost:9001
