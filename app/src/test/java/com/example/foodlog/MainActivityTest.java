package com.example.foodlog;

import android.app.AlertDialog;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class MainActivityTest {
    @Before public void resetFileProviderCache() {
        // Robolectric gives each test a different files directory, while AndroidX
        // retains its static path cache across tests in the same JVM sandbox.
        java.util.Map<?,?> cache=org.robolectric.util.ReflectionHelpers.getStaticField(
            androidx.core.content.FileProvider.class,"sCache");
        cache.clear();
    }
    private <T extends View> T find(View view, Class<T> type, String text) {
        if(type.isInstance(view) && (text==null || view instanceof Button && ((Button)view).getText().toString().contains(text)))return type.cast(view);
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            T result=find(((ViewGroup)view).getChildAt(i),type,text);if(result!=null)return result;
        }
        return null;
    }
    @Test public void addLogAndShareCsvAttachment() throws Exception {
        RuntimeEnvironment.getApplication().deleteDatabase("food-log.db");
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity=controller.get();View root=activity.getWindow().getDecorView();
            find(root,Button.class,"+ Food").performClick();
            shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
            find(dialog.getWindow().getDecorView(),EditText.class,null).setText("Milk");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            shadowOf(android.os.Looper.getMainLooper()).idle();
            EditText weight=find(activity.getWindow().getDecorView(),EditText.class,null);
            assertNotNull(weight);weight.setText("250");
            shareFromSettings(activity);
            Intent chooser=shadowOf(activity).getNextStartedActivity();
            assertEquals(Intent.ACTION_CHOOSER,chooser.getAction());
            Intent share=chooser.getParcelableExtra(Intent.EXTRA_INTENT);
            assertEquals("text/csv",share.getType());
            assertNotNull(share.getParcelableExtra(Intent.EXTRA_STREAM));
            assertTrue((share.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0);
            android.net.Uri uri=share.getParcelableExtra(Intent.EXTRA_STREAM);
            try(var stream=activity.getContentResolver().openInputStream(uri)) {
                assertTrue(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).contains("\"Milk\",250"));
            }
        }
    }
    private void enterDialog(String value) {
        shadowOf(android.os.Looper.getMainLooper()).idle();
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        find(dialog.getWindow().getDecorView(),EditText.class,null).setText(value);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(android.os.Looper.getMainLooper()).idle();
    }
    @Test public void createExpandLogAndDeleteRecipe() throws Exception {
        RuntimeEnvironment.getApplication().deleteDatabase("food-log.db");
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity=controller.get();
            find(activity.getWindow().getDecorView(),Button.class,"+ Recipe").performClick();
            enterDialog("Smoothie");
            find(activity.getWindow().getDecorView(),Button.class,"Add ingredient").performClick();
            enterDialog("Milk");
            EditText milk=find(activity.getWindow().getDecorView(),EditText.class,null);
            milk.setText("250");
            Button toggle=find(activity.getWindow().getDecorView(),Button.class,"Smoothie");
            assertTrue(milk.isShown());toggle.performClick();assertFalse(milk.isShown());
            toggle.performClick();assertTrue(milk.isShown());assertEquals("250",milk.getText().toString());
            shareFromSettings(activity);
            Intent chooser=shadowOf(activity).getNextStartedActivity();
            Intent share=chooser.getParcelableExtra(Intent.EXTRA_INTENT);
            android.net.Uri uri=share.getParcelableExtra(Intent.EXTRA_STREAM);
            try(var stream=activity.getContentResolver().openInputStream(uri)) {
                assertTrue(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).contains("\"Smoothie\",\"Milk\",250"));
            }
            ViewGroup heading=(ViewGroup)toggle.getParent();
            find(heading,Button.class,"×").performClick();
            shadowOf(android.os.Looper.getMainLooper()).idle();
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            shadowOf(android.os.Looper.getMainLooper()).idle();
            assertNull(find(activity.getWindow().getDecorView(),Button.class,"Smoothie"));
            try(var database=new LogStore(activity)) {
                assertTrue(database.recipes(java.time.LocalDate.now().plusDays(1).toString(),true).isEmpty());
                assertTrue(database.foods(java.time.LocalDate.now().toString(),false).isEmpty());
            }
        }
    }
    private java.util.List<EditText> weightFields(View view) {
        java.util.List<EditText> result=new java.util.ArrayList<>();
        if(view instanceof EditText) result.add((EditText)view);
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            result.addAll(weightFields(((ViewGroup)view).getChildAt(i)));
        }
        return result;
    }
    private void shareFromSettings(MainActivity activity) {
        find(activity.getWindow().getDecorView(),Button.class,"Email").performClick();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        find(dialog.getWindow().getDecorView(),Button.class,"Share CSV").performClick();
    }
    private String shareCsv(MainActivity activity) throws Exception {
        shareFromSettings(activity);
        Intent chooser=shadowOf(activity).getNextStartedActivity();
        assertNotNull(chooser);
        Intent share=chooser.getParcelableExtra(Intent.EXTRA_INTENT);
        android.net.Uri uri=share.getParcelableExtra(Intent.EXTRA_STREAM);
        try(var stream=activity.getContentResolver().openInputStream(uri)) {
            return new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    @Test public void previousDayHintsNeverBecomeLoggedOrExportedWeights() throws Exception {
        var context=RuntimeEnvironment.getApplication();context.deleteDatabase("food-log.db");
        String today=java.time.LocalDate.now().toString();
        String yesterday=java.time.LocalDate.now().minusDays(1).toString();
        try(var database=new LogStore(context)) {
            database.add("Milk");database.add("Oats");
            long recipe=database.addRecipe("Smoothie");database.add("Milk",recipe);
            var foods=database.foods(today,true);
            database.save(yesterday,foods.get(0),"250");
            database.save(java.time.LocalDate.now().minusDays(2).toString(),foods.get(1),"80");
            database.save(yesterday,foods.get(2),"100");
        }
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity=controller.get();
            find(activity.getWindow().getDecorView(),Button.class,"Smoothie").performClick();
            var weights=weightFields(activity.getWindow().getDecorView());
            assertEquals(3,weights.size());
            assertEquals("250",weights.get(0).getHint().toString());
            assertEquals("—",weights.get(1).getHint().toString()); // No entry yesterday.
            assertEquals("100",weights.get(2).getHint().toString()); // Independent recipe weight.
            assertNotEquals(weights.get(0).getCurrentTextColor(),weights.get(0).getCurrentHintTextColor());
            for(EditText weight:weights) assertEquals("",weight.getText().toString());
            find(activity.getWindow().getDecorView(),Button.class,"Send").performClick();
            assertNull(shadowOf(activity).getNextStartedActivity());
            try(var database=new LogStore(context)) {
                assertTrue(database.foods(today,false).isEmpty());
                assertEquals("Date,Recipe,Food,Grams\r\n",database.csv(today));
            }
            weights.get(0).setText("300");
            assertEquals("Date,Recipe,Food,Grams\r\n\""+today+"\",\"\",\"Milk\",300\r\n",shareCsv(activity));
            weights.get(2).setText("120");
            String csv=shareCsv(activity);
            assertTrue(csv.contains("\"Smoothie\",\"Milk\",120"));
            assertFalse(csv.contains(",250"));assertFalse(csv.contains(",100"));
            weights.get(2).setText(""); // Clearing restores hint without logging it.
            assertEquals("100",weights.get(2).getHint().toString());
            assertFalse(shareCsv(activity).contains("Smoothie"));
            weights.get(0).setText("");
            controller.recreate();activity=controller.get();
            weights=weightFields(activity.getWindow().getDecorView());
            assertEquals("250",weights.get(0).getHint().toString());
            for(EditText weight:weights) assertEquals("",weight.getText().toString());
            try(var database=new LogStore(context)) {
                assertTrue(database.foods(today,false).isEmpty());
                assertEquals(2,database.foods(yesterday,false).size());
            }
        }
    }
    @Test public void sendWithoutGmailSetupOpensSettingsInsteadOfEmailChooser() {
        var context=RuntimeEnvironment.getApplication();context.deleteDatabase("food-log.db");
        context.getSharedPreferences("gmail",0).edit().clear().commit();
        try(var database=new LogStore(context)) {
            database.add("Milk");String today=java.time.LocalDate.now().toString();
            database.save(today,database.foods(today,true).get(0),"250");
        }
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity=controller.get();
            find(activity.getWindow().getDecorView(),Button.class,"Send").performClick();
            shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing());
            assertNotNull(find(dialog.getWindow().getDecorView(),Button.class,"Create Google app password"));
            assertNull(shadowOf(activity).getNextStartedActivity());
        }
    }
    @Test public void previouslyRemovedFoodCanBeClearedFromScreenAndEmail() throws Exception {
        var context=RuntimeEnvironment.getApplication();context.deleteDatabase("food-log.db");
        String today=java.time.LocalDate.now().toString();
        String yesterday=java.time.LocalDate.now().minusDays(1).toString();
        try(var database=new LogStore(context)) {
            database.add("Milk");database.add("Oats");var foods=database.foods(today,true);
            database.save(yesterday,foods.get(0),"250");database.save(today,foods.get(0),"300");
            database.save(today,foods.get(1),"75");
            // Reproduce the inactive food with a retained weight created by v1.4.
            database.getWritableDatabase().execSQL("UPDATE foods SET active=0 WHERE id=?",new Object[]{foods.get(0).id()});
        }
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity=controller.get();
            assertEquals(2,weightFields(activity.getWindow().getDecorView()).size());
            find(activity.getWindow().getDecorView(),Button.class,"×").performClick();
            shadowOf(android.os.Looper.getMainLooper()).idle();
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(1,weightFields(activity.getWindow().getDecorView()).size());
            String csv=shareCsv(activity);assertFalse(csv.contains("Milk"));assertTrue(csv.contains("Oats"));
            controller.recreate();assertEquals(1,weightFields(controller.get().getWindow().getDecorView()).size());
            try(var database=new LogStore(context)) {assertEquals("250",database.foods(yesterday,false).get(0).grams());}
        }
    }
    private void clickAddGrams(EditText field) {
        find((View)field.getParent(),Button.class,"+").performClick();
        shadowOf(android.os.Looper.getMainLooper()).idle();
    }
    @Test public void addingGramsAccumulatesInOneRowAndExportsTheTotal() throws Exception {
        var context=RuntimeEnvironment.getApplication();context.deleteDatabase("food-log.db");
        String today=java.time.LocalDate.now().toString();
        try(var database=new LogStore(context)) {
            database.add("Banana");database.save(today,database.foods(today,true).get(0),"125");
        }
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity=controller.get();EditText total=weightFields(activity.getWindow().getDecorView()).get(0);
            clickAddGrams(total);enterDialog("68");assertEquals("193",total.getText().toString());
            clickAddGrams(total);enterDialog("0,5");assertEquals("193.5",total.getText().toString());
            clickAddGrams(total);
            AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
            find(dialog.getWindow().getDecorView(),EditText.class,null).setText("50");
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals("193.5",total.getText().toString());
            assertTrue(shareCsv(activity).contains("\"Banana\",193.5"));
            controller.recreate();activity=controller.get();
            assertEquals(1,weightFields(activity.getWindow().getDecorView()).size());
            assertEquals("193.5",weightFields(activity.getWindow().getDecorView()).get(0).getText().toString());
            try(var database=new LogStore(context)){assertEquals(1,database.foods(today,false).size());}
        }
    }
    @Test public void recipeAddIgnoresGreyReferenceAndRejectsEmptyAmount() throws Exception {
        var context=RuntimeEnvironment.getApplication();context.deleteDatabase("food-log.db");
        String today=java.time.LocalDate.now().toString(),yesterday=java.time.LocalDate.now().minusDays(1).toString();
        try(var database=new LogStore(context)) {
            database.add("Banana");long recipe=database.addRecipe("Smoothie");database.add("Banana",recipe);
            var foods=database.foods(today,true);database.save(today,foods.get(0),"125");database.save(yesterday,foods.get(1),"200");
        }
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity=controller.get();find(activity.getWindow().getDecorView(),Button.class,"Smoothie").performClick();
            EditText ingredient=weightFields(activity.getWindow().getDecorView()).get(1);
            assertEquals("200",ingredient.getHint().toString());clickAddGrams(ingredient);enterDialog("");
            assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing());assertEquals("",ingredient.getText().toString());
            enterDialog("68");assertEquals("68",ingredient.getText().toString());
            assertEquals("125",weightFields(activity.getWindow().getDecorView()).get(0).getText().toString());
            String csv=shareCsv(activity);assertTrue(csv.contains("\"Smoothie\",\"Banana\",68"));assertFalse(csv.contains(",268"));
            try(var database=new LogStore(context)){assertEquals("200",database.foods(yesterday,false).get(0).grams());}
        }
    }
}
