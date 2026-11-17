package com.example.foodlog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

public class MainActivity extends Activity {
    private static final int BG=0xFFF7F8F2, INK=0xFF1E352B, GREEN=0xFF276247, MUTED=0xFF68766E;
    private LogStore store;
    private AutoSync autoSync;
    private TextView syncStatus;
    private boolean resumed, syncRefreshPending;
    private final Runnable autoSyncTick=new Runnable(){
        @Override public void run(){
            if(resumed){
                handler.removeCallbacks(this);handler.postDelayed(this,15000);
                if(weightInputs.stream().noneMatch(input->input.getError()!=null))autoSync.sync();
            }
        }
    };
    private void scheduleSync(){if(resumed){handler.removeCallbacks(autoSyncTick);handler.postDelayed(autoSyncTick,2000);}}
    private void syncChanged(boolean changed){
        syncRefreshPending|=changed;
        if(syncStatus!=null)syncStatus.setText(autoSync.status());
        if(syncRefreshPending&&hasWindowFocus()&&weightInputs.stream().noneMatch(input->input.getError()!=null)){
            View focus=getCurrentFocus();Object tag=focus==null?null:focus.getTag();
            int selection=focus instanceof EditText?((EditText)focus).getSelectionStart():-1;
            syncRefreshPending=false;render();
            if(tag!=null)for(EditText input:weightInputs)if(tag.equals(input.getTag())){input.requestFocus();input.setSelection(Math.min(Math.max(0,selection),input.length()));break;}
        }
    }
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus&&autoSync!=null)syncChanged(false);}

    private SendController mailer;
    private LocalDate date;
    private boolean followingToday=true;
    private LinearLayout root, rows;
    private Button sendButton;
    private final Map<Long,String> previousWeights = new HashMap<>();
    private final Set<Long> expandedRecipes = new HashSet<>();
    private final List<EditText> weightInputs = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable rollover = new Runnable() {
        @Override public void run() { checkDay(); handler.postDelayed(this,15000); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store=new LogStore(this);
        mailer=((FoodLogApplication)getApplication()).sender();
        autoSync=((FoodLogApplication)getApplication()).sync();
        date=state==null ? LocalDate.now() : LocalDate.parse(state.getString("date"));
        followingToday=state==null || state.getBoolean("today");
        if (state != null) {
            long[] expanded = state.getLongArray("expandedRecipes");
            if (expanded != null) for (long id : expanded) expandedRecipes.add(id);
        }
        render();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("date",date.toString()); out.putBoolean("today",followingToday);
        long[] expanded = new long[expandedRecipes.size()]; int i=0;
        for (long id : expandedRecipes) expanded[i++]=id;
        out.putLongArray("expandedRecipes",expanded);
    }
    @Override protected void onResume() {
        super.onResume(); checkDay(); handler.postDelayed(rollover,15000);
        resumed=true;autoSync.observe(this::syncChanged);syncRefreshPending=true;syncChanged(false);handler.post(autoSyncTick);
        mailer.observe(()->{
            updateSummary();String result=mailer.takeResult();
            if(!result.isEmpty()) {
                if(result.startsWith("Gmail accepted"))toast(result);
                else new AlertDialog.Builder(this).setTitle("Email status").setMessage(result).setPositiveButton("OK",null).show();
            }
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==42&&result==RESULT_OK)render();}
    @Override protected void onPause() { super.onPause(); resumed=false;handler.removeCallbacks(autoSyncTick);autoSync.observe(null);handler.removeCallbacks(rollover); mailer.observe(null); }
    @Override protected void onDestroy() { super.onDestroy(); store.close(); }
    private void checkDay() {
        if (followingToday && !date.equals(LocalDate.now()) && validInputs()) {
            date=LocalDate.now(); render(); toast("A new day. Your food list is ready.");
        }
    }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color,int radius) {
        GradientDrawable drawable=new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable;
    }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if(bold)t.setTypeface(null,Typeface.BOLD); return t;
    }
    private Button button(String label,boolean primary) {
        Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(15);
        b.setTextColor(primary?Color.WHITE:GREEN); b.setBackground(shape(primary?GREEN:0xFFE8EEDC,8));
        b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48)); b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(8),0,dp(8),0); return b;
    }
    private void gap(LinearLayout layout,int height) { View v=new View(this);layout.addView(v,new LinearLayout.LayoutParams(1,dp(height))); }
    private void render() {
        scheduleSync();
        weightInputs.clear();
        // Reference values are UI hints only. Never copy them into today's entries.
        previousWeights.clear();
        for(LogStore.Food food:store.foods(date.minusDays(1).toString(),false)) {
            previousWeights.put(food.id(),food.grams());
        }
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(8),dp(4),dp(8),dp(4)); root.setBackgroundColor(BG);
        root.setFocusableInTouchMode(true);
        setContentView(root);
        boolean isToday=date.equals(LocalDate.now());
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        Button dateButton=button((isToday?"Today · ":"")+date.format(DateTimeFormatter.ofPattern("d MMM yyyy",Locale.getDefault()))+" ▾",false);
        dateButton.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        dateButton.setBackgroundColor(Color.TRANSPARENT);
        dateButton.setContentDescription("History: choose log date, currently "+date);
        dateButton.setOnClickListener(v->pickDate());
        top.addView(dateButton,new LinearLayout.LayoutParams(0,dp(48),1));
        if(!followingToday) {
            Button today=button("Today",false);today.setOnClickListener(v->{if(validInputs()){followingToday=true;date=LocalDate.now();render();}});
            top.addView(today);
        }
        Button sync=button("Sync",false);sync.setContentDescription("Sync with browser");sync.setOnClickListener(v->{if(autoSync.busy()){toast("Sync is running. Please wait a moment.");return;}if(validInputs())startActivityForResult(new Intent(this,SyncActivity.class),42);});top.addView(sync);
        Button settings=button("Email",false);settings.setBackgroundColor(Color.TRANSPARENT);
        settings.setContentDescription("Set recipient email address");settings.setOnClickListener(v->emailSettings());top.addView(settings);
        root.addView(top);
        syncStatus=text(autoSync.status(),12,MUTED,false);root.addView(syncStatus);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        List<LogStore.Food> foods=store.foods(date.toString(),isToday);
        List<LogStore.Recipe> recipes=store.recipes(date.toString(),isToday);
        if(foods.isEmpty() && recipes.isEmpty()) {
            TextView empty=text(isToday?"Add a food or recipe to get started.":"No foods logged on this date.",15,MUTED,false);
            empty.setGravity(Gravity.CENTER);empty.setPadding(dp(16),dp(28),dp(16),dp(28)); rows.addView(empty);
        }
        for(LogStore.Food food:foods) if(food.recipeId()==0) addFoodRow(food,rows);
        for(LogStore.Recipe recipe:recipes) addRecipeRow(recipe,foods,isToday);
        gap(root,4);
        LinearLayout actions=new LinearLayout(this);
        if(isToday) {
            Button add=button("+ Food",false);add.setContentDescription("Add a food");add.setOnClickListener(v->addFood(null));
            actions.addView(add,new LinearLayout.LayoutParams(0,dp(48),1));
            View spacer=new View(this);actions.addView(spacer,new LinearLayout.LayoutParams(dp(4),1));
            Button recipe=button("+ Recipe",false);recipe.setContentDescription("Add recipe");recipe.setOnClickListener(v->addRecipe());
            actions.addView(recipe,new LinearLayout.LayoutParams(0,dp(48),1));
            View spacer2=new View(this);actions.addView(spacer2,new LinearLayout.LayoutParams(dp(4),1));
        }
        sendButton=button("Send",true);sendButton.setContentDescription("Send daily log");sendButton.setOnClickListener(v->send());
        actions.addView(sendButton,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(actions);updateSummary();
    }
    private void addFoodRow(LogStore.Food food, LinearLayout container) {
        LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);
        line.setMinimumHeight(dp(52));line.setPadding(dp(8),0,0,0);line.setBackground(shape(Color.WHITE,6));
        TextView name=text(food.name()+(food.active()?"":" · removed"),15,INK,false);
        name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setTooltipText(food.name());
        line.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        EditText input=new EditText(this);input.setSingleLine(true);input.setTextSize(18);input.setTextColor(INK);
        input.setPadding(dp(4),0,dp(4),0);input.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        String previous=previousWeights.getOrDefault(food.id(),"");
        input.setHint(previous.isEmpty()?"—":previous);input.setHintTextColor(0xFF818781);
        input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
        input.setTag(food.id());
        input.setContentDescription(food.name()+", total weight in grams"+(previous.isEmpty()?"":". Grey hint: "+previous+" grams on previous day, not logged"));
        input.setTooltipText(previous.isEmpty()?"Enter grams":"Grey: previous day, not logged");input.setText(food.grams());
        line.addView(input,new LinearLayout.LayoutParams(dp(80),dp(48)));
        TextView grams=text("g",13,MUTED,false);grams.setPadding(dp(4),0,dp(4),0);line.addView(grams);
        Button add=button("+",false);add.setTextSize(22);add.setBackgroundColor(Color.TRANSPARENT);
        add.setContentDescription("Add grams to "+food.name());add.setTooltipText("Add another amount");
        add.setOnClickListener(v->addWeight(food,input));line.addView(add,new LinearLayout.LayoutParams(dp(48),dp(48)));
        { // Also allow removing entries marked removed by older app versions.
            Button remove=button("×",false);remove.setTextSize(20);remove.setTextColor(MUTED);remove.setBackgroundColor(Color.TRANSPARENT);
            remove.setContentDescription("Remove "+food.name()+" from daily list");remove.setOnClickListener(v->removeFood(food));
            line.addView(remove,new LinearLayout.LayoutParams(dp(48),dp(48)));
        }
        final String entryDay=date.toString();
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count) {
                try { String value=LogFormat.weight(s.toString());store.save(entryDay,food,value);input.setError(null);updateSummary();scheduleSync(); }
                catch(IllegalArgumentException e){ input.setError(e.getMessage()); }
                catch(android.database.SQLException e){ input.setError("Could not save. Please try again."); }
            }
            public void afterTextChanged(Editable e){}
        });
        weightInputs.add(input);container.addView(line,new LinearLayout.LayoutParams(-1,-2));gap(container,2);
    }
    private void addWeight(LogStore.Food food, EditText totalInput) {
        if(!validInputs())return;
        // Read the current field, not the row's original value or its grey hint.
        String current=totalInput.getText().toString();
        EditText extra=new EditText(this);extra.setSingleLine(true);extra.setHint("Grams to add");
        extra.setContentDescription("Additional grams");
        extra.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        extra.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Add to "+food.name())
            .setMessage("Logged: "+(current.isEmpty()?"0":current)+" g · "+date)
            .setView(extra).setNegativeButton("Cancel",null).setPositiveButton("Add",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                String total=LogFormat.addWeight(current,extra.getText().toString());
                totalInput.setText(total);
                if(totalInput.getError()!=null){extra.setError("Could not save. Please try again.");return;}
                dialog.dismiss();
            } catch(IllegalArgumentException e){extra.setError(e.getMessage());}
        }));dialog.show();extra.requestFocus();
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }
    private void addRecipeRow(LogStore.Recipe recipe, List<LogStore.Food> foods, boolean isToday) {
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(4),0,dp(4),dp(2));card.setBackground(shape(0xFFE8EEDC,8));
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        Button toggle=button("",false);toggle.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        heading.addView(toggle,new LinearLayout.LayoutParams(0,-2,1));
        { // Historical and previously removed recipes can be cleared for this date.
            Button remove=button("×",false);remove.setTextSize(22);remove.setContentDescription("Delete recipe "+recipe.name());
            remove.setOnClickListener(v->removeRecipe(recipe));heading.addView(remove,new LinearLayout.LayoutParams(dp(48),dp(48)));
        }
        card.addView(heading);
        LinearLayout ingredients=new LinearLayout(this);ingredients.setOrientation(LinearLayout.VERTICAL);
        int count=0;
        for(LogStore.Food food:foods) if(food.recipeId()==recipe.id()) {addFoodRow(food,ingredients);count++;}
        if(count==0) {ingredients.addView(text("No ingredients yet.",13,MUTED,false));}
        if(recipe.active() && isToday) {
            Button add=button("+  Add ingredient",false);add.setOnClickListener(v->addFood(recipe));ingredients.addView(add);
        }
        card.addView(ingredients);
        Runnable refresh=()->{
            boolean expanded=expandedRecipes.contains(recipe.id());
            ingredients.setVisibility(expanded?View.VISIBLE:View.GONE);
            toggle.setText((expanded?"▾  ":"▸  ")+recipe.name()+(recipe.active()?"":" · removed"));
            toggle.setContentDescription((expanded?"Collapse recipe ":"Expand recipe ")+recipe.name());
        };
        toggle.setOnClickListener(v->{
            if(expandedRecipes.contains(recipe.id())) {
                if(!validInputs())return;
                expandedRecipes.remove(recipe.id());
            } else expandedRecipes.add(recipe.id());
            refresh.run();
        });refresh.run();
        rows.addView(card,new LinearLayout.LayoutParams(-1,-2));gap(rows,4);
    }
    private void addRecipe() {
        if(!validInputs())return;
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("e.g. Smoothie");
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Create recipe")
            .setMessage("Name your recipe. Then add its ingredients, which stay ready for each new day.")
            .setView(input).setNegativeButton("Cancel",null).setPositiveButton("Create",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String name=input.getText().toString().trim();
            if(name.isEmpty()||name.length()>100){input.setError("Use a name between 1 and 100 characters");return;}
            long id=store.addRecipe(name);
            if(id==-1){input.setError("This recipe already exists");return;}
            expandedRecipes.add(id);dialog.dismiss();render();
        }));dialog.show();input.requestFocus();
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }
    private void removeRecipe(LogStore.Recipe recipe) {
        if(!validInputs())return;
        new AlertDialog.Builder(this).setTitle("Delete "+recipe.name()+"?")
            .setMessage("Remove this recipe from your list and delete all its weights for "+date+" from the log and future emails. Other dates and foods stay unchanged.")
            .setNegativeButton("Keep",null).setPositiveButton("Delete",(d,w)->{store.removeRecipe(recipe.id(),date.toString());render();}).show();
    }
    private void updateSummary() {
        if(sendButton==null)return;
        int count=store.foods(date.toString(),false).size();
        sendButton.setEnabled(!mailer.sending());
        sendButton.setText(mailer.sending()?"Sending…":count==0?"Send":"Send ("+count+")");
    }
    private boolean validInputs() {
        for(EditText input:weightInputs)if(input.getError()!=null){input.requestFocus();return false;}return true;
    }
    private void addFood(LogStore.Recipe recipe) {
        if(!validInputs())return;
        AutoCompleteTextView input=new AutoCompleteTextView(this);input.setHint("e.g. Milk");input.setSingleLine(true);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setThreshold(1);input.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_dropdown_item_1line,store.foodNames()));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(recipe==null?"Add a food":"Add ingredient to "+recipe.name()).setMessage(recipe==null?"It stays on your list until you remove it.":"Choose a familiar food or type a new one. Its weight is separate from foods outside this recipe.").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Add",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String name=input.getText().toString().trim();
            if(name.isEmpty()||name.length()>100){input.setError("Use a name between 1 and 100 characters");return;}
            if(!store.add(name,recipe==null?0:recipe.id())){input.setError(recipe==null?"This food is already on your list":"This ingredient is already in the recipe");return;}
            if(recipe!=null)expandedRecipes.add(recipe.id());
            dialog.dismiss();render();
        }));dialog.show();input.requestFocus();dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }
    private void removeFood(LogStore.Food food) {
        if(!validInputs())return;
        new AlertDialog.Builder(this).setTitle("Remove "+food.name()+"?")
            .setMessage("Remove this food from your list and delete its weight for "+date+" from the log and future emails. Other dates stay unchanged.")
            .setNegativeButton("Keep",null).setPositiveButton("Remove",(d,w)->{store.remove(food.id(),date.toString());render();}).show();
    }
    private void pickDate() {
        if(!validInputs())return;
        DatePickerDialog picker=new DatePickerDialog(this,(view,y,m,d)->{date=LocalDate.of(y,m+1,d);followingToday=date.equals(LocalDate.now());render();},date.getYear(),date.getMonthValue()-1,date.getDayOfMonth());
        picker.getDatePicker().setMaxDate(System.currentTimeMillis());picker.show();
    }
    private String recipient() { return getPreferences(MODE_PRIVATE).getString("recipient",""); }
    private EditText emailField(LinearLayout form,String label,String value,boolean password) {
        form.addView(text(label,13,MUTED,false));
        EditText field=new EditText(this);field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT|(password?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS));
        field.setText(value);field.setContentDescription(label);form.addView(field);return field;
    }
    private void emailSettings() {
        if(mailer.sending()){toast("Please wait until sending finishes.");return;}
        GmailSettings settings=new GmailSettings(this);
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(20),dp(8),dp(20),dp(8));
        EditText from=emailField(form,"Gmail address",settings.sender(),false);
        EditText to=emailField(form,"Send logs to",settings.configured()?settings.recipient():recipient(),false);
        EditText password=emailField(form,"Google app password","",true);
        password.setHint(settings.configured()?"Leave blank to keep saved password":"16-character app password");
        password.setSaveEnabled(false);password.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        form.addView(text("Use a Google app password, not your regular password. Enable 2-Step Verification in your Google account first.",13,MUTED,false));
        Button help=button("Create Google app password",false);help.setOnClickListener(v->{
            try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://myaccount.google.com/apppasswords")));}
            catch(android.content.ActivityNotFoundException e){toast("Open myaccount.google.com/apppasswords in your browser.");}
        });form.addView(help);
        Button share=button("Share CSV using another app",false);form.addView(share);
        Button disconnect=button("Forget Gmail connection",false);if(settings.configured())form.addView(disconnect);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("One-button Gmail sending").setView(scroll)
            .setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        share.setOnClickListener(v->{dialog.dismiss();shareLog();});
        disconnect.setOnClickListener(v->{settings.clear();dialog.dismiss();toast("Gmail connection removed from this phone.");});
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String sender=from.getText().toString().trim(),recipient=to.getText().toString().trim();
            String secret=password.getText().toString().replaceAll("\\s","");
            if(!android.util.Patterns.EMAIL_ADDRESS.matcher(sender).matches()){from.setError("Enter your Gmail address");return;}
            if(!android.util.Patterns.EMAIL_ADDRESS.matcher(recipient).matches()){to.setError("Enter a recipient email address");return;}
            if(secret.isEmpty() && (!settings.configured() || !sender.equals(settings.sender()))) {password.setError("Enter a Google app password");return;}
            if(!secret.isEmpty() && !secret.matches("[A-Za-z0-9]{16}")){password.setError("Use the 16-character Google app password");return;}
            try {
                settings.save(sender,recipient,secret);
                getPreferences(MODE_PRIVATE).edit().putString("recipient",recipient).apply();
                password.setText("");dialog.dismiss();toast("Saved. Tap Send to email your daily log.");
            } catch(Exception e) {password.setError("Could not securely save settings. Please try again.");}
        }));dialog.show();
    }
    private void send() {
        if(mailer.sending())return;
        if(!validInputs()){toast("Please check the highlighted weight first.");return;}
        if(store.foods(date.toString(),false).isEmpty()){toast("Enter a weight for at least one food first.");return;}
        GmailSettings settings=new GmailSettings(this);
        if(!settings.configured()){emailSettings();return;}
        mailer.send(settings.sender(),settings.recipient(),settings::password,date.toString(),store.csv(date.toString()));
    }
    private void shareLog() {
        if(!validInputs()){toast("Please check the highlighted weight first.");return;}
        if(store.foods(date.toString(),false).isEmpty()){toast("Enter a weight for at least one food first.");return;}
        try {
            File directory=new File(getFilesDir(),"exports");if(!directory.exists()&&!directory.mkdirs())throw new java.io.IOException();
            // A unique snapshot keeps an already-open email attachment stable after further edits.
            File file=new File(directory,"food-log-"+date+"-"+System.currentTimeMillis()+".csv");
            Files.write(file.toPath(),store.csv(date.toString()).getBytes(StandardCharsets.UTF_8));
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",file);
            Intent intent=new Intent(Intent.ACTION_SEND);intent.setType("text/csv");
            if(!recipient().isEmpty())intent.putExtra(Intent.EXTRA_EMAIL,new String[]{recipient()});
            intent.putExtra(Intent.EXTRA_SUBJECT,"Food log — "+date);
            intent.putExtra(Intent.EXTRA_TEXT,"My food log for "+date+" is attached. All weights are in grams.");
            intent.putExtra(Intent.EXTRA_STREAM,uri);intent.setClipData(ClipData.newRawUri("Daily food log",uri));intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent,"Choose your email app"));
        } catch(android.content.ActivityNotFoundException e){toast("Install an email app to send your log.");}
          catch(java.io.IOException e){toast("Could not create the attachment. Please try again.");}
    }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
}
