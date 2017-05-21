/**
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.berkeley.ground.postgres.dao.version;

import edu.berkeley.ground.common.dao.version.TagDao;
import edu.berkeley.ground.common.exception.GroundException;
import edu.berkeley.ground.common.model.version.GroundType;
import edu.berkeley.ground.common.model.version.Tag;
import edu.berkeley.ground.postgres.dao.SqlConstants;
import edu.berkeley.ground.postgres.util.PostgresStatements;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import play.db.Database;

public class PostgresTagDao implements TagDao {

  private Database dbSource;

  public PostgresTagDao(Database dbSource) {
    this.dbSource = dbSource;
  }

  @Override
  public PostgresStatements insertItemTag(final Tag tag) {
    List<String> sqlList = new ArrayList<>();
    if (tag.getValue() != null) {
      sqlList.add(String.format(SqlConstants.INSERT_ITEM_TAG_WITH_VALUE, tag.getId(), tag.getKey(), tag.getValue(), tag.getValueType()));
    } else {
      sqlList.add(
        String.format(SqlConstants.INSERT_ITEM_TAG_NO_VALUE, tag.getId(), tag.getKey()));
    }
    return new PostgresStatements(sqlList);
  }

  @Override
  public PostgresStatements insertRichVersionTag(final Tag tag) {
    List<String> sqlList = new ArrayList<>();
    if (tag.getValue() != null) {
      sqlList.add(String.format(SqlConstants.INSERT_RICH_VERSION_TAG_WITH_VALUE, tag.getId(), tag.getKey(), tag.getValue(), tag.getValueType()));
    } else {
      sqlList.add(String.format(SqlConstants.INSERT_RICH_VERSION_TAG_NO_VALUE, tag.getId(), tag.getKey()));
    }

    return new PostgresStatements(sqlList);
  }

  @Override
  public Map<String, Tag> retrieveFromDatabaseByVersionId(long id) throws GroundException {
    return this.retrieveFromDatabaseById(id, "rich_version");
  }

  @Override
  public Map<String, Tag> retrieveFromDatabaseByItemId(long id) throws GroundException {
    return this.retrieveFromDatabaseById(id, "item");
  }

  private Map<String, Tag> retrieveFromDatabaseById(long id, String prefix) throws GroundException {

    Map<String, Tag> results = new HashMap<>();
    try {
      Connection con = dbSource.getConnection();

      Statement stmt = con.createStatement();
      String sql = String.format("select * from %s_tag where %s_id = %d", prefix, prefix, id);
      ResultSet resultSet = stmt.executeQuery(sql);

      if (!resultSet.next()) {
        stmt.close();
        con.close();
        return results;
      }
      do {
        String key = resultSet.getString(2);

        // these methods will return null if the input is null, so there's no need to check
        GroundType type = GroundType.fromString(resultSet.getString(4));
        Object value = this.getValue(type, resultSet, 3);

        results.put(key, new Tag(id, key, value, type));
      } while (resultSet.next());
      stmt.close();
      con.close();
    } catch (SQLException e) {
      throw new GroundException(e);
    }
    return results;
  }

  @Override
  public List<Long> getVersionIdsByTag(String tag) throws GroundException {
    return this.getIdsByTag(tag, "rich_version");
  }

  @Override
  public List<Long> getItemIdsByTag(String tag) throws GroundException {
    return this.getIdsByTag(tag, "item");
  }

  private List<Long> getIdsByTag(String tag, String keyPrefix) throws GroundException {

    String sql = String.format("select * from %s_tag where key=\'%s\'", keyPrefix, tag);
    List<Long> result = new ArrayList<>();
    try {
      Connection con = dbSource.getConnection();
      Statement stmt = con.createStatement();
      ResultSet resultSet = stmt.executeQuery(sql);

      if (!resultSet.next()) {
        stmt.close();
        con.close();
        return result;
      }
      do {
        result.add(resultSet.getLong(1));
      } while (resultSet.next());

    } catch (SQLException e) {
      throw new GroundException(e);
    }
    return result;
  }

  private Object getValue(GroundType type, ResultSet resultSet, int index)
    throws GroundException, SQLException {

    if (type == null) {
      return null;
    }

    switch (type) {
      case STRING:
        return resultSet.getString(index);
      case INTEGER:
        return resultSet.getInt(index);
      case LONG:
        return resultSet.getLong(index);
      case BOOLEAN:
        return resultSet.getBoolean(index);
      default:
        // this should never happen because we've listed all types
        throw new GroundException("Unidentified type: " + type);
    }
  }
}
