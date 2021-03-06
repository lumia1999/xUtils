/*
 * Copyright (c) 2013. wyouflf (wyouflf@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lidroid.xutils.db.table;

import android.database.Cursor;
import com.lidroid.xutils.DbUtils;
import com.lidroid.xutils.db.converter.ColumnConverter;
import com.lidroid.xutils.db.converter.ColumnConverterFactory;
import com.lidroid.xutils.db.sqlite.ForeignLazyLoader;
import com.lidroid.xutils.exception.DbException;
import com.lidroid.xutils.util.LogUtils;

import java.lang.reflect.Field;
import java.util.List;

public class Foreign extends Column {

    public DbUtils db;

    private String foreignColumnName;
    private Class<?> foreignColumnType;
    private ColumnConverter foreignColumnConverter;

    protected Foreign(Class<?> entityType, Field field) {
        super(entityType, field);
        foreignColumnName = ColumnUtils.getForeignColumnNameByField(field);
        foreignColumnConverter = ColumnConverterFactory.getColumnConverter(getForeignColumnType());
    }

    public String getForeignColumnName() {
        return foreignColumnName;
    }

    public Class<?> getForeignEntityType() {
        return ColumnUtils.getForeignEntityType(this);
    }

    public Class<?> getForeignColumnType() {
        if (foreignColumnType == null) {
            foreignColumnType = TableUtils.getColumnOrId(getForeignEntityType(), foreignColumnName).columnField.getType();
        }
        return foreignColumnType;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setValue2Entity(Object entity, Cursor cursor, int index) {
        Object value = null;
        Object foreignColumnValue = foreignColumnConverter.getFiledValue(entity, cursor, index);
        Class<?> columnType = columnField.getType();
        if (columnType.equals(ForeignLazyLoader.class)) {
            value = new ForeignLazyLoader(this, foreignColumnValue);
        } else if (columnType.equals(List.class)) {
            try {
                value = new ForeignLazyLoader(this, foreignColumnValue).getAllFromDb();
            } catch (DbException e) {
                LogUtils.e(e.getMessage(), e);
            }
        } else {
            try {
                value = new ForeignLazyLoader(this, foreignColumnValue).getFirstFromDb();
            } catch (DbException e) {
                LogUtils.e(e.getMessage(), e);
            }
        }

        if (setMethod != null) {
            try {
                setMethod.invoke(entity, value);
            } catch (Throwable e) {
                LogUtils.e(e.getMessage(), e);
            }
        } else {
            try {
                this.columnField.setAccessible(true);
                this.columnField.set(entity, value);
            } catch (Throwable e) {
                LogUtils.e(e.getMessage(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object getColumnValue(Object entity) {
        Object fieldValue = getFieldValue(entity);

        if (fieldValue != null) {
            Class<?> columnType = columnField.getType();
            if (columnType.equals(ForeignLazyLoader.class)) {
                fieldValue = ((ForeignLazyLoader) fieldValue).getColumnValue();
            } else if (columnType.equals(List.class)) {
                try {
                    List<?> foreignEntities = (List<?>) fieldValue;
                    if (foreignEntities.size() > 0) {

                        if (this.db != null) {
                            this.db.saveOrUpdateAll(foreignEntities);
                        }

                        Class<?> foreignEntityType = ColumnUtils.getForeignEntityType(this);
                        Column column = TableUtils.getColumnOrId(foreignEntityType, foreignColumnName);
                        fieldValue = column.getColumnValue(foreignEntities.get(0));
                    }
                } catch (Throwable e) {
                    fieldValue = null;
                    LogUtils.e(e.getMessage(), e);
                }
            } else {
                try {
                    if (this.db != null) {
                        try {
                            this.db.saveOrUpdate(fieldValue);
                        } catch (DbException e) {
                            LogUtils.e(e.getMessage(), e);
                        }
                    }
                    Column column = TableUtils.getColumnOrId(columnType, foreignColumnName);
                    fieldValue = column.getColumnValue(fieldValue);
                } catch (Throwable e) {
                    fieldValue = null;
                    LogUtils.e(e.getMessage(), e);
                }
            }
        }

        return foreignColumnConverter.fieldValue2ColumnValue(entity, fieldValue);
    }

    public Object getFieldValue(Object entity) {
        Object fieldValue = null;
        if (entity != null) {
            if (getMethod != null) {
                try {
                    fieldValue = getMethod.invoke(entity);
                } catch (Throwable e) {
                    LogUtils.e(e.getMessage(), e);
                }
            } else {
                try {
                    this.columnField.setAccessible(true);
                    fieldValue = this.columnField.get(entity);
                } catch (Throwable e) {
                    LogUtils.e(e.getMessage(), e);
                }
            }
        }
        return fieldValue;
    }

    @Override
    public String getColumnDbType() {
        return foreignColumnConverter.getColumnDbType();
    }

    /**
     * It always return null.
     *
     * @return null
     */
    @Override
    public Object getDefaultValue() {
        return null;
    }
}
