package esel.esel.esel.util;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import esel.esel.esel.Esel;


/**
 * Created by mike on 17.02.2017.
 */

public class SP {
    static public SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Esel.getsInstance().getApplicationContext());

    static public boolean contains(String key) {
        return sharedPreferences.contains(key);
    }

    static public String getString(int resourceID, String defaultValue) {
        return sharedPreferences.getString(Esel.getsResources().getString(resourceID), defaultValue);
    }

    static public String getString(String key, String defaultValue) {
        return sharedPreferences.getString(key, defaultValue);
    }

    static public boolean getBoolean(int resourceID, boolean defaultValue) {
        try {
            return sharedPreferences.getBoolean(Esel.getsResources().getString(resourceID), defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    static public boolean getBoolean(String key, boolean defaultValue) {
        try {
            return sharedPreferences.getBoolean(key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    static public Double getDouble(int resourceID, Double defaultValue) {
        return SafeParse.stringToDouble(sharedPreferences.getString(Esel.getsResources().getString(resourceID), defaultValue.toString()));
    }

    static public Double getDouble(String key, Double defaultValue) {
        Double value = SafeParse.stringToDouble(sharedPreferences.getString(key, defaultValue.toString()));
        return value;
    }

    static public Double getDouble(String key, Double defaultValue, Double min, Double max) {
        Double value = SafeParse.stringToDouble(sharedPreferences.getString(key, defaultValue.toString()));
        if(value<min){
            value = min;
        }
        if(value>max){
            value = max;
        }
        return value;
    }

    static public int getInt(int resourceID, Integer defaultValue) {
        try {
            return sharedPreferences.getInt(Esel.getsResources().getString(resourceID), defaultValue);
        } catch (Exception e) {
            return SafeParse.stringToInt(sharedPreferences.getString(Esel.getsResources().getString(resourceID), defaultValue.toString()));
        }
    }

    static public int getInt(String key, Integer defaultValue) {
        try {
            return sharedPreferences.getInt(key, defaultValue);
        } catch (Exception e) {
            return SafeParse.stringToInt(sharedPreferences.getString(key, defaultValue.toString()));
        }
    }

    static public long getLong(int resourceID, Long defaultValue) {
        return SafeParse.stringToLong(sharedPreferences.getString(Esel.getsResources().getString(resourceID), defaultValue.toString()));
    }

    static public long getLong(String key, Long defaultValue) {
        try {
            return sharedPreferences.getLong(key, defaultValue);
        } catch (Exception e) {
            return SafeParse.stringToLong(sharedPreferences.getString(key, defaultValue.toString()));
        }
    }

    static public float getFloat(String key, Float defaultValue) {
        try {
            return sharedPreferences.getFloat(key, defaultValue);
        } catch (Exception e) {
            return SafeParse.stringToInt(sharedPreferences.getString(key, defaultValue.toString()));
        }
    }

    static public void putBoolean(String key, boolean value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    static public void putBoolean(int resourceID, boolean value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(Esel.getsResources().getString(resourceID), value);
        editor.apply();
    }

    static public void removeBoolean(int resourceID) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(Esel.getsResources().getString(resourceID));
        editor.apply();
    }

    static public void putLong(String key, long value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(key, value);
        editor.apply();
    }

    static public void putLong(int resourceID, long value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(Esel.getsResources().getString(resourceID), value);
        editor.apply();
    }

    static public void putInt(String key, int value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(key, value);
        editor.apply();
    }

    static public void putInt(int resourceID, int value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(Esel.getsResources().getString(resourceID), value);
        editor.apply();
    }

    static public void putFloat(String key, float value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat(key, value);
        editor.apply();
    }

    static public void putString(int resourceID, String value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(Esel.getsResources().getString(resourceID), value);
        editor.apply();
    }

    static public void putString(String key, String value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }

    static public void removeString(int resourceID) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(Esel.getsResources().getString(resourceID));
        editor.apply();
    }
}
