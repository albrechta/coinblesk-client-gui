/*
 * Copyright 2016 The Coinblesk team and the CSG Group at University of Zurich
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.coinblesk.client.helpers;


import com.coinblesk.client.BuildConfig;

/**
 * @author Andreas Albrecht
 */
public final class AppUtils {
    private AppUtils() {
        // prevent instances
    }

    public static String getAppVersion() {
        // strip build number
        String v = getVersionName();
        return v.substring(0, v.lastIndexOf('.'));
    }

    // Version has the form: x.y.build
    // see: build.gradle (app module)
    public static String getVersionName() {
        return BuildConfig.VERSION_NAME;
    }
}
