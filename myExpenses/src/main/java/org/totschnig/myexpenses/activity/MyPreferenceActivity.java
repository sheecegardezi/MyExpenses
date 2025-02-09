/*   This file is part of My Expenses.
 *   My Expenses is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   My Expenses is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with My Expenses.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.totschnig.myexpenses.activity;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.Settings.Secure;
import android.support.v4.provider.DocumentFile;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.MyApplication.PrefKey;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.dialog.DialogUtils;
import org.totschnig.myexpenses.model.ContribFeature;
import org.totschnig.myexpenses.preference.CalendarListPreference;
import org.totschnig.myexpenses.provider.TransactionProvider;
import org.totschnig.myexpenses.service.DailyAutoBackupScheduler;
import org.totschnig.myexpenses.util.FileUtils;
import org.totschnig.myexpenses.util.Utils;
import org.totschnig.myexpenses.widget.AbstractWidget;
import org.totschnig.myexpenses.widget.AccountWidget;
import org.totschnig.myexpenses.widget.TemplateWidget;

import java.io.Serializable;
import java.net.URI;
import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Present references screen defined in Layout file
 * @author Michael Totschnig
 *
 */
public class MyPreferenceActivity extends ProtectedPreferenceActivity implements
    OnPreferenceChangeListener,
    OnSharedPreferenceChangeListener,
    OnPreferenceClickListener, ContribIFace {
  
  private static final int RESTORE_REQUEST = 1;
  private static final int PICK_FOLDER_REQUEST = 2;
  public static final String KEY_OPEN_PREF_KEY = "openPrefKey";

  @SuppressWarnings("deprecation")
  @Override
  public void onCreate(Bundle savedInstanceState) {
    setTheme(MyApplication.getThemeId(Build.VERSION.SDK_INT < 11));
    super.onCreate(savedInstanceState);
    setTitle(Utils.concatResStrings(this, R.string.app_name, R.string.menu_settings));
    addPreferencesFromResource(R.xml.preferences);
    Preference pref = findPreference(PrefKey.SHARE_TARGET.getKey());
    pref.setSummary(getString(R.string.pref_share_target_summary) + ":\n" +
        "ftp: \"ftp://login:password@my.example.org:port/my/directory/\"\n" +
        "mailto: \"mailto:john@my.example.com\"");
    pref.setOnPreferenceChangeListener(this);
    configureContribPrefs();
    findPreference(PrefKey.SEND_FEEDBACK.getKey())
      .setOnPreferenceClickListener(this);
    findPreference(PrefKey.MORE_INFO_DIALOG.getKey())
      .setOnPreferenceClickListener(this);

    pref = findPreference(PrefKey.RESTORE.getKey());
    pref.setTitle(getString(R.string.pref_restore_title) + " (ZIP)");
    pref.setOnPreferenceClickListener(this);

    pref = findPreference(PrefKey.RESTORE_LEGACY.getKey());
    pref.setTitle(getString(R.string.pref_restore_title) + " (" + getString(R.string.pref_restore_alternative) + ")");
    pref.setOnPreferenceClickListener(this);

    findPreference(PrefKey.RATE.getKey())
    .setOnPreferenceClickListener(this);

    findPreference(PrefKey.ENTER_LICENCE.getKey())
      .setOnPreferenceChangeListener(this);
    setProtectionDependentsState();

    pref = findPreference(PrefKey.CUSTOM_DECIMAL_FORMAT.getKey());
    pref.setOnPreferenceChangeListener(this);
    if (PrefKey.CUSTOM_DECIMAL_FORMAT.getString("").equals("")) {
      setDefaultNumberFormat(((EditTextPreference) pref));
    }
    
    findPreference(PrefKey.APP_DIR.getKey())
    .setOnPreferenceClickListener(this);
    setAppDirSummary();
    if (savedInstanceState == null &&
        TextUtils.equals(
            getIntent().getStringExtra(KEY_OPEN_PREF_KEY),
            PrefKey.PLANNER_CALENDAR_ID.getKey())) {
      ((CalendarListPreference) findPreference(PrefKey.PLANNER_CALENDAR_ID.getKey())).show();
    }
    
    findPreference(PrefKey.SHORTCUT_CREATE_TRANSACTION.getKey()).setOnPreferenceClickListener(this);
    findPreference(PrefKey.SHORTCUT_CREATE_TRANSFER.getKey()).setOnPreferenceClickListener(this);
    findPreference(PrefKey.SHORTCUT_CREATE_SPLIT.getKey()).setOnPreferenceClickListener(this);
    findPreference(PrefKey.SECURITY_QUESTION.getKey()).setSummary(
        getString(R.string.pref_security_question_summary) + " " +
            ContribFeature.SECURITY_QUESTION.buildRequiresString(this));
    findPreference(PrefKey.SHORTCUT_CREATE_SPLIT.getKey()).setSummary(
        getString(R.string.pref_shortcut_summary) + " " +
            ContribFeature.SPLIT_TRANSACTION.buildRequiresString(this));

    final PreferenceCategory categoryManage = ((PreferenceCategory) findPreference(PrefKey.CATEGORY_MANAGE.getKey()));
    final Preference prefStaleImages = findPreference(PrefKey.MANAGE_STALE_IMAGES.getKey());
    categoryManage.removePreference(prefStaleImages);

    pref = findPreference(PrefKey.IMPORT_QIF.getKey());
    pref.setSummary(getString(R.string.pref_import_summary,"QIF"));
    pref.setTitle(getString(R.string.pref_import_title, "QIF"));
    pref = findPreference(PrefKey.IMPORT_CSV.getKey());
    pref.setSummary(getString(R.string.pref_import_summary, "CSV"));
    pref.setTitle(getString(R.string.pref_import_title, "CSV"));
    pref.setOnPreferenceClickListener(this);

    new AsyncTask<Void, Void, Boolean>(){
      @Override
      protected Boolean doInBackground(Void... params) {
        Cursor c = getContentResolver().query(
            TransactionProvider.STALE_IMAGES_URI,
            new String[]{"count(*)"},
            null, null, null);
        if (c==null)
          return false;
        boolean hasImages = false;
        if (c.moveToFirst() &&  c.getInt(0) >0)
          hasImages = true;
        c.close();
        return hasImages;
      }

      @Override
      protected void onPostExecute(Boolean result) {
        if (!isFinishing() && result)
          categoryManage.addPreference(prefStaleImages);
      }
    }.execute();
  }

  private void configureContribPrefs() {
    Preference pref1 = findPreference(PrefKey.REQUEST_LICENCE.getKey()),
        pref2 = findPreference(PrefKey.CONTRIB_PURCHASE.getKey()),
        pref3 = findPreference(PrefKey.AUTO_BACKUP.getKey());
    if (MyApplication.getInstance().isExtendedEnabled()) {
      PreferenceCategory cat = ((PreferenceCategory) findPreference(PrefKey.CATEGORY_CONTRIB.getKey()));
      cat.removePreference(pref1);
      cat.removePreference(pref2);
    } else {
      if (pref1!=null && pref2!=null) {//if a user replaces a valid key with an invalid key, we might run into that uncommon situation
        pref1.setOnPreferenceClickListener(this);
        pref1.setSummary(getString(R.string.pref_request_licence_summary, Secure.getString(getContentResolver(), Secure.ANDROID_ID)));
        int baseTitle = MyApplication.getInstance().isContribEnabled() ?
            R.string.pref_contrib_purchase_title_upgrade : R.string.pref_contrib_purchase_title;
        if (Utils.IS_FLAVOURED) {
          pref2.setTitle(getString(baseTitle) + " (" + getString(R.string.pref_contrib_purchase_title_in_app) + ")");
        } else {
          pref2.setTitle(baseTitle);
        }
        pref2.setOnPreferenceClickListener(this);
      }
    }

    findPreference(PrefKey.SHORTCUT_CREATE_SPLIT.getKey()).setEnabled(MyApplication.getInstance().isContribEnabled());

    String summary = getString(R.string.pref_auto_backup_summary) + " " +
        ContribFeature.AUTO_BACKUP.buildRequiresString(this);
    pref3.setSummary(summary);
    pref3.setOnPreferenceChangeListener(this);
  }
  private void setProtectionDependentsState() {
    boolean isProtected = PrefKey.PERFORM_PROTECTION.getBoolean(false);
    findPreference(PrefKey.SECURITY_QUESTION.getKey()).setEnabled( MyApplication.getInstance().isContribEnabled() && isProtected);
    findPreference(PrefKey.PROTECTION_DELAY_SECONDS.getKey()).setEnabled(isProtected);
    findPreference(PrefKey.PROTECTION_ENABLE_ACCOUNT_WIDGET.getKey()).setEnabled(isProtected);
    findPreference(PrefKey.PROTECTION_ENABLE_TEMPLATE_WIDGET.getKey()).setEnabled(isProtected);
    findPreference(PrefKey.PROTECTION_ENABLE_DATA_ENTRY_FROM_WIDGET.getKey()).setEnabled(isProtected);
  }

  @Override
  protected void onResume() {
    super.onResume();
    PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
    if (getIntent()!=null && getIntent().getAction() != null &&
        getIntent().getAction().equals("myexpenses.intent.preference.password")) {
      //only used for screenshot generation
      setPreferenceScreen((PreferenceScreen)findPreference(getString(R.string.pref_screen_protection)));
    }
  }
  @Override
  protected void onPause() {
    super.onPause();
    PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
  }
  @Override
  public boolean onPreferenceChange(Preference pref, Object value) {
    String key = pref.getKey();
    if (key.equals(PrefKey.SHARE_TARGET.getKey())) {
      String target = (String) value;
      URI uri;
      if (!target.equals("")) {
        uri = Utils.validateUri(target);
        if (uri == null) {
          Toast.makeText(getBaseContext(),getString(R.string.ftp_uri_malformed,target), Toast.LENGTH_LONG).show();
          return false;
        }
        String scheme = uri.getScheme();
        if (!(scheme.equals("ftp") || scheme.equals("mailto"))) {
          Toast.makeText(getBaseContext(),getString(R.string.share_scheme_not_supported,scheme), Toast.LENGTH_LONG).show();
          return false;
        }
        Intent intent;
        if (scheme.equals("ftp")) {
          intent = new Intent(android.content.Intent.ACTION_SENDTO);
          intent.setData(android.net.Uri.parse(target));
          if (!Utils.isIntentAvailable(this,intent)) {
            showDialog(R.id.FTP_DIALOG);
          }
        }
      }
    } else if (key.equals(PrefKey.ENTER_LICENCE.getKey())) {
      Utils.LicenceStatus licenceStatus = Utils.verifyLicenceKey((String) value);
      if (licenceStatus!=null) {
       Toast.makeText(getBaseContext(),
           Utils.concatResStrings(this,
               R.string.licence_validation_success,
               (licenceStatus == Utils.LicenceStatus.EXTENDED ?
               R.string.licence_validation_extended :R.string.licence_validation_premium)),
           Toast.LENGTH_LONG).show();
      } else {
        Toast.makeText(getBaseContext(), R.string.licence_validation_failure, Toast.LENGTH_LONG).show();
      }
      MyApplication.getInstance().setContribEnabled(licenceStatus);
      setProtectionDependentsState();
      configureContribPrefs();
    } else if (key.equals(PrefKey.CUSTOM_DECIMAL_FORMAT.getKey())) {
      if (TextUtils.isEmpty((String) value)) {
        Utils.setNumberFormat(NumberFormat.getCurrencyInstance());
        return true;
      }
      try {
        DecimalFormat nf = new DecimalFormat();
        nf.applyLocalizedPattern(((String) value));
        Utils.setNumberFormat(nf);
      } catch (IllegalArgumentException e) {
        Toast.makeText(getBaseContext(), R.string.number_format_illegal, Toast.LENGTH_LONG).show();
        return false;
      }
    } else if (key.equals(PrefKey.AUTO_BACKUP.getKey())) {
      if (!((Boolean) value) || ContribFeature.AUTO_BACKUP.hasAccess()) {
        return true;
      }
      CommonCommands.showContribDialog(this,ContribFeature.AUTO_BACKUP,null);
      return ContribFeature.AUTO_BACKUP.usagesLeft()>0;
    }
    return true;
  }
  private void setDefaultNumberFormat(EditTextPreference pref) {
    String pattern = ((DecimalFormat) NumberFormat.getCurrencyInstance()).toLocalizedPattern();
    //Log.d(MyApplication.TAG,pattern);
    pref.setText(pattern);
  }
  private void restart() {
    Intent intent = getIntent();
    finish();
    startActivity(intent);
  }
  @Override
  protected Dialog onCreateDialog(int id) {
    switch(id) {
    case R.id.FTP_DIALOG:
      return DialogUtils.sendWithFTPDialog((Activity) this);
    case R.id.MORE_INFO_DIALOG:
      LayoutInflater li = LayoutInflater.from(this);
      View view = li.inflate(R.layout.more_info, null);
      ((TextView)view.findViewById(R.id.aboutVersionCode)).setText(CommonCommands.getVersionInfo(this));
      return new AlertDialog.Builder(this)
        .setTitle(R.string.pref_more_info_dialog_title)
        .setView(view)
        .setPositiveButton(android.R.string.ok,null)
        .create();
    case R.id.PLANNER_SETUP_INFO_CREATE_NEW_WARNING_DIALOG:
      return new AlertDialog.Builder(this)
      .setTitle(R.string.dialog_title_attention)
      .setMessage(R.string.planner_setup_info_create_new_warning)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(android.R.string.ok,new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int id) {
        //TODO: use Async Task Strict Mode violation
        boolean success = MyApplication.getInstance().createPlanner();
        Toast.makeText(
            MyPreferenceActivity.this,
            success ? R.string.planner_create_calendar_success : R.string.planner_create_calendar_failure,
            Toast.LENGTH_LONG).show();
          }
       })
      .create();
    }
    return null;
  }
  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
      String key) {
    if (key.equals(PrefKey.PERFORM_PROTECTION.getKey())) {
      setProtectionDependentsState();
      AbstractWidget.updateWidgets(this, AccountWidget.class);
      AbstractWidget.updateWidgets(this, TemplateWidget.class);
    } else if (key.equals(PrefKey.UI_FONTSIZE.getKey()) ||
        key.equals(PrefKey.UI_LANGUAGE.getKey()) ||
        key.equals(PrefKey.UI_THEME_KEY.getKey())) {
      restart();
    } else if (key.equals(PrefKey.PROTECTION_ENABLE_ACCOUNT_WIDGET.getKey())) {
      //Log.d("DEBUG","shared preference changed: Account Widget");
      AbstractWidget.updateWidgets(this, AccountWidget.class);
    } else if (key.equals(PrefKey.PROTECTION_ENABLE_TEMPLATE_WIDGET.getKey())) {
      //Log.d("DEBUG","shared preference changed: Template Widget");
      AbstractWidget.updateWidgets(this, TemplateWidget.class);
    } else if (key.equals(PrefKey.ACCOUNT_GROUPING.getKey())) {
      getContentResolver().notifyChange(TransactionProvider.ACCOUNTS_URI, null);
    } else if (key.equals(PrefKey.AUTO_BACKUP.getKey()) || key.equals(PrefKey.AUTO_BACKUP_TIME.getKey())) {
      DailyAutoBackupScheduler.updateAutoBackupAlarms(this);
    }
  }
  @Override
  public boolean onPreferenceClick(Preference preference) {
    if (preference.getKey().equals(PrefKey.CONTRIB_PURCHASE.getKey())) {
      if (MyApplication.getInstance().isExtendedEnabled()) {
        //showDialog(R.id.DONATE_DIALOG);//should not happen
      } else {
        Intent i = new Intent(this,ContribInfoDialogActivity.class);
        startActivity(i);
      }
      return true;
    }
    if (preference.getKey().equals(PrefKey.REQUEST_LICENCE.getKey())) {
      String androidId = Secure.getString(getContentResolver(),Secure.ANDROID_ID);
      Intent i = new Intent(android.content.Intent.ACTION_SEND);
      i.setType("plain/text");
      i.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{ MyApplication.FEEDBACK_EMAIL });
      i.putExtra(android.content.Intent.EXTRA_SUBJECT,
          "[" + getString(R.string.app_name) + "] " + getString(R.string.contrib_key));
      i.putExtra(android.content.Intent.EXTRA_TEXT,
          getString(R.string.request_licence_mail_head,androidId)
          + " \n\n[" + getString(R.string.request_licence_mail_description) + "]");
      if (!Utils.isIntentAvailable(MyPreferenceActivity.this,i)) {
        Toast.makeText(getBaseContext(),R.string.no_app_handling_email_available, Toast.LENGTH_LONG).show();
      } else {
        startActivity(i);
      }
      return true;
    }
    if (preference.getKey().equals(PrefKey.SEND_FEEDBACK.getKey())) {
      CommonCommands.dispatchCommand(this, R.id.FEEDBACK_COMMAND, null);
      return true;
    }
    if (preference.getKey().equals(PrefKey.RATE.getKey())) {
      PrefKey.NEXT_REMINDER_RATE.putLong(-1);
      CommonCommands.dispatchCommand(this, R.id.RATE_COMMAND, null);
      return true;
    }
    if (preference.getKey().equals(PrefKey.MORE_INFO_DIALOG.getKey())) {
      showDialog(R.id.MORE_INFO_DIALOG);
      return true;
    }
    if (preference.getKey().equals(PrefKey.RESTORE.getKey()) ||
        preference.getKey().equals(PrefKey.RESTORE_LEGACY.getKey())) {
      startActivityForResult(preference.getIntent(), RESTORE_REQUEST);
      return true;
    }
    if (preference.getKey().equals(PrefKey.APP_DIR.getKey())) {
      DocumentFile appDir = Utils.getAppDir();
      if (appDir == null) {
        preference.setSummary(R.string.external_storage_unavailable);
        preference.setEnabled(false);
      } else {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
          try {
            startActivityForResult(intent, PICK_FOLDER_REQUEST);
            return true;
          } catch (ActivityNotFoundException e) {
            Utils.reportToAcra(e);
            //fallback to FolderBrowser
          }
        }
        intent = new Intent(this, FolderBrowser.class);
        intent.putExtra(FolderBrowser.PATH, appDir.getUri().getPath());
        startActivityForResult(intent, PICK_FOLDER_REQUEST);
      }
      return true;
    }
    if (preference.getKey().equals(PrefKey.SHORTCUT_CREATE_TRANSACTION.getKey())) {
      Bundle extras = new Bundle();
      extras.putBoolean(AbstractWidget.EXTRA_START_FROM_WIDGET, true);
      extras.putBoolean(AbstractWidget.EXTRA_START_FROM_WIDGET_DATA_ENTRY, true);
      addShortcut(".activity.ExpenseEdit",R.string.transaction, R.drawable.shortcut_create_transaction_icon,extras);
      return true;
    }
    if (preference.getKey().equals(PrefKey.SHORTCUT_CREATE_TRANSFER.getKey())) {
      Bundle extras = new Bundle();
      extras.putBoolean(AbstractWidget.EXTRA_START_FROM_WIDGET, true);
      extras.putBoolean(AbstractWidget.EXTRA_START_FROM_WIDGET_DATA_ENTRY, true);
      extras.putInt(MyApplication.KEY_OPERATION_TYPE, MyExpenses.TYPE_TRANSFER);
      addShortcut(".activity.ExpenseEdit",R.string.transfer, R.drawable.shortcut_create_transfer_icon,extras);
      return true;
    }
    if (preference.getKey().equals(PrefKey.SHORTCUT_CREATE_SPLIT.getKey())) {
      Bundle extras = new Bundle();
      extras.putBoolean(AbstractWidget.EXTRA_START_FROM_WIDGET, true);
      extras.putBoolean(AbstractWidget.EXTRA_START_FROM_WIDGET_DATA_ENTRY, true);
      extras.putInt(MyApplication.KEY_OPERATION_TYPE, MyExpenses.TYPE_SPLIT);
      addShortcut(".activity.ExpenseEdit",R.string.split_transaction, R.drawable.shortcut_create_split_icon,extras);
      return true;
    }
    if (preference.getKey().equals(PrefKey.IMPORT_CSV.getKey())) {
      if (ContribFeature.CSV_IMPORT.hasAccess()) {
        contribFeatureCalled(ContribFeature.CSV_IMPORT,null);
      } else {
        CommonCommands.showContribDialog(this, ContribFeature.CSV_IMPORT, null);
      }
      return true;
    }
    return false;
  }
  @Override
  protected void onActivityResult(int requestCode, int resultCode, 
      Intent intent) {
    if (requestCode == RESTORE_REQUEST && resultCode == RESULT_FIRST_USER) {
      setResult(resultCode);
      finish();
    } else if (requestCode == PICK_FOLDER_REQUEST && resultCode == RESULT_OK) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        Uri dir = intent.getData();
        getContentResolver().takePersistableUriPermission(dir,
            Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        PrefKey.APP_DIR.putString(intent.getData().toString());
      }
      setAppDirSummary();
    } else if (requestCode == ProtectionDelegate.CONTRIB_REQUEST && resultCode == RESULT_OK) {
        contribFeatureCalled(
            (ContribFeature) intent.getSerializableExtra(ContribInfoDialogActivity.KEY_FEATURE),
            intent.getSerializableExtra(ContribInfoDialogActivity.KEY_TAG));
    }
  }
  private void setAppDirSummary() {
    Preference pref = findPreference(PrefKey.APP_DIR.getKey());
    if (Utils.isExternalStorageAvailable()) {
      DocumentFile appDir = Utils.getAppDir();
      if (appDir != null) {
        pref.setSummary(FileUtils.getPath(this,appDir.getUri()));
        return;
      }
    }
    pref.setSummary(R.string.external_storage_unavailable);
    pref.setEnabled(false);
  }

  // credits Financisto
  // src/ru/orangesoftware/financisto/activity/PreferencesActivity.java
  private void addShortcut(String activity, int nameId, int iconId, Bundle extra) {
    Intent shortcutIntent = createShortcutIntent(activity);
    if (extra != null) {
      shortcutIntent.putExtras(extra);
    }
    Intent intent = new Intent();
    intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
    intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(nameId));
    intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(this, iconId));
    intent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");

    if (Utils.isIntentReceiverAvailable(this, intent)) {
      sendBroadcast(intent);
      Toast.makeText(getBaseContext(),getString(R.string.pref_shortcut_added), Toast.LENGTH_LONG).show();
    } else {
      Toast.makeText(getBaseContext(),getString(R.string.pref_shortcut_not_added), Toast.LENGTH_LONG).show();
    }
}

  private Intent createShortcutIntent(String activity) {
    Intent shortcutIntent = new Intent();
    shortcutIntent.setComponent(new ComponentName(this.getPackageName(), activity));
    shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    return shortcutIntent;
  }
  private Intent findDirPicker() {
    Intent intent = new Intent("com.estrongs.action.PICK_DIRECTORY ");
    intent.putExtra("com.estrongs.intent.extra.TITLE", "Select Directory");
    if (Utils.isIntentAvailable(this, intent)) {
      return intent;
    }
    return null;
  }
  public void onCalendarListPreferenceSet() {
    if (TextUtils.equals(
        getIntent().getStringExtra(KEY_OPEN_PREF_KEY),
        PrefKey.PLANNER_CALENDAR_ID.getKey())) {
      setResult(RESULT_OK);
      finish();
    }
  }

  @Override
  public void contribFeatureCalled(ContribFeature feature, Serializable tag) {
    if (feature==ContribFeature.CSV_IMPORT) {
      Intent i = new Intent(this,CsvImportActivity.class);
      startActivity(i);
    }
  }

  @Override
  public void contribFeatureNotCalled() {

  }
}