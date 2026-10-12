package local.aicy.browserhook;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

/** Exposes routing settings read-only; only the Aicy packages may report hook status. */
public final class ConfigProvider extends ContentProvider {
    public static final Uri URI = Uri.parse("content://local.aicy.browserhook.config");
    public static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("routing", Context.MODE_PRIVATE);
    }
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        Context context = getContext();
        if (context == null) return null;
        SharedPreferences prefs = preferences(context);
        if ("get".equals(method)) {
            Bundle result = new Bundle();
            result.putBoolean("enabled", prefs.getBoolean("enabled", true));
            result.putString("choice", RoutingPolicy.normalizeChoice(prefs.getString("choice", RoutingPolicy.CHROME)));
            result.putBoolean("web_search_entry", prefs.getBoolean("web_search_entry", true));
            return result;
        }
        if ("status".equals(method) && extras != null && mayReport(context)) {
            SharedPreferences status = context.getSharedPreferences("status", Context.MODE_PRIVATE);
            SharedPreferences.Editor edit = status.edit();
            String event = extras.getString("event", "");
            if ("loaded".equals(event)) {
                edit.putLong("loaded_at", System.currentTimeMillis());
                edit.putInt("hooks", extras.getInt("hooks", 0));
                edit.putInt("markers", extras.getInt("markers", 0));
                edit.putInt("panel_hooks", extras.getInt("panel_hooks", 0));
                edit.putString("version", extras.getString("version", "未知"));
                edit.putString("package", extras.getString("package", "未知"));
            } else if ("route".equals(event)) {
                edit.putLong("route_at", System.currentTimeMillis());
                edit.putInt("count", status.getInt("count", 0) + 1);
                edit.putString("target", extras.getString("target", ""));
                edit.putString("entry", extras.getString("entry", ""));
                edit.putBoolean("fallback", extras.getBoolean("fallback", false));
            } else if ("error".equals(event)) {
                edit.putString("error", extras.getString("error", "未知错误"));
                edit.putLong("error_at", System.currentTimeMillis());
            }
            edit.apply();
            return Bundle.EMPTY;
        }
        return null;
    }
    private boolean mayReport(Context context) {
        int uid = Binder.getCallingUid();
        if (uid == android.os.Process.myUid()) return true;
        String[] packages = context.getPackageManager().getPackagesForUid(uid);
        if (packages != null) for (String pkg : packages) if (RoutingPolicy.isAicyPackage(pkg)) return true;
        return false;
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
