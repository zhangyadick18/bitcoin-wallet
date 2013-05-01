/*
 * Copyright 2011-2013 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui;

import java.math.BigInteger;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.bitcoin.core.Wallet;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.ExchangeRatesProvider;
import de.schildbach.wallet.ExchangeRatesProvider.ExchangeRate;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet.R;

/**
 * @author Andreas Schildbach
 */
public final class WalletBalanceFragment extends Fragment
{
	private WalletApplication application;
	private AbstractWalletActivity activity;
	private Wallet wallet;
	private SharedPreferences prefs;
	private LoaderManager loaderManager;

	private View viewBalance;
	private CurrencyTextView viewBalanceBtc;
	private FrameLayout viewBalanceLocalFrame;
	private CurrencyTextView viewBalanceLocal;
	private View viewReplaying;

	private boolean showLocalBalance;

	private boolean replaying = false;
	private BigInteger balance = null;
	private ExchangeRate exchangeRate = null;

	private static final int ID_BALANCE_LOADER = 0;
	private static final int ID_RATE_LOADER = 1;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractWalletActivity) activity;
		this.application = (WalletApplication) activity.getApplication();
		this.wallet = application.getWallet();
		this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);
		this.loaderManager = getLoaderManager();

		showLocalBalance = getResources().getBoolean(R.bool.show_local_balance);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.wallet_balance_fragment, container, false);
	}

	@Override
	public void onViewCreated(final View view, final Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);

		final boolean showExchangeRatesOption = getResources().getBoolean(R.bool.show_exchange_rates_option);

		viewBalance = view.findViewById(R.id.wallet_balance);
		if (showExchangeRatesOption)
		{
			viewBalance.setOnClickListener(new OnClickListener()
			{
				public void onClick(final View v)
				{
					startActivity(new Intent(getActivity(), ExchangeRatesActivity.class));
				}
			});
		}
		else
		{
			viewBalance.setEnabled(false);
		}

		viewBalanceBtc = (CurrencyTextView) view.findViewById(R.id.wallet_balance_btc);
		viewBalanceBtc.setPrefix(Constants.CURRENCY_CODE_BITCOIN);

		viewBalanceLocalFrame = (FrameLayout) view.findViewById(R.id.wallet_balance_local_frame);
		if (showExchangeRatesOption)
			viewBalanceLocalFrame.setForeground(getResources().getDrawable(R.drawable.dropdown_ic_arrow_small));

		viewBalanceLocal = (CurrencyTextView) view.findViewById(R.id.wallet_balance_local);
		viewBalanceLocal.setPrecision(Constants.LOCAL_PRECISION);
		viewBalanceLocal.setInsignificantRelativeSize(1);
		viewBalanceLocal.setStrikeThru(Constants.TEST);

		viewReplaying = view.findViewById(R.id.wallet_balance_replaying);
	}

	@Override
	public void onResume()
	{
		super.onResume();

		activity.registerReceiver(broadcastReceiver, new IntentFilter(BlockchainService.ACTION_BLOCKCHAIN_STATE));

		loaderManager.initLoader(ID_BALANCE_LOADER, null, balanceLoaderCallbacks);
		loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);

		updateView();
	}

	@Override
	public void onPause()
	{
		loaderManager.destroyLoader(ID_RATE_LOADER);
		loaderManager.destroyLoader(ID_BALANCE_LOADER);

		activity.unregisterReceiver(broadcastReceiver);

		super.onPause();
	}

	private void updateView()
	{
		if (!replaying)
		{
			viewBalance.setVisibility(View.VISIBLE);

			if (!showLocalBalance)
				viewBalanceLocalFrame.setVisibility(View.GONE);

			if (balance != null)
			{
				viewBalanceBtc.setVisibility(View.VISIBLE);
				viewBalanceBtc.setPrecision(Integer.parseInt(prefs.getString(Constants.PREFS_KEY_BTC_PRECISION,
						Integer.toString(Constants.BTC_PRECISION))));
				viewBalanceBtc.setAmount(balance);

				if (showLocalBalance)
				{
					if (exchangeRate != null)
					{
						final BigInteger localValue = WalletUtils.localValue(balance, exchangeRate.rate);
						viewBalanceLocalFrame.setVisibility(View.VISIBLE);
						viewBalanceLocal.setPrefix(Constants.PREFIX_ALMOST_EQUAL_TO + exchangeRate.currencyCode);
						viewBalanceLocal.setAmount(localValue);
						viewBalanceLocal.setTextColor(getResources().getColor(R.color.fg_less_significant));
					}
					else
					{
						viewBalanceLocalFrame.setVisibility(View.INVISIBLE);
					}
				}
			}
			else
			{
				viewBalanceBtc.setVisibility(View.INVISIBLE);
			}

			viewReplaying.setVisibility(View.GONE);
		}
		else
		{
			viewReplaying.setVisibility(View.VISIBLE);
			viewBalance.setVisibility(View.INVISIBLE);
		}
	}

	private final BlockchainBroadcastReceiver broadcastReceiver = new BlockchainBroadcastReceiver();

	private final class BlockchainBroadcastReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			replaying = intent.getBooleanExtra(BlockchainService.ACTION_BLOCKCHAIN_STATE_REPLAYING, false);

			updateView();
		}
	}

	private final LoaderCallbacks<BigInteger> balanceLoaderCallbacks = new LoaderManager.LoaderCallbacks<BigInteger>()
	{
		public Loader<BigInteger> onCreateLoader(final int id, final Bundle args)
		{
			return new WalletBalanceLoader(activity, wallet);
		}

		public void onLoadFinished(final Loader<BigInteger> loader, final BigInteger balance)
		{
			WalletBalanceFragment.this.balance = balance;

			updateView();
		}

		public void onLoaderReset(final Loader<BigInteger> loader)
		{
		}
	};

	private final LoaderCallbacks<Cursor> rateLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>()
	{
		public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
		{
			return new ExchangeRateLoader(activity);
		}

		public void onLoadFinished(final Loader<Cursor> loader, final Cursor data)
		{
			if (data != null)
			{
				data.moveToFirst();
				exchangeRate = ExchangeRatesProvider.getExchangeRate(data);
				updateView();
			}
		}

		public void onLoaderReset(final Loader<Cursor> loader)
		{
		}
	};
}
