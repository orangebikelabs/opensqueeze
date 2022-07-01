/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.menu;

import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.artwork.ArtworkType;
import com.orangebikelabs.orangesqueeze.artwork.ThumbnailProcessor;
import com.orangebikelabs.orangesqueeze.browse.common.IconRetriever;
import com.orangebikelabs.orangesqueeze.browse.common.Item;
import com.orangebikelabs.orangesqueeze.browse.node.NodeItem;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.SBPreferences;

import javax.annotation.Nullable;

/**
 * @author tsandee
 */

class MenuElementIconRetriever extends IconRetriever {

    final static private ImmutableMap<String, Integer> sIdDrawableMap;

    static {
        // @formatter:off
        sIdDrawableMap = new ImmutableMap.Builder<String, Integer>()
                .put("favorites", R.drawable.ic_favorite)
                .put("opmlappgallery", R.drawable.ic_apps)
                .put("opmlmyapps", R.drawable.ic_apps)
                .put("radios", R.drawable.ic_internet_radio)
                .put("myMusic", R.drawable.ic_musiclibrary)
                .put("randomplay", R.drawable.ic_shuffle)
                .put("randomplaychoosegenres", R.drawable.ic_genre)
                .put("randomchoosegenres", R.drawable.ic_genre)
                .put("randomyears", R.drawable.ic_year)
                .put("randomartists", R.drawable.ic_artist)
                .put("randomalbums", R.drawable.ic_album)
                .put("randomtracks", R.drawable.ic_shuffle)
                .put("opmlsearch", R.drawable.ic_search)
                .put("albums", R.drawable.ic_album)
                .put("artists", R.drawable.ic_artist)
                .put("myMusicNewMusic", R.drawable.ic_new_releases)
                .put("myMusicGenres", R.drawable.ic_genre)
                .put("myMusicAlbums", R.drawable.ic_album)
                .put("myMusicArtists", R.drawable.ic_artist)
                .put("myMusicMusicFolder", R.drawable.ic_folder)
                .put("myMusicPlaylists", R.drawable.ic_playlist)
                .put("myMusicYears", R.drawable.ic_year)
                .put("myMusicSearch", R.drawable.ic_search)
                .put("myMusicSearchRecent", R.drawable.ic_search)
                .put("homeSearchRecent", R.drawable.ic_search)
                .put("globalSearch", R.drawable.ic_search)
                .put(NodeItem.EXTRAS_NODE, R.drawable.ic_more)
                .build();
    }

    final private Function<Item, MenuElement> mElementFunction;

    MenuElementIconRetriever(Function<Item, MenuElement> elementFn) {
        mElementFunction = elementFn;
    }

    @Override
    public boolean applies(Item item) {
        return mElementFunction.apply(item) != null;
    }

    @Override
    public boolean load(ThumbnailProcessor processor, Item item, AbsListView parentView, @Nullable ImageView imageView) {
        boolean retval = false;

        MenuElement elem = mElementFunction.apply(item);
        if (elem == null) {
            return false;
        }

        Integer iconRid = null;
        String serverResource = null;

        // get drawable id
        String id = elem.getId();
        if (id != null) {
            iconRid = sIdDrawableMap.get(id);
        }
        if (iconRid == null) {
            serverResource = getServerResource(elem);
            if (serverResource != null) {
                // see if this is a URL that we are remapping
                iconRid = ImageRemapper.getInstance().get(serverResource);
            }
        }
        if (iconRid != null) {
            Drawable resourceDrawable = ContextCompat.getDrawable(processor.getContext(), iconRid);
            OSAssert.assertNotNull(resourceDrawable, "resource rid returned a null drawable: " + iconRid);
            setArtwork(processor, imageView, resourceDrawable, ScaleType.CENTER_CROP);
            retval = true;
        } else if (serverResource != null) {
            if (serverResource.contains("/")) {
                addArtworkJob(processor, imageView, serverResource, ArtworkType.SERVER_RESOURCE_THUMBNAIL, ScaleType.CENTER_INSIDE);
            } else {
                addArtworkJob(processor, imageView, serverResource, ArtworkType.ALBUM_THUMBNAIL, ScaleType.CENTER);
            }
            retval = true;
        } else if (elem.isArtist()) {
            // make sure to disable artist artwork if the pref suggests it
            if (!isArtistArtworkDisabled()) {
                String artistId = elem.getArtistId();
                if (artistId != null) {
                    addArtworkJob(processor, imageView, artistId, ArtworkType.ARTIST_THUMBNAIL, ScaleType.CENTER);
                    retval = true;
                }
            }
        } else if (elem.isAlbum()) {
            if (imageView != null) {
                String albumId = elem.getAlbumId();
                if (albumId != null) {
                    addArtworkJob(processor, imageView, albumId, ArtworkType.LEGACY_ALBUM_THUMBNAIL, ScaleType.CENTER);
                } else {
                    processor.setNoArtwork(imageView);
                }
                retval = true;
            }
        } else if (elem.isVariousArtist()) {
            if (!isArtistArtworkDisabled()) {
                // this is a special-case for some custombrowse lists
                Drawable d = ContextCompat.getDrawable(processor.getContext(), R.drawable.ic_artist);
                OSAssert.assertNotNull(d, "artist list drawable shouldn't be null");

                setArtwork(processor, imageView, d, ScaleType.CENTER_CROP);
                retval = true;
            }
        }
        return retval;
    }

    private boolean isArtistArtworkDisabled() {
        return SBPreferences.get().isArtistArtworkDisabled();
    }

    private String getServerResource(MenuElement elem) {
        String retval = elem.getIconId();
        if (retval == null) {
            retval = elem.getMenuIcon();
        }
        if (retval == null) {
            retval = elem.getIcon();
        }
        return retval;
    }

}
