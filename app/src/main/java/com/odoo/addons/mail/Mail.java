/**
 * Odoo, Open Source Management Solution
 * Copyright (C) 2012-today Odoo SA (<http:www.odoo.com>)
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
 * along with this program.  If not, see <http:www.gnu.org/licenses/>
 *
 * Created on 12/3/15 2:17 PM
 */
package com.odoo.addons.mail;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import com.odoo.R;
import com.odoo.addons.groups.Groups;
import com.odoo.addons.mail.services.MailSyncService;
import com.odoo.base.addons.mail.MailMessage;
import com.odoo.core.orm.ODataRow;
import com.odoo.core.support.addons.fragment.BaseFragment;
import com.odoo.core.support.addons.fragment.ISyncStatusObserverListener;
import com.odoo.core.support.drawer.ODrawerItem;
import com.odoo.core.support.list.OCursorListAdapter;
import com.odoo.core.utils.BitmapUtils;
import com.odoo.core.utils.OControls;
import com.odoo.core.utils.ODateUtils;
import com.odoo.core.utils.OResource;

import java.util.ArrayList;
import java.util.List;

public class Mail extends BaseFragment implements ISyncStatusObserverListener,
        LoaderManager.LoaderCallbacks<Cursor>, OCursorListAdapter.OnViewBindListener, SwipeRefreshLayout.OnRefreshListener, OCursorListAdapter.OnViewCreateListener {
    public static final String TAG = Mail.class.getSimpleName();
    public static final String KEY_MAIL_TYPE = "mail_type";
    private ListView listView;
    private OCursorListAdapter listAdapter;
    private View mView;
    private Boolean syncRequested = false;

    public enum Type {
        Inbox, ToMe, ToDo, Archives, Outbox, Group
    }

    private Type mType = Type.Inbox;
    private Bundle extra;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.common_listview, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mView = view;
        extra = getArguments();
        mType = Type.valueOf(extra.getString(KEY_MAIL_TYPE));
        setHasSyncStatusObserver(TAG, this, db());
        init();
    }

    private void init() {
        listView = (ListView) mView.findViewById(R.id.listview);
        listAdapter = new OCursorListAdapter(getActivity(), null, R.layout.mail_item_view);
        listAdapter.setOnViewCreateListener(this);
        listAdapter.setOnViewBindListener(this);
        listView.setAdapter(listAdapter);
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public View onViewCreated(Context context, ViewGroup view, Cursor cr, int position) {
        return null;
    }

    @Override
    public void onViewBind(View view, Cursor cursor, ODataRow row) {
        OControls.setText(view, R.id.subject, row.getString("message_title"));
        OControls.setText(view, R.id.short_body, row.getString("short_body"));
        OControls.setText(view, R.id.author_name, row.getString("author_name"));
        String date = ODateUtils.convertToDefault(row.getString("date")
                , ODateUtils.DEFAULT_FORMAT, "MMM dd");
        OControls.setText(view, R.id.date, date);
        MailMessage db = (MailMessage) db();
        String image = db.getAuthorImage(row.getInt("author_id"));
        if (!image.equals("false")) {
            Bitmap bmp = BitmapUtils.getBitmapImage(getActivity(), image);
            OControls.setImage(view, R.id.author_image, bmp);
        } else {
            OControls.setImage(view, R.id.author_image, R.drawable.avatar);
        }
        if (row.getBoolean("to_read")) {
            view.setBackground(null);
        } else {
            view.setBackgroundColor(Color.parseColor("#f5f5f5"));
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle data) {
        Object[] clauses = getClause(mType);
        String where = (String) clauses[0];
        List<String> args = (List<String>) clauses[1];
        return new CursorLoader(getActivity(), ((MailMessage) db()).sortedUri(), null,
                where, args.toArray(new String[args.size()]), "date desc");
    }

    private Object[] getClause(Type mType) {
        String where = " date != ?";
        List<String> args = new ArrayList<>();
        args.add("false");
        switch (mType) {
            case Inbox:
                where += " and to_read = ? and starred = ? and id != ?";
                args.add("true");
                args.add("false");
                args.add("0");
                break;
            case ToMe:
                where += " and to_me = ? and to_read = ? and starred = ? and id != ?";
                args.add("true");
                args.add("true");
                args.add("false");
                args.add("0");
                break;
            case ToDo:
                where += " and to_read = ? and starred = ? and id != ?";
                args.add("true");
                args.add("true");
                args.add("0");
                break;
            case Archives:
                where += " and id != ?";
                args.add("0");
                break;
            case Outbox:
                where += " and id = ?";
                args.add("0");
                break;
            case Group:
                int group_id = extra.getInt(Groups.KEY_GROUP_ID);
                where += " and res_id = ? and model = ?";
                args.add(group_id + "");
                args.add("mail.group");
                break;
        }
        return new Object[]{where, args};
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        listAdapter.changeCursor(data);
        if (data.getCount() > 0) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    OControls.setGone(mView, R.id.loadingProgress);
                    OControls.setVisible(mView, R.id.swipe_container);
                    OControls.setGone(mView, R.id.customer_no_items);
                    setHasSwipeRefreshView(mView, R.id.swipe_container, Mail.this);
                }
            }, 500);
        } else {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    OControls.setGone(mView, R.id.loadingProgress);
                    OControls.setGone(mView, R.id.swipe_container);
                    OControls.setVisible(mView, R.id.customer_no_items);
                    setHasSwipeRefreshView(mView, R.id.customer_no_items, Mail.this);
                    OControls.setImage(mView, R.id.icon, R.drawable.ic_action_inbox);
                    OControls.setText(mView, R.id.title, _s(R.string.label_no_messages_found));
                    OControls.setText(mView, R.id.subTitle, "");
                }
            }, 500);
            if (db().isEmptyTable() && !syncRequested) {
                syncRequested = true;
                onRefresh();
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        listAdapter.changeCursor(null);
    }

    @Override
    public List<ODrawerItem> drawerMenus(Context context) {
        List<ODrawerItem> menu = new ArrayList<>();
        menu.add(new ODrawerItem(TAG).setTitle(OResource.string(context, R.string.label_inbox))
                .setInstance(new Mail())
                .setIcon(R.drawable.ic_action_inbox)
                .setExtra(extra(Type.Inbox))
                .setCounter(count(context, Type.Inbox)));
        menu.add(new ODrawerItem(TAG).setTitle(OResource.string(context, R.string.label_to_me))
                .setInstance(new Mail())
                .setIcon(R.drawable.ic_action_user)
                .setExtra(extra(Type.ToMe))
                .setCounter(count(context, Type.ToMe)));
        menu.add(new ODrawerItem(TAG).setTitle(OResource.string(context, R.string.label_to_do))
                .setInstance(new Mail())
                .setIcon(R.drawable.ic_action_todo)
                .setExtra(extra(Type.ToDo))
                .setCounter(count(context, Type.ToDo)));
        menu.add(new ODrawerItem(TAG).setTitle(OResource.string(context, R.string.label_archives))
                .setInstance(new Mail())
                .setIcon(R.drawable.ic_action_archive)
                .setExtra(extra(Type.Archives)));
        menu.add(new ODrawerItem(TAG).setTitle(OResource.string(context, R.string.label_outbox))
                .setInstance(new Mail())
                .setIcon(R.drawable.ic_action_outbox)
                .setExtra(extra(Type.Outbox))
                .setCounter(count(context, Type.Outbox)));
        return menu;
    }

    private int count(Context context, Type type) {
        MailMessage mail = new MailMessage(context, null);
        Object[] clauses = getClause(type);
        String where = (String) clauses[0];
        List<String> args = (List<String>) clauses[1];
        return mail.count(where, args.toArray(new String[args.size()]));
    }

    private Bundle extra(Type type) {
        Bundle extra = new Bundle();
        extra.putString(KEY_MAIL_TYPE, type.toString());
        return extra;
    }

    @Override
    public Class<MailMessage> database() {
        return MailMessage.class;
    }

    @Override
    public void onRefresh() {
        if (inNetwork()) {
            setSwipeRefreshing(true);
            Bundle extra = new Bundle();
            switch (mType) {
                case Inbox:
                    extra.putBoolean(MailSyncService.KEY_FILTER_TOREAD, true);
                    break;
                case ToDo:
                    extra.putBoolean(MailSyncService.KEY_FILTER_STARRED, true);
                    break;
                case Group:
                    extra.putBoolean(MailSyncService.KEY_FILTER_GROUP, true);
                    extra.putInt(Groups.KEY_GROUP_ID, getArguments().getInt(Groups.KEY_GROUP_ID));
                    break;
            }
            parent().sync().requestSync(MailMessage.AUTHORITY, extra);
        } else {
            Toast.makeText(getActivity(), R.string.toast_network_required, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onStatusChange(Boolean refreshing) {
        if (!refreshing) {
            setSwipeRefreshing(false);
            getLoaderManager().restartLoader(0, null, this);
        }
    }

}