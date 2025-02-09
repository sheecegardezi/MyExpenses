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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Currency;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.dialog.DialogUtils;
import org.totschnig.myexpenses.export.qif.QifAccount;
import org.totschnig.myexpenses.export.qif.QifBufferedReader;
import org.totschnig.myexpenses.export.qif.QifCategory;
import org.totschnig.myexpenses.export.qif.QifDateFormat;
import org.totschnig.myexpenses.export.qif.QifParser;
import org.totschnig.myexpenses.export.qif.QifTransaction;
import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.model.Category;
import org.totschnig.myexpenses.model.ContribFeature;
import org.totschnig.myexpenses.model.Payee;
import org.totschnig.myexpenses.model.SplitTransaction;
import org.totschnig.myexpenses.model.Transaction;
import org.totschnig.myexpenses.provider.DatabaseConstants;
import org.totschnig.myexpenses.util.FileUtils;
import org.totschnig.myexpenses.util.Utils;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;

public class QifImportTask extends AsyncTask<Void, String, Void> {
  private final TaskExecutionFragment taskExecutionFragment;
  private QifDateFormat dateFormat;
  private String encoding;
  private long accountId;
  private int totalCategories=0;
  private final Map<String, Long> payeeToId = new HashMap<>();
  private final Map<String, Long> categoryToId = new HashMap<>();
  private final Map<String, QifAccount> accountTitleToAccount = new HashMap<String, QifAccount>();
  Uri fileUri;
  /**
   * should we handle parties/categories?
   */
  boolean withPartiesP, withCategoriesP, withTransactionsP;
  
  private Currency mCurrency;

  public QifImportTask(TaskExecutionFragment taskExecutionFragment,Bundle b) {
    this.taskExecutionFragment = taskExecutionFragment;
    this.dateFormat = (QifDateFormat) b.getSerializable(TaskExecutionFragment.KEY_DATE_FORMAT);
    this.accountId = b.getLong(DatabaseConstants.KEY_ACCOUNTID);
    this.fileUri = b.getParcelable(TaskExecutionFragment.KEY_FILE_PATH);
    this.withPartiesP = b.getBoolean(TaskExecutionFragment.KEY_WITH_PARTIES);
    this.withCategoriesP = b.getBoolean(TaskExecutionFragment.KEY_WITH_CATEGORIES);
    this.withTransactionsP = b.getBoolean(TaskExecutionFragment.KEY_WITH_TRANSACTIONS);
    this.mCurrency = Currency.getInstance(b.getString(DatabaseConstants.KEY_CURRENCY));
    this.encoding = b.getString(TaskExecutionFragment.KEY_ENCODING);
  }

  @Override
  protected void onPostExecute(Void result) {
    if (this.taskExecutionFragment.mCallbacks != null) {
      this.taskExecutionFragment.mCallbacks.onPostExecute(
          TaskExecutionFragment.TASK_QIF_IMPORT, null);
    }
  }

  @Override
  protected void onProgressUpdate(String... values) {
    if (this.taskExecutionFragment.mCallbacks != null) {
      for (String progress: values) {
        this.taskExecutionFragment.mCallbacks.onProgressUpdate(progress);
      }
    }
  }

  @Override
  protected Void doInBackground(Void... params) {
    long t0 = System.currentTimeMillis();
    QifBufferedReader r;
    QifParser parser;
    try {
      InputStream inputStream = MyApplication.getInstance().getContentResolver().openInputStream(fileUri);
      r = new QifBufferedReader(
          new BufferedReader(
              new InputStreamReader(
                  inputStream,
                  encoding)));
    } catch (FileNotFoundException e) {
      publishProgress(MyApplication.getInstance()
          .getString(R.string.parse_error_file_not_found,fileUri));
      return null;
    } catch (Exception e) {
      publishProgress(MyApplication.getInstance()
          .getString(R.string.parse_error_other_exception,e.getMessage()));
      return null;
    }
    parser = new QifParser(r, dateFormat);
    try {
      parser.parse();
      long t1 = System.currentTimeMillis();
      Log.i(MyApplication.TAG, "QIF Import: Parsing done in "
          + TimeUnit.MILLISECONDS.toSeconds(t1 - t0) + "s");
      publishProgress(MyApplication.getInstance()
          .getString(
              R.string.qif_parse_result,
              String.valueOf(parser.accounts.size()),
              String.valueOf(parser.categories.size()),
              String.valueOf(parser.payees.size())));
      doImport(parser);
      return(null);
    } catch (IOException e) {
      publishProgress(MyApplication.getInstance()
          .getString(R.string.parse_error_other_exception,e.getMessage()));
      return null;
    } finally {
      try {
        r.close();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

/*  private String detectEncoding(InputStream inputStream) throws IOException {
    byte[] buf = new byte[4096];

    // (1)
    UniversalDetector detector = new UniversalDetector(null);

    // (2)
    int nread;
    while ((nread = inputStream.read(buf)) > 0 && !detector.isDone()) {
      detector.handleData(buf, 0, nread);
    }
    // (3)
    detector.dataEnd();

    // (4)
    String encoding = detector.getDetectedCharset();
    if (encoding != null) {
      System.out.println("Detected encoding = " + encoding);
    } else {
      System.out.println("No encoding detected.");
    }

    // (5)
    detector.reset();
    return encoding;
  }*/

  private void doImport(QifParser parser) {
    if (withPartiesP) {
      int totalParties = insertPayees(parser.payees);
      publishProgress(totalParties == 0 ? 
          MyApplication.getInstance().getString(R.string.import_parties_none):
          MyApplication.getInstance().getString(R.string.import_parties_success,totalParties));
    }
    /*
     * insertProjects(parser.classes); long t2 = System.currentTimeMillis();
     * Log.i(MyApplication.TAG, "QIF Import: Inserting projects done in "+
     * TimeUnit.MILLISECONDS.toSeconds(t2-t1)+"s");
     */
    if (withCategoriesP) {
      insertCategories(parser.categories);
      publishProgress(totalCategories == 0 ? 
        MyApplication.getInstance().getString(R.string.import_categories_none):
        MyApplication.getInstance().getString(R.string.import_categories_success,totalCategories));
    }
    if (withTransactionsP) {
      if (accountId == 0) {
        int importedAccounts = insertAccounts(parser.accounts);
        publishProgress(importedAccounts == 0 ?
          MyApplication.getInstance().getString(R.string.import_accounts_none):
          MyApplication.getInstance().getString(R.string.import_accounts_success,importedAccounts));
      } else {
        if (parser.accounts.size() > 1) {
          publishProgress(
              MyApplication.getInstance()
                  .getString(R.string.qif_parse_failure_found_multiple_accounts)
                  + " "
                  + MyApplication.getInstance()
                      .getString(R.string.qif_parse_failure_found_multiple_accounts_cannot_merge));
          return;
        }
        if (parser.accounts.size() == 0) {
          return;
        }
        Account dbAccount = Account.getInstanceFromDb(accountId);
        parser.accounts.get(0).dbAccount = dbAccount;
        if (dbAccount==null) {
          Utils.reportToAcra(new Exception(
              "Exception during QIF import. Did not get instance from DB for id " +accountId));
        }
      }
      insertTransactions(parser.accounts);
    }
  }

  private int insertPayees(Set<String> payees) {
    int count = 0;
    for (String payee : payees) {
      Long id = payeeToId.get(payee);
      if (id == null) {
        id = Payee.find(payee);
        if (id == -1) {
          id = Payee.maybeWrite(payee);
          if (id != -1)
            count++;
        }
        if (id != -1) {
          payeeToId.put(payee, id);
        }
      }
    }
    return count;
  }

  private void insertCategories(Set<QifCategory> categories) {
    for (QifCategory category : categories) {
      totalCategories += category.insert(categoryToId);
    }
  }

  private int insertAccounts(List<QifAccount> accounts) {
    int nrOfAccounts = Account.count(null, null);

    int importCount = 0;
    for (QifAccount account : accounts) {
      if (!ContribFeature.ACCOUNTS_UNLIMITED.hasAccess()
          && nrOfAccounts + importCount > 5) {
        publishProgress(
            MyApplication.getInstance()
                .getString(R.string.qif_parse_failure_found_multiple_accounts) + " " +
                MyApplication.getInstance()
                .getText(R.string.contrib_feature_accounts_unlimited_description) + " " +
                ContribFeature.ACCOUNTS_UNLIMITED.buildRemoveLimitation(
                    MyApplication.getInstance(), false));
        break;
      }
      long dbAccountId = Account.findAny(account.memo);
      if (dbAccountId!=-1) {
        Account dbAccount = Account.getInstanceFromDb(accountId);
        account.dbAccount = dbAccount;
        if (dbAccount==null) {
          Utils.reportToAcra(new Exception(
              "Exception during QIF import. Did not get instance from DB for id " +accountId));
        }
      } else {
        Account a = account.toAccount(mCurrency);
        if (TextUtils.isEmpty(a.label)) {
          String displayName = DialogUtils.getDisplayName(fileUri);
          if (FileUtils.getExtension(displayName).equalsIgnoreCase(".qif")) {
            displayName = displayName.substring(0,displayName.lastIndexOf('.'));
          }
          displayName = displayName.replace('-',' ').replace('_',' ');
          a.label = displayName;
        }
        if (a.save() != null)
          importCount++;
        account.dbAccount = a;
      }
      accountTitleToAccount.put(account.memo, account);
    }
    return importCount;
  }

  private void insertTransactions(List<QifAccount> accounts) {
    long t0 = System.currentTimeMillis();
    reduceTransfers(accounts);
    long t1 = System.currentTimeMillis();
    Log.i(MyApplication.TAG, "QIF Import: Reducing transfers done in "
        + TimeUnit.MILLISECONDS.toSeconds(t1 - t0) + "s");
    convertUnknownTransfers(accounts);
    long t2 = System.currentTimeMillis();
    Log.i(MyApplication.TAG, "QIF Import: Converting transfers done in "
        + TimeUnit.MILLISECONDS.toSeconds(t2 - t1) + "s");
    int count = accounts.size();
    for (int i = 0; i < count; i++) {
      long t3 = System.currentTimeMillis();
      QifAccount account = accounts.get(i);
      Account a = account.dbAccount;
      if (a!=null) {
        int countTransactions = insertTransactions(a, account.transactions);
        publishProgress(countTransactions == 0 ?
            MyApplication.getInstance().getString(R.string.import_transactions_none, a.label) :
            MyApplication.getInstance().getString(R.string.import_transactions_success, countTransactions, a.label));
      } else {
        publishProgress("Unable to import into QIF account "+account.memo+ ". No matching database account found");
      }
      // this might help GC
      account.transactions.clear();
      long t4 = System.currentTimeMillis();
      Log.i(MyApplication.TAG,
          "QIF Import: Inserting transactions for account " + i + "/" + count
              + " done in " + TimeUnit.MILLISECONDS.toSeconds(t4 - t3) + "s");
    }
  }

  private void reduceTransfers(List<QifAccount> accounts) {
    for (QifAccount fromAccount : accounts) {
      List<QifTransaction> transactions = fromAccount.transactions;
      reduceTransfers(fromAccount, transactions);
    }
  }

  private void reduceTransfers(QifAccount fromAccount,
      List<QifTransaction> transactions) {
    for (QifTransaction fromTransaction : transactions) {
      if (fromTransaction.isTransfer() && fromTransaction.amount.signum() == -1) {
        boolean found = false;
        if (!fromTransaction.toAccount.equals(fromAccount.memo)) {
          QifAccount toAccount = accountTitleToAccount
              .get(fromTransaction.toAccount);
          if (toAccount != null) {
            Iterator<QifTransaction> iterator = toAccount.transactions
                .iterator();
            while (iterator.hasNext()) {
              QifTransaction toTransaction = iterator.next();
              if (twoSidesOfTheSameTransfer(fromAccount, fromTransaction,
                  toAccount, toTransaction)) {
                iterator.remove();
                found = true;
                break;
              }
            }
          }
        }
        if (!found) {
          convertIntoRegularTransaction(fromTransaction);
        }
      }
      if (fromTransaction.splits != null) {
        reduceTransfers(fromAccount, fromTransaction.splits);
      }
    }
  }

  private void convertUnknownTransfers(List<QifAccount> accounts) {
    for (QifAccount fromAccount : accounts) {
      List<QifTransaction> transactions = fromAccount.transactions;
      convertUnknownTransfers(fromAccount, transactions);
    }
  }

  private void convertUnknownTransfers(QifAccount fromAccount,
      List<QifTransaction> transactions) {
    for (QifTransaction transaction : transactions) {
      if (transaction.isTransfer() && transaction.amount.signum() >= 0) {
        convertIntoRegularTransaction(transaction);
      }
      if (transaction.splits != null) {
        convertUnknownTransfers(fromAccount, transaction.splits);
      }
    }
  }

  private String prependMemo(String prefix, QifTransaction fromTransaction) {
    if (TextUtils.isEmpty(fromTransaction.memo)) {
      return prefix;
    } else {
      return prefix + " | " + fromTransaction.memo;
    }
  }

  private void convertIntoRegularTransaction(QifTransaction fromTransaction) {
    fromTransaction.memo = prependMemo(
        "Transfer: " + fromTransaction.toAccount, fromTransaction);
    fromTransaction.toAccount = null;
  }

  private boolean twoSidesOfTheSameTransfer(QifAccount fromAccount,
      QifTransaction fromTransaction, QifAccount toAccount,
      QifTransaction toTransaction) {
    return toTransaction.isTransfer()
        && toTransaction.toAccount.equals(fromAccount.memo)
        && fromTransaction.toAccount.equals(toAccount.memo)
        && fromTransaction.date.equals(toTransaction.date)
        && fromTransaction.amount == toTransaction.amount.negate();
  }

  private int insertTransactions(Account a, List<QifTransaction> transactions) {
    int count = 0;
    for (QifTransaction transaction : transactions) {
      Transaction t = transaction.toTransaction(a);
      t.payeeId = findPayee(transaction.payee);
      // t.projectId = findProject(transaction.categoryClass);
      findToAccount(transaction, t);

       if (transaction.splits != null) {
         ((SplitTransaction) t).persistForEdit();
         for (QifTransaction split : transaction.splits) {
           Transaction s = split.toTransaction(a);
           s.parentId = t.getId();
           s.status = org.totschnig.myexpenses.provider.DatabaseConstants.STATUS_UNCOMMITTED;
           findToAccount(split, s);
           findCategory(split, s);
           s.save();
         }
       } else {
         findCategory(transaction, t);
       }
       if (t.save() != null)
         count++;
    }
    return count;
  }

  private void findToAccount(QifTransaction transaction, Transaction t) {
    if (transaction.isTransfer()) {
      Account toAccount = findAccount(transaction.toAccount);
      if (toAccount != null) {
        t.transfer_account = toAccount.getId();
      }
    }
  }

  private Account findAccount(String account) {
    QifAccount a = accountTitleToAccount.get(account);
    return a != null ? a.dbAccount : null;
  }

  public Long findPayee(String payee) {
    return findIdInAMap(payee, payeeToId);
  }

  private Long findIdInAMap(String project, Map<String, Long> map) {
    if (map.containsKey(project)) {
      return map.get(project);
    }
    return null;
  }

  private void findCategory(QifTransaction transaction, Transaction t) {
    t.setCatId(categoryToId.get(transaction.category));
  }
}