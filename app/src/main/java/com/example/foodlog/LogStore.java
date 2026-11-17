package com.example.foodlog;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

final class LogStore extends SQLiteOpenHelper {
    record Food(long id, String name, String grams, boolean active, long recipeId) {}
    record Recipe(long id, String name, boolean active) {}
    LogStore(Context context) { super(context, "food-log.db", null, 3); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE foods (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, active INTEGER NOT NULL DEFAULT 1, recipe_id INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE entries (date TEXT NOT NULL, food_id INTEGER NOT NULL, name TEXT NOT NULL, grams TEXT NOT NULL, PRIMARY KEY(date, food_id))");
        createRecipes(db);
        createSync(db);
    }
    private void createRecipes(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE recipes (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, active INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE INDEX foods_recipe ON foods(recipe_id)");
    }
    private void createSync(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE foods ADD COLUMN sync_key TEXT NOT NULL DEFAULT ''");
        db.execSQL("ALTER TABLE recipes ADD COLUMN sync_key TEXT NOT NULL DEFAULT ''");
        db.execSQL("CREATE TABLE sync_state (id INTEGER PRIMARY KEY CHECK(id=1), owner TEXT NOT NULL, revision INTEGER NOT NULL, snapshot TEXT NOT NULL)");
        for(String table:new String[]{"foods","recipes"})try(Cursor c=db.rawQuery("SELECT id FROM "+table,null)) {
            while(c.moveToNext()){ContentValues v=new ContentValues();v.put("sync_key",UUID.randomUUID().toString());db.update(table,v,"id=?",new String[]{c.getString(0)});}
        }
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE foods ADD COLUMN recipe_id INTEGER NOT NULL DEFAULT 0");
            createRecipes(db);
        }
        if(oldVersion<3)createSync(db);
    }
    boolean add(String name) { return add(name, 0); }
    boolean add(String name, long recipeId) {
        if (recipeId != 0) {
            try (Cursor c = getReadableDatabase().rawQuery("SELECT id FROM recipes WHERE id=? AND active=1", new String[]{String.valueOf(recipeId)})) {
                if (!c.moveToFirst()) throw new IllegalArgumentException("Recipe is no longer available");
            }
        }
        try (Cursor c = getReadableDatabase().rawQuery("SELECT name FROM foods WHERE active=1 AND recipe_id=?", new String[]{String.valueOf(recipeId)})) {
            while (c.moveToNext()) if (c.getString(0).equalsIgnoreCase(name)) return false;
        }
        ContentValues v = new ContentValues(); v.put("name", name); v.put("recipe_id", recipeId); v.put("sync_key",UUID.randomUUID().toString());
        getWritableDatabase().insertOrThrow("foods", null, v); return true;
    }
    long addRecipe(String name) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT name FROM recipes WHERE active=1", null)) {
            while (c.moveToNext()) if (c.getString(0).equalsIgnoreCase(name)) return -1;
        }
        ContentValues v = new ContentValues(); v.put("name", name); v.put("sync_key",UUID.randomUUID().toString());
        return getWritableDatabase().insertOrThrow("recipes", null, v);
    }
    void removeRecipe(long id, String day) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            ContentValues v = new ContentValues(); v.put("active", 0);
            db.update("recipes", v, "id=?", new String[]{String.valueOf(id)});
            db.update("foods", v, "recipe_id=?", new String[]{String.valueOf(id)});
            db.delete("entries", "date=? AND food_id IN (SELECT id FROM foods WHERE recipe_id=?)",
                new String[]{day,String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    List<Recipe> recipes(String day, boolean today) {
        List<Recipe> result = new ArrayList<>();
        String recorded = "EXISTS (SELECT 1 FROM entries e JOIN foods f ON e.food_id=f.id WHERE f.recipe_id=r.id AND e.date=?)";
        String sql = "SELECT r.id,r.name,r.active FROM recipes r WHERE " + (today ? "r.active=1 OR " : "") + recorded + " ORDER BY r.id";
        try (Cursor c = getReadableDatabase().rawQuery(sql, new String[]{day})) {
            while (c.moveToNext()) result.add(new Recipe(c.getLong(0), c.getString(1), c.getInt(2)==1));
        }
        return result;
    }
    List<String> foodNames() {
        List<String> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT DISTINCT name COLLATE NOCASE FROM foods WHERE active=1 ORDER BY name COLLATE NOCASE", null)) {
            while(c.moveToNext()) result.add(c.getString(0));
        }
        return result;
    }
    void remove(long id, String day) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            ContentValues v = new ContentValues(); v.put("active", 0);
            db.update("foods", v, "id=?", new String[]{String.valueOf(id)});
            db.delete("entries", "date=? AND food_id=?", new String[]{day,String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    List<Food> foods(String day, boolean today) {
        List<Food> rows = new ArrayList<>();
        String sql = today
            ? "SELECT f.id, COALESCE(e.name,f.name), COALESCE(e.grams,''), f.active, f.recipe_id FROM foods f LEFT JOIN entries e ON f.id=e.food_id AND e.date=? WHERE f.active=1 OR e.food_id IS NOT NULL ORDER BY f.id"
            : "SELECT e.food_id,e.name,e.grams,f.active,f.recipe_id FROM entries e JOIN foods f ON f.id=e.food_id WHERE e.date=? ORDER BY e.food_id";
        try (Cursor c = getReadableDatabase().rawQuery(sql, new String[]{day})) {
            while(c.moveToNext()) rows.add(new Food(c.getLong(0),c.getString(1),c.getString(2),c.getInt(3)==1,c.getLong(4)));
        }
        return rows;
    }
    void save(String day, Food food, String grams) {
        if (grams.isEmpty()) {
            getWritableDatabase().delete("entries", "date=? AND food_id=?", new String[]{day,String.valueOf(food.id())});
        } else {
            ContentValues v = new ContentValues(); v.put("date",day); v.put("food_id",food.id()); v.put("name",food.name()); v.put("grams",grams);
            getWritableDatabase().insertWithOnConflict("entries",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        }
    }
    String csv(String day) {
        StringBuilder result = new StringBuilder("Date,Recipe,Food,Grams\r\n");
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(r.name,''),e.name,e.grams FROM entries e JOIN foods f ON e.food_id=f.id LEFT JOIN recipes r ON r.id=f.recipe_id WHERE e.date=? ORDER BY f.recipe_id,f.id", new String[]{day})) {
            while(c.moveToNext()) result.append(LogFormat.csvCell(day)).append(',')
                .append(LogFormat.csvCell(c.getString(0))).append(',')
                .append(LogFormat.csvCell(c.getString(1))).append(',').append(c.getString(2)).append("\r\n");
        }
        return result.toString();
    }
    JSONObject snapshot() throws JSONException {
        JSONObject data=SyncMerge.empty();Map<Long,String> recipes=new HashMap<>(),foods=new HashMap<>();
        SQLiteDatabase db=getReadableDatabase();
        try(Cursor c=db.rawQuery("SELECT id,sync_key,name,active FROM recipes ORDER BY id",null)) {
            while(c.moveToNext()){recipes.put(c.getLong(0),c.getString(1));data.getJSONObject("recipes").put(c.getString(1),new JSONObject().put("name",c.getString(2)).put("active",c.getInt(3)==1));}
        }
        try(Cursor c=db.rawQuery("SELECT id,sync_key,name,active,recipe_id FROM foods ORDER BY id",null)) {
            while(c.moveToNext()){foods.put(c.getLong(0),c.getString(1));data.getJSONObject("foods").put(c.getString(1),new JSONObject().put("name",c.getString(2)).put("active",c.getInt(3)==1).put("recipeId",recipes.getOrDefault(c.getLong(4),"")));}
        }
        try(Cursor c=db.rawQuery("SELECT date,food_id,name,grams FROM entries ORDER BY date,food_id",null)) {
            while(c.moveToNext()){String id=foods.get(c.getLong(1)),date=c.getString(0);data.getJSONObject("entries").put(id+"@"+date,new JSONObject().put("foodId",id).put("date",date).put("name",c.getString(2)).put("grams",c.getString(3)));}
        }
        return data;
    }
    JSONObject syncBase() throws JSONException {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT owner,revision,snapshot FROM sync_state WHERE id=1",null)) {
            if(c.moveToFirst())return new JSONObject().put("owner",c.getString(0)).put("revision",c.getLong(1)).put("snapshot",new JSONObject(c.getString(2)));
        }
        return new JSONObject().put("owner","").put("revision",0).put("snapshot",SyncMerge.empty());
    }
    void applySynced(JSONObject data,String owner,long revision) throws JSONException {
        applySynced(data,owner,revision,data);
    }
    void applySynced(JSONObject data,String owner,long revision,JSONObject serverBase) throws JSONException {
        // Preserve local integer IDs so existing rows remain stable; sync uses UUIDs.
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            Map<String,Long> recipes=new HashMap<>(),foods=new HashMap<>();
            for(String table:new String[]{"recipes","foods"}) {
                Map<String,Long> ids=table.equals("recipes")?recipes:foods;
                try(Cursor c=db.rawQuery("SELECT id,sync_key FROM "+table,null)){while(c.moveToNext())ids.put(c.getString(1),c.getLong(0));}
                JSONObject values=data.getJSONObject(table);
                var keys=values.keys();while(keys.hasNext()){
                    String key=keys.next();JSONObject row=values.getJSONObject(key);ContentValues v=new ContentValues();
                    v.put("sync_key",key);v.put("name",row.getString("name"));v.put("active",row.getBoolean("active")?1:0);
                    if(table.equals("foods")){String recipe=row.getString("recipeId");if(!recipe.isEmpty()&&!recipes.containsKey(recipe))throw new JSONException("Missing recipe");v.put("recipe_id",recipe.isEmpty()?0:recipes.get(recipe));}
                    if(ids.containsKey(key))db.update(table,v,"id=?",new String[]{String.valueOf(ids.get(key))});else ids.put(key,db.insertOrThrow(table,null,v));
                }
                // Templates are soft-deleted; an older browser cannot erase historical names.
            }
            db.delete("entries",null,null);
            for(var item:foods.entrySet())if(!data.getJSONObject("foods").has(item.getKey()))db.delete("foods","id=?",new String[]{String.valueOf(item.getValue())});
            for(var item:recipes.entrySet())if(!data.getJSONObject("recipes").has(item.getKey()))db.delete("recipes","id=?",new String[]{String.valueOf(item.getValue())});
            JSONObject entries=data.getJSONObject("entries");var keys=entries.keys();
            while(keys.hasNext()) {
                JSONObject e=entries.getJSONObject(keys.next());String key=e.getString("foodId");if(!foods.containsKey(key))throw new JSONException("Missing food");
                ContentValues v=new ContentValues();v.put("food_id",foods.get(key));v.put("date",e.getString("date"));v.put("name",e.getString("name"));v.put("grams",LogFormat.weight(e.getString("grams")));db.insertOrThrow("entries",null,v);
            }
            ContentValues meta=new ContentValues();meta.put("id",1);meta.put("owner",owner);meta.put("revision",revision);meta.put("snapshot",serverBase.toString());db.insertWithOnConflict("sync_state",null,meta,SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
}
