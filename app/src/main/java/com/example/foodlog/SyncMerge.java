package com.example.foodlog;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

final class SyncMerge {
    record Conflict(String table,String id,Object local,Object remote) {}
    record Result(JSONObject snapshot,List<Conflict> conflicts) {}
    static JSONObject empty() {try{return new JSONObject("{\"recipes\":{},\"foods\":{},\"entries\":{}}");}catch(JSONException e){throw new IllegalStateException(e);}}
    static boolean equal(Object a,Object b) {return canonical(a).equals(canonical(b));}
    static String canonical(Object value) {
        if(value==null)return "undefined";
        if(value instanceof JSONObject o){StringBuilder s=new StringBuilder("{");Set<String> keys=new TreeSet<>();o.keys().forEachRemaining(keys::add);for(String k:keys)s.append(JSONObject.quote(k)).append(':').append(canonical(o.opt(k))).append(',');return s.append('}').toString();}
        return value.toString();
    }
    static Result merge(JSONObject base,JSONObject local,JSONObject remote) throws JSONException {
        JSONObject result=empty();List<Conflict> conflicts=new ArrayList<>();
        for(String table:new String[]{"recipes","foods","entries"}) {
            JSONObject b=base.getJSONObject(table),l=local.getJSONObject(table),r=remote.getJSONObject(table),out=result.getJSONObject(table);
            Set<String> keys=new TreeSet<>();b.keys().forEachRemaining(keys::add);l.keys().forEachRemaining(keys::add);r.keys().forEachRemaining(keys::add);
            for(String id:keys){Object bv=b.opt(id),lv=l.opt(id),rv=r.opt(id),value;
                if(equal(lv,bv))value=rv;else if(equal(rv,bv)||equal(lv,rv))value=lv;
                else {conflicts.add(new Conflict(table,id,lv,rv));value=lv;}
                if(value!=null)out.put(id,value);
            }
        }
        // Review template removal versus a new/edited weight or newly added ingredient.
        for(JSONObject[] pair:new JSONObject[][]{{local,remote},{remote,local}}) {
            JSONObject deleting=pair[0],editing=pair[1],baseFoods=base.getJSONObject("foods"),baseRecipes=base.getJSONObject("recipes");
            var ids=baseFoods.keys();while(ids.hasNext()) {
                String id=ids.next();JSONObject f=baseFoods.getJSONObject(id),deleted=deleting.getJSONObject("foods").optJSONObject(id);
                if(!f.getBoolean("active")||deleted==null||deleted.getBoolean("active"))continue;
                var entries=editing.getJSONObject("entries").keys();while(entries.hasNext()) {
                    String eid=entries.next();JSONObject e=editing.getJSONObject("entries").getJSONObject(eid);
                    if(e.getString("foodId").equals(id)&&!equal(e,base.getJSONObject("entries").opt(eid))) {
                        mark(result,conflicts,local,remote,"foods",id);mark(result,conflicts,local,remote,"entries",eid);
                        String rid=f.getString("recipeId");JSONObject br=baseRecipes.optJSONObject(rid),dr=deleting.getJSONObject("recipes").optJSONObject(rid);
                        if(br!=null&&br.getBoolean("active")&&dr!=null&&!dr.getBoolean("active"))mark(result,conflicts,local,remote,"recipes",rid);
                    }
                }
            }
            ids=baseRecipes.keys();while(ids.hasNext()) {
                String rid=ids.next();JSONObject r=baseRecipes.getJSONObject(rid),dr=deleting.getJSONObject("recipes").optJSONObject(rid);
                if(!r.getBoolean("active")||dr==null||dr.getBoolean("active"))continue;
                var foods=editing.getJSONObject("foods").keys();while(foods.hasNext()) {
                    String fid=foods.next();JSONObject f=editing.getJSONObject("foods").getJSONObject(fid);
                    if(f.getString("recipeId").equals(rid)&&!equal(f,baseFoods.opt(fid))) {
                        mark(result,conflicts,local,remote,"recipes",rid);mark(result,conflicts,local,remote,"foods",fid);
                        var entries=editing.getJSONObject("entries").keys();while(entries.hasNext()) {String eid=entries.next();if(editing.getJSONObject("entries").getJSONObject(eid).getString("foodId").equals(fid))mark(result,conflicts,local,remote,"entries",eid);}
                    }
                }
            }
        }
        return new Result(result,conflicts);
    }
    private static void mark(JSONObject result,List<Conflict> conflicts,JSONObject local,JSONObject remote,String table,String id) throws JSONException {
        Object l=local.getJSONObject(table).opt(id),r=remote.getJSONObject(table).opt(id);
        if(equal(l,r)||conflicts.stream().anyMatch(c->c.table().equals(table)&&c.id().equals(id)))return;
        conflicts.add(new Conflict(table,id,l,r));if(l==null)result.getJSONObject(table).remove(id);else result.getJSONObject(table).put(id,l);
    }
    static void chooseRemote(Result result) throws JSONException {
        for(Conflict c:result.conflicts()){JSONObject table=result.snapshot().getJSONObject(c.table());if(c.remote()==null)table.remove(c.id());else table.put(c.id(),c.remote());}
    }
}
