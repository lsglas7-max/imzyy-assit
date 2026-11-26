package com.imzyyknox.gameturbo;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;

/** Displays per-package cache estimates. Android owns the final cache deletion confirmation. */
public class CacheControlActivity extends Activity {
    private final int CY=0xFF53E9FF, MUTED=0xFF91A4B8;
    private static final int REQ_CLEAR_CACHE = 5101;
    private LinearLayout list;
    private EditText search;
    private TextView summary;
    private final ArrayList<AppEntry> apps = new ArrayList<>();
    private static class AppEntry { String label,pkg; long cache; AppEntry(String l,String p,long c){label=l;pkg=p;cache=c;} }
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable panel(){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF102237,0xFF09121E});d.setCornerRadius(dp(16));d.setStroke(dp(1),0x3353E9FF);return d;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return t;}
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(0xFF050A12);getWindow().setNavigationBarColor(0xFF050A12);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(16),dp(16),dp(16));root.setBackgroundColor(0xFF050A12);
        TextView title=text("CACHE CONTROL CENTER",18,0xFFFFFFFF,true);title.setLetterSpacing(.08f);root.addView(title);
        TextView sub=text("ALL APPS • CACHE SIZE • GUIDED CLEANUP",9,CY,true);sub.setPadding(0,dp(4),0,dp(12));root.addView(sub);
        LinearLayout notice=new LinearLayout(this);notice.setOrientation(LinearLayout.VERTICAL);notice.setPadding(dp(13),dp(12),dp(13),dp(12));notice.setBackground(panel());
        notice.addView(text("LIVE CACHE INVENTORY",12,CY,true));
        notice.addView(text("SYSTEM CACHE RECLAIMER • Android menampilkan konfirmasi resmi untuk cache eksternal aplikasi yang eligible. Perlu izin Akses semua file. Cache internal/terlindungi mungkin tidak ikut terhapus.",10,0xFFD0DCE8,false));root.addView(notice);
        Button clearAll=new Button(this);clearAll.setText("CLEAR ALL APP CACHE • SYSTEM");clearAll.setOnClickListener(v->requestSystemCacheClear());root.addView(clearAll,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        Button access=new Button(this);access.setText("USAGE ACCESS");access.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));}catch(Exception e){Toast.makeText(this,"Pengaturan Usage Access tidak tersedia",Toast.LENGTH_SHORT).show();}});actions.addView(access,new LinearLayout.LayoutParams(0,-2,1));
        Button scan=new Button(this);scan.setText("SCAN ALL APPS");scan.setOnClickListener(v->scanApps());LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-2,1);sp.leftMargin=dp(6);actions.addView(scan,sp);root.addView(actions);
        search=new EditText(this);search.setSingleLine(true);search.setTextColor(0xFFFFFFFF);search.setHintTextColor(MUTED);search.setHint("Cari aplikasi...");root.addView(search);
        summary=text("Menunggu pemindaian...",10,MUTED,false);root.addView(summary);
        ScrollView scroll=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){render(s.toString());}public void afterTextChanged(android.text.Editable e){}});
        setContentView(root);scanApps();
    }
    private boolean hasUsageAccess(){try{AppOpsManager ops=(AppOpsManager)getSystemService(APP_OPS_SERVICE);return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),getPackageName())==AppOpsManager.MODE_ALLOWED;}catch(Exception e){return false;}}
    private String size(long b){if(b<1024)return b+" B";if(b<1024L*1024)return String.format(Locale.US,"%.1f KB",b/1024d);if(b<1024L*1024*1024)return String.format(Locale.US,"%.1f MB",b/(1024d*1024));return String.format(Locale.US,"%.2f GB",b/(1024d*1024*1024));}
    private void scanApps(){
        if(!hasUsageAccess()){summary.setText("Usage Access belum aktif. Aktifkan melalui tombol di atas, lalu tekan SCAN ALL APPS.");apps.clear();render("");return;}
        summary.setText("Memindai cache seluruh aplikasi...");
        new Thread(()->{
            ArrayList<AppEntry> found=new ArrayList<>();long total=0;int failed=0;PackageManager pm=getPackageManager();StorageStatsManager sm=(StorageStatsManager)getSystemService(STORAGE_STATS_SERVICE);
            for(ApplicationInfo info:pm.getInstalledApplications(PackageManager.GET_META_DATA)){
                if(info.packageName.equals(getPackageName()))continue;
                try{StorageStats st=sm.queryStatsForPackage(StorageManager.UUID_DEFAULT,info.packageName,android.os.Process.myUserHandle());long cache=st.getCacheBytes();found.add(new AppEntry(String.valueOf(info.loadLabel(pm)),info.packageName,cache));total+=cache;}catch(Exception e){failed++;}
            }
            Collections.sort(found,(a,b)->Long.compare(b.cache,a.cache));final long totalBytes=total;final int failedCount=failed;
            runOnUiThread(()->{apps.clear();apps.addAll(found);summary.setText(found.size()+" aplikasi dipindai • cache terukur "+size(totalBytes)+(failedCount>0?" • "+failedCount+" tidak terbaca":""));render(search==null?"":search.getText().toString());});
        }).start();
    }
    private void render(String filter){list.removeAllViews();int shown=0;String q=filter.toLowerCase(Locale.ROOT);
        for(AppEntry a:apps){if(!a.label.toLowerCase(Locale.ROOT).contains(q)&&!a.pkg.toLowerCase(Locale.ROOT).contains(q))continue;
            LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.HORIZONTAL);item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(dp(10),dp(8),dp(8),dp(8));item.setBackground(panel());
            LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.addView(text(a.label,12,0xFFFFFFFF,true));names.addView(text(a.pkg+" • Cache: "+size(a.cache),8,MUTED,false));item.addView(names,new LinearLayout.LayoutParams(0,-2,1));
            Button clear=new Button(this);clear.setText("CLEAR");clear.setTextSize(9);clear.setOnClickListener(v->clearDirect(a.pkg,a.label));item.addView(clear);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(7);list.addView(item,lp);shown++;
        }
        if(shown==0)list.addView(text(apps.isEmpty()?"Belum ada data. Aktifkan Usage Access dan scan ulang.":"Tidak ada aplikasi yang cocok.",11,MUTED,false));
    }
    private void requestSystemCacheClear(){
        if(Build.VERSION.SDK_INT < 30){
            Toast.makeText(this,"Fitur system cache cleanup memerlukan Android 11 atau lebih baru",Toast.LENGTH_LONG).show();
            return;
        }
        if(!Environment.isExternalStorageManager()){
            new android.app.AlertDialog.Builder(this)
                .setTitle("Izin Akses Semua File")
                .setMessage("Android memerlukan izin khusus ini sebelum menampilkan dialog pembersihan cache sistem. Izin ini tidak memberi IMZYY hak untuk menghapus data privat aplikasi lain. Aktifkan hanya jika kamu setuju.")
                .setNegativeButton("BATAL",null)
                .setPositiveButton("BUKA PENGATURAN",(d,w)->{
                    try{Intent i=new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName()));startActivity(i);}
                    catch(Exception e){try{startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}catch(Exception ex){Toast.makeText(this,"Pengaturan izin tidak tersedia",Toast.LENGTH_LONG).show();}}
                }).show();
            return;
        }
        new android.app.AlertDialog.Builder(this)
            .setTitle("Konfirmasi pembersihan cache")
            .setMessage("Android akan membuka dialog sistem untuk membersihkan cache eksternal aplikasi yang dinilai bisa dibersihkan. Ini bukan hapus data aplikasi. Sebagian cache internal atau cache yang dilindungi sistem mungkin tetap ada. Lanjutkan?")
            .setNegativeButton("BATAL",null)
            .setPositiveButton("LANJUTKAN",(d,w)->{
                try{startActivityForResult(new Intent(StorageManager.ACTION_CLEAR_APP_CACHE),REQ_CLEAR_CACHE);}
                catch(Exception e){Toast.makeText(this,"Dialog pembersihan cache sistem tidak tersedia pada firmware ini",Toast.LENGTH_LONG).show();}
            }).show();
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQ_CLEAR_CACHE){
            if(resultCode==Activity.RESULT_OK){Toast.makeText(this,"Permintaan pembersihan cache sistem selesai",Toast.LENGTH_LONG).show();}
            else if(resultCode==android.system.OsConstants.EIO){Toast.makeText(this,"Android melaporkan kegagalan saat membersihkan cache",Toast.LENGTH_LONG).show();}
            else{Toast.makeText(this,"Pembersihan dibatalkan oleh pengguna atau sistem",Toast.LENGTH_LONG).show();}
            scanApps();
        }
    }
    @Override protected void onResume(){super.onResume(); if(search!=null && Build.VERSION.SDK_INT>=30 && Environment.isExternalStorageManager()) { /* permission is ready; user can start cleanup explicitly */ }}
    /** Clear cache satu app langsung dari sini lewat IMZYY Cleaner; tanpa Cleaner, sistem melepas cache senyap. Tidak lagi membuka halaman app untuk dibersihkan manual. */
    private void clearDirect(final String pkg,final String label){
        if(CleanerService.enabled()){CleanerService.enqueue(pkg,CleanerService.CLEAR_CACHE,label);Toast.makeText(this,"Membersihkan cache "+label+"…",Toast.LENGTH_SHORT).show();new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::scanApps,4500);return;}
        Toast.makeText(this,"Cleaner belum aktif • cache dilepas lewat sistem",Toast.LENGTH_SHORT).show();
        new Thread(()->{CacheEngine.reclaim(this);runOnUiThread(this::scanApps);}).start();
    }
    private void openAppStorage(String pkg){try{Intent i=Build.VERSION.SDK_INT>=31?new Intent("android.settings.MANAGE_APP_STORAGE",Uri.parse("package:"+pkg)):new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+pkg));startActivity(i);}catch(Exception e){try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+pkg)));}catch(Exception ex){Toast.makeText(this,"Halaman penyimpanan aplikasi tidak tersedia",Toast.LENGTH_SHORT).show();}}}
}
