/*
 * Copyright 2016 The Coinblesk team and the CSG Group at University of Zurich
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package com.coinblesk.client.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.coinblesk.client.R;
import com.coinblesk.client.config.Constants;
import com.coinblesk.client.models.TransactionWrapper;
import com.coinblesk.util.BitcoinUtils;
import com.google.gson.Gson;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.utils.BtcFixedFormat;
import org.bitcoinj.utils.BtcFormat;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;
import org.bitcoinj.utils.MonetaryFormat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author ckiller
 * @author Andreas Albrecht
 */

public class UIUtils {
    private static final String TAG = UIUtils.class.getName();

    private static final Float CONNECTION_ICON_ENABLED = 0.8f;
    private static final Float CONNECTION_ICON_DISABLED = 0.25f;  // see: styles.xml -> card_view_connection_icon
    private static final String COLOR_MATERIAL_LIGHT_YELLOW_900 = "#F47F1F"; // see: color_material.xml
    private static final String COLOR_COLOR_ACCENT = "#AEEA00";
    private static final String COLOR_WHITE = "#FFFFFF";

    public static SpannableString getLargeBalance(Context context, Coin balanceCoin, Fiat balanceFiat) {
        SpannableString result;
        if (SharedPrefUtils.isBitcoinPrimaryBalance(context)) {
            String coinScale = SharedPrefUtils.getBitcoinScalePrefix(context);
            result = toLargeSpannable(context, scaleCoin(context, balanceCoin), coinScale);
        } else if (SharedPrefUtils.isFiatPrimaryBalance(context)) {
            result = toLargeSpannable(context, balanceFiat.toPlainString(), balanceFiat.getCurrencyCode());
        } else {
            Log.e(TAG, "Unknown setting for primary balance: " + SharedPrefUtils.getPrimaryBalance(context));
            result = new SpannableString("N/A");
        }
        return result;
    }

    public static SpannableString getSmallBalance(Context context, Coin balanceCoin, Fiat balanceFiat) {
        SpannableString result;
        if (SharedPrefUtils.isBitcoinPrimaryBalance(context)) {
            result = toSmallSpannable(balanceFiat.toPlainString(), balanceFiat.getCurrencyCode());
        } else if (SharedPrefUtils.isFiatPrimaryBalance(context)) {
            String btcSymbol = SharedPrefUtils.getBitcoinScalePrefix(context);
            result = toSmallSpannable(scaleCoin(context, balanceCoin), btcSymbol);
        } else {
            Log.e(TAG, "Unknown setting for primary balance: " + SharedPrefUtils.getPrimaryBalance(context));
            result = new SpannableString("N/A");
        }
        return result;
    }

    public static String scaleCoin(Context context, Coin coin) {
        String result;
        // Dont try to use the Builder,"You cannot invoke both scale() and style()"... Add Symbol (Style) Manually

        if (SharedPrefUtils.isBitcoinScaleBTC(context)) {
            result = BtcFormat.getInstance(BtcFormat.COIN_SCALE).format(coin);
        } else if (SharedPrefUtils.isBitcoinScaleMilliBTC(context)) {
            result = BtcFormat.getInstance(BtcFormat.MILLICOIN_SCALE).format(coin);
        } else if (SharedPrefUtils.isBitcoinScaleMicroBTC(context)) {
            result = BtcFormat.getInstance(BtcFormat.MICROCOIN_SCALE).format(coin);
        } else {
            throw new RuntimeException("Unknown coin scale.");
        }

        return result;
    }

    public static SpannableString scaleCoinForDialogs(Context context, Coin coin) {
        String result = "n/a";
        String coinDenomination = SharedPrefUtils.getBitcoinScalePrefix(context);
        // Dont try to use the Builder,"You cannot invoke both scale() and style()"... Add Symbol (Style) Manually
        if (SharedPrefUtils.isBitcoinScaleBTC(context)) {
            result = BtcFormat.getInstance(BtcFormat.COIN_SCALE).format(coin, 0, BtcFixedFormat.REPEATING_PLACES);
        } else if (SharedPrefUtils.isBitcoinScaleMilliBTC(context)) {
            result = BtcFormat.getInstance(BtcFormat.MILLICOIN_SCALE).format(coin, 0, BtcFixedFormat.REPEATING_PLACES);
        } else if (SharedPrefUtils.isBitcoinScaleMicroBTC(context)) {
            result = BtcFormat.getInstance(BtcFormat.MICROCOIN_SCALE).format(coin, 0, BtcFixedFormat.REPEATING_PLACES);
        }

        // 1.3F Size Span necessary - otherwise Overflowing Edge of Dialog
        float sizeSpan = 1.3F;
        return toLargeSpannable(context, result, coinDenomination, sizeSpan);
    }

    public static Coin getValue(Context context, long amount) {
        BigDecimal bdAmount = new BigDecimal(amount);
        BigDecimal multiplicand = new BigDecimal(Coin.COIN.getValue());

        if (SharedPrefUtils.isBitcoinScaleBTC(context)) {
            multiplicand = new BigDecimal(Coin.COIN.getValue());
        } else if (SharedPrefUtils.isBitcoinScaleMilliBTC(context)) {
            multiplicand = new BigDecimal((Coin.MILLICOIN.getValue()));
        } else if (SharedPrefUtils.isBitcoinScaleMicroBTC(context)) {
            multiplicand = new BigDecimal((Coin.MICROCOIN.getValue()));
        }
        return Coin.valueOf((bdAmount.multiply(multiplicand).longValue()));
    }


    public static Coin getValue(Context context, String amount) {
        BigDecimal bdAmount = new BigDecimal(amount);
        BigDecimal multiplicand = new BigDecimal(Coin.COIN.getValue());

        if (SharedPrefUtils.isBitcoinScaleBTC(context)) {
            multiplicand = new BigDecimal(Coin.COIN.getValue());
        } else if (SharedPrefUtils.isBitcoinScaleMilliBTC(context)) {
            multiplicand = new BigDecimal((Coin.MILLICOIN.getValue()));
        } else if (SharedPrefUtils.isBitcoinScaleMicroBTC(context)) {
            multiplicand = new BigDecimal((Coin.MICROCOIN.getValue()));
        }
        return Coin.valueOf((bdAmount.multiply(multiplicand).longValue()));
    }

    public static int scale(Context context) {
        if (SharedPrefUtils.isBitcoinScaleBTC(context)) {
            return 100000000;
        } else if (SharedPrefUtils.isBitcoinScaleMilliBTC(context)) {
            return 100000;
        } else if (SharedPrefUtils.isBitcoinScaleMicroBTC(context)) {
            return 100;
        }
        throw new RuntimeException("unknown scale");
    }

    public static BtcFormat formater(Context context) {
        if (SharedPrefUtils.isBitcoinScaleBTC(context)) {
            return BtcFormat.builder().scale(BtcFormat.COIN_SCALE).build();
        } else if (SharedPrefUtils.isBitcoinScaleMilliBTC(context)) {
            return BtcFormat.builder().scale(BtcFormat.MILLICOIN_SCALE).build();
        } else if (SharedPrefUtils.isBitcoinScaleMicroBTC(context)) {
            return BtcFormat.builder().scale(BtcFormat.MICROCOIN_SCALE).build();
        }
        throw new RuntimeException("unknown format");
    }

    public static String coinToAmount(Context context, Coin coin) {
        // transform a given coin value to the "amount string".
        BigDecimal coinAmount = new BigDecimal(coin.getValue());
        BigDecimal div = new BigDecimal(Coin.COIN.getValue());

        if (SharedPrefUtils.isBitcoinScaleBTC(context)) {
            div = new BigDecimal(Coin.COIN.getValue());
        } else if (SharedPrefUtils.isBitcoinScaleMilliBTC(context)) {
            div = new BigDecimal(Coin.MILLICOIN.getValue());
        } else if (SharedPrefUtils.isBitcoinScaleMicroBTC(context)) {
            div = new BigDecimal(Coin.MICROCOIN.getValue());
        }

        DecimalFormat df = new DecimalFormat("#.####");
        df.setRoundingMode(RoundingMode.DOWN);
        df.setMaximumFractionDigits(4);
        DecimalFormatSymbols decFormat = new DecimalFormatSymbols();
        decFormat.setDecimalSeparator('.');
        df.setDecimalFormatSymbols(decFormat);
        String amount = df.format(coinAmount.divide(div));
        return amount;
    }

    public static int pow(int nr, int exp) {
        //O(n), but we use it for small numbers only for exp, so don't bother
        if(exp > 8) {
            throw new RuntimeException("only for small numbers");
        }
        int result = 1;
        for (int i = 1; i <= exp; i++) {
            result *= nr;
        }
        return result;
    }

    public static SpannableString toSmallSpannable(String amount, String currency) {
        StringBuffer stringBuffer = new StringBuffer(amount + " " + currency);
        return new SpannableString(stringBuffer);
    }

    public static SpannableString toLargeSpannable(Context context, String amount, String currency) {
        final int amountLength = amount.length();
        SpannableString result = new SpannableString(new StringBuffer(amount + " " + currency));
        result.setSpan(new RelativeSizeSpan(2), 0, amountLength, 0);
        result.setSpan(new ForegroundColorSpan(Color.WHITE), 0, amountLength, 0);
        result.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.colorAccent)), amountLength, result.length(), 0);
        return result;
    }

    public static SpannableString toLargeSpannable(Context context, String amount, String currency, float sizeSpan) {
        final int amountLength = amount.length();
        SpannableString result = new SpannableString(new StringBuffer(amount + " " + currency));
        result.setSpan(new RelativeSizeSpan(sizeSpan), 0, amountLength, 0);
        result.setSpan(new ForegroundColorSpan(Color.WHITE), 0, amountLength, 0);
        result.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.colorAccent)), amountLength, result.length(), 0);
        return result;
    }

    public static SpannableString coinFiatSpannable(Context context, Coin amountCoin, ExchangeRate exchangeRate, boolean primaryIsCoin, float secondaryRelativeSize) {
        Fiat amountFiat = null;
        if (exchangeRate != null && amountCoin != null) {
            amountFiat = exchangeRate.coinToFiat(amountCoin);
        }
        return coinFiatSpannable(context, amountCoin, amountFiat, primaryIsCoin, secondaryRelativeSize);
    }

    public static SpannableString coinFiatSpannable(Context context, Coin amountCoin, Fiat amountFiat, boolean primaryIsCoin, float secondaryRelativeSize) {
        String amountCoinStr = null, coinCode = null;
        if (amountCoin != null) {
            // For Coin: respect the BTC, mBTC, uBTC settings
            MonetaryFormat formatter = getMoneyFormat(context);
            amountCoinStr = formatter.noCode().format(amountCoin).toString();
            coinCode = formatter.code();
        }

        String amountFiatStr = null, fiatCode = null;
        if (amountFiat != null) {
            amountFiatStr = amountFiat.toPlainString();
            fiatCode = amountFiat.currencyCode;
        }

        if (primaryIsCoin) {
            return coinFiatSpannable(context, amountCoinStr, coinCode, amountFiatStr, fiatCode, secondaryRelativeSize);
        } else {
            return coinFiatSpannable(context, amountFiatStr, fiatCode, amountCoinStr, coinCode, secondaryRelativeSize);
        }
    }

    private static SpannableString coinFiatSpannable(Context context, String amountFirst, String codeFirst, String amountSecond, String codeSecond, float secondaryRelativeSize) {
        if (amountFirst == null) amountFirst = "";
        if (codeFirst == null) codeFirst = "";
        if (amountSecond == null) amountSecond = "";
        if (codeSecond == null) codeSecond = "";

        StringBuffer resultBuffer = new StringBuffer();

        resultBuffer.append(amountFirst).append(" ");
        int lenFirstAmount = resultBuffer.length();
        resultBuffer.append(codeFirst);
        int lenFirstCode = resultBuffer.length();

        resultBuffer.append(" ").append(amountSecond).append(" ").append(codeSecond);
        int lenSecond = resultBuffer.length();

        SpannableString formattedString = new SpannableString(resultBuffer);
        formattedString.setSpan(
                new ForegroundColorSpan(Color.WHITE),
                0, lenFirstAmount, 0);
        formattedString.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(context, R.color.colorAccent)),
                lenFirstAmount, lenFirstCode, 0);
        formattedString.setSpan(
                new RelativeSizeSpan(secondaryRelativeSize),
                lenFirstAmount, lenFirstCode, 0);

        formattedString.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(context, R.color.main_color_400)),
                lenFirstCode, lenSecond, 0);
        formattedString.setSpan(
                new RelativeSizeSpan(secondaryRelativeSize),
                lenFirstCode, lenSecond, 0);

        return formattedString;
    }

    public static int getLargeTextSize(Context context, int amountLength) {
        int textSize = context.getResources().getInteger(R.integer.text_size_xxlarge);
        final int orientation = context.getResources().getConfiguration().orientation;
        switch (orientation) {
            case Configuration.ORIENTATION_LANDSCAPE:
                textSize = context.getResources().getInteger(R.integer.text_size_large_landscape);
                if (amountLength > 6)
                    textSize = context.getResources().getInteger(R.integer.text_size_medium_landscape);
                if (amountLength > 7)
                    textSize = context.getResources().getInteger(R.integer.text_size_small_landscape);
                break;
            case Configuration.ORIENTATION_PORTRAIT:
                if (amountLength > 6)
                    textSize = context.getResources().getInteger(R.integer.text_size_xlarge);
                if (amountLength > 7)
                    textSize = context.getResources().getInteger(R.integer.text_size_large);
                if (amountLength > 8)
                    textSize = context.getResources().getInteger(R.integer.text_size_medium);
                break;
        }

        return textSize;
    }

    public static int getLargeTextSizeForBalance(Context context, int amountLength) {

        int textSize = context.getResources().getInteger(R.integer.text_size_xxlarge);
        final int orientation = context.getResources().getConfiguration().orientation;
        switch (orientation) {
            case Configuration.ORIENTATION_LANDSCAPE:
                textSize = context.getResources().getInteger(R.integer.text_size_large_landscape);
                if (amountLength > 10)
                    textSize = context.getResources().getInteger(R.integer.text_size_medium_landscape);
                if (amountLength > 12)
                    textSize = context.getResources().getInteger(R.integer.text_size_small_landscape);
                break;
            case Configuration.ORIENTATION_PORTRAIT:
                if (amountLength > 12)
                    textSize = context.getResources().getInteger(R.integer.text_size_xlarge);
                if (amountLength > 13)
                    textSize = context.getResources().getInteger(R.integer.text_size_large);
                if (amountLength > 14)
                    textSize = context.getResources().getInteger(R.integer.text_size_medium);
                break;
        }

        return textSize;
    }

    public static int getLargeTextSizeForDialogs(Context context, int amountLength) {

        int textSize = context.getResources().getInteger(R.integer.text_size_xxlarge);
        final int orientation = context.getResources().getConfiguration().orientation;
        switch (orientation) {
            case Configuration.ORIENTATION_LANDSCAPE:
                textSize = context.getResources().getInteger(R.integer.text_size_large_landscape);
                if (amountLength > 8)
                    textSize = context.getResources().getInteger(R.integer.text_size_medium_landscape);
                if (amountLength > 9)
                    textSize = context.getResources().getInteger(R.integer.text_size_small_landscape);
                break;
            case Configuration.ORIENTATION_PORTRAIT:
                if (amountLength > 8)
                    textSize = context.getResources().getInteger(R.integer.text_size_xlarge);
                if (amountLength > 9)
                    textSize = context.getResources().getInteger(R.integer.text_size_large);
                if (amountLength > 10)
                    textSize = context.getResources().getInteger(R.integer.text_size_medium);
                break;
        }

        return textSize;
    }


    public static SpannableString toFriendlyAmountString(Context context, TransactionWrapper transaction) {
        StringBuffer friendlyAmount = new StringBuffer();

        MonetaryFormat formatter = getMoneyFormat(context);
        String btcCode = formatter.code();
        String scaledAmount = formatter.noCode().format(transaction.getAmount()).toString();
        friendlyAmount.append(scaledAmount).append(" ");
        final int coinLength = friendlyAmount.length();

        friendlyAmount.append(btcCode).append(" ");
        final int codeLength = friendlyAmount.length();

        ExchangeRate exchangeRate = transaction.getTransaction().getExchangeRate();
        if (exchangeRate != null) {
            Fiat fiat = exchangeRate.coinToFiat(transaction.getAmount());
            friendlyAmount.append("~ " + fiat.toFriendlyString());
            friendlyAmount.append(System.getProperty("line.separator") + "(1 BTC = "
                    + exchangeRate.fiat.toFriendlyString() + " as of now)");
        }
        final int amountLength = friendlyAmount.length();

        SpannableString friendlySpannable = new SpannableString(friendlyAmount);
        friendlySpannable.setSpan(new RelativeSizeSpan(2), 0, coinLength, 0);
        friendlySpannable.setSpan(
                new ForegroundColorSpan(context.getResources().getColor(R.color.colorAccent)),
                coinLength, codeLength, 0);
        friendlySpannable.setSpan(
                new ForegroundColorSpan(context.getResources().getColor(R.color.main_color_400)),
                codeLength, amountLength, 0);
        return friendlySpannable;

    }

    public static SpannableString toFriendlyFeeString(Context context, Transaction tx) {
        Coin fee = tx.getFee();
        ExchangeRate exchangeRate = tx.getExchangeRate();
        if (fee == null) {
            return new SpannableString("");
        }

        StringBuffer friendlyFee = new StringBuffer(UIUtils.formatCoin(context, fee));
        int feeLength = friendlyFee.length();

        int exchangeRateLength = feeLength;
        if (exchangeRate != null) {
            friendlyFee.append(" ~ " + exchangeRate.coinToFiat(fee).toFriendlyString());
            exchangeRateLength = friendlyFee.length();
        }


        SpannableString friendlySpannable = new SpannableString(friendlyFee);
        friendlySpannable.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(context, R.color.main_color_400)),
                feeLength,
                exchangeRateLength,
                0);
        return friendlySpannable;

    }

    public static String formatCustomButton(String description, String amount) {
        String result = amount + System.getProperty("line.separator") + description;
        return result;
    }

    public static SpannableString toFriendlySnackbarString(Context context, String input) {
        final ForegroundColorSpan whiteSpan = new ForegroundColorSpan(ContextCompat.getColor(context, R.color.colorAccent));
        final SpannableString snackbarText = new SpannableString(input);
        snackbarText.setSpan(whiteSpan, 0, snackbarText.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        return snackbarText;
    }


    public static String getSum(String amounts) {
        String delims = "[+]";
        String result = "0";
        String[] tokens = amounts.split(delims);
        if (tokens.length > 1) {
            BigDecimal sum = new BigDecimal(0);
            for (int i = 0; i < tokens.length; i++) {
                sum = sum.add(new BigDecimal(tokens[i]));
            }
            result = sum.toString();
        }
        return result;
    }

    public static boolean stringIsNotZero(String amountString){
        //Checks if a string is actually of Zero value 0.00 0 0.000 etc.
        BigDecimal bd = new BigDecimal(amountString);
        return bd.compareTo(BigDecimal.ZERO) != 0;
    }

    public static List<String> getCustomButton(Context context, String customKey) {
        if (!SharedPrefUtils.isCustomButtonEmpty(context, customKey)) {
            String json = SharedPrefUtils.getCustomButton(context, customKey);
            Gson gson = new Gson();
            String[] contentArray = gson.fromJson(json, String[].class);
            List<String> contentList;
            try {
                contentList = Arrays.asList(contentArray);
                return contentList;
            } catch (Exception e) {
                Log.e(TAG, "Could not decode content from json to a list.");
            }
        }

        return null;
    }

    /**
     * Updates the connection icons (enables/disables the icons)
     * @param context
     * @param container root view
     */
    public static void refreshConnectionIconStatus(Context context, View container) {
        if (container == null) {
            return;
        }

        UIUtils.formatConnectionIcon(
                context,
                (ImageView) container.findViewById(R.id.nfc_balance),
                SharedPrefUtils.isConnectionNfcEnabled(context));
        UIUtils.formatConnectionIcon(
                context,
                (ImageView) container.findViewById(R.id.bluetooth_balance),
                SharedPrefUtils.isConnectionBluetoothLeEnabled(context));
        UIUtils.formatConnectionIcon(
                context,
                (ImageView) container.findViewById(R.id.wifidirect_balance),
                SharedPrefUtils.isConnectionWiFiDirectEnabled(context));

    }


    /* sets the icon style (color and alpha) depending on isEnabled */
    private static void formatConnectionIcon(Context context, ImageView icon, boolean isEnabled) {
        if (icon == null) {
            return;
        }

        if (isEnabled) {
            icon.setAlpha(CONNECTION_ICON_ENABLED);
            icon.setColorFilter(ContextCompat.getColor(context, R.color.colorAccent));
        } else {
            icon.setAlpha(CONNECTION_ICON_DISABLED);
            icon.clearColorFilter();
        }
    }

    public static int getFractionalLengthFromString(String amount) {
        // Escape '.' otherwise won't work
        String delims = "\\.";
        int length = -1;
        String[] tokens = amount.split(delims);
        if (tokens.length == 2)
            length = tokens[1].length();
        return length;
    }

    public static int getIntegerLengthFromString(String amount) {
        // Escape '.' otherwise won't work
        String delims = "\\.";
        int length = -1;
        String[] tokens = amount.split(delims);
        if (tokens.length == 1)
            length = tokens[0].length();
        return length;
    }

    public static boolean isDecimal(String amount) {
        return amount.contains(".");

    }

    public static int getDecimalThreshold(Context context) {
        if (SharedPrefUtils.isBitcoinScaleBTC(context)) {
            return 4;
        } else if (SharedPrefUtils.isBitcoinScaleMilliBTC(context)) {
            return 5;
        } else if (SharedPrefUtils.isBitcoinScaleMicroBTC(context)) {
            return 2;
        }

        return 2;
    }

    public static int getStatusColorFilter(TransactionWrapper tx) {
        if (ClientUtils.isConfidenceReached(tx)) {
            return Color.parseColor(COLOR_WHITE);
        } else {
            return Color.parseColor(COLOR_MATERIAL_LIGHT_YELLOW_900);
        }
    }

    public static String lockedUntilText(long lockTime) {
        String lockedUntil;
        if (BitcoinUtils.isLockTimeByTime(lockTime)) {
            lockedUntil = DateFormat.getDateTimeInstance().format(new Date(lockTime * 1000L));
        } else {
            lockedUntil = String.format(Locale.US, "block %d", lockTime);
        }
        return lockedUntil;
    }

    public static Drawable tintIconAccent(Drawable drawable, Context context) {
        int tint = context.getResources().getColor(R.color.colorAccent);
        return tintIcon(drawable, tint);
    }

    public static Drawable tintIconWhite(Drawable drawable, Context context) {
        return tintIcon(drawable, Color.WHITE);
    }

    public static Drawable tintIcon(Drawable drawable, @ColorInt int tint) {
        drawable = DrawableCompat.wrap(drawable);
        DrawableCompat.setTint(drawable, tint);
        return drawable;
    }

    public static String formatCoin(Context context, Coin coin) {
        return getMoneyFormat(context).format(coin).toString();
    }

    public static MonetaryFormat getMoneyFormat(Context context) {
        if (SharedPrefUtils.isBitcoinScaleBTC(context)) {
            return MonetaryFormat.BTC.postfixCode();
        } else if (SharedPrefUtils.isBitcoinScaleMilliBTC(context)) {
            return MonetaryFormat.MBTC.postfixCode();
        } else if (SharedPrefUtils.isBitcoinScaleMicroBTC(context)) {
            return MonetaryFormat.UBTC.postfixCode();
        } else {
            return MonetaryFormat.BTC.postfixCode();
        }
    }
}
