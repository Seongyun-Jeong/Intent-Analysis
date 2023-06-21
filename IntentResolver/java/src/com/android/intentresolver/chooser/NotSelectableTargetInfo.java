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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;

import com.android.intentresolver.R;
import com.android.intentresolver.ResolverActivity;

import java.util.List;

/**
 * Distinguish between targets that selectable by the user, vs those that are
 * placeholders for the system while information is loading in an async manner.
 */
public abstract class NotSelectableTargetInfo extends ChooserTargetInfo {
    /** Create a non-selectable {@link TargetInfo} with no content. */
    public static TargetInfo newEmptyTargetInfo() {
        return new NotSelectableTargetInfo() {
                @Override
                public boolean isEmptyTargetInfo() {
                    return true;
                }
            };
    }

    /**
     * Create a non-selectable {@link TargetInfo} with placeholder content to be displayed
     * unless/until it can be replaced by the result of a pending asynchronous load.
     */
    public static TargetInfo newPlaceHolderTargetInfo(Context context) {
        return new NotSelectableTargetInfo() {
                @Override
                public boolean isPlaceHolderTargetInfo() {
                    return true;
                }

                @Override
                public IconHolder getDisplayIconHolder() {
                    return new IconHolder() {
                        @Override
                        public Drawable getDisplayIcon() {
                            AnimatedVectorDrawable avd = (AnimatedVectorDrawable)
                                    context.getDrawable(
                                            R.drawable.chooser_direct_share_icon_placeholder);
                            avd.start();  // Start animation after generation.
                            return avd;
                        }

                        @Override
                        public void setDisplayIcon(Drawable icon) {}
                    };
                }

                @Override
                public boolean hasDisplayIcon() {
                    return true;
                }
            };
    }

    public final boolean isNotSelectableTargetInfo() {
        return true;
    }

    public Intent getResolvedIntent() {
        return null;
    }

    public ComponentName getResolvedComponentName() {
        return null;
    }

    public boolean start(Activity activity, Bundle options) {
        return false;
    }

    public boolean startAsCaller(ResolverActivity activity, Bundle options, int userId) {
        return false;
    }

    public boolean startAsUser(Activity activity, Bundle options, UserHandle user) {
        return false;
    }

    public ResolveInfo getResolveInfo() {
        return null;
    }

    public CharSequence getDisplayLabel() {
        return null;
    }

    public CharSequence getExtendedInfo() {
        return null;
    }

    public TargetInfo cloneFilledIn(Intent fillInIntent, int flags) {
        return null;
    }

    public List<Intent> getAllSourceIntents() {
        return null;
    }

    public float getModifiedScore() {
        return -0.1f;
    }

    public boolean isSuspended() {
        return false;
    }

    public boolean isPinned() {
        return false;
    }

    @Override
    public IconHolder getDisplayIconHolder() {
        return new IconHolder() {
            @Override
            public Drawable getDisplayIcon() {
                return null;
            }

            @Override
            public void setDisplayIcon(Drawable icon) {}
        };
    }
}
