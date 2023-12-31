/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.vcard;

import com.android.vcard.JapaneseUtils;

import junit.framework.TestCase;

public class JapaneseUtilsTest extends TestCase {
    static final char TEST_CONTAINED_CHAR = '\uFFE5';
    static final char TEST_UNCONTAINED_CHAR = '\uFFF5';

    public void testTryGetHalfWidthText() {
        assertNull(JapaneseUtils.tryGetHalfWidthText(TEST_UNCONTAINED_CHAR));
        assertEquals(JapaneseUtils.tryGetHalfWidthText(TEST_CONTAINED_CHAR), "\u005C\u005C");
    }
}
