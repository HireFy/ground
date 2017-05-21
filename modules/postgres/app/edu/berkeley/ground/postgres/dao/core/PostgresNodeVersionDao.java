package edu.berkeley.ground.postgres.dao.core;

import com.fasterxml.jackson.databind.JsonNode;
import edu.berkeley.ground.common.dao.core.NodeVersionDao;
import edu.berkeley.ground.common.exception.GroundException;
import edu.berkeley.ground.common.model.core.NodeVersion;
import edu.berkeley.ground.common.model.core.RichVersion;
import edu.berkeley.ground.common.util.IdGenerator;
import edu.berkeley.ground.postgres.dao.SqlConstants;
import edu.berkeley.ground.postgres.util.PostgresStatements;
import edu.berkeley.ground.postgres.util.PostgresUtils;
import java.util.List;
import play.db.Database;
import play.libs.Json;

public class PostgresNodeVersionDao extends PostgresRichVersionDao<NodeVersion> implements NodeVersionDao {

  private PostgresNodeDao postgresNodeDao;

  public PostgresNodeVersionDao(Database dbSource, IdGenerator idGenerator) {
    super(dbSource, idGenerator);
    this.postgresNodeDao = new PostgresNodeDao(dbSource, idGenerator);
  }

  @Override
  public NodeVersion create(final NodeVersion nodeVersion, List<Long> parentIds)
    throws GroundException {

    final long uniqueId = idGenerator.generateVersionId();
    NodeVersion newNodeVersion = new NodeVersion(uniqueId, nodeVersion);

    PostgresStatements updateVersionList = this.postgresNodeDao.update(newNodeVersion.getNodeId(), newNodeVersion.getId(), parentIds);

    try {
      PostgresStatements statements = super.insert(newNodeVersion);
      statements.append(String.format(SqlConstants.INSERT_NODE_VERSION, uniqueId, nodeVersion.getNodeId()));
      statements.merge(updateVersionList);

      PostgresUtils.executeSqlList(dbSource, statements);
    } catch (Exception e) {
      e.printStackTrace();
      throw new GroundException(e);
    }
    return newNodeVersion;
  }

  @Override
  public NodeVersion retrieveFromDatabase(long id) throws GroundException {
    String sql = String.format(SqlConstants.SELECT_STAR_BY_ID, "node_version", id);
    JsonNode json = Json.parse(PostgresUtils.executeQueryToJson(dbSource, sql));

    if (json.size() == 0) {
      // TODO: throw version not found exception
      throw new GroundException(String.format("Node Version with id %d does not exist.", id));
    }

    NodeVersion nodeVersion = Json.fromJson(json.get(0), NodeVersion.class);
    RichVersion richVersion = super.retrieveFromDatabase(id);

    return new NodeVersion(id, richVersion.getTags(), richVersion.getStructureVersionId(), richVersion.getReference(), richVersion.getParameters(),
                            nodeVersion.getNodeId());
  }
}
