package openfoodfacts.github.scrachx.openfood.views;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NavUtils;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import butterknife.BindView;
import butterknife.OnClick;
import openfoodfacts.github.scrachx.openfood.BuildConfig;
import openfoodfacts.github.scrachx.openfood.R;
import openfoodfacts.github.scrachx.openfood.models.HistoryItem;
import openfoodfacts.github.scrachx.openfood.models.HistoryProduct;
import openfoodfacts.github.scrachx.openfood.models.HistoryProductDao;
import openfoodfacts.github.scrachx.openfood.utils.SwipeController;
import openfoodfacts.github.scrachx.openfood.utils.SwipeControllerActions;
import openfoodfacts.github.scrachx.openfood.utils.Utils;
import openfoodfacts.github.scrachx.openfood.views.adapters.HistoryListAdapter;

public class HistoryScanActivity extends BaseActivity implements SwipeControllerActions {

    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.listHistoryScan)
    RecyclerView recyclerHistoryScanView;
    private List<HistoryItem> productItems;
    private boolean emptyHistory;
    private HistoryProductDao mHistoryProductDao;
    private HistoryListAdapter adapter;
    private List<HistoryProduct> listHistoryProducts;
    @BindView(R.id.scanFirst)
    Button scanFirst;
    @BindView(R.id.empty_history_info)
    TextView infoView;
    @BindView(R.id.history_progressbar)
    ProgressBar historyProgressbar;
    private static String SORT_TYPE = "none";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getResources().getBoolean(R.bool.portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        setContentView(R.layout.activity_history_scan);
        setTitle(getString(R.string.scan_history_drawer));

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mHistoryProductDao = Utils.getAppDaoSession(this).getHistoryProductDao();
        productItems = new ArrayList<>();
        setInfo(infoView);
        new HistoryScanActivity.FillAdapter(this).execute(this);
    }

    @Override
    public void onRightClicked(int position) {
        if (listHistoryProducts != null && listHistoryProducts.size() > 0) {
            mHistoryProductDao.delete(listHistoryProducts.get(position));
        }
        adapter.remove(productItems.get(position));
        adapter.notifyItemRemoved(position);
        adapter.notifyItemRangeChanged(position, adapter.getItemCount());

        if (adapter.getItemCount() == 0) {

            infoView.setVisibility(View.VISIBLE);
            scanFirst.setVisibility(View.VISIBLE);

        }
    }


    public void exportCSV() {
        String folder_main = " ";
        String appname = " ";
        if ((BuildConfig.FLAVOR.equals("off"))) {
            folder_main = " Open Food Facts ";
            appname = "OFF";
        } else if ((BuildConfig.FLAVOR.equals("opff"))) {
            folder_main = " Open Pet Food Facts ";
            appname = "OPFF";
        } else if ((BuildConfig.FLAVOR.equals("opf"))) {
            folder_main = " Open Products Facts ";
            appname = "OPF";
        } else {
            folder_main = " Open Beauty Facts ";
            appname = "OBF";
        }
        Toast.makeText(this, R.string.txt_exporting_history, Toast.LENGTH_LONG).show();
        File baseDir = new File(Environment.getExternalStorageDirectory(), folder_main);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        Log.d("dir", String.valueOf(baseDir));
        String fileName = appname + "-" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".csv";
        String filePath = baseDir + File.separator + fileName;
        File f = new File(filePath);
        CSVWriter writer;
        FileWriter fileWriter;
        try {
            if (f.exists() && !f.isDirectory()) {
                fileWriter = new FileWriter(filePath, false);
                writer = new CSVWriter(fileWriter);
            } else {
                writer = new CSVWriter(new FileWriter(filePath));
            }
            String[] headers = getResources().getStringArray(R.array.headers);
            writer.writeNext(headers);
            List<HistoryProduct> listHistoryProducts = mHistoryProductDao.loadAll();
            for (HistoryProduct hp : listHistoryProducts) {
                String[] line = {hp.getBarcode(), hp.getTitle(), hp.getBrands()};
                writer.writeNext(line);
            }
            writer.close();
            Toast.makeText(this, R.string.txt_history_exported, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_history, menu);

        menu.findItem(R.id.action_export_all_history)
                .setVisible(!emptyHistory);

        menu.findItem(R.id.action_remove_all_history)
                .setVisible(!emptyHistory);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.action_remove_all_history:
                new MaterialDialog.Builder(this)
                        .title(R.string.title_clear_history_dialog)
                        .content(R.string.text_clear_history_dialog)
                        .onPositive((dialog, which) -> {
                            mHistoryProductDao.deleteAll();
                            productItems.clear();
                            recyclerHistoryScanView.getAdapter().notifyDataSetChanged();
                            infoView.setVisibility(View.VISIBLE);
                            scanFirst.setVisibility(View.VISIBLE);
                        })
                        .positiveText(R.string.txtYes)
                        .negativeText(R.string.txtNo)
                        .show();
                return true;
            case R.id.action_export_all_history:
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        new MaterialDialog.Builder(this)
                                .title(R.string.action_about)
                                .content(R.string.permision_write_external_storage)
                                .neutralText(R.string.txtOk)
                                .show();
                    } else {
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, Utils
                                .MY_PERMISSIONS_REQUEST_STORAGE);
                    }
                } else {
                    exportCSV();
                }
                return true;

            case R.id.sort_history:
                MaterialDialog.Builder builder = new MaterialDialog.Builder(this);
                builder.title(R.string.sort_by);
                String[] sortTypes = {getString(R.string.by_title), getString(R.string.by_brand), getString(R.string.by_nutrition_grade), getString(R.string.by_barcode), getString(R.string.by_time)};
                builder.items(sortTypes);
                builder.itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog dialog, View itemView, int position, CharSequence text) {

                        switch (position) {

                            case 0:
                                SORT_TYPE = "title";
                                callTask();
                                break;

                            case 1:
                                SORT_TYPE = "brand";
                                callTask();
                                break;


                            case 2:
                                SORT_TYPE = "grade";
                                callTask();
                                break;


                            case 3:
                                SORT_TYPE = "barcode";
                                callTask();
                                break;


                            default:
                                SORT_TYPE = "time";
                                callTask();
                                break;


                        }


                    }
                });
                builder.show();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case Utils.MY_PERMISSIONS_REQUEST_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    exportCSV();
                } else {
                    new MaterialDialog.Builder(this)
                            .title(R.string.permission_title)
                            .content(R.string.permission_denied)
                            .negativeText(R.string.txtNo)
                            .positiveText(R.string.txtYes)
                            .onPositive((dialog, which) -> {
                                Intent intent = new Intent();
                                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package", getPackageName(), null);
                                intent.setData(uri);
                                startActivity(intent);
                            })
                            .show();
                }
                break;
            }
        }
    }


    public class FillAdapter extends AsyncTask<Context, Void, Context> {

        private Activity activity;

        public FillAdapter(Activity act) {
            activity = act;
        }

        @Override
        protected void onPreExecute() {
            historyProgressbar.setVisibility(View.VISIBLE);
            productItems.clear();
            List<HistoryProduct> listHistoryProducts = mHistoryProductDao.loadAll();
            if (listHistoryProducts.size() == 0) {
                emptyHistory = true;
                historyProgressbar.setVisibility(View.GONE);
                infoView.setVisibility(View.VISIBLE);
                scanFirst.setVisibility(View.VISIBLE);
                invalidateOptionsMenu();
                cancel(true);
            }
        }

        @Override
        protected Context doInBackground(Context... ctx) {
            listHistoryProducts = mHistoryProductDao.queryBuilder().orderDesc(HistoryProductDao.Properties.LastSeen).list();


            for (HistoryProduct historyProduct : listHistoryProducts) {
                productItems.add(new HistoryItem(historyProduct.getTitle(), historyProduct.getBrands(), historyProduct.getUrl(), historyProduct
                        .getBarcode(), historyProduct.getLastSeen(), historyProduct.getQuantity(), historyProduct.getNutritionGrade()));
            }

            return ctx[0];
        }

        @Override
        protected void onPostExecute(Context ctx) {

            sort(SORT_TYPE, productItems);
            adapter = new HistoryListAdapter(productItems, getString(R.string
                    .website_product), activity);
            recyclerHistoryScanView.setAdapter(adapter);
            recyclerHistoryScanView.setLayoutManager(new LinearLayoutManager(ctx));
            historyProgressbar.setVisibility(View.GONE);

            SwipeController swipeController = new SwipeController(ctx, HistoryScanActivity.this);
            ItemTouchHelper itemTouchhelper = new ItemTouchHelper(swipeController);
            itemTouchhelper.attachToRecyclerView(recyclerHistoryScanView);
            recyclerHistoryScanView.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
                    swipeController.onDraw(c);
                }
            });
        }
    }


    @OnClick(R.id.scanFirst)
    protected void onScanFirst() {


        if (Utils.isHardwareCameraInstalled(getBaseContext())) {
            if (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(HistoryScanActivity.this, Manifest.permission.CAMERA)) {
                    new MaterialDialog.Builder(getBaseContext())
                            .title(R.string.action_about)
                            .content(R.string.permission_camera)
                            .neutralText(R.string.txtOk)
                            .onNeutral((dialog, which) -> ActivityCompat.requestPermissions(HistoryScanActivity.this, new String[]{Manifest
                                    .permission.CAMERA}, Utils.MY_PERMISSIONS_REQUEST_CAMERA))
                            .show();
                } else {
                    ActivityCompat.requestPermissions(HistoryScanActivity.this, new String[]{Manifest.permission.CAMERA}, Utils
                            .MY_PERMISSIONS_REQUEST_CAMERA);
                }
            } else {
                Intent intent = new Intent(HistoryScanActivity.this, ScannerFragmentActivity.class);
                startActivity(intent);
            }
        }

    }

    public void setInfo(TextView view) {

        String info = getString(R.string.scan_first_string);

        view.setText(info);

    }

    /**
     * Function to compare history items based on title, brand, barcode, time and nutrition grade
     *
     * @param sortType     String to determine type of sorting
     * @param productItems List of history items to be sorted
     */

    private void sort(String sortType, List<HistoryItem> productItems) {


        switch (sortType) {

            case "title":

                Collections.sort(productItems, new Comparator<HistoryItem>() {
                    @Override
                    public int compare(HistoryItem historyItem, HistoryItem t1) {
                        return historyItem.getTitle().compareToIgnoreCase(t1.getTitle());
                    }
                });

                break;


            case "brand":

                Collections.sort(productItems, new Comparator<HistoryItem>() {
                    @Override
                    public int compare(HistoryItem historyItem, HistoryItem t1) {
                        return historyItem.getBrands().compareToIgnoreCase(t1.getBrands());
                    }
                });

                break;

            case "barcode":

                Collections.sort(productItems, new Comparator<HistoryItem>() {
                    @Override
                    public int compare(HistoryItem historyItem, HistoryItem t1) {
                        return historyItem.getBarcode().compareTo(t1.getBarcode());
                    }
                });
                break;

            case "grade":
                Collections.sort(productItems, new Comparator<HistoryItem>() {

                    @Override
                    public int compare(HistoryItem historyItem, HistoryItem t1) {
                        String nGrade1;
                        String nGrade2;
                        if (historyItem.getNutritionGrade() == null) {
                            nGrade1 = "E";
                        } else {
                            nGrade1 = historyItem.getNutritionGrade();
                        }
                        if (t1.getNutritionGrade() == null) {
                            nGrade2 = "E";
                        } else {
                            nGrade2 = t1.getNutritionGrade();
                        }
                        return nGrade1.compareToIgnoreCase(nGrade2);
                    }
                });

                break;


            default:

                Collections.sort(productItems, new Comparator<HistoryItem>() {
                    @Override
                    public int compare(HistoryItem historyItem, HistoryItem t1) {
                        return 0;
                    }
                });


        }


    }

    private void callTask() {
        new HistoryScanActivity.FillAdapter(HistoryScanActivity.this).execute(HistoryScanActivity.this);

    }


}
