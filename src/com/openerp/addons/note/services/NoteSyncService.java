/*
 * OpenERP, Open Source Management Solution
 * Copyright (C) 2012-today OpenERP SA (<http://www.openerp.com>)
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * 
 */
package com.openerp.addons.note.services;

import odoo.OEDomain;

import org.json.JSONArray;

import android.accounts.Account;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.openerp.addons.note.NoteDB;
import com.openerp.addons.note.widgets.NoteWidget;
import com.openerp.auth.OpenERPAccountManager;
import com.openerp.base.ir.Ir_AttachmentDBHelper;
import com.openerp.orm.OEDataRow;
import com.openerp.orm.OEHelper;
import com.openerp.receivers.SyncFinishReceiver;

public class NoteSyncService extends Service {
	public static final String TAG = "com.openerp.addons.note.services.NoteSyncService";
	private static SyncAdapterImpl sSyncAdapter = null;
	static int i = 0;
	Context mContext = null;

	public NoteSyncService() {
		mContext = this;
	}

	@Override
	public IBinder onBind(Intent intent) {
		IBinder ret = null;
		ret = getSyncAdapter().getSyncAdapterBinder();
		return ret;
	}

	public SyncAdapterImpl getSyncAdapter() {

		if (sSyncAdapter == null) {
			sSyncAdapter = new SyncAdapterImpl(this);
		}
		return sSyncAdapter;
	}

	public void performSync(Context context, Account account, Bundle extras,
			String authority, ContentProviderClient provider,
			SyncResult syncResult) {

		try {
			Intent intent = new Intent();
			intent.setAction(SyncFinishReceiver.SYNC_FINISH);
			Intent updateWidgetIntent = new Intent();
			updateWidgetIntent
					.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
			updateWidgetIntent.putExtra(NoteWidget.ACTION_NOTE_WIDGET_UPDATE,
					true);

			NoteDB note = new NoteDB(context);
			note.setAccountUser(OpenERPAccountManager.getAccountDetail(context,
					account.name));
			OEHelper oe = note.getOEInstance();
			if (oe != null) {
				if (oe.syncWithServer(true)) {
					// Getting attachment of notes
					Ir_AttachmentDBHelper attachment = new Ir_AttachmentDBHelper(
							context);
					OEDomain domain = new OEDomain();
					domain.add("res_model", "=", note.getModelName());
					JSONArray note_ids = new JSONArray();
					for (OEDataRow row : note.select()) {
						note_ids.put(row.getInt("id"));
					}

					domain.add("res_id", "in", note_ids);
					attachment.getOEInstance().syncWithServer(domain);
				}
			}
			if (OpenERPAccountManager.currentUser(context).getAndroidName()
					.equals(account.name)) {
				context.sendBroadcast(intent);
				context.sendBroadcast(updateWidgetIntent);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public class SyncAdapterImpl extends AbstractThreadedSyncAdapter {
		private Context mContext;

		public SyncAdapterImpl(Context context) {
			super(context, true);
			mContext = context;
		}

		@Override
		public void onPerformSync(Account account, Bundle bundle, String str,
				ContentProviderClient providerClient, SyncResult syncResult) {
			Log.d(TAG, "Note sync service started");
			try {
				if (account != null) {
					new NoteSyncService().performSync(mContext, account,
							bundle, str, providerClient, syncResult);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	}
}
