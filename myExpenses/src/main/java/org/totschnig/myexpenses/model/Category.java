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

package org.totschnig.myexpenses.model;

import org.apache.commons.lang3.StringUtils;
import org.totschnig.myexpenses.provider.DbUtils;
import org.totschnig.myexpenses.provider.TransactionProvider;
import org.totschnig.myexpenses.util.Utils;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;
import static org.totschnig.myexpenses.provider.DatabaseConstants.*;

//TODO implement complete DAO
//for the moment we only wrap calls to the content provider
public class Category extends Model {
  public String label;
  public Long parentId;

  /**
   * we currently do not need a full representation of a category as an object
   * when we create an instance with an id, we only want to alter its label
   * and are not interested in its parentId
   * when we create an instance with a parentId, it is a new instance
   * @param id
   * @param label
   * @param parentId
   */
  public Category(Long id, String label,Long parentId) {
    this.setId(id);
    this.label = StringUtils.strip(label);
    this.parentId = parentId;
  }
  public static final String[] PROJECTION = new String[] {KEY_ROWID, KEY_LABEL, KEY_PARENTID};
  public static final Uri CONTENT_URI = TransactionProvider.CATEGORIES_URI;

  /**
   * inserts a new category if id = 0, or alters an existing one if id != 0
   * @param id 0 if a new instance, database id otherwise
   * @param label
   * @param parentId a new instance is created under this parent, ignored for existing instances
   * @return id of new record, or -1, if it already exists
   */
  public static long write(long id, String label, Long parentId) {
    Uri uri = new Category(id,label,parentId).save();
    return uri == null ? -1 : Integer.valueOf(uri.getLastPathSegment());
  }

  /**
   * Looks for a cat with a label under a given parent
   * @param label
   * @param parentId
   * @return id or -1 if not found
   */
  public static long find(String label, Long parentId) {
    label = StringUtils.strip(label);
    String selection;
    String[] selectionArgs;
    if (parentId == null) {
      selection = KEY_PARENTID + " is null";
      selectionArgs = new String[]{label};
    } else {
      selection = KEY_PARENTID + " = ?";
      selectionArgs = new String[]{String.valueOf(parentId),label};
    }
    selection += " and " + KEY_LABEL + " = ?";
    Cursor mCursor = cr().query(CONTENT_URI,
        new String[] {KEY_ROWID}, selection, selectionArgs, null);
    if (mCursor.getCount() == 0) {
      mCursor.close();
      return -1;
    } else {
      mCursor.moveToFirst();
      long result = mCursor.getLong(0);
      mCursor.close();
      return result;
    }
  }
  public static boolean delete(long id) {
    return cr().delete(CONTENT_URI,
        KEY_PARENTID + " =  ?  OR " + KEY_ROWID + " = ?",
        new String[]{String.valueOf(id),String.valueOf(id)}
        ) > 0;
  }
  @Override
  public Uri save() {
    ContentValues initialValues = new ContentValues();
    initialValues.put(KEY_LABEL, label);
    Uri uri;
    if (getId() == 0) {
      if (!isMain(parentId)) {
        uri = null;
        Utils.reportToAcra(new Exception("Attempt to store deep category hierarchy detected"));
      } else {
        initialValues.put(KEY_PARENTID, parentId);
        try {
          uri = cr().insert(CONTENT_URI, initialValues);
        } catch (SQLiteConstraintException e) {
          uri = null;
        }
      }
    } else {
      uri = CONTENT_URI.buildUpon().appendPath(String.valueOf(getId())).build();
      try {
        cr().update(CONTENT_URI.buildUpon().appendPath(String.valueOf(getId())).build(),
            initialValues, null, null);
      } catch (SQLiteConstraintException e) {
        uri = null;
      }
    }
    return uri;
  }

  private static boolean isMain(Long parentId) {
    if (parentId==null) {
      return true;
    }
    Cursor mCursor = cr().query(CONTENT_URI,
            new String[] {KEY_PARENTID}, KEY_ROWID + " = ?", new String[] {String.valueOf(parentId)}, null);
    if (mCursor.getCount() == 0) {
      mCursor.close();
      return false;
    } else {
      mCursor.moveToFirst();
      long result = DbUtils.getLongOr0L(mCursor,0);
      mCursor.close();
      return result == 0L;
    }
  }

  /**
   * How many subcategories under a given parent?
   * @param parentId
   * @return number of subcategories
   */
  public static int countSub(long parentId){
    Cursor mCursor = cr().query(CONTENT_URI,
        new String[] {"count(*)"}, KEY_PARENTID + " = ?", new String[] {String.valueOf(parentId)}, null);
    if (mCursor.getCount() == 0) {
      mCursor.close();
      return 0;
    } else {
      mCursor.moveToFirst();
      int result = mCursor.getInt(0);
      mCursor.close();
      return result;
    }
  }
}