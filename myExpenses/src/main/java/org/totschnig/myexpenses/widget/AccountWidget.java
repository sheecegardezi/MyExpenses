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
// based on Financisto

package org.totschnig.myexpenses.widget;

import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_CURRENT_BALANCE;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.ExpenseEdit;
import org.totschnig.myexpenses.activity.MyExpenses;
import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.model.Money;
import org.totschnig.myexpenses.provider.DatabaseConstants;
import org.totschnig.myexpenses.provider.TransactionProvider;
import org.totschnig.myexpenses.util.Utils;


public class AccountWidget extends AbstractWidget<Account> {
  
  @Override
  Uri getContentUri() {
    return Uri
        .parse("content://org.totschnig.myexpenses/accountwidget");
  }
  
  @Override
  String getPrefName() {
    // TODO Auto-generated method stub
    return "org.totschnig.myexpenses.activity.AccountWidget";
  }
  @Override
  MyApplication.PrefKey getProtectionKey() {
    return MyApplication.PrefKey.PROTECTION_ENABLE_ACCOUNT_WIDGET;
  }

  public static final Uri[] OBSERVED_URIS = new Uri[] {
        TransactionProvider.ACCOUNTS_URI,
        TransactionProvider.TRANSACTIONS_URI
  };
  private Money mCurrentBalance;

  @Override
  RemoteViews updateWidgetFrom(Context context,
      int widgetId, int layoutId, Account a) {
    Log.d("MyExpensesWidget", "updating account " + a.getId());
    RemoteViews updateViews = new RemoteViews(context.getPackageName(),
        layoutId);
    updateViews.setTextViewText(R.id.line1, a.label);
    updateViews.setTextViewText(R.id.note,
        Utils.formatCurrency(mCurrentBalance));
//    updateViews.setTextColor(R.id.note, context.getResources().getColor(
//        balance.getAmountMinor() < 0 ? R.color.colorExpenseDark : R.color.colorIncomeDark));
    setBackgroundColorSave(updateViews,R.id.divider3,a.color);
    addScrollOnClick(context, updateViews, widgetId);
    addTapOnClick(context, updateViews, widgetId, a.getId());
    addButtonsClick(context, updateViews, widgetId, a);
    saveForWidget(context, widgetId, a.getId());
    int multipleAccountsVisible = Account.count(null, null) < 2 ? View.GONE
        : View.VISIBLE;
    int transferEnabledVisible = Account.getTransferEnabledGlobal() ? View.VISIBLE
        : View.GONE;
    updateViews.setViewVisibility(R.id.navigation, multipleAccountsVisible);
    updateViews.setViewVisibility(R.id.divider1, transferEnabledVisible);
    updateViews.setViewVisibility(R.id.command2, transferEnabledVisible);
    return updateViews;
  }

  private void addTapOnClick(Context context, RemoteViews updateViews,
                             int widgetId, long accountId) {
    Intent intent = new Intent(context, MyExpenses.class);
    intent.putExtra(DatabaseConstants.KEY_ROWID, accountId);
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent pendingIntent = PendingIntent.getActivity(context, widgetId, intent,
        PendingIntent.FLAG_UPDATE_CURRENT);
    updateViews.setOnClickPendingIntent(R.id.object_info, pendingIntent);
  }

  private Intent buildButtonIntent(Context context,Account account) {
    Intent intent = new Intent(context, ExpenseEdit.class);
    if (account.getId()<0) {
      intent.putExtra(DatabaseConstants.KEY_CURRENCY,account.currency.getCurrencyCode());
    } else {
      intent.putExtra(DatabaseConstants.KEY_ACCOUNTID, account.getId());
    }
    intent.putExtra(AbstractWidget.EXTRA_START_FROM_WIDGET, true);
    intent.putExtra(AbstractWidget.EXTRA_START_FROM_WIDGET_DATA_ENTRY, true);
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    return  intent;
  }

  private void addButtonsClick(Context context, RemoteViews updateViews,
      int widgetId, Account account) {
    Intent intent = buildButtonIntent(context,account);
    PendingIntent pendingIntent = PendingIntent.getActivity(
        context,
        2*widgetId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT);
    updateViews.setOnClickPendingIntent(R.id.command1, pendingIntent);
    updateViews.setImageViewResource(R.id.command1, android.R.drawable.ic_menu_add);
    intent = buildButtonIntent(context,account);
    intent.putExtra(MyApplication.KEY_OPERATION_TYPE, MyExpenses.TYPE_TRANSFER);
    pendingIntent = PendingIntent.getActivity(
        context,
        2*widgetId+1,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT);
    updateViews.setOnClickPendingIntent(R.id.command2, pendingIntent);
    updateViews.setImageViewResource(R.id.command2, R.drawable.ic_menu_forward);
  }

  @Override
  Account getObject(Cursor c) {
    Account a = Account.fromCacheOrFromCursor(c);
    mCurrentBalance =new Money(a.currency,
        c.getLong(c.getColumnIndexOrThrow(KEY_CURRENT_BALANCE)));
    return a;
  }

  @Override
  Cursor getCursor(Context c) {
    Uri.Builder builder = TransactionProvider.ACCOUNTS_URI.buildUpon();
    builder.appendQueryParameter(TransactionProvider.QUERY_PARAMETER_MERGE_CURRENCY_AGGREGATES, "1");
    return c.getContentResolver().query(
        builder.build(), null, null, null, null);
  }
  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d("AccountWidget", "onReceive intent "+intent);
    super.onReceive(context, intent);
  }
}
