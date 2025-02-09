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

import java.io.File;
import java.io.IOException;
import java.util.Date;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.provider.DatabaseConstants;
import org.totschnig.myexpenses.provider.DbUtils;
import org.totschnig.myexpenses.provider.TransactionProvider;
import org.totschnig.myexpenses.util.Utils;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;

import static org.totschnig.myexpenses.provider.DatabaseConstants.*;

/**
 * Domain class for transactions
 *
 * @author Michael Totschnig
 */
public class Transaction extends Model {
  public String comment = "", payee = "", referenceNumber = "";
  /**
   * stores a short label of the category or the account the transaction is linked to
   */
  public String label = "";
  protected Date date;
  public Money amount;
  private Long catId;
  public Long accountId;
  public Long transfer_peer;
  public Long transfer_account;
  public Long methodId;
  public String methodLabel = "";
  public Long parentId = null;
  public Long payeeId = null;
  /**
   * id of the template which defines the plan for which this transaction has been created
   */
  public Long originTemplateId = null;
  /**
   * id of an instance of the event (plan) for which this transaction has been created
   */
  public Long originPlanInstanceId = null;
  /**
   * 0 = is normal, special states are
   * {@link org.totschnig.myexpenses.provider.DatabaseConstants#STATUS_EXPORTED} and
   * {@link org.totschnig.myexpenses.provider.DatabaseConstants#STATUS_UNCOMMITTED}
   */
  public int status = 0;
  public static String[] PROJECTION_BASE, PROJECTION_EXTENDED;

  static {
    PROJECTION_BASE = new String[]{
        KEY_ROWID,
        KEY_DATE,
        KEY_AMOUNT,
        KEY_COMMENT,
        KEY_CATID,
        LABEL_MAIN,
        LABEL_SUB,
        KEY_PAYEE_NAME,
        KEY_TRANSFER_PEER,
        KEY_METHODID,
        KEY_METHOD_LABEL,
        KEY_CR_STATUS,
        KEY_REFERENCE_NUMBER,
        KEY_PICTURE_URI,
        YEAR_OF_WEEK_START + " AS " + KEY_YEAR_OF_WEEK_START,
        YEAR + " AS " + KEY_YEAR,
        MONTH + " AS " + KEY_MONTH,
        WEEK + " AS " + KEY_WEEK,
        DAY + " AS " + KEY_DAY,
        THIS_YEAR_OF_WEEK_START + " AS " + KEY_THIS_YEAR_OF_WEEK_START,
        THIS_YEAR + " AS " + KEY_THIS_YEAR,
        THIS_WEEK + " AS " + KEY_THIS_WEEK,
        THIS_DAY + " AS " + KEY_THIS_DAY,
        WEEK_START + " AS " + KEY_WEEK_START,
        WEEK_END + " AS " + KEY_WEEK_END
    };
    int baseLength = PROJECTION_BASE.length;
    PROJECTION_EXTENDED = new String[baseLength + 3];
    System.arraycopy(PROJECTION_BASE, 0, PROJECTION_EXTENDED, 0, baseLength);
    PROJECTION_EXTENDED[baseLength] = KEY_COLOR;
    //the definition of column TRANSFER_PEER_PARENT refers to view_extended,
    //thus can not be used in PROJECTION_BASE
    PROJECTION_EXTENDED[baseLength + 1] = TRANSFER_PEER_PARENT + " AS transfer_peer_parent";
    PROJECTION_EXTENDED[baseLength + 2] = KEY_STATUS;
  }

  public static final Uri CONTENT_URI = TransactionProvider.TRANSACTIONS_URI;
  public static final Uri EXTENDED_URI = CONTENT_URI.buildUpon().appendQueryParameter(
      TransactionProvider.QUERY_PARAMETER_EXTENDED, "1").build();


  public enum CrStatus {
    UNRECONCILED(Color.GRAY, ""), CLEARED(Color.BLUE, "*"), RECONCILED(Color.GREEN, "X"), VOID(Color.RED, null);
    public int color;
    public String symbol;

    CrStatus(int color, String symbol) {
      this.color = color;
      this.symbol = symbol;
    }

    public String toString() {
      Context ctx = MyApplication.getInstance();
      switch (this) {
        case CLEARED:
          return ctx.getString(R.string.status_cleared);
        case RECONCILED:
          return ctx.getString(R.string.status_reconciled);
        case UNRECONCILED:
          return ctx.getString(R.string.status_uncreconciled);
        case VOID:
          return ctx.getString(R.string.status_void);
      }
      return super.toString();
    }

    public static final String JOIN;

    static {
      JOIN = Utils.joinEnum(CrStatus.class);
    }

    public static CrStatus fromQifName(String qifName) {
      if (qifName == null)
        return UNRECONCILED;
      if (qifName.equals("*")) {
        return CLEARED;
      } else if (qifName.equalsIgnoreCase("X")) {
        return RECONCILED;
      } else {
        return UNRECONCILED;
      }
    }
  }

  public CrStatus crStatus;
  protected Uri pictureUri;

  /**
   * factory method for retrieving an instance from the db with the given id
   *
   * @param id
   * @return instance of {@link Transaction} or {@link Transfer} or null if not found
   */
  public static Transaction getInstanceFromDb(long id) {
    Transaction t;
    String[] projection = new String[]{KEY_ROWID, KEY_DATE, KEY_AMOUNT, KEY_COMMENT, KEY_CATID,
        FULL_LABEL, KEY_PAYEEID, KEY_PAYEE_NAME, KEY_TRANSFER_PEER, KEY_TRANSFER_ACCOUNT, KEY_ACCOUNTID, KEY_METHODID,
        KEY_PARENTID, KEY_CR_STATUS, KEY_REFERENCE_NUMBER, KEY_PICTURE_URI, KEY_METHOD_LABEL, KEY_STATUS};

    Cursor c = cr().query(
        CONTENT_URI.buildUpon().appendPath(String.valueOf(id)).build(), projection, null, null, null);
    if (c == null) {
      return null;
    }
    if (c.getCount() == 0) {
      c.close();
      return null;
    }
    c.moveToFirst();
    Long transfer_peer = DbUtils.getLongOrNull(c, KEY_TRANSFER_PEER);
    long account_id = c.getLong(c.getColumnIndexOrThrow(KEY_ACCOUNTID));
    long amount = c.getLong(c.getColumnIndexOrThrow(KEY_AMOUNT));
    Long parent_id = DbUtils.getLongOrNull(c, KEY_PARENTID);
    Long catId = DbUtils.getLongOrNull(c, KEY_CATID);
    if (transfer_peer != null) {
      t = parent_id != null ? new SplitPartTransfer(account_id, amount, parent_id) : new Transfer(account_id, amount);
    } else {
      if (catId == DatabaseConstants.SPLIT_CATID) {
        t = new SplitTransaction(account_id, amount);
      } else {
        t = parent_id != null ? new SplitPartCategory(account_id, amount, parent_id) : new Transaction(account_id, amount);
      }
    }
    try {
      t.crStatus = CrStatus.valueOf(c.getString(c.getColumnIndexOrThrow(KEY_CR_STATUS)));
    } catch (IllegalArgumentException ex) {
      t.crStatus = CrStatus.UNRECONCILED;
    }
    t.methodId = DbUtils.getLongOrNull(c, KEY_METHODID);
    t.methodLabel = DbUtils.getString(c, KEY_METHOD_LABEL);
    t.setCatId(catId);
    t.payeeId = DbUtils.getLongOrNull(c, KEY_PAYEEID);
    t.payee = DbUtils.getString(c, KEY_PAYEE_NAME);
    t.transfer_peer = transfer_peer;
    t.transfer_account = DbUtils.getLongOrNull(c, KEY_TRANSFER_ACCOUNT);
    t.setId(id);
    t.setDate(c.getLong(
        c.getColumnIndexOrThrow(KEY_DATE)) * 1000L);
    t.comment = DbUtils.getString(c, KEY_COMMENT);
    t.referenceNumber = DbUtils.getString(c, KEY_REFERENCE_NUMBER);
    t.label = DbUtils.getString(c, KEY_LABEL);
    int pictureUriColumnIndex = c.getColumnIndexOrThrow(KEY_PICTURE_URI);
    t.pictureUri = c.isNull(pictureUriColumnIndex) ?
        null :
        Uri.parse(c.getString(pictureUriColumnIndex));
    t.status = c.getInt(c.getColumnIndexOrThrow(KEY_STATUS));
    c.close();
    return t;
  }

  public static Transaction getInstanceFromTemplate(long id) {
    Template te = Template.getInstanceFromDb(id);
    return te == null ? null : getInstanceFromTemplate(te);
  }

  public static Transaction getInstanceFromTemplate(Template te) {
    Transaction tr;
    if (te.isTransfer) {
      tr = new Transfer(te.accountId, te.amount);
      tr.transfer_account = te.transfer_account;
    } else {
      tr = new Transaction(te.accountId, te.amount);
      tr.methodId = te.methodId;
      tr.methodLabel = te.methodLabel;
      tr.setCatId(te.getCatId());
    }
    tr.comment = te.comment;
    tr.payee = te.payee;
    tr.label = te.label;
    tr.originTemplateId = te.getId();
    cr().update(
        TransactionProvider.TEMPLATES_URI
            .buildUpon()
            .appendPath(String.valueOf(te.getId()))
            .appendPath(TransactionProvider.URI_SEGMENT_INCREASE_USAGE)
            .build(),
        null, null, null);
    return tr;
  }

  /**
   * factory method for creating an object of the correct type and linked to a given account
   *
   * @param accountId the account the transaction belongs to if account no longer exists {@link Account#getInstanceFromDb(long) is called with 0}
   * @return instance of {@link Transaction} or {@link Transfer} or {@link SplitTransaction} with date initialized to current date
   * if parentId == 0L, otherwise {@link SplitPartCategory} or {@link SplitPartTransfer}
   */
  public static Transaction getNewInstance(long accountId) {
    Account account = Account.getInstanceFromDb(accountId);
    if (account == null) {
      account = Account.getInstanceFromDb(0L);
    }
    if (account == null) {
      return null;
    }
    return new Transaction(account, 0L);
  }

  public static void delete(long id, boolean markAsVoid) {
    Uri.Builder builder = ContentUris.appendId(CONTENT_URI.buildUpon(), id);
    if (markAsVoid) {
      builder.appendQueryParameter(TransactionProvider.QUERY_PARAMETER_MARK_VOID, "1");
    }
    cr().delete(builder.build(), null, null);
  }

  public static void undelete(long id) {
    Uri uri = ContentUris.appendId(CONTENT_URI.buildUpon(), id)
        .appendPath(TransactionProvider.URI_SEGMENT_UNDELETE).build();
    cr().update(uri, null, null, null);
  }

  //needed for Template subclass
  protected Transaction() {
    setDate(new Date());
    this.crStatus = CrStatus.UNRECONCILED;
  }

  /**
   * new empty transaction
   */
  public Transaction(long accountId, Long amount) {
    this(Account.getInstanceFromDb(accountId), amount);
  }

  public Transaction(long accountId, Money amount) {
    this();
    this.accountId = accountId;
    this.amount = amount;
  }

  public Transaction(Account account, long amount) {
    this();
    this.accountId = account.getId();
    this.amount = new Money(account.currency, amount);
  }

  public Long getCatId() {
    return catId;
  }

  public void setCatId(Long catId) {
    this.catId = catId;
  }

  public void setDate(Date date) {
    if (date == null) {
      throw new RuntimeException("Transaction date cannot be set to null");
    }
    this.date = date;
  }

  private void setDate(Long unixEpoch) {
    this.date = new Date(unixEpoch);
  }

  public Date getDate() {
    return date;
  }

  /**
   * updates the payee string to a new value
   * it will me mapped to an existing or new row in payee table during save
   * @param payee
   */
  public void setPayee(String payee) {
    if (!this.payee.equals(payee)) {
      this.payeeId = null;
    }
    this.payee = payee;
  }

  /**
   * updates the payee to a row that already exists in the DB
   * @param payee
   * @param payeeId
   */
  public void updatePayeeWithId(String payee, Long payeeId) {
    this.payee = payee;
    this.payeeId = payeeId;
  }

  /**
   * Saves the transaction, creating it new if necessary
   * as a side effect calls {@link Payee#require(String)}
   *
   * @return the URI of the transaction. Upon creation it is returned from the content provider
   */
  public Uri save() {
    boolean needIncreaseUsage = false;
    if (catId != null && catId != DatabaseConstants.SPLIT_CATID) {
      if (getId() == 0) {
        needIncreaseUsage = true;
      } else {
        Cursor c = cr().query(
            CONTENT_URI.buildUpon().appendPath(String.valueOf(getId())).build(),
            new String[]{KEY_CATID},
            null, null, null);
        if (c != null) {
          if (c.moveToFirst()) {
            if (c.getLong(0) != catId) {
              //category has been changed
              needIncreaseUsage = true;
            }
          }
          c.close();
        }
      }
    }
    Uri uri;
    ContentValues initialValues = buildInitialValues();
    if (getId() == 0) {
      uri = cr().insert(CONTENT_URI, initialValues);
      if (uri == null) {
        return null;
      }
      if (pictureUri != null) {
        ContribFeature.ATTACH_PICTURE.recordUsage();
      }
      setId(ContentUris.parseId(uri));
      if (parentId == null)
        cr().update(
            TransactionProvider.ACCOUNTS_URI
                .buildUpon()
                .appendPath(String.valueOf(accountId))
                .appendPath(TransactionProvider.URI_SEGMENT_INCREASE_USAGE)
                .build(),
            null, null, null);
      if (originPlanInstanceId != null) {
        ContentValues values = new ContentValues();
        values.put(KEY_TEMPLATEID, originTemplateId);
        values.put(KEY_INSTANCEID, originPlanInstanceId);
        values.put(KEY_TRANSACTIONID, getId());
        cr().insert(TransactionProvider.PLAN_INSTANCE_STATUS_URI, values);
      }
    } else {
      uri = CONTENT_URI.buildUpon().appendPath(String.valueOf(getId())).build();
      cr().update(uri, initialValues, null, null);
    }
    if (needIncreaseUsage) {
      cr().update(
          TransactionProvider.CATEGORIES_URI
              .buildUpon()
              .appendPath(String.valueOf(catId))
              .appendPath(TransactionProvider.URI_SEGMENT_INCREASE_USAGE)
              .build(),
          null, null, null);
    }
    return uri;
  }

  ContentValues buildInitialValues() {
    ContentValues initialValues = new ContentValues();

    Long payeeStore;
    if (payeeId != null) {
      payeeStore = payeeId;
    } else {
      payeeStore =
          (payee != null && !payee.equals("")) ?
              Payee.require(payee) :
              null;
    }
    initialValues.put(KEY_COMMENT, comment);
    initialValues.put(KEY_REFERENCE_NUMBER, referenceNumber);
    //store in UTC
    initialValues.put(KEY_DATE, date.getTime() / 1000);

    initialValues.put(KEY_AMOUNT, amount.getAmountMinor());
    initialValues.put(KEY_CATID, getCatId());
    initialValues.put(KEY_PAYEEID, payeeStore);
    initialValues.put(KEY_METHODID, methodId);
    initialValues.put(KEY_CR_STATUS, crStatus.name());
    initialValues.put(KEY_ACCOUNTID, accountId);

    savePicture(initialValues);
    if (getId() == 0) {
      initialValues.put(KEY_PARENTID, parentId);
      initialValues.put(KEY_STATUS, status);
    }
    return initialValues;
  }

  private void throwExternalNotAvailable() {
    throw new ExternalStorageNotAvailableException();
  }

  protected void savePicture(ContentValues initialValues) {
    if (pictureUri != null) {
      String pictureUriBase = Utils.getPictureUriBase(false);
      if (pictureUriBase == null) {
        throwExternalNotAvailable();
      }
      if (pictureUri.toString().startsWith(pictureUriBase)) {
        Log.d("DEBUG", "got Uri in our home space, nothing todo");
      } else {
        pictureUriBase = Utils.getPictureUriBase(true);
        if (pictureUriBase == null) {
          throwExternalNotAvailable();
        }
        boolean isInTempFolder = pictureUri.toString().startsWith(pictureUriBase);
        Uri homeUri = Utils.getOutputMediaUri(false);
        if (homeUri == null) {
          throwExternalNotAvailable();
        }
        try {
          if (isInTempFolder && homeUri.getScheme().equals("file")) {
            if (new File(pictureUri.getPath()).renameTo(new File(homeUri.getPath()))) {
              setPictureUri(homeUri);
            } else {
              //fallback
              copyPictureHelper(isInTempFolder, homeUri);
            }
          } else {
            copyPictureHelper(isInTempFolder, homeUri);
          }
        } catch (IOException e) {
          throw new UnknownPictureSaveException(pictureUri,homeUri,e);
        }
      }
      initialValues.put(KEY_PICTURE_URI, pictureUri.toString());
    } else {
      initialValues.putNull(KEY_PICTURE_URI);
    }
  }

  private void copyPictureHelper(boolean delete, Uri homeUri) throws IOException {
    Utils.copy(pictureUri, homeUri);
    if (delete) {
      new File(pictureUri.getPath()).delete();
    }
    setPictureUri(homeUri);
  }

  public Uri saveAsNew() {
    setId(0L);
    setDate(new Date());
    Uri result = save();
    setId(ContentUris.parseId(result));
    return result;
  }

  /**
   * @param whichTransactionId
   * @param whereAccountId
   */
  public static void move(long whichTransactionId, long whereAccountId) {
    ContentValues args = new ContentValues();
    args.put(KEY_ACCOUNTID, whereAccountId);
    cr().update(Uri.parse(
            CONTENT_URI + "/" + whichTransactionId + "/" + TransactionProvider.URI_SEGMENT_MOVE + "/" + whereAccountId),
        null, null, null);
  }

  public static int count(Uri uri, String selection, String[] selectionArgs) {
    Cursor cursor = cr().query(uri, new String[]{"count(*)"},
        selection, selectionArgs, null);
    if (cursor.getCount() == 0) {
      cursor.close();
      return 0;
    } else {
      cursor.moveToFirst();
      int result = cursor.getInt(0);
      cursor.close();
      return result;
    }
  }

  public static int countAll(Uri uri) {
    return count(uri, null, null);
  }

  public static int countPerCategory(Uri uri, long catId) {
    return count(uri, KEY_CATID + " = ?", new String[]{String.valueOf(catId)});
  }

  public static int countPerMethod(Uri uri, long methodId) {
    return count(uri, KEY_METHODID + " = ?", new String[]{String.valueOf(methodId)});
  }

  public static int countPerAccount(Uri uri, long accountId) {
    return count(uri, KEY_ACCOUNTID + " = ?", new String[]{String.valueOf(accountId)});
  }

  public static int countPerCategory(long catId) {
    return countPerCategory(CONTENT_URI, catId);
  }

  public static int countPerMethod(long methodId) {
    return countPerMethod(CONTENT_URI, methodId);
  }

  public static int countPerAccount(long accountId) {
    return countPerAccount(CONTENT_URI, accountId);
  }

  public static int countAll() {
    return countAll(CONTENT_URI);
  }

  /**
   * @return the number of transactions that have been created since creation of the db based on sqllite sequence
   */
  public static Long getSequenceCount() {
    Cursor mCursor = cr().query(TransactionProvider.SQLITE_SEQUENCE_TRANSACTIONS_URI,
        null, null, null, null);
    if (mCursor == null) {
      return 0L;
    }
    if (mCursor.getCount() == 0) {
      mCursor.close();
      return 0L;
    }
    mCursor.moveToFirst();
    Long result = mCursor.getLong(0);
    mCursor.close();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Transaction other = (Transaction) obj;
    if (accountId == null) {
      if (other.accountId != null)
        return false;
    } else if (!accountId.equals(other.accountId))
      return false;
    if (amount == null) {
      if (other.amount != null)
        return false;
    } else if (!amount.equals(other.amount))
      return false;
    if (getCatId() == null) {
      if (other.getCatId() != null)
        return false;
    } else if (!getCatId().equals(other.getCatId()))
      return false;
    if (comment == null) {
      if (other.comment != null)
        return false;
    } else if (!comment.equals(other.comment))
      return false;
    if (date == null) {
      if (other.date != null)
        return false;
    } else if (Math.abs(date.getTime() - other.date.getTime()) > 30000) //30 seconds tolerance
      return false;
    if (getId() == null) {
      if (other.getId() != null)
        return false;
    } else if (!getId().equals(other.getId()))
      return false;
    //label is constructed on hoc by database as a consquence of transfer_account and category
    //and is not yet set when transaction is not saved, hence we do not consider it relevant
    //here for equality
/*    if (label == null) {
      if (other.label != null)
        return false;
    } else if (!label.equals(other.label))
      return false;*/
    if (methodId == null) {
      if (other.methodId != null)
        return false;
    } else if (!methodId.equals(other.methodId))
      return false;
    if (payee == null) {
      if (other.payee != null)
        return false;
    } else if (!payee.equals(other.payee))
      return false;
    if (transfer_account == null) {
      if (other.transfer_account != null)
        return false;
    } else if (!transfer_account.equals(other.transfer_account))
      return false;
    if (transfer_peer == null) {
      if (other.transfer_peer != null)
        return false;
    } else if (!transfer_peer.equals(other.transfer_peer))
      return false;
    if (pictureUri == null) {
      if (other.pictureUri != null)
        return false;
    } else if (!pictureUri.equals(other.pictureUri))
      return false;
    return true;
  }

  public Uri getPictureUri() {
    return pictureUri;
  }

  public void setPictureUri(Uri pictureUriIn) {
    this.pictureUri = pictureUriIn;
  }

  public static Intent getViewIntent(Uri pictureUri) {
    Intent intent = new Intent(Intent.ACTION_VIEW, pictureUri);
    intent.putExtra(Intent.EXTRA_STREAM, pictureUri);
    intent.setDataAndType(pictureUri, "image/jpeg");
    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    return intent;
  }

  public static class ExternalStorageNotAvailableException extends IllegalStateException {
  }

  public static class UnknownPictureSaveException extends IllegalStateException {
    public Uri pictureUri,homeUri;
    public UnknownPictureSaveException(Uri pictureUri, Uri homeUri, IOException e) {
      super(e);
      this.pictureUri = pictureUri;
      this.homeUri = homeUri;
    }
  }
}
