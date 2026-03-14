package com.haier.emptyscreen.utils;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

public class FocusUtils {
    
    public static void setupTVFocus(View view) {
        if (view == null) return;
        
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setClickable(true);
    }
    
    public static void setupTVFocus(ViewGroup viewGroup) {
        if (viewGroup == null) return;
        
        viewGroup.setFocusable(true);
        viewGroup.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
    }
    
    public static void scrollToFocusedView(ScrollView scrollView, View focusedView) {
        if (scrollView == null || focusedView == null) return;
        
        final View viewToScroll = focusedView;
        scrollView.post(() -> {
            int scrollY = viewToScroll.getTop();
            int viewHeight = viewToScroll.getHeight();
            int scrollViewHeight = scrollView.getHeight();
            
            int[] location = new int[2];
            viewToScroll.getLocationOnScreen(location);
            scrollView.getLocationOnScreen(location);
            
            int currentScrollY = scrollView.getScrollY();
            int targetScrollY = viewToScroll.getTop() - (scrollViewHeight / 2) + (viewHeight / 2);
            
            if (targetScrollY < 0) targetScrollY = 0;
            
            scrollView.smoothScrollTo(0, targetScrollY);
        });
    }
    
    public static void setFocusScale(View view, float scale) {
        if (view == null) return;
        view.setScaleX(scale);
        view.setScaleY(scale);
    }
    
    public static void applyFocusAnimation(View view, boolean hasFocus) {
        if (view == null) return;
        
        final float scale = hasFocus ? 1.05f : 1.0f;
        final long duration = 150;
        
        view.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(duration)
            .start();
    }
}
