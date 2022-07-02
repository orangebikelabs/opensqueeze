/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.artwork;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;

import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.common.io.Closer;
import com.orangebikelabs.orangesqueeze.artwork.ArtworkCacheData.ImageTarget;
import com.orangebikelabs.orangesqueeze.cache.CacheEntry;
import com.orangebikelabs.orangesqueeze.cache.CacheEntry.Type;
import com.orangebikelabs.orangesqueeze.cache.CacheService;
import com.orangebikelabs.orangesqueeze.cache.CachedItemInvalidException;
import com.orangebikelabs.orangesqueeze.cache.CachedItemNotFoundException;
import com.orangebikelabs.orangesqueeze.cache.ManagedTemporary;
import com.orangebikelabs.orangesqueeze.cache.SBCacheException;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.ConnectionInfo;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.OSLog.Tag;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.common.ThreadLocalStringBuilder;
import com.orangebikelabs.orangesqueeze.net.HttpUtils;
import com.orangebikelabs.orangesqueeze.net.SBCredentials;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tsandee
 */
public class StandardArtworkCacheRequest implements ArtworkCacheRequestCallback {

    final static private ThreadLocalStringBuilder sThreadLocalStringBuilder = ThreadLocalStringBuilder.newInstance();

    @Nonnull
    final protected Context mContext;

    @Nonnull
    final protected String mId;

    @Nonnull
    final protected ArtworkType mType;

    @Nonnull
    final protected CacheEntry mEntry;

    final protected int mWidthPixels;

    final protected CompressFormat mCompressFormat;

    protected boolean mImageNeedsRescaling;

    public StandardArtworkCacheRequest(Context context, String id, ArtworkType type, int widthPixels) {
        Preconditions.checkArgument(!id.equals(""), "id cannot be blank");
        Preconditions.checkArgument(widthPixels > 0, "pixel dimensions should be non-zero");

        OSAssert.assertApplicationContext(context);

        mContext = context;
        mId = id;
        mType = type;
        mWidthPixels = widthPixels;
        mCompressFormat = BitmapTools.getStoredCompressFormat(mType, mWidthPixels);

        StringBuilder cacheKey = sThreadLocalStringBuilder.get();
        OSAssert.assertEquals(cacheKey.length(), 0, "length should be zero leaving the get()");
        cacheKey.append("Artwork{id=");
        cacheKey.append(id);
        cacheKey.append(",");
        cacheKey.append("w=");
        cacheKey.append(mWidthPixels);
        cacheKey.append(",t=");

        cacheKey.append(mCompressFormat);
        cacheKey.append(",q=");

        int quality = BitmapTools.getStoredCompressQuality(mCompressFormat, mWidthPixels);

        cacheKey.append(quality);
        cacheKey.append("}");

        CacheEntry.Type cacheType;

        switch (type) {
            case ALBUM_FULL:
            case ALBUM_THUMBNAIL:
            case ARTIST_THUMBNAIL:
            case LEGACY_ALBUM_THUMBNAIL:
                cacheType = Type.SERVERSCAN;
                break;
            default:
                cacheType = Type.TIMEOUT;
                break;
        }
        // don't use memory cache for thumbnails
        mEntry = new CacheEntry(cacheType, SBContextProvider.get().getServerId(), cacheKey.toString());
    }

    @Override
    @Nonnull
    public CacheEntry getEntry() {
        return mEntry;
    }

    @Override
    @Nonnull
    public ArtworkCacheData onDeserializeCacheData(CacheService service, ByteSource byteSource, long expectedLength) throws IOException {
        return FromCacheArtworkData.newInstance(mContext, mEntry.getKey(), mType, byteSource);
    }

    @Nonnull
    public URL getUrl() throws IOException {
        URL retval;
        switch (mType) {
            case SERVER_RESOURCE_FULL:
            case SERVER_RESOURCE_THUMBNAIL:
                retval = getServerResourceUrl(mId);
                break;
            default:
                retval = getCoverArtUrl(mId);
                break;
        }
        return retval;
    }

    @Override
    @Nonnull
    public ArtworkCacheData onLoadData(CacheService service) throws SBCacheException, IOException, InterruptedException {
        Preconditions.checkArgument(mType != ArtworkType.ARTIST_THUMBNAIL, "artwork type must be ARTIST_THUMBNAIL");
        // do data load on the current thread because artwork requests are throttled elsewhere
        try {
            // default is that image needs rescaling
            mImageNeedsRescaling = true;
            URL url = getUrl();
            return loadUrl(service, url);
        } catch (MalformedURLException e) {
            String message = "Remote artwork URI invalid, marking as such in cache";
            if (OSLog.isLoggable(Tag.ARTWORK, OSLog.VERBOSE)) {
                OSLog.v(Tag.ARTWORK, message);
            }
            throw new CachedItemNotFoundException(message);
        }
    }

    @Nonnull
    private ArtworkCacheData loadUrl(CacheService service, URL url) throws IOException, CachedItemNotFoundException, CachedItemInvalidException {
        OSLog.TimingLoggerCompat logger = Tag.ARTWORK.newTimingLogger("Standard artwork load (request " + ArtworkRequestId.next() + ")");
        ManagedTemporary managedTemporary = null;
        try {
            logger.addSplit("load url: " + url);
            HttpURLConnection connection = HttpUtils.open(url, true);
            SBCredentials creds = SBContextProvider.get().getConnectionCredentials();
            if (creds != null) {
                creds.apply(connection);
            }
            logger.addSplit("handling response");
            managedTemporary = processResponse(service, connection);

            long length = managedTemporary.size();
            if (length < 20) {
                throw new CachedItemNotFoundException("length was only " + length + " bytes, not a valid image");
            }
            logger.addSplit("wrote " + length + " bytes to " + managedTemporary);
            logger.close();

            ArtworkCacheData retval = null;
            if (mImageNeedsRescaling) {
                retval = new ScalingArtworkData(mContext, mEntry.getKey(), mType, mWidthPixels, managedTemporary);
            } else if (mCompressFormat == CompressFormat.JPEG) {
                // if we downloaded a PNG image but we want to store a JPEG, return artworkdata that will convert it
                BitmapDecoder decoder = BitmapDecoder.getInstance(mContext, mType);

                BitmapFactory.Options header = decoder.decodeHeader(managedTemporary.asByteSource());
                if (header.outMimeType == null || header.outMimeType.equals("image/png")) {
                    retval = new RecompressArtworkData(mContext, mEntry.getKey(), mType, managedTemporary);
                }
            }
            if (retval == null) {
                retval = new StandardArtworkCacheData(mContext, mEntry.getKey(), mType, managedTemporary);
            }

            // don't remove close managed temporary
            managedTemporary = null;

            return retval;
        } finally {
            // release managed temporary we're holding on failure
            if (managedTemporary != null) {
                managedTemporary.close();
            }
        }
    }

    @Nonnull
    private ManagedTemporary processResponse(CacheService service, HttpURLConnection connection) throws CachedItemNotFoundException, IOException {
        // defer creation of managed temporary until it's needed
        ManagedTemporary retval = null;
        String itemNotFoundMessage = null;

        Closer closer = Closer.create();
        try {
            // this can throw an IOException because it submits the initial request
            connection.connect();

            int statusCode = connection.getResponseCode();
            if (statusCode != HttpURLConnection.HTTP_OK) {
                switch (statusCode) {
                    case HttpURLConnection.HTTP_NOT_FOUND:
                        itemNotFoundMessage = "Remote artwork missing, marking as such in cache";
                        if (OSLog.isLoggable(Tag.ARTWORK, OSLog.VERBOSE)) {
                            OSLog.v(Tag.ARTWORK, itemNotFoundMessage);
                        }
                        break;
                    default:
                        itemNotFoundMessage = "Remote artwork missing (reason=" + connection.getResponseMessage() + ")";
                        OSLog.w(Tag.ARTWORK, itemNotFoundMessage);
                        break;
                }
            } else {
                InputStream is = closer.register(connection.getInputStream());

                retval = service.createManagedTemporary();
                retval.asByteSink().writeFrom(is);
            }
        } catch (Throwable t) {
            if (t instanceof IOException) {
                // invalidate connection
                connection.disconnect();
            }

            if (retval != null) {
                // close managed temporary too
                closer.register(retval);
            }

            throw closer.rethrow(t);
        } finally {
            closer.close();
        }

        if (retval == null) {
            throw new CachedItemNotFoundException(itemNotFoundMessage);
        }
        return retval;
    }

    @Override
    @Nonnull
    public ByteSource onSerializeForDatabaseCache(CacheService service, ArtworkCacheData data, AtomicLong outEstimatedSize) throws IOException {
        outEstimatedSize.set(data.getEstimatedSize());
        return data.getImageByteSource(ImageTarget.DATABASE);
    }

    @Override
    public int onEstimateMemorySize(CacheService service, InCacheArtworkData cacheData) {
        return (int) cacheData.getEstimatedSize();
    }

    @Nullable
    @Override
    public InCacheArtworkData onAdaptForMemoryCache(CacheService service, ArtworkCacheData dataToAdapt) throws IOException {
        if (dataToAdapt.getType().isThumbnail()) {
            return dataToAdapt.adaptForMemoryCache();
        }
        return null;
    }

    @Nonnull
    @Override
    public ArtworkCacheData onAdaptFromMemoryCache(CacheService service, InCacheArtworkData dataToAdapt) {
        return dataToAdapt.adaptFromMemoryCache();
    }

    @Override
    public boolean shouldMarkFailedRequests() {
        // failed and invalid artwork requests are cached for some duration
        return true;
    }


    @Nonnull
    private URL getCoverArtUrl(String id) throws MalformedURLException {
        mImageNeedsRescaling = false;
        ConnectionInfo ci = SBContextProvider.get().getConnectionInfo();
        if (mType.isThumbnail()) {
            return new URL("http://" + ci.getServerHost() + ":" + ci.getServerPort() + "/music/" + id + "/cover_" + mWidthPixels + "xX_F.jpg");
        } else {
            return new URL("http://" + ci.getServerHost() + ":" + ci.getServerPort() + "/music/" + id + "/cover.jpg");
        }
    }

    final private static Map<String, String> sProxyExtensionMap = ImmutableMap.<String, String>builder()
            .put("jpeg", "jpg")
            .put("png", "png")
            .put("jpg", "jpg")
            .build();

    @Nonnull
    private URL getServerResourceUrl(String icon) throws IOException {
        ConnectionInfo ci = SBContextProvider.get().getConnectionInfo();
        // by default, we need to scale the bitmap when loading it
        URL base = new URL("http://" + ci.getServerHost() + ":" + ci.getServerPort() + "/");
        URL resolved;

        // for thumbnails, add in some server-side resizing
        if (mType.isThumbnail()) {
            resolved = new URL(base, icon);
            // are we getting the image from LMS?
            if (resolved.getHost().equals(ci.getServerHost()) && resolved.getPort() == ci.getServerPort()) {
                int ndx;

                if ((ndx = icon.indexOf("{resizeParams}")) != -1) {
                    // if we can, add sizing data
                    String newIcon = icon.substring(0, ndx) + "_" + mWidthPixels + "xX_F";
                    resolved = new URL(base, newIcon);

                    // let the calling function know that we've already scaled
                    // it (thanks LMS!)
                    mImageNeedsRescaling = false;
                } else if ((ndx = icon.lastIndexOf(".")) != -1) {
                    // assume server artwork is in correct aspect ratio and doesn't need padding
                    // preserve extension
                    String extension = icon.substring(ndx + 1);

                    // if we can, add sizing data
                    String newIcon = icon.substring(0, ndx) + "_" + mWidthPixels + "x" + mWidthPixels + "." + extension;
                    resolved = new URL(base, newIcon);

                    // let the calling function know that we've already scaled
                    // it (thanks LMS!)
                    mImageNeedsRescaling = false;
                }
            } else {
                String path = resolved.getPath();
                InetAddress addr = InetAddress.getByName(resolved.getHost());
                if (!addr.isSiteLocalAddress() && path != null) {
                    // from SqueezePlay source, default format is blank
                    String format = null;
                    int lastDot = path.lastIndexOf(".");
                    if (lastDot != -1) {
                        String parsedFormat = Ascii.toLowerCase(path.substring(lastDot + 1).trim());
                        format = sProxyExtensionMap.get(parsedFormat);
                    }

                    //noinspection StringBufferReplaceableByString
                    StringBuilder builder = new StringBuilder();
                    builder.append("http://www.squeezenetwork.com/public/imageproxy?w=");
                    builder.append(mWidthPixels);
                    builder.append("&h=");
                    builder.append(mWidthPixels);
                    builder.append("&f=");
                    builder.append(Strings.nullToEmpty(format));
                    builder.append("&u=");
                    builder.append(URLEncoder.encode(resolved.toString(), "UTF-8"));

                    resolved = new URL(builder.toString());
                    mImageNeedsRescaling = false;
                }
            }
        } else {
            // not thumbnail, strip off resizeParams because we want the full image
            int ndx = icon.indexOf("{resizeParams}");
            if (ndx != -1) {
                icon = icon.substring(0, ndx);
            }
            resolved = new URL(base, icon);
        }
        return resolved;
    }

    static class StandardArtworkCacheData extends ManagedTemporaryArtworkCacheData {
        final private RecyclableBitmap mDecodedBitmap;

        public StandardArtworkCacheData(Context context, String artworkKey, ArtworkType type, ManagedTemporary managedTemporary) throws IOException {
            super(context, artworkKey, type, managedTemporary);

            BitmapDecoder decoder = BitmapDecoder.getInstance(context, type);

            mDecodedBitmap = decoder.decodeScaledBitmapForDeviceDisplay(managedTemporary.asByteSource(), Bitmap.Config.ARGB_8888);
        }

        @Nonnull
        @Override
        public InCacheArtworkData adaptForMemoryCache() throws IOException {
            return InCacheArtworkData.newInstance(mApplicationContext, mArtworkKey, mType, mDecodedBitmap);
        }

        @Nonnull
        @Override
        public RecyclableBitmap decodeBitmap() {
            mDecodedBitmap.incrementRefCount();

            return mDecodedBitmap;
        }

        @Override
        public void close() throws IOException {
            super.close();

            mDecodedBitmap.recycle();
        }
    }
}
