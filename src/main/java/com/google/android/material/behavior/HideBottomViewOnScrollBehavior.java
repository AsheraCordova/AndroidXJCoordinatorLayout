//start - license
/*
 * Copyright (c) 2025 Ashera Cordova
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
//end - license
/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.material.behavior;
import r.android.animation.TimeInterpolator;
import r.android.view.View;
import r.android.view.ViewGroup;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import java.util.LinkedHashSet;
public class HideBottomViewOnScrollBehavior<V extends View> extends CoordinatorLayout.Behavior<V> {
  private final LinkedHashSet<OnScrollStateChangedListener> onScrollStateChangedListeners=new LinkedHashSet<>();
  private static final int DEFAULT_ENTER_ANIMATION_DURATION_MS=225;
  private static final int DEFAULT_EXIT_ANIMATION_DURATION_MS=175;
  private int enterAnimDuration;
  private int exitAnimDuration;
  private TimeInterpolator enterAnimInterpolator;
  private TimeInterpolator exitAnimInterpolator;
  public static final int STATE_SCROLLED_DOWN=1;
  public static final int STATE_SCROLLED_UP=2;
  private int height=0;
  private int currentState=STATE_SCROLLED_UP;
  private int additionalHiddenOffsetY=0;
  public boolean onLayoutChild(  CoordinatorLayout parent,  V child,  int layoutDirection){
    ViewGroup.MarginLayoutParams paramsCompat=(ViewGroup.MarginLayoutParams)child.getLayoutParams();
    height=child.getMeasuredHeight() + paramsCompat.bottomMargin;
    enterAnimDuration=(DEFAULT_ENTER_ANIMATION_DURATION_MS);
    exitAnimDuration=(DEFAULT_EXIT_ANIMATION_DURATION_MS);
    enterAnimInterpolator=new androidx.interpolator.view.animation.LinearOutSlowInInterpolator();//MotionUtils.resolveThemeInterpolator(child.getContext(),ENTER_EXIT_ANIM_EASING_ATTR,AnimationUtils.LINEAR_OUT_SLOW_IN_INTERPOLATOR);
    exitAnimInterpolator=new androidx.interpolator.view.animation.FastOutLinearInInterpolator();//MotionUtils.resolveThemeInterpolator(child.getContext(),ENTER_EXIT_ANIM_EASING_ATTR,AnimationUtils.FAST_OUT_LINEAR_IN_INTERPOLATOR);
    return super.onLayoutChild(parent,child,layoutDirection);
  }
  public void setAdditionalHiddenOffsetY(  V child,  int offset){
    additionalHiddenOffsetY=offset;
    if (currentState == STATE_SCROLLED_DOWN) {
      child.setTranslationY(height + additionalHiddenOffsetY);
    }
  }
  public boolean onStartNestedScroll(  CoordinatorLayout coordinatorLayout,  V child,  View directTargetChild,  View target,  int nestedScrollAxes,  int type){
    return nestedScrollAxes == ViewCompat.SCROLL_AXIS_VERTICAL;
  }
  public void onNestedScroll(  CoordinatorLayout coordinatorLayout,  V child,  View target,  int dxConsumed,  int dyConsumed,  int dxUnconsumed,  int dyUnconsumed,  int type,  int[] consumed){
    if (dyConsumed > 0) {
      slideDown(child);
    }
 else     if (dyConsumed < 0) {
      slideUp(child);
    }
  }
  public boolean isScrolledUp(){
    return currentState == STATE_SCROLLED_UP;
  }
  public void slideUp(  V child){
    slideUp(child,true);
  }
  public void slideUp(  V child,  boolean animate){
    if (isScrolledUp()) {
      return;
    }
    if (currentAnimator != null) {
      currentAnimator.cancel();
      //child.clearAnimation();
    }
    updateCurrentState(child,STATE_SCROLLED_UP);
    int targetTranslationY=0;
    if (animate) {
      animateChildTo(child,targetTranslationY,enterAnimDuration,enterAnimInterpolator);
    }
 else {
      child.setTranslationY(targetTranslationY);
    }
  }
  public boolean isScrolledDown(){
    return currentState == STATE_SCROLLED_DOWN;
  }
  public void slideDown(  V child){
    slideDown(child,true);
  }
  public void slideDown(  V child,  boolean animate){
    if (isScrolledDown()) {
      return;
    }
    if (currentAnimator != null) {
      currentAnimator.cancel();
      //child.clearAnimation();
    }
    updateCurrentState(child,STATE_SCROLLED_DOWN);
    int targetTranslationY=height + additionalHiddenOffsetY;
    if (animate) {
      animateChildTo(child,targetTranslationY,exitAnimDuration,exitAnimInterpolator);
    }
 else {
      child.setTranslationY(targetTranslationY);
    }
  }
  private void updateCurrentState(  V child,  int state){
    currentState=state;
    for (    OnScrollStateChangedListener listener : onScrollStateChangedListeners) {
      listener.onStateChanged(child,currentState);
    }
  }
  private r.android.animation.Animator currentAnimator;
  private void animateChildTo(  V child,  int targetY,  long duration,  TimeInterpolator interpolator){
    currentAnimator=r.android.animation.ObjectAnimator.ofFloat(child,"translationY",0f,targetY);
    currentAnimator.setDuration(duration);
    currentAnimator.setInterpolator(interpolator);
    currentAnimator.addListener(new r.android.animation.AnimatorListenerAdapter(){
      @Override public void onAnimationEnd(      r.android.animation.Animator animation){
        currentAnimator=null;
      }
    }
);
    currentAnimator.start();
  }
static class OnScrollStateChangedListener<V> {
    public void onStateChanged(    V child,    int currentState){
    }
  }
}
