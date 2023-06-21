/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.intentresolver.chooser;

import android.app.Activity;
import android.os.Bundle;
import android.os.UserHandle;

import com.android.intentresolver.ResolverActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a "stack" of chooser targets for various activities within the same component.
 */
public class MultiDisplayResolveInfo extends DisplayResolveInfo {

    ArrayList<DisplayResolveInfo> mTargetInfos = new ArrayList<>();

    // Index of selected target
    private int mSelected = -1;

    /**
     * @param targetInfos A list of targets in this stack. The first item is treated as the
     * "representative" that provides the main icon, title, etc.
     */
    public static MultiDisplayResolveInfo newMultiDisplayResolveInfo(
            List<DisplayResolveInfo> targetInfos) {
        return new MultiDisplayResolveInfo(targetInfos);
    }

    /**
     * @param targetInfos A list of targets in this stack. The first item is treated as the
     * "representative" that provides the main icon, title, etc.
     */
    private MultiDisplayResolveInfo(List<DisplayResolveInfo> targetInfos) {
        super(targetInfos.get(0));
        mTargetInfos = new ArrayList<>(targetInfos);
    }

    @Override
    public final boolean isMultiDisplayResolveInfo() {
        return true;
    }

    @Override
    public CharSequence getExtendedInfo() {
        // Never show subtitle for stacked apps
        return null;
    }

    /**
     * List of all {@link DisplayResolveInfo}s included in this target.
     * TODO: provide as a generic {@code List<DisplayResolveInfo>} once {@link ChooserActivity}
     * stops requiring the signature to match that of the other "lists" it builds up.
     */
    @Override
    public ArrayList<DisplayResolveInfo> getAllDisplayTargets() {
        return mTargetInfos;
    }

    public void setSelected(int selected) {
        mSelected = selected;
    }

    /**
     * Return selected target.
     */
    public DisplayResolveInfo getSelectedTarget() {
        return hasSelected() ? mTargetInfos.get(mSelected) : null;
    }

    /**
     * Whether or not the user has selected a specific target for this MultiInfo.
     */
    public boolean hasSelected() {
        return mSelected >= 0;
    }

    @Override
    public boolean start(Activity activity, Bundle options) {
        return mTargetInfos.get(mSelected).start(activity, options);
    }

    @Override
    public boolean startAsCaller(ResolverActivity activity, Bundle options, int userId) {
        return mTargetInfos.get(mSelected).startAsCaller(activity, options, userId);
    }

    @Override
    public boolean startAsUser(Activity activity, Bundle options, UserHandle user) {
        return mTargetInfos.get(mSelected).startAsUser(activity, options, user);
    }
}
