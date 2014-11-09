package com.danilov.manga.fragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.android.httpimage.HttpImageManager;
import com.danilov.manga.R;
import com.danilov.manga.activity.DownloadsActivity;
import com.danilov.manga.activity.MangaInfoActivity;
import com.danilov.manga.core.interfaces.RefreshableActivity;
import com.danilov.manga.core.model.Manga;
import com.danilov.manga.core.model.MangaChapter;
import com.danilov.manga.core.repository.RepositoryEngine;
import com.danilov.manga.core.repository.RepositoryException;
import com.danilov.manga.core.util.Constants;
import com.danilov.manga.core.util.ServiceContainer;
import com.danilov.manga.core.util.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Semyon on 09.11.2014.
 */
public class ChaptersFragment extends Fragment implements AdapterView.OnItemClickListener {

    private final String TAG = "ChaptersFragment";
    private static final String CHAPTERS_KEY = "CK";
    private static final String SELECTED_CHAPTERS = "SC";

    private MangaInfoActivity activity;
    private RefreshableActivity refreshable;

    private View view;

    private ListView chaptersListView;

    private Button backButton;
    private Button download;

    private Manga manga;

    private ChaptersAdapter adapter = null;

    private boolean isLoading = false;

    private boolean[] selection = null;

    public static ChaptersFragment newInstance(final Manga manga) {
        ChaptersFragment chaptersFragment = new ChaptersFragment();
        chaptersFragment.manga = manga;
        return chaptersFragment;
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.manga_chapters_fragment, container, false);
        return view;
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        activity = (MangaInfoActivity) getActivity();
        refreshable = (RefreshableActivity) getActivity();
        chaptersListView = (ListView) view.findViewById(R.id.chaptersListView);
        backButton = (Button) view.findViewById(R.id.back);
        download = (Button) view.findViewById(R.id.download);
        chaptersListView.setOnItemClickListener(this);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                activity.showInfoFragment();
            }
        });
        download.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                Intent intent = new Intent(activity, DownloadsActivity.class);
                intent.putExtra(Constants.MANGA_PARCEL_KEY, manga);
                intent.putIntegerArrayListExtra(Constants.SELECTED_CHAPTERS_KEY, adapter.getSelectedChaptersList());
                startActivity(intent);
                activity.showInfoFragment();
            }
        });
        if (savedInstanceState == null) {
            Intent i = activity.getIntent();
            manga = i.getParcelableExtra(Constants.MANGA_PARCEL_KEY);
            if (manga != null) {
                loadChaptersInfo(manga);
            }
        } else {
            restoreInstanceState(savedInstanceState);
        }
    }

    private void loadChaptersInfo(final Manga manga) {
        List<MangaChapter> chapters = manga.getChapters();
        if (chapters != null) {
            showChapters();
        } else {
            isLoading = true;
            refreshable.startRefresh();
            MangaChaptersQueryThread thread = new MangaChaptersQueryThread(manga);
            thread.start();
        }
    }

    private void showChapters() {
        List<MangaChapter> chapters = manga.getChapters();
        adapter = new ChaptersAdapter(getActivity(), chapters);
        if (selection != null) {
            adapter.setSelectedChapters(selection);
        }
        chaptersListView.setAdapter(adapter);
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        if (manga != null) {
            outState.putParcelable(Constants.MANGA_PARCEL_KEY, manga);
            ArrayList<MangaChapter> chapterList = Utils.listToArrayList(manga.getChapters());
            if (chapterList != null) {
                outState.putParcelableArrayList(CHAPTERS_KEY, chapterList);
            }
            if (adapter != null) {
                outState.putBooleanArray(SELECTED_CHAPTERS, adapter.getSelectedChapters());
            }
        }
        super.onSaveInstanceState(outState);
    }

    public void restoreInstanceState(final Bundle savedInstanceState) {
        manga = savedInstanceState.getParcelable(Constants.MANGA_PARCEL_KEY);
        ArrayList<MangaChapter> chapters = savedInstanceState.getParcelableArrayList(CHAPTERS_KEY);
        if (chapters != null) {
            manga.setChapters(chapters);
            manga.setChaptersQuantity(chapters.size());
            selection = savedInstanceState.getBooleanArray(SELECTED_CHAPTERS);
        }
        if (manga != null) {
            loadChaptersInfo(manga);
        }
        if (isLoading) {
            refreshable.startRefresh();
        } else {
            refreshable.stopRefresh();
        }
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
        if (adapter != null) {
            adapter.select(position);
        }
    }

    private class MangaChaptersQueryThread extends Thread {

        private boolean loaded = false;
        private Manga manga;
        private String error = null;

        public MangaChaptersQueryThread(final Manga manga) {
            this.manga = manga;
        }

        @Override
        public void run() {
            RepositoryEngine repositoryEngine = manga.getRepository().getEngine();
            try {
                loaded = repositoryEngine.queryForChapters(manga);
            } catch (RepositoryException e) {
                error = e.getMessage();
                Log.d(TAG, e.getMessage());
            }
            activity.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    if (loaded) {
                        showChapters();
                    } else {
                        Context context = getActivity();
                        String message = Utils.errorMessage(context, error, R.string.p_internet_error);
                        Utils.showToast(context, message);
                    }
                    isLoading = false;
                    refreshable.stopRefresh();
                }

            });
        }

    }


    private class ChaptersAdapter extends ArrayAdapter<MangaChapter> {

        private List<MangaChapter> chapters = null;

        private boolean[] selectedChapters = null;

        @Override
        public int getCount() {
            return chapters.size();
        }

        public ChaptersAdapter(final Context context, final List<MangaChapter> objects) {
            super(context, 0, objects);
            this.chapters = objects;
            selectedChapters = new boolean[chapters.size()];
            for (int i = 0; i < chapters.size(); i++) {
                selectedChapters[i] = false;
            }
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {
            View view = convertView;
            Holder h = null;
            if (view == null) {
                view = activity.getLayoutInflater().inflate(R.layout.chapter_list_item, null);
                h = new Holder();
                TextView title = (TextView) view.findViewById(R.id.chapterTitle);
                TextView subtitle = (TextView) view.findViewById(R.id.chapterSubtitle);
                CheckBox checkBox = (CheckBox) view.findViewById(R.id.checkbox);
                h.checkBox = checkBox;
                h.title = title;
                h.subtitle = subtitle;
                view.setTag(h);
            } else {
                h = (Holder) view.getTag();
            }
            MangaChapter chapter = chapters.get(position);
            h.title.setText((chapter.getNumber() + 1) + ". " + chapter.getTitle());

            h.checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
                    select(position, isChecked);
                }
            });

            h.checkBox.setChecked(selectedChapters[position]);

            return view;
        }

        public boolean[] getSelectedChapters() {
            return selectedChapters;
        }

        public void setSelectedChapters(final boolean[] selectedChapters) {
            this.selectedChapters = selectedChapters;
        }

        public ArrayList<Integer> getSelectedChaptersList() {
            ArrayList<Integer> selection = new ArrayList<Integer>();
            for (int i = 0; i < selectedChapters.length; i++) {
                if (selectedChapters[i]) {
                    selection.add(i);
                }
            }
            return selection;
        }

        public void select(final int position) {
            selectedChapters[position] = !selectedChapters[position];
            notifyDataSetChanged();
        }

        public void select(final int position, final boolean isChecked) {
            selectedChapters[position] = isChecked;
        }

        private class Holder {

            public CheckBox checkBox;

            public TextView title;

            public TextView subtitle;

        }

    }

}
