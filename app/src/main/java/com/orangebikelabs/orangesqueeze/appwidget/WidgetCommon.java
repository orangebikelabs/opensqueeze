/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.appwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;

import androidx.annotation.LayoutRes;
import androidx.annotation.StringRes;
import androidx.core.text.HtmlCompat;

import android.text.Html;
import android.util.DisplayMetrics;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.RemoteViews;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.app.ServerConnectionService;
import com.orangebikelabs.orangesqueeze.artwork.Artwork;
import com.orangebikelabs.orangesqueeze.common.NavigationManager;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.PlayerCommands;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus;
import com.orangebikelabs.orangesqueeze.common.SBContext;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.common.SBPreferences;
import com.orangebikelabs.orangesqueeze.compat.Compat;
import com.orangebikelabs.orangesqueeze.nowplaying.NowPlayingActivity;
import com.orangebikelabs.orangesqueeze.nowplaying.PlayerControlStates;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tsandee
 */
public class WidgetCommon {

    static public void setDisconnectedWidgets(Context context) {
        setDisconnectedWidgets(context, R.string.disconnected);
    }

    static public void setDisconnectedWidgets(Context context, @StringRes int textRid, Object... args) {
        doUpdateWidget(context, WidgetCommon.getDisconnectedRemoteViews(context, SmallerWidget.LAYOUT_RID, textRid, args));
        doUpdateWidget(context, WidgetCommon.getDisconnectedRemoteViews(context, LargerWidget.LAYOUT_RID, textRid, args));
    }

    static public void updateWidgets(SBContext context) {
        OSAssert.assertNotMainThread();

        if (!context.isConnected()) {
            setDisconnectedWidgets(context.getApplicationContext());
        } else {
            updateWidgetsWithPlayerStatus(context.getApplicationContext(), context.getPlayerStatus());
        }
    }

    static public void updateWidgetsWithPlayerStatus(Context context, @Nullable PlayerStatus status) {
        OSAssert.assertNotMainThread();
        if (status != null) {
            RemoteViews views = WidgetCommon.getConnectedRemoteViews(context, status, SmallerWidget.LAYOUT_RID);
            doUpdateWidget(context, views);

            views = WidgetCommon.getConnectedRemoteViews(context, status, LargerWidget.LAYOUT_RID);
            doUpdateWidget(context, views);
        } else {
            setDisconnectedWidgets(context, R.string.widget_noplayers_text);
        }
    }

    // Push update for this widget to the home screen
    static protected void doUpdateWidget(Context context, RemoteViews views) {
        OSAssert.assertNotMainThread();

        try {
            ComponentName statusWidget;
            if (views.getLayoutId() == SmallerWidget.LAYOUT_RID) {
                statusWidget = new ComponentName(context, SmallerWidget.class);
            } else {
                statusWidget = new ComponentName(context, LargerWidget.class);
            }

            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            if (manager != null) {
                manager.updateAppWidget(statusWidget, views);
            }
        } catch (Exception e) {
            // this can fire a "bitmap too large" exception, log it if it happens but don't crash
            OSLog.w(e.getMessage(), e);

            //Crashlytics.logException(e);
        }
    }

    static public boolean isWidgetEnabled(Context context) {
        SBPreferences prefs = SBPreferences.get();
        return prefs.isWidgetEnabled(LargerWidget.class.getSimpleName()) || prefs.isWidgetEnabled(SmallerWidget.class.getSimpleName());
    }

    @Nonnull
    static public RemoteViews getDisconnectedRemoteViews(Context context, @LayoutRes int layout, @StringRes int rid, Object... args) {
        RemoteViews views = new RemoteViews(context.getPackageName(), layout);

        views.setTextViewText(R.id.nowplaying_text, HtmlCompat.fromHtml(context.getString(rid, args), 0));
        views.setViewVisibility(R.id.player_name_label, View.GONE);
        views.setViewVisibility(R.id.next_button, View.GONE);
        views.setViewVisibility(R.id.play_button, View.GONE);
        views.setViewVisibility(R.id.pause_button, View.GONE);
        views.setViewVisibility(R.id.search_button, View.GONE);
        views.setViewVisibility(R.id.previous_button, View.GONE);
        views.setViewVisibility(R.id.thumbsdown_button, View.GONE);
        views.setViewVisibility(R.id.thumbsup_button, View.GONE);
        views.setViewVisibility(R.id.volume_button, View.GONE);
        views.setImageViewResource(R.id.artwork, R.drawable.artwork_missing);

        views.setOnClickPendingIntent(R.id.nowplaying_text, buildNowPlayingPendingIntent(context));
        views.setOnClickPendingIntent(R.id.artwork, buildNowPlayingPendingIntent(context));
        views.setOnClickPendingIntent(R.id.widget_container, buildNowPlayingPendingIntent(context));

        return views;
    }

    @Nonnull
    static public RemoteViews getConnectedRemoteViews(Context context, PlayerStatus status, @LayoutRes int layout) {
        RemoteViews views = new RemoteViews(context.getPackageName(), layout);

        boolean livingLarge = layout == LargerWidget.LAYOUT_RID;
        SBContext sbContext = SBContextProvider.get();
        int connectedPlayerCount = sbContext.getServerStatus().getAvailablePlayerIds().size();

        views.setViewVisibility(R.id.player_name_label,
                livingLarge && connectedPlayerCount > 1 ? View.VISIBLE : View.GONE);
        views.setTextViewText(R.id.player_name_label,
                context.getString(R.string.widget_playername_text, status.getName()));

        views.setTextViewText(R.id.text1, status.getTrack());
        views.setTextViewText(R.id.text2, status.getDisplayArtist());

        String nowPlayingText =
                "<b><font color=\"white\">" + Html.escapeHtml(status.getTrack()) + "</font></b>";
        if (!status.getDisplayArtist().isEmpty()) {
            nowPlayingText += " - " + Html.escapeHtml(status.getDisplayArtist());
        }
        views.setTextViewText(R.id.nowplaying_text, HtmlCompat.fromHtml(nowPlayingText, 0));

        updateArtwork(sbContext, status, views, livingLarge);

        views.setOnClickPendingIntent(R.id.pause_button,
                buildCommandPendingIntent(context, R.id.pause_button, status.getId(),
                        PlayerCommands.PAUSE));
        views.setOnClickPendingIntent(R.id.next_button,
                buildCommandPendingIntent(context, R.id.next_button, status.getId(),
                        PlayerCommands.NEXT_TRACK));
        views.setOnClickPendingIntent(R.id.previous_button,
                buildCommandPendingIntent(context, R.id.previous_button, status.getId(),
                        PlayerCommands.PREVIOUS_TRACK));
        views.setOnClickPendingIntent(R.id.play_button,
                buildCommandPendingIntent(context, R.id.play_button, status.getId(), PlayerCommands.PLAY));
        views.setOnClickPendingIntent(R.id.thumbsup_button,
                buildThumbsUpButtonIntent(context, status.getId()));
        views.setOnClickPendingIntent(R.id.thumbsdown_button,
                buildThumbsDownButtonIntent(context, status.getId()));
        views.setOnClickPendingIntent(R.id.artwork, buildNowPlayingPendingIntent(context));
        views.setOnClickPendingIntent(R.id.nowplaying_text, buildNowPlayingPendingIntent(context));

        // TODO take this right to player selection
        views.setOnClickPendingIntent(R.id.player_name_label, buildNowPlayingPendingIntent(context));
        views.setOnClickPendingIntent(R.id.search_button, buildSearchPendingIntent(context));
        views.setOnClickPendingIntent(R.id.widget_container, buildNowPlayingPendingIntent(context));

        // set baseline for controls
        SparseIntArray visibilities = new SparseIntArray();

        PlayerControlStates.putViewVisibilities(status, visibilities);
        for (int i = 0; i < visibilities.size(); i++) {
            int id = visibilities.keyAt(i);
            int visibility = visibilities.valueAt(i);

            // set view visibility
            views.setViewVisibility(id, visibility);

            // optionally include the image level
            int maxLevel = PlayerControlStates.getMaxViewImageLevel(id);
            if (maxLevel >= 0) {
                int imageLevel = PlayerControlStates.getCurrentViewImageLevel(status, id);
                imageLevel = maxLevel + imageLevel;
                views.setInt(id, "setImageLevel", imageLevel);
            }
        }

        // disable these for the small widget
        if (!livingLarge) {
            views.setViewVisibility(R.id.previous_button, View.GONE);
        }

        // disable for all widgets
        views.setViewVisibility(R.id.volume_button, View.GONE);

        // enable these for the small widget
        views.setViewVisibility(R.id.search_button, View.VISIBLE);

        return views;
    }

    private static void updateArtwork(SBContext sbContext, PlayerStatus status, RemoteViews views, boolean livingLarge) {
        OSAssert.assertNotMainThread();

        boolean artworkSet = false;

        Context context = sbContext.getApplicationContext();
        Artwork artwork = status.getArtwork();
        if (artwork.isPresent()) {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int screenWidth = Math.min(dm.heightPixels, dm.widthPixels);
            // set maximum size of bitmaps to send through widgets
            int targetWidth = Math.min(1024, screenWidth);

            if (!livingLarge) {
                targetWidth /= 8;
            }

            // always use thumbnails retriever, which guarantees a specific image size
            // normal artwork.get() can return an image much too large
            ListenableFuture<Bitmap> bmpFuture = artwork.getThumbnail(targetWidth);

            // we're not the main thread, block for just a bit to get bitmap
            try {
                Bitmap bmp = bmpFuture.get(100, TimeUnit.MILLISECONDS);
                if (bmp != null) {
                    views.setImageViewBitmap(R.id.artwork, bmp);
                    artworkSet = true;
                }
            } catch (InterruptedException e) {
                // ignore
            } catch (ExecutionException e) {
                OSLog.w(e.getMessage(), e);
            } catch (TimeoutException e) {
                // artwork is loading
                views.setImageViewResource(R.id.artwork, R.drawable.artwork_loading);
                artworkSet = true;
                Futures.addCallback(bmpFuture, new FutureCallback<Bitmap>() {
                    @Override
                    public void onSuccess(@Nullable Bitmap bitmap) {
                        updateWidgets(sbContext);
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        updateWidgets(sbContext);
                    }
                }, OSExecutors.getUnboundedPool());
            }
        }
        if (!artworkSet) {
            // no artwork, specify missing
            views.setImageViewResource(R.id.artwork, R.drawable.artwork_missing);
        }
    }

    @Nonnull
    static protected PendingIntent buildNowPlayingPendingIntent(Context context) {
        Intent intent = NavigationManager.Companion.newBlankNowPlayingIntent(context);
        intent.putExtra(NowPlayingActivity.EXTRA_RECREATE_MAIN_ACTIVITY_ON_UP, true);
        return PendingIntent.getActivity(context, 0, intent, Compat.getDefaultPendingIntentFlags() | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Nonnull
    static protected PendingIntent buildSearchPendingIntent(Context context) {
        Intent intent = NavigationManager.Companion.newSearchIntent(context);
        return PendingIntent.getActivity(context, 0, intent, Compat.getDefaultPendingIntentFlags() | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Nonnull
    static protected PendingIntent buildThumbsUpButtonIntent(Context context, PlayerId playerId) {
        Intent intent = ServerConnectionService.Companion.getIntent(context, ServerConnectionService.ServiceActions.THUMBSUP);

        return PendingIntent.getService(context, 0, intent, Compat.getDefaultPendingIntentFlags() | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Nonnull
    static protected PendingIntent buildThumbsDownButtonIntent(Context context, PlayerId playerId) {
        Intent intent = ServerConnectionService.Companion.getIntent(context, ServerConnectionService.ServiceActions.THUMBSDOWN);

        return PendingIntent.getService(context, 0, intent, Compat.getDefaultPendingIntentFlags() | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Nonnull
    static protected PendingIntent buildCommandPendingIntent(Context context, int id, PlayerId playerId, List<String> commands) {
        Intent intent = ServerConnectionService.Companion.getIntent(context, ServerConnectionService.ServiceActions.SEND_COMMANDS);
        intent.putExtra(ServerConnectionService.EXTRA_COMMANDS, commands.toArray(new String[0]));
        intent.putExtra(ServerConnectionService.EXTRA_PLAYER, playerId.toString());

        return PendingIntent.getService(context, id, intent, Compat.getDefaultPendingIntentFlags() | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
