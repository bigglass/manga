package com.danilov.supermanga.core.repository;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.danilov.supermanga.core.application.MangaApplication;
import com.danilov.supermanga.core.http.LinesSearchInputStream;
import com.danilov.supermanga.core.http.RequestPreprocessor;
import com.danilov.supermanga.core.model.Manga;
import com.danilov.supermanga.core.model.MangaChapter;
import com.danilov.supermanga.core.model.MangaSuggestion;
import com.danilov.supermanga.core.repository.special.AuthorizableEngine;
import com.danilov.supermanga.core.util.Constants;
import com.danilov.supermanga.core.util.IoUtils;
import com.danilov.supermanga.core.util.Utils;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * This file is part of MangAGradle.
 * <p>
 * MangAGradle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * MangAGradle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with MangAGradle.  If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * Created by Semyon on 18.06.2016.
 */
public class HentaichanEngine extends AuthorizableEngine {

    private final String baseUri = "http://hentaichan.me/";
    MangaApplication context;
    @NonNull
    private String login;
    @NonNull
    private String password;
    private boolean isAuthorized = false;

    public HentaichanEngine() {
        MangaApplication mangaApplication = MangaApplication.get();
        mangaApplication.applicationComponent().inject(this);

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mangaApplication);
        login = preferences.getString(Constants.HentaichanConstants.LOGIN, "");
        password = preferences.getString(Constants.HentaichanConstants.PASSWORD, "");
        isAuthorized = preferences.getBoolean(Constants.HentaichanConstants.AUTHORIZED, false);
    }

    @Override
    public void setPassword(@NonNull final String password) {
        this.password = password;
        save();
    }

    @Override
    public void setLogin(@NonNull final String login) {
        this.login = login;
        save();
    }

    @Override
    public boolean login() {
        final boolean loginResult = super.login();
        isAuthorized = loginResult;
        save();
        return loginResult;
    }

    @Override
    public void logout() {
        this.isAuthorized = false;
        this.login = "";
        this.password = "";
        save();
    }

    private synchronized void save() {
        MangaApplication mangaApplication = MangaApplication.get();
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mangaApplication);
        preferences.edit()
                .putString(Constants.HentaichanConstants.LOGIN, login)
                .putString(Constants.HentaichanConstants.PASSWORD, password)
                .putBoolean(Constants.HentaichanConstants.AUTHORIZED, isAuthorized)
                .apply();
    }

    @Override
    public boolean checkAuthResponse(final String response) {
        final Document document = Utils.toDocument(response);
        //there is input#login field for unauthorized user
        final Element login = document.getElementById("login");
        if (login != null) {
            //field exists, auth failed
            return false;
        }
        //auth success
        return true;
    }

    @NonNull
    @Override
    public String getLogin() {
        return login;
    }

    @Override
    public boolean isAuthorized() {
        return isAuthorized;
    }

    @Override
    public String authPageUrl() {
        return "http://hentaichan.me/index.php";
    }

    @Override
    public String authSuccessUrl() {
        return null;
    }

    @Override
    public String authSuccessMethod() {
        return null;
    }

    @Override
    @NonNull
    public Map<String, String> loginParams() {
        final HashMap<String, String> params = new HashMap<>();
        params.put("login", "submit");
        params.put("login_name", login);
        params.put("login_password", password);
        params.put("image", "Вход");
        return params;
    }

    @Override
    public String getLanguage() {
        return "Русский";
    }

    @Override
    public List<MangaSuggestion> getSuggestions(final String query) throws RepositoryException {
        return Collections.emptyList();
    }

    private String doWithLoginAttempt(final Callable<Response> callable) throws RepositoryException {
        try {
            String response = null;
            int count = 0;
            do {
                if (!isAuthorized || count > 0) {
                    final boolean loginSuccess = login();
                    if (!loginSuccess) {
                        throw new RepositoryException("Failed to login");
                    }
                }
                Response _response = callable.call();
                count++;
                response = _response.body().string();
            } while (!checkAuthResponse(response));
            return response;
        } catch (Exception e) {
            throw new RepositoryException("Failed to load: " + e.getMessage());
        }
    }

    @Override
    public List<Manga> queryRepository(final String query, final List<Filter.FilterValue> filterValues) throws RepositoryException {
        final String response = doWithLoginAttempt(() -> {
            final Call call = httpClient.newCall(new Request.Builder()
                    .url(baseUri + "?do=search&subaction=search&story=" + URLEncoder.encode(query, "UTF-8"))
                    .get().build());
            final Response rsp;
            try {
                rsp = call.execute();
            } catch (IOException e) {
                throw new RepositoryException("Failed to load: " + e.getMessage());
            }
            return rsp;
        });
        final Document document;
        document = Utils.toDocument(response);
        final List<Manga> mangas = parseSearchResponse(document);
        return mangas;
    }

    @Override
    public List<Manga> queryRepository(final Genre genre) throws RepositoryException {
        return null;
    }

    @Override
    public boolean queryForMangaDescription(final Manga manga) throws RepositoryException {
        final String response = doWithLoginAttempt(() -> {
            final Call call = httpClient.newCall(new Request.Builder()
                    .url(manga.getUri())
                    .get().build());
            final Response rsp;
            try {
                rsp = call.execute();
            } catch (IOException e) {
                throw new RepositoryException("Failed to load: " + e.getMessage());
            }
            return rsp;
        });

        final Document document;
        document = Utils.toDocument(response);

        final boolean description = parseMangaDescriptionResponse(manga, document);
        final boolean chapters = queryForChapters(manga);

        return description && chapters;
    }

    @Override
    public boolean queryForChapters(final Manga manga) throws RepositoryException {
        final String mangaRelatedUri = manga.getUri().replace("/manga/", "/related/");
        final String response = loadPage(mangaRelatedUri);

        final Document document = Utils.toDocument(response);

        final Elements pagination = document.select("#pagination_related");
        final Element quantityEl = pagination.select("b").first();
        if (quantityEl == null) {
            return false;
        }
        final String quantityS = quantityEl.text().split(" ")[0];
        final Integer quantity;
        try {
            quantity = Integer.valueOf(quantityS);
        } catch (NumberFormatException e) {
            return false;
        }
        manga.setChaptersQuantity(quantity);
        final List<MangaChapter> chapters = new ArrayList<>();
        final List<MangaChapter> firstPage = parseMangaChaptersResponse(document);
        if (firstPage == null) {
            return false;
        }
        chapters.addAll(firstPage);


        final List<MangaChapter> pages = Stream.of(pagination.select("a"))
                .filter(value -> value.text().matches("^[0-9]+$"))
                .map(value -> value.attr("href"))
                .map(url -> {
                    try {
                        return loadPage(mangaRelatedUri + url);
                    } catch (RepositoryException e) {

                    }
                    return null;
                })
                .filter(value -> value != null)
                .map(value -> parseMangaChaptersResponse(Utils.toDocument(value)))
                .flatMap(Stream::of)
                .collect(() -> chapters, List::add);

        manga.setChapters(pages);
        manga.setChaptersQuantity(pages.size());
        return true;
    }

    private String loadPage(final String uri) throws RepositoryException {
        return doWithLoginAttempt(() -> {
            final Call call = httpClient.newCall(new Request.Builder()
                    .url(uri)
                    .get().build());
            final Response rsp;
            try {
                rsp = call.execute();
            } catch (IOException e) {
                throw new RepositoryException("Failed to load: " + e.getMessage());
            }
            return rsp;
        });
    }

    @Override
    public List<String> getChapterImages(final MangaChapter chapter) throws RepositoryException {
        String uri = baseUri + chapter.getUri();
        final String response = doWithLoginAttempt(() -> {
            final Call call = httpClient.newCall(new Request.Builder()
                    .url(uri)
                    .get().build());
            final Response rsp;
            try {
                rsp = call.execute();
            } catch (IOException e) {
                throw new RepositoryException("Failed to load: " + e.getMessage());
            }
            return rsp;
        });

        final InputStream stream;
        try {
            stream = new ByteArrayInputStream(response.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RepositoryException("Failed to load: " + e.getMessage());
        }

        try {
            byte[] bytes = new byte[1024];
            final LinesSearchInputStream inputStream = new LinesSearchInputStream(stream, "\"fullimg\":[", "]");
            int status = LinesSearchInputStream.SEARCHING;
            while (status == LinesSearchInputStream.SEARCHING) {
                status = inputStream.read(bytes);
            }
            bytes = inputStream.getResult();
            String str = IoUtils.convertBytesToString(bytes);
            return extractUrls(str);
        } catch (Exception e) {

        }
        return null;
    }

    private List<Manga> parseSearchResponse(final Document document) {
        final ArrayList<Manga> mangas = new ArrayList<>();
        final Elements contentRows = document.getElementsByClass("content_row");
        for (Element element : contentRows) {
            final String mangaTitle = element.attr("title");
            String uri = "";
            String coverUri = "";
            final Elements linkElement = element.select(".manga_row1 a");
            if (!linkElement.isEmpty()) {
                uri = linkElement.attr("href");
            }
            final Elements images = element.select(".manga_images img");
            if (!images.isEmpty()) {
                coverUri = images.get(0).attr("src");
            }
            Manga manga = new Manga(mangaTitle, uri, DefaultRepository.HENTAICHAN);
            manga.setCoverUri(coverUri);
            mangas.add(manga);
        }
        return mangas;
    }

    private boolean parseMangaDescriptionResponse(final Manga manga, final Document document) {
        final Element description = document.getElementById("description");
        if (description != null) {
            manga.setDescription(description.text());
        }
        final String tags = Stream.of(document.select(".sidetag a"))
                .map(Element::text)
                .filter(value -> !value.matches("[\\+-]"))
                .collect(Collectors.joining(", "));
        manga.setGenres(tags);

        final Element author = document.select("#info_wrap .row:nth-child(2) div.item2").first();
        if (author != null) {
            manga.setAuthor(author.text());
        }

        return true;
    }

    private List<MangaChapter> parseMangaChaptersResponse(final Document document) {
        final List<MangaChapter> chapters = new ArrayList<>();
        final Elements contentRows = document.select("#right .related");
        int i = 0;
        for (Element element : contentRows) {
            String title = "";
            String uri = "";
            final Elements linkElement = element.select(".related_info a");
            if (!linkElement.isEmpty()) {
                final Element link = linkElement.first();
                uri = link.attr("href").replace("/manga/", "/online/");
                title = link.text();
            }
            MangaChapter chapter = new MangaChapter(title, i, uri);
            chapters.add(chapter);
            i++;
        }
        return chapters;
    }

    private List<String> extractUrls(final String str) {
        String newStr = str.replaceAll("\"", "").replaceAll("]", "");
        return Arrays.asList(newStr.split(","));
    }

    @Override
    public String getBaseSearchUri() {
        return null;
    }

    @Override
    public String getBaseUri() {
        return null;
    }

    @NonNull
    @Override
    public List<FilterGroup> getFilters() {
        return Collections.emptyList();
    }

    @NonNull
    @Override
    public List<Genre> getGenres() {
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public RequestPreprocessor getRequestPreprocessor() {
        return null;
    }
}
