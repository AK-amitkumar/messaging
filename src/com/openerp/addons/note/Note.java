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

import odoo.OEDomain;

import org.json.JSONArray;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.openerp.OESwipeListener.SwipeCallbacks;
import com.openerp.OETouchListener;
import com.openerp.R;
import com.openerp.addons.note.NoteDB.NoteStages;
import com.openerp.addons.note.providers.note.NoteProvider;
import com.openerp.base.ir.Attachment;
import com.openerp.base.ir.Ir_AttachmentDBHelper;
import com.openerp.orm.OEDataRow;
import com.openerp.orm.OEHelper;
import com.openerp.orm.OEValues;
import com.openerp.receivers.SyncFinishReceiver;
import com.openerp.support.AppScope;
import com.openerp.support.BaseFragment;
import com.openerp.support.OEUser;
import com.openerp.support.fragment.FragmentListener;
import com.openerp.support.listview.OEListAdapter;
import com.openerp.util.HTMLHelper;
import com.openerp.util.TextViewTags;
import com.openerp.util.drawer.DrawerColorTagListener;
import com.openerp.util.drawer.DrawerItem;
import com.openerp.util.drawer.DrawerListener;

public class Note extends BaseFragment implements
		OETouchListener.OnPullListener, SwipeCallbacks, OnClickListener,
		OnItemClickListener, DrawerColorTagListener {

	public static final String TAG = "com.openerp.addons.note.Note";
	public static final int KEY_NOTE = 222;

	View mView = null;
	String mTagColors[] = new String[] { "#C55C7E", "#6D96B7", "#10AB64",
			"#C71600", "#FFBB22", "#77DDBB", "#192823", "#0099CC", "#218559",
			"#EBB035" };
	static HashMap<String, Integer> mStageTagColors = new HashMap<String, Integer>();

	GridView mNoteGridView = null;
	List<Object> mNotesList = new ArrayList<Object>();
	OEListAdapter mNoteListAdapter = null;
	SearchView mSearchView = null;
	Integer mStageId = 0;
	NoteLoader mNoteLoader = null;
	OETouchListener mTouchListener = null;
	Boolean mSynced = false;
	EditText edtTitle = null;
	ImageView mImgBtnShowQuickNote = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		mView = inflater.inflate(R.layout.fragment_note, container, false);
		Bundle bundle = getArguments();
		if (bundle != null) {
			mStageId = bundle.getInt("stage_id");
		}
		init();
		return mView;
	}

	private void init() {
		Log.d(TAG, "Note->init()");
		scope = new AppScope(getActivity());
		mTouchListener = scope.main().getTouchAttacher();
		initControls();
	}

	private void initControls() {
		Log.d(TAG, "Note->initControls()");
		mNoteGridView = (GridView) mView.findViewById(R.id.noteGridView);
		mView.findViewById(R.id.imgBtnCreateQuickNote).setOnClickListener(this);
		mImgBtnShowQuickNote = (ImageView) mView
				.findViewById(R.id.imgBtnShowQuickNote);
		edtTitle = (EditText) mView.findViewById(R.id.edtNoteQuickTitle);
		edtTitle.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				if (s.length() != 0) {
					mImgBtnShowQuickNote.setVisibility(View.VISIBLE);
				} else {
					mImgBtnShowQuickNote.setVisibility(View.GONE);
				}

			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});
		mImgBtnShowQuickNote.setOnClickListener(this);
		mView.findViewById(R.id.imgBtnAttachImage).setOnClickListener(this);
		mView.findViewById(R.id.imgBtnAttachAudio).setOnClickListener(this);
		mView.findViewById(R.id.imgBtnAttachFile).setOnClickListener(this);
		mNotesList.clear();
		mNoteListAdapter = new OEListAdapter(getActivity(),
				R.layout.fragment_note_grid_custom_layout, mNotesList) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				View mView = convertView;
				if (mView == null) {
					mView = getActivity().getLayoutInflater().inflate(
							getResource(), parent, false);
				}
				OEDataRow row = (OEDataRow) mNotesList.get(position);
				TextView txvTitle, txvDesc, txvStage, txvTags;
				ImageView imgNoteAttachIcon;
				txvTitle = (TextView) mView.findViewById(R.id.txvNoteTitle);
				txvDesc = (TextView) mView
						.findViewById(R.id.txvNoteDescription);
				txvStage = (TextView) mView.findViewById(R.id.txvNoteStage);
				txvTags = (TextView) mView.findViewById(R.id.txvNoteTags);
				imgNoteAttachIcon = (ImageView) mView
						.findViewById(R.id.imgNoteAttachIcon);
				Ir_AttachmentDBHelper mAttachmentDB = new Ir_AttachmentDBHelper(
						getActivity());
				List<OEDataRow> attachments = mAttachmentDB.select(
						"res_model = ? AND res_id = ?", new String[] {
								db().getModelName(), row.getInt("id") + "" });
				if (attachments.size() > 0)
					imgNoteAttachIcon.setVisibility(View.VISIBLE);
				else
					imgNoteAttachIcon.setVisibility(View.GONE);
				txvTitle.setText(row.getString("name"));
				txvDesc.setText(HTMLHelper.htmlToString(row.getString("memo")));
				OEDataRow stage = row.getM2ORecord("stage_id").browse();
				txvStage.setText("New");
				int color = Color.parseColor("#ffffff");
				if (stage != null) {
					txvStage.setText(stage.getString("name"));
					Integer tagColor = getTagColor("key_"
							+ stage.getString("id"));
					if (tagColor != null) {
						color = tagColor;
					}
				}
				List<String> tags = new ArrayList<String>();
				List<OEDataRow> notetags = row.getM2MRecord("tag_ids")
						.browseEach();

				for (OEDataRow tag : notetags) {
					tags.add(tag.getString("name"));
				}
				txvTags.setText(new TextViewTags(getActivity(), tags,
						"#ebebeb", "#414141", 25).generate());
				mView.findViewById(R.id.noteGridClildView).setBackgroundColor(
						color);
				txvStage.setTextColor(color);
				return mView;
			}
		};
		mNoteGridView.setAdapter(mNoteListAdapter);
		mNoteGridView.setOnItemClickListener(this);
		mTouchListener.setPullableView(mNoteGridView, this);
		mTouchListener.setSwipeableView(mNoteGridView, this);
		mNoteGridView.setOnScrollListener(mTouchListener.makeScrollListener());
		mNoteLoader = new NoteLoader(mStageId);
		mNoteLoader.execute();
	}

	class NoteLoader extends AsyncTask<Void, Void, Void> {

		Integer mStageId = null;

		public NoteLoader(Integer stageId) {
			mStageId = stageId;
			Log.d(TAG, "Note->NoteLoader->constructor()");
		}

		@Override
		protected Void doInBackground(Void... params) {
			mNotesList.clear();
			String where = "";
			String[] whereArgs = null;
			switch (mStageId) {
			case -1:
				where = "open = ?";
				whereArgs = new String[] { "true" };
				break;
			case -2:
				where = "open = ?";
				whereArgs = new String[] { "false" };
				break;
			default:
				where = "open = ? AND stage_id = ?";
				whereArgs = new String[] { "true", mStageId + "" };
				break;
			}
			List<OEDataRow> result = db().select(where, whereArgs, null, null,
					"id DESC");
			mNotesList.addAll(result);
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			Log.d(TAG, "Note->NoteLoader->onPostExecute()");
			mNoteListAdapter.notifiyDataChange(mNotesList);
			if (mSearchView != null)
				mSearchView
						.setOnQueryTextListener(getQueryListener(mNoteListAdapter));
			mNoteLoader = null;
			mView.findViewById(R.id.loadingProgress).setVisibility(View.GONE);
			checkStatus();
		}

	}

	private void checkStatus() {
		Log.d(TAG, "Note->checkStatus()");
		if (mNotesList.size() == 0) {
			TextView txvSubMessage = (TextView) mView
					.findViewById(R.id.txvMessageHeaderSubtitle);
			if (db().isEmptyTable() && !mSynced) {
				scope.main().requestSync(NoteProvider.AUTHORITY);
				mView.findViewById(R.id.waitingForSyncToStart).setVisibility(
						View.VISIBLE);

				txvSubMessage.setText("Your notes will appear shortly");
			} else {
				TextView txvMsg = (TextView) mView
						.findViewById(R.id.txvNoteAllArchive);
				txvMsg.setVisibility(View.VISIBLE);
				mView.findViewById(R.id.waitingForSyncToStart).setVisibility(
						View.GONE);
				txvSubMessage.setVisibility(View.GONE);
			}
		} else {
			mView.findViewById(R.id.txvNoteAllArchive).setVisibility(View.GONE);
			mView.findViewById(R.id.waitingForSyncToStart).setVisibility(
					View.GONE);
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		menu.clear();
		inflater.inflate(R.menu.menu_fragment_note, menu);
		mSearchView = (SearchView) menu.findItem(R.id.menu_note_search)
				.getActionView();
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public Object databaseHelper(Context context) {
		return new NoteDB(context);
	}

	@Override
	public List<DrawerItem> drawerMenus(Context context) {
		List<DrawerItem> items = null;
		NoteDB note = new NoteDB(context);
		NoteStages stages = note.new NoteStages(context);
		if (stages.isEmptyTable()) {
			OEDomain domain = new OEDomain();
			domain.add("user_id", "=", OEUser.current(context).getUser_id());
			OEHelper oe = stages.getOEInstance();
			if (oe != null) {
				oe.syncWithServer(domain);
			}
		}
		if (note.isInstalledOnServer()) {
			items = new ArrayList<DrawerItem>();
			items.add(new DrawerItem(TAG, "Notes", true));
			items.add(new DrawerItem(TAG, "Notes", count("-1", context),
					R.drawable.ic_action_notes, fragmentObject(-1)));
			items.add(new DrawerItem(TAG, "Archive", 0,
					R.drawable.ic_action_archive, fragmentObject(-2)));
			if (stages.count() > 0) {
				int index = 0;
				for (OEDataRow stage : stages.select()) {
					if (index > mTagColors.length - 1) {
						index = 0;
					}
					DrawerItem stageItem = new DrawerItem(TAG,
							stage.getString("name"), count(
									stage.getString("id"), context),
							mTagColors[index],
							fragmentObject(stage.getInt("id")));
					mStageTagColors.put("key_" + stage.getString("id"),
							Color.parseColor(mTagColors[index]));
					items.add(stageItem);
					index++;
				}
			}
		}
		return items;
	}

	public int count(String stage_id, Context context) {
		int count = 0;
		NoteDB note = new NoteDB(context);
		String where = null;
		String[] whereArgs = null;

		if (stage_id.equals("-1")) {
			where = "open = ?";
			whereArgs = new String[] { "true" };
		} else {
			where = "open = ? AND stage_id = ? ";
			whereArgs = new String[] { "true", stage_id };
		}
		count = note.count(where, whereArgs);
		return count;
	}

	private Fragment fragmentObject(int value) {
		Note note = new Note();
		Bundle bundle = new Bundle();
		bundle.putInt("stage_id", value);
		note.setArguments(bundle);
		return note;
	}

	@Override
	public boolean canSwipe(int position) {
		return true;
	}

	@Override
	public void onSwipe(View view, int[] positions) {
		for (int position : positions) {
			OEDataRow row = (OEDataRow) mNotesList.get(position);
			NoteToggleStatus mNoteToggle = new NoteToggleStatus(
					row.getInt("id"), row.getBoolean("open"), getActivity());
			mNoteToggle.execute();
			mNotesList.remove(position);
			mNoteListAdapter.notifiyDataChange(mNotesList);
		}
	}

	public class NoteToggleStatus extends AsyncTask<Void, Void, Void> {
		int mId = 0;
		boolean mStatus = false;
		String mToast = "";
		FragmentActivity mActivity = null;

		public NoteToggleStatus(int id, boolean status,
				FragmentActivity activity) {
			mId = id;
			mStatus = status;
			mActivity = activity;
		}

		@Override
		protected Void doInBackground(Void... params) {
			NoteDB note = new NoteDB(mActivity);
			OEHelper oe = note.getOEInstance();
			if (oe != null) {
				try {
					JSONArray args = new JSONArray("[" + mId + "]");
					String method = "onclick_note_is_done";
					mToast = "Moved to archive";

					if (!mStatus) {
						method = "onclick_note_not_done";
						mToast = "Moved to active notes";
						mStatus = true;
					} else {
						mStatus = false;
					}
					oe.openERP().call_kw(note.getModelName(), method, args);
					OEValues values = new OEValues();
					values.put("open", mStatus);
					int count = note.update(values, mId);
					Log.i(TAG, "Note->NoteToggleStatus() : " + count
							+ " row updated");
				} catch (Exception e) {
					mToast = "No Connection !";
				}
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			DrawerListener drawer = (DrawerListener) mActivity;
			drawer.refreshDrawer(TAG);
			Toast.makeText(mActivity, mToast, Toast.LENGTH_LONG).show();
		}

	}

	@Override
	public void onPullStarted(View view) {
		Log.d(TAG, "Note->onPullStarted()");
		scope.main().requestSync(NoteProvider.AUTHORITY);
	}

	@Override
	public void onResume() {
		super.onResume();
		scope.context().registerReceiver(syncFinishReceiver,
				new IntentFilter(SyncFinishReceiver.SYNC_FINISH));
	}

	@Override
	public void onPause() {
		super.onPause();
		scope.context().unregisterReceiver(syncFinishReceiver);
	}

	private SyncFinishReceiver syncFinishReceiver = new SyncFinishReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			mTouchListener.setPullComplete();
			DrawerListener drawer = (DrawerListener) getActivity();
			drawer.refreshDrawer(TAG);
			mNoteLoader = new NoteLoader(mStageId);
			mNoteLoader.execute();
			mSynced = true;
		}
	};

	/**
	 * On QuickNote Create button click listener
	 */
	@Override
	public void onClick(View v) {
		Log.d(TAG, "[QuickNote create] Note->onClick()");
		switch (v.getId()) {
		case R.id.imgBtnAttachImage:
			requestIntent(Attachment.Types.IMAGE_OR_CAPTURE_IMAGE);
			break;
		case R.id.imgBtnAttachAudio:
			requestIntent(Attachment.Types.AUDIO);
			break;
		case R.id.imgBtnAttachFile:
			requestIntent(Attachment.Types.FILE);
			break;
		default:
			requestIntent(null);
		}
		edtTitle.setText(null);
	}

	private void requestIntent(Attachment.Types attachmentType) {
		Intent intent = new Intent(getActivity(), NoteComposeActivity.class);
		if (attachmentType != null) {
			intent.putExtra("request_code", attachmentType);
		} else {
			intent.putExtra("note_title", edtTitle.getText().toString());
			intent.putExtra("stage_id", mStageId);
		}
		startActivityForResult(intent, KEY_NOTE);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case KEY_NOTE:
			if (resultCode == Activity.RESULT_OK) {
				int new_id = data.getExtras().getInt("result");
				boolean is_new = data.getExtras().getBoolean("is_new");
				if (is_new) {
					OEDataRow row = db().select(new_id);
					mNotesList.add(0, row);
					mNoteListAdapter.notifiyDataChange(mNotesList);
				}
				checkStatus();
			}
			break;
		}
	}

	/**
	 * On Note GridView item click listener
	 */
	@Override
	public void onItemClick(AdapterView<?> adapter, View view, int position,
			long id) {
		Log.d(TAG, "Note->onItemClick()");
		OEDataRow row = (OEDataRow) mNotesList.get(position);

		Bundle bundle = new Bundle();
		bundle.putInt("note_id", row.getInt("id"));
		bundle.putBoolean("row_status", row.getBoolean("open"));
		OEDataRow stage = row.getM2ORecord("stage_id").browse();
		if (stage != null) {
			bundle.putString("stage_id", stage.getString("id"));
			Integer tag_color = getTagColor("key_"
					+ bundle.getString("stage_id"));
			if (tag_color != null)
				bundle.putInt("stage_color", tag_color);
		}
		NoteDetail note = new NoteDetail();
		note.setArguments(bundle);
		FragmentListener mFragment = (FragmentListener) getActivity();
		mFragment.startMainFragment(note, true);
	}

	@Override
	public Integer getTagColor(String key) {
		if (mStageTagColors.containsKey(key)) {
			return mStageTagColors.get(key);
		}
		return null;
	}
}
