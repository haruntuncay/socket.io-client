# Socket.IO Client
This repository contains a Socket.IO client implementation written in Java language.
If you don't know about socket.io, it's basically a framework that aims to provide real-time, bidirectional and event-based communication. You can head over to [socket.io website](https://socket.io/) to learn more about it.

## Installation
You can install this library using the following:
```
<dependency>
    <groupId>com.github.haruntuncay</groupId>
    <artifactId>socket.io-client</artifactId>
    <version>1.0</version>
</dependency>
```

## Usage and API
This library uses `socketio_client.Socket` instances to expose its client api.
You can get an instance of one through `socketio_client.IO` builder class.
A basic example:
```
// Create and configure the socket instance.
Socket socket = IO.of("http://localhost:3000/")
                  .socket();

// Register for available callbacks.
socket.on(Socket.CONNECT, argv -> {...});
socket.on("eventName", argv -> {...});

// Open the connection.
socket.open();

// Emit "eventName" to server event with "hello" as data.
socket.emit("eventName", "hello");
```
Check out the [wiki](https://github.com/haruntuncay/socket.io-client/wiki) for a detailed explanation on usage and api of this library.

## License
MIT license.


## Contributing
Contributions are appreciated, but before submitting any contributions, please open an issue first.
You can checkout [inner details](https://github.com/haruntuncay/socket.io-client/wiki/Inner-Details) wiki page for implementation information.
