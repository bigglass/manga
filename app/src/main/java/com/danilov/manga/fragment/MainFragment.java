package com.danilov.manga.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.danilov.manga.R;
import com.danilov.manga.core.database.DatabaseAccessException;
import com.danilov.manga.core.database.DownloadedMangaDAO;
import com.danilov.manga.core.database.UpdatesDAO;
import com.danilov.manga.core.model.LocalManga;
import com.danilov.manga.core.model.Manga;
import com.danilov.manga.core.model.UpdatesElement;
import com.danilov.manga.core.service.MangaUpdateService;
import com.danilov.manga.core.util.Constants;
import com.danilov.manga.core.util.ServiceContainer;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by Semyon Danilov on 07.10.2014.
 */
public class MainFragment extends BaseFragment {

    private Button update;
    private ListView updatesView;
    private List<UpdatesElement> updates = new ArrayList<UpdatesElement>();

    private UpdatesAdapter adapter;

    private FragmentActivity activity;

    private UpdateBroadcastReceiver receiver;

    private DownloadedMangaDAO downloadedMangaDAO = ServiceContainer.getService(DownloadedMangaDAO.class);
    private UpdatesDAO updatesDAO = ServiceContainer.getService(UpdatesDAO.class);

    public static MainFragment newInstance() {
        return new MainFragment();
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.manga_main_fragment, container, false);
        return view;
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        updatesView = findViewById(R.id.updates);
        update = findViewById(R.id.update);
        try {
            updates = updatesDAO.getAllUpdates();
        } catch (Exception e) {
            e.printStackTrace();
        }
        adapter = new UpdatesAdapter(getActivity(), 0, updates);
        updatesView.setAdapter(adapter);
        activity = getActivity();
        receiver = new UpdateBroadcastReceiver();
        activity.registerReceiver(receiver, new IntentFilter(MangaUpdateService.UPDATE));
        update.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                try {
                    updates.clear();
                    adapter.notifyDataSetChanged();
                    List<LocalManga> mangaList = downloadedMangaDAO.getAllManga();
                    for (Manga manga : mangaList) {
                        updatesDAO.deleteManga((LocalManga) manga);
                    }
                    MangaUpdateService.startUpdateList(getActivity(), mangaList);
                } catch (DatabaseAccessException e) {
                    e.printStackTrace();
                }
            }
        });
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onDetach() {
        activity.unregisterReceiver(receiver);
        super.onDetach();
    }

    private class UpdateBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(final Context context, final Intent intent) {
            Manga manga = intent.getParcelableExtra(Constants.MANGA_PARCEL_KEY);
            int difference = intent.getIntExtra(Constants.MANGA_CHAPTERS_DIFFERENCE, 0);
            onUpdate(manga, difference);
        }

    }

    private void onUpdate(final Manga manga, final Integer difference) {
        try {
            updatesDAO.updateLocalInfo((LocalManga) manga, difference, new Date());
            downloadedMangaDAO.updateInfo(manga, manga.getChaptersQuantity());
            UpdatesElement element = updatesDAO.getUpdatesByManga((LocalManga) manga);
            updates.add(element);
            adapter.notifyDataSetChanged();
        } catch (DatabaseAccessException e) {
            e.printStackTrace();
        }
    }

    private class UpdatesAdapter extends ArrayAdapter<UpdatesElement> {

        private List<UpdatesElement> updates;

        @Override
        public int getCount() {
            return updates.size();
        }

        public UpdatesAdapter(final Context context, final int resource, final List<UpdatesElement> objects) {
            super(context, resource, objects);
            updates = objects;
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            TextView view = (TextView) inflater.inflate(android.R.layout.simple_list_item_1, null);
            UpdatesElement element = updates.get(position);
            view.setText(element.getManga().getTitle() + " +" + element.getDifference());
            return view;
        }
    }

}
