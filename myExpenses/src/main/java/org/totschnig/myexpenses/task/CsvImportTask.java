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
//based on Financisto

package org.totschnig.myexpenses.task;

import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;

import com.google.common.primitives.Ints;

import org.apache.commons.csv.CSVRecord;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.export.CategoryInfo;
import org.totschnig.myexpenses.export.qif.QifDateFormat;
import org.totschnig.myexpenses.export.qif.QifUtils;
import org.totschnig.myexpenses.fragment.CsvImportDataFragment;
import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.model.Money;
import org.totschnig.myexpenses.model.Payee;
import org.totschnig.myexpenses.model.PaymentMethod;
import org.totschnig.myexpenses.model.SplitPartCategory;
import org.totschnig.myexpenses.model.SplitPartTransfer;
import org.totschnig.myexpenses.model.SplitTransaction;
import org.totschnig.myexpenses.model.Transaction;
import org.totschnig.myexpenses.model.Transfer;
import org.totschnig.myexpenses.provider.DatabaseConstants;
import org.totschnig.myexpenses.util.Result;
import org.totschnig.myexpenses.util.SparseBooleanArrayParcelable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;

public class CsvImportTask extends AsyncTask<Void, Integer, Result> {
  private final TaskExecutionFragment taskExecutionFragment;
  private QifDateFormat dateFormat;
  ArrayList<CSVRecord> data;
  int[] column2FieldMap;
  SparseBooleanArrayParcelable discardedRows;
  private long accountId;
  private Currency mCurrency;
  private Account.Type mAccountType;
  private final Map<String, Long> payeeToId = new HashMap<>();
  private final Map<String, Long> categoryToId = new HashMap<>();

  public CsvImportTask(TaskExecutionFragment taskExecutionFragment, Bundle b) {
    this.taskExecutionFragment = taskExecutionFragment;
    this.dateFormat = (QifDateFormat) b.getSerializable(TaskExecutionFragment.KEY_DATE_FORMAT);
    this.data = (ArrayList<CSVRecord>) b.getSerializable(CsvImportDataFragment.KEY_DATASET);
    this.column2FieldMap = (int[]) b.getSerializable(CsvImportDataFragment.KEY_FIELD_TO_COLUMN);
    this.discardedRows = b.getParcelable(CsvImportDataFragment.KEY_DISCARDED_ROWS);
    this.accountId = b.getLong(DatabaseConstants.KEY_ACCOUNTID);
    this.mCurrency = Currency.getInstance(b.getString(DatabaseConstants.KEY_CURRENCY));
    this.mAccountType = (Account.Type) b.getSerializable(DatabaseConstants.KEY_TYPE);
  }

  @Override
  protected void onPostExecute(Result result) {
    if (this.taskExecutionFragment.mCallbacks != null) {
      this.taskExecutionFragment.mCallbacks.onPostExecute(
          TaskExecutionFragment.TASK_CSV_IMPORT, result);
    }
  }

  @Override
  protected void onProgressUpdate(Integer... values) {
    if (this.taskExecutionFragment.mCallbacks != null) {
      this.taskExecutionFragment.mCallbacks.onProgressUpdate(values[0]);
    }
  }

  @Override
  protected Result doInBackground(Void... params) {
    int totalImported = 0, totalDiscarded = 0, totalFailed = 0;
    Account a;
    if (accountId==0) {
      a = new Account();
      a.currency = mCurrency;
      a.label = MyApplication.getInstance().getString(R.string.pref_import_title,"CSV");
      a.type = mAccountType;
      a.save();
      accountId = a.getId();
    } else {
      a = Account.getInstanceFromDb(accountId);
    }
    int columnIndexAmount = findColumnIndex(R.string.amount);
    int columnIndexExpense = findColumnIndex(R.string.expense);
    int columnIndexIncome = findColumnIndex(R.string.income);
    int columnIndexDate = findColumnIndex(R.string.date);
    int columnIndexPayee = findColumnIndex(R.string.payer_or_payee);
    int columnIndexNotes = findColumnIndex(R.string.comment);
    int columnIndexCategory = findColumnIndex(R.string.category);
    int columnIndexSubcategory = findColumnIndex(R.string.subcategory);
    int columnIndexMethod = findColumnIndex(R.string.method);
    int columnIndexStatus = findColumnIndex(R.string.status);
    int columnIndexNumber = findColumnIndex(R.string.reference_number);
    int columnIndexSplit = findColumnIndex(R.string.split_transaction);

    boolean isSplitParent = false, isSplitPart = false;
    Transaction t = null;
    Long splitParent = null;
    for (int i = 0; i < data.size(); i++) {
      long transferAccountId = -1;
      if (discardedRows.get(i,false)) {
        totalDiscarded++;
      } else {
        CSVRecord record = data.get(i);
        BigDecimal amount;
        String categoryInfo = null;
        if (columnIndexSplit!=-1) {
          if (isSplitParent) {
            isSplitPart = saveGetFromRecord(record,columnIndexSplit).equals(SplitTransaction.CSV_PART_INDICATOR);
            isSplitParent= false;
          } else {
            isSplitParent = saveGetFromRecord(record,columnIndexSplit).equals(SplitTransaction.CSV_INDICATOR);
          }
        }
        if (columnIndexAmount!=-1) {
          amount = QifUtils.parseMoney(saveGetFromRecord(record,columnIndexAmount));
        } else {
          BigDecimal income = columnIndexIncome!=-1 ?
              QifUtils.parseMoney(saveGetFromRecord(record,columnIndexIncome)).abs() :
              new BigDecimal(0);
          BigDecimal expense = columnIndexExpense!=-1 ?
              QifUtils.parseMoney(saveGetFromRecord(record,columnIndexExpense)).abs() :
              new BigDecimal(0);
          amount = income.subtract(expense);
        }
        Money m = new Money(a.currency,amount);

        if (!isSplitParent && columnIndexCategory!=-1) {
          String category = saveGetFromRecord(record, columnIndexCategory);
          String subCategory = columnIndexSubcategory !=-1 ?
              saveGetFromRecord(record, columnIndexSubcategory)
              : "";
          if (category.equals(MyApplication.getInstance().getString(R.string.transfer)) &&
              !subCategory.equals("") &&
              QifUtils.isTransferCategory(subCategory)) {
            transferAccountId = Account.findAny(subCategory.substring(1, subCategory.length() - 1));
          } else if (QifUtils.isTransferCategory(category)) {
            transferAccountId = Account.findAny(category.substring(1, category.length() - 1));
          }
          if (transferAccountId==-1) {
            categoryInfo = category;
            if (!subCategory.equals("")) {
              categoryInfo += ":" + subCategory;
            }
          }
        }

        if (isSplitPart) {
          if (transferAccountId!=-1) {
            t = SplitPartTransfer.getNewInstance(accountId, m.getAmountMinor(), splitParent);
            t.transfer_account = transferAccountId;
          } else {
            t = new SplitPartCategory(accountId, m.getAmountMinor(), splitParent);
          }
        } else {
          if (isSplitParent) {
            t = new SplitTransaction(accountId, m);
          } else {
            if (transferAccountId!=-1) {
              t = new Transfer(accountId, m);
              t.transfer_account = transferAccountId;
            } else {
              t = new Transaction(accountId, m);
            }
          }
        }
        if (!TextUtils.isEmpty(categoryInfo)) {
          new CategoryInfo(categoryInfo).insert(categoryToId);
          t.setCatId(categoryToId.get(categoryInfo));
        }
        if (columnIndexDate!=-1) {
          t.setDate(QifUtils.parseDate(saveGetFromRecord(record,columnIndexDate),dateFormat));
        }

        if (columnIndexPayee!=-1) {
          String payee = saveGetFromRecord(record,columnIndexPayee);
          Long id = payeeToId.get(payee);
          if (id == null) {
            id = Payee.find(payee);
            if (id == -1) {
              id = Payee.maybeWrite(payee);
            }
            if (id != -1) {
              payeeToId.put(payee, id);
              t.payeeId = id;
            }
          }
        }

        if (columnIndexNotes!=-1) {
          t.comment = saveGetFromRecord(record,columnIndexNotes);
        }

        if (columnIndexMethod!=-1) {
          String method = saveGetFromRecord(record, columnIndexMethod);
          for (PaymentMethod.PreDefined preDefined: PaymentMethod.PreDefined.values()) {
            if (preDefined.getLocalizedLabel().equals(method)) {
              method = preDefined.name();
              break;
            }
          }
          long methodId = PaymentMethod.find(method);
          if (methodId!=-1) {
            t.methodId = methodId;
          }
        }

        if (columnIndexStatus!=-1) {
          t.crStatus = Transaction.CrStatus.fromQifName(saveGetFromRecord(record, columnIndexStatus));
        }

        if (columnIndexNumber!=-1) {
          t.referenceNumber = saveGetFromRecord(record, columnIndexNumber);
        }
        if (t.save()!=null) {
          if (isSplitParent) {
            splitParent = t.getId();
          }
          if (!isSplitPart) {
            totalImported++;
          }
        } else {
          totalFailed++;
        }
        if (totalImported%10==0) {
          publishProgress(totalImported);
        }
      }
    }
    return new Result(true,
        0,
        Integer.valueOf(totalImported),
        Integer.valueOf(totalFailed),
        Integer.valueOf(totalDiscarded),
        a.label);
  }
  private int findColumnIndex(int field) {
    return Ints.indexOf(column2FieldMap,field);
  }
  private String saveGetFromRecord(CSVRecord record, int index) {
    return record.size()>index ? record.get(index) : "";
  }
}