/* 
 * Sourcerer: an infrastructure for large-scale source code analysis.
 * Copyright (C) by contributors. See CONTRIBUTORS.txt for full list.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package edu.uci.ics.sourcerer.util;

import java.lang.ref.SoftReference;

/**
 * @author Joel Ossher (jossher@uci.edu)
 */
public abstract class CachedReference<T> {
  private SoftReference<T> ref;
  
  protected abstract T make();
  
  public T get() {
    T result = null;
    if (ref != null) {
      result = ref.get();
    }
    if (result == null) {
      result = make();
      ref = new SoftReference<>(result);
    }
    return result;
  }
  
  public T getIfCached() {
    if (ref == null) {
      return null;
    } else {
      return ref.get();
    }
  }
}