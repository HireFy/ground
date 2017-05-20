package edu.berkeley.ground.postgres.controllers;

import akka.actor.ActorSystem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.berkeley.ground.common.exception.GroundException;
import edu.berkeley.ground.common.model.core.Node;
import edu.berkeley.ground.common.model.core.NodeVersion;
import edu.berkeley.ground.common.util.IdGenerator;
import edu.berkeley.ground.postgres.dao.core.NodeDao;
import edu.berkeley.ground.postgres.dao.core.NodeVersionDao;
import edu.berkeley.ground.postgres.util.GroundUtils;
import edu.berkeley.ground.postgres.util.PostgresUtils;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;
import play.cache.CacheApi;
import play.db.Database;
import play.libs.Json;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Results;

public class NodeController extends Controller {

  private CacheApi cache;
  private ActorSystem actorSystem;

  private NodeDao nodeDao;
  private NodeVersionDao nodeVersionDao;

  @Inject
  final void injectUtils(final CacheApi cache, final Database dbSource, final ActorSystem actorSystem, final IdGenerator idGenerator) {
    this.actorSystem = actorSystem;
    this.cache = cache;

    this.nodeDao = new NodeDao(dbSource, idGenerator);
    this.nodeVersionDao = new NodeVersionDao(dbSource, idGenerator);
  }

  public final CompletionStage<Result> getNode(String sourceKey) {
    return CompletableFuture.supplyAsync(
      () -> {
        try {
          return this.cache.getOrElse(
            "nodes",
            () -> Json.toJson(this.nodeDao.retrieveFromDatabase(sourceKey)),
            Integer.parseInt(System.getProperty("ground.cache.expire.secs")));
        } catch (Exception e) {
          throw new CompletionException(e);
        }
      },
      PostgresUtils.getDbSourceHttpContext(this.actorSystem))
             .thenApply(Results::ok)
             .exceptionally(e -> internalServerError(GroundUtils.getServerError(request(), e)));
  }

  @BodyParser.Of(BodyParser.Json.class)
  public final CompletionStage<Result> addNode() {
    return CompletableFuture.supplyAsync(
      () -> {
        JsonNode json = request().body().asJson();
        Node node = Json.fromJson(json, Node.class);
        try {
          node = this.nodeDao.create(node);
        } catch (GroundException e) {
          throw new CompletionException(e);
        }
        return Json.toJson(node);
      },
      PostgresUtils.getDbSourceHttpContext(this.actorSystem))
             .thenApply(Results::created)
             .exceptionally(
               e -> {
                 if (e.getCause() instanceof GroundException) {
                   // TODO: fix
                   return badRequest(GroundUtils.getClientError(request(), e, GroundException.exceptionType.ITEM_NOT_FOUND));
                 } else {
                   return internalServerError(GroundUtils.getServerError(request(), e));
                 }
               });
  }

  public final CompletionStage<Result> getNodeVersion(Long id) {
    return CompletableFuture.supplyAsync(
      () -> {
        try {
          return this.cache.getOrElse(
            "node_versions",
            () -> Json.toJson(this.nodeVersionDao.retrieveFromDatabase(id)),
            Integer.parseInt(System.getProperty("ground.cache.expire.secs")));
        } catch (Exception e) {
          throw new CompletionException(e);
        }
      },
      PostgresUtils.getDbSourceHttpContext(this.actorSystem))
             .thenApply(Results::ok)
             .exceptionally(e -> internalServerError(GroundUtils.getServerError(request(), e)));
  }

  @BodyParser.Of(BodyParser.Json.class)
  public final CompletionStage<Result> addNodeVersion() {
    return CompletableFuture.supplyAsync(
      () -> {
        JsonNode json = request().body().asJson();

        List<Long> parentIds = GroundUtils.getListFromJson(json, "parentIds");
        ((ObjectNode) json).remove("parentIds");
        NodeVersion nodeVersion = Json.fromJson(json, NodeVersion.class);

        try {
          nodeVersion = this.nodeVersionDao.create(nodeVersion, parentIds);
        } catch (GroundException e) {
          e.printStackTrace();
          throw new CompletionException(e);
        }
        return Json.toJson(nodeVersion);
      },
      PostgresUtils.getDbSourceHttpContext(this.actorSystem))
             .thenApply(Results::created)
             .exceptionally(
               e -> {
                 if (e.getCause() instanceof GroundException) {
                   // TODO: fix
                   return badRequest(GroundUtils.getClientError(request(), e, GroundException.exceptionType.ITEM_NOT_FOUND));
                 } else {
                   return internalServerError(GroundUtils.getServerError(request(), e));
                 }
               });
  }
}
