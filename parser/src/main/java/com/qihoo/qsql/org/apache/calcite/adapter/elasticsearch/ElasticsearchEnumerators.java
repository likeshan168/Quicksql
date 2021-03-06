/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qihoo.qsql.org.apache.calcite.adapter.elasticsearch;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import org.apache.calcite.avatica.util.DateTimeUtils;
import com.qihoo.qsql.org.apache.calcite.linq4j.function.Function1;
import com.qihoo.qsql.org.apache.calcite.linq4j.tree.Primitive;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Util functions which convert
 * {@link ElasticsearchJson.SearchHit}
 * into calcite specific return type (map, object[], list etc.)
 */
class ElasticsearchEnumerators {
  private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

  private ElasticsearchEnumerators() {}

  private static Function1<ElasticsearchJson.SearchHit, Map> mapGetter() {
    return ElasticsearchJson.SearchHit::sourceOrFields;
  }

  private static Function1<ElasticsearchJson.SearchHit, Object> singletonGetter(
      final String fieldName,
      final Class fieldClass) {
    return hits -> convert(hits.valueOrNull(fieldName), fieldClass);
  }

  /**
   * Function that extracts a given set of fields from elastic search result
   * objects.
   *
   * @param fields List of fields to project
   *
   * @return function that converts the search result into a generic array
   */
  //Updated by qsql-team
  private static Function1<ElasticsearchJson.SearchHit, Object[]> listGetter(
      final List<Map.Entry<String, Class>> fields) {
    return hit -> {
      Object[] objects = new Object[fields.size()];
      for (int i = 0; i < fields.size(); i++) {
        final Map.Entry<String, Class> field = fields.get(i);
        final String name = field.getKey();
        final Class type = field.getValue();

        Object value = hit.valueOrNull(name.toLowerCase());
        objects[i] = convert(value == null ? hit.valueOrNull(name) : value, type);

      }
      return objects;
    };
  }

  //Updated by qsql-team
  static Function1<ElasticsearchJson.SearchHit, Object> getter(
      List<Map.Entry<String, Class>> fields) {
    //noinspection unchecked
    final Function1 getter;
    if (fields == null) {
      // select * from table
      getter = mapGetter();
    } else if (fields.size() == 1) {
      // select foo from table
      getter = singletonGetter(fields.get(0).getKey(), fields.get(0).getValue());
    } else {
      // select a, b, c from table
      getter = listGetter(fields);
    }

    return getter;
  }

  private static Object convert(Object o, Class clazz) {
    if (o == null) {
      return null;
    }
    Primitive primitive = Primitive.of(clazz);
    if (primitive != null) {
      clazz = primitive.boxClass;
    } else {
      primitive = Primitive.ofBox(clazz);
    }
    if (clazz.isInstance(o)) {
      return o;
    }
    //TODO es date type format needs to be supported
    if (java.sql.Date.class.equals(clazz)) {
      if (o instanceof Long) {
        o = new java.sql.Date((long)o);
      } else if (o instanceof String) {
        try {
          Date date = df.parse((String) o);
          o = new java.sql.Date(date.getTime());
        } catch (ParseException e) {
          e.printStackTrace();
        }
      }
    }

    if (o instanceof Date && primitive != null) {
      o = ((Date) o).getTime() / DateTimeUtils.MILLIS_PER_DAY;
    }
    if (o instanceof Number && primitive != null) {
      return primitive.number((Number) o);
    }
    return o;
  }
}

// End ElasticsearchEnumerators.java
