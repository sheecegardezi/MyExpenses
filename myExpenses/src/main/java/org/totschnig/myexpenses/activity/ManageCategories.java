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

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.dialog.EditTextDialog;
import org.totschnig.myexpenses.dialog.ProgressDialogFragment;
import org.totschnig.myexpenses.dialog.SelectGroupingDialogFragment;
import org.totschnig.myexpenses.dialog.EditTextDialog.EditTextDialogListener;
import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.model.Category;
import org.totschnig.myexpenses.model.Model;
import org.totschnig.myexpenses.model.Account.Grouping;
import org.totschnig.myexpenses.provider.DatabaseConstants;
import org.totschnig.myexpenses.task.TaskExecutionFragment;
import org.totschnig.myexpenses.util.FileUtils;
import org.totschnig.myexpenses.util.Result;
import org.totschnig.myexpenses.fragment.CategoryList;
import org.totschnig.myexpenses.fragment.DbWriteFragment;
import org.totschnig.myexpenses.util.Utils;

import com.github.mikephil.charting.charts.PieChart;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.MenuItemCompat;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.ArrayList;

/**
 * SelectCategory activity allows to select categories while editing a transaction
 * and also managing (creating, deleting, importing)
 *
 * @author Michael Totschnig
 */
public class ManageCategories extends ProtectedFragmentActivity implements
    EditTextDialogListener, DbWriteFragment.TaskCallbacks {

  public enum HelpVariant {
    manage, distribution, select_mapping, select_filter
  }

  private Category mCategory;
  private GestureDetector mDetector;
  private static final int SWIPE_MIN_DISTANCE = 120;
  private static final int SWIPE_MAX_OFF_PATH = 250;
  private static final int SWIPE_THRESHOLD_VELOCITY = 100;
  private CategoryList mListFragment;

  public CategoryList getListFragment() {
    return mListFragment;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    setTheme(MyApplication.getThemeId());
    super.onCreate(savedInstanceState);
    Intent intent = getIntent();
    String action = intent.getAction();
    int layoutId = R.layout.select_category;
    if (action == null) {
      helpVariant = HelpVariant.select_mapping;
      getSupportActionBar().setTitle(R.string.select_category);
    } else if (action.equals("myexpenses.intent.manage.categories") ||
        action.equals(Intent.ACTION_MAIN)) {
      helpVariant = HelpVariant.manage;
      getSupportActionBar().setTitle(R.string.pref_manage_categories_title);
    } else if (action.equals("myexpenses.intent.distribution")) {
      helpVariant = HelpVariant.distribution;
      layoutId = R.layout.distribution;
      //title is set in categories list
      DisplayMetrics dm = getResources().getDisplayMetrics();

      final int REL_SWIPE_MIN_DISTANCE = (int) (SWIPE_MIN_DISTANCE * dm.densityDpi / 160.0f);
      final int REL_SWIPE_MAX_OFF_PATH = (int) (SWIPE_MAX_OFF_PATH * dm.densityDpi / 160.0f);
      final int REL_SWIPE_THRESHOLD_VELOCITY = (int) (SWIPE_THRESHOLD_VELOCITY * dm.densityDpi / 160.0f);
      mDetector = new GestureDetector(this,
          new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2,
                                   float velocityX, float velocityY) {
              //http://stackoverflow.com/questions/937313/android-basic-gesture-detection
              try {
                if (Math.abs(e1.getY() - e2.getY()) > REL_SWIPE_MAX_OFF_PATH)
                  return false;
                if (e1.getX() - e2.getX() > REL_SWIPE_MIN_DISTANCE
                    && Math.abs(velocityX) > REL_SWIPE_THRESHOLD_VELOCITY) {
                  mListFragment.forward();
                  return true;
                } else if (e2.getX() - e1.getX() > REL_SWIPE_MIN_DISTANCE
                    && Math.abs(velocityX) > REL_SWIPE_THRESHOLD_VELOCITY) {
                  mListFragment.back();
                  return true;
                }
              } catch (Exception e) {
              }
              return false;
            }
          });
    } else if (action.equals("myexpenses.intent.select_filter")) {
      helpVariant = HelpVariant.select_filter;
      getSupportActionBar().setTitle(R.string.search_category);
    }
    setContentView(layoutId);
    FragmentManager fm = getSupportFragmentManager();
    mListFragment = ((CategoryList) fm.findFragmentById(R.id.category_list));
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    if (helpVariant.equals(HelpVariant.distribution)) {
      inflater.inflate(R.menu.distribution, menu);

      ToggleButton typeButton = (ToggleButton)
          MenuItemCompat.getActionView(menu.findItem(R.id.switchId))
              .findViewById(R.id.TaType);

      typeButton.setOnCheckedChangeListener(new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
          mListFragment.setType(isChecked);
        }
      });

    } else if (!helpVariant.equals(HelpVariant.select_filter)) {
      inflater.inflate(R.menu.categories, menu);
    }
    super.onCreateOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean dispatchCommand(int command, Object tag) {
    switch (command) {
      case R.id.CREATE_COMMAND:
        createCat(null);
        return true;
      case R.id.GROUPING_COMMAND:
        SelectGroupingDialogFragment.newInstance(mListFragment.mGrouping.ordinal())
            .show(getSupportFragmentManager(), "SELECT_GROUPING");
        return true;
      case R.id.GROUPING_COMMAND_DO:
        mListFragment.setGrouping(Account.Grouping.values()[(Integer) tag]);
        return true;
      case R.id.DELETE_COMMAND_DO:
        finishActionMode();
        startTaskExecution(
            TaskExecutionFragment.TASK_DELETE_CATEGORY,
            (Long[]) tag,
            null,
            R.string.progress_dialog_deleting);
        return true;
      case R.id.CANCEL_CALLBACK_COMMAND:
        finishActionMode();
        return true;
      case R.id.SETUP_CATEGORIES_DEFAULT_COMMAND:
        importCats();
        return true;
      case R.id.EXPORT_CATEGORIES_COMMAND_ISO88591:
        exportCats("ISO-8859-1");
        return true;
      case R.id.EXPORT_CATEGORIES_COMMAND_UTF8:
        exportCats("UTF-8");
        return true;
    }
    return super.dispatchCommand(command, tag);
  }

  /**
   * presents AlertDialog for adding a new category
   * if label is already used, shows an error
   *
   * @param parentId
   */
  public void createCat(Long parentId) {
    Bundle args = new Bundle();
    int dialogTitle;
    if (parentId != null) {
      args.putLong(DatabaseConstants.KEY_PARENTID, parentId);
      dialogTitle = R.string.menu_create_sub_cat;
    } else
      dialogTitle = R.string.menu_create_main_cat;
    args.putString(EditTextDialog.KEY_DIALOG_TITLE, getString(dialogTitle));
    EditTextDialog.newInstance(args).show(getSupportFragmentManager(), "CREATE_CATEGORY");
  }

  /**
   * presents AlertDialog for editing an existing category
   * if label is already used, shows an error
   *
   * @param label
   * @param catId
   */
  public void editCat(String label, Long catId) {
    Bundle args = new Bundle();
    args.putLong(DatabaseConstants.KEY_ROWID, catId);
    args.putString(EditTextDialog.KEY_DIALOG_TITLE, getString(R.string.menu_edit_cat));
    args.putString(EditTextDialog.KEY_VALUE, label);
    EditTextDialog.newInstance(args).show(getSupportFragmentManager(), "EDIT_CATEGORY");
  }

  /**
   * Callback from button
   *
   * @param v
   */
  public void importCats(View v) {
    importCats();
  }

  private void importCats() {
    getSupportFragmentManager()
        .beginTransaction()
        .add(TaskExecutionFragment.newInstanceGrisbiImport(false, null, true, false),
            ProtectionDelegate.ASYNC_TAG)
        .add(ProgressDialogFragment.newInstance(
            0, 0, ProgressDialog.STYLE_HORIZONTAL, false), ProtectionDelegate.PROGRESS_TAG)
        .commit();

  }

  private void exportCats(String encoding) {
    startTaskExecution(
        TaskExecutionFragment.TASK_EXPORT_CATEGRIES,
        null,
        encoding,
        R.string.menu_categories_export);
  }

  @Override
  public void onFinishEditDialog(Bundle args) {
    Long parentId;
    if ((parentId = args.getLong(DatabaseConstants.KEY_PARENTID)) == 0L)
      parentId = null;
    mCategory = new Category(
        args.getLong(DatabaseConstants.KEY_ROWID),
        args.getString(EditTextDialog.KEY_RESULT),
        parentId);
    startDbWriteTask(false);
    finishActionMode();
  }

  @Override
  public void onCancelEditDialog() {
    finishActionMode();
  }

  private void finishActionMode() {
    if (mListFragment != null)
      mListFragment.finishActionMode();
  }

  @Override
  public void onPostExecute(Object result) {
    if (result == null) {
      Toast.makeText(ManageCategories.this,
          getString(R.string.already_defined,
              mCategory != null ? mCategory.label : ""),
          Toast.LENGTH_LONG)
          .show();
    }
    super.onPostExecute(result);
  }

  //callback from grisbi import task
  @Override
  public void onPostExecute(int taskId, Object result) {
    super.onPostExecute(taskId, result);
    if (!(result instanceof Result)) {
      return;
    }
    Result r = (Result) result;
    String msg = null;
    if (r.success) {
      switch (taskId) {
        case TaskExecutionFragment.TASK_GRISBI_IMPORT:
          Integer imported = (Integer) r.extra[0];
          if (imported > 0) {
            msg = getString(R.string.import_categories_success, imported);
          } else {
            msg = getString(R.string.import_categories_none);
          }
          break;
        case TaskExecutionFragment.TASK_EXPORT_CATEGRIES:
          Uri uri = (Uri) r.extra[0];
          msg = getString(r.getMessage(),
            FileUtils.getPath(MyApplication.getInstance(), uri));
          if (MyApplication.PrefKey.PERFORM_SHARE.getBoolean(false)) {
            ArrayList<Uri> uris = new ArrayList<>();
            uris.add(uri);
            Utils.share(this, uris,
                MyApplication.PrefKey.SHARE_TARGET.getString("").trim(),
                "text/qif");
          }
          break;
      }
    }  else {
      msg = r.print(this);
    }
    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
  }

  @Override
  public Model getObject() {
    return mCategory;
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent event) {
    if (mDetector != null && !mListFragment.mGrouping.equals(Grouping.NONE)) {
      if (mDetector.onTouchEvent(event)) {
        return true;
      }
    }
    // Be sure to call the superclass implementation
    return super.dispatchTouchEvent(event);
  }
}