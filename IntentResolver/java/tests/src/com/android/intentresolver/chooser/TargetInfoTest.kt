/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.intentresolver.chooser

import android.app.prediction.AppTarget
import android.app.prediction.AppTargetId
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.os.UserHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.android.intentresolver.createChooserTarget
import com.android.intentresolver.createShortcutInfo
import com.android.intentresolver.mock
import com.android.intentresolver.ResolverDataProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TargetInfoTest {
    private val context = InstrumentationRegistry.getInstrumentation().getContext()

    @Test
    fun testNewEmptyTargetInfo() {
        val info = NotSelectableTargetInfo.newEmptyTargetInfo()
        assertThat(info.isEmptyTargetInfo()).isTrue()
        assertThat(info.isChooserTargetInfo()).isTrue()  // From legacy inheritance model.
        assertThat(info.hasDisplayIcon()).isFalse()
        assertThat(info.getDisplayIconHolder().getDisplayIcon()).isNull()
    }

    @Test
    fun testNewPlaceholderTargetInfo() {
        val info = NotSelectableTargetInfo.newPlaceHolderTargetInfo(context)
        assertThat(info.isPlaceHolderTargetInfo()).isTrue()
        assertThat(info.isChooserTargetInfo()).isTrue()  // From legacy inheritance model.
        assertThat(info.hasDisplayIcon()).isTrue()
        // TODO: test infrastructure isn't set up to assert anything about the icon itself.
    }

    @Test
    fun testNewSelectableTargetInfo() {
        val resolvedIntent = Intent()
        val baseDisplayInfo = DisplayResolveInfo.newDisplayResolveInfo(
            resolvedIntent,
            ResolverDataProvider.createResolveInfo(1, 0),
            "label",
            "extended info",
            resolvedIntent,
            /* resolveInfoPresentationGetter= */ null)
        val chooserTarget = createChooserTarget(
            "title", 0.3f, ResolverDataProvider.createComponentName(2), "test_shortcut_id")
        val shortcutInfo = createShortcutInfo("id", ResolverDataProvider.createComponentName(3), 3)
        val appTarget = AppTarget(
            AppTargetId("id"),
            chooserTarget.componentName.packageName,
            chooserTarget.componentName.className,
            UserHandle.CURRENT)

        val targetInfo = SelectableTargetInfo.newSelectableTargetInfo(
            baseDisplayInfo,
            mock(),
            resolvedIntent,
            chooserTarget,
            0.1f,
            shortcutInfo,
            appTarget,
            mock(),
        )
        assertThat(targetInfo.isSelectableTargetInfo).isTrue()
        assertThat(targetInfo.isChooserTargetInfo).isTrue()  // From legacy inheritance model.
        assertThat(targetInfo.displayResolveInfo).isSameInstanceAs(baseDisplayInfo)
        assertThat(targetInfo.chooserTargetComponentName).isEqualTo(chooserTarget.componentName)
        assertThat(targetInfo.directShareShortcutId).isEqualTo(shortcutInfo.id)
        assertThat(targetInfo.directShareShortcutInfo).isSameInstanceAs(shortcutInfo)
        assertThat(targetInfo.directShareAppTarget).isSameInstanceAs(appTarget)
        assertThat(targetInfo.resolvedIntent).isSameInstanceAs(resolvedIntent)
        // TODO: make more meaningful assertions about the behavior of a selectable target.
    }

    @Test
    fun test_SelectableTargetInfo_componentName_no_source_info() {
        val chooserTarget = createChooserTarget(
            "title", 0.3f, ResolverDataProvider.createComponentName(1), "test_shortcut_id")
        val shortcutInfo = createShortcutInfo("id", ResolverDataProvider.createComponentName(2), 3)
        val appTarget = AppTarget(
            AppTargetId("id"),
            chooserTarget.componentName.packageName,
            chooserTarget.componentName.className,
            UserHandle.CURRENT)
        val pkgName = "org.package"
        val className = "MainActivity"
        val backupResolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = pkgName
                name = className
            }
        }

        val targetInfo = SelectableTargetInfo.newSelectableTargetInfo(
            null,
            backupResolveInfo,
            mock(),
            chooserTarget,
            0.1f,
            shortcutInfo,
            appTarget,
            mock(),
        )
        assertThat(targetInfo.resolvedComponentName).isEqualTo(ComponentName(pkgName, className))
    }

    @Test
    fun testNewDisplayResolveInfo() {
        val intent = Intent(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_TEXT, "testing intent sending")
        intent.setType("text/plain")

        val resolveInfo = ResolverDataProvider.createResolveInfo(3, 0)

        val targetInfo = DisplayResolveInfo.newDisplayResolveInfo(
            intent,
            resolveInfo,
            "label",
            "extended info",
            intent,
            /* resolveInfoPresentationGetter= */ null)
        assertThat(targetInfo.isDisplayResolveInfo()).isTrue()
        assertThat(targetInfo.isMultiDisplayResolveInfo()).isFalse()
        assertThat(targetInfo.isChooserTargetInfo()).isFalse()
    }

    @Test
    fun testNewMultiDisplayResolveInfo() {
        val intent = Intent(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_TEXT, "testing intent sending")
        intent.setType("text/plain")

        val resolveInfo = ResolverDataProvider.createResolveInfo(3, 0)
        val firstTargetInfo = DisplayResolveInfo.newDisplayResolveInfo(
            intent,
            resolveInfo,
            "label 1",
            "extended info 1",
            intent,
            /* resolveInfoPresentationGetter= */ null)
        val secondTargetInfo = DisplayResolveInfo.newDisplayResolveInfo(
            intent,
            resolveInfo,
            "label 2",
            "extended info 2",
            intent,
            /* resolveInfoPresentationGetter= */ null)

        val multiTargetInfo = MultiDisplayResolveInfo.newMultiDisplayResolveInfo(
            listOf(firstTargetInfo, secondTargetInfo))

        assertThat(multiTargetInfo.isMultiDisplayResolveInfo()).isTrue()
        assertThat(multiTargetInfo.isDisplayResolveInfo()).isTrue()  // From legacy inheritance.
        assertThat(multiTargetInfo.isChooserTargetInfo()).isFalse()

        assertThat(multiTargetInfo.getExtendedInfo()).isNull()

        assertThat(multiTargetInfo.getAllDisplayTargets())
                .containsExactly(firstTargetInfo, secondTargetInfo)

        assertThat(multiTargetInfo.hasSelected()).isFalse()
        assertThat(multiTargetInfo.getSelectedTarget()).isNull()

        multiTargetInfo.setSelected(1)

        assertThat(multiTargetInfo.hasSelected()).isTrue()
        assertThat(multiTargetInfo.getSelectedTarget()).isEqualTo(secondTargetInfo)

        // TODO: consider exercising activity-start behavior.
        // TODO: consider exercising DisplayResolveInfo base class behavior.
    }
}
