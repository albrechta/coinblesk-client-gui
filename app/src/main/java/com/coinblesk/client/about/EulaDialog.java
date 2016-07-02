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
package com.coinblesk.client.about;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import com.coinblesk.client.R;

/**
 * @author ckiller
 */
public class EulaDialog extends DialogFragment {

    public static DialogFragment newInstance() {
        return new EulaDialog();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        int padding = getResources().getDimensionPixelSize(R.dimen.padding_dialog);

        TextView eulaTextView = new TextView(getActivity());
        eulaTextView.setText(Html.fromHtml(getString(R.string.social_about_tos_content)));
        eulaTextView.setMovementMethod(LinkMovementMethod.getInstance());
        eulaTextView.setPadding(padding, padding, padding, padding);

        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.social_about_tos)
                .setView(eulaTextView)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dialog.dismiss();
                            }
                        }
                )
                .create();
    }
}
