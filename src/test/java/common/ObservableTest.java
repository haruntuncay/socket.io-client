package common;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import common.Observable.Callback;
import common.Observable.CallbackHandle;

import static org.junit.Assert.*;

public class ObservableTest {

    private static Observable observable = new Observable();

    @Before
    public void beforeEach() {
       observable.removeAllListeners();
    }

    @Test
    public void testEmitWithArgs() {
        String strObj = "obj";
        Integer intObj = 1;
        Object testObj = new Object();
        List<Object> list = new LinkedList<>();

        observable.on("emitWithArgsTest", args -> Arrays.stream(args).forEach(list::add));
        observable.emitEvent("emitWithArgsTest", strObj, intObj, testObj);

        assertEquals(list, Arrays.asList(strObj, intObj, testObj));
    }

    @Test
    public void testEmitWithNoArgsAndNullArgs() {
        observable.on("emitWithNoArgsAndNullArgsTest", args -> {
                                                assertArrayEquals(args, new Object[0]);
                                                assertNotNull(args);
                                            });
        observable.emitEvent("emitWithNoArgsAndNullArgsTest");

        observable.on("nullTest", Assert::assertNull);
        observable.emitEvent("nullTest", null);
    }

    @Test
    public void testOn() {
        Callback cb1 = args -> {};
        Callback cb2 = args -> {};

        observable.on("onTest", cb1);
        observable.on("onTest", cb2);

        assertEquals(observable.callbackMap.get("onTest"), Arrays.asList(cb1, cb2));
    }

    @Test
    public void testOnce() {
        int[] arr = {0};
        observable.once("onceTest", args -> arr[0]++);

        assertEquals(observable.callbackMap.get("onceTest").size(), 1);
        observable.emitEvent("onceTest");
        assertEquals(arr[0], 1);

        assertEquals(observable.callbackMap.get("onceTest").size(), 0);
        observable.emitEvent("onceTest");
        assertEquals(arr[0], 1);
    }

    @Test
    public void testRemoveListener() {
        Callback cb = args -> {};
        Callback cb2 = args -> {};

        observable.on("removeListenerTest", cb);
        observable.on("removeListenerTest", cb2);
        Queue<Callback> callbackQ = observable.callbackMap.get("removeListenerTest");

        assertTrue(callbackQ.contains(cb));
        assertTrue(callbackQ.contains(cb2));

        observable.removeListener("removeListenerTest", cb);
        assertFalse(callbackQ.contains(cb));
        assertTrue(callbackQ.contains(cb2));

        observable.on("removeListenerTest", cb);
        observable.removeAllListeners();
        assertNull(observable.callbackMap.get("removeListenerTest"));
    }

    @Test
    public void testCallbackHandleRemove() {
        Callback cb = args -> {};
        CallbackHandle handle = observable.on("callbackHandleRemoveTest", cb);
        assertTrue(observable.callbackMap.get("callbackHandleRemoveTest").contains(cb));

        handle.remove();
        assertFalse(observable.callbackMap.get("callbackHandleRemoveTest").contains(cb));
    }
}
