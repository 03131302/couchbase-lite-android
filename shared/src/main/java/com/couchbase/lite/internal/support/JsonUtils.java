package com.couchbase.lite.internal.support;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class JsonUtils {
    public static JSONObject toJson(Map<String, Object> map) throws JSONException {
        if (map == null)
            return null;
        JSONObject json = new JSONObject();
        for (String key : map.keySet()) {
            Object value = map.get(key);
            if (value instanceof Map)
                json.put(key, toJson((Map) value));
            else if (value instanceof List)
                json.put(key, toJson((List) value));
            else
                json.put(key, value);
        }
        return json;
    }

    public static JSONArray toJson(List<Object> list) throws JSONException {
        if (list == null)
            return null;
        JSONArray json = new JSONArray();
        for (Object value : list) {
            if (value instanceof Map)
                json.put(toJson((Map) value));
            else if (value instanceof List)
                json.put(toJson((List) value));
            else
                json.put(value);
        }
        return json;
    }

    public static Map<String, Object> fromJson(JSONObject json) throws JSONException {
        if (json == null)
            return null;
        Map<String, Object> result = new HashMap<String, Object>();
        Iterator<String> it = json.keys();
        while(it.hasNext()) {
            String key = it.next();
            Object value = json.get(key);
            if (value instanceof JSONObject)
                result.put(key, fromJson((JSONObject) value));
            else if (value instanceof JSONArray)
                result.put(key, fromJson((JSONArray) value));
            else
                result.put(key, value);
        }
        return result;
    }

    public static List<Object> fromJson(JSONArray json) throws JSONException {
        if (json == null)
            return null;
        List<Object> result = new ArrayList<Object>();
        for (int i = 0; i < json.length(); i++) {
            Object value = json.get(i);
            if (value instanceof JSONObject)
                result.add(fromJson((JSONObject) value));
            else if (value instanceof JSONArray)
                result.add(fromJson((JSONArray) value));
            else
                result.add(value);
        }
        return result;
    }
}
