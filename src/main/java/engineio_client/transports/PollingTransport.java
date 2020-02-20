package engineio_client.transports;

import engineio_client.Config;
import engineio_client.parser.Packet;
import engineio_client.parser.Type;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static common.Worker.submit;

/**
 * Manages a poll/push cycle. Since polling transports pass payloads(array of packets) to each other,
 *  we can buffer any sent requested buffers until the current write operation finished, and them send them all at once.
 * Same principle applies to servers as well. They buffer any sent requested data and push it to clients as a whole when they poll.
 */
public class PollingTransport extends Transport {

    private final Logger logger = LoggerFactory.getLogger(PollingTransport.class);

    public static final String NAME = "polling";
    private static final MediaType BINARY_MEDIA_TYPE = MediaType.parse("application/octet-stream");
    private static final MediaType TEXT_MEDIA_TYPE = MediaType.parse("text/plain; charset=UTF-8");

    /*
       Indicated whether there is currently a write operating in progress or not.
        (If true, it means there is no current write operation in progress)
       Starts out as false because the transport is not open at first, but once the open packet is received,
        becomes true and any buffered data is flushed.
    */
    private volatile boolean writeChannelAvailable;
    // Indicates whether there is currently a poll operation is in progress or not. True if there is no poll in progress.
    private volatile boolean pollChannelAvailable;
    private Call.Factory callFactory;
    private List<Runnable> bufferedOnWriteCallbacks;

    public PollingTransport(Config configuration) {
        super(configuration);
        callFactory = configuration.callFactory;
        pollChannelAvailable = true;
        bufferedOnWriteCallbacks = new LinkedList<>();
    }

    @Override
    public void open() {
        if(!isOpen())
            poll();
    }

    @Override
    public void close(boolean isCloseClientInitiated) {
        if(isCloseClientInitiated)
            send(Packet.CLOSE, this::onClose);
        else
            onClose();
    }

    /**
     * Request a send operation for the given packet.
     * The packet is put into a buffer, where they all will be written out once the current write operation completes, if any.
     *
     * @param packet Packet to write out.
     */
    @Override
    public void send(Packet packet) {
        send(packet, null);
    }

    private void send(Packet packet, Runnable onWriteCallback) {
        if(state == State.CLOSED)
            return;

        sendBuffer.offer(packet);
        if(writeChannelAvailable) {
            submit(() -> {
                parser.encodePayload(sendBuffer.toArray(new Packet[]{}), encodedData -> doPostRequest(encodedData, onWriteCallback));
                sendBuffer.clear();
            });
        } else
            if(onWriteCallback != null)
                bufferedOnWriteCallbacks.add(onWriteCallback);
    }

    /**
     * Flush the current contents of outgoing packet buffer.
     */
    @Override
    public void flush() {
        // Force a "send" cycle to flush buffered outgoing packets.
        if(sendBuffer.size() > 0)
            send(Packet.NOOP);
    }

    private void poll() {
        if(state == State.CLOSED)
            return;
        doGetRequest();
    }

    private void doPostRequest(Object data) {
        doPostRequest(data, null);
    }

    private void doPostRequest(Object data, Runnable onWriteCallback) {
        submit(() -> handleRequest(data, onWriteCallback));
    }

    private void doGetRequest() {
        submit(() -> handleRequest(null));
    }

    private void handleRequest(Object data) {
        handleRequest(data, null);
    }

    /*
        If there is currently an operation of same type (GET/POST) is in progress, skip.
        Otherwise;
        Create an HTTP request, either GET or POST depending on the existence of "data" parameter.
        If it's a post request, put the necessary content type header.
        Put the headers in config.headerMap in either case.
        Mark the respective channel (read or write) unavailable depending on the request type.
        Build the request object and enqueue it.
     */
    private void handleRequest(Object data, Runnable onWriteCallback) {
        boolean isPostRequest = data != null;

        if(!isPostRequest && !pollChannelAvailable)
            return;
        else if(isPostRequest && !writeChannelAvailable)
            return;

        Request.Builder requestBuilder = new Request.Builder()
                                                    .url(buildURLString());

        if(config.headerMap != null)
            config.headerMap.forEach(requestBuilder::addHeader);

        if(isPostRequest) {
            if(data instanceof byte[])
                requestBuilder.post(RequestBody.create(BINARY_MEDIA_TYPE, (byte[]) data));
            else
                requestBuilder.post(RequestBody.create(TEXT_MEDIA_TYPE, (String) data));

            writeChannelAvailable = false;
        } else {
            pollChannelAvailable = false;
        }

        Request request = requestBuilder.build();
        PollingTransport pollingTp = this;

        callFactory.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // Connection failed due to a network problem. Retrying at a later time could establish a successful connection.
                // Since closeAbruptly is followed by a retry (if user didn't choose otherwise), choose it in case of connection exception.
                // Choose erroneous closing otherwise.
                if(e instanceof ConnectException || e instanceof SocketTimeoutException) {
                    logger.error("Abrupt close during {} request.", isPostRequest ? "post" : "poll", e);
                    closeAbruptly("Connection exception during " + (isPostRequest ? "post" : "poll") + " request.", e);
                } else {
                    logger.error("Error during {} request.", isPostRequest ? "post" : "poll", e);
                    handleError("An error occurred.", e);
                }
            }

            @Override
            public void onResponse(Call call, Response response) {
               try(ResponseBody responseBody = response.body()) {
                   // Request has terminated with an error or an unrecoverable condition,
                   //   or the Socket.IO server refused(status code 403) the client.
                   if(!response.isSuccessful()) {
                       logger.error("Response error during {} request. Response: {}", isPostRequest ? "post" : "poll", response);
                       handleError((isPostRequest ? "Post" : "Poll") + " request failed. Response: " + response);
                       return;
                   }

                   MediaType type = responseBody.contentType();
                   if(BINARY_MEDIA_TYPE.equals(type)) {
                       byte[] bytes = responseBody.bytes();
                       submit(() -> parser.decodePayload(bytes, pollingTp::onPacket));
                   } else if(TEXT_MEDIA_TYPE.equals(type)) {
                       String data = responseBody.string();
                       submit(() -> parser.decodePayload(data, pollingTp::onPacket));
                   }
               } catch (IOException e) {
                   logger.error("Error while reading response.", e);
                   closeAbruptly("Error while retrieving the response body.", e);
               } finally {
                   if(state != State.ABRUPTLY_CLOSED) {
                       if(isPostRequest) {
                           writeChannelAvailable = true;
                           pollingTp.onWriteComplete();
                       } else {
                           pollChannelAvailable = true;
                           pollingTp.onPollComplete();
                       }
                   }
               }
            }
        });

        if(isPostRequest) {
            bufferedOnWriteCallbacks.forEach(Runnable::run);
            bufferedOnWriteCallbacks.clear();
            if(onWriteCallback != null)
                onWriteCallback.run();
        }
    }

    private void onPacket(Packet packet) {
        if(packet.getType() == Type.OPEN) {
            onOpenPacket(packet);
            writeChannelAvailable = true;
            flush();
        } else
            emitEvent(Transport.PACKET, packet);
    }

    private void onClose() {
        state = State.CLOSED;
        pollChannelAvailable = false;
        writeChannelAvailable = false;
        sendBuffer.clear();
        emitEvent(CLOSE);
        removeAllListeners();
    }

    private void onPollComplete() {
        poll();
    }

    private void onWriteComplete() {
        if(state == State.CLOSED) {
            writeChannelAvailable = false;
            sendBuffer.clear();
            return;
        }

        if(isOpen())
            flush();
    }

    @Override
    void closeAbruptly(String message, Throwable throwable) {
        state = State.ABRUPTLY_CLOSED;
        sendBuffer.clear();
        pollChannelAvailable = true;
        writeChannelAvailable = true;
        if(throwable != null)
            emitEvent(Transport.ABRUPT_CLOSE, message, throwable);
        else
            emitEvent(Transport.ABRUPT_CLOSE, message);
        removeAllListeners();
    }

    @Override
    void handleError(String reason, Throwable throwable) {
        state = State.CLOSED;
        pollChannelAvailable = false;
        writeChannelAvailable = false;
        sendBuffer.clear();
        emitEvent(Transport.ERROR, reason, throwable);
        removeAllListeners();
    }

    /**
     * Called right after a successful WebSocket probe in order to close the old transport(this one) and move to a new one.
     */
    public void pause() {
        while(!writeChannelAvailable)
            ; // spin until write channel is empty.
        writeChannelAvailable = false;
    }

    public void unPause() {
        writeChannelAvailable = true;
        flush();
    }

    public Queue<Packet> getBufferedPackets() {
        return sendBuffer;
    }
}
