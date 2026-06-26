package com.haier.logger.behavior;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class SessionManagerTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void testGetSessionId() {
        SessionManager manager = new SessionManager(context);
        
        String sessionId = manager.getSessionId();
        
        assertNotNull(sessionId);
        assertFalse(sessionId.isEmpty());
    }

    @Test
    public void testSessionIdConsistency() {
        SessionManager manager = new SessionManager(context);
        
        String sessionId1 = manager.getSessionId();
        String sessionId2 = manager.getSessionId();
        
        assertEquals(sessionId1, sessionId2);
    }

    @Test
    public void testSetUserId() {
        SessionManager manager = new SessionManager(context);
        String testUserId = "user123";
        
        manager.setUserId(testUserId);
        
        assertEquals(testUserId, manager.getUserId());
    }

    @Test
    public void testSetUserType() {
        SessionManager manager = new SessionManager(context);
        String testUserType = "vip";
        
        manager.setUserType(testUserType);
        
        assertEquals(testUserType, manager.getUserType());
    }

    @Test
    public void testCreateNewSession() {
        SessionManager manager = new SessionManager(context);
        
        String sessionId1 = manager.getSessionId();
        manager.createNewSession();
        String sessionId2 = manager.getSessionId();
        
        assertNotEquals(sessionId1, sessionId2);
    }

    @Test
    public void testSessionDuration() {
        SessionManager manager = new SessionManager(context);
        
        manager.createNewSession();
        long duration1 = manager.getSessionDuration();
        
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        long duration2 = manager.getSessionDuration();
        
        assertTrue(duration2 > duration1);
        assertTrue(duration2 >= 100);
    }
}