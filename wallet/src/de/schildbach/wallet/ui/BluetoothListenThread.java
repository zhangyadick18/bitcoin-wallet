/*
 * Copyright 2012-2013 the original author or authors.
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

import java.io.DataInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import de.schildbach.wallet.Constants;

/**
 * @author Shahar Livne <shahar@square>
 */
public abstract class BluetoothListenThread extends Thread
{
	private final BluetoothServerSocket listeningSocket;
	private final AtomicBoolean running = new AtomicBoolean(true);

	public BluetoothListenThread(final BluetoothAdapter adapter)
	{
		try
		{
			this.listeningSocket = adapter.listenUsingInsecureRfcommWithServiceRecord("Bitcoin Transaction Submission", Constants.BLUETOOTH_UUID);

			start();
		}
		catch (final IOException x)
		{
			throw new RuntimeException(x);
		}
	}

	@Override
	public void run()
	{
		System.out.println("=== BTTX Thread run");
		while (running.get())
		{
			BluetoothSocket socket = null;
			DataInputStream inputStream = null;

			try
			{
				// start a blocking call, and return only on success or exception
				socket = listeningSocket.accept();
				System.out.println("=== BTTX accepted");

				inputStream = new DataInputStream(socket.getInputStream());
				final int numMessages = inputStream.readInt();

				for (int i = 0; i < numMessages; i++)
				{
					System.out.println("BTTX reading msg: " + i);
					final int msgLength = inputStream.readInt();
					final byte[] msg = new byte[msgLength];
					inputStream.readFully(msg);

					handleTx(msg);
				}
			}
			catch (final IOException x)
			{
				x.printStackTrace();
			}
			finally
			{
				if (inputStream != null)
				{
					try
					{
						inputStream.close();
					}
					catch (final IOException x)
					{
						// swallow
					}
				}

				if (socket != null)
				{
					try
					{
						socket.close();
					}
					catch (final IOException x)
					{
						// swallow
					}
				}
			}
		}
	}

	public void stopAccepting()
	{
		System.out.println("BTTX stop accepting");

		running.set(false);

		try
		{
			listeningSocket.close();
		}
		catch (final IOException x)
		{
			// swallow
		}
	}

	protected abstract void handleTx(byte[] msg);
}
