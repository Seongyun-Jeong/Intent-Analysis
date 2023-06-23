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
 *
 */

package com.android.systemui.keyguard.data.quickaffordance

import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.settings.UserTracker
import com.android.systemui.shared.customization.data.content.CustomizationProviderClient
import com.android.systemui.shared.customization.data.content.CustomizationProviderClientImpl
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher

interface KeyguardQuickAffordanceProviderClientFactory {
    fun create(): CustomizationProviderClient
}

class KeyguardQuickAffordanceProviderClientFactoryImpl
@Inject
constructor(
    private val userTracker: UserTracker,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
) : KeyguardQuickAffordanceProviderClientFactory {
    override fun create(): CustomizationProviderClient {
        return CustomizationProviderClientImpl(
            context = userTracker.userContext,
            backgroundDispatcher = backgroundDispatcher,
        )
    }
}