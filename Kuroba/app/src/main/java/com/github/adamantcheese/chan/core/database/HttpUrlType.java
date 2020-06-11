package com.github.adamantcheese.chan.core.database;

import androidx.annotation.NonNull;

import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.field.SqlType;
import com.j256.ormlite.field.types.BaseDataType;
import com.j256.ormlite.support.DatabaseResults;

import java.sql.SQLException;

import okhttp3.HttpUrl;

public class HttpUrlType
        extends BaseDataType {

    private static final HttpUrlType singleton = new HttpUrlType();
    private static Class<?>[] associatedClassNames = new Class<?>[]{HttpUrl.class};

    public static HttpUrlType getSingleton() {
        return singleton;
    }

    private HttpUrlType() {
        super(SqlType.STRING, associatedClassNames);
    }

    @Override
    public Object javaToSqlArg(FieldType fieldType, @NonNull Object javaObject) {
        return javaObject.toString();
    }

    @Override
    public Object parseDefaultString(FieldType fieldType, String defaultStr) {
        return defaultStr;
    }

    @Override
    public Object resultToSqlArg(FieldType fieldType, DatabaseResults results, int columnPos)
            throws SQLException {
        return results.getString(columnPos);
    }

    @Override
    public Object sqlArgToJava(FieldType fieldType, Object sqlArg, int columnPos) {
        try {
            return HttpUrl.get((String) sqlArg);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isAppropriateId() {
        return false;
    }
}
