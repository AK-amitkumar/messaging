/**
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
package com.openerp.addons.note;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import odoo.OEArguments;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.openerp.R;
import com.openerp.base.res.ResPartnerDB;
import com.openerp.orm.OEDataRow;
import com.openerp.orm.OEHelper;
import com.openerp.orm.OEM2MIds.Operation;
import com.openerp.support.BaseFragment;
import com.openerp.support.listview.OEListAdapter;
import com.openerp.util.Base64Helper;
import com.openerp.util.contactview.OEContactView;
import com.openerp.util.drawer.DrawerItem;
import com.openerp.util.tags.MultiTagsTextView.TokenListener;
import com.openerp.util.tags.TagsView;
import com.openerp.util.tags.TagsView.CustomTagViewListener;

public class NoteFollowers extends BaseFragment implements TokenListener,
		OnClickListener {
	public static final String TAG = "com.openerp.addons.note.NoteFollowers";
	View mView = null;
	TagsView mFollowersTag = null;
	GridView mNoteFollowerGrid = null;
	OEListAdapter mNoteFollowerAdapter = null;
	Bundle mArguments = null;
	List<Object> mFollowerList = new ArrayList<Object>();
	List<Object> mPartnersList = new ArrayList<Object>();
	PartnerLoader mPartnersLoader = null;
	Context mContext = null;

	UnSubscribeOperation mUnSubscriber = null;
	SubscribeOperation mSubscriber = null;
	HashMap<String, OEDataRow> mSelectedPartners = new HashMap<String, OEDataRow>();
	OEListAdapter mTagsAdapter = null;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		setHasOptionsMenu(true);
		mView = inflater.inflate(R.layout.fragment_note_followers, container,
				false);
		mContext = getActivity();
		return mView;
	}

	@Override
	public void onStart() {
		super.onStart();
		mArguments = getArguments();
		getActivity().setTitle(mArguments.getString("note_name"));
		OEDataRow note = db().select(mArguments.getInt("note_id"));
		mFollowerList.clear();
		mFollowerList.addAll(note.getM2MRecord("message_follower_ids")
				.browseEach());
		mPartnersLoader = new PartnerLoader(getActivity());
		mPartnersLoader.execute();
		setupTagsView();
		setupGridView();
	}

	private void setupTagsView() {
		mFollowersTag = (TagsView) mView.findViewById(R.id.edtNoteFollowers);
		mFollowersTag.setCustomTagView(new CustomTagViewListener() {

			@Override
			public View getViewForTags(LayoutInflater layoutInflater,
					Object object, ViewGroup tagsViewGroup) {
				OEDataRow row = (OEDataRow) object;
				View mView = layoutInflater.inflate(
						R.layout.fragment_message_receipient_tag_layout, null);
				TextView txvSubject = (TextView) mView
						.findViewById(R.id.txvTagSubject);
				txvSubject.setText(row.getString("name"));
				ImageView imgPic = (ImageView) mView
						.findViewById(R.id.imgTagImage);
				if (!row.getString("image_small").equals("false")) {
					imgPic.setImageBitmap(Base64Helper.getBitmapImage(mContext,
							row.getString("image_small")));
				}
				return mView;
			}
		});
		mFollowersTag.setTokenListener(this);
		mTagsAdapter = new OEListAdapter(getActivity(),
				R.layout.tags_view_partner_item_layout, mPartnersList) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				View mView = convertView;
				if (mView == null) {
					mView = getActivity().getLayoutInflater().inflate(
							getResource(), parent, false);
				}
				return generateView(mView, mPartnersList.get(position));
			}
		};
		mFollowersTag.setAdapter(mTagsAdapter);
		mView.findViewById(R.id.imgBtnAddFollower).setOnClickListener(this);
	}

	private View generateView(View mView, Object obj) {
		OEDataRow item = (OEDataRow) obj;
		TextView txvSubSubject, txvSubject;
		ImageView imgPic = (ImageView) mView
				.findViewById(R.id.imgReceipientPic);
		txvSubject = (TextView) mView.findViewById(R.id.txvSubject);
		txvSubSubject = (TextView) mView.findViewById(R.id.txvSubSubject);
		txvSubject.setText(item.getString("name"));
		if (!item.getString("email").equals("false")) {
			txvSubSubject.setText(item.getString("email"));
		} else {
			txvSubSubject.setText("No email");
		}
		if (item.getString("image_small") != null
				&& !item.getString("image_small").equals("false")) {
			imgPic.setImageBitmap(Base64Helper.getBitmapImage(getActivity(),
					item.getString("image_small")));
		}
		return mView;
	}

	private void setupGridView() {
		mNoteFollowerGrid = (GridView) mView
				.findViewById(R.id.noteFollowersGridView);
		mNoteFollowerAdapter = new OEListAdapter(getActivity(),
				R.layout.fragment_note_followers_grid_item_view, mFollowerList) {
			@Override
			public View getView(final int position, View convertView,
					ViewGroup parent) {
				View mView = convertView;
				if (mView == null) {
					mView = getActivity().getLayoutInflater().inflate(
							getResource(), parent, false);
				}
				OEDataRow row = (OEDataRow) mFollowerList.get(position);
				OEContactView imgContact = (OEContactView) mView
						.findViewById(R.id.imgFollowerPic);

				TextView txvName = (TextView) mView
						.findViewById(R.id.txvFollowerName);
				TextView txvEmail = (TextView) mView
						.findViewById(R.id.txvFollowerEmail);
				txvName.setText(row.getString("name"));
				txvEmail.setText(row.getString("email"));

				imgContact.assignPartnerId(row.getInt("id"));
				imgContact.setImageBitmap(Base64Helper.getBitmapImage(
						getActivity(), row.getString("image_small")));
				mView.findViewById(R.id.imgFollowerRemove).setOnClickListener(
						new OnClickListener() {

							@Override
							public void onClick(View v) {
								confirmRemoveFollower(position);
							}
						});

				return mView;
			}
		};
		mNoteFollowerGrid.setAdapter(mNoteFollowerAdapter);
	}

	private void confirmRemoveFollower(final int position) {
		AlertDialog.Builder deleteDialogConfirm = new AlertDialog.Builder(
				getActivity());
		deleteDialogConfirm.setTitle("Unsubscribe");
		deleteDialogConfirm
				.setMessage("Are you sure want to remove follower ?");
		deleteDialogConfirm.setCancelable(true);

		deleteDialogConfirm.setPositiveButton("Yes",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						mUnSubscriber = new UnSubscribeOperation(position,
								mArguments.getInt("note_id"));
						mUnSubscriber.execute();
					}
				});

		deleteDialogConfirm.setNegativeButton("No", null);
		deleteDialogConfirm.show();
	}

	class UnSubscribeOperation extends AsyncTask<Void, Void, Void> {

		int mPosition = 0;
		int mNoteID = 0;
		int mPartnerId = 0;
		String mToast = "";

		public UnSubscribeOperation(int position, int note_id) {
			mPosition = position;
			mNoteID = note_id;
			OEDataRow row = (OEDataRow) mFollowerList.get(position);
			mPartnerId = row.getInt("id");
		}

		@Override
		protected Void doInBackground(Void... params) {
			OEHelper oe = db().getOEInstance();
			mToast = "No Connection.";
			if (oe != null) {
				OEArguments arguments = new OEArguments();
				arguments.add(new JSONArray().put(mNoteID));
				arguments.add(new JSONArray().put(mPartnerId));
				Boolean result = (Boolean) oe.call_kw("message_unsubscribe",
						arguments);
				if (result) {
					db().updateManyToManyRecords("message_follower_ids",
							Operation.REMOVE, mNoteID, mPartnerId);
				}
				mToast = "Follower removed";
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			mFollowerList.remove(mPosition);
			mNoteFollowerAdapter.notifiyDataChange(mFollowerList);
			Toast.makeText(getActivity(), mToast, Toast.LENGTH_LONG).show();

		}
	}

	class SubscribeOperation extends AsyncTask<Void, Void, Void> {
		List<Integer> mPartnerIds = null;
		int mNoteID = 0;
		String mToast = "";

		public SubscribeOperation(List<Integer> ids, int note_id) {
			mPartnerIds = ids;
			mNoteID = note_id;
		}

		@Override
		protected Void doInBackground(Void... params) {
			OEHelper oe = db().getOEInstance();
			mToast = "No Connection.";
			if (oe != null) {
				try {
					JSONObject args = new JSONObject();
					args.put("res_model", "note.note");
					args.put("res_id", mNoteID);
					args.put("message", "You have been invited to follow "
							+ getArguments().getString("note_name"));
					JSONArray partner_ids = new JSONArray();
					partner_ids.put(6);
					partner_ids.put(false);
					JSONArray partnerIds = new JSONArray();
					for (int id : mPartnerIds) {
						partnerIds.put(id);
					}
					partner_ids.put(partnerIds);
					args.put("partner_ids",
							new JSONArray("[" + partner_ids.toString() + "]"));
					JSONObject result = oe.openERP().createNew(
							"mail.wizard.invite", args);
					int id = result.getInt("result");
					OEArguments arguments = new OEArguments();
					arguments.add(new JSONArray("[" + id + "]"));
					JSONObject context = new JSONObject();
					context.put("default_res_model", "note.note");
					context.put("default_res_id", mNoteID);
					JSONObject res = (JSONObject) oe.call_kw(
							"mail.wizard.invite", "add_followers", arguments,
							context, null);
					if (res != null) {
						db().updateManyToManyRecords("message_follower_ids",
								Operation.APPEND, mNoteID, mPartnerIds);
						mToast = "Follower added";
						for (Object obj : mFollowersTag.getObjects()) {
							mFollowersTag.removeObject(obj);
						}
					}
				} catch (Exception e) {
				}
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			ResPartnerDB partner = new ResPartnerDB(getActivity());
			for (int id : mPartnerIds) {
				mFollowerList.add(partner.select(id));
			}
			mNoteFollowerAdapter.notifiyDataChange(mFollowerList);
			Toast.makeText(getActivity(), mToast, Toast.LENGTH_LONG).show();
		}

	}

	class PartnerLoader extends AsyncTask<Void, Void, Void> {

		ResPartnerDB mPartner = null;
		FragmentActivity mActivity = null;
		OEHelper mOpenERP = null;

		public PartnerLoader(FragmentActivity activity) {
			mPartner = new ResPartnerDB(activity);
			mActivity = activity;
			mOpenERP = mPartner.getOEInstance();
		}

		@Override
		protected Void doInBackground(Void... params) {
			mPartnersList.clear();
			if (mOpenERP != null) {
				mPartnersList.addAll(mOpenERP.search_read());
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			mTagsAdapter.notifiyDataChange(mPartnersList);
		}

	}

	@Override
	public Object databaseHelper(Context context) {
		return new NoteDB(context);
	}

	@Override
	public List<DrawerItem> drawerMenus(Context context) {
		return null;
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mPartnersLoader != null)
			mPartnersLoader.cancel(true);
		mPartnersLoader = null;
	}

	@Override
	public void onTokenAdded(Object token, View view) {
		OEDataRow tag = (OEDataRow) token;
		mSelectedPartners.put("_" + tag.getInt("id"), tag);
	}

	@Override
	public void onTokenSelected(Object token, View view) {

	}

	@Override
	public void onTokenRemoved(Object token) {
		OEDataRow tag = (OEDataRow) token;
		mSelectedPartners.remove("_" + tag.getInt("id"));
	}

	/**
	 * Add Follower click listener
	 */
	@Override
	public void onClick(View v) {
		List<Integer> ids = new ArrayList<Integer>();
		List<Object> mIds = new ArrayList<Object>();
		ResPartnerDB partner = new ResPartnerDB(getActivity());
		for (String key : mSelectedPartners.keySet()) {
			OEDataRow item = mSelectedPartners.get(key);
			ids.add(item.getInt("id"));
			mIds.add(item.getInt("id"));
		}
		OEHelper oe = partner.getOEInstance();
		if (oe != null) {
			oe.syncWithServer(false, null, mIds);
		}
		mSubscriber = new SubscribeOperation(ids, mArguments.getInt("note_id"));
		mSubscriber.execute();
	}
}
