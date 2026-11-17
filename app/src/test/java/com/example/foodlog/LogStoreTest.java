package com.example.foodlog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LogStoreTest {
    private LogStore store;
    @Before public void setup() {
        RuntimeEnvironment.getApplication().deleteDatabase("food-log.db");
        store=new LogStore(RuntimeEnvironment.getApplication());
    }
    @After public void close() {store.close();}
    @Test public void foodsPersistButWeightsBelongToEachDay() {
        assertTrue(store.add("Milk"));
        LogStore.Food milk=store.foods("2026-11-16",true).get(0);
        store.save("2026-11-16",milk,"250");
        assertEquals("",store.foods("2026-11-17",true).get(0).grams());
        store.save("2026-11-17",milk,"300");
        store.close();store=new LogStore(RuntimeEnvironment.getApplication());
        assertEquals("250",store.foods("2026-11-16",false).get(0).grams());
        assertEquals("300",store.foods("2026-11-17",false).get(0).grams());
    }
    @Test public void removalKeepsLogsAndReaddingDoesNotOverwriteThem() {
        store.add("Milk");LogStore.Food milk=store.foods("2026-11-16",true).get(0);
        store.save("2026-11-16",milk,"250");store.remove(milk.id(),"2026-11-17");
        assertTrue(store.foods("2026-11-17",true).isEmpty());
        assertEquals("250",store.foods("2026-11-16",false).get(0).grams());
        assertTrue(store.add("Milk"));
        assertNotEquals(milk.id(),store.foods("2026-11-17",true).get(0).id());
        assertTrue(store.csv("2026-11-16").contains("\"Milk\",250"));
    }
    @Test public void editingReplacesDailyTotalAndClearingRemovesEntry() {
        store.add("Milk");assertFalse(store.add("MILK"));
        LogStore.Food milk=store.foods("2026-11-16",true).get(0);
        store.save("2026-11-16",milk,"250");store.save("2026-11-16",milk,"300");
        assertEquals(1,store.foods("2026-11-16",false).size());
        assertTrue(store.csv("2026-11-16").contains(",300\r\n"));
        store.save("2026-11-16",milk,"");
        assertTrue(store.foods("2026-11-16",false).isEmpty());
        assertEquals(1,store.foods("2026-11-17",true).size());
    }
    @Test public void recipesHaveIndependentWeightsAndPersistWithBlankWeightsTomorrow() {
        long smoothie=store.addRecipe("Smoothie"), porridge=store.addRecipe("Porridge");
        assertEquals(-1,store.addRecipe("SMOOTHIE"));
        assertTrue(store.add("Milk"));assertTrue(store.add("Milk",smoothie));assertTrue(store.add("Milk",porridge));
        assertFalse(store.add("MILK",smoothie));
        var foods=store.foods("2026-11-16",true);
        store.save("2026-11-16",foods.get(0),"250");
        store.save("2026-11-16",foods.get(1),"100");
        store.save("2026-11-16",foods.get(2),"75");
        assertEquals(3,store.foods("2026-11-16",false).size());
        store.close();store=new LogStore(RuntimeEnvironment.getApplication());
        assertEquals(2,store.recipes("2026-11-17",true).size());
        for(var food:store.foods("2026-11-17",true)) assertEquals("",food.grams());
        String csv=store.csv("2026-11-16");
        assertTrue(csv.startsWith("Date,Recipe,Food,Grams"));
        assertTrue(csv.contains("\"\",\"Milk\",250"));
        assertTrue(csv.contains("\"Smoothie\",\"Milk\",100"));
        assertTrue(csv.contains("\"Porridge\",\"Milk\",75"));
    }
    @Test public void deletingRecipePreservesHistoryAndDoesNotAffectOtherFoods() {
        long recipe=store.addRecipe("Smoothie");store.add("Milk",recipe);store.add("Milk");
        var ingredient=store.foods("2026-11-16",true).get(0);
        store.save("2026-11-16",ingredient,"200");store.removeRecipe(recipe,"2026-11-17");
        assertTrue(store.recipes("2026-11-17",true).isEmpty());
        assertEquals(1,store.foods("2026-11-17",true).size());
        assertEquals(0,store.foods("2026-11-17",true).get(0).recipeId());
        assertEquals(1,store.recipes("2026-11-16",false).size());
        assertFalse(store.recipes("2026-11-16",false).get(0).active());
        assertTrue(store.csv("2026-11-16").contains("\"Smoothie\",\"Milk\",200"));
        assertNotEquals(recipe,store.addRecipe("Smoothie"));
        assertThrows(IllegalArgumentException.class,()->store.add("Banana",recipe));
    }
    @Test public void removingIngredientAndClearingWeightPreserveRecipeTemplate() {
        long recipe=store.addRecipe("Smoothie");store.add("Milk",recipe);store.add("Banana",recipe);
        var foods=store.foods("2026-11-16",true);
        store.save("2026-11-16",foods.get(0),"250");store.remove(foods.get(0).id(),"2026-11-17");
        assertEquals(1,store.foods("2026-11-17",true).size());
        assertEquals("Banana",store.foods("2026-11-17",true).get(0).name());
        assertTrue(store.csv("2026-11-16").contains("\"Milk\",250"));
        store.save("2026-11-16",foods.get(0),"");
        assertTrue(store.foods("2026-11-16",false).isEmpty());
        assertEquals(1,store.recipes("2026-11-17",true).size());
        assertTrue(store.recipes("2026-11-16",false).isEmpty());
    }
    @Test public void versionOneUpgradeKeepsExistingFoodsAndLogs() {
        store.close();
        var context=RuntimeEnvironment.getApplication();context.deleteDatabase("food-log.db");
        try(var old=context.openOrCreateDatabase("food-log.db",0,null)) {
            old.execSQL("CREATE TABLE foods (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, active INTEGER NOT NULL DEFAULT 1)");
            old.execSQL("CREATE TABLE entries (date TEXT NOT NULL, food_id INTEGER NOT NULL, name TEXT NOT NULL, grams TEXT NOT NULL, PRIMARY KEY(date, food_id))");
            old.execSQL("INSERT INTO foods (name) VALUES ('Milk')");
            old.execSQL("INSERT INTO entries VALUES ('2026-11-16',1,'Milk','250')");
            old.setVersion(1);
        }
        store=new LogStore(context);
        assertEquals("250",store.foods("2026-11-16",false).get(0).grams());
        assertEquals(0,store.foods("2026-11-16",false).get(0).recipeId());
        assertEquals("",store.foods("2026-11-17",true).get(0).grams());
        long recipe=store.addRecipe("Smoothie");assertTrue(store.add("Milk",recipe));
        assertEquals(2,store.foods("2026-11-17",true).size());
    }
    @Test public void removalClearsSelectedDayAndExportButPreservesOtherDates() {
        store.add("Milk");var milk=store.foods("2026-11-17",true).get(0);
        store.save("2026-11-16",milk,"250");store.save("2026-11-17",milk,"300");
        store.remove(milk.id(),"2026-11-17");
        assertTrue(store.foods("2026-11-17",true).isEmpty());
        assertTrue(store.foods("2026-11-17",false).isEmpty());
        assertEquals("Date,Recipe,Food,Grams\r\n",store.csv("2026-11-17"));
        assertEquals("250",store.foods("2026-11-16",false).get(0).grams());
        store.close();store=new LogStore(RuntimeEnvironment.getApplication());
        assertTrue(store.foods("2026-11-17",true).isEmpty());
    }
    @Test public void recipeRemovalClearsAllIngredientsForSelectedDateOnly() {
        long recipe=store.addRecipe("Smoothie");store.add("Milk",recipe);store.add("Banana",recipe);store.add("Milk");
        var foods=store.foods("2026-11-17",true);
        for(var food:foods) {store.save("2026-11-16",food,"100");store.save("2026-11-17",food,"200");}
        store.removeRecipe(recipe,"2026-11-17");
        assertTrue(store.recipes("2026-11-17",true).isEmpty());
        assertEquals(1,store.foods("2026-11-17",false).size());
        assertEquals(0,store.foods("2026-11-17",false).get(0).recipeId());
        assertFalse(store.csv("2026-11-17").contains("Smoothie"));
        assertEquals(3,store.foods("2026-11-16",false).size());
        assertEquals(1,store.recipes("2026-11-16",false).size());
    }
    @Test public void removingIngredientDoesNotRemoveOtherIngredientsOrStandaloneFood() {
        long recipe=store.addRecipe("Smoothie");store.add("Milk",recipe);store.add("Banana",recipe);store.add("Milk");
        var foods=store.foods("2026-11-17",true);
        for(var food:foods)store.save("2026-11-17",food,"100");
        store.remove(foods.get(0).id(),"2026-11-17");
        assertEquals(2,store.foods("2026-11-17",true).size());
        assertEquals(2,store.foods("2026-11-17",false).size());
        assertFalse(store.csv("2026-11-17").contains("\"Smoothie\",\"Milk\""));
        assertEquals(1,store.recipes("2026-11-17",true).size());
    }
}
