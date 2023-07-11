package androidx.coordinatorlayout.widget;
import r.android.graphics.Rect;
import r.android.graphics.drawable.Drawable;
import r.android.util.Log;
import r.android.view.Gravity;
import r.android.view.View;
import r.android.view.ViewGroup;
import r.android.view.ViewParent;
import r.android.util.Pools;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class CoordinatorLayout extends ViewGroup {
  static final String TAG="CoordinatorLayout";
  private static final int TYPE_ON_INTERCEPT=0;
  private static final int TYPE_ON_TOUCH=1;
  static final int EVENT_PRE_DRAW=0;
  static final int EVENT_NESTED_SCROLL=1;
  static final int EVENT_VIEW_REMOVED=2;
  private static final Pools.Pool<Rect> sRectPool=new Pools.SynchronizedPool<>(12);
  private static Rect acquireTempRect(){
    Rect rect=sRectPool.acquire();
    if (rect == null) {
      rect=new Rect();
    }
    return rect;
  }
  private static void releaseTempRect(  Rect rect){
    rect.setEmpty();
    sRectPool.release(rect);
  }
  private final List<View> mDependencySortedChildren=new ArrayList<>();
  private final DirectedAcyclicGraph<View> mChildDag=new DirectedAcyclicGraph<>();
  private boolean mDisallowInterceptReset;
  private boolean mIsAttachedToWindow;
  private int[] mKeylines;
  private boolean mNeedsPreDrawListener;
  private WindowInsetsCompat mLastInsets;
  private boolean mDrawStatusBarBackground;
  private int getKeyline(  int index){
    if (mKeylines == null) {
      Log.e(TAG,"No keylines defined for " + this + " - attempted index lookup "+ index);
      return 0;
    }
    if (index < 0 || index >= mKeylines.length) {
      Log.e(TAG,"Keyline index " + index + " out of range for "+ this);
      return 0;
    }
    return mKeylines[index];
  }
  private void prepareChildren(){
    mDependencySortedChildren.clear();
    mChildDag.clear();
    for (int i=0, count=getChildCount(); i < count; i++) {
      final View view=getChildAt(i);
      final LayoutParams lp=getResolvedLayoutParams(view);
      lp.findAnchorView(this,view);
      mChildDag.addNode(view);
      for (int j=0; j < count; j++) {
        if (j == i) {
          continue;
        }
        final View other=getChildAt(j);
        if (lp.dependsOn(this,view,other)) {
          if (!mChildDag.contains(other)) {
            mChildDag.addNode(other);
          }
          mChildDag.addEdge(other,view);
        }
      }
    }
    mDependencySortedChildren.addAll(mChildDag.getSortedList());
    Collections.reverse(mDependencySortedChildren);
  }
  void getDescendantRect(  View descendant,  Rect out){
    ViewGroupUtils.getDescendantRect(this,descendant,out);
  }
  public void onMeasureChild(  View child,  int parentWidthMeasureSpec,  int widthUsed,  int parentHeightMeasureSpec,  int heightUsed){
    measureChildWithMargins(child,parentWidthMeasureSpec,widthUsed,parentHeightMeasureSpec,heightUsed);
  }
  protected void onMeasure(  int widthMeasureSpec,  int heightMeasureSpec){
    prepareChildren();
    ensurePreDrawListener();
    final int paddingLeft=getPaddingLeft();
    final int paddingTop=getPaddingTop();
    final int paddingRight=getPaddingRight();
    final int paddingBottom=getPaddingBottom();
    final int layoutDirection=ViewCompat.getLayoutDirection(this);
    final boolean isRtl=layoutDirection == ViewCompat.LAYOUT_DIRECTION_RTL;
    final int widthMode=MeasureSpec.getMode(widthMeasureSpec);
    final int widthSize=MeasureSpec.getSize(widthMeasureSpec);
    final int heightMode=MeasureSpec.getMode(heightMeasureSpec);
    final int heightSize=MeasureSpec.getSize(heightMeasureSpec);
    final int widthPadding=paddingLeft + paddingRight;
    final int heightPadding=paddingTop + paddingBottom;
    int widthUsed=getSuggestedMinimumWidth();
    int heightUsed=getSuggestedMinimumHeight();
    int childState=0;
    final boolean applyInsets=mLastInsets != null && ViewCompat.getFitsSystemWindows(this);
    final int childCount=mDependencySortedChildren.size();
    for (int i=0; i < childCount; i++) {
      final View child=mDependencySortedChildren.get(i);
      if (child.getVisibility() == GONE) {
        continue;
      }
      final LayoutParams lp=(LayoutParams)child.getLayoutParams();
      int keylineWidthUsed=0;
      if (lp.keyline >= 0 && widthMode != MeasureSpec.UNSPECIFIED) {
        final int keylinePos=getKeyline(lp.keyline);
        final int keylineGravity=GravityCompat.getAbsoluteGravity(resolveKeylineGravity(lp.gravity),layoutDirection) & Gravity.HORIZONTAL_GRAVITY_MASK;
        if ((keylineGravity == Gravity.LEFT && !isRtl) || (keylineGravity == Gravity.RIGHT && isRtl)) {
          keylineWidthUsed=Math.max(0,widthSize - paddingRight - keylinePos);
        }
 else         if ((keylineGravity == Gravity.RIGHT && !isRtl) || (keylineGravity == Gravity.LEFT && isRtl)) {
          keylineWidthUsed=Math.max(0,keylinePos - paddingLeft);
        }
      }
      int childWidthMeasureSpec=widthMeasureSpec;
      int childHeightMeasureSpec=heightMeasureSpec;
      if (applyInsets && !ViewCompat.getFitsSystemWindows(child)) {
        final int horizInsets=mLastInsets.getSystemWindowInsetLeft() + mLastInsets.getSystemWindowInsetRight();
        final int vertInsets=mLastInsets.getSystemWindowInsetTop() + mLastInsets.getSystemWindowInsetBottom();
        childWidthMeasureSpec=MeasureSpec.makeMeasureSpec(widthSize - horizInsets,widthMode);
        childHeightMeasureSpec=MeasureSpec.makeMeasureSpec(heightSize - vertInsets,heightMode);
      }
      final Behavior b=lp.getBehavior();
      if (b == null || !b.onMeasureChild(this,child,childWidthMeasureSpec,keylineWidthUsed,childHeightMeasureSpec,0)) {
        onMeasureChild(child,childWidthMeasureSpec,keylineWidthUsed,childHeightMeasureSpec,0);
      }
      widthUsed=Math.max(widthUsed,widthPadding + child.getMeasuredWidth() + lp.leftMargin+ lp.rightMargin);
      heightUsed=Math.max(heightUsed,heightPadding + child.getMeasuredHeight() + lp.topMargin+ lp.bottomMargin);
      childState=View.combineMeasuredStates(childState,child.getMeasuredState());
    }
    final int width=View.resolveSizeAndState(widthUsed,widthMeasureSpec,childState & View.MEASURED_STATE_MASK);
    final int height=View.resolveSizeAndState(heightUsed,heightMeasureSpec,childState << View.MEASURED_HEIGHT_STATE_SHIFT);
    setMeasuredDimension(width,height);
  }
  public void onLayoutChild(  View child,  int layoutDirection){
    final LayoutParams lp=(LayoutParams)child.getLayoutParams();
    if (lp.checkAnchorChanged()) {
      throw new IllegalStateException("An anchor may not be changed after CoordinatorLayout" + " measurement begins before layout is complete.");
    }
    if (lp.mAnchorView != null) {
      layoutChildWithAnchor(child,lp.mAnchorView,layoutDirection);
    }
 else     if (lp.keyline >= 0) {
      layoutChildWithKeyline(child,lp.keyline,layoutDirection);
    }
 else {
      layoutChild(child,layoutDirection);
    }
  }
  protected void onLayout(  boolean changed,  int l,  int t,  int r,  int b){
    final int layoutDirection=ViewCompat.getLayoutDirection(this);
    final int childCount=mDependencySortedChildren.size();
    for (int i=0; i < childCount; i++) {
      final View child=mDependencySortedChildren.get(i);
      if (child.getVisibility() == GONE) {
        continue;
      }
      final LayoutParams lp=(LayoutParams)child.getLayoutParams();
      final Behavior behavior=lp.getBehavior();
      if (behavior == null || !behavior.onLayoutChild(this,child,layoutDirection)) {
        onLayoutChild(child,layoutDirection);
      }
    }
  }
  void recordLastChildRect(  View child,  Rect r){
    final LayoutParams lp=(LayoutParams)child.getLayoutParams();
    lp.setLastChildRect(r);
  }
  void getLastChildRect(  View child,  Rect out){
    final LayoutParams lp=(LayoutParams)child.getLayoutParams();
    out.set(lp.getLastChildRect());
  }
  void getChildRect(  View child,  boolean transform,  Rect out){
    if (child.isLayoutRequested() || child.getVisibility() == View.GONE) {
      out.setEmpty();
      return;
    }
    if (transform) {
      getDescendantRect(child,out);
    }
 else {
      out.set(child.getLeft(),child.getTop(),child.getRight(),child.getBottom());
    }
  }
  private void getDesiredAnchoredChildRectWithoutConstraints(  int layoutDirection,  Rect anchorRect,  Rect out,  LayoutParams lp,  int childWidth,  int childHeight){
    final int absGravity=GravityCompat.getAbsoluteGravity(resolveAnchoredChildGravity(lp.gravity),layoutDirection);
    final int absAnchorGravity=GravityCompat.getAbsoluteGravity(resolveGravity(lp.anchorGravity),layoutDirection);
    final int hgrav=absGravity & Gravity.HORIZONTAL_GRAVITY_MASK;
    final int vgrav=absGravity & Gravity.VERTICAL_GRAVITY_MASK;
    final int anchorHgrav=absAnchorGravity & Gravity.HORIZONTAL_GRAVITY_MASK;
    final int anchorVgrav=absAnchorGravity & Gravity.VERTICAL_GRAVITY_MASK;
    int left;
    int top;
switch (anchorHgrav) {
default :
case Gravity.LEFT:
      left=anchorRect.left;
    break;
case Gravity.RIGHT:
  left=anchorRect.right;
break;
case Gravity.CENTER_HORIZONTAL:
left=anchorRect.left + anchorRect.width() / 2;
break;
}
switch (anchorVgrav) {
default :
case Gravity.TOP:
top=anchorRect.top;
break;
case Gravity.BOTTOM:
top=anchorRect.bottom;
break;
case Gravity.CENTER_VERTICAL:
top=anchorRect.top + anchorRect.height() / 2;
break;
}
switch (hgrav) {
default :
case Gravity.LEFT:
left-=childWidth;
break;
case Gravity.RIGHT:
break;
case Gravity.CENTER_HORIZONTAL:
left-=childWidth / 2;
break;
}
switch (vgrav) {
default :
case Gravity.TOP:
top-=childHeight;
break;
case Gravity.BOTTOM:
break;
case Gravity.CENTER_VERTICAL:
top-=childHeight / 2;
break;
}
out.set(left,top,left + childWidth,top + childHeight);
}
private void constrainChildRect(LayoutParams lp,Rect out,int childWidth,int childHeight){
final int width=getWidth();
final int height=getHeight();
int left=Math.max(getPaddingLeft() + lp.leftMargin,Math.min(out.left,width - getPaddingRight() - childWidth- lp.rightMargin));
int top=Math.max(getPaddingTop() + lp.topMargin,Math.min(out.top,height - getPaddingBottom() - childHeight- lp.bottomMargin));
out.set(left,top,left + childWidth,top + childHeight);
}
void getDesiredAnchoredChildRect(View child,int layoutDirection,Rect anchorRect,Rect out){
final LayoutParams lp=(LayoutParams)child.getLayoutParams();
final int childWidth=child.getMeasuredWidth();
final int childHeight=child.getMeasuredHeight();
getDesiredAnchoredChildRectWithoutConstraints(layoutDirection,anchorRect,out,lp,childWidth,childHeight);
constrainChildRect(lp,out,childWidth,childHeight);
}
private void layoutChildWithAnchor(View child,View anchor,int layoutDirection){
final Rect anchorRect=acquireTempRect();
final Rect childRect=acquireTempRect();
try {
getDescendantRect(anchor,anchorRect);
getDesiredAnchoredChildRect(child,layoutDirection,anchorRect,childRect);
child.layout(childRect.left,childRect.top,childRect.right,childRect.bottom);
}
  finally {
releaseTempRect(anchorRect);
releaseTempRect(childRect);
}
}
private void layoutChildWithKeyline(View child,int keyline,int layoutDirection){
final LayoutParams lp=(LayoutParams)child.getLayoutParams();
final int absGravity=GravityCompat.getAbsoluteGravity(resolveKeylineGravity(lp.gravity),layoutDirection);
final int hgrav=absGravity & Gravity.HORIZONTAL_GRAVITY_MASK;
final int vgrav=absGravity & Gravity.VERTICAL_GRAVITY_MASK;
final int width=getWidth();
final int height=getHeight();
final int childWidth=child.getMeasuredWidth();
final int childHeight=child.getMeasuredHeight();
if (layoutDirection == ViewCompat.LAYOUT_DIRECTION_RTL) {
keyline=width - keyline;
}
int left=getKeyline(keyline) - childWidth;
int top=0;
switch (hgrav) {
default :
case Gravity.LEFT:
break;
case Gravity.RIGHT:
left+=childWidth;
break;
case Gravity.CENTER_HORIZONTAL:
left+=childWidth / 2;
break;
}
switch (vgrav) {
default :
case Gravity.TOP:
break;
case Gravity.BOTTOM:
top+=childHeight;
break;
case Gravity.CENTER_VERTICAL:
top+=childHeight / 2;
break;
}
left=Math.max(getPaddingLeft() + lp.leftMargin,Math.min(left,width - getPaddingRight() - childWidth- lp.rightMargin));
top=Math.max(getPaddingTop() + lp.topMargin,Math.min(top,height - getPaddingBottom() - childHeight- lp.bottomMargin));
child.layout(left,top,left + childWidth,top + childHeight);
}
private void layoutChild(View child,int layoutDirection){
final LayoutParams lp=(LayoutParams)child.getLayoutParams();
final Rect parent=acquireTempRect();
parent.set(getPaddingLeft() + lp.leftMargin,getPaddingTop() + lp.topMargin,getWidth() - getPaddingRight() - lp.rightMargin,getHeight() - getPaddingBottom() - lp.bottomMargin);
if (mLastInsets != null && ViewCompat.getFitsSystemWindows(this) && !ViewCompat.getFitsSystemWindows(child)) {
parent.left+=mLastInsets.getSystemWindowInsetLeft();
parent.top+=mLastInsets.getSystemWindowInsetTop();
parent.right-=mLastInsets.getSystemWindowInsetRight();
parent.bottom-=mLastInsets.getSystemWindowInsetBottom();
}
final Rect out=acquireTempRect();
GravityCompat.apply(resolveGravity(lp.gravity),child.getMeasuredWidth(),child.getMeasuredHeight(),parent,out,layoutDirection);
child.layout(out.left,out.top,out.right,out.bottom);
releaseTempRect(parent);
releaseTempRect(out);
}
private static int resolveGravity(int gravity){
if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.NO_GRAVITY) {
gravity|=GravityCompat.START;
}
if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.NO_GRAVITY) {
gravity|=Gravity.TOP;
}
return gravity;
}
private static int resolveKeylineGravity(int gravity){
return gravity == Gravity.NO_GRAVITY ? GravityCompat.END | Gravity.TOP : gravity;
}
private static int resolveAnchoredChildGravity(int gravity){
return gravity == Gravity.NO_GRAVITY ? Gravity.CENTER : gravity;
}
public final void onChildViewsChanged(final int type){
final int layoutDirection=ViewCompat.getLayoutDirection(this);
final int childCount=mDependencySortedChildren.size();
final Rect inset=acquireTempRect();
final Rect drawRect=acquireTempRect();
final Rect lastDrawRect=acquireTempRect();
for (int i=0; i < childCount; i++) {
final View child=mDependencySortedChildren.get(i);
final LayoutParams lp=(LayoutParams)child.getLayoutParams();
if (type == EVENT_PRE_DRAW && child.getVisibility() == View.GONE) {
continue;
}
for (int j=0; j < i; j++) {
final View checkChild=mDependencySortedChildren.get(j);
if (lp.mAnchorDirectChild == checkChild) {
offsetChildToAnchor(child,layoutDirection);
}
}
getChildRect(child,true,drawRect);
if (lp.insetEdge != Gravity.NO_GRAVITY && !drawRect.isEmpty()) {
final int absInsetEdge=GravityCompat.getAbsoluteGravity(lp.insetEdge,layoutDirection);
switch (absInsetEdge & Gravity.VERTICAL_GRAVITY_MASK) {
case Gravity.TOP:
inset.top=Math.max(inset.top,drawRect.bottom);
break;
case Gravity.BOTTOM:
inset.bottom=Math.max(inset.bottom,getHeight() - drawRect.top);
break;
}
switch (absInsetEdge & Gravity.HORIZONTAL_GRAVITY_MASK) {
case Gravity.LEFT:
inset.left=Math.max(inset.left,drawRect.right);
break;
case Gravity.RIGHT:
inset.right=Math.max(inset.right,getWidth() - drawRect.left);
break;
}
}
if (lp.dodgeInsetEdges != Gravity.NO_GRAVITY && child.getVisibility() == View.VISIBLE) {
offsetChildByInset(child,inset,layoutDirection);
}
if (type != EVENT_VIEW_REMOVED) {
getLastChildRect(child,lastDrawRect);
if (lastDrawRect.equals(drawRect)) {
continue;
}
recordLastChildRect(child,drawRect);
}
for (int j=i + 1; j < childCount; j++) {
final View checkChild=mDependencySortedChildren.get(j);
final LayoutParams checkLp=(LayoutParams)checkChild.getLayoutParams();
final Behavior b=checkLp.getBehavior();
if (b != null && b.layoutDependsOn(this,checkChild,child)) {
if (type == EVENT_PRE_DRAW && checkLp.getChangedAfterNestedScroll()) {
checkLp.resetChangedAfterNestedScroll();
continue;
}
final boolean handled;
switch (type) {
case EVENT_VIEW_REMOVED:
b.onDependentViewRemoved(this,checkChild,child);
handled=true;
break;
default :
handled=b.onDependentViewChanged(this,checkChild,child);
break;
}
if (type == EVENT_NESTED_SCROLL) {
checkLp.setChangedAfterNestedScroll(handled);
}
}
}
}
releaseTempRect(inset);
releaseTempRect(drawRect);
releaseTempRect(lastDrawRect);
}
private void offsetChildByInset(final View child,final Rect inset,final int layoutDirection){
if (!ViewCompat.isLaidOut(child)) {
return;
}
if (child.getWidth() <= 0 || child.getHeight() <= 0) {
return;
}
final LayoutParams lp=(LayoutParams)child.getLayoutParams();
final Behavior behavior=lp.getBehavior();
final Rect dodgeRect=acquireTempRect();
final Rect bounds=acquireTempRect();
bounds.set(child.getLeft(),child.getTop(),child.getRight(),child.getBottom());
if (behavior != null && behavior.getInsetDodgeRect(this,child,dodgeRect)) {
if (!bounds.contains(dodgeRect)) {
throw new IllegalArgumentException("Rect should be within the child's bounds." + " Rect:" + dodgeRect.toShortString() + " | Bounds:"+ bounds.toShortString());
}
}
 else {
dodgeRect.set(bounds);
}
releaseTempRect(bounds);
if (dodgeRect.isEmpty()) {
releaseTempRect(dodgeRect);
return;
}
final int absDodgeInsetEdges=GravityCompat.getAbsoluteGravity(lp.dodgeInsetEdges,layoutDirection);
boolean offsetY=false;
if ((absDodgeInsetEdges & Gravity.TOP) == Gravity.TOP) {
int distance=dodgeRect.top - lp.topMargin - lp.mInsetOffsetY;
if (distance < inset.top) {
setInsetOffsetY(child,inset.top - distance);
offsetY=true;
}
}
if ((absDodgeInsetEdges & Gravity.BOTTOM) == Gravity.BOTTOM) {
int distance=getHeight() - dodgeRect.bottom - lp.bottomMargin + lp.mInsetOffsetY;
if (distance < inset.bottom) {
setInsetOffsetY(child,distance - inset.bottom);
offsetY=true;
}
}
if (!offsetY) {
setInsetOffsetY(child,0);
}
boolean offsetX=false;
if ((absDodgeInsetEdges & Gravity.LEFT) == Gravity.LEFT) {
int distance=dodgeRect.left - lp.leftMargin - lp.mInsetOffsetX;
if (distance < inset.left) {
setInsetOffsetX(child,inset.left - distance);
offsetX=true;
}
}
if ((absDodgeInsetEdges & Gravity.RIGHT) == Gravity.RIGHT) {
int distance=getWidth() - dodgeRect.right - lp.rightMargin + lp.mInsetOffsetX;
if (distance < inset.right) {
setInsetOffsetX(child,distance - inset.right);
offsetX=true;
}
}
if (!offsetX) {
setInsetOffsetX(child,0);
}
releaseTempRect(dodgeRect);
}
private void setInsetOffsetX(View child,int offsetX){
final LayoutParams lp=(LayoutParams)child.getLayoutParams();
if (lp.mInsetOffsetX != offsetX) {
final int dx=offsetX - lp.mInsetOffsetX;
ViewCompat.offsetLeftAndRight(child,dx);
lp.mInsetOffsetX=offsetX;
}
}
private void setInsetOffsetY(View child,int offsetY){
final LayoutParams lp=(LayoutParams)child.getLayoutParams();
if (lp.mInsetOffsetY != offsetY) {
final int dy=offsetY - lp.mInsetOffsetY;
ViewCompat.offsetTopAndBottom(child,dy);
lp.mInsetOffsetY=offsetY;
}
}
public void dispatchDependentViewsChanged(View view){
final List<View> dependents=mChildDag.getIncomingEdgesInternal(view);
if (dependents != null && !dependents.isEmpty()) {
for (int i=0; i < dependents.size(); i++) {
final View child=dependents.get(i);
LayoutParams lp=(LayoutParams)child.getLayoutParams();
Behavior b=lp.getBehavior();
if (b != null) {
b.onDependentViewChanged(this,child,view);
}
}
}
}
public List<View> getDependencies(View child){
List<View> result=mChildDag.getOutgoingEdges(child);
return result == null ? Collections.<View>emptyList() : result;
}
void ensurePreDrawListener(){
boolean hasDependencies=false;
final int childCount=getChildCount();
for (int i=0; i < childCount; i++) {
final View child=getChildAt(i);
if (hasDependencies(child)) {
hasDependencies=true;
break;
}
}
if (hasDependencies != mNeedsPreDrawListener) {
if (hasDependencies) {
addPreDrawListener();
}
 else {
removePreDrawListener();
}
}
}
private boolean hasDependencies(View child){
return mChildDag.hasOutgoingEdges(child);
}
void offsetChildToAnchor(View child,int layoutDirection){
final LayoutParams lp=(LayoutParams)child.getLayoutParams();
if (lp.mAnchorView != null) {
final Rect anchorRect=acquireTempRect();
final Rect childRect=acquireTempRect();
final Rect desiredChildRect=acquireTempRect();
getDescendantRect(lp.mAnchorView,anchorRect);
getChildRect(child,false,childRect);
int childWidth=child.getMeasuredWidth();
int childHeight=child.getMeasuredHeight();
getDesiredAnchoredChildRectWithoutConstraints(layoutDirection,anchorRect,desiredChildRect,lp,childWidth,childHeight);
boolean changed=desiredChildRect.left != childRect.left || desiredChildRect.top != childRect.top;
constrainChildRect(lp,desiredChildRect,childWidth,childHeight);
final int dx=desiredChildRect.left - childRect.left;
final int dy=desiredChildRect.top - childRect.top;
if (dx != 0) {
ViewCompat.offsetLeftAndRight(child,dx);
}
if (dy != 0) {
ViewCompat.offsetTopAndBottom(child,dy);
}
if (changed) {
final Behavior b=lp.getBehavior();
if (b != null) {
b.onDependentViewChanged(this,child,lp.mAnchorView);
}
}
releaseTempRect(anchorRect);
releaseTempRect(childRect);
releaseTempRect(desiredChildRect);
}
}
public static abstract class Behavior<V extends View> {
public Behavior(){
}
public void onAttachedToLayoutParams(CoordinatorLayout.LayoutParams params){
}
public void onDetachedFromLayoutParams(){
}
public boolean layoutDependsOn(CoordinatorLayout parent,V child,View dependency){
return false;
}
public boolean onDependentViewChanged(CoordinatorLayout parent,V child,View dependency){
return false;
}
public void onDependentViewRemoved(CoordinatorLayout parent,V child,View dependency){
}
public boolean onMeasureChild(CoordinatorLayout parent,V child,int parentWidthMeasureSpec,int widthUsed,int parentHeightMeasureSpec,int heightUsed){
return false;
}
public boolean onLayoutChild(CoordinatorLayout parent,V child,int layoutDirection){
return false;
}
public boolean getInsetDodgeRect(CoordinatorLayout parent,V child,Rect rect){
return false;
}
}
public static class LayoutParams extends MarginLayoutParams {
Behavior mBehavior;
boolean mBehaviorResolved=false;
public int gravity=Gravity.NO_GRAVITY;
public int anchorGravity=Gravity.NO_GRAVITY;
public int keyline=-1;
int mAnchorId=View.NO_ID;
public int insetEdge=Gravity.NO_GRAVITY;
public int dodgeInsetEdges=Gravity.NO_GRAVITY;
int mInsetOffsetX;
int mInsetOffsetY;
View mAnchorView;
View mAnchorDirectChild;
private boolean mDidBlockInteraction;
private boolean mDidAcceptNestedScrollTouch;
private boolean mDidAcceptNestedScrollNonTouch;
private boolean mDidChangeAfterNestedScroll;
final Rect mLastChildRect=new Rect();
Object mBehaviorTag;
public LayoutParams(int width,int height){
super(width,height);
}
public LayoutParams(LayoutParams p){
super(p);
}
public LayoutParams(ViewGroup.LayoutParams p){
super(p);
}
public int getAnchorId(){
return mAnchorId;
}
public void setAnchorId(int id){
invalidateAnchor();
mAnchorId=id;
}
public Behavior getBehavior(){
return mBehavior;
}
public void setBehavior(Behavior behavior){
if (mBehavior != behavior) {
if (mBehavior != null) {
mBehavior.onDetachedFromLayoutParams();
}
mBehavior=behavior;
mBehaviorTag=null;
mBehaviorResolved=true;
if (behavior != null) {
behavior.onAttachedToLayoutParams(this);
}
}
}
void setLastChildRect(Rect r){
mLastChildRect.set(r);
}
Rect getLastChildRect(){
return mLastChildRect;
}
boolean checkAnchorChanged(){
return mAnchorView == null && mAnchorId != View.NO_ID;
}
boolean getChangedAfterNestedScroll(){
return mDidChangeAfterNestedScroll;
}
void setChangedAfterNestedScroll(boolean changed){
mDidChangeAfterNestedScroll=changed;
}
void resetChangedAfterNestedScroll(){
mDidChangeAfterNestedScroll=false;
}
boolean dependsOn(CoordinatorLayout parent,View child,View dependency){
return dependency == mAnchorDirectChild || shouldDodge(dependency,ViewCompat.getLayoutDirection(parent)) || (mBehavior != null && mBehavior.layoutDependsOn(parent,child,dependency));
}
void invalidateAnchor(){
mAnchorView=mAnchorDirectChild=null;
}
View findAnchorView(CoordinatorLayout parent,View forChild){
if (mAnchorId == View.NO_ID) {
mAnchorView=mAnchorDirectChild=null;
return null;
}
if (mAnchorView == null || !verifyAnchorView(forChild,parent)) {
resolveAnchorView(forChild,parent);
}
return mAnchorView;
}
private void resolveAnchorView(final View forChild,final CoordinatorLayout parent){
mAnchorView=parent.findViewById(mAnchorId);
if (mAnchorView != null) {
if (mAnchorView == parent) {
if (parent.isInEditMode()) {
mAnchorView=mAnchorDirectChild=null;
return;
}
throw new IllegalStateException("View can not be anchored to the the parent CoordinatorLayout");
}
View directChild=mAnchorView;
for (ViewParent p=mAnchorView.getParent(); p != parent && p != null; p=p.getParent()) {
if (p == forChild) {
if (parent.isInEditMode()) {
mAnchorView=mAnchorDirectChild=null;
return;
}
throw new IllegalStateException("Anchor must not be a descendant of the anchored view");
}
if (p instanceof View) {
directChild=(View)p;
}
}
mAnchorDirectChild=directChild;
}
 else {
if (parent.isInEditMode()) {
mAnchorView=mAnchorDirectChild=null;
return;
}
throw new IllegalStateException("Could not find CoordinatorLayout descendant view" + " with id " + parent.getResources().getResourceName(mAnchorId) + " to anchor view "+ forChild);
}
}
private boolean verifyAnchorView(View forChild,CoordinatorLayout parent){
if (mAnchorView.getId() != mAnchorId) {
return false;
}
View directChild=mAnchorView;
for (ViewParent p=mAnchorView.getParent(); p != parent; p=p.getParent()) {
if (p == null || p == forChild) {
mAnchorView=mAnchorDirectChild=null;
return false;
}
if (p instanceof View) {
directChild=(View)p;
}
}
mAnchorDirectChild=directChild;
return true;
}
private boolean shouldDodge(View other,int layoutDirection){
LayoutParams lp=(LayoutParams)other.getLayoutParams();
final int absInset=GravityCompat.getAbsoluteGravity(lp.insetEdge,layoutDirection);
return absInset != Gravity.NO_GRAVITY && (absInset & GravityCompat.getAbsoluteGravity(dodgeInsetEdges,layoutDirection)) == absInset;
}
}
public void setKeyLines(int[] keyLines){
mKeylines=keyLines;
}
LayoutParams getResolvedLayoutParams(View child){
final LayoutParams result=(LayoutParams)child.getLayoutParams();
result.mBehaviorResolved=true;
return result;
}
void addPreDrawListener(){
}
void removePreDrawListener(){
}
class WindowInsetsCompat {
public int getSystemWindowInsetLeft(){
return 0;
}
public int getSystemWindowInsetBottom(){
return 0;
}
public int getSystemWindowInsetTop(){
return 0;
}
public int getSystemWindowInsetRight(){
return 0;
}
}
static class ViewGroupUtils {
public static void getDescendantRect(CoordinatorLayout coordinatorLayout,View descendant,Rect out){
out.set(descendant.getLeft(),descendant.getTop(),descendant.getRight(),descendant.getBottom());
}
}
}
