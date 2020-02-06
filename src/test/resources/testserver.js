var env = process.env;
var path = env["socketioPath"] || "/socket.io";
var port = env["socketioPort"] || 5000;
var nsp = env["socketioNsp"] || "/";
var enginePort = env["engineioPort"] || 5001;

var http = null;

if(env["socketioSecure"]) {
    var fs = require("fs");
    http = require('https').createServer({
        key: fs.readFileSync(__dirname + '/key.pem'),
        cert: fs.readFileSync(__dirname + '/cert.pem')
    });
} else {
    http = require('http').createServer();
}

var io = require('socket.io')(http, {
   path: path,
});

io.on('connection', function(socket){

    console.log("Got a connection.");

    socket.on("test", function(msg) {
        console.log(msg);
        if(msg == "close")
            socket.disconnect();
        else if(msg == "error")
            socket.error("Error Packet");
        else
            socket.emit("test", msg);
    });

    socket.on("ack", function(msg, fn) {
        console.log(msg);
        fn(msg + "ack");
        socket.emit("requestAck", msg, function(ackArg) {
            console.log(ackArg);
            socket.emit("serverAckReceive", ackArg + "ack");
        });
    });

    socket.on("disconnect", function(s) {
        console.log("disconnect", s);
    });
});

if(nsp != "/") {
    io.of(nsp).on('connection', function(socket){

        console.log("Got a connection to " + nsp);

        socket.on("test", function(msg) {
          console.log(msg);
          if(msg == "close")
            socket.disconnect();
          else if(msg == "error")
            socket.error("Error Packet");
          else
            socket.emit("test", msg);
        });

        socket.on("ack", function(msg, fn) {
          console.log(msg);
          fn("Ack " + msg);
        });

        socket.on("disconnect", function(info) {
          console.log("disconnect", info);
        });
    });
}

http.listen(port, function(){
  console.log('SocketIo on port:' + port);
});

var engine = require('engine.io',).listen(enginePort, {
    pingInterval: 1000000,
    pingTimeout: 1000000,
});
console.log("EngineIO on port: " + enginePort);

engine.on('connection', function(socket){
    console.log("got engine connection.");

    socket.on("message", function(msg) {
        console.log(msg);

        if(msg == "close")
            socket.close();
        else
            socket.send(msg);
    });

    socket.on("close", function(msg) {
        console.log("close: " + msg);
    });
});