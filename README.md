# AKKA - textual-based WhatsApp

## Project Information

In this project we will be implementing a textual-based WhatsApp!
It will contain most of the features of the application.
It imeplements Akka, using Java.

## Project Structure

We seperated in to:
1. client - has main class, get 2 arguments - ip & port
2. server - has main class
3. common - contains classes definitions and messages

Running instrunction can be found at the end of the file.

## Server Actor

### The server implements an instance of actor.

The server holds 2 hashmaps:

1. the connected users - user name mapped to its actorRef,
containing the connected users.
2. the existing groups - group name mapped to its Group instance, containg the information of existing groups

The server receives commands from Client which includes all the required information of a request.
The server analyse the command relevant predicates.

## Client Actor

### Each client implements instance of actor.

The client class holds 3 members:

1. user name
2. user ip
3. server actor
   When a client wants to connect another client, it first will ask its actor
   from the server, and then will be able to send a message.

A client has 2 behaviours, connected and disconnected.

1. The client actor receives messages from the stdin console, the server actor or another client.
2. Messages from the stdin are parsed from string to the correct message/request class and are sent to the client Actor.
3. The client actor receives parsed messages from the user stdin, then the client actor handles the command depends on the command type and a matching predicate. Handling the command made by sending the command, using akka ask, to the server after manipulate it and wait to result command from the server.
4. While getting the result command from the server, the client actor works according to the result - reply os notification. The client actor will print to the screen a message according to a succsus or failure of the command.

## Requests, Replies & Notifications

### We seperated the type of messages to:

1. Message
   This type of message is a message that contains conetent.
   it can be one of the following:

- text message from user to user
- text message from user to group
- file message from user to user
- file message from user to group

2. Requests
   This type of message is any request that a user send to a server
3. Replies
   This type of message is any active reply from the server to a user that waits
   for reply after sending a request to the server.
4. Notifications
   This type of message is any passive response from the server to a user that
   should be notified for something.

## Group Class

### A group class holds the following members:

1. groupName - the name of the group
2. admin - the name of the admin group
3. coAdmins - list of co-admins of the group
4. users - list of users of the group
5. mutedUsers - map of muted users
6. router - router of the group

## MutedUser Class

### A muted user holds the following members:

1. username - username of muted user
2. startTime- start time of mute
3. interval- interval time to be muted
4. Cancellable cancelable - an akka object which can be defined by limit of time


## Run

### set up

```
cd AKKA
mvn install
mvn compile

cd common
mvn install
mvn compile

cd ..
```

### turn on the server

```
cd server
mvn install
mvn compile
mvn exec:java
```

### turn on client - example

```
cd client
mvn install
mvn compile
mvn exec:java -Dip=127.0.0.1 -Dport=8080
```

for any client

```
mvn exec:java -Dip=<ip> -Dport=<port>
```
