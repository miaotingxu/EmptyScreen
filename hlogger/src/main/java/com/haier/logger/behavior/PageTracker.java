package com.haier.logger.behavior;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.SystemClock;

import java.util.LinkedList;
import java.util.Map;
import java.util.WeakHashMap;

class PageTracker implements Application.ActivityLifecycleCallbacks {

    private final WeakHashMap<Activity, Long> pageEnterTime = new WeakHashMap<>();
    private final LinkedList<String> pageStack = new LinkedList<>();
    private final BehaviorProcessor behaviorProcessor;

    PageTracker(BehaviorProcessor behaviorProcessor) {
        this.behaviorProcessor = behaviorProcessor;
    }

    void start(Application application) {
        application.registerActivityLifecycleCallbacks(this);
    }

    void stop(Application application) {
        application.unregisterActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        String pageName = activity.getClass().getName();
        if (!pageStack.contains(pageName)) {
            pageStack.add(pageName);
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
        String pageName = activity.getClass().getName();
        long enterTime = SystemClock.elapsedRealtime();
        pageEnterTime.put(activity, enterTime);

        String prevPage = pageStack.size() > 1 ? pageStack.get(pageStack.size() - 2) : null;

        BehaviorEvent enterEvent = new BehaviorEvent.Builder()
                .category(BehaviorEvent.CATEGORY_PAGE)
                .name(BehaviorEvent.EVENT_PAGE_ENTER)
                .pageName(pageName)
                .prevPageName(prevPage)
                .build();

        behaviorProcessor.processEvent(enterEvent);
    }

    @Override
    public void onActivityPaused(Activity activity) {
        String pageName = activity.getClass().getName();
        Long enterTime = pageEnterTime.get(activity);
        if (enterTime != null) {
            long duration = SystemClock.elapsedRealtime() - enterTime;

            String prevPage = pageStack.size() > 1 ? pageStack.get(pageStack.size() - 2) : null;

            BehaviorEvent leaveEvent = new BehaviorEvent.Builder()
                    .category(BehaviorEvent.CATEGORY_PAGE)
                    .name(BehaviorEvent.EVENT_PAGE_LEAVE)
                    .pageName(pageName)
                    .prevPageName(prevPage)
                    .duration(duration)
                    .build();

            behaviorProcessor.processEvent(leaveEvent);
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        String pageName = activity.getClass().getName();
        pageStack.remove(pageName);
        pageEnterTime.remove(activity);

        BehaviorEvent closeEvent = new BehaviorEvent.Builder()
                .category(BehaviorEvent.CATEGORY_PAGE)
                .name(BehaviorEvent.EVENT_PAGE_CLOSE)
                .pageName(pageName)
                .build();

        behaviorProcessor.processEvent(closeEvent);
    }

    String getCurrentPage() {
        return pageStack.isEmpty() ? null : pageStack.getLast();
    }

    String getPreviousPage() {
        if (pageStack.size() < 2) {
            return null;
        }
        return pageStack.get(pageStack.size() - 2);
    }

    LinkedList<String> getPageStack() {
        return new LinkedList<>(pageStack);
    }
}