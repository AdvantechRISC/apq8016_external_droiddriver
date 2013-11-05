/*
 * Copyright (C) 2013 DroidDriver committers
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

package com.google.android.droiddriver.instrumentation;

import static com.google.android.droiddriver.util.TextUtils.charSequenceToString;

import android.content.res.Resources;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Checkable;
import android.widget.TextView;

import com.google.android.droiddriver.actions.InputInjector;
import com.google.android.droiddriver.base.BaseUiElement;
import com.google.android.droiddriver.exceptions.DroidDriverException;
import com.google.android.droiddriver.finders.Attribute;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;

/**
 * A UiElement that is backed by a View.
 */
public class ViewElement extends BaseUiElement {
  private static class SnapshotViewAttributesRunnable implements Runnable {
    private final View view;
    final Map<Attribute, Object> attribs = Maps.newEnumMap(Attribute.class);
    boolean visible;
    Rect visibleBounds;
    List<View> childViews;
    Throwable exception;

    private SnapshotViewAttributesRunnable(View view) {
      this.view = view;
    }

    @Override
    public void run() {
      try {
        put(Attribute.PACKAGE, view.getContext().getPackageName());
        put(Attribute.CLASS, getClassName());
        put(Attribute.TEXT, getText());
        put(Attribute.CONTENT_DESC, charSequenceToString(view.getContentDescription()));
        put(Attribute.RESOURCE_ID, getResourceId());
        put(Attribute.CHECKABLE, view instanceof Checkable);
        put(Attribute.CHECKED, isChecked());
        put(Attribute.CLICKABLE, view.isClickable());
        put(Attribute.ENABLED, view.isEnabled());
        put(Attribute.FOCUSABLE, view.isFocusable());
        put(Attribute.FOCUSED, view.isFocused());
        put(Attribute.LONG_CLICKABLE, view.isLongClickable());
        put(Attribute.PASSWORD, isPassword());
        put(Attribute.SCROLLABLE, isScrollable());
        put(Attribute.SELECTED, view.isSelected());
        put(Attribute.BOUNDS, getBounds());

        // Order matters as setVisible() depends on setVisibleBounds().
        this.visibleBounds = getVisibleBounds();
        // isShown() checks the visibility flag of this view and ancestors; it
        // needs to have the VISIBLE flag as well as non-empty bounds to be
        // visible.
        this.visible = view.isShown() && !visibleBounds.isEmpty();
        setChildViews();
      } catch (Throwable e) {
        exception = e;
      }
    }

    private void put(Attribute key, Object value) {
      if (value != null) {
        attribs.put(key, value);
      }
    }

    private String getText() {
      if (!(view instanceof TextView)) {
        return null;
      }
      return charSequenceToString(((TextView) view).getText());
    }

    private String getClassName() {
      String className = view.getClass().getName();
      return CLASS_NAME_OVERRIDES.containsKey(className) ? CLASS_NAME_OVERRIDES.get(className)
          : className;
    }

    private String getResourceId() {
      if (view.getId() != View.NO_ID && view.getResources() != null) {
        try {
          return charSequenceToString(view.getResources().getResourceName(view.getId()));
        } catch (Resources.NotFoundException nfe) {
          /* ignore */
        }
      }
      return null;
    }

    private boolean isChecked() {
      return view instanceof Checkable && ((Checkable) view).isChecked();
    }

    private boolean isScrollable() {
      // TODO: find a meaningful implementation
      return true;
    }

    private boolean isPassword() {
      // TODO: find a meaningful implementation
      return false;
    }

    private Rect getBounds() {
      Rect rect = new Rect();
      int[] xy = new int[2];
      view.getLocationOnScreen(xy);
      rect.set(xy[0], xy[1], xy[0] + view.getWidth(), xy[1] + view.getHeight());
      return rect;
    }

    private Rect getVisibleBounds() {
      Rect visibleBounds = new Rect();
      if (!view.isShown() || !view.getGlobalVisibleRect(visibleBounds)) {
        visibleBounds.setEmpty();
      }
      int[] xyScreen = new int[2];
      view.getLocationOnScreen(xyScreen);
      int[] xyWindow = new int[2];
      view.getLocationInWindow(xyWindow);
      int windowLeft = xyScreen[0] - xyWindow[0];
      int windowTop = xyScreen[1] - xyWindow[1];

      // Bounds are relative to root view; adjust to screen coordinates.
      visibleBounds.offset(windowLeft, windowTop);
      return visibleBounds;
    }

    private void setChildViews() {
      if (!(view instanceof ViewGroup)) {
        return;
      }
      ViewGroup group = (ViewGroup) view;
      int childCount = group.getChildCount();
      childViews = Lists.newArrayListWithExpectedSize(childCount);
      for (int i = 0; i < childCount; i++) {
        View child = group.getChildAt(i);
        if (child != null) {
          childViews.add(child);
        }
      }
    }
  }

  private static final Map<String, String> CLASS_NAME_OVERRIDES = Maps.newHashMap();

  /**
   * Typically users find the class name to use in tests using SDK tool
   * uiautomatorviewer. This name is returned by
   * {@link AccessibilityNodeInfo#getClassName}. If the app uses custom View
   * classes that do not call {@link AccessibilityNodeInfo#setClassName} with
   * the actual class name, different types of drivers see different class names
   * (InstrumentationDriver sees the actual class name, while UiAutomationDriver
   * sees {@link AccessibilityNodeInfo#getClassName}).
   * <p>
   * If tests fail with InstrumentationDriver, find the actual class name by
   * examining app code or by calling
   * {@link com.google.android.droiddriver.DroidDriver#dumpUiElementTree}, then
   * call this method in setUp to override it with the class name seen in
   * uiautomatorviewer.
   */
  public static void overrideClassName(String actualClassName, String overridingClassName) {
    CLASS_NAME_OVERRIDES.put(actualClassName, overridingClassName);
  }

  private final InstrumentationContext context;
  private final Map<Attribute, Object> attributes;
  private final boolean visible;
  private final Rect visibleBounds;
  private final ViewElement parent;
  private final List<ViewElement> children;

  /**
   * A snapshot of all attributes is taken at construction. The attributes of a
   * {@code ViewElement} instance are immutable. If the underlying view is
   * updated, a new {@code ViewElement} instance will be created in
   * {@link com.google.android.droiddriver.DroidDriver#refreshUiElementTree}.
   */
  public ViewElement(final InstrumentationContext context, View view, ViewElement parent) {
    this.context = Preconditions.checkNotNull(context);
    Preconditions.checkNotNull(view);
    this.parent = parent;
    SnapshotViewAttributesRunnable attributesSnapshot = new SnapshotViewAttributesRunnable(view);
    context.getInstrumentation().runOnMainSync(attributesSnapshot);
    if (attributesSnapshot.exception != null) {
      throw new DroidDriverException(attributesSnapshot.exception);
    }

    attributes = ImmutableMap.copyOf(attributesSnapshot.attribs);
    this.visibleBounds = attributesSnapshot.visibleBounds;
    this.visible = attributesSnapshot.visible;
    this.children =
        attributesSnapshot.childViews == null ? null : ImmutableList.copyOf(Lists.transform(
            attributesSnapshot.childViews, new Function<View, ViewElement>() {
              public ViewElement apply(View input) {
                return context.getUiElement(input, ViewElement.this);
              }
            }));
  }

  @Override
  public Rect getVisibleBounds() {
    return visibleBounds;
  }

  @Override
  public boolean isVisible() {
    return visible;
  }

  @Override
  public ViewElement getParent() {
    return parent;
  }

  @Override
  protected List<ViewElement> getChildren() {
    return children;
  }

  @Override
  protected Map<Attribute, Object> getAttributes() {
    return attributes;
  }

  @Override
  protected InputInjector getInjector() {
    return context.getInjector();
  }
}
