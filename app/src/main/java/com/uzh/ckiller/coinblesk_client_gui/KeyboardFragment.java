package com.uzh.ckiller.coinblesk_client_gui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.gson.Gson;
import com.uzh.ckiller.coinblesk_client_gui.helpers.SpannableStringFormatter;
import com.uzh.ckiller.coinblesk_client_gui.ui.dialogs.CustomValueDialog;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

/**
 * Created by ckiller on 24/01/16.
 */

public abstract class KeyboardFragment extends Fragment implements View.OnClickListener, OnKeyboardListener, CustomValueDialog.CustomValueListener {
    SwipeRefreshLayout mSwipeRefreshLayout;
    private Handler handler = new Handler();
    private final static String TAG = KeyboardFragment.class.getName();


    public static final String MERCHANT_CUSTOM_BUTTONS_PREF_KEY = "MERCHANT_CUSTOM_BUTTONS";

    private String amountString = "0";
    private SharedPreferences prefs;
    private SpannableStringFormatter spannableStringFormatter;
    private String sumString = "";

    // TODO: get current exchange rate from net, largeamount settings from prefs, prefered fiat from prefs.
    private String currencyCode = "CHF";
    private ExchangeRate exchangeRate = new ExchangeRate(Fiat.parseFiat(currencyCode, "430"));
    private boolean isBitcoinLargeAmount = true;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_keyboard, container, false);

        final int screenLayout = getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
        switch (screenLayout) {
            case Configuration.SCREENLAYOUT_SIZE_LARGE:
            case Configuration.SCREENLAYOUT_SIZE_XLARGE:
                initLarge(view);
                break;
            default:
                initStandard(view);
                break;
        }

        mSwipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.keyboard_swipe_refresh_layout);
        try {
            mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    //TODO: Refresh Exchange Rate Here
                    handler.post(refreshing);
                }
            });

            // sets the colors used in the refresh animation
            mSwipeRefreshLayout.setColorSchemeResources(R.color.colorAccent, R.color.material_lime_A100,
                    R.color.material_lime_A400, R.color.material_lime_A400);

        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        this.onKeyboardListener = this;
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final int screenLayout = getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
        if (screenLayout == Configuration.SCREENLAYOUT_SIZE_LARGE || screenLayout == Configuration.SCREENLAYOUT_SIZE_XLARGE) {
            this.initCustomButtons("1");
            this.initCustomButtons("2");
            this.initCustomButtons("3");
            this.initCustomButtons("4");
            this.initCustomButtons("5");
            this.initCustomButtons("6");
            this.initCustomButtons("7");
            this.initCustomButtons("8");
        }

    }

    private void initStandard(View view) {

        // Numbers '0 1 2 3 4 5 6 7 8 9,' and '.'
        view.findViewById(R.id.key_one).setOnClickListener(this);
        view.findViewById(R.id.key_two).setOnClickListener(this);
        view.findViewById(R.id.key_three).setOnClickListener(this);
        view.findViewById(R.id.key_four).setOnClickListener(this);
        view.findViewById(R.id.key_five).setOnClickListener(this);
        view.findViewById(R.id.key_six).setOnClickListener(this);
        view.findViewById(R.id.key_seven).setOnClickListener(this);
        view.findViewById(R.id.key_eight).setOnClickListener(this);
        view.findViewById(R.id.key_nine).setOnClickListener(this);
        view.findViewById(R.id.key_dot).setOnClickListener(this);
        view.findViewById(R.id.key_zero).setOnClickListener(this);
        view.findViewById(R.id.key_accept).setOnClickListener(this);

        // Backspace Button
        view.findViewById(R.id.amount_backspace_image_view).setOnClickListener(this);

        // Switch Currency Button
        view.findViewById(R.id.amount_switch_image_view).setOnClickListener(this);
    }

    private void initLarge(View view) {

        spannableStringFormatter = new SpannableStringFormatter(this.getActivity());
        this.prefs = getActivity().getSharedPreferences(MERCHANT_CUSTOM_BUTTONS_PREF_KEY, Context.MODE_PRIVATE);

        // Numbers '00 0 1 2 3 4 5 6 7 8 9 . + x  clear'
        view.findViewById(R.id.key_one).setOnClickListener(this);
        view.findViewById(R.id.key_two).setOnClickListener(this);
        view.findViewById(R.id.key_three).setOnClickListener(this);
        view.findViewById(R.id.key_four).setOnClickListener(this);
        view.findViewById(R.id.key_five).setOnClickListener(this);
        view.findViewById(R.id.key_six).setOnClickListener(this);
        view.findViewById(R.id.key_seven).setOnClickListener(this);
        view.findViewById(R.id.key_eight).setOnClickListener(this);
        view.findViewById(R.id.key_nine).setOnClickListener(this);
        view.findViewById(R.id.key_dot).setOnClickListener(this);
        view.findViewById(R.id.key_zero).setOnClickListener(this);
        view.findViewById(R.id.key_zero_zero).setOnClickListener(this);
        view.findViewById(R.id.key_accept).setOnClickListener(this);
        view.findViewById(R.id.key_clear).setOnClickListener(this);
        view.findViewById(R.id.key_multiply).setOnClickListener(this);
        view.findViewById(R.id.key_plus).setOnClickListener(this);

        // Backspace Button
        view.findViewById(R.id.amount_backspace_image_view).setOnClickListener(this);
        // Switch Currency Button
        view.findViewById(R.id.amount_switch_image_view).setOnClickListener(this);

        // Customize Buttons for Merchant Mode
        view.findViewById(R.id.key_custom_one).setOnClickListener(this);
        view.findViewById(R.id.key_custom_two).setOnClickListener(this);
        view.findViewById(R.id.key_custom_three).setOnClickListener(this);
        view.findViewById(R.id.key_custom_four).setOnClickListener(this);
        view.findViewById(R.id.key_custom_five).setOnClickListener(this);
        view.findViewById(R.id.key_custom_six).setOnClickListener(this);
        view.findViewById(R.id.key_custom_seven).setOnClickListener(this);
        view.findViewById(R.id.key_custom_eight).setOnClickListener(this);


    }

    private OnKeyboardListener onKeyboardListener;


    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.key_one:
                onKeyboardListener.onDigit(1);
                break;

            case R.id.key_two:
                onKeyboardListener.onDigit(2);
                break;

            case R.id.key_three:
                onKeyboardListener.onDigit(3);
                break;

            case R.id.key_four:
                onKeyboardListener.onDigit(4);
                break;

            case R.id.key_five:
                onKeyboardListener.onDigit(5);
                break;

            case R.id.key_six:
                onKeyboardListener.onDigit(6);
                break;

            case R.id.key_seven:
                onKeyboardListener.onDigit(7);
                break;

            case R.id.key_eight:
                onKeyboardListener.onDigit(8);
                break;

            case R.id.key_nine:
                onKeyboardListener.onDigit(9);
                break;

            case R.id.key_dot:
                onKeyboardListener.onDot();
                break;

            case R.id.key_zero:
                onKeyboardListener.onDigit(0);
                break;

            case R.id.amount_backspace_image_view:
                if (this.amountString.length() == 1) {
                    this.amountString = "0";
                } else {
                    this.amountString = this.amountString.substring(0, this.amountString.length() - 1);
                }
                this.updateAmount();
                break;

            case R.id.amount_switch_image_view:
                this.isBitcoinLargeAmount = !this.isBitcoinLargeAmount;
                this.updateAmount();
                break;

            case R.id.key_accept:
                onKeyboardListener.onEnter();
                break;

            case R.id.key_plus:
                onPlus(amountString);
                break;

            case R.id.key_multiply:
                // TODO Add multiply funct.
                break;

            case R.id.key_clear:
                this.sumString = "";
                this.amountString = "0";
                updateAmount();
                updateSum();
                break;

            case R.id.key_custom_one:
                onKeyboardListener.onCustom(1);
                break;
            case R.id.key_custom_two:
                onKeyboardListener.onCustom(2);
                break;
            case R.id.key_custom_three:
                onKeyboardListener.onCustom(3);
                break;
            case R.id.key_custom_four:
                onKeyboardListener.onCustom(4);
                break;
            case R.id.key_custom_five:
                onKeyboardListener.onCustom(5);
                break;
            case R.id.key_custom_six:
                onKeyboardListener.onCustom(6);
                break;
            case R.id.key_custom_seven:
                onKeyboardListener.onCustom(7);
                break;
            case R.id.key_custom_eight:
                onKeyboardListener.onCustom(8);
                break;
        }

    }


    protected Coin getCoin() {
        if (isBitcoinLargeAmount) {
            return Coin.parseCoin(this.amountString);
        } else {
            return exchangeRate.fiatToCoin(this.getFiat());
        }
    }

    protected Fiat getFiat() {
        if (!isBitcoinLargeAmount) {
            return Fiat.parseFiat(currencyCode, this.amountString);
        } else {
            return exchangeRate.coinToFiat(this.getCoin());
        }
    }

    private void updateAmount() {
        final Coin coin = this.getCoin();
        final Fiat fiat = this.getFiat();

        final TextView smallTextView = (TextView) this.getView().findViewById(R.id.amount_small_text_view);
        final TextView largeTextView = (TextView) this.getView().findViewById(R.id.amount_large_text_view);
        if (this.isBitcoinLargeAmount) {
            largeTextView.setText(spannableStringFormatter.toLargeSpannable(this.amountString, "BTC"));
            smallTextView.setText(spannableStringFormatter.toSmallSpannable(fiat.toPlainString(), "CHF"));
        } else {
            largeTextView.setText(spannableStringFormatter.toLargeSpannable(this.amountString, "CHF"));
            smallTextView.setText(spannableStringFormatter.toSmallSpannable(coin.toPlainString(), "BTC"));
        }
    }

    // Code partly from https://yassirh.com/2014/05/how-to-use-swiperefreshlayout-the-right-way/
    // and here: http://stackoverflow.com/a/28173911

    private final Runnable refreshing = new Runnable() {
        public void run() {
            try {
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                }, 5000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    public void onStart() {
        super.onStart();
        this.updateAmount();
    }

    @Override
    public void onDigit(int digit) {
        this.amountString += digit;
        this.amountString = new BigDecimal(amountString).toString();
        this.updateAmount();
    }

    @Override
    public void onDot() {
        if (!this.amountString.contains(".")) {
            this.amountString += ".";
        }
        this.updateAmount();
    }

    @Override
    public void onEnter() {
        if (this.getCoin().isPositive()) {
            this.getDialogFragment().show(this.getFragmentManager(), "keyboard_dialog_fragment");
        }
    }

    @Override
    public void onPlus(String value) {
        if (this.sumString.length() == 0) {
            this.sumString += value;
        } else {
            this.sumString += ("+" + value);
        }
        this.amountString = "0";
        updateAmount();
        this.updateSum();
    }


    private void updateSum() {
        final TextView sumTextView = (TextView) this.getView().findViewById(R.id.sum_values_text_view);
        sumTextView.setText((sumString + "=" + this.getResult(sumString)));
    }

    protected abstract DialogFragment getDialogFragment();

    public void onCustom(int customKey) {

        if (this.prefs.contains(Integer.toString(customKey))) {
            try {
                this.onPlus((getPrefForCustomKey(Integer.toString(customKey))).get(1));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            CustomValueDialog cvd = new CustomValueDialog(getContext(), Integer.toString(customKey));
            cvd.setCustomValueListener(new CustomValueDialog.CustomValueListener() {
                @Override
                public void onSharedPrefsUpdated(String customKey) {
                    initCustomButton(customKey);
                }
            });
            cvd.show();
        }
    }


    public void initCustomButton(String customKey) {

        List<String> contentList = getPrefForCustomKey(customKey);

        if (contentList != null) {
            switch (customKey) {
                case "1":
                    ((TextView) this.getActivity().findViewById(R.id.key_custom_one))
                            .setText(spannableStringFormatter.toFriendlyProductString(contentList.get(0), contentList.get(1)));
                    break;
                case "2":
                    ((TextView) this.getActivity().findViewById(R.id.key_custom_two))
                            .setText(spannableStringFormatter.toFriendlyProductString(contentList.get(0), contentList.get(1)));
                    break;
                case "3":
                    ((TextView) this.getActivity().findViewById(R.id.key_custom_three))
                            .setText(spannableStringFormatter.toFriendlyProductString(contentList.get(0), contentList.get(1)));
                    break;
                case "4":
                    ((TextView) this.getActivity().findViewById(R.id.key_custom_four))
                            .setText(spannableStringFormatter.toFriendlyProductString(contentList.get(0), contentList.get(1)));
                    break;
                case "5":
                    ((TextView) this.getActivity().findViewById(R.id.key_custom_five))
                            .setText(spannableStringFormatter.toFriendlyProductString(contentList.get(0), contentList.get(1)));
                    break;
                case "6":
                    ((TextView) this.getActivity().findViewById(R.id.key_custom_six))
                            .setText(spannableStringFormatter.toFriendlyProductString(contentList.get(0), contentList.get(1)));
                    break;
                case "7":
                    ((TextView) this.getActivity().findViewById(R.id.key_custom_seven))
                            .setText(spannableStringFormatter.toFriendlyProductString(contentList.get(0), contentList.get(1)));
                    break;
                case "8":
                    ((TextView) this.getActivity().findViewById(R.id.key_custom_eight))
                            .setText(spannableStringFormatter.toFriendlyProductString(contentList.get(0), contentList.get(1)));
                    break;
            }
        }

    }

    private List<String> getPrefForCustomKey(String customKey) {
        String json = prefs.getString(customKey, null);
        Gson gson = new Gson();
        String[] contentArray = gson.fromJson(json, String[].class);
        List<String> contentList = Arrays.asList(contentArray);
        return contentList;
    }

    private void initCustomButtons(String customKey) {
        String json = prefs.getString(customKey, null);
        if (json != null) {
            this.initCustomButton(customKey);
        }
    }

    private String getResult(String amounts) {
        String delims = "[+]";
        String result = "";
        String[] tokens = amounts.split(delims);
        if (tokens.length > 1){
            BigDecimal sum = new BigDecimal(0);
            for (int i = 0; i < tokens.length; i++){
                sum = sum.add(new BigDecimal(tokens[i]));
            }
            result = sum.toString();
        }

        return result;
    }

}
