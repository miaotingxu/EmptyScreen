package com.haier.logger.behavior;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class BehaviorEventTest {

    @Test
    public void testEventBuilder() {
        Map<String, Object> data = new HashMap<>();
        data.put("key1", "value1");
        data.put("key2", 123);

        BehaviorEvent event = new BehaviorEvent.Builder()
                .category(BehaviorEvent.CATEGORY_ACTION)
                .name(BehaviorEvent.EVENT_ACTION_CLICK)
                .target("btn_submit")
                .targetText("提交")
                .data(data)
                .build();

        assertNotNull(event.getEventId());
        assertEquals(BehaviorEvent.CATEGORY_ACTION, event.getCategory());
        assertEquals(BehaviorEvent.EVENT_ACTION_CLICK, event.getName());
        assertEquals("btn_submit", event.getTarget());
        assertEquals("提交", event.getTargetText());
        assertEquals(data, event.getData());
        assertTrue(event.getTimestamp() > 0);
    }

    @Test
    public void testEventCategoryConstants() {
        assertEquals("page", BehaviorEvent.CATEGORY_PAGE);
        assertEquals("action", BehaviorEvent.CATEGORY_ACTION);
        assertEquals("business", BehaviorEvent.CATEGORY_BUSINESS);
        assertEquals("system", BehaviorEvent.CATEGORY_SYSTEM);
    }

    @Test
    public void testToMap() {
        BehaviorEvent event = new BehaviorEvent.Builder()
                .category(BehaviorEvent.CATEGORY_PAGE)
                .name(BehaviorEvent.EVENT_PAGE_OPEN)
                .pageName("MainActivity")
                .sessionId("test-session-id")
                .build();

        Map<String, Object> map = event.toMap();

        assertEquals(event.getEventId(), map.get("eventId"));
        assertEquals(BehaviorEvent.CATEGORY_PAGE, map.get("category"));
        assertEquals(BehaviorEvent.EVENT_PAGE_OPEN, map.get("name"));
        assertEquals("MainActivity", map.get("pageName"));
        assertEquals("test-session-id", map.get("sessionId"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderWithoutCategory() {
        new BehaviorEvent.Builder()
                .name("test")
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderWithoutName() {
        new BehaviorEvent.Builder()
                .category("test")
                .build();
    }

    @Test
    public void testAddData() {
        BehaviorEvent event = new BehaviorEvent.Builder()
                .category("test")
                .name("test")
                .addData("key1", "value1")
                .addData("key2", 456)
                .build();

        assertNotNull(event.getData());
        assertEquals(2, event.getData().size());
        assertEquals("value1", event.getData().get("key1"));
        assertEquals(456, event.getData().get("key2"));
    }
}