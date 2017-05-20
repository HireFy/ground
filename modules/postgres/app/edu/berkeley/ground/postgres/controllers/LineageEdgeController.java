package edu.berkeley.ground.postgres.controllers;

import akka.actor.ActorSystem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.berkeley.ground.common.exception.GroundException;
import edu.berkeley.ground.common.model.usage.LineageEdge;
import edu.berkeley.ground.common.model.usage.LineageEdgeVersion;
import edu.berkeley.ground.common.util.IdGenerator;
import edu.berkeley.ground.postgres.dao.usage.LineageEdgeDao;
import edu.berkeley.ground.postgres.dao.usage.LineageEdgeVersionDao;
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

public class LineageEdgeController extends Controller {

  private CacheApi cache;
  private ActorSystem actorSystem;

  private LineageEdgeDao lineageEdgeDao;
  private LineageEdgeVersionDao lineageEdgeVersionDao;

  @Inject
  final void injectUtils(final CacheApi cache, final Database dbSource, final ActorSystem actorSystem, final IdGenerator idGenerator) {
    this.actorSystem = actorSystem;
    this.cache = cache;

    this.lineageEdgeDao = new LineageEdgeDao(dbSource, idGenerator);
    this.lineageEdgeVersionDao = new LineageEdgeVersionDao(dbSource, idGenerator);
  }

  public final CompletionStage<Result> getLineageEdge(String sourceKey) {
    return CompletableFuture.supplyAsync(
      () -> {
        try {
          return this.cache.getOrElse(
            "lineage_edges",
            () -> Json.toJson(this.lineageEdgeDao.retrieveFromDatabase(sourceKey)),
            Integer.parseInt(System.getProperty("ground.cache.expire.secs")));
        } catch (Exception e) {
          throw new CompletionException(e);
        }
      },
      PostgresUtils.getDbSourceHttpContext(actorSystem))
             .thenApply(Results::ok)
             .exceptionally(e -> internalServerError(GroundUtils.getServerError(request(), e)));
  }

  public final CompletionStage<Result> getLineageEdgeVersion(Long id) {
    return CompletableFuture.supplyAsync(
      () -> {
        try {
          return this.cache.getOrElse(
            "lineage_edge_versions",
            () -> Json.toJson(this.lineageEdgeVersionDao.retrieveFromDatabase(id)),
            Integer.parseInt(System.getProperty("ground.cache.expire.secs")));
        } catch (Exception e) {
          throw new CompletionException(e);
        }
      },
      PostgresUtils.getDbSourceHttpContext(actorSystem))
             .thenApply(Results::ok)
             .exceptionally(e -> internalServerError(GroundUtils.getServerError(request(), e)));
  }

  @BodyParser.Of(BodyParser.Json.class)
  public final CompletionStage<Result> createLineageEdge() {
    return CompletableFuture.supplyAsync(
      () -> {
        JsonNode json = request().body().asJson();
        LineageEdge lineageEdge = Json.fromJson(json, LineageEdge.class);
        try {
          lineageEdge = this.lineageEdgeDao.create(lineageEdge);
        } catch (GroundException e) {
          throw new CompletionException(e);
        }
        return Json.toJson(lineageEdge);
      },
      PostgresUtils.getDbSourceHttpContext(this.actorSystem))
             .thenApply(Results::created)
             .exceptionally(e -> {
               if (e.getCause() instanceof GroundException) {
                 // TODO: fix
                 return badRequest(GroundUtils.getClientError(request(), e, GroundException.exceptionType.ITEM_NOT_FOUND));
               } else {
                 return internalServerError(GroundUtils.getServerError(request(), e));
               }
             });
  }

  public final CompletionStage<Result> createLineageEdgeVersion() {
    return CompletableFuture.supplyAsync(
      () -> {
        JsonNode json = request().body().asJson();

        List<Long> parentIds = GroundUtils.getListFromJson(json, "parentIds");
        ((ObjectNode) json).remove("parentIds");

        LineageEdgeVersion lineageEdgeVersion = Json.fromJson(json, LineageEdgeVersion.class);

        try {
          lineageEdgeVersion = this.lineageEdgeVersionDao.create(lineageEdgeVersion, parentIds);
        } catch (GroundException e) {
          throw new CompletionException(e);
        }
        return Json.toJson(lineageEdgeVersion);
      },
      PostgresUtils.getDbSourceHttpContext(actorSystem))
             .thenApply(Results::created)
             .exceptionally(e -> {
               if (e.getCause() instanceof GroundException) {
                 // TODO: fix
                 return badRequest(GroundUtils.getClientError(request(), e, GroundException.exceptionType.ITEM_NOT_FOUND));
               } else {
                 return internalServerError(GroundUtils.getServerError(request(), e));
               }
             });
  }
}
