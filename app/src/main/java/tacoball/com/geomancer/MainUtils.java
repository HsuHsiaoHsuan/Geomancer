package tacoball.com.geomancer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import tacoball.com.geomancer.checkupdate.ConfirmUpdateService;

/**
 * 共用程式
 */
public class MainUtils {

    private static final String TAG = "MainUtils";

    // 更新通知的 ID
    private static final int NFID_UPDATE = 233;

    // 各偏好設定 KEY 值
    private static final String PREFKEY_UPDATE_REQUEST   = "UPDATE_REQUEST";     // 使用者要求更新
    private static final String PREFKEY_UPDATE_BY_MOFILE = "UPDATE_FROM_MOFILE"; // 允許行動網路更新

    // 地圖檔名
//    public static final String MAP_NAME = "taiwan-taco.map";

    // 資料庫檔名
    public static final String UNLUCKY_HOUSE = "unluckyhouse.sqlite";
    public static final String UNLUCKY_LABOR = "unluckylabor.sqlite";

    // 更新伺服器
    public static final String[] MIRROR_SITES = {
        "mirror.ossplanet.net",  // Mirror
        "tacosync.com",          // Web 1
        "sto.tacosync.com",      // Web 2
        "192.168.1.81",          // WiFi LAN 1 (Debug)
        "192.168.1.172",         // WiFi LAN 2 (Debug)
        "192.168.42.170"         // USB LAN (Debug)
    };
    public static final String MIRROR_PATTERN = "http://%s/geomancer/0.0.10/%s.gz";
    public static final int    MIRROR_NUM = 0;

    // 需要檢查更新的檔案清單
    public static final String[] REQUIRED_FILES = {
        /*MAP_NAME,*/ UNLUCKY_HOUSE, UNLUCKY_LABOR
    };

    // 檔案版本最低要求 (單位: Unix Timestamp x 1000)
    // 使用這個指令觀察: stat -c '%Y %n' *.gz
    public static final long[] REQUIRED_MTIME = {
        1471928015000L,
        1472000000000L,
        1473845300000L
    };

    /**
     * 取得檔案的遠端位置
     */
    public static String getRemoteURL(int fileIndex) {
        return String.format(Locale.getDefault(), MIRROR_PATTERN, MIRROR_SITES[MIRROR_NUM], REQUIRED_FILES[fileIndex]);
    }

    /**
     * 取得檔案的本地存檔路徑
     */
    public static File getSavePath(Context context, int fileIndex) throws IOException {
        if (context==null) {
            throw new IOException("Context is null");
        }

        if (fileIndex>=REQUIRED_FILES.length) {
            throw new IOException("fileIndex out of range");
        }

        String filename = REQUIRED_FILES[fileIndex];
        int begin = filename.lastIndexOf('.') + 1;
        String ext = filename.substring(begin);
        String category = ext;
        if (ext.equals("sqlite")) category = "db";

        File[] dirs = context.getExternalFilesDirs(category);
        for (int i=dirs.length-1;i>=0;i--) {
            if (dirs[i]!=null) return dirs[i];
        }

        throw new IOException("Cannot get save path");
    }

    /**
     * 取得檔案的本地完整路徑
     */
    public static File getFilePath(Context context, int fileIndex) throws IOException {
        return new File(getSavePath(context, fileIndex), REQUIRED_FILES[fileIndex]);
    }

    /**
     * 清理儲存空間
     */
    public static void cleanStorage(Context context) {
        File[] dirs = context.getExternalFilesDirs("database");
        for (int i=dirs.length-1;i>=0;i--) {
            if (dirs[i]!=null) {
                try {
                    FileUtils.deleteDirectory(dirs[i]);
                } catch(IOException ex) {
                    Log.e(TAG, getReason(ex));
                }
            }
        }
    }

    /**
     * 例外訊息改進程式，避免捕捉例外時還發生例外
     */
    public static String getReason(final Exception ex) {
        String msg = ex.getMessage();

        if (msg==null) {
            StackTraceElement ste = ex.getStackTrace()[0];
            msg = String.format(
                Locale.getDefault(),
                "%s with null message (%s.%s() Line:%d)",
                ex.getClass().getSimpleName(),
                ste.getClassName(),
                ste.getMethodName(),
                ste.getLineNumber()
            );
        }

        return msg;
    }

    /**
     * 移除資料更新的系統通知
     */
    public static void clearUpdateNotification(Context context) {
        NotificationManager notiMgr = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        notiMgr.cancel(TAG, NFID_UPDATE);
    }

    /**
     * 產生資料更新的系統通知
     */
    public static void buildUpdateNotification(Context context, double mblen) {
        Intent itYes = new Intent(context, ConfirmUpdateService.class).setAction("Yes");
        Intent itNo  = new Intent(context, ConfirmUpdateService.class).setAction("No");
        PendingIntent piYes = PendingIntent.getService(context, 0, itYes, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent piNo  = PendingIntent.getService(context, 0, itNo, PendingIntent.FLAG_UPDATE_CURRENT);
        long[] vpat = {0, 100, 100, 100};

        BitmapDrawable sic = (BitmapDrawable)context.getResources().getDrawable(R.mipmap.geomancer);
        // TODO: Don't know how to kill LINT message.
        Bitmap lic = sic.getBitmap();

        String longPat = context.getString(R.string.pattern_confirm_update_long);
        String longMsg = String.format(Locale.getDefault(), longPat, mblen);

        String shortPat = context.getString(R.string.pattern_confirm_update_short);
        String shortMsg = String.format(Locale.getDefault(), shortPat, mblen);

        NotificationCompat.Style style = new NotificationCompat.BigTextStyle().bigText(longMsg);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        Notification nf = builder
            .setContentTitle(context.getString(R.string.term_update))
            .setContentText(shortMsg)
            .setStyle(style)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setLargeIcon(lic)
            .setVibrate(vpat)
            .addAction(android.R.drawable.ic_menu_save, context.getString(R.string.term_yes), piYes)
            .addAction(android.R.drawable.ic_menu_delete, context.getString(R.string.term_no), piNo)
            .setAutoCancel(false)
            .build();

        NotificationManager notiMgr = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        notiMgr.notify(TAG, NFID_UPDATE, nf);
    }

    /**
     * 紀錄使用者更新要求
     */
    public static void setUpdateRequest(Context context) {
        SharedPreferences.Editor pedit = PreferenceManager.getDefaultSharedPreferences(context).edit();
        pedit.putBoolean(PREFKEY_UPDATE_REQUEST, true).apply();
    }

    /**
     * 清除使用者更新要求
     */
    public static void clearUpdateRequest(Context context) {
        SharedPreferences.Editor pedit = PreferenceManager.getDefaultSharedPreferences(context).edit();
        pedit.putBoolean(PREFKEY_UPDATE_REQUEST, false).apply();
    }

    /**
     * 確認使用者是否要求更新
     */
    public static boolean hasUpdateRequest(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(PREFKEY_UPDATE_REQUEST, false);
    }

    /**
     * 是否允許透過行動網路更新
     */
    public static boolean canUpdateByMobile(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(PREFKEY_UPDATE_BY_MOFILE, true);
    }

}
