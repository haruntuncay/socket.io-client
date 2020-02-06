package common;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * <h1>common.Observable</h1>
 * Instances of this class provide a way for clients to be notified when an event that intrigues them (clients) occurs.
 * To subscribe for an <b>event</b>, use {@link #on(String, Callback)} and {@link #once(String, Callback)}.
 */
public class Observable {

    /**
     * Maps event names to a Queue of {@link Callback}.
     */
    Map<String, Queue<Callback>> callbackMap;

    protected Observable() {
        callbackMap = new HashMap<>();
    }

    /**
     * Notify the observers (if any) of the occurrence of a given event.
     *
     * @param event The name of the occured event.
     * @param args The arguments to pass to observers' callback.
     */
    public void emitEvent(String event, Object... args) {
        if(callbackMap.get(event) != null)
            callbackMap.get(event).forEach(callback -> callback.call(args));
    }

    /**
     * Register a {@link Callback} that will be called by the {@code Observable} instance, <b>every time</b> the event occurs.
     * @see #once(String, Callback) To register a Callback that will only be called once.
     *
     * @param event Name of the event the client wants to listen to.
     * @param callback {@link Callback} instance that will be called every time when the event occurs.
     * @return {@link CallbackHandle} that can be used to remove the Callback
     *          or chain further calls to {@link #on(String, Callback)} and {@link #once(String, Callback)}.
     */
    public CallbackHandle on(String event, Callback callback) {
        CallbackHandle handle = new CallbackHandle(event, callback);
        Queue<Callback> listenerQ = callbackMap.computeIfAbsent(event, k -> new LinkedList<>());
        listenerQ.add(callback);
        return handle;
    }

    /**
     * Register a {@link Callback} that will only be called <b>once</b> by the {@code Observable} instance when the event occurs.
     * The Callback will not repeat once it is fired.
     * @see #on(String, Callback) to register a recurring Callback.
     *
     * @param event Name of the event the client wants to listen to.
     * @param callback {@link Callback} instance that will be called once when the event occurs.
     * @return {@link CallbackHandle} that can be used to remove the callback
     *          or chain further calls to {@link #on(String, Callback)} and {@link #once(String, Callback)}.
     */
    public CallbackHandle once(String event, Callback callback) {
        Callback onceListener = new Callback() {
            @Override
            public void call(Object... args) {
                // Since the callback could cause the same event to occur again, by emitting the same event,
                //  we have to remove it first to make sure it won't be called more than once.
                Observable.this.removeListener(event, this);
                callback.call(args);
            }
        };

        return on(event, onceListener);
    }

    /**
     * Can be called to remove/unregister a specific Callback from the {@code Queue<Callback>} of callbacks
     *  when the client is no longer interested in the event.
     *
     * @param event Name of the event the client wants to remove a Callback from.
     * @param callback The callback to remove.
     */
    public void removeListener(String event, Callback callback) {
        if(callbackMap.get(event) != null)
            callbackMap.get(event).remove(callback);
    }

    /**
     * Remove all the Callbacks for the given event.
     *
     * @param event The event name that client is no longer interested about.
     */
    public void removeAllListenersForEvent(String event) {
        callbackMap.remove(event);
    }

    /**
     * Clears all the callbacks for all the events.
     */
    public void removeAllListeners() {
        callbackMap.clear();
    }

    /**
     * This class wraps the registered callback and event name,
     *  so that a client can have a way to remove the when he/she is no longer interested.
     * By exposing {@link CallbackHandle#on(String, Callback)} and {@link CallbackHandle#once(String, Callback)} methods,
     *  it allows clients to be able to chain multiple callback register calls.
     *
     * <p>
     * Example:
     * {@code new Observable().on("event", args -> {}).on("event2", args -> {});}
     */
    public class CallbackHandle {

        // The event name this callback was registered for.
        private String event;
        // Callback instance.
        private Callback callback;

        private CallbackHandle(String event, Callback callback) {
            this.event = event;
            this.callback = callback;
        }
        /**
         * @see Observable#on(String, Callback)
         */
        public CallbackHandle on(String event, Callback callback) {
            return Observable.this.on(event, callback);
        }

        /**
         * @see Observable#once(String, Callback)
         */
        public CallbackHandle once(String event, Callback callback) {
            return Observable.this.on(event, callback);
        }

        /**
         * Remove the callback that this instance wraps.
         */
        public void remove() {
            Observable.this.removeListener(event, callback);
        }
    }

    /**
     * Implement this interface in order to register a callback to an Observable for any interested events.
     * The {@link #call(Object...)} method will be called when the interested event occurs,
     *  along with any arguments passed by the Observable.
     */
    @FunctionalInterface
    public static interface Callback {

        /**
         * Notify any interested parties by calling this method when the event it was registered for occurs.
         *
         * @param args Any argument that Observable wants to pass to its observers.
         */
        void call(Object... args);
    }
}
