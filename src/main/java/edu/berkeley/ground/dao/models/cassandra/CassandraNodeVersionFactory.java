/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.berkeley.ground.dao.models.cassandra;

import edu.berkeley.ground.dao.models.NodeVersionFactory;
import edu.berkeley.ground.db.CassandraClient;
import edu.berkeley.ground.db.CassandraResults;
import edu.berkeley.ground.db.DbClient;
import edu.berkeley.ground.db.DbDataContainer;
import edu.berkeley.ground.exceptions.EmptyResultException;
import edu.berkeley.ground.exceptions.GroundException;
import edu.berkeley.ground.model.models.NodeVersion;
import edu.berkeley.ground.model.models.RichVersion;
import edu.berkeley.ground.model.models.Tag;
import edu.berkeley.ground.model.versions.GroundType;
import edu.berkeley.ground.util.IdGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CassandraNodeVersionFactory extends NodeVersionFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(CassandraNodeVersionFactory.class);
  private final CassandraClient dbClient;
  private final CassandraNodeFactory nodeFactory;
  private final CassandraRichVersionFactory richVersionFactory;

  private final IdGenerator idGenerator;

  /**
   * Constructor for the Cassandra node version factory.
   *
   * @param nodeFactory the singleton CassandraNodeFactory
   * @param richVersionFactory the singleton CassandraRichVersionFactory
   * @param dbClient the Cassandra client
   * @param idGenerator a unique id generator
   */
  public CassandraNodeVersionFactory(CassandraNodeFactory nodeFactory,
                                     CassandraRichVersionFactory richVersionFactory,
                                     CassandraClient dbClient,
                                     IdGenerator idGenerator) {
    this.dbClient = dbClient;
    this.nodeFactory = nodeFactory;
    this.richVersionFactory = richVersionFactory;
    this.idGenerator = idGenerator;
  }


  /**
   * Create and persist a node version.
   *
   * @param tags tags associated with this version
   * @param structureVersionId the id of the StructureVersion associated with this version
   * @param reference an optional external reference
   * @param referenceParameters access parameters for the reference
   * @param nodeId the id of the node containing this version
   * @param parentIds the ids of the parent(s) of this version
   * @return the newly created version
   * @throws GroundException an error while creating or persisting the version
   */
  @Override
  public NodeVersion create(Map<String, Tag> tags,
                            long structureVersionId,
                            String reference,
                            Map<String, String> referenceParameters,
                            long nodeId,
                            List<Long> parentIds) throws GroundException {

    try {
      long id = this.idGenerator.generateVersionId();

      // add the id of the version to the tag
      tags = tags.values().stream().collect(Collectors.toMap(Tag::getKey, tag ->
          new Tag(id, tag.getKey(), tag.getValue(), tag.getValueType()))
      );

      this.richVersionFactory.insertIntoDatabase(id, tags, structureVersionId, reference,
          referenceParameters);

      List<DbDataContainer> insertions = new ArrayList<>();
      insertions.add(new DbDataContainer("id", GroundType.LONG, id));
      insertions.add(new DbDataContainer("node_id", GroundType.LONG, nodeId));

      this.dbClient.insert("node_version", insertions);

      this.nodeFactory.update(nodeId, id, parentIds);

      this.dbClient.commit();
      LOGGER.info("Created node version " + id + " in node " + nodeId + ".");

      return NodeVersionFactory.construct(id, tags, structureVersionId, reference,
          referenceParameters, nodeId);
    } catch (GroundException e) {
      this.dbClient.abort();

      throw e;
    }
  }

  /**
   * Retrieve a node version from the database.
   *
   * @param id the id of the version to retrieve
   * @return the retrieved version
   * @throws GroundException either the version doesn't exist or couldn't be retrieved
   */
  @Override
  public NodeVersion retrieveFromDatabase(long id) throws GroundException {
    try {
      final RichVersion version = this.richVersionFactory.retrieveFromDatabase(id);

      List<DbDataContainer> predicates = new ArrayList<>();
      predicates.add(new DbDataContainer("id", GroundType.LONG, id));

      CassandraResults resultSet;
      try {
        resultSet = this.dbClient.equalitySelect("node_version", DbClient.SELECT_STAR, predicates);
      } catch (EmptyResultException e) {
        this.dbClient.abort();

        throw new GroundException("No NodeVersion found with id " + id + ".");
      }

      long nodeId = resultSet.getLong("node_id");

      this.dbClient.commit();
      LOGGER.info("Retrieved node version " + id + " in node " + nodeId + ".");

      return NodeVersionFactory.construct(id, version.getTags(), version.getStructureVersionId(),
          version.getReference(), version.getParameters(), nodeId);
    } catch (GroundException e) {
      this.dbClient.abort();

      throw e;
    }
  }
}
