/*
 * Copyright 2013, 2014 Megion Research and Development GmbH
 *
 * Licensed under the Microsoft Reference Source License (MS-RSL)
 *
 * This license governs use of the accompanying software. If you use the software, you accept this license.
 * If you do not accept the license, do not use the software.
 *
 * 1. Definitions
 * The terms "reproduce," "reproduction," and "distribution" have the same meaning here as under U.S. copyright law.
 * "You" means the licensee of the software.
 * "Your company" means the company you worked for when you downloaded the software.
 * "Reference use" means use of the software within your company as a reference, in read only form, for the sole purposes
 * of debugging your products, maintaining your products, or enhancing the interoperability of your products with the
 * software, and specifically excludes the right to distribute the software outside of your company.
 * "Licensed patents" means any Licensor patent claims which read directly on the software as distributed by the Licensor
 * under this license.
 *
 * 2. Grant of Rights
 * (A) Copyright Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free copyright license to reproduce the software for reference use.
 * (B) Patent Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free patent license under licensed patents for reference use.
 *
 * 3. Limitations
 * (A) No Trademark License- This license does not grant you any rights to use the Licensor’s name, logo, or trademarks.
 * (B) If you begin patent litigation against the Licensor over patents that you think may apply to the software
 * (including a cross-claim or counterclaim in a lawsuit), your license to the software ends automatically.
 * (C) The software is licensed "as-is." You bear the risk of using it. The Licensor gives no express warranties,
 * guarantees or conditions. You may have additional consumer rights under your local laws which this license cannot
 * change. To the extent permitted under your local laws, the Licensor excludes the implied warranties of merchantability,
 * fitness for a particular purpose and non-infringement.
 */

package com.mycelium.wallet.lt.activity;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.*;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.google.common.base.Preconditions;
import com.megiontechnologies.Bitcoins;
import com.mrd.bitlib.StandardTransactionBuilder;
import com.mrd.bitlib.crypto.PublicKey;
import com.mrd.bitlib.model.Transaction;
import com.mrd.bitlib.util.HexUtils;
import com.mycelium.lt.ChatMessageEncryptionKey;
import com.mycelium.lt.ChatMessageEncryptionKey.InvalidChatMessage;
import com.mycelium.lt.api.model.ActionState;
import com.mycelium.lt.api.model.ChatEntry;
import com.mycelium.lt.api.model.TradeSession;
import com.mycelium.lt.api.params.TradeChangeParameters;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.Utils;
import com.mycelium.wallet.activity.send.SignTransactionActivity;
import com.mycelium.wallet.lt.LocalTraderEventSubscriber;
import com.mycelium.wallet.lt.LocalTraderManager;
import com.mycelium.wallet.lt.TradeSessionChangeMonitor;
import com.mycelium.wallet.lt.activity.buy.SetTradeAddress;
import com.mycelium.wallet.lt.api.*;
import com.mycelium.wapi.wallet.WalletAccount;

public class TradeActivity extends Activity {

   protected static final int CHANGE_PRICE_REQUEST_CODE = 1;
   protected static final int REFRESH_PRICE_REQUEST_CODE = 2;
   private static final int SIGN_TX_REQUEST_CODE = 3;

   public static void callMe(Activity currentActivity, TradeSession tradeSession) {
      if (tradeSession.isOpen && tradeSession.isBuyer && tradeSession.buyerAddress == null) {
         // We are the buyer, and the receiving address has not yet been set. Do
         // this now. It will call us again later
         SetTradeAddress.callMe(currentActivity, tradeSession);
      } else {
         Intent intent = new Intent(currentActivity, TradeActivity.class);
         intent.putExtra("tradeSession", tradeSession);
         intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
         currentActivity.startActivity(intent);
      }
   }

   private MbwManager _mbwManager;
   private LocalTraderManager _ltManager;
   private TradeSession _tradeSession;
   // private MyTradeSessionUpdateListener _tradeSessionListener;
   private MyListener _myListener;
   private Button _btRefresh;
   private Button _btChangePrice;
   private Button _btAccept;
   private Button _btCashReceived;
   private Button _btAbort;
   private EditText _etMessage;
   private ImageButton _btSendMessage;
   private TextView _tvStatus;
   private TextView _tvOldStatus;
   private View _flConfidence;
   private ProgressBar _pbConfidence;
   private TextView _tvConfidence;
   private ListView _lvChat;
   private ChatAdapter _chatAdapter;
   private Ringtone _updateSound;
   private boolean _dingOnUpdates;
   private boolean _didShowInsufficientFunds;
   public ChatMessageEncryptionKey _key;

   @Override
   public void onCreate(Bundle savedInstanceState) {
      this.requestWindowFeature(Window.FEATURE_NO_TITLE);
      super.onCreate(savedInstanceState);
      setContentView(R.layout.lt_trade_activity);
      _mbwManager = MbwManager.getInstance(this.getApplication());
      _ltManager = _mbwManager.getLocalTraderManager();

      _btRefresh = (Button) findViewById(R.id.btRefresh);
      _btChangePrice = (Button) findViewById(R.id.btChangePrice);
      _etMessage = (EditText) findViewById(R.id.etMessage);
      _btSendMessage = (ImageButton) findViewById(R.id.btSendMessage);
      _btAccept = (Button) findViewById(R.id.btAccept);
      _btCashReceived = (Button) findViewById(R.id.btCashReceived);
      _btAbort = (Button) findViewById(R.id.btAbort);
      _tvStatus = (TextView) findViewById(R.id.tvStatus);
      _tvOldStatus = (TextView) findViewById(R.id.tvOldStatus);
      _flConfidence = findViewById(R.id.flConfidence);
      _pbConfidence = (ProgressBar) findViewById(R.id.pbConfidence);
      _tvConfidence = (TextView) findViewById(R.id.tvConfidence);

      _btRefresh.setOnClickListener(refreshClickListener);
      _btChangePrice.setOnClickListener(changePriceClickListener);
      _etMessage.setOnEditorActionListener(editActionListener);
      _btSendMessage.setOnClickListener(sendMessageClickListener);
      _btAccept.setOnClickListener(acceptClickListener);
      _btAbort.setOnClickListener(abortOrStopOrDeleteClickListener);
      _btCashReceived.setOnClickListener(cashReceivedClickListener);
      _updateSound = RingtoneManager
            .getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

      _tradeSession = (TradeSession) getIntent().getSerializableExtra("tradeSession");

      // We may have a more recent copy if the activity is re-created.
      if (savedInstanceState != null) {
         _tradeSession = (TradeSession) savedInstanceState.getSerializable("tradeSession");
      }

      _mbwManager.getLocalTraderManager().markViewed(_tradeSession);

      _chatAdapter = new ChatAdapter(this, new ArrayList<ChatEntry>());

      _lvChat = (ListView) findViewById(R.id.lvChat);
      _lvChat.setAdapter(_chatAdapter);
      //to follow urls
      _lvChat.setOnItemClickListener(chatItemClickListener);
      //to copy to clipboard
      _lvChat.setOnItemLongClickListener(chatLongClickListener);

      Utils.showOptionalMessage(this, R.string.lt_cash_only_warning);

   }

   @Override
   protected void onResume() {
      _ltManager.enableNotifications(false);
      _ltManager.subscribe(ltSubscriber);
      _myListener = new MyListener(_tradeSession.id, _tradeSession.lastChange);
      _ltManager.startMonitoringTradeSession(_myListener);
      updateUi();
      super.onResume();
   }

   @Override
   protected void onPause() {
      _ltManager.stopMonitoringTradeSession();
      // _tradeSessionListener.cancel();
      _ltManager.unsubscribe(ltSubscriber);
      _ltManager.enableNotifications(true);
      super.onPause();
   }

   @Override
   protected void onSaveInstanceState(Bundle outState) {
      outState.putSerializable("tradeSession", _tradeSession);
      super.onSaveInstanceState(outState);
   }

   private ChatMessageEncryptionKey getChatMessageEncryptionKey() {
      Preconditions.checkState(_tradeSession != null);
      if (_key == null) {
         PublicKey foreignPublicKey = _tradeSession.isOwner ? _tradeSession.peerPublicKey
               : _tradeSession.ownerPublicKey;
         _key = _ltManager.generateChatMessageEncryptionKey(foreignPublicKey, _tradeSession.id);
      }
      return _key;
   }

   OnClickListener refreshClickListener = new OnClickListener() {

      @Override
      public void onClick(View arg0) {
         RefreshPriceActivity.callMeForResult(TradeActivity.this, _tradeSession, REFRESH_PRICE_REQUEST_CODE);
      }
   };

   OnClickListener changePriceClickListener = new OnClickListener() {

      @Override
      public void onClick(View arg0) {
         ChangePriceActivity.callMeForResult(TradeActivity.this, _tradeSession, CHANGE_PRICE_REQUEST_CODE);
      }
   };

   OnClickListener acceptClickListener = new OnClickListener() {

      @Override
      public void onClick(View arg0) {
         disableButtons();
         _dingOnUpdates = false;
         _ltManager.makeRequest(new AcceptTrade(_tradeSession.id, _tradeSession.lastChange));
      }
   };

   OnClickListener cashReceivedClickListener = new OnClickListener() {

      @Override
      public void onClick(View arg0) {
         confirmCashReceived();
      }
   };

   private void confirmCashReceived() {
      AlertDialog.Builder confirmDialog = new AlertDialog.Builder(this);
      confirmDialog.setTitle(R.string.lt_confirm_title);
      String name = _tradeSession.isOwner ? _tradeSession.peerName : _tradeSession.ownerName;
      String msg = getString(R.string.lt_confirm_cash_received, _tradeSession.fiatTraded, _tradeSession.currency, name);
      confirmDialog.setMessage(msg);
      confirmDialog.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

         public void onClick(DialogInterface arg0, int arg1) {
            // User clicked yes
            pinProtectedCashRelease();
         }
      });
      confirmDialog.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {

         public void onClick(DialogInterface arg0, int arg1) {
            // User clicked no
         }
      });
      confirmDialog.show();
   }

   private void pinProtectedCashRelease() {
      _mbwManager.runPinProtectedFunction(this, new Runnable() {

         @Override
         public void run() {
            createSignedTransaction(_tradeSession, _mbwManager);
         }

      });
   }

   private void createSignedTransaction(TradeSession ts, MbwManager mbwManager) {
      Preconditions.checkNotNull(ts.buyerAddress);
      WalletAccount acc = mbwManager.getSelectedAccount();

      // Create unsigned transaction
      StandardTransactionBuilder.UnsignedTransaction unsigned = TradeActivityUtil.createUnsignedTransaction(ts.satoshisFromSeller, ts.satoshisForBuyer,
            ts.buyerAddress, ts.feeAddress, acc, _ltManager.getMinerFeeEstimation().getLongValue());

      SignTransactionActivity.callMe(this, mbwManager.getSelectedAccount().getId(), false, unsigned, SIGN_TX_REQUEST_CODE);
   }


   OnClickListener abortOrStopOrDeleteClickListener = new OnClickListener() {

      @Override
      public void onClick(View arg0) {
         if (_tradeSession.abortAction.isApplicable()) {
            //abort trade
            confirmAbort();
         } else {
            //delete history
            confirmDelete();
         }
      }
   };

   private void confirmDelete() {
      AlertDialog.Builder confirmDialog = new AlertDialog.Builder(this);
      confirmDialog.setTitle(R.string.lt_confirm_title);
      confirmDialog.setMessage(getString(R.string.lt_confirm_delete_history));
      confirmDialog.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

         public void onClick(DialogInterface arg0, int arg1) {
            // User clicked yes
            _ltManager.makeRequest(new DeleteTradeHistory(_tradeSession.id));
            finish();
         }
      });
      confirmDialog.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {

         public void onClick(DialogInterface arg0, int arg1) {
            // User clicked no
         }
      });
      confirmDialog.show();

   }

   private void confirmAbort() {
      String message;
      if (_tradeSession.isWaitingForPeerAccept || _tradeSession.acceptAction.isEnabled()) {
         // Both did not accept, no rating penalty
         message = getString(R.string.lt_confirm_stop);
      } else {
         // Both accepted, rating penalty may apply
         message = getString(R.string.lt_confirm_abort);
      }
      AlertDialog.Builder confirmDialog = new AlertDialog.Builder(this);
      confirmDialog.setTitle(R.string.lt_confirm_title);
      confirmDialog.setMessage(message);
      confirmDialog.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

         public void onClick(DialogInterface arg0, int arg1) {
            // User clicked yes
            disableButtons();
            _dingOnUpdates = false;
            _ltManager.makeRequest(new AbortTrade(_tradeSession.id));
         }
      });
      confirmDialog.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {

         public void onClick(DialogInterface arg0, int arg1) {
            // User clicked no
         }
      });
      confirmDialog.show();

   }

   OnEditorActionListener editActionListener = new OnEditorActionListener() {

      @Override
      public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
         if (actionId == EditorInfo.IME_ACTION_DONE) {
            sendMessage();
         }
         return false;

      }
   };

   OnClickListener sendMessageClickListener = new OnClickListener() {

      @Override
      public void onClick(View arg0) {
         sendMessage();
      }
   };

   private void sendMessage() {
      String msg = _etMessage.getEditableText().toString();
      if (msg.trim().length() == 0) {
         return;
      }
      _etMessage.setText("");
      disableButtons();
      _dingOnUpdates = false;
      _ltManager.makeRequest(new SendEncryptedChatMessage(_tradeSession.id, msg, getChatMessageEncryptionKey()));
   }

   OnItemClickListener chatItemClickListener = new OnItemClickListener() {

      @Override
      public void onItemClick(AdapterView<?> adapter, View view, int arg2, long arg3) {
         if (view != null) {
            TextView tvMessage = (TextView) view.findViewById(R.id.tvMessage);
            if (tvMessage != null) {
               String text = tvMessage.getText().toString();
               Uri uri = getUriFromAnyText(text);
               if (uri != null) {
                  Intent intent = new Intent(Intent.ACTION_VIEW);
                  intent.setData(uri);
                  startActivity(intent);
                  String toast = getString(R.string.lt_going_to_website, uri.getHost());
                  Toast.makeText(TradeActivity.this, toast, Toast.LENGTH_LONG).show();
               }
            }
         }
      }

      private Uri getUriFromAnyText(String text) {
         if (text == null) {
            return null;
         }
         int start = text.indexOf("http://");
         if (start == -1) {
            start = text.indexOf("https://");
         }
         if (start == -1) {
            return null;
         }
         int end = text.indexOf(' ', start);
         if (end == -1) {
            end = text.length();
         }
         String urlString = text.substring(start, end);
         try {
            URL url = new URL(urlString);
            String protocol = url.getProtocol();
            if (protocol.equals("http") || protocol.equals("https")) {
               // Converting URL to URI to Uri, a bit stupid though
               Uri uri = Uri.parse(url.toURI().toString());
               return uri;
            }
         } catch (MalformedURLException e) {
            // pass through
         } catch (URISyntaxException e) {
            // pass through
         }
         return null;
      }
   };

   OnItemLongClickListener chatLongClickListener = new OnItemLongClickListener() {
      @Override
      public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
         if (view != null) {
            TextView tvMessage = (TextView) view.findViewById(R.id.tvMessage);
            if (tvMessage != null) {
               String text = tvMessage.getText().toString();
               //set the message to clipboard
               Utils.setClipboardString(text, TradeActivity.this);
               String toast = getString(R.string.lt_copied_to_clipboard);
               Toast.makeText(TradeActivity.this, toast, Toast.LENGTH_LONG).show();
               return true;
            }
         }
         return false;
      }
   };

   private void updateUi() {
      if (_tradeSession == null) {
         findViewById(R.id.pbWait).setVisibility(View.VISIBLE);
         findViewById(R.id.llRoot).setVisibility(View.GONE);
      } else {
         findViewById(R.id.pbWait).setVisibility(View.GONE);
         findViewById(R.id.llRoot).setVisibility(View.VISIBLE);
         updateUi(_tradeSession);
      }
   }

   private void updateUi(TradeSession tradeSession) {

      long satoshisTraded;
      if (tradeSession.isBuyer) {
         satoshisTraded = tradeSession.satoshisForBuyer;
      } else {
         satoshisTraded = tradeSession.satoshisFromSeller;
      }

      TradeActivityUtil.populatePriceDetails(this, findViewById(R.id.llRoot), tradeSession.isBuyer, true,
            tradeSession.currency, tradeSession.priceFormula, tradeSession.satoshisAtMarketPrice, satoshisTraded,
            tradeSession.fiatTraded, _mbwManager);

      // Status
      String newStatusText = getResources().getString(R.string.lt_status_text, tradeSession.statusText);
      String oldStatusText = _tvStatus.getText().toString();
      if (!newStatusText.equals(oldStatusText)) {
         _tvStatus.setText(newStatusText);

         if (tradeSession.confidence != null) {
            // Don't animate when we show the progress bar
            _tvOldStatus.setText("");
         } else {
            _tvOldStatus.setText(oldStatusText);
            // Move new status in
            Utils.moveView(_tvStatus, _tvStatus.getWidth(), 0, 0, 0, 1000);
            // Move old status out
            Utils.moveView(_tvOldStatus, 0, 0, -_tvOldStatus.getWidth(), 0, 1000);
         }
      }

      // Progress
      if (tradeSession.confidence != null) {
         _flConfidence.setVisibility(View.VISIBLE);
         _pbConfidence.setProgress((int) (_pbConfidence.getMax() * tradeSession.confidence));
         _pbConfidence.setSecondaryProgress(_pbConfidence.getMax());
         int percent = (int) (100 * tradeSession.confidence);
         if (percent > 99) {
            percent = 99;
         }
         _tvConfidence.setText(getResources().getString(R.string.lt_transaction_confidence, percent));
      } else {
         _flConfidence.setVisibility(View.GONE);
      }

      //Delete or Stop or Abort
      if (!_tradeSession.isOpen) {
         //trade is through, so delete history is possible
         _btAbort.setText(R.string.lt_delete_trade_history);
      } else if (_tradeSession.isWaitingForPeerAccept || _tradeSession.acceptAction.isEnabled()) {
         //no one acceptec, so stop is possible
         _btAbort.setText(R.string.lt_stop_trade_button);
      } else {
         //was accepted, so only abort with penalty is possible
         _btAbort.setText(R.string.lt_abort_trade_button);
      }

      // Chat
      _chatAdapter.clear();
      for (ChatEntry chatEntry : tradeSession.chatEntries) {
         _chatAdapter.add(chatEntry);
      }
      _chatAdapter.notifyDataSetChanged();
      scrollChatToBottom();

      enableButtons(tradeSession);
   }

   private void enableButtons(TradeSession tradeSession) {

      // Figure out whether we can afford to send BTC for this trade
      boolean canWeAffordThis;
      if (tradeSession.isBuyer) {
         // Buyer does not have to pay any BTC
         canWeAffordThis = true;
      } else {
         canWeAffordThis = TradeActivityUtil.canAffordTrade(tradeSession, _mbwManager);
      }

      if (!canWeAffordThis) {
         if (tradeSession.acceptAction.isEnabled() || tradeSession.releaseBtcAction.isEnabled()) {
            displayInsufficientFunds();
         }
      }

      applyActionStateToButton(tradeSession.acceptAction, canWeAffordThis, _btAccept);
      applyActionStateToButton(tradeSession.refreshRateAction, _btRefresh);
      applyActionStateToButton(tradeSession.changePriceAction, _btChangePrice);
      applyActionStateToButton(tradeSession.releaseBtcAction, canWeAffordThis, _btCashReceived);
      applyActionStateToButton(tradeSession.sendMessageAction, _btSendMessage);
      applyActionStateToButton(tradeSession.sendMessageAction, _etMessage);
      applyActionStateToButton(tradeSession.abortAction, _btAbort);

      //if the trade is through, show the button to enable delete history
      if (!_tradeSession.isOpen) {
         _btAbort.setVisibility(View.VISIBLE);
         _btAbort.setEnabled(true);
      }
   }

   private void displayInsufficientFunds() {
      if (!_didShowInsufficientFunds) {
         // Only show this dialog once for every time the activity is displayed
         Utils.showSimpleMessageDialog(this, R.string.lt_cannot_affort_trade);
         _didShowInsufficientFunds = true;
      }
   }

   private void applyActionStateToButton(ActionState state, View button) {
      applyActionStateToButton(state, true, button);
   }

   private void applyActionStateToButton(ActionState state, boolean extraEnableCondition, View view) {
      if (!state.isApplicable()) {
         view.setVisibility(View.GONE);
      } else {
         view.setVisibility(View.VISIBLE);
         view.setEnabled(state.isEnabled() && extraEnableCondition);
      }
   }

   private void disableButtons() {
      _btRefresh.setEnabled(false);
      _btChangePrice.setEnabled(false);
      _btAccept.setEnabled(false);
      _btAbort.setEnabled(false);
      _btSendMessage.setEnabled(false);
      _etMessage.setEnabled(false);
      _btCashReceived.setEnabled(false);
   }

   private class ChatAdapter extends ArrayAdapter<ChatEntry> {
      private Context _context;
      private Date _midnight;
      private DateFormat _dayFormat;
      private DateFormat _hourFormat;
      private int _ownerMessageBackgroundColor;
      private int _peerMessageBackgroundColor;
      private int _eventBackgroundColor;
      private int _invalidMessageBackgroundColor;

      public ChatAdapter(Context context, List<ChatEntry> objects) {
         super(context, R.layout.lt_chat_entry_row, objects);
         _context = context;
         // Get the time at last midnight
         Calendar midnight = Calendar.getInstance();
         midnight.set(midnight.get(Calendar.YEAR), midnight.get(Calendar.MONTH), midnight.get(Calendar.DAY_OF_MONTH),
               0, 0, 0);
         _midnight = midnight.getTime();
         // Create date formats for hourly and day format
         Locale locale = getResources().getConfiguration().locale;
         _dayFormat = DateFormat.getDateInstance(DateFormat.SHORT, locale);
         _hourFormat = android.text.format.DateFormat.getTimeFormat(_context);
         _context = context;
         _ownerMessageBackgroundColor = context.getResources().getColor(R.color.grey);
         _peerMessageBackgroundColor = context.getResources().getColor(R.color.darkgrey);
         _eventBackgroundColor = context.getResources().getColor(R.color.black);
         _ownerMessageBackgroundColor = 0x22FFFFFF; // half transparent white
         // (grey)
         _peerMessageBackgroundColor = 0x11FFFFFF; // slightly transparent white
                                                   // (dark grey)
         _eventBackgroundColor = 0x00FFFFFF; // transparent white (black)
         _invalidMessageBackgroundColor = 0xFFFF0000; // red
      }

      @Override
      public View getView(int position, View convertView, ViewGroup parent) {
         View v = convertView;

         if (v == null) {
            LayoutInflater vi = (LayoutInflater) _context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = Preconditions.checkNotNull(vi.inflate(R.layout.lt_chat_entry_row, null));
         }
         ChatEntry o = getItem(position);

         // Date, format depending on whether it is the same day or earlier
         Date date = new Date(o.time);
         String dateString;
         if (date.before(_midnight)) {
            dateString = _dayFormat.format(date) + "\n" + _hourFormat.format(date);
         } else {
            dateString = _hourFormat.format(date);
         }
         TextView tvDate = (TextView) v.findViewById(R.id.tvDate);
         tvDate.setText(dateString);

         // Message text and color
         TextView tvMessage = (TextView) v.findViewById(R.id.tvMessage);
         String text;
         int color;
         // Message Color
         switch (o.type) {
         case ChatEntry.TYPE_EVENT:
            text = o.message;
            color = _eventBackgroundColor;
            break;
         case ChatEntry.TYPE_OWNER_CHAT:
            try {
               text = new StringBuilder().append(_tradeSession.ownerName).append(": ")
                     .append(getChatMessageEncryptionKey().decryptAndCheckChatMessage(o.message)).toString();
               color = _ownerMessageBackgroundColor;
            } catch (InvalidChatMessage e) {
               text = getString(R.string.lt_invalid_chat_message, _tradeSession.ownerName);
               color = _invalidMessageBackgroundColor;
            }
            break;
         case ChatEntry.TYPE_PEER_CHAT:
            try {
               text = new StringBuilder().append(_tradeSession.peerName).append(": ")
                     .append(getChatMessageEncryptionKey().decryptAndCheckChatMessage(o.message)).toString();
               color = _peerMessageBackgroundColor;
            } catch (InvalidChatMessage e) {
               text = getString(R.string.lt_invalid_chat_message, _tradeSession.peerName);
               color = _invalidMessageBackgroundColor;
            }
            break;
         default:
            text = "";
            color = _eventBackgroundColor;
         }
         tvMessage.setText(text);
         v.setBackgroundColor(color);

         v.setTag(o);
         return v;
      }
   }

   private void scrollChatToBottom() {
      _lvChat.post(new Runnable() {
         @Override
         public void run() {
            _lvChat.setSelection(_chatAdapter.getCount() - 1);
         }
      });
   }

   class MyListener extends TradeSessionChangeMonitor.Listener {

      protected MyListener(UUID tradeSessionId, long lastChange) {
         super(tradeSessionId, lastChange);
      }

      @Override
      public void onTradeSessionChanged(TradeSession tradeSession) {
         _tradeSession = tradeSession;
         // Mark session as viewed
         _mbwManager.getLocalTraderManager().markViewed(_tradeSession);
         // Tell other listeners that we have taken care of audibly notifying up
         // till this timestamp
         _ltManager.setLastNotificationSoundTimestamp(tradeSession.lastChange);
         if (tradeSession.confidence != null && tradeSession.confidence > 0) {
            // While displaying confidence we do not play a notification sound
         } else {
            if (_dingOnUpdates && _updateSound != null && _ltManager.getPlaySoundOnTradeNotification()) {
               _updateSound.play();
            }
            _dingOnUpdates = true;
         }
         updateUi();
      }

   }

   public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
      if (requestCode == CHANGE_PRICE_REQUEST_CODE) {
         if (resultCode == RESULT_OK) {
            disableButtons();
            _dingOnUpdates = false;
            TradeChangeParameters params = (TradeChangeParameters) intent
                  .getSerializableExtra(ChangePriceActivity.RESULT_STRING);
            _ltManager.makeRequest(new ChangeTradeSessionPrice(params));
         }
      } else if (requestCode == REFRESH_PRICE_REQUEST_CODE) {
         if (resultCode == RESULT_OK) {
            disableButtons();
            _dingOnUpdates = false;
            _ltManager.makeRequest(new RequestMarketRateRefresh(_tradeSession.id));
         }
      } else if (requestCode == SIGN_TX_REQUEST_CODE) {
         if (resultCode == RESULT_OK){
            Transaction tx = (Transaction)intent.getSerializableExtra("signedTx");
            if (tx == null) {
               Toast.makeText(TradeActivity.this, R.string.lt_cannot_affort_trade, Toast.LENGTH_LONG).show();
               return;
            }
            disableButtons();
            _dingOnUpdates = false;
            // send signed tx to server - it will check it and handle the broadcast
            _ltManager.makeRequest(new ReleaseBtc(_tradeSession.id, HexUtils.toHex(tx.toBytes())));
         }
      }
   }

   private LocalTraderEventSubscriber ltSubscriber = new LocalTraderEventSubscriber(new Handler()) {

      @Override
      public void onLtError(int errorCode) {
         Toast.makeText(TradeActivity.this, R.string.lt_error_api_occurred, Toast.LENGTH_LONG).show();
         finish();
      }

      @Override
      public boolean onNoLtConnection() {
         Utils.toastConnectionError(TradeActivity.this);
         finish();
         return true;
      }

      @Override
      public void onLtBtcReleased(Boolean success, ReleaseBtc request) {
         _mbwManager.getWalletManager(false).startSynchronization();
      }

   };

}