/*
 * Copyright (C) 2013 UiDriver committers
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

package com.google.android.uidriver.actions;

import com.google.android.uidriver.InputInjector;
import com.google.android.uidriver.UiElement;

/**
 * Interface for performing action on a UiElement.
 */
public interface Action {
  /**
   * Performs the action.
   *
   * @param injector the injector to inject input events.
   * @param element the Ui element to perform the action on.
   */
  boolean perform(InputInjector injector, UiElement element);
}
